package com.sawn.domain.block

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingPolicyTest {
    private val now = 1_000_000_000L

    @Test fun focusOverridesMasterOff() =
        assertTrue(BlockingPolicy.isActive(false, false, now + 60_000, now, false))

    @Test fun expiredFocusDoesNotOverride() =
        assertFalse(BlockingPolicy.isActive(false, false, now - 1, now, false))

    @Test fun masterOffIgnoresSchedule() =
        assertFalse(BlockingPolicy.isActive(false, false, 0L, now, true))

    @Test fun alwaysOnWithMaster() =
        assertTrue(BlockingPolicy.isActive(true, true, 0L, now, false))

    @Test fun activeScheduleWithMaster() =
        assertTrue(BlockingPolicy.isActive(true, false, 0L, now, true))

    @Test fun idleWhenNothingActive() =
        assertFalse(BlockingPolicy.isActive(true, false, 0L, now, false))

    @Test fun lockdownForcesBlockingWithoutMasterOrSchedule() =
        assertTrue(BlockingPolicy.isActive(false, false, 0L, now, false, lockdownActive = true))
}
