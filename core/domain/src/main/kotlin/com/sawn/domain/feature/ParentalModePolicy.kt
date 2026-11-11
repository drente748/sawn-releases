package com.sawn.domain.feature

/**
 * «وضع ولي الأمر» — على جهاز الطفل.
 * رمز ولي أمر ثانٍ يُقفل كل وظائف الإدارة (القوائم/الجداول/الفلاتر/الإعدادات).
 * عند تفعيله تُرقَّى حماية الحذف (Device Admin) من «اختياري» إلى «مطلوبة».
 * اللوحة: تقرير أسبوعي يُولَّد محليًا ويُشارَكه الوالد يدويًا (واتساب/بريد) —
 * ليس تطبيقًا موجّهًا للأطفال ولا تصنيفًا عائليًا.
 */
object ParentalModePolicy {

    /** عند تفعيل وضع ولي الأمر: كل الإدارة مقفلة برمز الولي. */
    fun isManagementLocked(parentPinPresent: Boolean, enabled: Boolean): Boolean =
        enabled && parentPinPresent

    /** حماية الحذف مطلوبة عند تفعيل الوضع. */
    val REQUIRES_DEVICE_ADMIN = true

    /**
     * قائمة "اللوحة" الأسبوعية: نص بسيط يُولَّد محليًا (بلا بيانات تُرسل تلقائيًا).
     * يشاركه الوالد يدويًا عبر Intent.
     */
    fun buildWeeklyReport(
        protectedDaysThisWeek: Int,
        blockedAttemptsThisWeek: Int,
        currentStreak: Int,
    ): String =
        "تقرير أسبوعي — صَوْن (محلي، لم يُرسَل تلقائيًا)\n" +
            "- أيام محمية هذا الأسبوع: $protectedDaysThisWeek\n" +
            "- محاولات مصدودة هذا الأسبوع: $blockedAttemptsThisWeek\n" +
            "- سلسلة حالية: $currentStreak يوم"
}
