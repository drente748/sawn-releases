package com.sawn.domain.feature

import java.text.Normalizer
import java.util.Locale

data class SocialContentHit(
    val platform: SocialPlatform,
    val reason: Reason,
    val target: String? = null,
) {
    enum class Reason { NSFW_SCREEN, ADULT_COMMUNITY, ADULT_SEARCH }

    val dedupKey: String = "${platform.key}:${reason.name}:${target.orEmpty()}"
}

object SocialContentDetector {

    fun detect(
        platform: SocialPlatform,
        root: UiNode?,
        maxNodes: Int = 500,
    ): SocialContentHit? {
        if (root == null) return null
        val stack = ArrayDeque<UiNode>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++

            val viewId = normalize(node.viewId)
            if (viewId != null && ADULT_VIEW_ID_SIGNALS.any(viewId::contains)) {
                return SocialContentHit(platform, SocialContentHit.Reason.NSFW_SCREEN)
            }
            val searchNode = viewId != null && SEARCH_VIEW_ID_SIGNALS.any(viewId::contains)
            for (raw in listOfNotNull(node.text, node.contentDesc)) {
                val text = normalize(raw) ?: continue
                if (platform == SocialPlatform.REDDIT) {
                    for (match in SUBREDDIT_TOKEN.findAll(text)) {
                        val name = match.groupValues[1]
                        if (name in ADULT_SUBREDDITS) {
                            return SocialContentHit(platform, SocialContentHit.Reason.ADULT_COMMUNITY, name)
                        }
                    }
                }
                if (searchNode && EXPLICIT_SEARCH_PATTERNS.any { it.containsMatchIn(text) }) {
                    return SocialContentHit(platform, SocialContentHit.Reason.ADULT_SEARCH)
                }
                if (NSFW_TOKEN.containsMatchIn(text) || ADULT_AGE_TOKEN.containsMatchIn(text) ||
                    WARNING_PHRASES.any(text::contains)
                ) {
                    return SocialContentHit(platform, SocialContentHit.Reason.NSFW_SCREEN)
                }
            }
            for (i in 0 until node.childCount) node.child(i)?.let { stack.addLast(it) }
        }
        return null
    }

    private fun normalize(value: String?): String? = value
        ?.take(MAX_TEXT_LENGTH)
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.lowercase(Locale.ROOT)
        ?.replace(WHITESPACE, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
    private val NSFW_TOKEN = Regex("(?<![a-z0-9])nsfw(?![a-z0-9])")
    private val ADULT_AGE_TOKEN = Regex("(?<![0-9])18\\+(?![0-9])")
    private val SUBREDDIT_TOKEN = Regex("(?<![a-z0-9_])r/([a-z0-9_]+)")

    private val ADULT_VIEW_ID_SIGNALS = listOf(
        "nsfw", "adult_content", "sensitive_content", "age_gate", "age_restrict",
    )
    private val SEARCH_VIEW_ID_SIGNALS = listOf("search", "query")

    private val WARNING_PHRASES = listOf(
        "not safe for work",
        "adult content",
        "sensitive content",
        "sensitive media",
        "may contain sensitive",
        "may include adult",
        "mature content",
        "explicit content",
        "pornographic content",
        "18+ community",
        "18+ only",
        "age-restricted content",
        "age restricted content",
        "this video is age-restricted",
        "this content is age-restricted",
        "محتوى للبالغين",
        "محتوى حساس",
        "وسائط حساسة",
        "محتوى جنسي",
        "محتوى إباحي",
        "محتوى اباحي",
        "للبالغين فقط",
        "مجتمع للبالغين",
        "غير مناسب للعمل",
        "مقيد بالعمر",
        "مقيد حسب العمر",
        "قد يحتوي على محتوى حساس",
        "قد يتضمن محتوى للبالغين",
    )

    private val EXPLICIT_SEARCH_TERMS = listOf(
        "porn", "porno", "pornography", "pornhub", "xvideos", "xnxx", "xhamster", "onlyfans",
        "nude", "nudes", "naked", "hentai", "rule34", "gonewild", "xxx", "nsfw",
        "sex video", "sex videos", "sex tape", "سكس", "اباحي", "إباحي",
        "افلام سكس", "أفلام سكس", "صور سكس", "عاري", "عارية", "هنتاي",
    )

    private val EXPLICIT_SEARCH_PATTERNS = EXPLICIT_SEARCH_TERMS.map { term ->
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])")
    }

    private val ADULT_SUBREDDITS = setOf(
        "nsfw", "nsfw_gif", "nsfw_gifs", "nsfwhardcore", "porn", "pornvids",
        "porninfifteenseconds", "pornhub", "gonewild", "gonewild18", "gonewildaudio",
        "realgirls", "girlsfinishingthejob", "rule34", "rule34lol", "hentai",
        "hentaigif", "nudes", "nude", "naked", "milf", "anal", "blowjob",
        "blowjobs", "cumsluts", "cumshot", "creampie", "collegesluts", "asstastic",
        "ass", "boobs", "tits", "bustypetite", "pawg", "dirtypenpals",
        "eroticliterature", "gonewildstories", "sexsells", "onlyfans101", "redgifs",
    )

    private const val MAX_TEXT_LENGTH = 2048
}
