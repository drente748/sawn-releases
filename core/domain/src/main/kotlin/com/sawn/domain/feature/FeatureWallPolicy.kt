package com.sawn.domain.feature

/**
 * Decides whether the feature wall still belongs on screen after `navigateAwayTo` ran.
 *
 * BACK from a root feed (for example TikTok's For You) can exit the app to the launcher.
 * That is the expected result of the escape action, so the wall must still appear and
 * explain why — otherwise blocking looks like a crash and the user simply reopens the app.
 * A switch to an unrelated user app during the settle delay, however, must not be
 * interrupted. [currentIsSystemSurface] is supplied by the Android layer (launcher or
 * System UI only, via `CriticalApps`), keeping this policy pure and unit-testable.
 */
object FeatureWallPolicy {
    fun shouldShow(
        originalPackage: String,
        currentPackage: String?,
        currentIsSystemSurface: Boolean,
    ): Boolean =
        currentPackage == null ||
            currentPackage == originalPackage ||
            currentIsSystemSurface
}
