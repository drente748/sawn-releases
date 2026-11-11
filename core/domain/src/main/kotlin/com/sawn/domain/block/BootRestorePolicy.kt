package com.sawn.domain.block

/** Everything a boot (or a cold process start) needs to know to restore protection. */
data class BootRestoreState(
    /** The user turned protection off from inside the app — the only sanctioned stop. */
    val userDisabledProtection: Boolean,
    /** A live time lock: it outranks the stop flag and must be re-armed in full. */
    val lockdownActive: Boolean,
    /** Blocking is enforced right now (master + always-on/schedule, focus, or the lock). */
    val filterActive: Boolean,
)

/**
 * What to do after a reboot or an app update. Kept pure so the precedence between the
 * user's stop flag, a live lock, and an ordinary active schedule is unit-tested instead of
 * being re-derived inline in a BroadcastReceiver.
 */
data class BootRestoreDecision(
    /** Force master + always-on + adult-filter + SafeSearch + DoH flags back on (lock only). */
    val reassertCoreFlags: Boolean,
    /** Ask the DNS filter to start again. */
    val startFilter: Boolean,
    /** Count today as protected, so a streak does not depend on opening the UI. */
    val recordProtectedDay: Boolean,
    /** Warn that the uninstall guard (device admin) is no longer active. */
    val warnMissingAdmin: Boolean,
) {
    val actsAtAll: Boolean get() = reassertCoreFlags || startFilter || warnMissingAdmin
}

object BootRestorePolicy {

    fun decide(state: BootRestoreState): BootRestoreDecision = when {
        // The lock was written by the user's past self. It wins over every other flag and
        // is restored whole, so a reboot can never quietly weaken it.
        state.lockdownActive -> BootRestoreDecision(
            reassertCoreFlags = true,
            startFilter = true,
            recordProtectedDay = true,
            warnMissingAdmin = true,
        )
        // A real stop means: no wake-up, no notification, nothing.
        state.userDisabledProtection -> BootRestoreDecision(
            reassertCoreFlags = false,
            startFilter = false,
            recordProtectedDay = false,
            warnMissingAdmin = false,
        )
        // Ordinary protection (master/always-on, schedule window, or focus session).
        state.filterActive -> BootRestoreDecision(
            reassertCoreFlags = false,
            startFilter = true,
            recordProtectedDay = true,
            warnMissingAdmin = false,
        )
        else -> BootRestoreDecision(
            reassertCoreFlags = false,
            startFilter = false,
            recordProtectedDay = false,
            warnMissingAdmin = false,
        )
    }
}
