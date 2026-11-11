package com.sawn.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sawn.domain.block.BlockingPolicy
import com.sawn.app.R
import com.sawn.domain.block.ScheduleEvaluator
import com.sawn.domain.block.StrictPolicy
import com.sawn.domain.model.BlockType
import com.sawn.core.data.SawnDatabase
import com.sawn.core.data.BlockedItem
import com.sawn.core.data.DisableOutcome
import com.sawn.domain.model.InterceptMode
import com.sawn.core.data.Schedule
import com.sawn.core.data.toDomain
import com.sawn.domain.OnboardingAnswers
import com.sawn.domain.feature.JournalSnapshot
import com.sawn.domain.feature.ShieldPreset
import com.sawn.app.security.AdultListFetcher
import com.sawn.domain.security.PinHasher
import com.sawn.domain.security.PinResult
import com.sawn.app.system.FocusEndWorker
import com.sawn.app.vpn.VpnController
import com.sawn.app.sawnContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.sawn.core.data.ChallengeRunEntity
import com.sawn.core.data.ChallengeDayEntity
import com.sawn.core.data.TriggerLogEntity
import com.sawn.domain.feature.NightGuardPolicy
import com.sawn.domain.feature.TriggerInsights
import com.sawn.domain.feature.TriggerTag
import com.sawn.domain.feature.TriggerLog
import com.sawn.domain.feature.BackupCrypto
import com.sawn.domain.feature.DeepFocusPolicy
import com.sawn.domain.feature.ParentalModePolicy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.content.Intent
import android.content.Context
import android.net.Uri
import com.sawn.domain.feature.BackupCodec
import com.sawn.domain.feature.TriggerInsightsSummary

class SawnViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.sawnContainer

    val items: StateFlow<List<BlockedItem>> =
        container.repository.items
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedules: StateFlow<List<Schedule>> =
        container.repository.schedules
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterEnabled: StateFlow<Boolean> =
        container.settings.masterEnabled
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val onboardingDone: StateFlow<Boolean> =
        container.settings.onboardingDone
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val onboardingAnswers: StateFlow<OnboardingAnswers?> =
        container.settings.onboardingAnswers
        .map(OnboardingAnswers::deserialize)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** False until DataStore has produced its first snapshot; UI treats that as locked. */
    val settingsReady: StateFlow<Boolean> = combine(
        container.settings.masterEnabled,
        container.settings.strictMode,
        container.settings.hasPin,
        container.settings.focusEndsAt,
        container.settings.onboardingDone,
    ) { _, _, _, _, _ -> true }
        // Fail-open: a DataStore read that throws must not pin the UI to the loading
        // spinner forever (the black screen). Each source already has its own catch.
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val profileName: StateFlow<String?> =
        container.settings.profileName
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val profileAge: StateFlow<Int?> =
        container.settings.profileAge
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Stores onboarding answers, applies the selected protection plan, then completes setup. */
    fun completeOnboarding(
        answers: OnboardingAnswers,
        plan: com.sawn.domain.Plan,
        onComplete: () -> Unit = {},
    ) = viewModelScope.launch {
        runCatching {
            applyPlan(plan)
            container.settings.setProfile(answers.name, answers.age)
            container.settings.setOnboardingAnswers(answers.serialize())
            container.settings.setOnboardingDone()
        }.onSuccess { onComplete() }
    }

    private suspend fun applyPlan(plan: com.sawn.domain.Plan) {
        val features = plan.featureIds.toSet()
        container.settings.setPornGuard(com.sawn.domain.PlanFeature.PORN in features)
        container.settings.setSafeSearchForce(com.sawn.domain.PlanFeature.SAFE_SEARCH in features)
        container.settings.setBlockAds(com.sawn.domain.PlanFeature.ADS in features)
        container.settings.setBlockDoh(com.sawn.domain.PlanFeature.DOH in features)
        container.settings.setBlockNotifications(com.sawn.domain.PlanFeature.NOTIFICATIONS in features)

        val feedsSelected = com.sawn.domain.PlanFeature.FEEDS in features
        val inAppFeatures = if (feedsSelected) {
            com.sawn.domain.feature.FeatureCatalog.ALL.mapTo(linkedSetOf()) { it.key }
        } else {
            emptySet()
        }
        container.settings.setEnabledFeatureKeys(inAppFeatures)
        container.settings.setSocialPlatformsEnabled(
            if (feedsSelected) com.sawn.domain.feature.SocialPlatform.allKeys else emptySet(),
        )
        container.settings.setSocialGuardEnabled(feedsSelected)
        if (com.sawn.domain.PlanFeature.STRICT in features && container.settings.hasPin.first()) {
            container.settings.enableStrictMode(container.settings.trustedNow())
        }
    }
    val strictMode: StateFlow<Boolean> =
        container.settings.strictMode
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /**
     * Global strict lock only. A strict focus session is time-bounded, so the Home screen
     * applies that part against its own ticking clock instead of a flow value that would go
     * stale the moment the session expires without a DataStore write.
     */
    val strictLocked: StateFlow<Boolean> = combine(
        container.settings.strictMode,
        container.settings.masterEnabled,
    ) { strict, enabled -> StrictPolicy.isMasterLocked(strict, enabled) }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val alwaysOn: StateFlow<Boolean> =
        container.settings.alwaysOn
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setAlwaysOn(value: Boolean) = viewModelScope.launch { container.settings.setAlwaysOn(value) }

    val blockDoh: StateFlow<Boolean> =
        container.settings.blockDoh
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setBlockDoh(value: Boolean) = viewModelScope.launch { container.settings.setBlockDoh(value) }

    val blockAds: StateFlow<Boolean> =
        container.settings.blockAds
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setBlockAds(value: Boolean) = viewModelScope.launch { container.settings.setBlockAds(value) }

    val blockNotifications: StateFlow<Boolean> =
        container.settings.blockNotifications
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setBlockNotifications(value: Boolean) = viewModelScope.launch { container.settings.setBlockNotifications(value) }

    // --- Porn Guard ---
    val pornGuard: StateFlow<Boolean> =
        container.settings.pornGuard
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val safeSearchForce: StateFlow<Boolean> =
        container.settings.safeSearchForce
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val adultExtraCount: StateFlow<Int> =
        container.settings.adultExtraDomains
            .map { it.size }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)// --- Accessibility (graphics) ---
    val reduceMotion: StateFlow<Boolean> =
        container.settings.reduceMotion
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setPornGuard(value: Boolean) = viewModelScope.launch { container.settings.setPornGuard(value) }
    fun setSafeSearchForce(value: Boolean) = viewModelScope.launch { container.settings.setSafeSearchForce(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { container.settings.setReduceMotion(value) }

    // --- التحديث الذاتي (2.2.0) ---
    /** مفعّل افتراضياً: المستخدم يتوقّع أن يتلقّى التحديث دون أن يبحث عنه. */
    val autoUpdateEnabled: StateFlow<Boolean> =
        container.settings.autoUpdateEnabled
            .catch { emit(true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoDownloadEnabled: StateFlow<Boolean> =
        container.settings.autoDownloadEnabled
            .catch { emit(true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoNotifyEnabled: StateFlow<Boolean> =
        container.settings.autoNotifyEnabled
            .catch { emit(true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    /**
     * تحديث نُزِّل وتُحقِّق منه، بانتظار موافقة المستخدم على التثبيت.
     *
     * يُخفى إن صار رقمه أقل أو مساوياً لنسخة التطبيق — أي بعد تثبيته فعلاً.
     * وإلّا لبقيت البطاقة تعرض «الإصدار X جاهز للتثبيت» لنسخة هي نفسها الآن.
     */
    val pendingUpdate: StateFlow<com.sawn.domain.update.PendingUpdate?> =
        container.settings.pendingUpdate
            .map { pending ->
                pending?.takeIf { it.versionCode > com.sawn.app.BuildConfig.VERSION_CODE }
            }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setAutoUpdateEnabled(value: Boolean) =
        viewModelScope.launch { container.settings.setAutoUpdateEnabled(value) }

    fun setAutoDownloadEnabled(value: Boolean) =
        viewModelScope.launch { container.settings.setAutoDownloadEnabled(value) }

    fun setAutoNotifyEnabled(value: Boolean) =
        viewModelScope.launch { container.settings.setAutoNotifyEnabled(value) }

    /**
     * يسجّل تحديثاً نُزِّل يدوياً من زر «تحقّق من وجود تحديث»، فيتّفق المساران:
     * التنزيل اليدوي والتنزيل التلقائي ينتجان نفس الحالة، فتبقى بطاقة
     * «جاهز للتثبيت» والإشعار متسقين.
     */
    fun savePendingUpdate(info: com.sawn.app.update.UpdateInfo, apkPath: String) =
        viewModelScope.launch {
            container.settings.setPendingUpdate(
                com.sawn.domain.update.PendingUpdate(
                    versionCode = info.versionCode,
                    versionName = info.versionName,
                    sha256 = info.sha256,
                    changelog = info.changelog,
                    apkPath = apkPath,
                ),
            )
        }

    // --- القفل الزمني (Lockdown) ---
    val lockdownUntil: StateFlow<Long> =
        container.settings.lockdownUntil
        .catch { emit(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    /**
     * عدّاد حي مشتق من النافذة الموثوقة (elapsedRealtime داخل الإقلاع، ووقت الجدار
     * المحمي بـ high-water بعده). لا نستخدم System.currentTimeMillis مباشرة في الواجهة
     * لأنه يسمح بتقديم الساعة لإنهاء القفل.
     */
    private val oneSecondTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    val lockdownRemaining: StateFlow<Long> = combine(
        container.settings.lockdownWindowState,
        oneSecondTicker,
    ) { window, _ -> window.remainingAt(SystemClock.elapsedRealtime(), System.currentTimeMillis()) }
        .catch { emit(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val lockdownActive: StateFlow<Boolean> = lockdownRemaining
        .map { it > 0L }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** True only when the policy actually enforces blocking at the current trusted time. */
    val enforcementActive: StateFlow<Boolean> = combine(
        combine(masterEnabled, alwaysOn, container.settings.focusEndsAt, schedules, oneSecondTicker) { master, on, focusAt, list, _ ->
            val calendar = Calendar.getInstance()
            BlockingPolicy.isActive(
                masterEnabled = master,
                alwaysOn = on,
                focusEndsAt = focusAt,
                now = calendar.timeInMillis,
                anyScheduleActive = ScheduleEvaluator.anyActive(list.map { it.toDomain() }, calendar),
            )
        },
        lockdownActive,
    ) { policyActive, lockdown ->
        policyActive || lockdown
    }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** المدة المنتظرة — لا تُكتب أي قيمة قبل موافقة Device Admin. */
    private var pendingLockdownDurationMs: Long? = null

    /**
     * المرحلة 1: احتفظ بالمدة في الذاكرة فقط. تُحسب النهاية عند [commitLockdown] من
     * الساعة الموثوقة (anti-tamper) حتى لا يقصّر زمن شاشة الموافقة القفل، وحتى لا
     * يسمح إرجاع ساعة الجهاز بقفل منتهٍ منذ لحظته الأولى.
     */
    fun stageLockdownForDuration(durationMs: Long, onStaged: () -> Unit = {}) {
        pendingLockdownDurationMs = durationMs
        onStaged()
    }

    fun hasStagedLockdown(): Boolean = pendingLockdownDurationMs != null

    /**
     * المرحلة 2 (بعد موافقة admin): لا يُكتب القفل إلا بعد تشغيل الفلتر فعلياً.
     * قفل بلا حجب حقيقي ليس قفلاً — لذلك يفشل التفعيل إن لم يعمل الـ VPN.
     */
    fun commitLockdown(onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val durationMs = pendingLockdownDurationMs
        if (durationMs == null) {
            onResult(false)
            return@launch
        }
        pendingLockdownDurationMs = null
        val app = getApplication<Application>()
        if (VpnController.consentIntent(app) != null) {
            // The UI requests consent before staging; this is a defensive backstop.
            onResult(false)
            return@launch
        }
        if (!VpnController.startAndAwait(app)) {
            onResult(false)
            return@launch
        }
        val now = container.settings.trustedNow()
        val currentUntil = container.settings.lockdownUntil.first()
        // A live lock only ever grows: the chosen duration is added to the time that is left,
        // so no choice in the UI can shorten or clear it.
        val endsAt = if (currentUntil > now) currentUntil + durationMs else now + durationMs
        onResult(container.settings.setLockdownUntil(endsAt))
    }

    fun discardStagedLockdown() {
        pendingLockdownDurationMs = null
    }

    /** "" تابع للنظام · "ar" العربية · "en" الإنجليزية. */
    val sawnLocale: StateFlow<String> =
        container.settings.locale
        .catch { emit("") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setLocale(value: String) {
        viewModelScope.launch { container.settings.setLocale(value) }
    }

    /** يحرّر ملف البروفايل المحلي من الإعدادات. */
    fun setProfile(name: String?, age: Int?) = viewModelScope.launch {
        container.settings.setProfile(name, age)
    }

    /** يعيد تفعيل ظهور الترحيب — يُستخدم من «إعادة عرض المقدمة» في الإعدادات. */
    fun resetOnboarding() = viewModelScope.launch {
        container.settings.clearOnboardingDone()
    }

    /**
     * «مسح كل بياناتي» — يمسح قاعدة البيانات كاملة وكل التفضيلات ويوقف الحماية.
     * يُستدعى من الإعدادات بعد تأكيد PIN. لا يمكن التراجع — المستخدم أخبرَنا صراحةً.
     */
    fun wipeAllData() {
        viewModelScope.launch {
            // A live lock also protects against the in-app escape hatch: clearing every setting
            // would drop the lock, so it waits until the timer ends like every other change.
            if (container.settings.isLockdownActiveNow()) return@launch
            runCatching {
                // clearAllTables يحجب على SQLite — لا يجوز على الخيط الرئيسي (ANR).
                withContext(Dispatchers.IO) {
                    SawnDatabase.get(getApplication()).clearAllTables()
                }
                container.settings.clearAllData()
                VpnController.stop(getApplication())
            }
        }
    }

    /** Pull open blocklist updates while preserving the last valid list on failure. */
    fun refreshAdultLists(onResult: (AdultListFetcher.FetchResult) -> Unit = {}) = viewModelScope.launch {
        val result = AdultListFetcher(getApplication()).refreshIfDue(force = true)
        onResult(result)
    }

    val hasPin: StateFlow<Boolean> =
        container.settings.hasPin
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** null until DataStore resolves, so PIN-gated screens can stay hidden on first frame. */
    val pinConfigured: StateFlow<Boolean?> =
        container.settings.hasPin
            .map<Boolean, Boolean?> { it }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val currentStreak: StateFlow<Int> =
        container.stats.currentStreak()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    /** السلسلة حيّة لكن اليوم لم يُسجَّل بعد — تستخدمها الرئيسية تذكيراً هادئاً. */
    val streakAtRisk: StateFlow<Boolean> =
        container.stats.isAtRisk()
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val blockJournal: StateFlow<JournalSnapshot> =
        container.settings.blockJournal
        .catch { emit(JournalSnapshot()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalSnapshot())

    fun recordUrgeSessionCompleted() = viewModelScope.launch {
        runCatching { container.settings.recordUrgeSessionCompleted() }
    }

    /** نمط حماية بضغطة واحدة (خفيف / متوازن / أقصى). */
    fun applyPreset(preset: ShieldPreset) = viewModelScope.launch { container.settings.applyPreset(preset) }
    val longestStreak: StateFlow<Int> =
        container.stats.longestStreak()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    /** Total recorded protected days (up to today) — shown next to the streak. */
    val totalProtectedDays: StateFlow<Int> =
        container.stats.totalProtectedDays()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    /** Last seven days, oldest first, for the streak strip. */
    val recentProtectedDays: StateFlow<List<com.sawn.domain.DayCell>> =
        container.stats.recentDays(7)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val focusSessionsDone: StateFlow<Int> =
        container.settings.focusSessionsDone
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val enabledFeatureKeys: StateFlow<Set<String>> =
        container.settings.enabledFeatureKeys
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val socialGuardEnabled: StateFlow<Boolean> =
        container.settings.socialGuard
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val enabledSocialPlatforms: StateFlow<Set<String>> =
        container.settings.enabledSocialPlatforms
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val focusEndsAt: StateFlow<Long> =
        container.settings.focusEndsAt
        .catch { emit(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val focusStrict: StateFlow<Boolean> =
        container.settings.focusStrict
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** Begin a focus session that forces blocking on for [minutes]. */
    fun startFocus(minutes: Int, strict: Boolean) = viewModelScope.launch {
        val globalStrict = container.settings.strictMode.first()
        // Base the end on the tamper-resistant clock, not raw wall time: a rolled-back
        // clock must not create a session that is already expired.
        val now = container.settings.trustedNow()
        val endsAt = now + minutes * 60_000L
        container.settings.startFocus(
            endsAt,
            StrictPolicy.effectiveFocusStrict(globalStrict, strict),
        )
        // الجلسة تفرض الحجب، فهي يوم محمي مهما كانت حالة المفتاح الرئيسي.
        container.settings.recordActiveDay()
        FocusEndWorker.schedule(getApplication(), minutes * 60_000L)
    }

    /** Cancel early; refused while a strict session is still running. */
    fun cancelFocus(onCancelled: suspend () -> Unit = {}) = viewModelScope.launch {
        val snapshot = container.settings.protectionSnapshot()
        val now = container.settings.trustedNow()
        if (!StrictPolicy.canCancelFocus(snapshot.focusStrict, snapshot.focusEndsAt, now)) {
            return@launch
        }
        container.settings.clearFocus()
        FocusEndWorker.cancel(getApplication())
        onCancelled()
    }

    /**
     * Atomically check the strict/focus locks and persist protection-off. The result says
     * whether the write happened and whether a focus session must keep the tunnel alive.
     */
    suspend fun disableProtectionNow(): DisableOutcome =
        container.settings.disableProtection(container.settings.trustedNow())

    /** Fresh read for post-cancellation decisions; never rely on the UI StateFlow. */
    suspend fun isMasterEnabledNow(): Boolean = container.settings.masterEnabled.first()

    fun setFeatureEnabled(key: String, enabled: Boolean) = viewModelScope.launch {
        container.settings.setFeatureEnabled(key, enabled)
    }

    fun setSocialGuardEnabled(enabled: Boolean) = viewModelScope.launch {
        if (enabled && container.settings.enabledSocialPlatforms.first().isEmpty()) {
            container.settings.setSocialPlatformsEnabled(com.sawn.domain.feature.SocialPlatform.allKeys)
        }
        container.settings.setSocialGuardEnabled(enabled)
    }

    fun setSocialPlatformEnabled(key: String, enabled: Boolean) = viewModelScope.launch {
        val current = container.settings.enabledSocialPlatforms.first().toMutableSet()
        if (enabled) current.add(key) else current.remove(key)
        container.settings.setSocialPlatformsEnabled(current)
    }

    fun setEnabledFeatureKeys(keys: Set<String>) = viewModelScope.launch {
        container.settings.setEnabledFeatureKeys(keys)
    }

    /**
     * سجّل اليوم المحمي عند فتح التطبيق. الشرط هو الحجب الفعلي لا مفتاح «الدائم» وحده:
     * جلسة تركيز أو نافذة جدول أو قفل زمني كلها حمايةٌ تستحق أن تُحتسب في السلسلة.
     */
    fun onAppOpened() = viewModelScope.launch {
        runCatching {
            if (container.blockEngine.isBlockingActiveNow()) {
                container.settings.recordActiveDay()
            }
        }
    }

    /** يُستدعى فور نجاح تشغيل الفلتر كي لا ينتظر العدّاد إعادة فتح التطبيق. */
    fun recordProtectedDayNow() = viewModelScope.launch {
        runCatching {
            if (container.blockEngine.isBlockingActiveNow()) container.settings.recordActiveDay()
        }
    }

    fun addWebsite(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore("/").ifBlank { return }
        viewModelScope.launch {
            container.repository.addItem(
                BlockedItem(type = BlockType.WEBSITE, value = clean, label = clean, createdAt = System.currentTimeMillis()),
            )
        }
    }

    /** New apps default to a mindful PAUSE (ScreenZen-style), not a hard block. */
    fun addApp(packageName: String, label: String) = viewModelScope.launch {
        if (container.repository.appItem(packageName) != null) return@launch
        container.repository.addItem(
            BlockedItem(
                type = BlockType.APP,
                value = packageName,
                label = label,
                createdAt = System.currentTimeMillis(),
                interceptMode = InterceptMode.PAUSE,
            ),
        )
    }

    fun toggleItem(item: BlockedItem) = viewModelScope.launch {
        container.repository.updateItem(item.copy(enabled = !item.enabled))
    }

    /** Persist edits from the per-item config editor. */
    fun saveItem(item: BlockedItem) = viewModelScope.launch { container.repository.updateItem(item) }

    fun removeItem(item: BlockedItem) = viewModelScope.launch { container.repository.removeItem(item) }

    fun addSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.addSchedule(schedule) }
    fun removeSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.removeSchedule(schedule) }

    fun setMasterEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setMasterEnabled(enabled) }

    /** Strict mode needs a PIN; the store enforces that atomically with the write. */
    suspend fun setStrictMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (enabled) container.settings.enableStrictMode(container.settings.trustedNow())
            else container.settings.setStrictMode(false)
        }.getOrDefault(false)
    }

    // --- PIN ---
    /**
     * Create a PIN. When [enableStrict] is true (the user asked for strict mode before a PIN
     * existed), strict is enabled in the same coroutine so ordering cannot be lost.
     */
    suspend fun setPin(pin: String, enableStrict: Boolean = false): Boolean =
        withContext(Dispatchers.Default) {
            runCatching {
                val salt = PinHasher.newSalt()
                val hash = PinHasher.hash(pin, salt)
                container.settings.setPin(hash, salt)
                !enableStrict || container.settings.enableStrictMode(container.settings.trustedNow())
            }.getOrDefault(false)
        }

    /** False when the store refused the removal — strict mode or a time lock still needs the PIN. */
    suspend fun clearPin(): Boolean = withContext(Dispatchers.IO) {
        runCatching { container.settings.clearPin() }.getOrDefault(false)
    }

    /** Verifies [pin], enforcing a compounding lockout after repeated failures. */
    suspend fun verifyPin(pin: String): PinResult {
        val lockRemaining = container.settings.pinLockRemainingMillis()
        if (lockRemaining > 0L) {
            return PinResult.Locked(((lockRemaining + 999L) / 1000L).toInt())
        }

        val credentials = runCatching { container.settings.pinCredentials() }
            .getOrNull() ?: return PinResult.Error
        val (hash, salt) = credentials
        return try {
            val verified = withContext(Dispatchers.Default) { PinHasher.verify(pin, salt, hash) }
            if (verified) {
                container.settings.recordPinSuccess()
                PinResult.Ok
            } else {
                val newLockRemaining = container.settings.recordPinFailure()
                if (newLockRemaining > 0L) {
                    PinResult.Locked(((newLockRemaining + 999L) / 1000L).toInt())
                } else {
                    PinResult.Wrong
                }
            }
        } catch (_: Exception) {
            PinResult.Error
        }
    }
    // --- Plan B (2.1) -----------------------------------------------------
    // العدّادات هنا تُقرأ من Room/DataStore، وكل تغيير يعيد القراءة عبر
    // [planBRefresh] حتى لا تبقى الشاشة على رقم قديم بعد التغيير.

    private val planBRefresh = MutableStateFlow(0L)
    private fun refreshPlanB() {
        planBRefresh.value = planBRefresh.value + 1
    }

    private fun todayIso(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** أيام التحدّي المكتملة في الجولة الجارية. */
    val challengeDays: StateFlow<Int> = planBRefresh
        .map {
            val run = runCatching { container.database.challengeDao().latestRun() }.getOrNull()
            run?.let { r ->
                runCatching { container.database.challengeDao().countDays(r.id) }.getOrDefault(0)
            } ?: 0
        }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * يُسجَّل اليوم في الجولة الجارية — والتاريخ مفتاحه الأساسي فلا يُحتسب مرتين.
     * شرط المشروع المعلَن في الواجهة: «لا يتقدّم إلا بيوم محمي» — بلا حمايةٍ فعّالةٍ
     * الآن لا يُسجَّل اليوم (الواجهة تُظهر رسالة التوضيح).
     */
    fun recordChallengeDay(onRejected: (suspend () -> Unit)? = null) = viewModelScope.launch {
        if (!container.blockEngine.isBlockingActiveNow()) {
            onRejected?.invoke()
            return@launch
        }
        val runId = container.database.challengeDao().latestRun()?.id
            ?: container.database.challengeDao().insertRun(
                ChallengeRunEntity(startedAt = System.currentTimeMillis()),
            )
        container.database.challengeDao().recordDay(ChallengeDayEntity(todayIso(), runId))
        refreshPlanB()
    }

    /** جولة جديدة من الصفر — يمسح التقدّم، فتلزمه تأكيد في الواجهة. */
    fun restartChallenge() = viewModelScope.launch {
        container.database.challengeDao().insertRun(
            ChallengeRunEntity(startedAt = System.currentTimeMillis()),
        )
        refreshPlanB()
    }

    // --- حارس الليل -------------------------------------------------------
    val nightGuardEnabled: StateFlow<Boolean> =
        container.settings.nightGuardEnabled
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val nightGuardStartMin: StateFlow<Int> =
        container.settings.nightGuardStartMin
        .catch { emit(1320) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1320)
    val nightGuardEndMin: StateFlow<Int> =
        container.settings.nightGuardEndMin
        .catch { emit(360) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 360)
    val nightGuardUnlockUntil: StateFlow<Long> =
        container.settings.nightGuardUnlockUntil
        .catch { emit(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** نبضة كل نصف دقيقة — تكفي لحساب «هل نحن داخل النافذة الآن». */
    private val clockTick: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    /** هل نحن داخل نافذة الحارس الآن (وفكّ مؤقت سارٍ)؟ */
    val nightGuardActiveNow: StateFlow<Boolean> = combine(
        container.settings.nightGuardEnabled,
        container.settings.nightGuardStartMin,
        container.settings.nightGuardEndMin,
        container.settings.nightGuardUnlockUntil,
        clockTick,
    ) { enabled, start, end, unlockUntil, now ->
        if (!enabled) return@combine false
        if (unlockUntil > now) return@combine false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        NightGuardPolicy.shouldEngage(nowMin, start, end, enabled = true)
    }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    suspend fun setNightGuard(enabled: Boolean, startMin: Int, endMin: Int): Boolean = withContext(Dispatchers.IO) {
        container.settings.setNightGuard(enabled, startMin, endMin)
    }

    /**
     * فكّ مؤقت للحارس (١٥ دقيقة) — برقم سري صحيح فقط، تماماً كما في القفل الزمني.
     * تُرجع نتيجة التحقق كاملة (سيّئة/مقفل/خطأ) لا مجرد «نعم/لا».
     */
    suspend fun requestNightGuardUnlock(pin: String): PinResult {
        return when (val result = verifyPin(pin)) {
            PinResult.Ok -> {
                container.settings.setNightGuardUnlock(
                    container.settings.trustedNow() + NIGHT_GUARD_UNLOCK_MS,
                )
                PinResult.Ok
            }
            else -> result
        }
    }

    // --- التركيز العميق ---------------------------------------------------
    val deepFocusEnabled: StateFlow<Boolean> =
        container.settings.deepFocusEnabled
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val deepFocusWhitelist: StateFlow<Set<String>> =
        container.settings.deepFocusWhitelist
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val deepFocusUnlockUntil: StateFlow<Long> =
        container.settings.deepFocusUnlockUntil
        .catch { emit(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setDeepFocusEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settings.setDeepFocus(enabled)
        if (enabled && container.settings.deepFocusWhitelist.first().isEmpty()) {
            container.settings.setDeepFocusWhitelist(DeepFocusPolicy.DEFAULT_WHITELIST)
        }
        if (!enabled) container.settings.clearDeepFocusUnlock()
    }

    /**
     * فكّ التركيز العميق: رقم سري صحيح، ثم مهلة انتظار قصيرة، ثم نافذة خروج محدودة
     * بعدها يعود الحجب من تلقاء نفسه.
     *
     * حدود النافذة تُحسب من الساعة الموثوقة (لا ساعة الحائط) لأن الحارس يقرأها
     * بالساعة الموثوقة — وإلا اختلفت المرجعيتان عند تغيير ساعة الجهاز.
     */
    suspend fun requestDeepFocusUnlock(pin: String): PinResult {
        return when (val result = verifyPin(pin)) {
            PinResult.Ok -> {
                val now = container.settings.trustedNow()
                container.settings.openDeepFocusEscape(
                    startsAt = DeepFocusPolicy.unlockStartsAt(now),
                    until = DeepFocusPolicy.unlockUntil(now),
                )
                PinResult.Ok
            }
            else -> result
        }
    }

    // --- المحفّزات --------------------------------------------------------
    /** تحليل المحفّزات: الأكثر تكراراً، وساعة الذروة، والمجموع. */
    val triggerInsights: StateFlow<TriggerInsightsSummary> = planBRefresh
        .map {
            val logs = container.database.triggerDao().recent()
            val summaries = logs.mapNotNull { entry ->
                runCatching { TriggerTag.valueOf(entry.tag) }.getOrNull()
                    ?.let { TriggerLog(it, entry.hourOfDay) }
            }
            TriggerInsights.analyze(summaries)
        }
        .catch { emit(TriggerInsightsSummary(null, null, 0)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TriggerInsightsSummary(null, null, 0))

    /** توزيع المحفّزات على ساعات اليوم (٢٤ خانة) — لرسم مخطط بسيط. */
    val triggerHourHistogram: StateFlow<List<Int>> = planBRefresh
        .map {
            val buckets = IntArray(24)
            container.database.triggerDao().recent().forEach { entry ->
                val hour = entry.hourOfDay.coerceIn(0, 23)
                buckets[hour]++
            }
            buckets.toList()
        }
        .catch { emit(List(24) { 0 }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(24) { 0 })

    fun logTrigger(tag: TriggerTag) = viewModelScope.launch {
        val hour = java.time.LocalTime.now().hour
        container.database.triggerDao().insert(
            TriggerLogEntity(tag = tag.name, hourOfDay = hour, timestampMs = System.currentTimeMillis()),
        )
        refreshPlanB()
    }

    // --- المساءلة ---------------------------------------------------------
    val accountabilityName: StateFlow<String?> =
        container.settings.accountabilityName
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val accountabilityContact: StateFlow<String?> =
        container.settings.accountabilityContact
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setAccountability(name: String?, contact: String?) = viewModelScope.launch {
        container.settings.setAccountability(name, contact)
    }

    /** يُنجَز الكتابة قبل أن تقول الواجهة «حُفظ». */
    suspend fun saveAccountability(name: String?, contact: String?) = withContext(Dispatchers.IO) {
        container.settings.setAccountability(name, contact)
    }

    /** تقرير أسبوعي حقيقي: أيام محمية، محاولات مصدودة، والسلسلة الحالية. */
    fun shareWeeklyReport(context: Context) {
        // سياق التطبيق لا الـ Activity: التقرير قد يُرسل بعد إغلاق الشاشة.
        val appContext = context.applicationContext
        viewModelScope.launch {
            val recent = runCatching { container.stats.recentDays(7).first() }.getOrDefault(emptyList())
            val journal = runCatching { container.settings.blockJournal.first() }.getOrDefault(JournalSnapshot())
            val streak = runCatching { container.stats.currentStreak().first() }.getOrDefault(0)
            val report = ParentalModePolicy.buildWeeklyReport(
                protectedDaysThisWeek = recent.count { it.protected },
                blockedAttemptsThisWeek = journal.weekTotal,
                currentStreak = streak,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    appContext.getString(R.string.feature_accountability_subject),
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                appContext.startActivity(
                    Intent.createChooser(intent, appContext.getString(R.string.feature_accountability_share)),
                )
            }
        }
    }

    // --- وضع الوالدين -----------------------------------------------------
    val parentModeEnabled: StateFlow<Boolean> =
        container.settings.parentModeEnabled
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val parentPinPresent: StateFlow<Boolean> =
        container.settings.parentPinPresent
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    suspend fun setParentModeEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        container.settings.setParentModeEnabled(enabled)
    }

    /** يضبط رقم الوالدين (مجزّأ بملح) — مستقل تماماً عن رقم المستخدم. */
    suspend fun setParentPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (pin.length < MIN_PIN_LENGTH) return@withContext false
        val salt = PinHasher.newSalt()
        container.settings.setParentPin(PinHasher.hash(pin, salt), salt)
        true
    }

    suspend fun clearParentPin(): Boolean = withContext(Dispatchers.IO) {
        container.settings.clearParentPin()
    }

    suspend fun verifyParentPin(pin: String): PinResult = withContext(Dispatchers.IO) {
        // نفس عدّاد الإخفاقات للرقم السري للمستخدم: محاولة خاطئة لأحدهما تُقفلهما
        // معًا، فلا يُكرَك أيٌّ منهما بلا حدود.
        val lockRemaining = container.settings.pinLockRemainingMillis()
        if (lockRemaining > 0L) {
            return@withContext PinResult.Locked(((lockRemaining + 999L) / 1000L).toInt())
        }
        val credentials = container.settings.parentPinCredentials()
            ?: return@withContext PinResult.Error
        val ok = PinHasher.verify(pin, credentials.second, credentials.first)
        if (ok) container.settings.recordPinSuccess() else container.settings.recordPinFailure()
        if (ok) PinResult.Ok else PinResult.Wrong
    }

    // --- النسخة الاحتياطية ------------------------------------------------
    private val backupRepository = com.sawn.core.data.BackupRepository(
        container.settings,
        container.database,
    )

    /** يصدّر الحالة الكاملة إلى ملف `.sawnbak` مشفّراً (AES-256-GCM). */
    suspend fun exportBackup(context: Context, uri: Uri, passphrase: String): BackupResult =
        withContext(Dispatchers.IO) {
            if (passphrase.length < MIN_BACKUP_PASSPHRASE) return@withContext BackupResult.PassphraseTooShort
            runCatching {
                val payload = BackupCodec.encode(backupRepository.buildDocument())
                val encrypted = BackupCrypto.encrypt(payload, passphrase)
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("output stream unavailable")
                stream.use { it.write(encrypted) }
            }.fold(
                onSuccess = { BackupResult.Success },
                onFailure = { BackupResult.Failed(it.message ?: "export failed") },
            )
        }

    /** يستورد نسخة احتياطية: يفكّ التشفير، ثم يطبّق الوثيقة على الجهاز. */
    suspend fun importBackup(context: Context, uri: Uri, passphrase: String): BackupResult =
        withContext(Dispatchers.IO) {
            if (passphrase.length < MIN_BACKUP_PASSPHRASE) return@withContext BackupResult.PassphraseTooShort
            val document = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                val plain = BackupCrypto.decrypt(bytes, passphrase) ?: return@runCatching null
                BackupCodec.decode(plain)
            }.getOrNull() ?: return@withContext BackupResult.CannotDecrypt

            runCatching { backupRepository.applyDocument(document) }
                .fold(
                    onSuccess = {
                        refreshPlanB()
                        BackupResult.Success
                    },
                    onFailure = { BackupResult.Failed(it.message ?: "import failed") },
                )
        }

    companion object {
        /** نافذة فكّ حارس الليل: ربع ساعة. */
        const val NIGHT_GUARD_UNLOCK_MS = 15L * 60L * 1000L
        const val MIN_PIN_LENGTH = 4
        const val MIN_BACKUP_PASSPHRASE = 8
    }
}

/** نتيجة عملية النسخة الاحتياطية — يعرضها كل خطأ بالعربية. */
sealed interface BackupResult {
    data object Success : BackupResult
    data object PassphraseTooShort : BackupResult
    data object CannotDecrypt : BackupResult
    data class Failed(val message: String) : BackupResult
}
