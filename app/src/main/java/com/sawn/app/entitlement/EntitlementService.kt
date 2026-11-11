package com.sawn.app.entitlement

/**
 * بنية المميزات المدفوعة — ونموذج الطرح المقرر:
 *
 *   - التطبيق يُقدَّم للمستخدمين مباشرة من موقع المالك، **وليس بالضرورة عبر Google Play**.
 *   - نسختان مستقبليتان: مجاني (مع إعلانات) ومدفوع (بدون إعلانات + كل الميزات).
 *   - استفادة التطبيق من استثناءات "التوزيع خارج المتجر": لا حاجة لـ Google Play Billing
 *     ما دامت النسخ المجانية/المدفوعة تُباع من موقع المالك — مع الالتزام بأي سياسات تطبقها
 *     الأنظمة على المتجر (بلاي/آبل) عند نشر نسخة عليهما لاحقًا.
 *   - لا يظهر أي "سعر/اشتراك/دفع" في واجهة المستخدم الآن — حاليًا كل شيء مجاني بالكامل.
 *
 * لا يوجد اليوم: لا Google Play Billing، لا متجر خارجي للدفع، لا Checker للاشتراك.
 * عند التفعيل: تُستبدل [NoopEntitlementService] بنسخة تقرأ ترخيص من الخلفية (site أولاً، ثم
 * Play Billing اختياريًا عند النشر على المتجر)، دون لمس أي سطح في التطبيق.
 *
 * التصنيف الحالي: FREE دائمًا — الميزات الثلاث/كلها مفعلة.
 */

enum class PaidFeature {
    NIGHT_GUARD,
    DEEP_FOCUS_WHITELIST,
    PARENT_MODE,
    SC_BACKUP_ADVANCED,
    ACCOUNTABILITY_PARTNER,
}

enum class EntitlementTier { FREE, PAID }

interface EntitlementService {
    /** هل هذه الميزة مفعلة؟ حاليًا دائمًا true (مجاني كامل). */
    suspend fun isEnabled(feature: PaidFeature): Boolean

    /** الطبقة الحالية. حاليًا FREE دائمًا. */
    suspend fun currentTier(): EntitlementTier
}

/** تنفيذ 2.0.0: كل شيء مجاني. لا كود يعتمد على PAID مفعّل في أي مكان. */
object NoopEntitlementService : EntitlementService {
    override suspend fun isEnabled(feature: PaidFeature): Boolean = true
    override suspend fun currentTier(): EntitlementTier = EntitlementTier.FREE
}
