package com.sawn.domain.block

import android.os.SystemClock
import com.sawn.core.data.BlockRepository
import com.sawn.core.data.ProtectionSnapshot
import com.sawn.core.data.SettingsStore
import com.sawn.core.data.toDomain
import java.util.Calendar
import com.sawn.domain.block.AdultHosts

/**
 * Answers "is blocking enforced right now?" from the master switch + schedules. The VPN
 * asks it for [blockedDomains]; per-app decisions go through [AccessController].
 *
 * Time is read through [SettingsStore.trustedNow] — a monotonic high-water mark — so
 * rolling the device clock back cannot hide active schedules or extend focus sessions.
 */
class BlockEngine(
    private val repo: BlockRepository,
    private val settings: SettingsStore,
) {
    /**
     * True when blocking is enforced right now. A focus session forces it on for its
     * duration; otherwise it's the master switch AND (always-on OR an active schedule).
     *
     * [peekOnly] uses the read-only clock so hot callers (the VPN refresher) cannot write
     * DataStore and re-trigger the flows watching it.
     */
    suspend fun isBlockingActiveNow(nowCal: Calendar? = null, peekOnly: Boolean = false): Boolean {
        val snapshot = settings.protectionSnapshot()
        val now = nowCal?.timeInMillis ?: if (peekOnly) settings.peekNow() else settings.trustedNow()
        return isActive(snapshot, now, nowCal)
    }

    private suspend fun isActive(
        snapshot: ProtectionSnapshot,
        now: Long,
        nowCal: Calendar?,
    ): Boolean {
        val cal = nowCal ?: Calendar.getInstance().apply { timeInMillis = now }
        return BlockingPolicy.isActive(
            masterEnabled = snapshot.masterEnabled,
            alwaysOn = snapshot.alwaysOn,
            focusEndsAt = snapshot.focusEndsAt,
            now = now,
            anyScheduleActive = ScheduleEvaluator.anyActive(repo.enabledSchedules().map { it.toDomain() }, cal),
            lockdownActive = lockdownActive(snapshot, now),
        )
    }

    private fun lockdownActive(snapshot: ProtectionSnapshot, now: Long): Boolean =
        LockdownWindow(
            wallUntil = snapshot.lockdownUntil,
            elapsedEnd = snapshot.lockdownElapsedEnd,
            startBootCount = snapshot.lockdownBootAtStart,
            currentBootCount = settings.currentBootCount(),
            elapsedNow = SystemClock.elapsedRealtime(),
            wallNow = now,
        ).isActive

    suspend fun blockedDomains(readOnly: Boolean = false): List<String> {
        val snapshot = settings.protectionSnapshot()
        val now = if (readOnly) settings.peekNow() else settings.trustedNow()
        val lockdown = lockdownActive(snapshot, now)
        if (!lockdown && !isActive(snapshot, now, null)) return emptyList()
        val domains = repo.enabledDomains().toMutableList()
        // A live lockdown always keeps the adult core on, even if a stale/corrupt pref
        // says otherwise.
        if (snapshot.pornGuard || lockdown) {
            domains += AdultHosts.ALL
            domains += AdultHosts.REDDIT_MEDIA
        }
        if (snapshot.blockDoh) domains += DohHosts.ALL
        if (snapshot.blockAds) domains += AdHosts.ALL
        return domains.distinct()
    }
}
