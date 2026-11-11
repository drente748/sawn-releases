package com.sawn.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Apps that must never be blockable — blocking them locks the user out of their own phone
 * (a recurring, much-complained-about failure mode of other blockers). We refuse to add
 * these in the picker and never intercept them at runtime.
 */
object CriticalApps {
    private val CORE = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.cellbroadcastreceiver",
    )

    fun isCritical(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (packageName in CORE) return true
        return packageName == defaultLauncher(context)
    }

    /**
     * Narrower than [isCritical]: surfaces where an intercept may still be shown after an
     * escape action (launcher / System UI). A call, the emergency dialer, or Settings is a
     * user-facing app in its own right and must never be covered by the wall. Pure overload
     * is unit-tested; a null launcher resolves to false (suppress rather than cover).
     */
    fun isSystemSurface(context: Context, packageName: String): Boolean =
        isSystemSurface(packageName, defaultLauncher(context))

    fun isSystemSurface(packageName: String?, launcherPackage: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == "com.android.systemui") return true
        return launcherPackage != null && packageName == launcherPackage
    }

    private fun defaultLauncher(context: Context): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }.getOrNull()
}
