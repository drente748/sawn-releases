package com.sawn.core.data

import androidx.room.withTransaction
import com.sawn.domain.feature.BackupDocument
import com.sawn.domain.feature.BackupItem
import com.sawn.domain.feature.BackupProfile
import com.sawn.domain.feature.BackupSchedule
import com.sawn.domain.feature.BackupSettings
import com.sawn.domain.feature.DeepFocusPolicy
import com.sawn.domain.model.BlockType
import com.sawn.domain.model.InterceptMode
import kotlinx.coroutines.flow.first

/**
 * بناء واستعادة النسخة الاحتياطية.
 *
 * الترميز نفسه في [com.sawn.domain.feature.BackupCodec] (نواة خالصة بلا أندرويد)،
 * وهنا المهمة الوحيدة: جمع الحالة من DataStore وRoom، ثم تطبيقها بعد فكّ الترميز.
 *
 * ما لا يُنسخ عمداً: الرقم السري (تجزئة + ملح)، رقم الوالدين، وعدّاد الإقلاع —
 *_device-scoped_ وقيم مضادة للعبث لا معنى لها على جهاز آخر.
 */
class BackupRepository(
    private val settings: SettingsStore,
    private val database: SawnDatabase,
) {

    /** يجمع الحالة الكاملة الحالية في وثيقة قابلة للترميز. */
    suspend fun buildDocument(): BackupDocument {
        val items = database.blockDao().allItems()
        val schedules = database.blockDao().allSchedules()
        val days = database.protectedDaysDao().all()

        return BackupDocument(
            profile = BackupProfile(
                name = settings.profileName.first(),
                age = settings.profileAge.first(),
            ),
            settings = BackupSettings(
                masterEnabled = settings.masterEnabled.first(),
                alwaysOn = settings.alwaysOn.first(),
                blockDoh = settings.blockDoh.first(),
                blockAds = settings.blockAds.first(),
                blockNotifications = settings.blockNotifications.first(),
                pornGuard = settings.pornGuard.first(),
                safeSearchForce = settings.safeSearchForce.first(),
                reduceMotion = settings.reduceMotion.first(),
                locale = settings.locale.first(),
                deepFocusEnabled = settings.deepFocusEnabled.first(),
                deepFocusWhitelist = settings.deepFocusWhitelist.first().toList(),
                nightGuardEnabled = settings.nightGuardEnabled.first(),
                nightGuardStartMin = settings.nightGuardStartMin.first(),
                nightGuardEndMin = settings.nightGuardEndMin.first(),
                accountabilityName = settings.accountabilityName.first(),
                accountabilityContact = settings.accountabilityContact.first(),
                nudgesEnabled = settings.nudgesEnabled.first(),
                socialGuardEnabled = settings.socialGuard.first(),
                enabledSocialPlatforms = settings.enabledSocialPlatforms.first().toList(),
                autoUpdateEnabled = settings.autoUpdateEnabled.first(),
                autoDownloadEnabled = settings.autoDownloadEnabled.first(),
                autoNotifyEnabled = settings.autoNotifyEnabled.first(),
            ),
            blockedItems = items.map { item ->
                BackupItem(
                    type = item.type.name,
                    value = item.value,
                    label = item.label,
                    enabled = item.enabled,
                    interceptMode = item.interceptMode.name,
                    pauseSeconds = item.pauseSeconds,
                    customPrompt = item.customPrompt,
                    openLimitPerDay = item.openLimitPerDay,
                    dailyLimitMinutes = item.dailyLimitMinutes,
                    cooldownMinutes = item.cooldownMinutes,
                )
            },
            schedules = schedules.map { schedule ->
                BackupSchedule(
                    name = schedule.name,
                    daysMask = schedule.daysMask,
                    startMinute = schedule.startMinute,
                    endMinute = schedule.endMinute,
                    enabled = schedule.enabled,
                )
            },
            protectedDays = days,
            enabledFeatures = settings.enabledFeatureKeys.first().toList(),
            onboardingAnswers = settings.onboardingAnswers.first(),
        )
    }

    /**
     * يطبّق وثيقة مستعادة. القوائم والجداول تُستبدل بالكامل (نسخة احتياطية تعني
     * «حالة الجهاز حينها» لا «إضافة إليها»)، أما الأيام المحمية فتُدمج حتى لا تُفقد
     * أيامٌ سُجّلت بعد تاريخ النسخة.
     *
     * كل الكتابة داخل معاملة واحدة: لو فشل أي صف، تبقى القاعدة كما كانت — لا قائمة
     * محذوفة بلا بديل.
     */
    suspend fun applyDocument(doc: BackupDocument) {
        val currentSettings = settingsSnapshot()
        val current = databaseSnapshot()
        try {
            with(doc.settings) {
                settings.restoreMasterEnabled(masterEnabled)
                settings.setAlwaysOn(alwaysOn)
                settings.setBlockDoh(blockDoh)
                settings.setBlockAds(blockAds)
                settings.setBlockNotifications(blockNotifications)
                settings.setPornGuard(pornGuard)
                settings.setSafeSearchForce(safeSearchForce)
                settings.setReduceMotion(reduceMotion)
                settings.setLocale(locale)
                settings.setDeepFocus(deepFocusEnabled)
                settings.setDeepFocusWhitelist(
                    if (deepFocusWhitelist.isEmpty()) DeepFocusPolicy.DEFAULT_WHITELIST
                    else deepFocusWhitelist.toSet(),
                )
                settings.setNightGuard(nightGuardEnabled, nightGuardStartMin, nightGuardEndMin)
                settings.setAccountability(accountabilityName, accountabilityContact)
                settings.setNudgesEnabled(nudgesEnabled)
                settings.setEnabledSocialPlatforms(enabledSocialPlatforms.toSet())
                settings.setSocialGuardEnabled(socialGuardEnabled)
            }
            settings.setProfile(doc.profile.name, doc.profile.age)
            doc.onboardingAnswers?.let { settings.setOnboardingAnswers(it) }
            settings.clearDeepFocusUnlock()
            settings.setNightGuardUnlock(0L)
            settings.setEnabledFeatureKeys(doc.enabledFeatures.toSet())

            database.withTransaction {
                database.blockDao().deleteAllItems()
                doc.blockedItems.forEach { item ->
                    val type = runCatching { BlockType.valueOf(item.type) }.getOrNull() ?: return@forEach
                    val mode = runCatching { InterceptMode.valueOf(item.interceptMode) }
                        .getOrDefault(InterceptMode.BLOCK)
                    database.blockDao().upsertItem(
                        BlockedItem(
                            type = type,
                            value = item.value,
                            label = item.label,
                            enabled = item.enabled,
                            createdAt = 0L,
                            interceptMode = mode,
                            pauseSeconds = item.pauseSeconds.coerceIn(1, 3600),
                            customPrompt = item.customPrompt,
                            openLimitPerDay = item.openLimitPerDay.coerceAtLeast(0),
                            dailyLimitMinutes = item.dailyLimitMinutes.coerceAtLeast(0),
                            cooldownMinutes = item.cooldownMinutes.coerceAtLeast(0),
                        ),
                    )
                }

                database.blockDao().deleteAllSchedules()
                doc.schedules.forEach { schedule ->
                    database.blockDao().upsertSchedule(
                        Schedule(
                            name = schedule.name,
                            enabled = schedule.enabled,
                            daysMask = schedule.daysMask and 0x7F,
                            startMinute = schedule.startMinute.coerceIn(0, 1439),
                            endMinute = schedule.endMinute.coerceIn(0, 1439),
                        ),
                    )
                }

                doc.protectedDays.forEach { date ->
                    runCatching { java.time.LocalDate.parse(date) }.getOrNull()?.let { valid ->
                        database.protectedDaysDao().upsert(
                            ProtectedDayEntity(date = valid.toString(), source = "backup", recordedAt = System.currentTimeMillis()),
                        )
                    }
                }
            }
        } catch (error: Exception) {
            runCatching { restoreSettings(currentSettings) }
                .onFailure(error::addSuppressed)
            runCatching { restoreDatabase(current) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }

    private data class SettingsSnapshot(
        val profileName: String?, val profileAge: Int?, val onboardingAnswers: String?,
        val masterEnabled: Boolean, val alwaysOn: Boolean, val blockDoh: Boolean, val blockAds: Boolean,
        val blockNotifications: Boolean, val pornGuard: Boolean, val safeSearchForce: Boolean,
        val reduceMotion: Boolean, val locale: String, val deepFocusEnabled: Boolean,
        val deepFocusWhitelist: Set<String>, val nightGuardEnabled: Boolean,
        val nightGuardStartMin: Int, val nightGuardEndMin: Int,
        val accountabilityName: String?, val accountabilityContact: String?, val nudgesEnabled: Boolean,
        val enabledFeatures: Set<String>, val socialGuard: Boolean, val socialPlatforms: Set<String>,
        val strictMode: Boolean, val userDisabledProtection: Boolean,
        val autoUpdateEnabled: Boolean, val autoDownloadEnabled: Boolean, val autoNotifyEnabled: Boolean,
    )

    private data class DatabaseSnapshot(
        val items: List<BlockedItem>, val schedules: List<Schedule>, val protectedDays: List<String>,
    )

    private suspend fun settingsSnapshot() = SettingsSnapshot(
        profileName = settings.profileName.first(), profileAge = settings.profileAge.first(),
        onboardingAnswers = settings.onboardingAnswers.first(), masterEnabled = settings.masterEnabled.first(),
        alwaysOn = settings.alwaysOn.first(), blockDoh = settings.blockDoh.first(), blockAds = settings.blockAds.first(),
        blockNotifications = settings.blockNotifications.first(), pornGuard = settings.pornGuard.first(),
        safeSearchForce = settings.safeSearchForce.first(), reduceMotion = settings.reduceMotion.first(),
        locale = settings.locale.first(), deepFocusEnabled = settings.deepFocusEnabled.first(),
        deepFocusWhitelist = settings.deepFocusWhitelist.first(), nightGuardEnabled = settings.nightGuardEnabled.first(),
        nightGuardStartMin = settings.nightGuardStartMin.first(), nightGuardEndMin = settings.nightGuardEndMin.first(),
        accountabilityName = settings.accountabilityName.first(), accountabilityContact = settings.accountabilityContact.first(),
        nudgesEnabled = settings.nudgesEnabled.first(), enabledFeatures = settings.enabledFeatureKeys.first(),
        socialGuard = settings.socialGuard.first(), socialPlatforms = settings.enabledSocialPlatforms.first(),
        strictMode = settings.strictMode.first(), userDisabledProtection = settings.userDisabledProtection.first(),
        autoUpdateEnabled = settings.autoUpdateEnabled.first(), autoDownloadEnabled = settings.autoDownloadEnabled.first(),
        autoNotifyEnabled = settings.autoNotifyEnabled.first(),
    )

    private suspend fun databaseSnapshot() = DatabaseSnapshot(
        items = database.blockDao().allItems(),
        schedules = database.blockDao().allSchedules(),
        protectedDays = database.protectedDaysDao().all(),
    )

    private suspend fun restoreSettings(snapshot: SettingsSnapshot) {
        settings.restoreMasterEnabled(snapshot.masterEnabled)
        settings.setAlwaysOn(snapshot.alwaysOn)
        settings.setStrictMode(snapshot.strictMode)
        settings.setUserDisabledProtection(snapshot.userDisabledProtection)
        settings.setBlockDoh(snapshot.blockDoh)
        settings.setBlockAds(snapshot.blockAds)
        settings.setBlockNotifications(snapshot.blockNotifications)
        settings.setPornGuard(snapshot.pornGuard)
        settings.setSafeSearchForce(snapshot.safeSearchForce)
        settings.setReduceMotion(snapshot.reduceMotion)
        settings.setLocale(snapshot.locale)
        settings.setDeepFocus(snapshot.deepFocusEnabled)
        settings.setDeepFocusWhitelist(snapshot.deepFocusWhitelist)
        settings.setNightGuard(snapshot.nightGuardEnabled, snapshot.nightGuardStartMin, snapshot.nightGuardEndMin)
        settings.setAccountability(snapshot.accountabilityName, snapshot.accountabilityContact)
        settings.setNudgesEnabled(snapshot.nudgesEnabled)
        settings.setProfile(snapshot.profileName, snapshot.profileAge)
        snapshot.onboardingAnswers?.let { settings.setOnboardingAnswers(it) }
        settings.setEnabledFeatureKeys(snapshot.enabledFeatures)
        settings.setEnabledSocialPlatforms(snapshot.socialPlatforms)
        settings.setSocialGuardEnabled(snapshot.socialGuard)
        // تفضيلات التحديث التلقائي تُستعاد كما هي: من أوقفها يجب ألّا تعود
        // مفعّلةً بعد الاستعادة، فالتواصل مع الشبكة قرارٌ يخصّه وحده.
        settings.setAutoUpdateEnabled(snapshot.autoUpdateEnabled)
        settings.setAutoDownloadEnabled(snapshot.autoDownloadEnabled)
        settings.setAutoNotifyEnabled(snapshot.autoNotifyEnabled)
    }

    private suspend fun restoreDatabase(snapshot: DatabaseSnapshot) {
        database.withTransaction {
            database.blockDao().deleteAllItems()
            snapshot.items.forEach { database.blockDao().upsertItem(it) }
            database.blockDao().deleteAllSchedules()
            snapshot.schedules.forEach { database.blockDao().upsertSchedule(it) }
            snapshot.protectedDays.forEach { date ->
                database.protectedDaysDao().upsert(
                    ProtectedDayEntity(date = date, source = "rollback", recordedAt = System.currentTimeMillis()),
                )
            }
        }
    }
}
