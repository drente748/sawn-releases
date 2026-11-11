package com.sawn.domain.block

/** Pure strict-mode rules shared by the app, the tile, and focus logic. */
object StrictPolicy {

    /**
     * The master switch is locked while strict mode is on and protection is running, or while
     * a strict focus session is still live. A non-strict focus can be ended deliberately from
     * the focus card, so it does not lock the master switch.
     */
    fun isMasterLocked(
        strictMode: Boolean,
        masterEnabled: Boolean,
        focusStrict: Boolean = false,
        focusEndsAt: Long = 0L,
        now: Long = 0L,
    ): Boolean = (strictMode && masterEnabled) || (focusStrict && focusEndsAt > now)

    /** Any live focus session means the tunnel must keep running, even if the master is off. */
    fun isFocusActive(focusEndsAt: Long, now: Long): Boolean = focusEndsAt > now

    fun effectiveFocusStrict(globalStrictMode: Boolean, requestedStrict: Boolean): Boolean =
        globalStrictMode || requestedStrict

    fun canCancelFocus(focusStrict: Boolean, focusEndsAt: Long, now: Long): Boolean =
        !focusStrict || focusEndsAt <= now

    /**
     * A stored strict flag without a PIN is legacy/corrupt state: there is no way to
     * authenticate disabling it, so it must not count as strict mode.
     */
    fun isEffectiveStrictMode(storedStrict: Boolean, hasPin: Boolean): Boolean =
        storedStrict && hasPin

    fun canEnableStrictMode(hasPin: Boolean): Boolean = hasPin

    /** Strict mode may not be turned off by deleting the credential that guards it. */
    fun canClearPin(effectiveStrict: Boolean, hasPin: Boolean): Boolean =
        !effectiveStrict || !hasPin
}
