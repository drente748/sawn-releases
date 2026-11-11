package com.sawn.domain.feature

/** The default navigate-away verb shipped by every built-in Block feature. */
const val DEFAULT_NAVIGATE_AWAY = "global_action:navigate_back"

/**
 * A blockable slice of a specific app (e.g. Instagram Reels), identified on-screen by
 * accessibility signals: resource-id fragments and/or visible text / content-descriptions.
 *
 * Signal classes, in detection order:
 *  - [viewIdContains] — resource-id fragments that only exist on the blocked screen.
 *  - [selectedViewIdContains] — fragments of always-present tab bar items that count only
 *    while selected (e.g. `clips_tab`, `search_tab`). Without this split, opening the app
 *    home screen would match the Reels tab icon and block the whole app.
 *  - [textContains] — visible text / content descriptions, a looser fallback.
 *
 * [navigateAwayTo] is Block's escape DSL, executed before the intercept screen appears.
 *
 * NOTE: these signals are inherently version-sensitive — app vendors rename view ids and
 * restructure UIs between releases. This is true of every app in this category (ScreenZen
 * et al. ship signal updates too). Keep view ids as the primary signal.
 */
data class AppFeature(
    val key: String,
    val packageName: String,
    val label: String,
    val description: String,
    val viewIdContains: List<String> = emptyList(),
    val selectedViewIdContains: List<String> = emptyList(),
    val textContains: List<String> = emptyList(),
    val navigateAwayTo: List<String> = listOf(DEFAULT_NAVIGATE_AWAY),
)

object FeatureCatalog {

    /** Block 11.3.0 player signals, hardened against Shorts shelves on Home/Subscriptions. */
    private val youtubeShortsSignals = listOf(
        "reel_watch_fragment_root",
        "reel_player_page_container",
        "reel_watch_player",
        "reel_player_underlay",
        "shorts_video_pager",
    )

    /** Existing Sawn signals plus Block's exact `clips_viewer_view_pager`. */
    private val instagramReelsSignals = listOf("clips_viewer", "clips_viewer_view_pager", "reel_viewer")

    /** BlockerHero's screen-exclusive Facebook Reels text (generic feed CTAs are excluded). */
    private val facebookReelsText = listOf("Navigate to your Reels profile")

    val ALL: List<AppFeature> = listOf(
        AppFeature(
            key = "yt_shorts",
            packageName = "com.google.android.youtube",
            label = "YouTube Shorts",
            description = "The vertical Shorts feed",
            viewIdContains = youtubeShortsSignals,
            // "shorts_" also names the permanent bottom-bar tab; only its selected state counts.
            selectedViewIdContains = listOf("shorts_"),
        ),
        AppFeature(
            key = "vanced_youtube_shorts",
            packageName = "com.vanced.android.youtube",
            label = "YouTube Vanced Shorts",
            description = "The Shorts feed in YouTube Vanced",
            viewIdContains = youtubeShortsSignals,
            selectedViewIdContains = listOf("shorts_"),
        ),
        AppFeature(
            key = "revanced_youtube_shorts",
            packageName = "app.revanced.android.youtube",
            label = "YouTube ReVanced Shorts",
            description = "The Shorts feed in YouTube ReVanced",
            viewIdContains = youtubeShortsSignals,
            selectedViewIdContains = listOf("shorts_"),
        ),
        AppFeature(
            key = "ig_reels",
            packageName = "com.instagram.android",
            label = "Instagram Reels",
            description = "The full-screen Reels feed",
            viewIdContains = instagramReelsSignals,
            // clips_tab sits in the bottom bar on every screen; require it to be active.
            selectedViewIdContains = listOf("clips_tab"),
        ),
        AppFeature(
            key = "ig_lite_reels",
            packageName = "com.instagram.lite",
            label = "Instagram Lite Reels",
            description = "The full-screen Reels feed in Instagram Lite",
            viewIdContains = instagramReelsSignals,
            selectedViewIdContains = listOf("clips_tab"),
        ),
        AppFeature(
            key = "ig_explore",
            packageName = "com.instagram.android",
            label = "Instagram Explore",
            description = "The Explore / search grid",
            viewIdContains = listOf("explore_grid", "explore_recycler"),
        ),
        AppFeature(
            key = "ig_search",
            packageName = "com.instagram.android",
            label = "Instagram Search",
            description = "The search screen and its field",
            viewIdContains = listOf("action_bar_search_edit_text", "search_bar_real_field"),
            selectedViewIdContains = listOf("search_tab"),
        ),
        AppFeature(
            key = "ig_lite_search",
            packageName = "com.instagram.lite",
            label = "Instagram Lite Search",
            description = "The search screen and its field in Instagram Lite",
            viewIdContains = listOf("action_bar_search_edit_text", "search_bar_real_field"),
            selectedViewIdContains = listOf("search_tab"),
        ),
        AppFeature(
            key = "fb_reels",
            packageName = "com.facebook.katana",
            label = "Facebook Reels",
            description = "The Reels feed in Facebook",
            viewIdContains = listOf("reel_viewer"),
            // "reels_" also names the permanent tab; require selection there.
            selectedViewIdContains = listOf("reels_"),
            textContains = facebookReelsText,
        ),
        AppFeature(
            key = "fb_lite_reels",
            packageName = "com.facebook.lite",
            label = "Facebook Lite Reels",
            description = "The Reels feed in Facebook Lite",
            viewIdContains = listOf("reel_viewer"),
            selectedViewIdContains = listOf("reels_"),
            textContains = facebookReelsText,
        ),
        AppFeature(
            key = "tiktok_feed",
            packageName = "com.zhiliaoapp.musically",
            label = "TikTok Feed",
            // The shared feed recycler is used by both For You and Following. Matching it
            // unconditionally is intentional: the toggle blocks the video feed, not one tab.
            // Localized tab text ("For You") is not used, and the tab id is selected-only.
            description = "The For You / Following video feed",
            viewIdContains = listOf("feed_recycler"),
            selectedViewIdContains = listOf("for_you"),
        ),
        AppFeature(
            key = "snap_spotlight",
            packageName = "com.snapchat.android",
            label = "Snapchat Spotlight & Stories",
            description = "The Spotlight / Stories feed",
            // Block's exact screen container; the tab icons only count while selected.
            viewIdContains = listOf("spotlight_container"),
            selectedViewIdContains = listOf(
                "ngs_spotlight_icon_container",
                "ngs_community_icon_container",
            ),
        ),
        AppFeature(
            key = "wa_updates",
            packageName = "com.whatsapp",
            label = "WhatsApp Updates",
            description = "The Updates / status feed",
            viewIdContains = listOf("updates_list"),
        ),
        AppFeature(
            key = "wa_business_updates",
            packageName = "com.whatsapp.w4b",
            label = "WhatsApp Business Updates",
            description = "The Updates / status feed in WhatsApp Business",
            viewIdContains = listOf("updates_list"),
        ),
    )

    fun byPackage(packageName: String): List<AppFeature> =
        ALL.filter { it.packageName == packageName }
}
