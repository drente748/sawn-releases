package com.sawn.domain.feature

/** حصيلة اليوم، آخر سبعة أيام (الأقدم أولاً)، والإجمالي منذ التثبيت. */
data class JournalSnapshot(
    val today: Int = 0,
    val last7: List<Int> = List(7) { 0 },
    val total: Int = 0,
    val urgesToday: Int = 0,
    val urgesLast7: Int = 0,
    val urgesTotal: Int = 0,
) {
    val weekTotal: Int get() = last7.sum()
    val peak: Int get() = last7.maxOrNull() ?: 0
}

/**
 * سجل محلي صغير لعدد المحاولات التي رفضها صَوْن كل يوم.
 *
 * يُرمَّز في تفضيلٍ واحد بصيغة "epochDay:count|epochDay:count" بدل جدول Room:
 * بياناته أصغر من مئة بايت، ولا يغادر الجهاز أبداً، ولا يحمل أي اسم موقع أو
 * تطبيق — عدّادٌ فقط.
 */
object BlockJournal {

    /** أسبوعان يكفيان للرسم وللمقارنة، ثم تُقلَّم الأيام الأقدم. */
    private const val KEEP_DAYS = 14L

    fun decode(encoded: String?): Map<Long, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split('|').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val day = parts[0].toLongOrNull() ?: return@mapNotNull null
            val count = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (day < 0L || count < 0) null else day to count
        }.toMap()
    }

    fun encode(log: Map<Long, Int>): String =
        log.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}" }

    /** Adds one event to [today] and drops days older than [KEEP_DAYS]. */
    fun bump(log: Map<Long, Int>, today: Long): Map<Long, Int> {
        val next = log.filterKeys { it > today - KEEP_DAYS }.toMutableMap()
        next[today] = ((next[today] ?: 0).coerceAtLeast(0) + 1)
        return next
    }

    fun snapshot(
        log: Map<Long, Int>,
        total: Int,
        today: Long,
        urges: Map<Long, Int> = emptyMap(),
        urgeTotal: Int = 0,
    ): JournalSnapshot =
        JournalSnapshot(
            today = (log[today] ?: 0).coerceAtLeast(0),
            last7 = (6 downTo 0).map { offset -> (log[today - offset] ?: 0).coerceAtLeast(0) },
            total = total.coerceAtLeast(0),
            urgesToday = (urges[today] ?: 0).coerceAtLeast(0),
            urgesLast7 = (6 downTo 0).sumOf { offset -> (urges[today - offset] ?: 0).coerceAtLeast(0) },
            urgesTotal = urgeTotal.coerceAtLeast(0),
        )
}
