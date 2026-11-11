package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Simple in-memory node for testing the detector. */
private class FakeNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDesc: String? = null,
    override val isVisibleToUser: Boolean = true,
    override val isSelected: Boolean = false,
    val children: List<FakeNode> = emptyList(),
) : UiNode {
    override val childCount get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
}

class FeatureDetectorTest {

    private val reels = FeatureCatalog.ALL.first { it.key == "ig_reels" }
    private val shorts = FeatureCatalog.ALL.first { it.key == "yt_shorts" }
    private val tiktok = FeatureCatalog.ALL.first { it.key == "tiktok_feed" }
    private val search = FeatureCatalog.ALL.first { it.key == "ig_search" }
    private val fbReels = FeatureCatalog.ALL.first { it.key == "fb_reels" }
    private val spotlight = FeatureCatalog.ALL.first { it.key == "snap_spotlight" }

    @Test fun detectsReelsByViewIdDeepInTree() {
        val tree = FakeNode(
            viewId = "com.instagram.android:id/root",
            children = listOf(
                FakeNode(viewId = "com.instagram.android:id/tab_bar"),
                FakeNode(
                    viewId = "com.instagram.android:id/coordinator",
                    children = listOf(FakeNode(viewId = "com.instagram.android:id/clips_viewer_view_pager")),
                ),
            ),
        )
        assertEquals(reels, FeatureDetector.detect(listOf(reels), tree))
    }

    @Test fun detectsTikTokFeedByRecycler() {
        val tree = FakeNode(children = listOf(FakeNode(viewId = "com.zhiliaoapp.musically:id/feed_recycler")))
        assertEquals(tiktok, FeatureDetector.detect(listOf(tiktok), tree))
    }

