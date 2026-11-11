package com.sawn.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the launcher and System UI count as surfaces where the feature wall may still
 * appear after BACK. A call, emergency dialer, or Settings screen is a user-facing app
 * in its own right and must never be covered by an intercept.
 */
class CriticalAppsTest {

    @Test
    fun `launcher and system ui are system surfaces`() {
        assertTrue(CriticalApps.isSystemSurface("com.android.systemui", "com.example.launcher"))
        assertTrue(CriticalApps.isSystemSurface("com.example.launcher", "com.example.launcher"))
    }

    @Test
    fun `dialer settings and emergency are not wall surfaces`() {
        assertFalse(CriticalApps.isSystemSurface("com.android.dialer", "com.example.launcher"))
        assertFalse(CriticalApps.isSystemSurface("com.android.settings", "com.example.launcher"))
        assertFalse(CriticalApps.isSystemSurface("com.android.emergency", "com.example.launcher"))
        assertFalse(CriticalApps.isSystemSurface("com.android.server.telecom", "com.example.launcher"))
    }

    @Test
    fun `unknown packages are not system surfaces`() {
        assertFalse(CriticalApps.isSystemSurface(null, "com.example.launcher"))
        assertFalse(CriticalApps.isSystemSurface("", "com.example.launcher"))
        // If the launcher cannot be resolved, suppress rather than cover another app.
        assertFalse(CriticalApps.isSystemSurface("com.example.launcher", null))
    }
}
