package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBPoliciesTest {

    @Test
    fun nightGuard_windowWithinDay() {
        val start = NightGuardPolicy.toMinutes(22, 0) // 1320
        val end = NightGuardPolicy.toMinutes(6, 0)   // 360
        assertTrue(NightGuardPolicy.isWithinWindow(23 * 60, start, end)) // 23:00 inside
        assertTrue(NightGuardPolicy.isWithinWindow(5 * 60, start, end))  // 05:00 inside
        assertTrue(!NightGuardPolicy.isWithinWindow(12 * 60, start, end)) // 12:00 outside
    }

    @Test
    fun nightGuard_minutesRemainingWraps() {
        assertEquals(120, NightGuardPolicy.minutesRemaining(4 * 60, 6 * 60))
    }

    @Test
    fun challenge_stagesAndCompletion() {
        assertEquals(ChallengeStage.START, ChallengePolicy.stageFor(1))
        assertEquals(ChallengeStage.WEEK, ChallengePolicy.stageFor(7))
        assertEquals(ChallengeStage.VICTORY, ChallengePolicy.stageFor(30))
        assertEquals(null, ChallengePolicy.stageFor(0))
        assertTrue(ChallengePolicy.isComplete(30))
        assertEquals(23, ChallengePolicy.daysRemaining(7))
    }

    @Test
    fun triggerInsights_topTagAndPeakHour() {
        val logs = listOf(
            TriggerLog(TriggerTag.STRESS, 22),
            TriggerLog(TriggerTag.STRESS, 23),
            TriggerLog(TriggerTag.BOREDOM, 21),
        )
        val insights = TriggerInsights.analyze(logs)
        assertEquals(TriggerTag.STRESS, insights.topTag)
        assertEquals(22, insights.peakHour) // أول ذروة مرسومة (earliest tied by group order)
        assertEquals(3, insights.totalLogs)
    }

    @Test
    fun triggerInsights_empty() {
        val insights: TriggerInsightsSummary = TriggerInsights.analyze(emptyList())
        assertNull(insights.topTag)
        assertEquals(0, insights.totalLogs)
    }

    @Test
    fun backupCrypto_roundTrip() {
        val payload = "{\"name\":\"ahmad\",\"streak\":5}"
        val enc = BackupCrypto.encrypt(payload, "correct horse battery staple")
        val dec = BackupCrypto.decrypt(enc, "correct horse battery staple")
        assertEquals(payload, dec)
    }

    @Test
    fun backupCrypto_tamperFails() {
        val enc = BackupCrypto.encrypt("hello", "pass")
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0xFF).toByte()
        assertNull(BackupCrypto.decrypt(enc, "pass"))
    }

    @Test
    fun backupCrypto_wrongPassphraseFails() {
        val enc = BackupCrypto.encrypt("secret", "right")
        assertNull(BackupCrypto.decrypt(enc, "wrong"))
    }

    @Test
    fun deepFocus_defaults() {
        assertEquals(10, DeepFocusPolicy.UNLOCK_DELAY_SECONDS)
        assertTrue(DeepFocusPolicy.isAllowed("com.google.android.apps.maps", DeepFocusPolicy.DEFAULT_WHITELIST))
        assertTrue(!DeepFocusPolicy.isAllowed("com.example.game", DeepFocusPolicy.DEFAULT_WHITELIST))
    }

    @Test
    fun parental_managementLockedOnlyWhenPinPresent() {
        assertTrue(ParentalModePolicy.isManagementLocked(true, true))
        assertTrue(!ParentalModePolicy.isManagementLocked(false, true))
        assertTrue(!ParentalModePolicy.isManagementLocked(true, false))
        assertTrue(ParentalModePolicy.REQUIRES_DEVICE_ADMIN)
    }
}
