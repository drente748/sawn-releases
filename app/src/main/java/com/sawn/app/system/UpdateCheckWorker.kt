package com.sawn.app.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sawn.app.BuildConfig
import com.sawn.app.sawnContainer
import com.sawn.app.update.InstallUpdateResult
import com.sawn.app.update.UpdateInfo
import com.sawn.app.update.UpdateInstaller
import com.sawn.app.update.UpdateStatus
import com.sawn.domain.update.PendingUpdate
import com.sawn.domain.update.UpdateAction
import com.sawn.domain.update.UpdateAutoPolicy
import com.sawn.domain.update.UpdateAvailability
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * الفحص التلقائي للتحديثات.
 *
 * يعمل كل ست ساعات عند توفّر شبكة، ويفعل ما يلي:
 *  1. لا يفعل شيئاً إن أوقف المستخدم «التحديث التلقائي».
 *  2. يقرأ البيان العام (بلا هوية، بلا تتبّع) ويقارن `versionCode`.
 *  3. إن وُجد تحديث والتنزيل التلقائي مفعّل: ينزّله، ويعيد حساب بصمة SHA-256
 *     محلياً، ولا يسجّل نجاحاً إلا إذا طابقت. الملف المُتحقَّق منه يبقى جاهزاً
 *     والواجهة تُخبر المستخدم بذلك.
 *  4. ينشر إشعاراً يفتح مثبّت أندرويد مباشرةً بالملف الجاهز.
 *
 * **ما لا يمكن للتطبيق فعله:** تثبيت صامت. أندرويد يشترط ضغط المستخدم على
 * «تثبيت» في مثبّت النظام، ولا يملك أي تطبيق — مهما بلغ حقّه — أن يتجاوز ذلك.
 * لذلك تبقى الخطوة الأخيرة للمستخدم، وكل ما قبلها تلقائي.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.sawnContainer
        val settings = container.settings

        val autoUpdate = settings.autoUpdateEnabled.first()
        val autoDownload = settings.autoDownloadEnabled.first()
        val autoNotify = settings.autoNotifyEnabled.first()
        val pending = settings.pendingUpdate.first()

        // المفتاح مطفأ: لا اتصال بالشبكة إطلاقاً، ولا إشعار.
        if (!autoUpdate) return Result.success()

        val status = runCatching {
            container.updateChecker.check(BuildConfig.VERSION_CODE)
        }.getOrElse { UpdateStatus.Error(it.message ?: "unknown") }

        val availability = when (status) {
            is UpdateStatus.Available -> UpdateAvailability.NEWER
            UpdateStatus.UpToDate -> UpdateAvailability.UP_TO_DATE
            UpdateStatus.Disabled -> UpdateAvailability.DISABLED
            is UpdateStatus.Error -> UpdateAvailability.FAILED
            // Downloading / Installing / Checking لا تُرجع من الفحص نفسه — عَرَضُ
            // واجهة فقط. تُعامل كعطل مؤقّت لتُعاد المحاولة.
            UpdateStatus.Checking, UpdateStatus.Downloading, UpdateStatus.Installing ->
                UpdateAvailability.FAILED
        }
        val availableVersion = (status as? UpdateStatus.Available)?.info?.versionCode

        val action = UpdateAutoPolicy.decide(
            autoUpdateEnabled = autoUpdate,
            autoDownloadEnabled = autoDownload,
            availability = availability,
            pendingVersionCode = pending?.versionCode,
            availableVersionCode = availableVersion,
        )

        when (action) {
            UpdateAction.NONE -> return Result.success()
            UpdateAction.RETRY -> return Result.retry()
            UpdateAction.CLEAR -> {
                // لا تحديث جديد: أي ملف نُزّله سابقاً صار بلا فائدة.
                settings.clearPendingUpdate()
                UpdateNotifier.clearReady(applicationContext)
                UpdateInstaller.pruneOldApks(applicationContext, keepVersionCode = Int.MAX_VALUE)
                return Result.success()
            }
            UpdateAction.NOTIFY -> {
                val ready = pending
                if (ready != null && (availableVersion == null || ready.versionCode >= availableVersion)) {
                    if (autoNotify) {
                        UpdateNotifier.notifyReadyToInstall(applicationContext, ready.versionName, ready.apkPath)
                    }
                } else {
                    val info = (status as? UpdateStatus.Available)?.info
                    if (info != null && autoNotify) {
                        UpdateNotifier.notifyAvailable(applicationContext, info.versionName)
                    }
                }
                return Result.success()
            }
            UpdateAction.DOWNLOAD -> Unit
        }

        val info = (status as? UpdateStatus.Available)?.info ?: return Result.success()
        handleAvailable(settings, info)
        return Result.success()
    }

    /**
     * ينزّل التحديث ويتحقّق منه. القرار بالتنزيل اتُّخذ في [UpdateAutoPolicy] قبل
     * الوصول هنا — لا يُعاد اتخاذه هنا حتى لا تتعارض قراران.
     */
    private suspend fun handleAvailable(
        settings: com.sawn.core.data.SettingsStore,
        info: UpdateInfo,
    ) {
        val notify = settings.autoNotifyEnabled.first()
        val result = UpdateInstaller.downloadAndVerify(applicationContext, info)
        when (result) {
            is InstallUpdateResult.Failed -> {
                // فشل التنزيل أو التحقق: لا نُبلغ عن «جاهز». نكتفي بأن يوجد تحديث.
                if (notify) UpdateNotifier.notifyAvailable(applicationContext, info.versionName)
            }
            is InstallUpdateResult.Ready -> {
                val apk = UpdateInstaller.apkFileFor(applicationContext, info.versionCode)
                if (!apk.exists()) {
                    if (notify) UpdateNotifier.notifyAvailable(applicationContext, info.versionName)
                    return
                }
                settings.setPendingUpdate(
                    PendingUpdate(
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        sha256 = info.sha256,
                        changelog = info.changelog,
                        apkPath = apk.absolutePath,
                    ),
                )
                UpdateInstaller.pruneOldApks(applicationContext, keepVersionCode = info.versionCode)
                if (notify) {
                    UpdateNotifier.notifyReadyToInstall(applicationContext, info.versionName, apk.absolutePath)
                }
            }
        }
    }

    companion object {
        private const val UNIQUE = "sawn_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
