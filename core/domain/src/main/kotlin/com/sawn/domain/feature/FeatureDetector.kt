package com.sawn.domain.feature

/**
 * Minimal, platform-agnostic view of an accessibility node so the detection logic can be
 * unit-tested without an Android AccessibilityNodeInfo.
 */
interface UiNode {
    val viewId: String?
    val text: String?
    val contentDesc: String?
    val childCount: Int
    fun child(index: Int): UiNode?

    /** True while the user is editing this node (address bar being typed into). */
    val isFocused: Boolean get() = false

    /**
     * Block's detection gate (decompiled `kr0.q`): only nodes actually shown to the user
     * may trigger a feature block. Recycled views stay attached to the tree off-screen;
     * without this check, scrolling past a Reels container would block the home feed.
     */
    val isVisibleToUser: Boolean get() = true

    /** True for the active tab; selected-only signals (e.g. Instagram's search_tab) use it. */
    val isSelected: Boolean get() = false

    /** First child matching a fully-qualified resource id, e.g. "com.android.chrome:id/url_bar". */
    fun findByViewId(id: String): UiNode? = null

    /** Presses this node; used by `click:<viewId>` navigate-away actions. */
    fun performClick(): Boolean = false
}

/**
 * Scans a node tree for any of the given [features]. Bounded by [maxNodes] so a deep tree
 * (e.g. a long feed) can't stall the accessibility thread. Returns the first feature whose
 * signals appear on screen, or null.
 */
object FeatureDetector {

    fun detect(features: List<AppFeature>, root: UiNode?, maxNodes: Int = 500): AppFeature? {
        if (root == null || features.isEmpty()) return null
        val stack = ArrayDeque<UiNode>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++
            features.firstOrNull { nodeMatches(it, node) }?.let { return it }
            for (i in 0 until node.childCount) node.child(i)?.let { stack.addLast(it) }
        }
        return null
    }

    fun nodeMatches(feature: AppFeature, node: UiNode): Boolean {
        if (!node.isVisibleToUser) return false
        val vid = node.viewId
        if (vid != null) {
            if (feature.viewIdContains.any { it.isNotBlank() && vid.contains(it, ignoreCase = true) }) return true
            // Always-present tab bar items (clips_tab, search_tab) only count while selected.
            if (node.isSelected &&
                feature.selectedViewIdContains.any { it.isNotBlank() && vid.contains(it, ignoreCase = true) }
            ) {
                return true
            }
        }
        if (feature.textContains.isNotEmpty()) {
            val haystack = listOfNotNull(node.text, node.contentDesc).joinToString(" ")
            if (feature.textContains.any { it.isNotBlank() && haystack.contains(it, ignoreCase = true) }) return true
        }
        return false
    }
}
