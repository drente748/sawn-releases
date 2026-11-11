package com.sawn.app.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Being an *active* device admin is what blocks the app from being uninstalled — Android
 * won't let you remove an app with an enabled admin until it's deactivated. Sawn uses
 * this for one thing only: uninstall protection during strict mode. It requests no
 * password/wipe/lock policies.
 */
class SawnDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off removes Sawn's uninstall protection. If a time lock is active it " +
            "still blocks protection changes until it ends — but the app can then be uninstalled " +
            "early. Wait out the urge instead."
}
