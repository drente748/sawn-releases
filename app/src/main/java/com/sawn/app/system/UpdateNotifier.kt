package com.sawn.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sawn.app.MainActivity
import com.sawn.app.R
import com.sawn.app.update.UpdateInstaller

/**
 * إشعارات التحديث الذاتي.
 *
 * حالتان:
 *  - **متاح**: وُجد تحديث جديد. النقر يفتح التطبيق عند زر التحديث.
 *  - **جاهز**: نُزِّل التحديث وتحقّقت بصمته. النقر يفتح مثبّت أندرويد مباشرة
 *    بالملف المُتحقَّق منه — لا إعادة تنزيل ولا إعادة تحقّق.
 *
 * أندرويد لا يسمح لأي تطبيق بتثبيت حزمة دون ضغط المستخدم على «تثبيت» في مثبّت
 * النظام، فالمتبقّي هنا هو آخر خطوة لا يملكها التطبيق.
 */
object UpdateNotifier {

    private const val CHANNEL_ID = "sawn_updates"
    private const val NOTIF_AVAILABLE = 1010
    private const val NOTIF_READY = 1011

    /** وُجد تحديث جديد لكنه لم يُنزَّل بعد (التنزيل التلقائي معطّل). */
    fun notifyAvailable(context: Context, versionName: String) {
        val openApp = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = NOTIF_AVAILABLE,
            title = context.getString(R.string.update_notif_available_title),
            text = context.getString(R.string.update_notif_available_text, versionName),
            intent = openApp,
            ongoing = false,
        )
    }

    /** التحديث مُنزَّل ومُتحقَّق منه — خطوة واحدة تفصله عن التثبيت. */
    fun notifyReadyToInstall(context: Context, versionName: String, apkPath: String) {
        val uri = runCatching { UpdateInstaller.uriFor(context, apkPath) }.getOrNull()
        if (uri == null) return
        val install = PendingIntent.getBroadcast(
            context, 11,
            Intent(context, UpdateInstallReceiver::class.java).apply {
                action = UpdateInstallReceiver.ACTION_INSTALL
                putExtra(UpdateInstallReceiver.EXTRA_URI, uri.toString())
                putExtra(UpdateInstallReceiver.EXTRA_VERSION_NAME, versionName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = NOTIF_READY,
            title = context.getString(R.string.update_notif_ready_title),
            text = context.getString(R.string.update_notif_ready_text, versionName),
            intent = install,
            ongoing = false,
        )
    }

    /** لا يوجد تحديث — امسح إشعار «الجاهز» إن وُجد. */
    fun clearReady(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(NOTIF_READY) }
    }

    fun clearAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(NOTIF_AVAILABLE) }
        runCatching { nm.cancel(NOTIF_READY) }
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        intent: PendingIntent,
        ongoing: Boolean,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setOngoing(ongoing)
            .build()
        runCatching { nm.notify(id, notification) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            com.sawn.app.SawnApp.instance.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = com.sawn.app.SawnApp.instance.getString(R.string.update_channel_desc)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }
}
