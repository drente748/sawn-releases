package com.sawn.domain.feature

/** طبقة واحدة من طبقات الحماية، بوزنها في الدرجة الكلية. */
enum class CheckupItem(val weight: Int) {
    /** إمكانية الوصول: حجب التطبيقات والمحتوى داخل التطبيقات. */
    ACCESSIBILITY(30),

    /** فلتر DNS المحلي: حجب المواقع في كل المتصفحات. */
    FILTER(30),

    /** فلتر الإباحية والفئات المحجوبة. */
    PORN_GUARD(12),
    SAFE_SEARCH(8),
    ENCRYPTED_DNS(6),
    ADS(6),
    NOTIFICATIONS(4),
    USAGE(4),
}

/** لقطة أذونات/مفاتيح الجهاز كما تُقرأ من النظام. */
data class CheckupState(
    val accessibility: Boolean = false,
    val filter: Boolean = false,
    val pornGuard: Boolean = true,
    val safeSearch: Boolean = true,
    val encryptedDns: Boolean = false,
    val ads: Boolean = true,
    val notifications: Boolean = false,
    val usage: Boolean = false,
)

data class CheckupResult(
    val score: Int,
    val failed: List<CheckupItem>,
    val earned: Int,
    val total: Int,
) {
    /** الطبقة التي بلا عملها لا تكون الحماية حماية. */
    val essentialMissing: Boolean
        get() = failed.contains(CheckupItem.ACCESSIBILITY) || failed.contains(CheckupItem.FILTER)

    val complete: Boolean get() = failed.isEmpty()
}

/**
 * درجة جاهزية الحماية: مجموع أوزان الطبقات العاملة من إجمالي الأوزان.
 * الأساسيات (إمكانية الوصول والفلتر) تحمل 60% من الدرجة عمداً، حتى لا يبدو
 * جهازٌ بلا حجبٍ حقيقي «جاهزاً» لمجرد أنه يمنع الإعلانات.
 */
object ProtectionCheckup {

    private val weights: Map<CheckupItem, Int> = CheckupItem.entries.associateWith { it.weight }
    val totalWeight: Int = weights.values.sum()

    fun evaluate(state: CheckupState): CheckupResult {
        val results = mapOf(
            CheckupItem.ACCESSIBILITY to state.accessibility,
            CheckupItem.FILTER to state.filter,
            CheckupItem.PORN_GUARD to state.pornGuard,
            CheckupItem.SAFE_SEARCH to state.safeSearch,
            CheckupItem.ENCRYPTED_DNS to state.encryptedDns,
            CheckupItem.ADS to state.ads,
            CheckupItem.NOTIFICATIONS to state.notifications,
            CheckupItem.USAGE to state.usage,
        )
        val earned = results.filterValues { it }.keys.sumOf { weights.getValue(it) }
        return CheckupResult(
            score = if (totalWeight == 0) 0 else (earned * 100) / totalWeight,
            failed = CheckupItem.entries.filter { results[it] != true },
            earned = earned,
            total = totalWeight,
        )
    }
}
