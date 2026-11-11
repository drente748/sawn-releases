package com.sawn.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sawn.app.sawnContainer
import com.sawn.domain.feature.NightGuardPolicy
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * الحارس الليلي — يتحقق كل 15 دقيقة إن كان داخل النافذة الليلية.
 * عند الدخول: يفعّل وضع الانضباط (نفس مسار Lockdown).
 * عند الخروج أو انتهاء الفك (15 دقيقة): يعود تلقائيًا + يُسجَّل.
 */
class NightGuardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = applicationContext.sawnContainer.settings
        if (!settings.nightGuardEnabled.first()) {
            settings.restoreNightGuardOverrides()
            return Result.success()
        }

        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMin = settings.nightGuardStartMin.first()
        val endMin = settings.nightGuardEndMin.first()
        val unlockUntil = settings.nightGuardUnlockUntil.first()
        val nowMs = System.currentTimeMillis()

        val within = NightGuardPolicy.isWithinWindow(nowMin, startMin, endMin)
        val unlocked = unlockUntil > nowMs

        if (within && !unlocked) {
            // Enforce the night policy atomically while retaining only overrides made here.
            settings.applyNightGuardOverrides()
        } else {
            // Restore only the settings the night guard itself changed.
            settings.restoreNightGuardOverrides()
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "sawn_night_guard"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NightGuardWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

/**
 * إشعارات النية — صباحية + مسائية، قناة صامتة، معطلة افتراضيًا.
 * (تفعيل من الإعدادات)
 */
class NudgesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = applicationContext.sawnContainer.settings
        if (!settings.nudgesEnabled.first()) return Result.success()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val msg = if (hour < 12) "نية اليوم: ابدأ بالامتناع عن المحتوى المنحرف."
        else "تأمل المساء: راجع يومك واشكر نفسك على كل تقدم."
        com.sawn.app.system.GuardHealthNotifier.notifyNudge(applicationContext, msg)
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "sawn_nudges"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NudgesWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
