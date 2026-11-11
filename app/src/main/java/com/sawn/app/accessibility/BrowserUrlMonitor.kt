package com.sawn.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import com.sawn.domain.block.AdultHosts
import com.sawn.domain.block.DomainMatcher
import com.sawn.domain.block.web.BrowserConfig
import com.sawn.domain.block.web.WebUrlExtractor
import com.sawn.app.sawnContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reads the browser address bar — by its view id, not by guessing — and intercepts
 * adult-domain navigation that escaped the DNS filter (DoH-using browsers, raw-IP entry
 * via a proxy, cached DNS).
 *
 * Address-bar engine derived from a reverse-engineering pass on a mature blocker: each
 * known browser ships an exact view-id list for its URL bar ([BrowserConfig], 116
 * browsers), a raw-tree fallback covers Firefox-family toolbars, and the whole-string
 * [com.sawn.domain.block.web.WebAddressPattern] gate rejects search queries so an
 * address bar reading "cats" is never a domain. Browsers without a config entry keep the
 * old heuristic text scan.
 *
 * Behavior details taken from Block: a focused bar means the user is typing and is
 * never read ([WebUrlExtractor]); repeated events for the same package are throttled;
 * traversals are bounded; every failure is contained.
 *
 * Privacy: the URL text is inspected in memory only; nothing is stored, logged, or
 * sent. It never records the URL — only a boolean decision against the static list.
 */
