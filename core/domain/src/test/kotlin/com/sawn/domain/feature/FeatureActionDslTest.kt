package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigateAwayTo DSL is ported from Block 11.3.0's built-in feature registry
 * (mp/f42): every shipped feature uses "global_action:navigate_back", while
 * "global_action:home" and "click:<viewId>" are the extension points documented
 * in the decompiled model. These tests pin the parser contract the accessibility
 * service relies on: known commands map to typed actions, malformed or unknown
 * commands are ignored instead of crashing the scan.
 */
class FeatureActionDslTest {

    @Test
    fun `parses navigate back`() {
        assertEquals(FeatureAction.NavigateBack, FeatureAction.parse("global_action:navigate_back"))
    }

    @Test
    fun `trims whitespace around commands`() {
        assertEquals(FeatureAction.NavigateBack, FeatureAction.parse("  global_action:navigate_back  "))
    }

    @Test
    fun `parses home`() {
        assertEquals(FeatureAction.GoHome, FeatureAction.parse("global_action:home"))
    }

    @Test
    fun `parses click with qualified view id`() {
        assertEquals(
            FeatureAction.Click("com.instagram.android:id/clips_tab"),
            FeatureAction.parse("click:com.instagram.android:id/clips_tab"),
        )
    }

    @Test
    fun `parses click with bare view id`() {
        assertEquals(FeatureAction.Click("url_bar"), FeatureAction.parse("click: url_bar "))
    }

    @Test
    fun `unknown or malformed commands are ignored`() {
        assertNull(FeatureAction.parse("global_action:rotate"))
        assertNull(FeatureAction.parse("global_action:"))
        assertNull(FeatureAction.parse("swipe:up"))
        assertNull(FeatureAction.parse("click:"))
        assertNull(FeatureAction.parse("click:   "))
        assertNull(FeatureAction.parse(""))
        assertNull(FeatureAction.parse("   "))
    }

    @Test
    fun `parseAll keeps order and drops unknown commands`() {
        val actions = FeatureAction.parseAll(
            listOf(
                "global_action:navigate_back",
                "open:settings",
                "click:com.whatsapp:id/updates_tab",
                "global_action:home",
            ),
        )
        assertEquals(
            listOf(
                FeatureAction.NavigateBack,
                FeatureAction.Click("com.whatsapp:id/updates_tab"),
                FeatureAction.GoHome,
            ),
            actions,
        )
    }

    @Test
    fun `null and blank elements are ignored`() {
        assertEquals(
            listOf(FeatureAction.GoHome),
            FeatureAction.parseAll(listOf(null, " ", "global_action:home")),
        )
    }

    @Test
    fun `action list is capped`() {
        // A malformed remote command list must not fire an unbounded run of BACKs.
        val commands = List(10) { "global_action:navigate_back" }
        assertEquals(3, FeatureAction.parseAll(commands).size)
    }

    @Test
    fun `empty command list parses to nothing`() {
        assertTrue(FeatureAction.parseAll(emptyList()).isEmpty())
    }
}
