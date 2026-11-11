package com.sawn.domain.feature

/** مراحل تحدّي «30 يوم صفحة جديدة». */
enum class ChallengeStage(val milestone: Int) {
    START(1),
    STABLE(3),
    WEEK(7),
    HALFWAY(14),
    MOMENTUM(21),
    VICTORY(30),
}

/**
 * تحدّي «30 يوم صفحة جديدة».
 * - لا يتقدّم إلا بيوم محمي (protected_days).
 * - «انتكاسة؟» = إعادة بدء بلا عقاب (لا يُحذف السجل).
 */
object ChallengePolicy {

    const val TOTAL_DAYS = 30

    fun stageFor(completedDays: Int): ChallengeStage? =
        ChallengeStage.entries.lastOrNull { completedDays >= it.milestone }

    /** هل أُنجز التحدي؟ */
    fun isComplete(completedDays: Int): Boolean = completedDays >= TOTAL_DAYS

    /** كم يومًا يتبقى؟ */
    fun daysRemaining(completedDays: Int): Int = (TOTAL_DAYS - completedDays).coerceAtLeast(0)
}
