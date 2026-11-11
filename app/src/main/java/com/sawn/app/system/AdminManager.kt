package com.sawn.app.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Helpers around Sawn's device-admin (uninstall protection). */
object AdminManager {
    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun component(context: Context) = ComponentName(context, SawnDeviceAdminReceiver::class.java)

    fun isActive(context: Context): Boolean = dpm(context).isAdminActive(component(context))

    fun enableIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Sawn uses device admin only to stop itself being uninstalled while blocking " +
                    "is active. It cannot read your data, messages, or control your device.",
            )

    fun disable(context: Context) {
        runCatching { dpm(context).removeActiveAdmin(component(context)) }
    }
}
