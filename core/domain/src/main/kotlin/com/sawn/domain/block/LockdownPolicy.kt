package com.sawn.domain.block

/**
 * سياسة القفل الزمني — نقية بالكامل (JVM-testable): أثناء قفل نشط، لا إيقاف حماية
 * ولا رفع قفل قبل انتهاء المدة. القفل مطلق (حسم المستخدم 2026-11-01).
 */
object LockdownPolicy {

    enum class DurationUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS }

    const val MIN_MINUTES: Long = 1L
    const val MAX_MONTHS: Long = 12L

    /** القفل نشط إذا المدة مستقبلية — `until > now`. صفر يعني بلا قفل. */
    fun isActive(until: Long, now: Long): Boolean = until > now

    /** تحويل مدة يدوية إلى millis، أو null إن كانت خارج [1 دقيقة .. 12 شهراً]. */
    fun validateManual(amount: Long, unit: DurationUnit): Long? {
        if (amount < MIN_MINUTES) return null
        val millis = when (unit) {
            DurationUnit.MINUTES -> amount * 60_000L
            DurationUnit.HOURS -> amount * 3_600_000L
            DurationUnit.DAYS -> amount * 86_400_000L
            DurationUnit.WEEKS -> amount * 7 * 86_400_000L
            DurationUnit.MONTHS -> amount * 30L * 86_400_000L
        }
        // 12 شهراً = 360 يوماً بخمن الشهر الثابت.
        val max = MAX_MONTHS * 30L * 86_400_000L
        return if (millis in 60_000L..max) millis else null
    }

    /** يحوّل الأرقام العربية-الهندية/الفارسية إلى ASCII حتى يقبلها toLongOrNull. */
    fun normalizeDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            append(
                when (c) {
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    else -> c
                },
            )
        }
    }

    /** مدة يدوية من نص المستخدم (بأي أرقام عربية أو لاتينية)، أو null إن لم تكن رقماً. */
    fun parseManualAmount(input: String): Long? =
        normalizeDigits(input.trim()).toLongOrNull()

    /**
     * قاعدة تعديل قفل نشط: القفل ينتهي فقط بانتهاء مهلته، فلا يُمحى ولا يُقصّر.
     * أثناء قفل نشط، الكتابة المقبولة الوحيدة هي تمديد صريح إلى ما بعد نهايته؛
     * بعد انتهائه يُسمح بقفل جديد.
     */
    fun mayApplyLockdown(currentUntil: Long, newUntil: Long, now: Long): Boolean =
        if (!isActive(currentUntil, now)) true else newUntil > currentUntil
}

/**
 * اللحظة التي يقاس منها القفل. داخل الإقلاع نفسه يكون `elapsedRealtime` هو المرجع:
 * تقديم ساعة الجهاز لا ينهي القفل. بعد إعادة الإقلاع لا يوجد مرجع أحادي متاح،
 * فنعود إلى وقت الجدار المحمي بـ high-water (قيود أندرويد العادي).
 */
data class LockdownWindow(
    val wallUntil: Long,
    val elapsedEnd: Long,
    val startBootCount: Int,
    val currentBootCount: Int,
    val elapsedNow: Long,
    val wallNow: Long,
) {
    /** العداد عند لحظة محددة. يُستخدم لتحديث الواجهة كل ثانية دون إعادة قراءة المخزن. */
    fun remainingAt(elapsedAt: Long, wallAt: Long): Long {
        if (wallUntil <= 0L) return 0L
        if (elapsedEnd > 0L && startBootCount >= 0 && startBootCount == currentBootCount) {
            return (elapsedEnd - elapsedAt).coerceAtLeast(0L)
        }
        return (wallUntil - maxOf(wallAt, wallNow)).coerceAtLeast(0L)
    }

    val remainingMillis: Long
        get() = remainingAt(elapsedAt = elapsedNow, wallAt = wallNow)

    val isActive: Boolean get() = remainingMillis > 0L
}
