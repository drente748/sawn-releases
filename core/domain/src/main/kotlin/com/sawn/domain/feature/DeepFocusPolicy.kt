package com.sawn.domain.feature

/**
 * «وضع التركيز العميق» — قائمة بيضاء تُسمح فقط؛ الفك = PIN + 10 ثوانٍ.
 * الاتصال/الرسائل/الخرائط افتراضيًا في القائمة البيضاء.
 */
object DeepFocusPolicy {

    /** حزم التطبيقات المسموحة افتراضيًا (اتصال/رسائل/خرائط). */
    val DEFAULT_WHITELIST: Set<String> = setOf(
        "com.android.dialer", // الهاتف
        "com.google.android.dialer",
        "android.telecom", // Telecom (framework)
        "com.google.android.apps.maps",
        "com.waze",
        "com.android.messaging", // الرسائل
        "com.google.android.apps.messaging",
    )

    /** هل يُسمح لهذا الحزمة خلال التركيز العميق؟ */
    fun isAllowed(packageName: String, whitelist: Set<String>): Boolean =
        packageName in whitelist

    /** مهلة الانتظار بعد الرقم السري قبل أن تُفتح نافذة الخروج. */
    const val UNLOCK_DELAY_SECONDS = 10

    /** طول نافذة الخروج: تكفي للانتقال بين التطبيقات ثم يعود الحجب من تلقاء نفسه. */
    const val ESCAPE_WINDOW_SECONDS = 120

    /** لحظة فتح نافذة الخروج = لحظة التحقق من الرقم + مهلة الانتظار. */
    fun unlockStartsAt(now: Long): Long = now + UNLOCK_DELAY_SECONDS * 1_000L

    /** لحظة إغلاق نافذة الخروج. */
    fun unlockUntil(now: Long): Long = unlockStartsAt(now) + ESCAPE_WINDOW_SECONDS * 1_000L

    /**
     * هل التطبيق محجوب الآن؟
     *
     * نافذة الخروج مفتوحة فقط بين [unlockStartsAt] و[unlockUntil]؛ وقبل ذلك يُحجب
     * التطبيق رغم إدخال الرقم (مهلة الانتظار)، وبعدها يعود الحجب.
     *
     * [masterEnabled] هو مفتاح الحماية الرئيسي: إطفاؤه يعني «لا حجب» فلا ينطبق
     * التركيز العميق حتى لو كان مفعّلًا.
     */
    fun isRestricted(
        packageName: String,
        whitelist: Set<String>,
        enabled: Boolean,
        unlockStartsAt: Long,
        unlockUntil: Long,
        now: Long,
        masterEnabled: Boolean = true,
    ): Boolean {
        if (!enabled || !masterEnabled) return false
        if (now >= unlockStartsAt && now < unlockUntil) return false
        return !isAllowed(packageName, whitelist)
    }
}

/** لقطة وضع التركيز العميق كما يقرؤها حارس الوصول. */
data class DeepFocusState(
    val enabled: Boolean = false,
    /** مفتاح الحماية الرئيسي: إطفاؤه يعني «لا حجب» فلا ينطبق التركيز العميق. */
    val masterEnabled: Boolean = true,
    val whitelist: Set<String> = emptySet(),
    val unlockStartsAt: Long = 0L,
    val unlockUntil: Long = 0L,
)
