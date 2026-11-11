package com.sawn.domain.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards injected navigate-away actions. `isForeground()` is deliberately lenient for
 * whole-app blocks (a transient null root must not skip a deserved intercept), but a
 * global BACK/HOME or a synthetic click must never be injected on that leniency: if the
 * live root is unknown or belongs to another app, the action is skipped.
 */
class NavigateAwayGuardTest {

    @Test
    fun `same package allows injection`() {
        assertTrue(NavigateAwayGuard.canInject("com.instagram.android", "com.instagram.android"))
    }

    @Test
    fun `missing live root blocks injection`() {
        assertFalse(NavigateAwayGuard.canInject("com.instagram.android", null))
    }

    @Test
    fun `another app blocks injection`() {
        assertFalse(NavigateAwayGuard.canInject("com.instagram.android", "com.android.chrome"))
    }

    @Test
    fun `blank live package blocks injection`() {
        assertFalse(NavigateAwayGuard.canInject("com.instagram.android", ""))
    }
}
