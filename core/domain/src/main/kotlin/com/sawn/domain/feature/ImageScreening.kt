package com.sawn.domain.feature

/**
 * Pure decision layer for on-device image screening. The Android-dependent classifier
 * (TFLite) lives in [NsfwClassifier]; this object holds the policy so it stays testable:
 *
 * - Only screens when the platform guard is on and the user selected the platform.
 * - Throttles: never re-classifies the same screen area inside [MIN_INTERVAL_MS] so a
 *   scrolling feed cannot push the model every frame (battery + jank).
 * - Caches verdicts keyed by the node's view-id + scroll position so re-checking an
 *   already-cleared view is free.
 *
 * Sources: model from `nipunru/nsfw-detector-android` (MobileNet AutoML, labels
 * nude/nonnude); decisions gate on confidence ≥ [threshold].
 */
object ImageScreeningPolicy {

    /** Classifier output is "nude" or "nonnude"; we flag when nude ≥ threshold. */
    const val DEFAULT_THRESHOLD = 0.6f

    data class Verdict(val screened: Boolean, val confidence: Float)

    fun isNsfw(labels: Map<String, Float>, threshold: Float = DEFAULT_THRESHOLD): Verdict {
        val nude = labels["nude"] ?: labels["sexual"] ?: labels["porn"] ?: 0f
        return Verdict(screened = nude >= threshold, confidence = nude)
    }

    /** Positive verdicts re-arm after this idle period without a new NSFW screen. */
    const val REARM_MS = 8_000L
    const val MIN_INTERVAL_MS = 750L

    /**
     * Minimum crop side in device px. Premium on recall over precision here: feeds show
     * plenty of ~160-220dp thumbnails, and a too-large gate silently skips them. The
     * classifier, not the size gate, is the real precision filter.
     */
    const val MIN_CROP_PX = 150

    /**
     * Hard budget on classified regions per screenshot, so a feed full of thumbnails
     * cannot push the model every frame.
     */
    const val CANDIDATE_BUDGET = 4
    const val NODE_BUDGET = 250

    /** Simple screen-space rect (device px) of a candidate media region. */
    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        fun area(): Int = width * height
    }

    /**
     * Media classes worth screening. Modern apps almost never use the exact
     * `android.widget.ImageView` class: Instagram uses `IgImageView`, Facebook/Twitter
     * use `SimpleDraweeView`, video is drawn in `TextureView`/`SurfaceView`/player
     * views. Mismatches here are silent misses, so the set is generous — the size
     * gate and the classifier stop false positives.
     */
    private val MEDIA_CLASSES = setOf(
        "android.widget.ImageView",
        "android.view.TextureView",
        "android.view.SurfaceView",
        "androidx.media3.ui.PlayerView",
        "com.facebook.drawee.view.DraweeView",
        "com.facebook.drawee.view.SimpleDraweeView",
        "com.facebook.customview.DraweeView",
        // Instagram
        "com.instagram.common.ui.widget.imageview.IgImageView",
        // TikTok
        "com.ss.android.ugc.aweme.base.ui.TextureVideoView",
        "com.ss.android.ugc.aweme.base.ui.SmoothLoopVideoView",
        // X / Twitter
        "com.twitter.ui.widget.PhotoView",
    )

    /** True when [className] is a media view we should consider screening. Pure function. */
    fun isMediaNode(className: String?): Boolean =
        className != null &&
            MEDIA_CLASSES.any { candidate ->
                className == candidate || className.endsWith("." + candidate.substringAfterLast('.'))
            }

    /**
     * Picks up to [budget] candidates, largest first, dropping any region under
     * [MIN_CROP_PX] on a side. Pure so selection can be unit-tested.
     */
    fun selectTopCandidates(
        candidates: List<CropRect>,
        budget: Int = CANDIDATE_BUDGET,
    ): List<CropRect> = candidates
        .asSequence()
        .filter { it.width >= MIN_CROP_PX && it.height >= MIN_CROP_PX }
        .sortedByDescending { it.area() }
        .take(budget)
        .toList()
}
