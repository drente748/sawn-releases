# play-compliance.md — مبرّرات الامتثال لـ Google Play (جاهز قبل التقديم)

هذا الملف يُسلَّم مع 2.2.0 ويُستخدم لتعبئة نموذج Google Play "App content" دون تخمين.

## 1) تبريرات الأذونات (Purpose & Justification)

| الإذن | لماذا يحتاجه «صَوْن» | النوع في بلاي | ملاحظة |
|---|---|---|---|
| INTERNET / ACCESS_NETWORK_STATE | إعادة توجيه استعلامات DNS غير المحظورة إلى المحلل المحلي؛ فحص إتاحة الشبكة | عادية | — |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE (+PROPERTY_SUBTYPE) | الفلتر نفسه: `specialUse` + سبب التشغيل المعلن من Google يصفّي المواقع المحجوبة لصحتك الرقمية | FGS خاص | يجب تقديم **declaration** خاصة باستخدام FGS |
| POST_NOTIFICATIONS (TIRAMISU+) | إشعار «الحماية تعمل» (المستمر) — ضرورة لاستمرارية الحماية وليس تسويقًا | عادي (T13) | لا ترسل شيء آخر |
| RECEIVE_BOOT_COMPLETED | إعادة تسليح الفلتر بعد إعادة تشغيل الهاتف | حساس | — |
| BIND_VPN_SERVICE | فلترة DNS على الجهاز | حساس | VPN محلية فقط، عنوان IP واحد (10.0.0.2)، لا يتصل بخادم خارجي |
| BIND_ACCESSIBILITY_SERVICE | (1) كشف التطبيق المفتوح للحجب (2) قراءة شريط العنوان (3) صور الريلز/الشورتس للتمويه | حساس جدًا | تحتاج إعلان **Accessibility API declaration** + يجب عدم استخدامه لأغراض خارج نطاق الحجب |
| BIND_DEVICE_ADMIN (اختياري) | يمنع إلغاء تثبيت التطبيق أثناء قفل الحماية | حساس | ليس إلزاميًا للتشغيل؛ يوصى به أثناء القفل/الوضع الصارم |
| PACKAGE_USAGE_STATS (اختياري) | زمن الشاشة + حدود يومية للتطبيقات | حساس | يطلب نظامًا — بلا قراءة لمحتوى الشاشة |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | فتح صفحة استثناء البطارية مباشرة (dialog واحد) | حساس | لا يطلب DEVICE_ADMIN هنا |
| REQUEST_INSTALL_PACKAGES | تثبيت نسخة موقّعة من ملف التحديث الذي نزّله التطبيق وتحقّق من بصمته، بموافقة المستخدم في مثبّت Android | **مقيَّد** | انظر §1.1 — الخطر الأساسي عند النشر على بلاي |
| QUERY_ALL_PACKAGES | **ممنوع** — نستخدم `<queries>` للتطبيق واللانشر فقط (الحد الأدنى) | — | لا تُضف QUERY_ALL_PACKAGES أبدًا |

### 1.1) التحديث الذاتي — خطر النشر على Google Play

المسار الحالي: البيان على GitHub Pages → التنزيل → التحقق من SHA-256 → مثبّت Android.
هذا يحتاج `REQUEST_INSTALL_PACKAGES`، وهو إذن **مقيَّد** عند Google Play: يُمنح للتطبيقات التي يكون تثبيت التطبيقات وظيفة أساسية لها فقط.

**من 2.2.0 صار الفحص والتنزيل تلقائيين** (كل ست ساعات، وعند توفّر شبكة)، بنقرة واحدة لإطفاءهما من الإعدادات. هذا يجعل الوضع أخطر عند بلاي لا أقلّ: تنزيل APK في الخلفية بلا طلب من المستخدم هو بالضبط ما يراه المراجع كـ«installing other apps» بغضّ النظر عن الغرض.

**القاعدة:** هذا المسار مُعدّ للتوزيع خارج المتجر (APK مُحمَّل يدويًا من موقع المالك) — وهو ما ينطبق على الوضع الحالي. عند النشر على Google Play:

