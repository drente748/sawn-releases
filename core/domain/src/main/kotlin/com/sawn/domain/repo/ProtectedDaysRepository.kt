package com.sawn.domain.repo

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Recorded days where protection was effectively active — the single source of truth for streaks. */
interface ProtectedDaysRepository {
    /** Idempotent — safe to call repeatedly for the same day. */
    suspend fun upsert(date: LocalDate, source: String)

    /** Live stream of all recorded protection days. */
    fun observeProtectedDays(): Flow<Set<LocalDate>>

    suspend fun allDays(): List<LocalDate>
}
