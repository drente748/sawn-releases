package com.sawn.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.sawn.domain.feature.ImageScreeningPolicy
import com.sawn.domain.feature.NsfwClassifier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Screens large images shown in social apps for adult content, closing the biggest
 * protection gap users report: media with no NSFW label, warning text, or caption.
 *
 * How it works (API 30+ only): requests the accessibility-service window screenshot,
 * crops candidate regions (image/media view nodes with a generous on-screen area) from
 * it, and runs the local TFLite classifier on a dedicated single-thread executor. The
 * verdict is delivered through [onResult]; a `true` means "intercept now". On API <30
 * this silently does nothing — those Android versions offer no safe screenshot path for
 * an accessibility service, and the text/social detectors remain the defense there.
 *
 * Costs are controlled three ways: a hard throttle between captures, a re-arm delay
 * before the next unlabeled image can trigger a new intercept, and per-window budgets
 * on candidate crops and traversed nodes. Nothing leaves the device.
 */
class ImageScreeningOrchestrator(private val service: AccessibilityService) {

    private val classifierExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sawn-nsfw").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    @Volatile private var lastCapture = 0L
    @Volatile private var lastPositive = 0L
    private val captureInFlight = AtomicBoolean(false)

    /**
     * Kicks off a screenshot+classify cycle. Returns immediately; [onResult] is called
     * (exactly once per accepted request) with the NSFW verdict.
     */
    fun maybeScreen(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(false); return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastCapture < ImageScreeningPolicy.MIN_INTERVAL_MS ||
            now - lastPositive < ImageScreeningPolicy.REARM_MS ||
            captureInFlight.get()
        ) {
            onResult(false); return
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            onResult(false); return
        }
        // прогреть/загрузить the TFLite model OFF the accessibility main thread: the
        // first load reads the asset and builds the interpreter (hundreds of ms), and
        // maybeScreen() runs inline in onAccessibilityEvent — blocking it risks input
        // dispatch ANRs. When the ready callback fires we retry maybeScreen().
        classifierExecutor.execute {
            classifierReady = NsfwClassifier.get(service) != null
            if (classifierReady) {
                service.mainExecutor.execute { maybeScreenRetry(onResult) }
            } else {
                captureInFlight.set(false)
                onResult(false)
            }
        }
    }

    @Volatile private var classifierReady = false

    private fun maybeScreenRetry(onResult: (Boolean) -> Unit) {
        val classifier = NsfwClassifier.get(service)
        if (classifier == null) {
            captureInFlight.set(false)
            onResult(false); return
        }
        val now = SystemClock.uptimeMillis()
        lastCapture = now
        capture(classifier, onResult)
    }

    /** takeScreenshot() and its result types are API 30+; maybeScreen() has already gated this. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(classifier: NsfwClassifier, onResult: (Boolean) -> Unit) {
        runCatching {
            val window = service.windows.firstOrNull()
                ?: throw IllegalStateException("no window")
            val windowId = window.id // AccessibilityWindowInfo.getId()
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    classifierExecutor.execute { process(result, classifier, onResult) }
                }
                override fun onFailure(errorCode: Int) {
                    captureInFlight.set(false)
                    onResult(false)
                }
            }
            service.takeScreenshot(windowId, service.mainExecutor, callback)
        }.onFailure {
            captureInFlight.set(false)
            onResult(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun process(
        result: AccessibilityService.ScreenshotResult,
        classifier: NsfwClassifier,
        onResult: (Boolean) -> Unit,
    ) {
        try {
            val buffer = result.hardwareBuffer
            var bitmap: Bitmap? = null
            try {
                // takeScreenshot() hands ownership to us: the buffer must be closed or
                // gralloc-backed fds pile up over scroll-heavy captures and starve the
                // fd table (which also breaks the VPN/socket layer in this process).
                bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                if (bitmap == null) { onResult(false); return }
                val root = service.rootInActiveWindow
                val candidates = if (root != null) collectCandidates(root) else emptyList()
                val hit = candidates.any { r -> classifyRegion(bitmap, r, classifier) }
                if (hit) lastPositive = SystemClock.uptimeMillis()
                onResult(hit)
            } finally {
                bitmap?.recycle()
                buffer.close()
            }
        } catch (e: Exception) {
            onResult(false)
        } finally {
            captureInFlight.set(false)
        }
    }

    private fun classifyRegion(
        shot: Bitmap,
        r: Rect,
        classifier: NsfwClassifier,
    ): Boolean {
        val w = shot.width
        val h = shot.height
        val left = r.left.coerceIn(0, w - 2)
        val top = r.top.coerceIn(0, h - 2)
        val right = r.right.coerceIn(left + 1, w)
        val bottom = r.bottom.coerceIn(top + 1, h)
        if (right - left < MIN_CROP || bottom - top < MIN_CROP) return false
        val cropped = Bitmap.createBitmap(shot, left, top, right - left, bottom - top)
        return try {
            val verdict = ImageScreeningPolicy.isNsfw(classifier.classify(cropped))
            verdict.screened
        } finally {
            cropped.recycle()
        }
    }

    /** Collects image-ish nodes covering a large share of the screen, capped by budget. */
    private fun collectCandidates(root: AccessibilityNodeInfo): List<Rect> {
        val out = ArrayList<Rect>(ImageScreeningPolicy.CANDIDATE_BUDGET)
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < ImageScreeningPolicy.NODE_BUDGET) {
            val node = stack.removeLast()
            visited++
            runCatching {
                if (ImageScreeningPolicy.isMediaNode(node.className?.toString())) {
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    out.add(bounds)
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        // Size gating + largest-first selection lives in the testable policy.
        return ImageScreeningPolicy
            .selectTopCandidates(
                out.map { ImageScreeningPolicy.CropRect(it.left, it.top, it.right, it.bottom) },
            )
            .map { Rect(it.left, it.top, it.right, it.bottom) }
    }

    fun cancelPendingTasks() { classifierExecutor.shutdownNow() }

    private companion object {
        /** Kept for reference; thresholds live in [ImageScreeningPolicy] so they're testable. */
        const val MIN_CROP = ImageScreeningPolicy.MIN_CROP_PX
    }
}
