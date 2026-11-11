package com.sawn.domain.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageScreeningPolicyTest {

    @Test
    fun nsfwVerdict_aboveThresholdFlags() {
        val labels = mapOf("nude" to 0.9f, "nonnude" to 0.1f)
        assertTrue(ImageScreeningPolicy.isNsfw(labels).screened)
    }

    @Test
    fun cleanImage_belowThresholdAllows() {
        val labels = mapOf("nude" to 0.02f, "nonnude" to 0.98f)
        assertFalse(ImageScreeningPolicy.isNsfw(labels).screened)
    }

    @Test
    fun missingLabels_neverFlags() {
        // empty map → classifier failed / unknown model; must be permissive (no wall)
        assertFalse(ImageScreeningPolicy.isNsfw(emptyMap()).screened)
        assertFalse(ImageScreeningPolicy.isNsfw(mapOf("cat" to 0.9f)).screened)
    }

    @Test
    fun atThreshold_flags() {
        // exactly at threshold is off-by-default true: a confident nude verdict blocks
        val labels = mapOf("nude" to ImageScreeningPolicy.DEFAULT_THRESHOLD)
        assertTrue(ImageScreeningPolicy.isNsfw(labels).screened)
    }

    @Test
    fun belowThreshold_bySmallMargin_allows() {
        val labels = mapOf("nude" to (ImageScreeningPolicy.DEFAULT_THRESHOLD - 0.01f))
        assertFalse(ImageScreeningPolicy.isNsfw(labels).screened)
    }

    @Test
    fun mediaNode_matchesSubclassesAndCustomViews() {
        assertTrue(ImageScreeningPolicy.isMediaNode("android.widget.ImageView"))
        // Modern apps never expose the exact android.widget.ImageView class name; the
        // custom/subclass names below are what Instagram/Facebook/TikTok actually show.
        assertTrue(ImageScreeningPolicy.isMediaNode("com.instagram.common.ui.widget.imageview.IgImageView"))
        assertTrue(ImageScreeningPolicy.isMediaNode("com.facebook.drawee.view.SimpleDraweeView"))
        assertTrue(ImageScreeningPolicy.isMediaNode("androidx.media3.ui.PlayerView"))
        assertTrue(ImageScreeningPolicy.isMediaNode("android.view.TextureView"))
        assertTrue(ImageScreeningPolicy.isMediaNode("android.view.SurfaceView"))
        assertTrue(ImageScreeningPolicy.isMediaNode("com.ss.android.ugc.aweme.base.ui.TextureVideoView"))
    }

    @Test
    fun mediaNode_ignoresNonMediaClasses() {
        assertFalse(ImageScreeningPolicy.isMediaNode(null))
        assertFalse(ImageScreeningPolicy.isMediaNode(""))
        assertFalse(ImageScreeningPolicy.isMediaNode("android.widget.TextView"))
        assertFalse(ImageScreeningPolicy.isMediaNode("android.view.ViewGroup"))
        assertFalse(ImageScreeningPolicy.isMediaNode("android.widget.FrameLayout"))
        assertFalse(ImageScreeningPolicy.isMediaNode("androidx.compose.ui.platform.ComposeView"))
    }

    @Test
    fun topCandidates_largestFirstWithinBudget() {
        val small = ImageScreeningPolicy.CropRect(0, 0, 100, 100)
        val tinyIcon = ImageScreeningPolicy.CropRect(0, 0, 50, 50)
        val big = ImageScreeningPolicy.CropRect(0, 0, 800, 1200)
        val mid = ImageScreeningPolicy.CropRect(0, 0, 400, 400)
        val huge2 = ImageScreeningPolicy.CropRect(0, 0, 700, 1000)
        val huge3 = ImageScreeningPolicy.CropRect(0, 0, 600, 900)
        val huge4 = ImageScreeningPolicy.CropRect(0, 0, 500, 800)

        val selected = ImageScreeningPolicy.selectTopCandidates(
            listOf(small, tinyIcon, big, mid, huge2, huge3, huge4),
            budget = 4,
        )

        // small/tinyIcon are under the min-crop size; the largest four survive, largest first.
        org.junit.Assert.assertEquals(listOf(big, huge2, huge3, huge4), selected)
    }

    @Test
    fun topCandidates_emptyWhenEverythingTooSmall() {
        // 200x200 passes the 150px gate but is under the old 220px one; use 140x140 so
        // the test verifies the size gate regardless of future threshold tuning.
        val tiny = ImageScreeningPolicy.CropRect(0, 0, 140, 140)
        org.junit.Assert.assertTrue(
            ImageScreeningPolicy.selectTopCandidates(listOf(tiny), budget = 4).isEmpty(),
        )
    }
}
