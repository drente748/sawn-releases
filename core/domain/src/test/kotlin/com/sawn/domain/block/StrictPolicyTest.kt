package com.sawn.domain.block

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictPolicyTest {
    private val now = 1_000_000_000L

    @Test fun strictLocksMasterWhileProtectionOn() =
        assertTrue(StrictPolicy.isMasterLocked(true, true))

    @Test fun strictDoesNotLockWhenProtectionOff() =
        assertFalse(StrictPolicy.isMasterLocked(true, false))

    @Test fun nonStrictNeverLocks() =
        assertFalse(StrictPolicy.isMasterLocked(false, true))

    @Test fun strictFocusLocksMasterWithoutGlobalStrict() =
        assertTrue(StrictPolicy.isMasterLocked(false, true, focusStrict = true, focusEndsAt = now + 60_000, now = now))

    @Test fun expiredStrictFocusDoesNotLockMaster() =
        assertFalse(StrictPolicy.isMasterLocked(false, true, focusStrict = true, focusEndsAt = now - 1, now = now))

    @Test fun nonStrictFocusDoesNotLockMaster() =
        assertFalse(StrictPolicy.isMasterLocked(false, true, focusStrict = false, focusEndsAt = now + 60_000, now = now))

    @Test fun activeFocusBlocksVpnStop() =
        assertTrue(StrictPolicy.isFocusActive(now + 60_000, now))

    @Test fun expiredFocusDoesNotBlockVpnStop() =
        assertFalse(StrictPolicy.isFocusActive(now - 1, now))

    @Test fun globalStrictMakesFocusStrict() =
        assertTrue(StrictPolicy.effectiveFocusStrict(true, false))

    @Test fun strictActiveFocusCannotBeCancelled() =
        assertFalse(StrictPolicy.canCancelFocus(true, now + 60_000, now))

    @Test fun expiredStrictFocusCanBeCleared() =
        assertTrue(StrictPolicy.canCancelFocus(true, now - 1, now))

    @Test fun nonStrictFocusCanBeCancelled() =
        assertTrue(StrictPolicy.canCancelFocus(false, now + 60_000, now))

    @Test fun storedStrictWithoutPinIsNotEffective() =
        assertFalse(StrictPolicy.isEffectiveStrictMode(storedStrict = true, hasPin = false))

    @Test fun storedStrictWithPinIsEffective() =
        assertTrue(StrictPolicy.isEffectiveStrictMode(storedStrict = true, hasPin = true))

    @Test fun strictWithPinCannotClearPin() =
        assertFalse(StrictPolicy.canClearPin(effectiveStrict = true, hasPin = true))

    @Test fun staleStrictWithoutPinCanClearPin() =
        assertTrue(StrictPolicy.canClearPin(effectiveStrict = false, hasPin = false))

    @Test fun nonStrictWithPinCanClearPin() =
        assertTrue(StrictPolicy.canClearPin(effectiveStrict = false, hasPin = true))
}
