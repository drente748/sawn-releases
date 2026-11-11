package com.sawn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.sawn.app.util.LocaleUtils
import com.sawn.core.data.BlockRepository
import com.sawn.core.data.SettingsStore
import com.sawn.domain.block.AccessController
import com.sawn.domain.block.BlockEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import com.sawn.app.vpn.VpnController
import com.sawn.app.vpn.SawnVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SawnApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // معالج تعطل عام: يسجّل الأثر ثم يُسلّم الاستثناء للمنصة لتنهي العملية نظيفاً.
        // السابق (Looper.prepare + Looper.loop) كان يرمي "Only one Looper may be created
        // per thread" على الخيط الرئيسي → تعطل مزدوج، ويجمّد خيوط العمال للأبد.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                // Keep a single bounded crash trace locally; never include Intent or user content.
                val trace = "thread=${thread.name} at=${System.currentTimeMillis()}\n" +
                    android.util.Log.getStackTraceString(throwable).take(MAX_CRASH_TRACE_CHARS) + "\n\n"
                openFileOutput(CRASH_FILE, Context.MODE_PRIVATE).use { out ->
                    out.write(trace.toByteArray())
                }
            }
            previousHandler?.uncaughtException(thread, throwable)
                ?: Process.killProcess(Process.myPid())
        }

        // اللغة تُطبّق قبل أي UI يُبنى. أي فشل لا يوقف التطبيق.
        runBlocking {
            runCatching {
                val locale = EntryPointAccessors.fromApplication(
                    this@SawnApp,
                    LegacyContainer::class.java,
                ).settings.locale.first()
                LocaleUtils.apply(this@SawnApp, locale)
            }
        }

        runCatching { createNotificationChannel() }
        runCatching { com.sawn.app.system.GuardHealthWorker.schedule(this) }
        runCatching { com.sawn.app.system.NightGuardWorker.schedule(this) }
        runCatching { com.sawn.app.system.NudgesWorker.schedule(this) }
        // التحديث الذاتي: فحص دوري كل ست ساعات. العامل نفسه يحترم مفتاح
        // «التحديث التلقائي» — إن أوقفه المستخدم لا يفعل شيئاً ولا يفتح شبكة.
        runCatching { com.sawn.app.system.UpdateCheckWorker.schedule(this) }
        restoreActiveLockdown()
    }

    /**
     * A live time lock must not depend on a boot broadcast arriving. Some OEMs drop
     * BOOT_COMPLETED, and a force-stop or a crash can skip it too, so every cold process
     * start re-asserts the lock's core flags and re-arms the filter (idempotent: it only
     * acts while the window is live and stops early when the tunnel already runs).
     */
    private fun restoreActiveLockdown() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val settings = sawnContainer.settings
                if (!settings.isLockdownActiveNow()) return@launch
                // Restore the switches the lock guarantees before anything else reads them.
                settings.enforceLockdownCore()
                settings.recordActiveDay()
                if (SawnVpnService.running) return@launch
                if (VpnController.consentIntent(this@SawnApp) == null) {
                    VpnController.start(this@SawnApp)
                }
                com.sawn.app.system.KeepAliveWorker.schedule(this@SawnApp, delaySeconds = 10L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            VPN_CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CRASH_FILE = "last-crash.txt"
        private const val MAX_CRASH_TRACE_CHARS = 32_000
        const val VPN_CHANNEL_ID = "sawn_blocking"

        /** App-scoped reference for notifiers that run outside an Activity context. */
        lateinit var instance: SawnApp
            private set
    }
}

/**
 * Legacy entry point for the pre-Hilt call sites that still reach the graph through a
 * Context. Reads the SAME Hilt singletons — there is only one wiring path.
 * Call sites migrate to proper injection (@Inject / hiltViewModel) one screen at a time.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LegacyContainer {
    val settings: SettingsStore
    val repository: BlockRepository
    val blockEngine: BlockEngine
    val accessController: AccessController
    val stats: com.sawn.domain.repo.StatsRepository
    val database: com.sawn.core.data.SawnDatabase
    val updateChecker: com.sawn.app.update.UpdateChecker
}

/** Convenience accessor from any Context. */
val android.content.Context.sawnContainer: LegacyContainer
    get() = EntryPointAccessors.fromApplication(
        applicationContext as SawnApp,
        LegacyContainer::class.java,
    )
