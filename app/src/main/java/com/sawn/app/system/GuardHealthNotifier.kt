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

/**
 * Posts the "protection down" health notification the moment Accessibility is lost —
 * the pattern RethinkDNS uses for tunnel loss: a real-time signal, not a poll result.
 * One standing notification id; each event replaces it so we never stack duplicates.
 *
 * The channel is HIGH importance so it presents even on the lock screen.
 * Clearing happens when the user re-enables the service (the boot worker clears it
 * after confirming Accessibility is back).
 */
object GuardHealthNotifier {

    private const val CHANNEL_ID = "sawn_guard_health"
    private const val NOTIF_ACCESSIBILITY = 1002
    private const val NOTIF_ADMIN = 1003
    private const val NOTIF_VPN = 1004
    private const val NOTIF_NUDGE = 1005

    /** إشعار نية صامت (صباحي/مسائي). */
    fun notifyNudge(context: Context, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val openApp = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("صَوْن — نية")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_NUDGE, notification) }
    }

    fun notifyAccessibilityDown(context: Context) {
        val settings = PendingIntent.getActivity(
            context, 1, PermissionsHelperAccessibility.openIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = NOTIF_ACCESSIBILITY,
            title = context.getString(R.string.guard_health_title),
            text = context.getString(R.string.guard_health_text),
            actionLabel = context.getString(R.string.guard_health_fix),
            action = settings,
        )
    }

    /** A live lockdown lost the uninstall guard: the app can now be removed early. */
    fun notifyAdminDown(context: Context) {
        val enable = PendingIntent.getActivity(
            context, 2, AdminManager.enableIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = NOTIF_ADMIN,
            title = context.getString(R.string.guard_admin_title),
            text = context.getString(R.string.guard_admin_text),
            actionLabel = context.getString(R.string.guard_admin_fix),
            action = enable,
        )
    }

    /** A live lockdown lost its tunnel: sites are no longer being filtered. */
    fun notifyVpnDown(context: Context) {
        val openApp = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = NOTIF_VPN,
            title = context.getString(R.string.guard_vpn_title),
            text = context.getString(R.string.guard_vpn_text),
            actionLabel = context.getString(R.string.guard_health_fix),
            action = openApp,
        )
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        actionLabel: String,
        action: PendingIntent,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openApp)
            .addAction(0, actionLabel, action)
            .setOngoing(true) // cannot be swiped away — only fixing the loss clears it
            .setAutoCancel(false)
            .build()
        runCatching { nm.notify(id, notification) }
    }

    /** Called when the boot/re-arm worker confirms Accessibility is back. */
    fun clearAccessibility(context: Context) = cancel(context, NOTIF_ACCESSIBILITY)

    fun clearAdmin(context: Context) = cancel(context, NOTIF_ADMIN)

    fun clearVpn(context: Context) = cancel(context, NOTIF_VPN)

    /** Clears every health notification once all signals are healthy. */
    fun clear(context: Context) {
        clearAccessibility(context)
        clearAdmin(context)
        clearVpn(context)
    }

    private fun cancel(context: Context, id: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(id) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            contextString(R.string.guard_health_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = contextString(R.string.guard_health_channel_desc)
            setShowBadge(true)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    private fun contextString(resId: Int) =
        com.sawn.app.SawnApp.instance.getString(resId)

    /** Indirection so the notifier doesn't import PermissionsHelper directly (test seam). */
    object PermissionsHelperAccessibility {
        fun openIntent(context: Context): Intent = Intent(
            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