- استبدل مسار البيان بـ **Play In-App Updates API** (`com.google.android.play.core`)، أو
- احذف `REQUEST_INSTALL_PACKAGES` من البيان كلها وفعّل التحديث من داخل المتجر فقط.

**لا يُنشر على بلاي وهو يحمل `REQUEST_INSTALL_PACKAGES` مع مسار البيان الخارجي.** إن بقي الإذن في النسخة المنشورة على بلاي فقد يُرفض التطبيق أو يُسحب. راجع `docs/update-hosting.md` للربط بين المسارين.

**عند النشر على بلاي يُلغى تلقائياً:**
- التنزيل التلقائي في الخلفية (Play يعالج التحديث بنفسه).
- مفتاحا «التحديث التلقائي» و«التنزيل التلقائي» في الإعدادات، أو يُبقيان معطّلين افتراضياً في نسخة المتجر.

> ملاحظة: لا يوجد زر «تحديث» تلقائي داخل التطبيق في الوقت الحالي — الفحص يدوي بالكامل
> (زر «تحقّق من وجود تحديث» في الإعدادات). هذا يخفف المخاطرة لكنه لا يلغيها.

## 2) Data Safety — الإجابات الجاهزة

| النوع | مجموع؟ | السجلات | لماذا |
|---|---|---|---|
| Personal info (name, age) | **لا يُجمع** | تُحفظ محليًا في DataStore وتبقى على الجهاز | تُخزَّن في DataStore الخاصة بالتطبيق — لا تغادر الجهاز |
| App activity / app info | **لا يُجمع** | قائمة الحجب والسلسلة والإحصائيات محلية 100% | لا خادم، لا حساب، لا تشخيص |
| Device or other IDs | **لا يُجمع** | — | — |
| Data shared? | **لا تُشارك إطلاقًا** | — | — |
| Security practices | مشفّرة في النقل (HTTPS فقط وليست إلحاقية) — البيانات لا تغادر الجهاز أصلًا | — | — |
| Account deletion | **لا يوجد حساب** — لا شيء لحذفه؛ لكن التطبيق يوفّر «مسح كل بياناتي» من الإعدادات | — | — |

## 3) Accessibility API Declaration (مسودة)

```
Sawn uses the AccessibilityService API to:
1. Detect when a user-blocked app is brought to the foreground to show a
   full-screen intercept (hard block or mindful pause), and to read the address
   bar in supported browsers to filter adult websites that bypass DNS filtering.
2. Optionally capture a transient screenshot of the foreground window to classify
   NSFW content locally (on-device ML, no image leaves the device), discarding it
   immediately after classification.
Data collected: none. No screen text or images are stored or transmitted.
This use is a core part of a digital-wellbeing blocker and cannot be implemented
with a less privileged API.
```

## 4) VPN Declaration (مسودة)

```
Sawn operates a LOCAL VPN (10.0.0.2/32) that intercepts DNS queries and filters
domains against a user-curated, offline adult-content blocklist. Non-blocked
queries are forwarded to the system's own resolver (or a documented public
fallback resolver when none is exposed). No other traffic is intercepted,
stored, logged, or transmitted. The VPN is excluded from Sawn itself
(addDisallowedApplication) and cannot see URLs, page content, or credentials.
```

## 5) Device Admin Justification (مسودة)

```
Sawn requests device administrator access SOLELY to prevent the app from being
uninstalled while the user has enabled time-lock or strict mode. It declares
no enforcement policies, no passwords, no remote wipe — only the uninstall
protection hook ("BIND_DEVICE_ADMIN" with an empty admin policy set).
```

## 8) البنية المعطلة مسبقًا (Dormant infrastructure) — تصريح أمانة

اعتبارًا من 2.0.0

