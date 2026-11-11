package com.sawn.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sawn.app.vpn.VpnController
import com.sawn.app.sawnContainer
import com.sawn.domain.block.BootRestorePolicy
import com.sawn.domain.block.BootRestoreState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms protection after a reboot, an app update, or an OEM quick-boot broadcast.
 *
 * The decision itself lives in [BootRestorePolicy] (pure + unit-tested): a live time lock
 * always comes back whole, an explicit in-app stop never wakes anything up, and ordinary
 * protection restarts the filter and counts the protected day without touching switches.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val boot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!boot && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Watchdog backstop runs after every boot so a stale "protection stopped"
                // notification from an accessibility loss during shutdown gets resolved.
                GuardHealthWorker.schedule(context)
                val container = context.sawnContainer
                val settings = container.settings

                // The monotonic anchor belongs to the previous boot; rebuild it from the wall
                // deadline protected by the high-water mark. This must run before any early
                // return, otherwise a stopped-then-restarted install keeps a stale anchor.
                if (boot) settings.reanchorLockdown()

                val lockdownActive = settings.isLockdownActiveNow()
                val filterActive = container.blockEngine.isBlockingActiveNow()
                val decision = BootRestorePolicy.decide(
                    BootRestoreState(
                        userDisabledProtection = settings.userDisabledProtection.first(),
                        lockdownActive = lockdownActive,
                        filterActive = filterActive,
                    ),
                )

                if (decision.reassertCoreFlags) {
                    // A lock must never survive with a weak filter: restore the core flags
                    // atomically before anything else reads them.
                    settings.enforceLockdownCore()
                }
                if (decision.recordProtectedDay) settings.recordActiveDay()
                if (decision.warnMissingAdmin && !AdminManager.isActive(context)) {
                    GuardHealthNotifier.notifyAdminDown(context)
                }

                // Consent was granted before the reboot; asking again is impossible from a
                // receiver, so only a best-effort start here. If Android refuses a background
                // foreground-service start, the worker retries seconds later.
                if (decision.startFilter && VpnController.consentIntent(context) == null) {
                    VpnController.start(context)
                }
                if (decision.startFilter) KeepAliveWorker.schedule(context, delaySeconds = 20L)
                if (decision.startFilter && !com.sawn.app.vpn.SawnVpnService.running && lockdownActive) {
                    GuardHealthNotifier.notifyVpnDown(context)
                }
                // الحالة تغيّرت بعد إعادة التشغيل — الودجت يجب أن يعكسها فوراً.
                com.sawn.app.widget.SawnWidgetProvider.requestUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }
}
