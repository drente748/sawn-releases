package com.sawn.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProtectionDayPolicyTest {

    private val d = { month: Int, day: Int -> LocalDate.of(2026, month, day) }

    @Test
    fun `dry run - consecutive days ending today returns full streak`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 3))
        assertEquals(3, ProtectionDayPolicy.currentStreak(days, d(11, 3)))
    }

    @Test
    fun `yesterday as last protected day keeps the streak visible today`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 3))
        assertEquals(3, ProtectionDayPolicy.currentStreak(days, d(11, 4)))
    }

    @Test
    fun `streak collapses to zero once a full day was missed`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 3))
        assertEquals(0, ProtectionDayPolicy.currentStreak(days, d(11, 5)))
    }

    @Test
    fun `last protected yesterday remains visible even after today passes`() {
        val days = setOf(d(11, 3))
        assertEquals(0, ProtectionDayPolicy.currentStreak(days, d(11, 5)))
        assertEquals(1, ProtectionDayPolicy.currentStreak(days, d(11, 4)))
    }

    @Test
    fun `a gap breaks the current streak but history is preserved`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 10), d(11, 11), d(11, 12))
        assertEquals(3, ProtectionDayPolicy.currentStreak(days, d(11, 12)))
    }

    @Test
    fun `same day recorded twice counts once`() {
        val days = setOf(d(11, 3), d(11, 3))
        assertEquals(1, ProtectionDayPolicy.currentStreak(days, d(11, 3)))
    }

    @Test
    fun `future dates are ignored for the current streak`() {
        assertEquals(0, ProtectionDayPolicy.currentStreak(setOf(d(11, 6)), d(11, 5)))
        // and a future date never extends "today"
        assertEquals(0, ProtectionDayPolicy.currentStreak(setOf(d(11, 1), d(11, 6)), d(11, 5)))
    }

    @Test
    fun `longest streak across a gap`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 10), d(11, 11), d(11, 12))
        assertEquals(3, ProtectionDayPolicy.longestStreak(days))
    }

    @Test
    fun `longest streak with empty history is zero`() {
        assertEquals(0, ProtectionDayPolicy.longestStreak(emptySet()))
    }

    @Test
    fun `longest streak across multiple month boundaries`() {
        val days = setOf(d(10, 30), d(10, 31), d(11, 1), d(11, 2), d(11, 3))
        assertEquals(5, ProtectionDayPolicy.longestStreak(days))
    }

    @Test
    fun `isAtRisk when yesterday was protected and today is not`() {
        val days = setOf(d(11, 4))
        assertTrue(ProtectionDayPolicy.isAtRisk(days, d(11, 5)))
        assertFalse(ProtectionDayPolicy.isAtRisk(days + d(11, 5), d(11, 5)))
        assertFalse(ProtectionDayPolicy.isAtRisk(emptySet(), d(11, 5)))
    }

    @Test
    fun `longest streak ignores future dates when a today is supplied`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 3), d(11, 20), d(11, 21), d(11, 22), d(11, 23))
        assertEquals(3, ProtectionDayPolicy.longestStreak(days, d(11, 5)))
        assertEquals(4, ProtectionDayPolicy.longestStreak(days, d(11, 23)))
        // Without a cutoff the legacy history-only behaviour is unchanged.
        assertEquals(4, ProtectionDayPolicy.longestStreak(days))
    }

    @Test
    fun `total protected days counts recorded days up to today only`() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 3), d(11, 9))
        assertEquals(3, ProtectionDayPolicy.totalProtectedDays(days, d(11, 5)))
        assertEquals(4, ProtectionDayPolicy.totalProtectedDays(days, d(11, 9)))
        assertEquals(0, ProtectionDayPolicy.totalProtectedDays(emptySet(), d(11, 5)))
    }

    @Test
    fun `heatmap returns pastDays cells ending today`() {
        val days = setOf(d(11, 1), d(11, 3))
        val cells = ProtectionDayPolicy.heatmap(days, d(11, 3), pastDays = 3)
        assertEquals(3, cells.size)
        assertEquals(d(11, 1), cells[0].date)
        assertTrue(cells[0].protected)
        assertFalse(cells[1].protected)
        assertTrue(cells[2].protected)
    }

    @Test
    fun `heatmap of a single day`() {
        val cells = ProtectionDayPolicy.heatmap(setOf(d(11, 3)), d(11, 3), pastDays = 1)
        assertEquals(1, cells.size)
        assertTrue(cells.single().protected)
    }
}
