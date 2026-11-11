package com.sawn.domain.update

/** ما يقوله الفحص عن التحديثات، بصيغة لا تعتمد على أندرويد. */
enum class UpdateAvailability {
    /** لا يوجد تحديث newer — النسخة الحالية هي الأحدث. */
    UP_TO_DATE,

    /** يوجد تحديث أحدث. */
    NEWER,

    /** القناة غير مهيّأة (لا رابط بيان مدموج في هذه النسخة). */
    DISABLED,

    /** فشل مؤقت (شبكة، خادم) — يُستحق إعادة المحاولة. */
    FAILED,
}

/** ما يجب أن يفعله التطبيق بعد الفحص. */
enum class UpdateAction {
    /** لا شيء: لا شبكة، ولا إشعار. */
    NONE,

    /** أخبر المستخدم أن هناك تحديثاً دون تنزيله. */
    NOTIFY,

    /** نزّل التحديث وتحقّق منه في الخلفية. */
    DOWNLOAD,

    /** امسح التحديث المعلّق — لم يعد هناك جديد. */
    CLEAR,

    /** أعد المحاولة لاحقاً. */
    RETRY,
}

/**
 * سياسة التحديث التلقائي.
 *
 * مسجولة في `core:domain` لا في عامل أندرويد، لأن قرار «هل نُنزّل ٣٧ ميغابايت
 * على بيانات المستخدم؟» قرار يستحق أن يكون قابلاً للاختبار بلا جهاز.
 */
object UpdateAutoPolicy {

    /**
     * @param autoUpdateEnabled مفتاح «التحديث التلقائي» — مفعّل افتراضياً.
     * @param autoDownloadEnabled مفتاح «التنزيل التلقائي» — مفعّل افتراضياً.
     * @param availability ما جاء به الفحص.
     * @param pendingVersionCode رقم النسخة المُنزَّلة والمُتحقَّق منها بالفعل، إن وُجدت.
     * @param availableVersionCode رقم النسخة المعروضة في البيان.
     */
    fun decide(
        autoUpdateEnabled: Boolean,
        autoDownloadEnabled: Boolean,
        availability: UpdateAvailability,
        pendingVersionCode: Int? = null,
        availableVersionCode: Int? = null,
    ): UpdateAction {
        // المستخدم أوقف الميزة: لا اتصال، ولا إشعار، ولا تنزيل.
        if (!autoUpdateEnabled) return UpdateAction.NONE

        return when (availability) {
            UpdateAvailability.DISABLED -> UpdateAction.NONE
            UpdateAvailability.FAILED -> UpdateAction.RETRY
            UpdateAvailability.UP_TO_DATE -> UpdateAction.CLEAR
            UpdateAvailability.NEWER -> {
                val alreadyHave = pendingVersionCode != null &&
                    (availableVersionCode == null || pendingVersionCode >= availableVersionCode)
                when {
                    // الملف الجاهز أحدث أو ليس أقدم: لا تُنزّل مرّتين.
                    alreadyHave -> UpdateAction.NOTIFY
                    autoDownloadEnabled -> UpdateAction.DOWNLOAD
                    else -> UpdateAction.NOTIFY
                }
            }
        }
    }
}
