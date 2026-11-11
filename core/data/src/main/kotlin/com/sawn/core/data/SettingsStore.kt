package com.sawn.core.data

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sawn.domain.block.LockdownPolicy
import com.sawn.domain.block.LockdownWindow
import com.sawn.domain.block.StrictPolicy
import com.sawn.domain.feature.BlockJournal
import com.sawn.domain.feature.DeepFocusState
import com.sawn.domain.feature.JournalSnapshot
import com.sawn.domain.feature.ShieldPreset
import com.sawn.domain.repo.ProtectedDaysRepository
import com.sawn.domain.security.PinLockoutPolicy
import com.sawn.domain.update.PendingUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "sawn_settings")

/** One consistent read of every setting that decides whether (and how) protection is active. */
data class ProtectionSnapshot(
    val masterEnabled: Boolean,
    val alwaysOn: Boolean,
    val strictMode: Boolean,
    val focusEndsAt: Long,
    val focusStrict: Boolean,
    val blockDoh: Boolean,
    val blockAds: Boolean,
    val hasPin: Boolean,
    val pornGuard: Boolean,
    val safeSearchForce: Boolean,
    val lockdownUntil: Long,
    val lockdownElapsedEnd: Long,
    val lockdownBootAtStart: Int,
)

/** Result of an atomic "turn protection off" request. */
data class DisableOutcome(val disabled: Boolean, val focusActive: Boolean)

/**
 * User settings & protection state. The PIN is stored as a salted hash — never plaintext.
 */
