package com.sawn.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sawn.app.vpn.VpnController
import com.sawn.app.vpn.SawnVpnService
import com.sawn.app.sawnContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * أعاد تشغيل الحماية بعد أن تُمسح المهمة من الخلفية.
 *
 * خدمة الفلتر مستقلة عن مهمة الواجهة (`stopWithTask=false`)، لكن الشركات التي
 * تقتل التطبيقات بقسوة تقتل العملية كاملة. هذه العامل يُشغَّل بعد ثوانٍ من
 * `onTaskRemoved`، فإن كان الحجب مطلوباً والفلتر ميتاً أعاده؛ وإن كان يعمل
 * يكتفي بتسجيل اليوم المحمي وإزالة تنبيه سابق.
 */
class KeepAliveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.sawnContainer
        // المستخدم أوقف الحماية من داخل التطبيق — "المُوقِف الوحيد": لا نعيد تشغيل anything.
        if (runCatching { container.settings.userDisabledProtection.first() }.getOrDefault(false)) {
            GuardHealthNotifier.clearVpn(applicationContext)
            return Result.success()
        }
        val active = runCatching { container.blockEngine.isBlockingActiveNow() }.getOrDefault(false)
        if (!active) {
            // المستخدم أوقف الحماية من داخل التطبيق: لا نعيد تشغيل شيء، ونمحي تنبيهاً
            // قديماً كي لا يظن أن الحماية متوقفة خطأً.
            GuardHealthNotifier.clearVpn(applicationContext)
            return Result.success()
        }

        container.settings.recordActiveDay()
        if (!SawnVpnService.running && VpnController.consentIntent(applicationContext) == null) {
            VpnController.start(applicationContext)
            // امنح الخدمة لحظةً لتثبيت الترنل قبل الحكم عليها.
            for (attempt in 0 until 12) {
                if (SawnVpnService.running) break
                delay(400L)
            }
        }
        if (SawnVpnService.running) {
            GuardHealthNotifier.clearVpn(applicationContext)
            return Result.success()
        }

        GuardHealthNotifier.notifyVpnDown(applicationContext)
        // الفشل غالباً مؤقت (قيود بدء الخدمات من الخلفية بعد إعادة تشغيل الجهاز).
        // نعيد المحاولة بتأخير تصاعدي محدود بدل الاستسلام من أول محاولة.
        return if (runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE = "sawn_keep_alive"

        /** يجدول فحصاً واحداً بعد [delaySeconds]، ويستبدل أي فحص معلّق سابق. */
        fun schedule(context: Context, delaySeconds: Long = 15L) {
            val request = OneTimeWorkRequestBuilder<KeepAliveWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }
}
