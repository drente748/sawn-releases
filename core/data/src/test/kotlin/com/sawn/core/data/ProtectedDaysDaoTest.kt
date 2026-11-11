package com.sawn.core.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ProtectedDaysDaoTest {

    private lateinit var db: SawnDatabase
    private val today: LocalDate get() = LocalDate.now()
    private val yesterday: LocalDate get() = today.minusDays(1)
    private val twoDaysAgo: LocalDate get() = today.minusDays(2)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SawnDatabase::class.java,
        ).build()
    }

    @org.junit.After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_isIdempotent_forSameDay() = runBlocking {
        val repo = RoomProtectedDaysRepository(db.protectedDaysDao())
        repo.upsert(today, "VPN")
        repo.upsert(today, "VPN")
        repo.upsert(today, "FOCUS")
        assertEquals(listOf(today), repo.allDays())
    }

    @Test
    fun observe_emits_all_recorded_days_as_a_set() = runBlocking {
        val repo = RoomProtectedDaysRepository(db.protectedDaysDao())
        repo.upsert(twoDaysAgo, "VPN")
        repo.upsert(yesterday, "FOCUS")
        repo.upsert(today, "VPN")
        val days = repo.observeProtectedDays().first()
        assertEquals(setOf(twoDaysAgo, yesterday, today), days)
    }

    @Test
    fun stats_derive_current_and_longest_streaks() = runBlocking {
        val days = RoomProtectedDaysRepository(db.protectedDaysDao())
        days.upsert(twoDaysAgo, "VPN")
        days.upsert(yesterday, "VPN")
        days.upsert(today, "VPN")
        val stats = RoomStatsRepository(days)
        assertEquals(3, stats.currentStreak().first())
        assertEquals(3, stats.longestStreak().first())
    }

    @Test
    fun stats_streak_visible_yesterday_but_isAtRisk() = runBlocking {
        val days = RoomProtectedDaysRepository(db.protectedDaysDao())
        days.upsert(yesterday, "VPN")
        val stats = RoomStatsRepository(days)
        assertEquals(1, stats.currentStreak().first())
        assertTrue(stats.isAtRisk().first())
    }

    // Regression: a corrupted protected_days row (garbage date) used to throw from
    // LocalDate.parse inside a Flow map, kill every streak/stats collector, and blank the
    // whole Home screen. Reading stats must never take the UI down.
    @Test
    fun corrupt_date_rows_do_not_crash_readers() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('not-a-date', 'VPN', 0)",
        )
        val days = RoomProtectedDaysRepository(db.protectedDaysDao())
        assertEquals(emptyList<LocalDate>(), days.allDays())
        val stats = RoomStatsRepository(days)
        assertEquals(0, stats.currentStreak().first())
        assertEquals(0, stats.longestStreak().first())
        assertFalse(stats.isAtRisk().first())
    }

    @Test
    fun observe_ignores_corrupt_rows_but_keeps_valid_ones() = runBlocking {
        val repo = RoomProtectedDaysRepository(db.protectedDaysDao())
        repo.upsert(yesterday, "VPN")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-13-40', 'VPN', 0)",
        )
        assertEquals(setOf(yesterday), repo.observeProtectedDays().first())
    }

    @Test
    fun empty_history_yields_zero_streaks() = runBlocking {
        val days = RoomProtectedDaysRepository(db.protectedDaysDao())
        val stats = RoomStatsRepository(days)
        assertEquals(0, stats.currentStreak().first())
        assertEquals(0, stats.longestStreak().first())
        assertFalse(stats.isAtRisk().first())
    }
}
