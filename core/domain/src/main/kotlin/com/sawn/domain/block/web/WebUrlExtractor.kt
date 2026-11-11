package com.sawn.domain.block.web

/**
 * Pure URL-bar extraction: the address-bar reader derived from a reverse-engineering
 * pass on a mature blocker's accessibility engine. No Android types —
 * fully unit-testable.
 *
 * Semantics (kept identical to the engine it was derived from):
 *  - A *focused* URL bar means the user is typing; extraction bails so we
 *    never block mid-typing and never read a half-finished query as a domain.
 *  - [text] is trusted as-is (browsers keep the URL in the bar text), passed
 *    through the whole-string [WebAddressPattern] gate.
 *  - [contentDesc] is scanned for the first `https?://\S+` and, failing that,
 *    the first bare domain, trimmed at sentence punctuation — this covers
 *    Firefox-family toolbars that keep the URL only in the content
 *    description. Every candidate must still pass the whole-string gate, so
 *    fragments inside prose can never match.
 */
object WebUrlExtractor {

    private val HTTPS_URL = Regex("https?://\\S+")

    /** Bare-domain fallback: "example.com/path" without scheme, as users type it. */
    private val BARE_DOMAIN = Regex(
        "[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+(/\\S*)?",
    )

    private val SENTENCE_ENDS = charArrayOf('.', ',', ';', '!', '?')

    fun extract(text: String?, contentDesc: String?, isFocused: Boolean): String? {
        if (isFocused) return null // TYPING — never read while the user edits the bar

        text?.trim()?.takeIf { it.isNotEmpty() }?.let { candidate ->
            if (WebAddressPattern.matches(candidate)) return candidate
        }

        contentDesc?.let { desc ->
            HTTPS_URL.find(desc)?.value?.trimEnd(*SENTENCE_ENDS)?.let { candidate ->
                if (WebAddressPattern.matches(candidate)) return candidate
            }
            BARE_DOMAIN.find(desc)?.value?.trimEnd(*SENTENCE_ENDS)?.let { candidate ->
                if (WebAddressPattern.matches(candidate)) return candidate
            }
        }
        return null
    }

    /** Convenience for a node already located via its view id. */
    fun extractFrom(
        text: String?,
        contentDesc: String?,
        isFocused: Boolean,
    ): String? = extract(text, contentDesc, isFocused)
}