    @Test fun tiktokFollowingFeedAlsoBlocks() {
        // The feature is now labelled "TikTok Feed": the shared recycler intentionally
        // covers both For You and Following rather than silently blocking only one.
        val following = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.zhiliaoapp.musically:id/feed_recycler"),
                FakeNode(viewId = "com.zhiliaoapp.musically:id/for_you", isSelected = false),
            ),
        )
        assertEquals(tiktok, FeatureDetector.detect(listOf(tiktok), following))
    }

    @Test fun noFalsePositiveOnRegularFeed() {
        val tree = FakeNode(
            viewId = "com.instagram.android:id/feed_recycler",
            children = listOf(FakeNode(text = "Home"), FakeNode(viewId = "com.instagram.android:id/profile_tab")),
        )
        assertNull(FeatureDetector.detect(listOf(reels, shorts), tree))
    }

    @Test fun respectsMaxNodesBudget() {
        // A long chain where the match is beyond the budget must not be found.
        var node = FakeNode(viewId = "com.instagram.android:id/clips_viewer")
        repeat(50) { node = FakeNode(viewId = "filler", children = listOf(node)) }
        assertNull(FeatureDetector.detect(listOf(reels), node, maxNodes = 10))
    }

    @Test fun invisibleViewIdNodeIsNotDetected() {
        // Block gates every detector hit on isVisibleToUser(): a recycled view that is
        // still attached off-screen must not trigger the block screen.
        val tree = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.instagram.android:id/clips_viewer_view_pager", isVisibleToUser = false),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(reels), tree))
    }

    @Test fun invisibleTextNodeIsNotDetected() {
        val tree = FakeNode(
            children = listOf(
                FakeNode(contentDesc = "Navigate to your Reels profile", isVisibleToUser = false),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(fbReels), tree))
    }

    @Test fun selectedTabSignalOnlyMatchesTheActiveTab() {
        val idle = FakeNode(
            children = listOf(FakeNode(viewId = "com.instagram.android:id/search_tab", isSelected = false)),
        )
        assertNull(FeatureDetector.detect(listOf(search), idle))

        val active = FakeNode(
            children = listOf(FakeNode(viewId = "com.instagram.android:id/search_tab", isSelected = true)),
        )
        assertEquals(search, FeatureDetector.detect(listOf(search), active))
    }

    @Test fun searchFieldMatchesWithoutSelection() {
        // The search field itself only exists on the search screen, so it needs no
        // selected-state gate; only the always-present tab bar item does.
        val tree = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.instagram.android:id/action_bar_search_edit_text"),
            ),
        )
        assertEquals(search, FeatureDetector.detect(listOf(search), tree))
    }

    @Test fun instagramHomeTabsDoNotTriggerReelsOrSearch() {
        // The Reels and Search icons live in the bottom bar on every Instagram screen.
        val tree = FakeNode(
            viewId = "com.instagram.android:id/root",
            children = listOf(
                FakeNode(viewId = "com.instagram.android:id/feed_recycler"),
                FakeNode(viewId = "com.instagram.android:id/clips_tab", isSelected = false),
                FakeNode(viewId = "com.instagram.android:id/search_tab", isSelected = false),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(reels, search), tree))
    }

    @Test fun selectedReelsTabTriggersReels() {
        val tree = FakeNode(
            children = listOf(FakeNode(viewId = "com.instagram.android:id/clips_tab", isSelected = true)),
        )
        assertEquals(reels, FeatureDetector.detect(listOf(reels), tree))
    }

    @Test fun youtubeShortsShelfDoesNotTrigger() {
        // reel_recycler is also used by the horizontal Shorts shelf on Home/Subscriptions;
        // only the immersive player ids may fire.
        val tree = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.google.android.youtube:id/reel_recycler"),
                FakeNode(viewId = "com.google.android.youtube:id/shorts_shelf"),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(shorts), tree))
    }

    @Test fun youtubeShortsPlayerTriggers() {
        val tree = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.google.android.youtube:id/reel_watch_fragment_root"),
            ),
        )
        assertEquals(shorts, FeatureDetector.detect(listOf(shorts), tree))
    }

    @Test fun snapchatTabIconOnlyTriggersWhenSelected() {
        val home = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.snapchat.android:id/ngs_spotlight_icon_container", isSelected = false),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(spotlight), home))

        val active = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.snapchat.android:id/ngs_spotlight_icon_container", isSelected = true),
            ),
        )
        assertEquals(spotlight, FeatureDetector.detect(listOf(spotlight), active))
    }

    @Test fun facebookHomeReelsCtaDoesNotTrigger() {
        // "View reels" also labels ordinary feed cards; only screen-exclusive ids count.
        val tree = FakeNode(children = listOf(FakeNode(text = "View reels")))
        assertNull(FeatureDetector.detect(listOf(fbReels), tree))
    }

    @Test fun facebookReelsTabOnlyTriggersWhenSelected() {
        val home = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.facebook.katana:id/reels_tab", isSelected = false),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(fbReels), home))

        val active = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.facebook.katana:id/reels_tab", isSelected = true),
            ),
        )
        assertEquals(fbReels, FeatureDetector.detect(listOf(fbReels), active))
    }

    @Test fun snapchatCommunityIconOnlyTriggersWhenSelected() {
        val home = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.snapchat.android:id/ngs_community_icon_container", isSelected = false),
            ),
        )
        assertNull(FeatureDetector.detect(listOf(spotlight), home))

        val active = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.snapchat.android:id/ngs_community_icon_container", isSelected = true),
            ),
        )
        assertEquals(spotlight, FeatureDetector.detect(listOf(spotlight), active))
    }

    @Test fun textFieldsDoNotSynthesizeAPhrase() {
        // Text and content-description of one node are separate fields; joining them
        // must not manufacture "Navigate to your Reels profile" across the boundary.
        val tree = FakeNode(children = listOf(FakeNode(text = "Navigate to your", contentDesc = " Reels profile")))
        assertNull(FeatureDetector.detect(listOf(fbReels), tree))
    }
}
