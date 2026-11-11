package com.sawn.domain.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decides whether the feature wall still belongs on screen after navigateAwayTo ran.
 *
 * BACK from a root feed (TikTok For You) can exit the app to the launcher. That is the
 * expected result of the escape action, so the wall must still appear and explain why —
 * otherwise blocking looks like a crash and the user simply reopens the app. A switch to
 * an unrelated user app, a call, or Settings during the settle delay must not be covered.
 */
class FeatureWallPolicyTest {

    @Test
    fun `staying in the blocked package keeps the wall`() {
        assertTrue(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.zhiliaoapp.musically", false))
    }

    @Test
    fun `landing on the launcher keeps the wall`() {
        // BACK from a root feed lands on the launcher, which is a system surface.
        assertTrue(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.google.android.apps.nexuslauncher", true))
    }

    @Test
    fun `landing on system ui keeps the wall`() {
        assertTrue(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.android.systemui", true))
    }

    @Test
    fun `a transient unknown foreground keeps the wall`() {
        assertTrue(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", null, false))
    }

    @Test
    fun `switching to another user app suppresses the wall`() {
        assertFalse(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.android.chrome", false))
    }

    @Test
    fun `landing on a call or settings suppresses the wall`() {
        // These packages are critical for blocking decisions but are not system surfaces;
        // the wall must not cover an incoming call, the dialer, or Settings.
        assertFalse(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.android.dialer", false))
        assertFalse(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.android.settings", false))
        assertFalse(FeatureWallPolicy.shouldShow("com.zhiliaoapp.musically", "com.android.emergency", false))
    }
}
