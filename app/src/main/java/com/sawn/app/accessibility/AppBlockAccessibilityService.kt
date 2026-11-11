package com.sawn.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sawn.domain.block.AccessDecision
import com.sawn.domain.block.LimitReason
import com.sawn.domain.feature.AppFeature
import com.sawn.domain.feature.FeatureAction
import com.sawn.domain.feature.FeatureCatalog
import com.sawn.domain.feature.FeatureDetector
import com.sawn.domain.feature.NavigateAwayGuard
import com.sawn.domain.feature.SocialContentDetector
import com.sawn.domain.feature.SocialContentHit
import com.sawn.domain.feature.SocialPlatform
import com.sawn.domain.feature.UiNode
import com.sawn.domain.feature.FeatureWallPolicy
import com.sawn.app.ui.intercept.InterceptActivity
import com.sawn.app.system.GuardHealthNotifier
import com.sawn.app.util.CriticalApps
import com.sawn.app.sawnContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches the foreground app and asks [com.sawn.domain.block.AccessController] what to do,
 * launching [InterceptActivity] for a hard block, limit notice, or mindful pause.
 *
 * It also performs *in-app feature* blocking: while a supported app is foreground, it scans
 * the visible node tree (throttled) for signals of a blocked feature — e.g. Instagram Reels
 * or YouTube Shorts — and intercepts when one is on screen.
 *
 * Privacy: it inspects the foreground package name and, for feature detection, view ids /
 * visible labels of on-screen nodes. Nothing is stored or transmitted.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            // An uncaught coroutine exception used to crash the whole process (and the VPN
            // with it). Fail this one task instead; the next event retries. A crash here
            // often precedes the system tearing the service down, so flag it for onDestroy.
            Log.w(TAG, "Accessibility task failed: ${error.javaClass.simpleName}")
            if (error is Error) isServiceCrashing = true
        },
    )
    @Volatile private var lastPackage: String? = null
    @Volatile private var expiryJob: Job? = null
    @Volatile private var evaluationJob: Job? = null
    @Volatile private var windowGeneration = 0L
    private val urlMonitor = BrowserUrlMonitor(this)
    private val imageScreening = ImageScreeningOrchestrator(this)
    private val scanInFlight = AtomicBoolean(false)

    // Written from onAccessibilityEvent (main thread) and from scan coroutines on
    // Dispatchers.Default — plain fields would let one side see a stale value.
    @Volatile private var lastFeatureScan = 0L
    @Volatile private var interceptedFeatureKey: String? = null
    @Volatile private var interceptedSocialKey: String? = null

    // Cache blocking-active + enabled features so scroll-heavy apps don't hammer DataStore/DB
    // on every content-changed event. Refreshed at most once per CACHE_TTL_MS.
    @Volatile private var cachedBlockingActive = false
    @Volatile private var cachedFeatureKeys: Set<String> = emptySet()
    @Volatile private var cachedSocialGuard = false
    @Volatile private var cachedSocialPlatforms: Set<String> = emptySet()
    @Volatile private var cacheStamp = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            val type = event?.eventType ?: return
            val pkg = event.packageName?.toString() ?: return

            // Do not ignore our own package: InterceptActivity coming and going must update
            // lastPackage so the blocked app is re-evaluated when it returns.
            val isWindowEvent = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            // Some apps only emit a view event when a tab becomes active or a search field
            // opens; selected-tab detection would otherwise miss the transition until the
            // next content change. These events only feed the throttled content scan, never
            // the whole-app block decision.
            val isViewEvent = type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_VIEW_SELECTED
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handleAppBlock(pkg)
            }
            if (isWindowEvent || isViewEvent) {
                maybeScanInAppContent(pkg)
            }
            if (isWindowEvent) {
                urlMonitor.onWindowChanged(pkg) { A11yNode(rootInActiveWindow) }
            }
        } catch (e: Exception) {
            // This is a system callback; throwing out of it would take down the process.
            Log.w(TAG, "Accessibility event failed: ${e.javaClass.simpleName}")
        }
    }

    // --- Whole-app blocking ---
    private fun handleAppBlock(pkg: String) {
        if (pkg == lastPackage) return
        lastPackage = pkg
        windowGeneration++
        expiryJob?.cancel()
        expiryJob = null
        evaluationJob?.cancel()
        evaluationJob = null
        interceptedFeatureKey = null // new app in front; allow feature re-detection
        interceptedSocialKey = null
        // Never lock the user out of their own phone (launcher, settings, dialer, us).
        if (CriticalApps.isCritical(this, pkg)) return
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        // A decision is only valid for the window generation it was requested for. Without
        // this, a slow DB read could launch a second intercept for an app that already left.
        val generation = windowGeneration
        evaluationJob?.cancel()
        evaluationJob = scope.launch {
            val decision = sawnContainer.accessController.decideForPackage(pkg)
            if (generation != windowGeneration || lastPackage != pkg) return@launch
            when (decision) {
                is AccessDecision.Allow -> Unit
                is AccessDecision.AllowUntil -> scheduleExpiryRecheck(pkg, decision.expiresAt)
                is AccessDecision.Pause ->
                    if (isForeground(pkg)) launchIntercept(InterceptActivity.KIND_PAUSE, decision.item.id, null, decision.escalations)
                is AccessDecision.HardBlock ->
                    if (isForeground(pkg)) launchIntercept(InterceptActivity.KIND_BLOCK, decision.item.id, null, null)
                is AccessDecision.LimitReached ->
                    if (isForeground(pkg)) launchIntercept(InterceptActivity.KIND_LIMIT, decision.item.id, decision.reason, null)
                is AccessDecision.DeepFocusBlock ->
                    if (isForeground(pkg)) launchIntercept(InterceptActivity.KIND_DEEP_FOCUS, -1L, null, null, pkg)
            }
        }
    }

    /**
     * Best-effort foreground check. If the accessibility root is temporarily unavailable we
     * fall back to the last window event (already matched against [pkg] by the caller), so a
     * transient null does not silently skip a deserved intercept.
     */
    private fun isForeground(pkg: String): Boolean {
        val rootPkg = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        return rootPkg == null || rootPkg == pkg
    }

    /**
     * A grant is only a loan: when it expires while its app is still foreground, re-run the
     * decision and show the intercept again. A window change cancels this job, so leaving the
     * app never produces a background intercept.
     */
    private fun scheduleExpiryRecheck(pkg: String, expiresAt: Long) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            val remaining = expiresAt - sawnContainer.settings.trustedNow()
            delay(remaining.coerceAtLeast(0L) + EXPIRY_GRACE_MS)
            if (lastPackage == pkg && isForeground(pkg)) evaluate(pkg)
        }
    }

    // --- In-app feature and adult-content blocking ---
    private fun maybeScanInAppContent(pkg: String) {
        val features = FeatureCatalog.byPackage(pkg)
        val socialPlatform = SocialPlatform.byPackage(pkg)
        if (features.isEmpty() && socialPlatform == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastFeatureScan < SCAN_THROTTLE_MS) return
        // A slow scan on a heavy feed must not overlap the next one; overlapping traversals
        // multiply node/string allocation and can get the whole process killed.
        if (!scanInFlight.compareAndSet(false, true)) return
        lastFeatureScan = now

        scope.launch {
            try {
                if (now - cacheStamp > CACHE_TTL_MS) {
                    cachedBlockingActive = sawnContainer.blockEngine.isBlockingActiveNow()
                    cachedFeatureKeys = sawnContainer.settings.enabledFeatureKeys.first()
                    cachedSocialGuard = sawnContainer.settings.socialGuard.first()
                    cachedSocialPlatforms = sawnContainer.settings.enabledSocialPlatforms.first()
                        ?: SocialPlatform.allKeys
                    cacheStamp = now
                }
                if (!cachedBlockingActive) return@launch

                val root = A11yNode(rootInActiveWindow)
                if (socialPlatform != null && cachedSocialGuard && socialPlatform.key in cachedSocialPlatforms) {
                    val rawHit = SocialContentDetector.detect(socialPlatform, root, SOCIAL_MAX_NODES)
                    val socialHit = onSocialScanResult(rawHit, pkg)
                    if (socialHit != null) {
                        interceptedFeatureKey = null
                        // The scan is asynchronous; do not pop an intercept after the user left.
                        if (lastPackage != pkg) return@launch
                        interceptedSocialKey = socialHit.dedupKey
                        launchIntercept(
                            InterceptActivity.KIND_SOCIAL,
                            -1L,
                            null,
                            null,
                            socialPlatform.label,
                        )
                        return@launch
                    } else {
                        // No text-path hit — image screening is the only remaining chance
                        // to catch unlabeled adult media (the "nothing detected" gap).
                        imageScreening.maybeScreen { hit ->
                            if (!hit || lastPackage != pkg) return@maybeScreen
                            // A fresh positive verdict re-arms text dedup as well.
                            interceptedSocialKey = null
                            launchIntercept(
                                InterceptActivity.KIND_SOCIAL,
                                -1L, null, null,
                                socialPlatform.label,
                            )
                        }
                    }
                }
                interceptedSocialKey = null

                val active = features.filter { it.key in cachedFeatureKeys }
                if (active.isEmpty()) {
                    interceptedFeatureKey = null
                    return@launch
                }
                if (lastPackage != pkg) return@launch
                val hit = FeatureDetector.detect(active, root)
                if (hit == null) {
                    interceptedFeatureKey = null
                    return@launch
                }
                if (hit.key == interceptedFeatureKey) return@launch
                // Block's order: leave the blocked screen first, then show the wall. The
                // short settle delay lets the queued global action reach the target window
                // before our activity opens, otherwise BACK could close the wall itself.
                val navigatedAway = performNavigateAway(hit, pkg)
                if (navigatedAway) delay(NAVIGATE_SETTLE_MS)
                // BACK from a root feed may land on the launcher; the wall still explains
                // what happened there. A switch to another user app is left uninterrupted.
                if (!shouldShowFeatureWall(pkg)) return@launch
                // Arm dedup only after a successful launch; a failed start must be retried.
                if (launchIntercept(InterceptActivity.KIND_FEATURE, -1L, null, null, hit.label)) {
                    interceptedFeatureKey = hit.key
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Content scan failed: ${e.javaClass.simpleName}")
            } finally {
                scanInFlight.set(false)
            }
        }
    }

    /**
     * Executes an [AppFeature.navigateAwayTo] DSL list before the intercept screen appears.
     *
     * Block performs this through its accessibility service (`th3.j` + `p15`): BACK leaves
     * the Reels/Shorts/Spotlight surface first, so the user is not left staring at the feed
     * behind a warning. `click:<viewId>` resolves either a fully-qualified id or a bare
     * resource name against the scanned package; unsupported verbs are already dropped by
     * [FeatureAction.parseAll], so a future server command cannot crash the scan.
     */
    private fun performNavigateAway(feature: AppFeature, pkg: String): Boolean {
        var performed = false
        FeatureAction.parseAll(feature.navigateAwayTo).forEach { action ->
            // Never inject after the scanned app lost the foreground, and require a positive
            // live-window match: unless the active root positively belongs to [pkg], a BACK
            // or HOME could land on whatever app the user switched to during the scan.
            if (lastPackage != pkg) return performed
            when (action) {
                FeatureAction.NavigateBack -> {
                    if (liveRootFor(pkg) == null) return performed
                    if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                        performed = true
                    } else {
                        Log.d(TAG, "navigateAway: BACK was not performed")
                    }
                }
                FeatureAction.GoHome -> {
                    if (liveRootFor(pkg) == null) return performed
                    if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                        performed = true
                    } else {
                        Log.d(TAG, "navigateAway: HOME was not performed")
                    }
                }
                is FeatureAction.Click -> {
                    val viewId = if (action.viewId.contains(":id/")) {
                        action.viewId
                    } else {
                        "$pkg:id/${action.viewId}"
                    }
                    // Use the live tree only: the detection snapshot may be stale, and a
                    // stale node could belong to a different window.
                    val clicked = liveRootFor(pkg)
                        ?.findByViewId(viewId)
                        ?.takeIf { it.isVisibleToUser }
                        ?.performClick() == true
                    if (clicked) {
                        performed = true
                    } else {
                        Log.d(TAG, "navigateAway: click target was not found")
                    }
                }
            }
        }
        return performed
    }

    /**
     * The active window, but only when it positively belongs to [pkg]. Unlike
     * [isForeground], a null/unreadable root is not given the benefit of the doubt,
     * because these nodes feed injected actions rather than a block decision.
     */
    private fun liveRootFor(pkg: String): UiNode? {
        val live = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val livePackage = runCatching { live.packageName?.toString() }.getOrNull()
        return if (NavigateAwayGuard.canInject(pkg, livePackage)) A11yNode(live) else null
    }

    /**
     * After navigateAwayTo, keep the wall when we are still in the blocked app or the
     * escape landed on a critical surface (launcher/system UI) — that is the expected
     * result of BACK from a root feed and the wall is the only explanation the user gets.
     * A switch to another user app during the settle delay is not ours to interrupt.
     */
    private fun shouldShowFeatureWall(pkg: String): Boolean {
        val current = runCatching { rootInActiveWindow?.packageName?.toString() }
            .getOrNull()
            ?: lastPackage
        val systemSurface = current != null && CriticalApps.isSystemSurface(this, current)
        return FeatureWallPolicy.shouldShow(pkg, current, systemSurface)
    }

    private fun launchIntercept(
        kind: String,
        itemId: Long,
        reason: LimitReason?,
        escalations: Int?,
        label: String? = null,
    ): Boolean {
        val intent = Intent(this, InterceptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(InterceptActivity.EXTRA_KIND, kind)
            .putExtra(InterceptActivity.EXTRA_ITEM_ID, itemId)
            .putExtra(InterceptActivity.EXTRA_REASON, reason?.name)
            .putExtra(InterceptActivity.EXTRA_ESCALATIONS, escalations ?: 0)
            .putExtra(InterceptActivity.EXTRA_FEATURE_LABEL, label)
        return runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Intercept launch failed: ${it.javaClass.simpleName}") }
            .isSuccess
    }

    /**
     * Re-arming the social dedup guard: a hit that vanished from the screen (scroll,
     * dismissed warning) must become interceptable again after a short cool-down, or a
     * user can dismiss the warning, scroll away and back, and sail through. Until the
     * target has been *absent* for SOCIAL_REARM_MS we keep suppressing repeats so
     * scroll-heavy feeds don't spam intercepts for the same warning bar.
     */
    // Shared between the main thread and scan coroutines — must stay visible (@see above).
    @Volatile private var socialKeyAbsentSince = 0L

    private fun onSocialScanResult(hit: SocialContentHit?, pkg: String): SocialContentHit? {
        if (hit == null) {
            if (socialKeyAbsentSince == 0L) socialKeyAbsentSince = SystemClock.uptimeMillis()
            if (SystemClock.uptimeMillis() - socialKeyAbsentSince >= SOCIAL_REARM_MS) {
                interceptedSocialKey = null
            }
            return null
        }
        socialKeyAbsentSince = 0L
        if (hit.dedupKey == interceptedSocialKey) return null
        return hit
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // The user (or the OS) just disconnected our eyes. Real-time signal: post a
        // notification *now* so the loss is never silent. The periodic worker is the
        // backstop for crashes that skip this callback.
        runCatching { GuardHealthNotifier.notifyAccessibilityDown(this) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        urlMonitor.destroy()
        imageScreening.cancelPendingTasks()
        if (isServiceCrashing) runCatching { GuardHealthNotifier.notifyAccessibilityDown(this) }
        super.onDestroy()
    }

    /** Set from a crashed-scope signal; clearer than inferring inside onDestroy. */
    @Volatile private var isServiceCrashing = false

    override fun onInterrupt() {}

    private companion object {
        const val TAG = "SawnA11y"
        const val SCAN_THROTTLE_MS = 400L
        const val CACHE_TTL_MS = 2000L
        const val EXPIRY_GRACE_MS = 250L
        /** Time for a queued navigate-away global action to land before the wall opens. */
        const val NAVIGATE_SETTLE_MS = 300L
        const val SOCIAL_MAX_NODES = 400
        /** How long a vanished social hit must stay gone before the same warning can
         *  intercept again — long enough to stop scroll spam, short enough that re-approaching
         *  the content still triggers the wall. */
        const val SOCIAL_REARM_MS = 10_000L
    }
}
