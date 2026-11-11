package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockJournalTest {

    private val today = 20_000L

    @Test
    fun `decode ignores malformed entries and hostile values`() {
        val log = BlockJournal.decode("19990:3|bad|1:x|:2|19999:-4|19998:5")
        assertEquals(3, log[19990L])
        assertEquals(5, log[19998L])
        assertEquals(2, log.size)
    }

    @Test
    fun `encode and decode round trip`() {
        val log = mapOf(19999L to 2, 19998L to 1)
        assertEquals(log, BlockJournal.decode(BlockJournal.encode(log)))
    }

    @Test
    fun `bump increments today and trims older than two weeks`() {
        val log = mapOf(today - 20 to 4, today - 1 to 2, today to 1)
        val next = BlockJournal.bump(log, today)
        assertEquals(2, next[today])
        assertEquals(2, next[today - 1])
        assertEquals(null, next[today - 20])
    }

    @Test
    fun `snapshot gives today and seven chronological days`() {
        val log = mapOf(today to 4, today - 1 to 2, today - 6 to 1, today - 7 to 9)
        val snapshot = BlockJournal.snapshot(log, total = 42, today = today)
        assertEquals(4, snapshot.today)
        assertEquals(listOf(1, 0, 0, 0, 0, 2, 4), snapshot.last7)
        assertEquals(7, snapshot.weekTotal)
        assertEquals(4, snapshot.peak)
        assertEquals(42, snapshot.total)
        assertEquals(0, snapshot.urgesToday)
        assertEquals(0, snapshot.urgesLast7)
        assertEquals(0, snapshot.urgesTotal)
    }

    @Test
    fun `empty journal reads as a quiet week`() {
        val snapshot = BlockJournal.snapshot(emptyMap(), total = 0, today = today)
        assertEquals(0, snapshot.today)
        assertEquals(0, snapshot.weekTotal)
        assertTrue(snapshot.last7.all { it == 0 })
        assertEquals(0, snapshot.urgesToday)
        assertEquals(0, snapshot.urgesLast7)
        assertEquals(0, snapshot.urgesTotal)
    }

    @Test
    fun `snapshot counts completed urge pauses separately from blocked attempts`() {
        val snapshot = BlockJournal.snapshot(
            log = mapOf(today to 8, today - 1 to 3),
            total = 100,
            today = today,
            urges = mapOf(today to 2, today - 2 to 1),
            urgeTotal = 9,
        )
        assertEquals(8, snapshot.today)
        assertEquals(2, snapshot.urgesToday)
        assertEquals(3, snapshot.urgesLast7)
        assertEquals(9, snapshot.urgesTotal)
    }
}
