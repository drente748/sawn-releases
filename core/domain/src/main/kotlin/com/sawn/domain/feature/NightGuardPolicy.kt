package com.sawn.domain.feature

/**
 * «الحارس الليلي» — نافذة ليلية (مثال: 22:00–06:00) تُفعّل وضع الانضباط تلقائيًا
 * باستخدام نفس مسار Lockdown/SchedulePolicy — لا منطق منفصل.
 *
 * - التفعيل = وضع الانضباط (نفس مفاتيح الحماية) خلال النافذة.
 * - الفك = PIN + تمديد 15 دقيقة، يعود تلقائيًا بعدها، يُسجَّل.
 * - الاتصال/الرسائل/الخرائط مستثناة دائمًا من الحجب.
 */
object NightGuardPolicy {

    /** تُحوِّل (hour, minute) إلى دقائق منذ منتصف الليل. */
    fun toMinutes(hour: Int, minute: Int): Int = hour * 60 + minute

    fun isWithinWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean =
        if (startMin < endMin) {
            nowMin >= startMin && nowMin < endMin
        } else {
            // نافذة تتجاوز منتصف الليل (22:00→06:00)
            nowMin >= startMin || nowMin < endMin
        }

    /** تُعيد لحظات min/اللحظة الحالية (للعرض فقط). */
    fun minutesRemaining(nowMin: Int, endMin: Int): Int =
        ((endMin - nowMin) % (24 * 60) + 24 * 60) % (24 * 60)

    /** هل يجب تبديل الحماية إلى وضع الانضباط الآن؟ */
    fun shouldEngage(nowMin: Int, startMin: Int, endMin: Int, enabled: Boolean): Boolean =
        enabled && isWithinWindow(nowMin, startMin, endMin)
}
