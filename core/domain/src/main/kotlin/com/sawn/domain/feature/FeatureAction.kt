package com.sawn.domain.feature

/**
 * One UI action from an [AppFeature.navigateAwayTo] DSL entry.
 *
 * Ported from Block 11.3.0's feature registry (`mp` / `f42`), where every built-in
 * feature ships `global_action:navigate_back`; the decompiled model documents
 * `global_action:home` and `click:<viewId>` as the extension points. Sawn executes
 * these before showing the intercept screen so the blocked feed is actually left
 * instead of remaining visible behind the overlay.
 */
sealed interface FeatureAction {

    /** Leave the current screen (the normal way out of Reels/Shorts/Spotlight). */
    data object NavigateBack : FeatureAction

    /** Leave the app entirely (reserved for features with no in-app exit). */
    data object GoHome : FeatureAction

    /** Press a specific control, e.g. a "close" button, by view id. */
    data class Click(val viewId: String) : FeatureAction

    companion object {
        private const val GLOBAL_ACTION_PREFIX = "global_action:"
        private const val CLICK_PREFIX = "click:"

        /** A malformed remote list must not fire an unbounded run of global actions. */
        private const val MAX_ACTIONS = 3

        /**
         * Parses one command, or returns null for anything unknown/malformed. Unknown
         * server-provided commands must never crash the accessibility scan: a future
         * DSL verb shipped ahead of the app simply does nothing until supported.
         * Nullable input guards against a JSON list containing null.
         */
        fun parse(command: String?): FeatureAction? {
            val trimmed = command?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.startsWith(GLOBAL_ACTION_PREFIX) -> when (
                    trimmed.removePrefix(GLOBAL_ACTION_PREFIX).trim().lowercase()
                ) {
                    "navigate_back" -> NavigateBack
                    "home" -> GoHome
                    else -> null
                }
                trimmed.startsWith(CLICK_PREFIX) ->
                    trimmed.removePrefix(CLICK_PREFIX).trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let(::Click)
                else -> null
            }
        }

        /** Parses a command list in order, dropping unsupported entries and capping length. */
        fun parseAll(commands: List<String?>): List<FeatureAction> =
            commands.asSequence()
                .mapNotNull(::parse)
                .take(MAX_ACTIONS)
                .toList()
    }
}
