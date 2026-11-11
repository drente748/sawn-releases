package com.sawn.domain.feature

/** لقطة مفاتيح الحماية الحالية، لمقارنتها بالأنماط الجاهزة. */
data class ShieldPresetState(
    val pornGuard: Boolean,
    val safeSearch: Boolean,
    val blockAds: Boolean,
    val blockDoh: Boolean,
    val blockNotifications: Boolean,
)

/**
 * أنماط حماية بضغطة واحدة. كل نمط هو مجموعة مفاتيح عادية يستطيع المستخدم
 * تعديلها فرداً فرداً؛ الأنماط تختصر الضغطات ولا تُخفي أي إعداد.
 */
enum class ShieldPreset(
    val pornGuard: Boolean,
    val safeSearch: Boolean,
    val blockAds: Boolean,
    val blockDoh: Boolean,
    val blockNotifications: Boolean,
) {
    /** الأخفّ حملاً: فلتر الإباحية وSafeSearch والإعلانات فقط. */
    LIGHT(pornGuard = true, safeSearch = true, blockAds = true, blockDoh = false, blockNotifications = false),

    /** المتوازن: يضيف حجب DNS المشفّر كي لا يتخطّى المتصفح الفلتر. */
    BALANCED(pornGuard = true, safeSearch = true, blockAds = true, blockDoh = true, blockNotifications = false),

    /** الأقصى: كل ما سبق مع كتم إشعارات التطبيقات المحجوبة. */
    MAXIMUM(pornGuard = true, safeSearch = true, blockAds = true, blockDoh = true, blockNotifications = true),
    ;

    fun matches(current: ShieldPresetState): Boolean =
        current.pornGuard == pornGuard &&
            current.safeSearch == safeSearch &&
            current.blockAds == blockAds &&
            current.blockDoh == blockDoh &&
            current.blockNotifications == blockNotifications
}
