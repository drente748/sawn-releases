package com.sawn.domain.repo

import com.sawn.domain.DayCell
import kotlinx.coroutines.flow.Flow

/** Derives the streak stats the UI shows, from the protection-day log. */
interface StatsRepository {
    fun currentStreak(): Flow<Int>
    fun longestStreak(): Flow<Int>
    fun isAtRisk(): Flow<Boolean>
    /** Recorded days up to today — the honest total behind the streak. */
    fun totalProtectedDays(): Flow<Int>
    /** Last [pastDays] calendar days, oldest first, for the streak strip. */
    fun recentDays(pastDays: Int): Flow<List<DayCell>>
}
