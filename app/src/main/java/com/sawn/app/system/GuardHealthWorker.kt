package com.sawn.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.vpn.VpnController
import com.sawn.app.vpn.SawnVpnService
import com.sawn.app.sawnContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * NetGuard-style watchdog, adapted for accessibility: every 15 minutes, if the user's
 * configuration says screening should be active (master on, Social Guard or in-app
 * features selected) and the accessibility service is NOT running, post the
 * "protection stopped" notification. When accessibility is back, clear the notification.
 *
 * The real-time signal is the service's own onUnbind/onDestroy → [GuardHealthNotifier];
 * this worker is the backstop for crashes that skip those callbacks and for the case
 * where the user manually re-enables but a later event silently kills the service again.
 */
class GuardHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.sawnContainer
        val settings = container.settings
        // المستخدم أوقف الحماية من داخل التطبيق — "المُوقِف الوحيد": لا تنبيهات/إيقاظ.
        if (settings.userDisabledProtection.first()) {
            return Result.success()
        }
        val lockdownActive = settings.isLockdownActiveNow()
        val needsScreening = lockdownActive || (
            settings.masterEnabled.first() &&
                (
                    settings.socialGuard.first() ||
                        settings.enabledFeatureKeys.first().isNotEmpty() ||
                        settings.pornGuard.first()
                    )
            )
        val accessibilityOn = PermissionsHelper.isAccessibilityEnabled(applicationContext)

        // سلسلة الأيام لا تعتمد على فتح الواجهة: طالما الحجب فعّالٌ فهذا اليوم محميّ.
        if (container.blockEngine.isBlockingActiveNow()) settings.recordActiveDay()
        if (needsScreening && !accessibilityOn) {
            GuardHealthNotifier.notifyAccessibilityDown(applicationContext)
        } else {
            GuardHealthNotifier.clearAccessibility(applicationContext)
        }

        // A lockdown is only meaningful if the uninstall guard and the filter are both live.
        val adminMissing = lockdownActive && !AdminManager.isActive(applicationContext)
        if (adminMissing) {
            GuardHealthNotifier.notifyAdminDown(applicationContext)
        } else {
            GuardHealthNotifier.clearAdmin(applicationContext)
        }

        // إعادة تسليح الفلتر حين يُفترض أن يعمل. إذن VPN مُمنوح مسبقاً من المستخدم،
        // والمحاولة عادية لا تتحايل على شيء: إن رفضها النظام (قيود بدء الخدمات
        // الأمامية من الخلفية) يبقى التنبيه هو الوصفة، ولا ندّعي أن الحماية سليمة.
        val filterShouldRun = needsScreening || container.blockEngine.isBlockingActiveNow()
        if (filterShouldRun && !SawnVpnService.running &&
            VpnController.consentIntent(applicationContext) == null
        ) {
            VpnController.start(applicationContext)
        }

        val vpnMissing = filterShouldRun && !SawnVpnService.running
        if (vpnMissing) {
            GuardHealthNotifier.notifyVpnDown(applicationContext)
        } else {
            GuardHealthNotifier.clearVpn(applicationContext)
        }

        // الودجت يعرض حالة الحماية والعداد — حدّثه بعد أن استقرّت الحالة.
        com.sawn.app.widget.SawnWidgetProvider.requestUpdate(applicationContext)

        return Result.success()
    }

    companion object {
        private const val UNIQUE = "guard_health"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardHealthWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE, // adopt new constraints on upgrade
                request,
            )
        }
    }
}
