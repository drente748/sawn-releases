package com.sawn.app.accessibility

import android.content.Context
import android.content.Intent
import com.sawn.app.ui.intercept.InterceptActivity

/** Small indirection so the monitor can launch the intercept without a service import. */
object InterceptActivityBridge {
    fun launchAdultIntercept(context: Context, host: String) {
        val intent = Intent(context, InterceptActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(InterceptActivity.EXTRA_KIND, InterceptActivity.KIND_ADULT)
        intent.putExtra(InterceptActivity.EXTRA_HOST, host)
        context.startActivity(intent)
    }
}
