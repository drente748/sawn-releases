package com.sawn.domain.update

/**
 * تحديث تم تنزيله والتحقق منه بالفعل، بانتظار موافقة المستخدم على تثبيته.
 *
 * النسخة المُنزَّلة موجودة على القرص ودقّتها محسوبة ومطابقة للبيان؛
 * ما ينقص هو موافقة المستخدم فقط — لأن أندرويد لا يسمح لأي تطبيق بتثبيت حزمة
 * دون ضغط المستخدم على «تثبيت» في مثبّت النظام.
 *
 * @property versionCode رقم النسخة المنشورة (المفتاح الذي يقارنه التطبيق)
 * @property versionName اسم النسخة للعرض
 * @property sha256 بصمة الملف كما وردت في البيان (للتسجيل والتدقيق)
 * @property changelog ملاحظات الإصدار للعرض
 * @property apkPath مسار الملف المُتحقَّق منه داخل ذاكرة التخزين المؤقتة
 */
data class PendingUpdate(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val changelog: String,
    val apkPath: String,
)
