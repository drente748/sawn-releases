package com.sawn.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sawn.app.update.UpdateInstaller

/**
 * يفتح مثبّت أندرويد على ملف تحديث مُتحقَّق منه.
 *
 * يستقبل مسار الملف كـ URI من FileProvider (لا مسارًا على القرص — لا يُمنح
 * أي تطبيق آخر وصولاً إلى ذاكرتنا) ثم يمرّره إلى مثبّت النظام.
 *
 * لا يوجد تثبيت صامت هنا ولا يمكن أن يكون: أندرويد يشترط موافقة المستخدم
 * على شاشة التثبيت. هذا المُستقبِل يوفّر للمستخدم خطوة «ابحث عن التحديث»
 * فقط.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL) return
        val raw = intent.getStringExtra(EXTRA_URI) ?: return
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        if (uri.scheme != "content") return
        UpdateInstaller.install(context, uri)
    }

    companion object {
        const val ACTION_INSTALL = "com.sawn.app.INSTALL_PENDING_UPDATE"
        const val EXTRA_URI = "uri"
        const val EXTRA_VERSION_NAME = "version_name"
    }
}