class SettingsStore(
    private val context: Context,
    private val protectedDays: ProtectedDaysRepository? = null,
) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val ALWAYS_ON = booleanPreferencesKey("always_on")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val ENABLED_FEATURES = stringSetPreferencesKey("enabled_features")
        val SOCIAL_GUARD = booleanPreferencesKey("social_guard")
        val ENABLED_SOCIAL_PLATFORMS = stringSetPreferencesKey("enabled_social_platforms")
        // ( social_guard / enabled_social_platforms مهجوران بعد 1.0.0 — لم يبق لهما تعريف)
        val FOCUS_ENDS_AT = longPreferencesKey("focus_ends_at")
        val FOCUS_STRICT = booleanPreferencesKey("focus_strict")
        val BLOCK_DOH = booleanPreferencesKey("block_doh")
        val BLOCK_ADS = booleanPreferencesKey("block_ads")
        val BLOCK_NOTIFICATIONS = booleanPreferencesKey("block_notifications")
        val FOCUS_SESSIONS_DONE = intPreferencesKey("focus_sessions_done")
        val PIN_FAILS = intPreferencesKey("pin_fails")
        val PIN_LOCK_UNTIL = longPreferencesKey("pin_lock_until")
        val PIN_LOCK_ELAPSED_UNTIL = longPreferencesKey("pin_lock_elapsed_until")
        val PIN_LOCK_BOOT_COUNT = intPreferencesKey("pin_lock_boot_count")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_AGE = intPreferencesKey("profile_age")
        val ONBOARDING_ANSWERS = stringPreferencesKey("onboarding_answers")
        val SEEN_TIME_HIGHWAT = longPreferencesKey("seen_time_highwat")
        val PAUSE_DEFEATS_DATE = longPreferencesKey("pause_defeats_date")
        val PAUSE_DEFEATS = intPreferencesKey("pause_defeats")
        // --- Porn Guard -------------------------------------------------------
        // Default ON: the app's core promise for its target users is protection from
        // adult content, so a fresh install is protected without visiting settings.
        val PORN_GUARD = booleanPreferencesKey("porn_guard")
        val SAFE_SEARCH_FORCE = booleanPreferencesKey("safe_search_force")
        val ADULT_EXTRA_DOMAINS = stringSetPreferencesKey("adult_extra_domains")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val APP_LOCALE = stringPreferencesKey("app_locale")
        val LOCKDOWN_UNTIL = longPreferencesKey("lockdown_until")
        val LOCKDOWN_ELAPSED_END = longPreferencesKey("lockdown_elapsed_end")
        val LOCKDOWN_BOOT_AT_START = intPreferencesKey("lockdown_boot_at_start")
        // سجل محلي لعدد المحاولات المصدودة يومياً — لا يغادر الجهاز.
        val BLOCK_LOG = stringPreferencesKey("block_log")
        val BLOCK_TOTAL = intPreferencesKey("block_total")
        val URGE_LOG = stringPreferencesKey("urge_log")
        val URGE_TOTAL = intPreferencesKey("urge_total")
        // العلم الوحيد الذي يمنع أي إيقاظ تلقائي للحماية — لا يضبطه إلا السحب من داخل
        // التطبيق (زر الإيقاف مع PIN عند وضع الانضباط/القفل الزمني). السحب من قائمة
        // المهام الأخيرة لا يلمسه أبدًا؛ هو وحده "المُوقِف" وفق المخطط.
        val USER_DISABLED_PROTECTION = booleanPreferencesKey("user_disabled_protection")
        // --- Plan B (2.1) -----------------------------------------------------
        val NIGHT_GUARD_ENABLED = booleanPreferencesKey("night_guard_enabled")
        val NIGHT_GUARD_START_MIN = intPreferencesKey("night_guard_start_min")
        val NIGHT_GUARD_END_MIN = intPreferencesKey("night_guard_end_min")
        val NIGHT_GUARD_UNLOCK_UNTIL = longPreferencesKey("night_guard_unlock_until")
        val NIGHT_GUARD_FORCED_STRICT = booleanPreferencesKey("night_guard_forced_strict")
        val NIGHT_GUARD_FORCED_ALWAYS_ON = booleanPreferencesKey("night_guard_forced_always_on")
        val NIGHT_GUARD_FORCED_MASTER = booleanPreferencesKey("night_guard_forced_master")
        val NIGHT_GUARD_PREVIOUS_STRICT = booleanPreferencesKey("night_guard_previous_strict")
        val NIGHT_GUARD_PREVIOUS_ALWAYS_ON = booleanPreferencesKey("night_guard_previous_always_on")
        val NIGHT_GUARD_PREVIOUS_MASTER = booleanPreferencesKey("night_guard_previous_master")
        val NIGHT_GUARD_PREVIOUS_USER_DISABLED = booleanPreferencesKey("night_guard_previous_user_disabled")
        val DEEP_FOCUS_ENABLED = booleanPreferencesKey("deep_focus_enabled")
        val DEEP_FOCUS_WHITELIST = stringSetPreferencesKey("deep_focus_whitelist")
        val DEEP_FOCUS_UNLOCK_UNTIL = longPreferencesKey("deep_focus_unlock_until")
        val DEEP_FOCUS_UNLOCK_AT = longPreferencesKey("deep_focus_unlock_at")
        val PARENT_MODE_ENABLED = booleanPreferencesKey("parent_mode_enabled")
        val PARENT_PIN_HASH = stringPreferencesKey("parent_pin_hash")
        val PARENT_PIN_SALT = stringPreferencesKey("parent_pin_salt")
        val ACCOUNTABILITY_NAME = stringPreferencesKey("accountability_name")
        val ACCOUNTABILITY_CONTACT = stringPreferencesKey("accountability_contact")
        val NUDGES_ENABLED = booleanPreferencesKey("nudges_enabled")
        // --- التحديث الذاتي (2.2.0) ---------------------------------------------
        // الافتراضي مفعّل: المستخدم يتوقع أن يتلقى التحديث دون أن يبحث عنه.
        // الفحص والتنزيل والتحقق كلها تلقائية؛ ما يبقى للمستخدم هو الموافقة
        // النهائية على التثبيت — لأن أندرويد لا يسمح بالتثبيت الصامت.
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val AUTO_NOTIFY_ENABLED = booleanPreferencesKey("auto_notify_enabled")
        // التحديث الذي نُزِّل وتحقّقنا منه، بانتظار موافقة المستخدم.
        val PENDING_UPDATE_VERSION = intPreferencesKey("pending_update_version")
        val PENDING_UPDATE_NAME = stringPreferencesKey("pending_update_name")
        val PENDING_UPDATE_SHA = stringPreferencesKey("pending_update_sha")
        val PENDING_UPDATE_CHANGELOG = stringPreferencesKey("pending_update_changelog")
        val PENDING_UPDATE_PATH = stringPreferencesKey("pending_update_path")
    }

    /** A PIN is only usable when both its hash and salt are present. */
    private fun pinPresent(p: Preferences): Boolean =
        p[Keys.PIN_HASH] != null && p[Keys.PIN_SALT] != null

    /**
     * The authoritative lockdown window. Within one boot the elapsed anchor is used, so a
     * forward wall-clock change cannot end an active lock; across a boot it falls back to
     * the wall deadline protected by the high-water mark.
     */
    private fun lockdownWindow(p: Preferences, wallNow: Long = System.currentTimeMillis()): LockdownWindow =
        LockdownWindow(
            wallUntil = p[Keys.LOCKDOWN_UNTIL] ?: 0L,
            elapsedEnd = p[Keys.LOCKDOWN_ELAPSED_END] ?: 0L,
            startBootCount = p[Keys.LOCKDOWN_BOOT_AT_START] ?: -1,
            currentBootCount = currentBootCount(),
            elapsedNow = SystemClock.elapsedRealtime(),
            wallNow = maxOf(wallNow, p[Keys.SEEN_TIME_HIGHWAT] ?: 0L),
        )

    /** A lockdown that leaves the core filter weak is meaningless: force it on atomically. */
    private fun forceLockdownCore(p: MutablePreferences) {
        p[Keys.MASTER_ENABLED] = true
        p[Keys.ALWAYS_ON] = true
        p[Keys.PORN_GUARD] = true
        p[Keys.SAFE_SEARCH_FORCE] = true
        p[Keys.BLOCK_DOH] = true
    }

    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: false }.distinctUntilChanged()
    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun setOnboardingDone() =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }.let { }

    /** يعيد تفعيل الترحيب («إعادة عرض المقدمة» من الإعدادات — لا يمسح بيانات الحماية). */
    suspend fun clearOnboardingDone() =
        context.dataStore.edit { it.remove(Keys.ONBOARDING_DONE) }.let { }

    // --- الملف الشخصي المحلي ---------------------------------------------------
    // اسم + عمر محليان 100% — لا يغادران الجهاز أبداً (انظر docs/privacy.md).
    val profileName: Flow<String?> =
        context.dataStore.data.map { it[Keys.PROFILE_NAME] }.distinctUntilChanged()
    val profileAge: Flow<Int?> =
        context.dataStore.data.map { it[Keys.PROFILE_AGE] }.distinctUntilChanged()

    suspend fun setProfile(name: String?, age: Int?) =
        context.dataStore.edit { p ->
            if (name == null) p.remove(Keys.PROFILE_NAME) else p[Keys.PROFILE_NAME] = name
            if (age == null) p.remove(Keys.PROFILE_AGE) else p[Keys.PROFILE_AGE] = age
        }.let { }

    /** الإجابات كما هي (نص خام مفصول بـ |) — تُستخدم لإعادة بناء الخطة لاحقاً. */
    val onboardingAnswers: Flow<String?> =
        context.dataStore.data.map { it[Keys.ONBOARDING_ANSWERS] }.distinctUntilChanged()

    suspend fun setOnboardingAnswers(value: String) =
        context.dataStore.edit { it[Keys.ONBOARDING_ANSWERS] = value }.let { }

    // --- التحديث الذاتي (2.2.0) ------------------------------------------------
    // الافتراضي مفعّل: المستخدم يتوقّع أن يتلقّى التحديث دون أن يبحث عنه بنفسه.
    // الفحص والتنزيل والتحقّق كلها تلقائية؛ ما يبقى للمستخدم هو الموافقة
    // النهائية على التثبيت — لأن أندرويد لا يسمح لأي تطبيق بتثبيت صامت.
    val autoUpdateEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_UPDATE_ENABLED] ?: true }.distinctUntilChanged()
    val autoDownloadEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_DOWNLOAD_ENABLED] ?: true }.distinctUntilChanged()
    /** إشعار «التحديث جاهز» فور توفّره — مفعّل افتراضياً. */
    val autoNotifyEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_NOTIFY_ENABLED] ?: true }.distinctUntilChanged()

    suspend fun setAutoUpdateEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_ENABLED] = value }.let { }
    }

    suspend fun setAutoDownloadEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DOWNLOAD_ENABLED] = value }.let { }
    }

    suspend fun setAutoNotifyEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_NOTIFY_ENABLED] = value }.let { }
    }

    /** التحديث الذي نُزِّل وتحقّقنا منه — ينتظر موافقة المستخدم على التثبيت. */
    val pendingUpdate: Flow<PendingUpdate?> = context.dataStore.data
        .map { p ->
            val version = p[Keys.PENDING_UPDATE_VERSION] ?: return@map null
            val path = p[Keys.PENDING_UPDATE_PATH] ?: return@map null
            PendingUpdate(
                versionCode = version,
                versionName = p[Keys.PENDING_UPDATE_NAME] ?: "",
                sha256 = p[Keys.PENDING_UPDATE_SHA] ?: "",
                changelog = p[Keys.PENDING_UPDATE_CHANGELOG] ?: "",
                apkPath = path,
            )
        }
        .distinctUntilChanged()

    suspend fun setPendingUpdate(update: PendingUpdate) {
        context.dataStore.edit { p ->
            p[Keys.PENDING_UPDATE_VERSION] = update.versionCode
            p[Keys.PENDING_UPDATE_NAME] = update.versionName
            p[Keys.PENDING_UPDATE_SHA] = update.sha256
            p[Keys.PENDING_UPDATE_CHANGELOG] = update.changelog
            p[Keys.PENDING_UPDATE_PATH] = update.apkPath
        }.let { }
    }

    suspend fun clearPendingUpdate() {
        context.dataStore.edit { p ->
            p.remove(Keys.PENDING_UPDATE_VERSION)
            p.remove(Keys.PENDING_UPDATE_NAME)
            p.remove(Keys.PENDING_UPDATE_SHA)
            p.remove(Keys.PENDING_UPDATE_CHANGELOG)
            p.remove(Keys.PENDING_UPDATE_PATH)
        }.let { }
    }

    val alwaysOn: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ALWAYS_ON] ?: true }.distinctUntilChanged()
    val strictMode: Flow<Boolean> = context.dataStore.data
        .map { StrictPolicy.isEffectiveStrictMode(it[Keys.STRICT_MODE] ?: false, pinPresent(it)) }
        .distinctUntilChanged()
    val blockDoh: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BLOCK_DOH] ?: false }.distinctUntilChanged()
    val blockAds: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BLOCK_ADS] ?: true }.distinctUntilChanged()
    val blockNotifications: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BLOCK_NOTIFICATIONS] ?: false }.distinctUntilChanged()

    // --- Porn Guard -----------------------------------------------------------
    // Missing key reads as ON (fresh installs are protected by default).
    val pornGuard: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PORN_GUARD] ?: true }.distinctUntilChanged()
    val safeSearchForce: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SAFE_SEARCH_FORCE] ?: true }.distinctUntilChanged()
    /** Runtime-merged extra adult domains fetched from open blocklists. */
    val adultExtraDomains: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ADULT_EXTRA_DOMAINS] ?: emptySet() }.distinctUntilChanged()

    suspend fun setPornGuard(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.PORN_GUARD] = value
        }
    }

    suspend fun setSafeSearchForce(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.SAFE_SEARCH_FORCE] = value
        }
    }

    suspend fun setAdultExtraDomains(domains: Set<String>) =
        context.dataStore.edit { it[Keys.ADULT_EXTRA_DOMAINS] = domains }.let { }

    // --- إمكانية الوصول الرسومي ---------------------------------------------
    /** تقليل حركة واجهة (نبض الدرع). Default false: الحركة حيّة إفتراضياً. */
    val reduceMotion: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }.distinctUntilChanged()

    suspend fun setReduceMotion(value: Boolean) =
        context.dataStore.edit { it[Keys.REDUCE_MOTION] = value }.let { }

    // --- اللغة ----------------------------------------------------------------
    /** "" = تابع للنظام · "ar" / "en" — تُطبّق في SawnApp.onCreate. */
    val locale: Flow<String> =
        context.dataStore.data.map { it[Keys.APP_LOCALE] ?: "" }.distinctUntilChanged()

    suspend fun setLocale(value: String) =
        context.dataStore.edit { it[Keys.APP_LOCALE] = value }.let { }

    /** هل أوقف المستخدم الحماية يدويًا من داخل التطبيق؟ — هو وحده يمنع الإيقاظ التلقائي. */
    val userDisabledProtection: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.USER_DISABLED_PROTECTION] ?: false }.distinctUntilChanged()

    /**
     * يمنع إيقاظ الحماية أبدًا ما لم يُطلب من داخل التطبيق (زر إيقاف مع PIN عند الانضباط).
     * يُعاد تفعيله عبر [startProtectionRequested] عند أي محاولة حماية ناجحة من داخل التطبيق.
     */
    suspend fun setUserDisabledProtection(value: Boolean) =
        context.dataStore.edit { it[Keys.USER_DISABLED_PROTECTION] = value }.let { }

    suspend fun clearUserDisabledProtection() =
        context.dataStore.edit { it.remove(Keys.USER_DISABLED_PROTECTION) }.let { }

    /** يمسح كل التفضيلات — للخصوصية: «مسح كل بياناتي». */
    suspend fun clearAllData() =
        context.dataStore.edit { p ->
            p.clear()
            p[Keys.USER_DISABLED_PROTECTION] = true
            p[Keys.PARENT_MODE_ENABLED] = false
        }.let { }

    suspend fun setBlockDoh(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.BLOCK_DOH] = value
        }
    }

    suspend fun setBlockAds(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.BLOCK_ADS] = value
        }
    }

    suspend fun setBlockNotifications(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.BLOCK_NOTIFICATIONS] = value
        }
    }

    /** وجود رقم سري = استخدام صالح. */
    val hasPin: Flow<Boolean> =
        context.dataStore.data.map { pinPresent(it) }.distinctUntilChanged()
    /**
     * سُجلّ "أيام الحماية" — مصدر الحقيقة للسلسلة في Room (protected_days).
     * مُحقون عبر [protectedDays] عند توفره (إنتاج)، وهنا يبدأ من صفر.
     */
    val enabledFeatureKeys: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ENABLED_FEATURES] ?: emptySet() }.distinctUntilChanged()
    val socialGuard: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SOCIAL_GUARD] ?: false }.distinctUntilChanged()
    val enabledSocialPlatforms: Flow<Set<String>> =
        context.dataStore.data.map {
            it[Keys.ENABLED_SOCIAL_PLATFORMS] ?: emptySet()
        }.distinctUntilChanged()

    /** Epoch millis a focus session ends (0 = none), and whether it can be cancelled early. */
    val focusEndsAt: Flow<Long> =
        context.dataStore.data.map { it[Keys.FOCUS_ENDS_AT] ?: 0L }.distinctUntilChanged()
    val focusStrict: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FOCUS_STRICT] ?: false }.distinctUntilChanged()

    suspend fun startFocus(endsAt: Long, strict: Boolean) =
        context.dataStore.edit { it[Keys.FOCUS_ENDS_AT] = endsAt; it[Keys.FOCUS_STRICT] = strict }.let { }

    suspend fun clearFocus() =
        context.dataStore.edit { it.remove(Keys.FOCUS_ENDS_AT); it.remove(Keys.FOCUS_STRICT) }.let { }

    val focusSessionsDone: Flow<Int> =
        context.dataStore.data.map { it[Keys.FOCUS_SESSIONS_DONE] ?: 0 }.distinctUntilChanged()

    suspend fun incrementFocusSessions() =
        context.dataStore.edit { it[Keys.FOCUS_SESSIONS_DONE] = (it[Keys.FOCUS_SESSIONS_DONE] ?: 0) + 1 }.let { }

    suspend fun setFeatureEnabled(key: String, enabled: Boolean) {
        context.dataStore.edit { p ->
            if (!enabled && lockdownWindow(p).isActive) return@edit
            val current = (p[Keys.ENABLED_FEATURES] ?: emptySet()).toMutableSet()
            if (enabled) current.add(key) else current.remove(key)
            p[Keys.ENABLED_FEATURES] = current
        }
    }

    suspend fun setEnabledFeatureKeys(keys: Set<String>) {
        context.dataStore.edit { p ->
            val current = p[Keys.ENABLED_FEATURES] ?: emptySet()
            if (lockdownWindow(p).isActive && !keys.containsAll(current)) return@edit
            p[Keys.ENABLED_FEATURES] = keys
        }
    }

    suspend fun setSocialGuardEnabled(enabled: Boolean) {
        context.dataStore.edit { p ->
            if (!enabled && lockdownWindow(p).isActive) return@edit
            p[Keys.SOCIAL_GUARD] = enabled
        }
    }

    suspend fun setEnabledSocialPlatforms(platforms: Set<String>) =
        context.dataStore.edit { p -> p[Keys.ENABLED_SOCIAL_PLATFORMS] = platforms }.let { }

    suspend fun setSocialPlatformsEnabled(platforms: Set<String>) {
        context.dataStore.edit { p ->
            val current = p[Keys.ENABLED_SOCIAL_PLATFORMS] ?: emptySet()
            if (lockdownWindow(p).isActive && !platforms.containsAll(current)) return@edit
            p[Keys.ENABLED_SOCIAL_PLATFORMS] = platforms
        }
    }

    /**
     * Mark today as a protected day — مصدر الحقيقة هو جدول protected_days في Room.
     * إذا وُفِّر [protectedDays] (إنتاج) يُسجَّل اليوم هناك (idempotent)؛ وإلا لا شيء
     * يُخزَّن محليًا. آمنة للتكرار في اليوم الواحد.
     */
    suspend fun recordActiveDay() {
        protectedDays?.upsert(LocalDate.now(), "VPN")
    }

    /**
     * محاولة محجوبة رُفضت (حجبٌ تام، توقّفٌ واعٍ، محتوى داخل تطبيق، شاشة إباحية).
     * عدّاد فقط لكل يوم محلي، ويُقلَّم بعد أسبوعين. لا يُخزَّن اسم موقع ولا تطبيق.
     */
    suspend fun recordBlockEvent() {
        val today = LocalDate.now().toEpochDay()
        context.dataStore.edit { p ->
            val log = BlockJournal.bump(BlockJournal.decode(p[Keys.BLOCK_LOG]), today)
            p[Keys.BLOCK_LOG] = BlockJournal.encode(log)
            p[Keys.BLOCK_TOTAL] = (p[Keys.BLOCK_TOTAL] ?: 0) + 1
        }
    }

    suspend fun recordUrgeSessionCompleted() {
        val today = LocalDate.now().toEpochDay()
        context.dataStore.edit { p ->
            p[Keys.URGE_LOG] = BlockJournal.encode(BlockJournal.bump(BlockJournal.decode(p[Keys.URGE_LOG]), today))
            p[Keys.URGE_TOTAL] = (p[Keys.URGE_TOTAL] ?: 0) + 1
        }
    }

    val blockJournal: Flow<JournalSnapshot> = context.dataStore.data
        .map { p ->
            BlockJournal.snapshot(
                log = BlockJournal.decode(p[Keys.BLOCK_LOG]),
                total = p[Keys.BLOCK_TOTAL] ?: 0,
                today = LocalDate.now().toEpochDay(),
                urges = BlockJournal.decode(p[Keys.URGE_LOG]),
                urgeTotal = p[Keys.URGE_TOTAL] ?: 0,
            )
        }
        .distinctUntilChanged()

    /** يطبّق نمط حماية بضغطة، دون إضعاف قفلٍ زمني نشط. */
    suspend fun applyPreset(preset: ShieldPreset) {
        context.dataStore.edit { p ->
            val locked = lockdownWindow(p).isActive
            fun put(key: Preferences.Key<Boolean>, value: Boolean) {
                if (locked && !value) return
                p[key] = value
            }
            put(Keys.PORN_GUARD, preset.pornGuard)
            put(Keys.SAFE_SEARCH_FORCE, preset.safeSearch)
            put(Keys.BLOCK_ADS, preset.blockAds)
            put(Keys.BLOCK_DOH, preset.blockDoh)
            put(Keys.BLOCK_NOTIFICATIONS, preset.blockNotifications)
        }
    }

    suspend fun setMasterEnabled(value: Boolean) {
        context.dataStore.edit { p ->
            // The store is the enforcement point, not the UI: any caller trying to turn
            // protection off during a live lockdown is refused atomically.
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.MASTER_ENABLED] = value
        }
        // تفعيل الحماية اليوم = يوم محمي. بلا هذا السطر يبقى عدّاد الأيام صفراً حتى
        // يُغلق المستخدم التطبيق ويفتحه من جديد.
        if (value) {
            recordActiveDay()
            clearUserDisabledProtection()
        } else {
            setUserDisabledProtection(true)
        }
    }

    suspend fun setAlwaysOn(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.ALWAYS_ON] = value
        }
    }

    /**
     * استعادة علم الحماية من نسخة احتياطية: يكتب العلم دون副作用 «تسجيل اليوم
     * المحمي»، فالاستعادة ليست يوماً محمياً جديداً.
     */
    suspend fun restoreMasterEnabled(value: Boolean) {
        context.dataStore.edit { p ->
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.MASTER_ENABLED] = value
        }
    }

    /** Returns true when the change was applied; enabling requires a usable PIN. */
    suspend fun setStrictMode(value: Boolean): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            if (value && !StrictPolicy.canEnableStrictMode(pinPresent(p))) return@edit
            if (!value && lockdownWindow(p).isActive) return@edit
            p[Keys.STRICT_MODE] = value
            applied = true
        }
        return applied
    }

    /**
     * Atomically enable strict mode and make any live focus session strict too, so there is
     * never a moment where strict is on but the running session can still be cancelled.
     * Requires a PIN; returns false when that precondition fails.
     */
    suspend fun enableStrictMode(now: Long): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            if (!StrictPolicy.canEnableStrictMode(pinPresent(p))) return@edit
            p[Keys.STRICT_MODE] = true
            val endsAt = p[Keys.FOCUS_ENDS_AT] ?: 0L
            if (endsAt > now) p[Keys.FOCUS_STRICT] = true
            applied = true
        }
        return applied
    }

    suspend fun setPin(hash: String, salt: String) = context.dataStore.edit { p ->
        // A legacy strict flag with no PIN was never effective; do not silently activate it
        // the moment a PIN is created. (The Settings flow explicitly re-enables strict after
        // PIN creation when that was the user's intent.)
        val staleStrictWithoutPin = (p[Keys.STRICT_MODE] ?: false) && !pinPresent(p)
        p[Keys.PIN_HASH] = hash
        p[Keys.PIN_SALT] = salt
        if (staleStrictWithoutPin) p[Keys.STRICT_MODE] = false
    }.let { }

    /** Returns true when the PIN was removed; strict mode keeps its credential in place. */
    suspend fun clearPin(): Boolean {
        var cleared = false
        context.dataStore.edit { p ->
            val pin = pinPresent(p)
            val effectiveStrict = StrictPolicy.isEffectiveStrictMode(
                p[Keys.STRICT_MODE] ?: false,
                pin,
            )
            if (!StrictPolicy.canClearPin(effectiveStrict, pin)) return@edit
            if (lockdownWindow(p).isActive) return@edit
            p.remove(Keys.PIN_HASH)
            p.remove(Keys.PIN_SALT)
            cleared = true
        }
        return cleared
    }

    /** Consistent snapshot for enforcement paths that must not mix values from different edits. */
    suspend fun protectionSnapshot(): ProtectionSnapshot {
        // A corrupted/unreadable DataStore must fail safe, not throw into the UI thread.
        val p = runCatching { context.dataStore.data.first() }.getOrNull()
            ?: return ProtectionSnapshot(
                masterEnabled = false, alwaysOn = true, strictMode = false,
                focusEndsAt = 0L, focusStrict = false, blockDoh = false, blockAds = true,
                hasPin = false, pornGuard = true, safeSearchForce = true,
                lockdownUntil = 0L, lockdownElapsedEnd = 0L, lockdownBootAtStart = -1,
            )
        val pin = pinPresent(p)
        return ProtectionSnapshot(
            masterEnabled = p[Keys.MASTER_ENABLED] ?: false,
            alwaysOn = p[Keys.ALWAYS_ON] ?: true,
            strictMode = StrictPolicy.isEffectiveStrictMode(p[Keys.STRICT_MODE] ?: false, pin),
            focusEndsAt = p[Keys.FOCUS_ENDS_AT] ?: 0L,
            focusStrict = p[Keys.FOCUS_STRICT] ?: false,
            blockDoh = p[Keys.BLOCK_DOH] ?: false,
            blockAds = p[Keys.BLOCK_ADS] ?: true,
            hasPin = pin,
            pornGuard = p[Keys.PORN_GUARD] ?: true,
            safeSearchForce = p[Keys.SAFE_SEARCH_FORCE] ?: true,
            lockdownUntil = p[Keys.LOCKDOWN_UNTIL] ?: 0L,
            lockdownElapsedEnd = p[Keys.LOCKDOWN_ELAPSED_END] ?: 0L,
            lockdownBootAtStart = p[Keys.LOCKDOWN_BOOT_AT_START] ?: -1,
        )
    }

    /**
     * Re-check strict/focus locks and turn the master switch off in the same DataStore
     * transaction, so a focus session starting concurrently cannot be undercut by a check
     * that used an older snapshot. [DisableOutcome.focusActive] tells the caller whether the
     * tunnel must stay up (a focus session is still forcing blocking on).
     */
    suspend fun disableProtection(now: Long): DisableOutcome {
        var outcome = DisableOutcome(disabled = false, focusActive = false)
        context.dataStore.edit { p ->
            val pin = pinPresent(p)
            val strict = StrictPolicy.isEffectiveStrictMode(p[Keys.STRICT_MODE] ?: false, pin)
            val master = p[Keys.MASTER_ENABLED] ?: false
            val focusStrict = p[Keys.FOCUS_STRICT] ?: false
            val focusEndsAt = p[Keys.FOCUS_ENDS_AT] ?: 0L
            val focusActive = StrictPolicy.isFocusActive(focusEndsAt, now)
            val lockdownActive = lockdownWindow(p, now).isActive
            val locked = lockdownActive ||
                StrictPolicy.isMasterLocked(strict, master, focusStrict, focusEndsAt, now)
            if (!locked) p[Keys.MASTER_ENABLED] = false
            outcome = DisableOutcome(disabled = !locked, focusActive = focusActive)
        }
        return outcome
    }

    // --- PIN brute-force protection ---
    suspend fun pinLockRemainingMillis(): Long {
        val p = runCatching { context.dataStore.data.first() }.getOrNull() ?: return 0L
        return PinLockoutPolicy.remainingMillis(
            wallLockUntil = p[Keys.PIN_LOCK_UNTIL] ?: 0L,
            elapsedLockUntil = p[Keys.PIN_LOCK_ELAPSED_UNTIL] ?: 0L,
            lockBootCount = p[Keys.PIN_LOCK_BOOT_COUNT] ?: -1,
            currentBootCount = currentBootCount(),
            wallNow = System.currentTimeMillis(),
            elapsedNow = SystemClock.elapsedRealtime(),
        )
    }

    suspend fun recordPinSuccess() = context.dataStore.edit { p ->
        p.remove(Keys.PIN_FAILS)
        p.remove(Keys.PIN_LOCK_UNTIL)
        p.remove(Keys.PIN_LOCK_ELAPSED_UNTIL)
        p.remove(Keys.PIN_LOCK_BOOT_COUNT)
    }.let { }

    /** Returns the remaining lock in milliseconds, or zero when another attempt is allowed. */
    suspend fun recordPinFailure(): Long {
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        var lockRemaining = 0L
        context.dataStore.edit { p ->
            val fails = (p[Keys.PIN_FAILS] ?: 0) + 1
            p[Keys.PIN_FAILS] = fails
            lockRemaining = PinLockoutPolicy.lockDurationMillis(fails)
            if (lockRemaining > 0L) {
                p[Keys.PIN_LOCK_UNTIL] = wallNow + lockRemaining
                p[Keys.PIN_LOCK_ELAPSED_UNTIL] = elapsedNow + lockRemaining
                p[Keys.PIN_LOCK_BOOT_COUNT] = bootCount
            }
        }
        return lockRemaining
    }

    fun currentBootCount(): Int = runCatching {
        android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.BOOT_COUNT,
        )
    }.getOrDefault(-1)

    /** Returns (hash, salt) or null if no PIN configured. */
    suspend fun pinCredentials(): Pair<String, String>? {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull() ?: return null
        val h = prefs[Keys.PIN_HASH]
        val s = prefs[Keys.PIN_SALT]
        return if (h != null && s != null) h to s else null
    }

    // --- القفل الزمني (Lockdown) -------------------------------------------------
    /** 0 = بلا قفل؛ غير ذلك millis انتهاء المدة. القفل مطلق: يُرفع فقط بانتهائه. */
    val lockdownUntil: Flow<Long> =
        context.dataStore.data.map { it[Keys.LOCKDOWN_UNTIL] ?: 0L }.distinctUntilChanged()
    val lockdownElapsedEnd: Flow<Long> =
        context.dataStore.data.map { it[Keys.LOCKDOWN_ELAPSED_END] ?: 0L }.distinctUntilChanged()
    val lockdownBootAtStart: Flow<Int> =
        context.dataStore.data.map { it[Keys.LOCKDOWN_BOOT_AT_START] ?: -1 }.distinctUntilChanged()

    /** نافذة القفل الحالية مع لحظة الحساب — الواجهة تعيد الحساب كل ثانية عبر [LockdownWindow.remainingAt]. */
    val lockdownWindowState: Flow<LockdownWindow> =
        context.dataStore.data.map { lockdownWindow(it) }.distinctUntilChanged()

    /** True while the lockdown window is live (elapsed-anchored within a boot). */
    suspend fun isLockdownActiveNow(): Boolean =
        runCatching { lockdownWindow(context.dataStore.data.first()).isActive }.getOrDefault(false)

    suspend fun lockdownRemainingMillis(): Long =
        runCatching { lockdownWindow(context.dataStore.data.first()).remainingMillis }.getOrDefault(0L)

    /** Returns true when the value was applied or cleared; false when a live lock refused it. */
    suspend fun setLockdownUntil(endsAtMs: Long): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            val wallNow = maxOf(System.currentTimeMillis(), p[Keys.SEEN_TIME_HIGHWAT] ?: 0L)
            // The lock belongs to the user's past self: while it is live only a strict extension
            // is accepted. Clearing or shortening it is refused atomically here, so no UI path
            // and no stray caller can lift it before its timer ends.
            if (!LockdownPolicy.mayApplyLockdown(p[Keys.LOCKDOWN_UNTIL] ?: 0L, endsAtMs, wallNow)) return@edit
            p[Keys.LOCKDOWN_UNTIL] = endsAtMs
            if (endsAtMs > wallNow) {
                val duration = endsAtMs - wallNow
                p[Keys.LOCKDOWN_ELAPSED_END] = SystemClock.elapsedRealtime() + duration
                p[Keys.LOCKDOWN_BOOT_AT_START] = currentBootCount()
                forceLockdownCore(p)
            } else {
                p[Keys.LOCKDOWN_ELAPSED_END] = 0L
                p[Keys.LOCKDOWN_BOOT_AT_START] = -1
            }
            applied = true
        }
        return applied
    }

    /** Re-assert the core filter flags for a live lock (used after boot/package replace). */
    suspend fun enforceLockdownCore() {
        context.dataStore.edit { p ->
            if (!lockdownWindow(p).isActive) return@edit
            forceLockdownCore(p)
        }
    }

    /** Re-anchor the monotonic deadline after a reboot; the lock value itself is unchanged. */
    suspend fun reanchorLockdown() {
        context.dataStore.edit { p ->
            val wallNow = maxOf(System.currentTimeMillis(), p[Keys.SEEN_TIME_HIGHWAT] ?: 0L)
            val until = p[Keys.LOCKDOWN_UNTIL] ?: 0L
            if (until > wallNow) {
                p[Keys.LOCKDOWN_ELAPSED_END] = SystemClock.elapsedRealtime() + (until - wallNow)
                p[Keys.LOCKDOWN_BOOT_AT_START] = currentBootCount()
            } else {
                p[Keys.LOCKDOWN_ELAPSED_END] = 0L
                p[Keys.LOCKDOWN_BOOT_AT_START] = -1
            }
        }
    }

    // --- Clock-tamper defense (Tempo's lastSeen high-water mark) ---
    // The only trusted "now": at least as late as any moment we've ever observed the system
    // report (and we observe it whenever anything checks time). Rolling the clock back can
    // no longer shorten schedules, focus sessions, cooldowns, or strict locks.
    suspend fun trustedNow(): Long {
        val sys = System.currentTimeMillis()
        var now = sys
        runCatching {
            context.dataStore.edit { p ->
                val hw = p[Keys.SEEN_TIME_HIGHWAT] ?: 0L
                if (sys > hw) { p[Keys.SEEN_TIME_HIGHWAT] = sys; }
                now = maxOf(sys, hw)
            }
        }
        return now
    }

    /**
     * Read-only clock for hot paths (the VPN's periodic refresh). Unlike [trustedNow] it
     * never writes, so it cannot re-trigger the DataStore flows that drive those refreshes.
     */
    suspend fun peekNow(): Long {
        val sys = System.currentTimeMillis()
        val hw = runCatching {
            context.dataStore.data.first()[Keys.SEEN_TIME_HIGHWAT] ?: 0L
        }.getOrDefault(0L)
        return maxOf(sys, hw)
    }

    // --- Escalating pause friction (PlugBrain-style difficulty floor, inverted) ---
    // Each "Not now — take me home" exit from an active pause adds one defeat today; the
    // pause wait added to every PAUSE item's next attempt grows with it. Resets daily.
    suspend fun recordPauseDefeat(now: Long) {
        context.dataStore.edit { p ->
            val day = p[Keys.PAUSE_DEFEATS_DATE] ?: 0L
            val dayOfNow = now / 86_400_000L
            val count = if (day == dayOfNow) (p[Keys.PAUSE_DEFEATS] ?: 0) + 1 else 1
            p[Keys.PAUSE_DEFEATS_DATE] = dayOfNow
            p[Keys.PAUSE_DEFEATS] = count
        }
    }

    suspend fun pauseDefeatsToday(now: Long): Int {
        val dayOfNow = now / 86_400_000L
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull() ?: return 0
        return if ((prefs[Keys.PAUSE_DEFEATS_DATE] ?: 0L) == dayOfNow) prefs[Keys.PAUSE_DEFEATS] ?: 0 else 0
    }
    // --- Plan B (2.1) accessors -------------------------------------------
    val nightGuardEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NIGHT_GUARD_ENABLED] ?: false }.distinctUntilChanged()
    val nightGuardStartMin: Flow<Int> =
        context.dataStore.data.map { it[Keys.NIGHT_GUARD_START_MIN] ?: 1320 }.distinctUntilChanged()
    val nightGuardEndMin: Flow<Int> =
        context.dataStore.data.map { it[Keys.NIGHT_GUARD_END_MIN] ?: 360 }.distinctUntilChanged()
    val nightGuardUnlockUntil: Flow<Long> =
        context.dataStore.data.map { it[Keys.NIGHT_GUARD_UNLOCK_UNTIL] ?: 0L }.distinctUntilChanged()
    suspend fun setNightGuard(enabled: Boolean, startMin: Int, endMin: Int): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            if (!enabled && lockdownWindow(p).isActive) return@edit
            p[Keys.NIGHT_GUARD_ENABLED] = enabled
            p[Keys.NIGHT_GUARD_START_MIN] = startMin.coerceIn(0, 1439)
            p[Keys.NIGHT_GUARD_END_MIN] = endMin.coerceIn(0, 1439)
            if (!enabled) p[Keys.NIGHT_GUARD_UNLOCK_UNTIL] = 0L
            applied = true
        }
        return applied
    }
    suspend fun setNightGuardUnlock(untilMs: Long) =
        context.dataStore.edit { it[Keys.NIGHT_GUARD_UNLOCK_UNTIL] = untilMs }.let { }

    suspend fun restoreNightGuardOverrides(): Boolean {
        var restored = false
        context.dataStore.edit { p ->
            val forcedStrict = p[Keys.NIGHT_GUARD_FORCED_STRICT] ?: false
            val forcedAlwaysOn = p[Keys.NIGHT_GUARD_FORCED_ALWAYS_ON] ?: false
            val forcedMaster = p[Keys.NIGHT_GUARD_FORCED_MASTER] ?: false
            if ((forcedStrict || forcedAlwaysOn || forcedMaster) && lockdownWindow(p).isActive) return@edit

            if (forcedStrict) p[Keys.STRICT_MODE] = p[Keys.NIGHT_GUARD_PREVIOUS_STRICT] ?: false
            if (forcedAlwaysOn) p[Keys.ALWAYS_ON] = p[Keys.NIGHT_GUARD_PREVIOUS_ALWAYS_ON] ?: false
            if (forcedMaster) {
                p[Keys.MASTER_ENABLED] = p[Keys.NIGHT_GUARD_PREVIOUS_MASTER] ?: false
                p[Keys.USER_DISABLED_PROTECTION] = p[Keys.NIGHT_GUARD_PREVIOUS_USER_DISABLED] ?: true
            }
            p[Keys.NIGHT_GUARD_FORCED_STRICT] = false
            p[Keys.NIGHT_GUARD_FORCED_ALWAYS_ON] = false
            p[Keys.NIGHT_GUARD_FORCED_MASTER] = false
            p.remove(Keys.NIGHT_GUARD_PREVIOUS_STRICT)
            p.remove(Keys.NIGHT_GUARD_PREVIOUS_ALWAYS_ON)
            p.remove(Keys.NIGHT_GUARD_PREVIOUS_MASTER)
            p.remove(Keys.NIGHT_GUARD_PREVIOUS_USER_DISABLED)
            p[Keys.NIGHT_GUARD_UNLOCK_UNTIL] = 0L
            restored = true
        }
        return restored
    }

    suspend fun applyNightGuardOverrides(): Boolean {
        var strictApplied = false
        var applied = false
        context.dataStore.edit { p ->
            if (lockdownWindow(p).isActive) {
                forceLockdownCore(p)
                return@edit
            }
            if (!(p[Keys.NIGHT_GUARD_FORCED_STRICT] ?: false) && pinPresent(p) && p[Keys.STRICT_MODE] != true) {
                p[Keys.NIGHT_GUARD_PREVIOUS_STRICT] = false
                p[Keys.NIGHT_GUARD_FORCED_STRICT] = true
                p[Keys.STRICT_MODE] = true
            }
            strictApplied = StrictPolicy.isEffectiveStrictMode(p[Keys.STRICT_MODE] ?: false, pinPresent(p))

            if (!(p[Keys.NIGHT_GUARD_FORCED_ALWAYS_ON] ?: false) && p[Keys.ALWAYS_ON] != true) {
                p[Keys.NIGHT_GUARD_PREVIOUS_ALWAYS_ON] = false
                p[Keys.NIGHT_GUARD_FORCED_ALWAYS_ON] = true
                p[Keys.ALWAYS_ON] = true
            }
            if (!(p[Keys.NIGHT_GUARD_FORCED_MASTER] ?: false) && p[Keys.MASTER_ENABLED] != true) {
                p[Keys.NIGHT_GUARD_PREVIOUS_MASTER] = false
                p[Keys.NIGHT_GUARD_PREVIOUS_USER_DISABLED] = p[Keys.USER_DISABLED_PROTECTION] ?: true
                p[Keys.NIGHT_GUARD_FORCED_MASTER] = true
                p[Keys.MASTER_ENABLED] = true
                p.remove(Keys.USER_DISABLED_PROTECTION)
            }
            applied = true
        }
        if (applied) recordActiveDay()
        return strictApplied
    }
    val deepFocusEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DEEP_FOCUS_ENABLED] ?: false }.distinctUntilChanged()
    val deepFocusWhitelist: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.DEEP_FOCUS_WHITELIST] ?: emptySet() }.distinctUntilChanged()
    suspend fun setDeepFocus(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DEEP_FOCUS_ENABLED] = enabled }.let { }
    suspend fun setDeepFocusWhitelist(values: Set<String>) =
        context.dataStore.edit { it[Keys.DEEP_FOCUS_WHITELIST] = values }.let { }
    val parentModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PARENT_MODE_ENABLED] ?: false }.distinctUntilChanged()
    suspend fun setParentModeEnabled(value: Boolean): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            if (value && !parentPinPresent(p)) return@edit
            p[Keys.PARENT_MODE_ENABLED] = value
            if (!value) {
                p.remove(Keys.PARENT_PIN_HASH)
                p.remove(Keys.PARENT_PIN_SALT)
            }
            applied = true
        }
        return applied
    }
    val accountabilityName: Flow<String?> =
        context.dataStore.data.map { it[Keys.ACCOUNTABILITY_NAME] }.distinctUntilChanged()
    val accountabilityContact: Flow<String?> =
        context.dataStore.data.map { it[Keys.ACCOUNTABILITY_CONTACT] }.distinctUntilChanged()
    suspend fun setAccountability(name: String?, contact: String?) =
        context.dataStore.edit { p ->
            if (name == null) p.remove(Keys.ACCOUNTABILITY_NAME) else p[Keys.ACCOUNTABILITY_NAME] = name
            if (contact == null) p.remove(Keys.ACCOUNTABILITY_CONTACT) else p[Keys.ACCOUNTABILITY_CONTACT] = contact
        }.let { }
    val nudgesEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NUDGES_ENABLED] ?: false }.distinctUntilChanged()
    suspend fun setNudgesEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.NUDGES_ENABLED] = value }.let { }

    // --- وضع الوالدين: رقم سري مستقل عن رقم المستخدم -----------------------------
    // رقم الوالدين لا يُخلط برقم المستخدم: الأول يحرس الإعدادات والثاني يفتحها.
    val parentPinPresent: Flow<Boolean> =
        context.dataStore.data.map { parentPinPresent(it) }.distinctUntilChanged()

    suspend fun setParentPin(hash: String, salt: String) =
        context.dataStore.edit { p ->
            p[Keys.PARENT_PIN_HASH] = hash
            p[Keys.PARENT_PIN_SALT] = salt
        }.let { }

    suspend fun clearParentPin(): Boolean {
        var cleared = false
        context.dataStore.edit { p ->
            if (p[Keys.PARENT_MODE_ENABLED] == true) return@edit
            p.remove(Keys.PARENT_PIN_HASH)
            p.remove(Keys.PARENT_PIN_SALT)
            cleared = true
        }
        return cleared
    }

    /** Hash + salt of the parent PIN, or null when it was never set. */
    suspend fun parentPinCredentials(): Pair<String, String>? {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull() ?: return null
        val hash = prefs[Keys.PARENT_PIN_HASH] ?: return null
        val salt = prefs[Keys.PARENT_PIN_SALT] ?: return null
        return hash to salt
    }

    private fun parentPinPresent(p: Preferences): Boolean =
        p[Keys.PARENT_PIN_HASH] != null && p[Keys.PARENT_PIN_SALT] != null

    // --- نافذة فكّ التركيز العميق ----------------------------------------------
    // لا يُفكّ التركيز العميق إلا برقم صحيح؛ بعدها مهلة قصيرة تكفي للانتقال بين
    // التطبيقات ثم يعود الحجب من تلقاء نفسه.
    val deepFocusUnlockUntil: Flow<Long> =
        context.dataStore.data.map { it[Keys.DEEP_FOCUS_UNLOCK_UNTIL] ?: 0L }.distinctUntilChanged()

    suspend fun clearDeepFocusUnlock() =
        context.dataStore.edit {
            it[Keys.DEEP_FOCUS_UNLOCK_UNTIL] = 0L
            it[Keys.DEEP_FOCUS_UNLOCK_AT] = 0L
        }.let { }

    /** لقطة واحدة كاملة لوضع التركيز العميق — يقرأها حارس الوصول عند كل قرار. */
    suspend fun deepFocusState(): DeepFocusState {
        val p = runCatching { context.dataStore.data.first() }.getOrNull()
            ?: return DeepFocusState()
        return DeepFocusState(
            enabled = p[Keys.DEEP_FOCUS_ENABLED] ?: false,
            masterEnabled = p[Keys.MASTER_ENABLED] ?: false,
            whitelist = p[Keys.DEEP_FOCUS_WHITELIST] ?: emptySet(),
            unlockStartsAt = p[Keys.DEEP_FOCUS_UNLOCK_AT] ?: 0L,
            unlockUntil = p[Keys.DEEP_FOCUS_UNLOCK_UNTIL] ?: 0L,
        )
    }

    /** يفتح نافذة الخروج بعد مهلة الانتظار: الأرقام هي [at] و[until]. */
    suspend fun openDeepFocusEscape(startsAt: Long, until: Long) =
        context.dataStore.edit {
            it[Keys.DEEP_FOCUS_UNLOCK_AT] = startsAt
            it[Keys.DEEP_FOCUS_UNLOCK_UNTIL] = until
        }.let { }

}
