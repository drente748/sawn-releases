package com.sawn.domain.block

/** Pure "is blocking enforced right now?" rule, shared by the engine and tests. */
object BlockingPolicy {
    fun isActive(
        masterEnabled: Boolean,
        alwaysOn: Boolean,
        focusEndsAt: Long,
        now: Long,
        anyScheduleActive: Boolean,
        lockdownActive: Boolean = false,
    ): Boolean = lockdownActive ||
        focusEndsAt > now ||
        (masterEnabled && (alwaysOn || anyScheduleActive))
}
