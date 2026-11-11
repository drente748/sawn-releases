package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class SocialFakeNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDesc: String? = null,
    val children: List<SocialFakeNode> = emptyList(),
) : UiNode {
    override val childCount get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
}

class SocialContentDetectorTest {

    @Test fun mapsOfficialAndLitePackages() {
        assertEquals(SocialPlatform.REDDIT, SocialPlatform.byPackage("com.reddit.frontpage"))
        assertEquals(SocialPlatform.FACEBOOK, SocialPlatform.byPackage("com.facebook.lite"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialPlatform.byPackage("com.instagram.lite"))
        assertEquals(SocialPlatform.TIKTOK, SocialPlatform.byPackage("com.ss.android.ugc.trill"))
        assertNull(SocialPlatform.byPackage("com.example.unrelated"))
    }

    @Test fun detectsKnownAdultSubredditDeepInTree() {
        val root = SocialFakeNode(
            children = listOf(
                SocialFakeNode(text = "Popular communities"),
                SocialFakeNode(children = listOf(SocialFakeNode(text = "r/gonewild • 2h"))),
            ),
        )
        val hit = SocialContentDetector.detect(SocialPlatform.REDDIT, root)
        assertEquals(SocialContentHit.Reason.ADULT_COMMUNITY, hit?.reason)
        assertEquals("gonewild", hit?.target)
    }

    @Test fun allowsRecoveryAndOrdinarySubreddits() {
        val root = SocialFakeNode(
            children = listOf(
                SocialFakeNode(text = "r/NoFap"),
                SocialFakeNode(text = "https://reddit.com/r/programming"),
            ),
        )
        assertNull(SocialContentDetector.detect(SocialPlatform.REDDIT, root))
    }

    @Test fun detectsNsfwBadgeWithoutDependingOnLanguage() {
        val root = SocialFakeNode(viewId = "com.reddit.frontpage:id/nsfw_badge")
        val hit = SocialContentDetector.detect(SocialPlatform.REDDIT, root)
        assertEquals(SocialContentHit.Reason.NSFW_SCREEN, hit?.reason)
    }

    @Test fun detectsEnglishAndArabicSensitiveWarnings() {
        val english = SocialFakeNode(text = "This media may contain sensitive material")
        val arabic = SocialFakeNode(contentDesc = "تحذير: محتوى حساس")
        assertEquals(
            SocialContentHit.Reason.NSFW_SCREEN,
            SocialContentDetector.detect(SocialPlatform.X, english)?.reason,
        )
        assertEquals(
            SocialContentHit.Reason.NSFW_SCREEN,
            SocialContentDetector.detect(SocialPlatform.INSTAGRAM, arabic)?.reason,
        )
    }

    @Test fun detectsExplicitSearchOnlyInsideSearchField() {
        val search = SocialFakeNode(viewId = "com.instagram.android:id/search_src_text", text = "pornhub")
        val ordinaryText = SocialFakeNode(text = "A documentary discussing pornography and its effects")
        assertEquals(
            SocialContentHit.Reason.ADULT_SEARCH,
            SocialContentDetector.detect(SocialPlatform.INSTAGRAM, search)?.reason,
        )
        assertNull(SocialContentDetector.detect(SocialPlatform.INSTAGRAM, ordinaryText))
    }

    @Test fun allowsEducationalSearchAndPartialWords() {
        val education = SocialFakeNode(viewId = "com.google.android.youtube:id/search_edit_text", text = "sex education")
        val partial = SocialFakeNode(viewId = "com.twitter.android:id/query", text = "sexton")
        assertNull(SocialContentDetector.detect(SocialPlatform.YOUTUBE, education))
        assertNull(SocialContentDetector.detect(SocialPlatform.X, partial))
    }

    @Test fun detectsArabicExplicitSearch() {
        val search = SocialFakeNode(viewId = "com.zhiliaoapp.musically:id/search_view", text = "صور سكس")
        assertEquals(
            SocialContentHit.Reason.ADULT_SEARCH,
            SocialContentDetector.detect(SocialPlatform.TIKTOK, search)?.reason,
        )
    }

    @Test fun detectsYoutubeAgeRestrictionAndAdultBadge() {
        val restricted = SocialFakeNode(text = "This video is age-restricted content")
        val badge = SocialFakeNode(text = "18+")
        assertEquals(
            SocialContentHit.Reason.NSFW_SCREEN,
            SocialContentDetector.detect(SocialPlatform.YOUTUBE, restricted)?.reason,
        )
        assertEquals(
            SocialContentHit.Reason.NSFW_SCREEN,
            SocialContentDetector.detect(SocialPlatform.FACEBOOK, badge)?.reason,
        )
    }

    @Test fun normalizesFullWidthSearchText() {
        val search = SocialFakeNode(viewId = "com.twitter.android:id/search_query", text = "ＰＯＲＮ")
        assertEquals(
            SocialContentHit.Reason.ADULT_SEARCH,
            SocialContentDetector.detect(SocialPlatform.X, search)?.reason,
        )
    }

    @Test fun respectsNodeBudget() {
        var node = SocialFakeNode(text = "NSFW")
        repeat(50) { node = SocialFakeNode(text = "filler", children = listOf(node)) }
        assertNull(SocialContentDetector.detect(SocialPlatform.REDDIT, node, maxNodes = 10))
    }
}
