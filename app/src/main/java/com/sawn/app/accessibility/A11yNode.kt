package com.sawn.app.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.sawn.domain.feature.UiNode

/**
 * Adapts an Android AccessibilityNodeInfo to the testable [UiNode] interface.
 *
 * Nodes invalidate while the scanned app is redrawing. A stale node may throw from any
 * getter, so every access is contained here rather than taking the whole process down.
 */
class A11yNode(private val info: AccessibilityNodeInfo?) : UiNode {
    override val viewId: String? get() = runCatching { info?.viewIdResourceName }.getOrNull()
    override val text: String? get() = runCatching { info?.text?.toString() }.getOrNull()
    override val contentDesc: String? get() = runCatching { info?.contentDescription?.toString() }.getOrNull()
    override val childCount: Int get() = runCatching { info?.childCount ?: 0 }.getOrDefault(0)
    override val isFocused: Boolean
        get() = runCatching { info?.isFocused ?: false }.getOrDefault(false)
    override val isVisibleToUser: Boolean
        get() = runCatching { info?.isVisibleToUser ?: false }.getOrDefault(false)
    override val isSelected: Boolean
        get() = runCatching { info?.isSelected ?: false }.getOrDefault(false)
    override fun child(index: Int): UiNode? =
        runCatching { info?.getChild(index)?.let { A11yNode(it) } }.getOrNull()

    /** First visible node matching a fully-qualified id ("pkg:id/name"), as Block reads address bars. */
    override fun findByViewId(id: String): UiNode? = runCatching {
        val matches = info?.findAccessibilityNodeInfosByViewId(id).orEmpty()
        (matches.firstOrNull { it.isVisibleToUser } ?: matches.firstOrNull())
            ?.let { A11yNode(it) }
    }.getOrNull()

    override fun performClick(): Boolean = runCatching {
        info?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }.getOrDefault(false)
}