- **لا إعلانات تُعرض لأي مستخدم** — لا Google Mobile Ads SDK، لا معرف تطبيق، لا banners
- **لا مشتريات داخلية عبر Google Play Billing** — وبما أن التوزيع المخطط للنسخة المدفوعة هو من موقع المالك، لا يُطلب Billing الآن
- **لا فحص تحديث نشط يتصل بالإنترنت** — Distribution channel الأصلي (APK من الموقع) يعمل كما هو
- **كل الميزات مفعلة ومجانية للجميع** — Free tier الآن = كامل الميزات

**النموذج المقرّر للتفعيل لاحقًا:**
- الإعلانات تُعرض فقط للمستخدمين الذين **لم يشتروا** النسخة المدفوعة (Free tier)، من شبكات إعلانية جوالية معروفة، في الإصدار الذي يفعل ذلك بعد: تحديث privacy.md، إضافة SDK مصرّح بها في Play Console، Data Safety متطابق، وزر واضح «إزالة الإعلانات» في الإعدادات.
- النسخة المدفوعة (بدون إعلانات) تُباع مباشرة من موقع المالك (وليست إلزامًا عبر بلاي) — ثم يُنشر على بلاي/آبل بتصريح/سياسات مناسبة حينها.
- كل هذه البنى (`ads/` + `entitlement/` + `update/`) تمرّنها فقط لتسهيل التفعيل لاحقًا، وتبقى معطلة صراحة في 2.0.0.

ما يوجد في الكود (لا تظهر واجهة المستخدم أيًا منه):
- `ads/AdsController.kt` — عقد `isEnabled(slot)` + `NoopAdsController` (دائمًا false)
- `entitlement/EntitlementService.kt` — تصنيف FREE/PAID و`NoopEntitlementService` (دائمًا مجاني/مفعل)
- `update/UpdateChecker.kt` — عقد التحديث + `RemoteManifestUpdateChecker` معطل (لم تُضف تبعية شبكة جديدة)
- `di/AppModule.kt` — يحجز نقاط الحقن (binding points) في الرسم الحالي

**قبل أي تفعيل مستقبلًا (يشترط كل واحد):**
1. إعلان محدّث في privacy.md (الأذونات، المعرفات، الغرض، الاحتفاظ بالبيانات)
2. تصريح Play Console محدّث و Data Safety بنفس يوم الإطلاق
3. خيار "إزالة الإعلانات" واضح لكل مستخدم (زر في الإعدادات)
4. لا يرى كل مستخدم إعلانات حتى يُفعّل الإصدار الإعلاني صراحة

## 9) Prohibited surface checklist (لا تُضف)

| السؤال | الجواب |
|---|---|
| Is the app a browser? | No |
| Does the app monetize or have ads? | No |
| Is this app directed to children? | No — tools for the adult user; parental mode exists for parents managing a child's device |
| Does the app use accessibility API? | Yes — declaration above |
| Does the app use VPN? | Yes — declaration above |
| Does the app host remote code? | No — TF Lite model is bundled in assets; no code ever fetched at runtime |
| Does the app use location? | No permission requested |
| Does the app implement account deletion? | No account exists; "Erase all my data" in-app wipes everything local |
| Does the app use a hotline/helpline? | Opens the external Sawn support page — no tracking, no data sent |
| Is the app free? | Yes, no IAP, no subscriptions |

## 7) Prohibited surface checklist (لا تُضف)

- [ ] لا زر "إلغاء التثبيت" داخل التطبيق (إزالة المستخدم عبر الإلغاء فقط)
- [ ] لا تحاكي محتويات خارج التطبيق (لا تعرض صور شريك/طفل)
- [ ] لا رسائل تسويقية/إعلانات داخل شاشة الحجب
- [ ] لا مقاومة للمراجعة: الوصول الكامل للرمز متاح للمحققين (privacy + security docs)
- [ ] لا تشفير خبيث/توزيع ثانٍ (لا "update APK" داخلي يتنكر لتطبيق آخر)
- [ ] لا استخدام Accessibility لأغراض غير الحجب (لا نقرات نيابةً، لا قراءة كلمات المرور/الدفع)
