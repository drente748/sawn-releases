package com.sawn.app.ui.status

/**
 * حالة تغطية الحماية من ثلاث نقاط حقيقية: تشغيل الفلتر (VPN)، إمكانية الوصول،
 * فرض SafeSearch عبر DNS. كل قيمة تأتي من مصدر حقيقي — لا افتراض.
 */
data class CoverageState(
    val vpn: Boolean,
    val accessibility: Boolean,
    val safeSearchDns: Boolean,
)

enum class CoverageLevel { Full, Partial, Off }

fun coverageLevel(s: CoverageState): CoverageLevel = when (listOf(s.vpn, s.accessibility, s.safeSearchDns).count { it }) {
    3 -> CoverageLevel.Full
    0 -> CoverageLevel.Off
    else -> CoverageLevel.Partial
}
