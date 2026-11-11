package com.sawn.domain.feature

/**
 * Guards actions injected into another app's window.
 *
 * The service's `isForeground()` is intentionally lenient for whole-app blocks: if the
 * accessibility root is transiently unavailable it falls back to the last window event so
 * a deserved intercept is not skipped. That leniency is unsafe for navigate-away actions:
 * a global BACK or a synthetic click must only fire when the live active window is
 * positively the app the feature was detected in.
 */
object NavigateAwayGuard {
    fun canInject(originalPackage: String, liveRootPackage: String?): Boolean =
        liveRootPackage != null &&
            liveRootPackage.isNotBlank() &&
            liveRootPackage == originalPackage
}
