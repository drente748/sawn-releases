package com.sawn.domain.feature

enum class SocialPlatform(
    val key: String,
    val label: String,
    val monogram: String,
    val coverage: String,
    val builtInFilterTip: String,
    val packageNames: Set<String>,
) {
    REDDIT(
        key = "reddit",
        label = "Reddit",
        monogram = "r/",
        coverage = "NSFW communities, warnings and adult searches",
        builtInFilterTip = "Account settings → turn off Show mature content",
        packageNames = setOf("com.reddit.frontpage"),
    ),
    FACEBOOK(
        key = "facebook",
        label = "Facebook",
        monogram = "f",
        coverage = "Sensitive-content screens and adult searches",
        builtInFilterTip = "Settings → Audience and visibility → Sensitive Content Control → Less",
        packageNames = setOf("com.facebook.katana", "com.facebook.lite"),
    ),
    INSTAGRAM(
        key = "instagram",
        label = "Instagram",
        monogram = "ig",
        coverage = "Sensitive-content screens and adult searches",
        builtInFilterTip = "Settings → Suggested content → Sensitive Content Control → Less",
        packageNames = setOf("com.instagram.android", "com.instagram.lite"),
    ),
    X(
        key = "x",
        label = "X / Twitter",
        monogram = "x",
        coverage = "Sensitive-media screens and adult searches",
        builtInFilterTip = "Privacy and safety → Content you see → hide sensitive media and use safe search",
        packageNames = setOf("com.twitter.android", "com.x.android"),
    ),
    TIKTOK(
        key = "tiktok",
        label = "TikTok",
        monogram = "tt",
        coverage = "Restricted-content screens and adult searches",
        builtInFilterTip = "Settings → Content preferences → Restricted Mode and keyword filters",
        packageNames = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
    ),
    YOUTUBE(
        key = "youtube",
        label = "YouTube",
        monogram = "yt",
        coverage = "Age-restricted screens and adult searches",
        builtInFilterTip = "Settings → General → Restricted Mode",
        packageNames = setOf("com.google.android.youtube"),
    ),
    ;

    companion object {
        val allKeys: Set<String> = entries.mapTo(linkedSetOf()) { it.key }
        private val packageIndex: Map<String, SocialPlatform> = entries
            .flatMap { platform -> platform.packageNames.map { it to platform } }
            .toMap()

        fun byPackage(packageName: String): SocialPlatform? = packageIndex[packageName]
    }
}
