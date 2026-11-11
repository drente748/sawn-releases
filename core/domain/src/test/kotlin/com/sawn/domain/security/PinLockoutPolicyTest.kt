package com.sawn.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockoutPolicyTest {

    @Test
    fun lockStartsAtFifthFailureAndEscalates() {
        assertEquals(0L, PinLockoutPolicy.lockDurationMillis(4))
        assertEquals(30_000L, PinLockoutPolicy.lockDurationMillis(5))
        assertEquals(60_000L, PinLockoutPolicy.lockDurationMillis(6))
        assertEquals(120_000L, PinLockoutPolicy.lockDurationMillis(7))
    }

    @Test
    fun lockIsCappedAtOneHour() {
        assertEquals(3_600_000L, PinLockoutPolicy.lockDurationMillis(20))
        assertEquals(3_600_000L, PinLockoutPolicy.lockDurationMillis(100))
    }

    @Test
    fun wallClockChangesCannotShortenSameBootLock() {
        val remaining = PinLockoutPolicy.remainingMillis(
            wallLockUntil = 1_030_000L,
            elapsedLockUntil = 530_000L,
            lockBootCount = 12,
            currentBootCount = 12,
            wallNow = 2_000_000L,
            elapsedNow = 510_000L,
        )
        assertEquals(20_000L, remaining)
    }

    @Test
    fun laterWallClockCanExtendLock() {
        val remaining = PinLockoutPolicy.remainingMillis(
            wallLockUntil = 1_060_000L,
            elapsedLockUntil = 530_000L,
            lockBootCount = 12,
            currentBootCount = 12,
            wallNow = 1_000_000L,
            elapsedNow = 510_000L,
        )
        assertEquals(60_000L, remaining)
    }

    @Test
    fun rebootFallsBackToWallClock() {
        val remaining = PinLockoutPolicy.remainingMillis(
            wallLockUntil = 1_030_000L,
            elapsedLockUntil = 530_000L,
            lockBootCount = 12,
            currentBootCount = 13,
            wallNow = 1_010_000L,
            elapsedNow = 5_000L,
        )
        assertEquals(20_000L, remaining)
    }

    @Test
    fun expiredLockAllowsAttempt() {
        assertEquals(
            0L,
            PinLockoutPolicy.remainingMillis(
                wallLockUntil = 1_000_000L,
                elapsedLockUntil = 500_000L,
                lockBootCount = 12,
                currentBootCount = 12,
                wallNow = 1_000_001L,
                elapsedNow = 500_001L,
            ),
        )
    }
}
