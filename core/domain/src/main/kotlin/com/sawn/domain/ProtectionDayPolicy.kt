package com.sawn.domain

import java.time.LocalDate

/** A single cell of the activity heatmap (GitHub-style grid). */
data class DayCell(val date: LocalDate, val protected: Boolean)

/**
 * حساب سلسلة الأيام — المصدر الحقيقي هو جدول `protected_days`، وليس عدّاداً مخزّناً يفقد اتساقه.
 *
 * القواعد:
 * - [currentStreak]: طول السلسلة المتتابعة المنتهية اليوم، أو أمس إن لم يُسجَّل اليوم بعد
 *   (السلسلة تبقى ظاهرة حتى ينتهي اليوم دون تسجيل — ولا تكسر تلقائياً في الصباح).
 * - التواريخ المستقبلية تُتجاهَل كلياً في [currentStreak] و[heatmap] (انحراف الساعة لا يكافأ).
 * - [longestStreak]: أطول سلسلة مسجّلة في التاريخ كله.
 * - [isAtRisk]: أمس محمي واليوم لم يُسجَّل بعد — لحظة تذكير لا إنذار.
 */
object ProtectionDayPolicy {

    fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        val eligible = if (today in days) today else today.minusDays(1)
        var streak = 0
        var cursor: LocalDate? = eligible
        while (cursor != null && cursor <= today && cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * Longest run in history. When [today] is supplied, future dates are ignored so a clock
     * jump forward cannot manufacture a "best streak" the user never earned.
     */
    fun longestStreak(days: Set<LocalDate>, today: LocalDate? = null): Int {
        val eligible = if (today == null) days else days.filter { it <= today }.toSet()
        if (eligible.isEmpty()) return 0
        val sorted = eligible.sorted()
        var best = 1
        var run = 1
        var prev = sorted.first()
        for (date in sorted.drop(1)) {
            if (date == prev.plusDays(1)) {
                run++
                if (run > best) best = run
            } else if (date != prev) {
                run = 1
            }
            prev = date
        }
        return best
    }

    /** Days actually counted towards the streak: recorded and not in the future. */
    fun totalProtectedDays(days: Set<LocalDate>, today: LocalDate): Int =
        days.count { it <= today }

    fun isAtRisk(days: Set<LocalDate>, today: LocalDate): Boolean =
        today !in days && today.minusDays(1) in days

    fun heatmap(days: Set<LocalDate>, today: LocalDate, pastDays: Int): List<DayCell> {
        require(pastDays >= 1) { "pastDays must be >= 1" }
        val first = today.minusDays((pastDays - 1).toLong())
        return (0 until pastDays).map { offset ->
            val date = first.plusDays(offset.toLong())
            DayCell(date = date, protected = date in days && date <= today)
        }
    }
}
