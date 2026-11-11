package com.sawn.domain.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRestorePolicyTest {

    private fun state(
        userDisabledProtection: Boolean = false,
        lockdownActive: Boolean = false,
        filterActive: Boolean = false,
    ) = BootRestoreState(userDisabledProtection, lockdownActive, filterActive)

    @Test
    fun liveLockdown_isRestoredWhole_afterReboot() {
        val decision = BootRestorePolicy.decide(state(lockdownActive = true, filterActive = true))
        assertTrue(decision.reassertCoreFlags)
        assertTrue(decision.startFilter)
        assertTrue(decision.recordProtectedDay)
        assertTrue(decision.warnMissingAdmin)
        assertTrue(decision.actsAtAll)
    }

    @Test
    fun liveLockdown_winsEvenIfTheStopFlagWasLeftSet() {
        val decision = BootRestorePolicy.decide(
            state(userDisabledProtection = true, lockdownActive = true, filterActive = true),
        )
        assertTrue(decision.reassertCoreFlags)
        assertTrue(decision.startFilter)
        assertTrue(decision.warnMissingAdmin)
    }

    @Test
    fun realStop_doesNothingOnReboot() {
        val decision = BootRestorePolicy.decide(state(userDisabledProtection = true))
        assertFalse(decision.actsAtAll)
        assertFalse(decision.startFilter)
        assertFalse(decision.recordProtectedDay)
    }

    @Test
    fun ordinaryProtection_restartsFilterWithoutTouchingSwitches() {
        val decision = BootRestorePolicy.decide(state(filterActive = true))
        assertFalse(decision.reassertCoreFlags)
        assertTrue(decision.startFilter)
        assertTrue(decision.recordProtectedDay)
        assertFalse(decision.warnMissingAdmin)
    }

    @Test
    fun idleInstall_staysIdle() {
        val decision = BootRestorePolicy.decide(state())
        assertFalse(decision.actsAtAll)
    }

    @Test
    fun decisionIsStableForRepeatedBoots() {
        val first = BootRestorePolicy.decide(state(lockdownActive = true, filterActive = true))
        val second = BootRestorePolicy.decide(state(lockdownActive = true, filterActive = true))
        assertEquals(first, second)
    }
}
