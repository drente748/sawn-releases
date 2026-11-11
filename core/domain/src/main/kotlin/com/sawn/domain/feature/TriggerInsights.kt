package com.sawn.domain.feature

/** وسوم محفزات الانتكاس (اختيار واحد). */
enum class TriggerTag {
    BOREDOM,
    STRESS,
    LONELINESS,
    LATE_NIGHT,
    ANGER,
    OTHER,
}

/** سجل محفز واحد (محلي 100%). */
data class TriggerLog(
    val tag: TriggerTag,
    val hourOfDay: Int,
    val note: String? = null,
    val timestampMs: Long = 0L,
)

/** رؤى محلل المحفزات: أكثر المحفزات + ساعات الذروة + «محفزك الأخطر». */
data class TriggerInsightsSummary(
    val topTag: TriggerTag?,
    val peakHour: Int?,
    val totalLogs: Int,
)

/**
 * محلل محفزات الانتكاس.
 * بعد أي محاولة/انتكاسة: وسم واحد + ملاحظة اختيارية.
 * أكثر المحفزات تكرارًا؛ ساعات الذروة؛ «المحفز الأخطر».
 */
object TriggerInsights {
    fun analyze(logs: List<TriggerLog>): TriggerInsightsSummary {
        if (logs.isEmpty()) return TriggerInsightsSummary(null, null, 0)
        val topTag = logs.groupingBy { it.tag }.eachCount().maxByOrNull { it.value }?.key
        val peakHour = logs.groupingBy { it.hourOfDay }.eachCount().maxByOrNull { it.value }?.key
        return TriggerInsightsSummary(topTag, peakHour, logs.size)
    }
}
