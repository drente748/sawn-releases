package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the invariants of the in-app feature registry after porting Block's five
 * built-ins and BlockerHero's platform signals. A feature with no detector would
 * silently never fire; a feature whose navigateAwayTo cannot be parsed would show
 * the block screen without first leaving the feed it was meant to close.
 */
class FeatureCatalogTest {

    @Test
    fun `feature keys are unique`() {
        val keys = FeatureCatalog.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every feature has identity and at least one non-blank signal`() {
        FeatureCatalog.ALL.forEach { feature ->
            assertTrue(feature.key.isNotBlank())
            assertTrue(feature.packageName.isNotBlank())
            assertTrue(feature.label.isNotBlank())
            assertTrue(feature.description.isNotBlank())
            assertTrue(
                "no detector signals for ${feature.key}",
                feature.viewIdContains.isNotEmpty() ||
                    feature.selectedViewIdContains.isNotEmpty() ||
                    feature.textContains.isNotEmpty(),
            )
            (feature.viewIdContains + feature.selectedViewIdContains + feature.textContains).forEach { signal ->
                assertTrue("blank signal in ${feature.key}", signal.isNotBlank())
            }
        }
    }

    @Test
    fun `every navigate away command parses`() {
        FeatureCatalog.ALL.forEach { feature ->
            assertTrue("no navigateAwayTo for ${feature.key}", feature.navigateAwayTo.isNotEmpty())
            assertEquals(
                "unparseable navigateAwayTo for ${feature.key}",
                feature.navigateAwayTo.size,
                FeatureAction.parseAll(feature.navigateAwayTo).size,
            )
        }
    }

    @Test
    fun `block built-in features are ported`() {
        assertFeature("yt_shorts", "com.google.android.youtube", "reel_watch_fragment_root")
        assertFeature(
            "revanced_youtube_shorts",
            "app.revanced.android.youtube",
            "reel_watch_fragment_root",
        )
        assertFeature("ig_reels", "com.instagram.android", "clips_viewer_view_pager")
        assertFeature("wa_updates", "com.whatsapp", "updates_list")
        assertFeature("snap_spotlight", "com.snapchat.android", "spotlight_container")

        // reel_recycler also appears in Home/Subscriptions Shorts shelves; using it as an
        // unconditional signal would back-press users out of normal YouTube browsing.
        val youtube = FeatureCatalog.ALL.first { it.key == "yt_shorts" }
        assertFalse("reel_recycler is shelf-prone", "reel_recycler" in youtube.viewIdContains)
        assertTrue("reel_player_page_container" in youtube.viewIdContains)
    }

    @Test
    fun `blockerhero platform signals are ported`() {
        val reels = FeatureCatalog.ALL.first { it.key == "ig_reels" }
        assertTrue("clips_tab" in reels.selectedViewIdContains)
        assertFalse(
            "clips_tab is a permanent tab bar item and must require selection",
            "clips_tab" in reels.viewIdContains,
        )

        val search = FeatureCatalog.ALL.first { it.key == "ig_search" }
        assertTrue("search_tab" in search.selectedViewIdContains)
        assertTrue("action_bar_search_edit_text" in search.viewIdContains)
        assertTrue("search_bar_real_field" in search.viewIdContains)
        assertFalse(
            "search_tab is a permanent tab bar item and must require selection",
            "search_tab" in search.viewIdContains,
        )

        val snap = FeatureCatalog.ALL.first { it.key == "snap_spotlight" }
        assertTrue("spotlight_container" in snap.viewIdContains)
        assertTrue("ngs_spotlight_icon_container" in snap.selectedViewIdContains)
        assertFalse(
            "the Spotlight icon is a permanent tab bar item and must require selection",
            "ngs_spotlight_icon_container" in snap.viewIdContains,
        )
        assertTrue("ngs_community_icon_container" in snap.selectedViewIdContains)
        assertFalse(
            "the community icon is a permanent tab bar item and must require selection",
            "ngs_community_icon_container" in snap.viewIdContains,
        )

        val fb = FeatureCatalog.ALL.first { it.key == "fb_reels" }
        assertTrue("Navigate to your Reels profile" in fb.textContains)
        // "View reels" also labels ordinary feed CTAs; an unconditional text match would
        // block the Facebook home feed.
        assertFalse("View reels" in fb.textContains)
        assertFalse("FbShortsComposer is a class name, not visible text", "FbShortsComposer" in fb.textContains)
        assertTrue("reels_" in fb.selectedViewIdContains)
        assertFalse(
            "reels_ is a permanent tab bar item and must require selection",
            "reels_" in fb.viewIdContains,
        )
    }

    @Test
    fun `tiktok feature covers the whole feed without localized text`() {
        val tiktok = FeatureCatalog.ALL.first { it.key == "tiktok_feed" }
        assertEquals("TikTok Feed", tiktok.label)
        assertTrue("feed_recycler" in tiktok.viewIdContains)
        assertTrue("for_you" in tiktok.selectedViewIdContains)
        assertTrue(
            "localized tab text must not be an unconditional signal",
            tiktok.textContains.isEmpty(),
        )
    }

    @Test
    fun `all shipped feature keys are present`() {
        val expected = setOf(
            "yt_shorts",
            "vanced_youtube_shorts",
            "revanced_youtube_shorts",
            "ig_reels",
            "ig_lite_reels",
            "ig_explore",
            "ig_search",
            "ig_lite_search",
            "fb_reels",
            "fb_lite_reels",
            "tiktok_feed",
            "snap_spotlight",
            "wa_updates",
            "wa_business_updates",
        )
        assertEquals(expected, FeatureCatalog.ALL.map { it.key }.toSet())
    }

    @Test
    fun `lite and alternative clients are covered`() {
        assertTrue(FeatureCatalog.ALL.any { it.packageName == "com.instagram.lite" })
        assertTrue(FeatureCatalog.ALL.any { it.packageName == "com.facebook.lite" })
        assertTrue(FeatureCatalog.ALL.any { it.packageName == "com.vanced.android.youtube" })
        assertTrue(FeatureCatalog.ALL.any { it.packageName == "com.whatsapp.w4b" })
    }

    private fun assertFeature(key: String, packageName: String, vararg viewIds: String) {
        val feature = FeatureCatalog.ALL.firstOrNull { it.key == key }
        assertTrue("missing feature $key", feature != null)
        assertEquals(packageName, feature!!.packageName)
        viewIds.forEach { id ->
            assertTrue("$key missing view id $id", id in feature.viewIdContains)
        }
        assertEquals(listOf("global_action:navigate_back"), feature.navigateAwayTo)
    }
}
