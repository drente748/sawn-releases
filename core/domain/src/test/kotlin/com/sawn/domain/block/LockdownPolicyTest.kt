package com.sawn.domain.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockdownPolicyTest {
    private val now = 1_000_000L

    @Test
    fun zeroUntil_isNeverActive() {
        assertFalse(LockdownPolicy.isActive(0L, now))
    }

    @Test
    fun futureUntil_isActive_beforeIt() {
        assertTrue(LockdownPolicy.isActive(now + 10L, now))
    }

    @Test
    fun expiredUntil_isInactive() {
        assertFalse(LockdownPolicy.isActive(now, now))
        assertFalse(LockdownPolicy.isActive(now - 10L, now))
    }

    @Test
    fun manualValidation_acceptsBounds() {
        assertTrue(LockdownPolicy.validateManual(1L, LockdownPolicy.DurationUnit.MINUTES)!! > 0)
        assertTrue(LockdownPolicy.validateManual(12L, LockdownPolicy.DurationUnit.MONTHS)!! > 0)
        assertTrue(LockdownPolicy.validateManual(6L, LockdownPolicy.DurationUnit.HOURS)!! > 0)
        assertTrue(LockdownPolicy.validateManual(3L, LockdownPolicy.DurationUnit.WEEKS)!! > 0)
        assertTrue(LockdownPolicy.validateManual(2L, LockdownPolicy.DurationUnit.DAYS)!! > 0)
    }

    @Test
    fun manualValidation_rejectsOutOfRange() {
        assertNull(LockdownPolicy.validateManual(0L, LockdownPolicy.DurationUnit.MINUTES))
        assertNull(LockdownPolicy.validateManual(-5L, LockdownPolicy.DurationUnit.DAYS))
        assertNull(LockdownPolicy.validateManual(13L, LockdownPolicy.DurationUnit.MONTHS))
        assertTrue(LockdownPolicy.validateManual(1L, LockdownPolicy.DurationUnit.MONTHS)!! >= 28L * 24 * 3600_000L)
    }

    @Test
    fun monthConversion_isSane() {
        val ms = LockdownPolicy.validateManual(1L, LockdownPolicy.DurationUnit.MONTHS)!!
        assertEquals(30L * 24 * 3600_000L, ms)
    }

    @Test
    fun sameBoot_forwardWallClockCannotEndLock() {
        val window = LockdownWindow(
            wallUntil = now - 10_000L, // wall clock jumped past the deadline
            elapsedEnd = 500_000L,
            startBootCount = 7,
            currentBootCount = 7,
            elapsedNow = 400_000L,
            wallNow = now,
        )
        assertTrue(window.isActive)
        assertEquals(100_000L, window.remainingMillis)
    }

    @Test
    fun sameBoot_elapsedDeadlineEndsLock() {
        val window = LockdownWindow(
            wallUntil = now + 10_000L, // wall clock says still active
            elapsedEnd = 500_000L,
            startBootCount = 7,
            currentBootCount = 7,
            elapsedNow = 500_000L,
            wallNow = now,
        )
        assertFalse(window.isActive)
        assertEquals(0L, window.remainingMillis)
    }

    @Test
    fun newBoot_fallsBackToWallDeadline() {
        val active = LockdownWindow(
            wallUntil = now + 10_000L,
            elapsedEnd = 500_000L,
            startBootCount = 6,
            currentBootCount = 7,
            elapsedNow = 400_000L,
            wallNow = now,
        )
        assertTrue(active.isActive)

        val expired = active.copy(wallUntil = now - 1L)
        assertFalse(expired.isActive)
    }

    @Test
    fun missingElapsedAnchor_fallsBackToWallDeadline() {
        val window = LockdownWindow(
            wallUntil = now + 10_000L,
            elapsedEnd = 0L,
            startBootCount = 7,
            currentBootCount = 7,
            elapsedNow = 400_000L,
            wallNow = now,
        )
        assertTrue(window.isActive)
    }

    @Test
    fun zeroWallUntil_isNeverActive() {
        val window = LockdownWindow(
            wallUntil = 0L,
            elapsedEnd = 500_000L,
            startBootCount = 7,
            currentBootCount = 7,
            elapsedNow = 400_000L,
            wallNow = now,
        )
        assertFalse(window.isActive)
    }

    @Test
    fun remainingAt_keepsUsingElapsedAnchorWithinTheSameBoot() {
        val window = LockdownWindow(
            wallUntil = now - 10_000L,
            elapsedEnd = 500_000L,
            startBootCount = 7,
            currentBootCount = 7,
            elapsedNow = 400_000L,
            wallNow = now,
        )
        // A forward wall-clock jump must not change the countdown.
        assertEquals(100_000L, window.remainingAt(elapsedAt = 400_000L, wallAt = now + 90_000_000L))
    }

    @Test
    fun manualAmount_acceptsArabicIndicAndPersianDigits() {
        assertEquals(30L, LockdownPolicy.parseManualAmount("٣٠"))
        assertEquals(12L, LockdownPolicy.parseManualAmount("۱۲"))
        assertEquals(7L, LockdownPolicy.parseManualAmount(" ٧ "))
    }

    @Test
    fun manualAmount_rejectsNonNumericInput() {
        assertNull(LockdownPolicy.parseManualAmount("abc"))
        assertNull(LockdownPolicy.parseManualAmount(""))
    }

    @Test
    fun newLockdown_isAllowedWhenNoLockIsActive() {
        assertTrue(LockdownPolicy.mayApplyLockdown(currentUntil = 0L, newUntil = now + 5_000L, now = now))
        assertTrue(LockdownPolicy.mayApplyLockdown(currentUntil = now - 1L, newUntil = now + 5_000L, now = now))
    }

    @Test
    fun activeLockdown_rejectsClearingIt() {
        assertFalse(LockdownPolicy.mayApplyLockdown(currentUntil = now + 10_000L, newUntil = 0L, now = now))
        assertFalse(LockdownPolicy.mayApplyLockdown(currentUntil = now + 10_000L, newUntil = now, now = now))
    }

    @Test
    fun activeLockdown_rejectsShorteningIt() {
        assertFalse(LockdownPolicy.mayApplyLockdown(currentUntil = now + 10_000L, newUntil = now + 5_000L, now = now))
        assertFalse(LockdownPolicy.mayApplyLockdown(currentUntil = now + 10_000L, newUntil = now + 10_000L, now = now))
    }

    @Test
    fun activeLockdown_acceptsOnlyAStrictExtension() {
        assertTrue(LockdownPolicy.mayApplyLockdown(currentUntil = now + 10_000L, newUntil = now + 10_001L, now = now))
    }
}
