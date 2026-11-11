package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionCheckupTest {

    @Test
    fun `every layer failing scores zero`() {
        val result = ProtectionCheckup.evaluate(
            CheckupState(
                accessibility = false,
                filter = false,
                pornGuard = false,
                safeSearch = false,
                encryptedDns = false,
                ads = false,
                notifications = false,
                usage = false,
            ),
        )
        assertEquals(0, result.score)
        assertTrue(result.essentialMissing)
        assertFalse(result.complete)
    }

    @Test
    fun `everything on is a perfect score`() {
        val result = ProtectionCheckup.evaluate(
            CheckupState(
                accessibility = true,
                filter = true,
                pornGuard = true,
                safeSearch = true,
                encryptedDns = true,
                ads = true,
                notifications = true,
                usage = true,
            ),
        )
        assertEquals(100, result.score)
        assertTrue(result.complete)
        assertFalse(result.essentialMissing)
    }

    @Test
    fun `optional layers alone never look ready`() {
        val result = ProtectionCheckup.evaluate(
            CheckupState(
                accessibility = false,
                filter = false,
                pornGuard = true,
                safeSearch = true,
                encryptedDns = true,
                ads = true,
                notifications = true,
                usage = true,
            ),
        )
        assertTrue("essentials missing", result.essentialMissing)
        assertTrue("score below half", result.score < 50)
    }

    @Test
    fun `essentials carry the majority of the score`() {
        val result = ProtectionCheckup.evaluate(
            CheckupState(
                accessibility = true,
                filter = true,
                pornGuard = false,
                safeSearch = false,
                encryptedDns = false,
                ads = false,
                notifications = false,
                usage = false,
            ),
        )
        assertEquals(60, result.score)
    }

    @Test
    fun `preset matching compares every switch`() {
        val maximum = ShieldPresetState(
            pornGuard = true,
            safeSearch = true,
            blockAds = true,
            blockDoh = true,
            blockNotifications = true,
        )
        assertTrue(ShieldPreset.MAXIMUM.matches(maximum))
        assertFalse(ShieldPreset.BALANCED.matches(maximum))
        assertFalse(ShieldPreset.LIGHT.matches(maximum))
    }
}