class BrowserUrlMonitor(private val service: AccessibilityService) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "Browser URL scan failed: ${error.javaClass.simpleName}")
        },
    )

    /** Heuristic fallback for browsers with no [BrowserConfig] entry (OEM builds etc). */
    private val HEURISTIC_BROWSERS = setOf(
        "com.android.browser", "org.chromium.webview_shell",
        "com.opera.touch", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.focus", "com.sec.android.app.sbrowser.beta",
        "com.UCMobile.intl", "com.UCMobile", "com.privatebrowser.tab",
        "com.bookmark.reed",
    )

    @Volatile var lastInterceptedHost: String? = null
        private set

    /** One URL-bar read per package per [SCAN_INTERVAL_MS] (Block's event throttle). */
    private val lastScanAt = HashMap<String, Long>()

    /** A job tearing down (destroy) must not receive an emit that was racing it. */
    @Volatile private var destroyed = false

    /**
     * Returns true when an adult URL was on screen and the intercept was launched.
     * Called from the accessibility hot path, so it launches its own coroutine.
     */
    fun onWindowChanged(pkg: String, rootNodeFactory: () -> com.sawn.domain.feature.UiNode?) {
        val known = BrowserConfig.byPackage(pkg) != null
        if (!known && pkg !in HEURISTIC_BROWSERS) {
            clearLastIntercepted()
            return
        }
        if (!shouldScan(pkg)) return
        if (destroyed) return

        scope.launch {
            try {
                val container = service.sawnContainer
                val active = runCatching { container.blockEngine.isBlockingActiveNow(peekOnly = true) }
                    .getOrDefault(false)
                if (!active) {
                    clearLastIntercepted()
                    return@launch
                }
                val guard = container.settings.pornGuard.first()
                if (!guard) {
                    clearLastIntercepted()
                    return@launch
                }
                val extra = container.settings.adultExtraDomains.first()

                // Snapshot the visible text once per window event; never store it.
                val url = readAddressBar(pkg, rootNodeFactory())
                val host = DomainMatcher.normalizeUrl(url)
                val matched = if (host != null && matchesAdult(host, extra)) host else null
                if (matched == null) {
                    clearLastIntercepted()
                    return@launch
                }
                if (matched == lastInterceptedHost) return@launch
                lastInterceptedHost = matched
                runCatching { InterceptActivityBridge.launchAdultIntercept(service, matched) }
                    .onFailure {
                        lastInterceptedHost = null
                        Log.w(TAG, "Adult intercept launch failed: ${it.javaClass.simpleName}")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Browser URL scan failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /** True when [host] hits any adult surface (core, reddit-media, user extras). */
    private fun matchesAdult(host: String, extra: Set<String>): Boolean =
        DomainMatcher.matches(host, AdultHosts.ALL) ||
            DomainMatcher.matches(host, AdultHosts.REDDIT_MEDIA.toSet()) ||
            DomainMatcher.matches(host, extra)

    /**
     * Block's two-phase address-bar read: exact view ids first (in Block's own
     * candidate order — Chrome lists "origin" before "url_bar"), then the raw-tree
     * fallback for browsers whose toolbar node is only reachable by traversal.
     */
    private fun readAddressBar(
        pkg: String,
        root: com.sawn.domain.feature.UiNode?,
    ): String? {
        if (root == null) return null
        val config = BrowserConfig.byPackage(pkg)
        if (config == null) {
            // No known bar id — legacy heuristic text scan over the whole tree.
            val texts = ArrayList<String>(64)
            runNodeScan(root) { texts.add(it) }
            return texts.firstNotNullOfOrNull { text ->
                DomainMatcher.normalizeUrl(text)?.let { host ->
                    if (DomainMatcher.matches(host, AdultHosts.ALL) ||
                        DomainMatcher.matches(host, AdultHosts.REDDIT_MEDIA.toSet())
                    ) text else null
                }
            }
        }
        for (id in config.qualifiedViewIds) {
            val node = runCatching { root.findByViewId(id) }.getOrNull() ?: continue
            WebUrlExtractor.extract(node.text, node.contentDesc, node.isFocused)
                ?.let { return it }
        }
        if (config.fallbackTraversal) {
            val node = findByTraversal(root, config.viewIdSet)
            if (node != null) {
                WebUrlExtractor.extract(node.text, node.contentDesc, node.isFocused)
                    ?.let { return it }
            }
        }
        return null
    }

    /** Bounded BFS (Block aborts past 2000 nodes) for a node whose viewId is in [ids]. */
    private fun findByTraversal(
        root: com.sawn.domain.feature.UiNode,
        ids: Set<String>,
    ): com.sawn.domain.feature.UiNode? {
        val stack = ArrayDeque<com.sawn.domain.feature.UiNode>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (++visited > MAX_TRAVERSAL_NODES) {
                Log.w(TAG, "Accessibility tree exceeds $MAX_TRAVERSAL_NODES nodes, aborting")
                return null
            }
            if (node.viewId != null && node.viewId in ids) return node
            for (i in 0 until node.childCount) node.child(i)?.let { stack.addLast(it) }
        }
        return null
    }

    private fun shouldScan(pkg: String): Boolean {
        val now = SystemClock.uptimeMillis()
        synchronized(lastScanAt) {
            val last = lastScanAt[pkg] ?: 0L
            if (now - last < SCAN_INTERVAL_MS) return false
            lastScanAt[pkg] = now
        }
        return true
    }

    private inline fun runNodeScan(
        root: com.sawn.domain.feature.UiNode?,
        onText: (String) -> Unit,
    ) {
        if (root == null) return
        val stack = ArrayDeque<com.sawn.domain.feature.UiNode>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            node.text?.let { if (it.length <= MAX_LEN) onText(it) }
            node.contentDesc?.let { if (it.length <= MAX_LEN) onText(it) }
            for (i in 0 until node.childCount) node.child(i)?.let { stack.addLast(it) }
        }
    }

    fun clearLastIntercepted() { lastInterceptedHost = null }

    fun destroy() {
        destroyed = true
        scope.cancel()
        synchronized(lastScanAt) { lastScanAt.clear() }
    }

    private companion object {
        const val TAG = "SawnUrl"
        const val MAX_NODES = 300
        const val MAX_TRAVERSAL_NODES = 2000
        const val MAX_LEN = 2048
        const val SCAN_INTERVAL_MS = 500L
    }
}
