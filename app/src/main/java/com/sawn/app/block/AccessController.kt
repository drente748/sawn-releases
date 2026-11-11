package com.sawn.domain.block

import com.sawn.core.data.BlockRepository
import com.sawn.core.data.BlockedItem
import com.sawn.domain.feature.DeepFocusPolicy
import com.sawn.domain.feature.DeepFocusState
import com.sawn.domain.model.InterceptMode

enum class LimitReason { OPENS, TIME, COOLDOWN }

/** What should happen when a blocked app is opened right now. */
sealed interface AccessDecision {
    /** Let the app through (not blocked, or a temporary grant is active). */
    data object Allow : AccessDecision
    /** Let the app through until [expiresAt]; re-check when that passes. */
    data class AllowUntil(val expiresAt: Long) : AccessDecision
    /** Show the mindful pause; the user may earn timed access. */
    data class Pause(val item: BlockedItem, val escalations: Int = 0) : AccessDecision
    /** Hard wall — no way through. */
    data class HardBlock(val item: BlockedItem) : AccessDecision
    /** A pause app that has hit a limit; access denied until it resets. */
    data class LimitReached(val item: BlockedItem, val reason: LimitReason) : AccessDecision
    /**
     * وضع التركيز العميق: التطبيق خارج القائمة البيضاء، فيُحجب قبل النظر في قوائم
     * الحجب أصلاً — لا مهلة ولا استثناء.
     */
    data class DeepFocusBlock(val packageName: String) : AccessDecision
}

/**
 * Central authority for per-app access decisions. Combines the schedule/master state
 * (via [BlockEngine]) with per-item limits and any active grant.
 *
 * [usageMinutesToday] returns the app's foreground minutes today (from UsageStats), used
 * to enforce daily time limits.
 */
class AccessController(
    private val repo: BlockRepository,
    private val engine: BlockEngine,
    private val usageMinutesToday: suspend (String) -> Long,
    /** Clock-tamper-safe "now" from the SettingsStore high-water mark. */
    private val trustedNow: suspend () -> Long = { System.currentTimeMillis() },
    /** Escalating friction: defeats of mindful pauses today (raises effective wait). */
    private val pauseDefeatsToday: suspend () -> Int = { 0 },
    /** وضع التركيز العميق: يُقرَأ قبل كل قرار لأنه يسبق قوائم الحجب. */
    private val deepFocusState: suspend () -> DeepFocusState = { DeepFocusState() },
) {
    /**
     * لقطة مخزّنة لوضع التركيز العميق: القرار يُتخذ مع كل تبديل للتطبيق الأمامي،
     * فلا نقرأ DataStore في كل مرة (القيمة في الذاكرة أصلًا، لكن تجميع التدفق كل
     * حدثٍ يكلّف). الحقلان بقيمة واحدة عبر [unsafe] pair داخلي، فلا تُخلط حالةٌ
     * جديدةٌ بطابعٍ زمنيّ قديم.
     */
    private data class DeepFocusCache(val state: DeepFocusState, val at: Long)

    @Volatile private var cachedDeepFocusPair: DeepFocusCache = DeepFocusCache(DeepFocusState(), Long.MIN_VALUE)

    private suspend fun deepFocusSnapshot(now: Long): DeepFocusState {
        val cachedSnapshot = cachedDeepFocusPair
        if (now - cachedSnapshot.at < DEEP_FOCUS_CACHE_MS) return cachedSnapshot.state
        val fresh = runCatching { deepFocusState() }.getOrDefault(DeepFocusState())
        if (fresh != cachedSnapshot.state) {
            cachedDeepFocusPair = DeepFocusCache(fresh, now)
            return fresh
        }
        // Same state — refresh just the timestamp atomically.
        cachedDeepFocusPair = DeepFocusCache(fresh, now)
        return fresh
    }

    suspend fun decideForPackage(packageName: String): AccessDecision {
        val now = trustedNow()
        // التركيز العميق يسبق كل شيء: خارج القائمة البيضاء يُحجب التطبيق حتى لو لم
        // يكن في قوائم الحجب أصلاً — لكن إطفاء مفتاح الحماية يوقف كل الحجب.
        val deep = deepFocusSnapshot(now)
        if (DeepFocusPolicy.isRestricted(
                packageName = packageName,
                whitelist = deep.whitelist,
                enabled = deep.enabled,
                masterEnabled = deep.masterEnabled,
                unlockStartsAt = deep.unlockStartsAt,
                unlockUntil = deep.unlockUntil,
                now = now,
            )
        ) {
            return AccessDecision.DeepFocusBlock(packageName)
        }
        if (!engine.isBlockingActiveNow()) return AccessDecision.Allow
        val item = repo.appItem(packageName) ?: return AccessDecision.Allow
        if (!item.enabled) return AccessDecision.Allow

        // Fetch only what each check needs (usage stats especially aren't free).
        val grant = repo.activeGrant(item.id, now)
        val used = if (item.dailyLimitMinutes > 0) usageMinutesToday(packageName) else 0L
        val opens = if (item.openLimitPerDay > 0) repo.opensToday(item.id) else 0
        val last = if (item.cooldownMinutes > 0) repo.lastGrantAt(item.id) else null
        val defeats = if (item.interceptMode == InterceptMode.PAUSE) pauseDefeatsToday() else 0

        return decide(item, grant?.expiresAt, opens, last, used, now, defeats)
    }

    companion object {
        /**
         * Pure decision logic — no I/O — so it's exhaustively unit-testable. Assumes blocking
         * is active and [item] is the (enabled) app in question.
         *
         * [activeGrantExpiresAt] is the expiry of an active grant, or null. An expired grant
         * must not allow access, so the decision also checks it against [now].
         *
         * [pauseDefeats] escalates friction: after repeated same-day exits from a mindful
         * pause, each additional attempt's initial wait grows — the "urge loop" breaker.
         */
        fun decide(
            item: BlockedItem,
            activeGrantExpiresAt: Long?,
            opensToday: Int,
            lastGrantAt: Long?,
            usedMinutesToday: Long,
            now: Long,
            pauseDefeats: Int = 0,
        ): AccessDecision {
            if (!item.enabled) return AccessDecision.Allow
            if (item.interceptMode == InterceptMode.BLOCK) return AccessDecision.HardBlock(item)
            if (item.dailyLimitMinutes > 0 && usedMinutesToday >= item.dailyLimitMinutes) {
                return AccessDecision.LimitReached(item, LimitReason.TIME)
            }
            if (activeGrantExpiresAt != null && activeGrantExpiresAt > now) {
                val recheckAt = if (item.dailyLimitMinutes > 0) {
                    minOf(activeGrantExpiresAt, now + DAILY_LIMIT_RECHECK_MS)
                } else {
                    activeGrantExpiresAt
                }
                return AccessDecision.AllowUntil(recheckAt)
            }

            if (item.openLimitPerDay > 0 && opensToday >= item.openLimitPerDay) {
                return AccessDecision.LimitReached(item, LimitReason.OPENS)
            }
            if (item.cooldownMinutes > 0 && lastGrantAt != null &&
                now - lastGrantAt < item.cooldownMinutes * 60_000L
            ) {
                return AccessDecision.LimitReached(item, LimitReason.COOLDOWN)
            }
            return AccessDecision.Pause(item, pauseDefeats)
        }

        private const val DAILY_LIMIT_RECHECK_MS = 60_000L

        /** عمر لقطة التركيز العميق المخزّنة (مللي ثانية). */
        private const val DEEP_FOCUS_CACHE_MS = 1_000L
    }
}
