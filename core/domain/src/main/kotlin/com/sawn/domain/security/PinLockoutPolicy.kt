package com.sawn.domain.security

object PinLockoutPolicy {

    fun lockDurationMillis(failures: Int): Long {
        if (failures < 5) return 0L
        val step = (failures - 5).coerceAtMost(7)
        return (30L shl step).coerceAtMost(3600L) * 1000L
    }

    fun remainingMillis(
        wallLockUntil: Long,
        elapsedLockUntil: Long,
        lockBootCount: Int,
        currentBootCount: Int,
        wallNow: Long,
        elapsedNow: Long,
    ): Long {
        val wallRemaining = positiveRemaining(wallLockUntil, wallNow)
        if (lockBootCount != currentBootCount) return wallRemaining
        return maxOf(wallRemaining, positiveRemaining(elapsedLockUntil, elapsedNow))
    }

    private fun positiveRemaining(until: Long, now: Long): Long =
        if (until > now) until - now else 0L
}
