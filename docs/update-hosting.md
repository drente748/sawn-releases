# استضافة التحديثات المجانية لصَوْن

يستضيف التطبيق بيان التحديثات على **GitHub Pages**، وملفات APK على **GitHub Releases**، ويُنشر كلٌّ منهما تلقائياً عبر **GitHub Actions**. لا يحتاج التطبيق إلى اتصال بالخادم إلا حين يختار المستخدم «تحقّق من وجود تحديث» في الإعدادات. رابط البيان يُضمَّن وقت البناء في `BuildConfig.UPDATE_MANIFEST_URL`؛ إذا تُرك فارغًا تبقى ميزة التحديث معطّلة.

## القيم الثابتة لهذا المستودع

| القيمة | القيمة |
|---|---|
| المستودع | `drente748/warden` |
| رابط البيان | `https://drente748.github.io/warden/updates/manifest.json` |
| فرع النشر | `gh-pages` (جذر المجلد) |
| ملف البيان | `updates/manifest.json` |
| بصمة شهادة التوقيع | `3e2ffe2f03ef0c9506a863fb32b63e5e8ed2a44ed99e819c0306da8bae2d1d86` |

## ما الذي يفعله مسار النشر

عند دفع tag مثل `v2.2.0` وتشغيل GitHub Actions:

1. يُبنى APK Release بإصدار Gradle وSDK المثبتين، مع رابط البيان مدمجًا في `BuildConfig`.
2. يُوقَّع بمفتاح التوقيع نفسه الذي وقّع النسخ الموجودة.
3. يُقارَن SHA-256 لشهادة التوقيع مع `SAWN_SIGNING_CERT_SHA256`؛ يفشل النشر إذا لم تتطابق.
4. يُحسب SHA-256 للـ APK، وتُستخرج ملاحظات الإصدار من `docs/CHANGELOG.md` (قسم النسخة الجارية حرفيًا).
5. يُرفض النشر إذا لم يطابق الـ tag رقم نسخة الـ APK — وإلا لأشار البيان إلى رابط لا يُطابق الإصدار.
6. يُنشر APK باسم `app-release.apk` وملف `manifest.json` في GitHub Release.
7. يُنشر البيان إلى `updates/manifest.json` على فرع `gh-pages`.
8. يُتحقق من أن البيان صار قابلاً للوصول فعليًا قبل انتهاء الـ Workflow.

عند طلب التحديث، يتحقق التطبيق من أن روابط المانيفست تستخدم HTTPS، وأن رابط APK صالح، وأن بصمة SHA-256 موجودة. بعد التنزيل يُعاد حساب البصمة محليًا؛ لا يُفتح مُثبّت Android قبل تطابقها. يتطلب التثبيت إذن Android لمصدر التثبيت وموافقة المستخدم ولا يوجد تثبيت صامت.

## الإعداد لمرة واحدة

> **الشرط:** حساب يملك **صلاحيات admin** على المستودع. بحساب قراءة فقط تفشل الخطوة الأولى بوضوح. سجّل الدخول بالحساب المالك:
> ```powershell
> gh auth login
> gh auth status          # تأكّد أن الحساب النشط هو مالك المستودع
> ```

### الطريقة الموصى بها — سكربت واحد

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-update-hosting.ps1 -Version 2.2.0
```

السكربت:

- يتحقق من تثبيت `gh` ومن تسجيل الدخول ومن صلاحيات admin.
- يقرأ `SAWN_*` من `local.properties` (المخزَّن نفسه،Keys).
- يحسب بصمة SHA-256 لشهادة التوقيع من الـ keystore ويقارنها بالبصمة الموثّقة — **ويرفض المتابعة** إن اختلف المفتاح، لأن تغييره يكسر كل نسخة مثبّتة.
- يرفع الأسرار الخمسة إلى Actions: `SAWN_KEYSTORE_BASE64` و`SAWN_STORE_PASSWORD` و`SAWN_KEY_ALIAS` و`SAWN_KEY_PASSWORD` و`SAWN_SIGNING_CERT_SHA256`. الـ keystore يُرمَّز Base64 في الذاكرة ويُمرَّر إلى `gh secret set` عبر الأنبوب — فلا يظهر في السجل ولا في سطر الأوامر ولا في ملف مؤقت.
- يفعّل GitHub Pages من فرع `gh-pages`.

السكربت **لا** ينشر شيئًا ولا pushes أي tag — النشر خطوة منفصلة صريحة أدناه.

### الطريقة اليدوية

1. أنشئ فرع `gh-pages` أولًا (GitHub Pages لا يُفعَّل على فرع غير موجود):
   ```powershell
   git fetch origin
   git checkout --orphan gh-pages
   git rm -rf .
   New-Item -ItemType Directory -Force -Path updates | Out-Null
   '{"versionCode":1,"versionName":"0.0.1","apkUrl":"https://example.com/","sha256":"0000000000000000000000000000000000000000000000000000000000000000","changelog":"","mandatory":false}' | Set-Content -Encoding utf8 updates/manifest.json
   git add updates/manifest.json
   git commit -m "chore: initialise gh-pages"
   git push origin gh-pages
   git checkout -
   ```
2. فعّل **Settings → Pages → Deploy from a branch → `gh-pages` / `/ (root)**`.
3. أضف الأسرار الخمسة من **Settings → Secrets and variables → Actions → New repository secret**:
   - `SAWN_KEYSTORE_BASE64`: محتوى ملف keystore مشفّرًا بـ Base64. على PowerShell:
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes("sawn-release.keystore")) | gh secret set SAWN_KEYSTORE_BASE64 --repo drente748/warden
     ```
     احتفظ بالسر الأصلي في مكان آمن؛ فقده يمنع تحديث التطبيقات المثبتة.
   - `SAWN_STORE_PASSWORD`: كلمة مرور المخزن.
   - `SAWN_KEY_ALIAS`: اسم المفتاح (`warden`).
   - `SAWN_KEY_PASSWORD`: كلمة مرور المفتاح.
   - `SAWN_SIGNING_CERT_SHA256`: البصمة الموثّقة أعلاه.
4. تحقّق من أن شهادة keystore هي الشهادة نفسها المستخدمة لتوقيع APK الذي ثبّته المستخدمون. **لا تغيّر المفتاح.**

## النشر (تحديث جديد)

```powershell
# 1. تأكّد أن رقم النسخة في app/build.gradle.kts يطابق، وأن docs/CHANGELOG.md فيه قسم للنسخة
# 2. ادفع الكود
git add -A
git commit -m "release: 2.2.0"
git push origin <branch>

# 3. ادفع الوسم — هذا ما يشغّل النشر
git tag v2.2.0
git push origin v2.2.0
```

ينتظر الـ Workflow (~٥ دقائق) ثم: يصبح `https://drente748.github.io/warden/updates/manifest.json` متاحًا، ويظهر الإصدار في **Releases**، ويكتشفه التطبيق عند الضغط على «تحقّق من وجود تحديث».

## النسخة المثبّتة على الأجهزة

APKات قديمة بُنيت برابط بيان فارغ لا تعرف عنوان الاستضافة — لأن العنوان يُدمج وقت البناء لا يُجلب وقت التشغيل. لذلك:

- **النسخة الأولى التي تُوزَّع على المستخدمين يجب أن تُبنى وهي تحمل الرابط**، وإلا لن تعمل التحديثات فيها.

للبناء محليًا بالرابط:

```powershell
$env:SAWN_UPDATE_MANIFEST_URL = "https://drente748.github.io/warden/updates/manifest.json"
.\gradlew.bat :app:assembleRelease
```

ويقبل Gradle الخاصية `-PSAWN_UPDATE_MANIFEST_URL=https://...` أيضًا. الروابط غير HTTPS مرفوضة. يبقى المانيفست فارغًا افتراضيًا، فلا يُجرى أي طلب شبكة للتحديث دون إعداد صريح وإعادة بناء.

> **قاعدة الـ versionCode:** رَقِّ `versionCode` في كل إصدار. المثبّت يرفض أي APK رقماً أقل أو مساوياً لنسخة الجهاز (`APK version is not newer`)، فإعادة بناء نفس `versionCode` بجسم مختلف يخلق نسخة لا يستطيع التطبيق تحديث نفسه إليها أبداً. في 2.2.0 رُفع الرقم من 30 إلى 31 لهذا السبب: نسخة 30 التجريبية كانت بلا رابط بيان.

### أرقام النسخ

| الإصدار | versionCode |
|---|---|
| 2.2.0 | 31 |
| 2.1.9 | 29 |
| 2.1.8 | 28 |

## كيف يصل التحديث إلى المستخدم

من 2.2.0، المسار **تلقائي بالكامل حتى الخطوة الأخيرة**:

```
كل 6 ساعات (عند شبكة) → فحص البيان → مقارنة versionCode
      ↓ وُجد تحديث
تنزيل في الخلفية → إعادة حساب SHA-256 محلياً → لا يُعتمد إلا إن طابقت
      ↓
إشعار «التحديث جاهز» → نقرة → مثبّت أندرويد بالملف المُتحقَّق منه → ضغط «تثبيت»
```

**ما لا يستطيعه التطبيق:** التثبيت الصامت. أندرويد يشترط ضغط المستخدم على «تثبيت» في مثبّت النظام، ولا توجد واجهة لأي تطبيق تتجاوز ذلك. لذلك:

| الخطوة | تلقائية؟ |
|---|---|
| فحص البيان | ✅ كل ٦ ساعات |
| تنزيل الملف | ✅ في الخلفية |
| التحقق من SHA-256 | ✅ محلياً |
| إشعار «جاهز» | ✅ |
| فتح مثبّت النظام | ✅ بنقرة واحدة |
| ضغط «تثبيت» | ❌ **يطلبه أندرويد من المستخدم** |

المفتاحان في **الإعدادات ← الخصوصية والدعم ← تحديث صَوْن**، وكلاهما **مفعّل افتراضياً**:
- **التحديث التلقائي** — يفحص البيان كل ست ساعات. إطفاؤه يمنع أي اتصال شبكي لهذا الغرض.
- **التنزيل التلقائي** — ينزّل التحديث في الخلفية فور 발견ه ويتحقّق من بصمته.
- **التثبيت فور الجاهزية** — يُنبّهك لحظة جهوزية الملف المُتحقَّق منه.

عند إطفاء «التحديث التلقائي» لا يفتح التطبيق شبكةً لهذا الغرض إطلاقاً. التفاصيل في `docs/privacy.md`.

## صيغة `manifest.json`

```json
{
  "versionCode": 31,
  "versionName": "2.2.0",
  "apkUrl": "https://github.com/drente748/warden/releases/download/v2.2.0/app-release.apk",
  "sha256": "<64 lowercase hexadecimal characters>",
  "changelog": "ملاحظات الإصدار مستخرجة من docs/CHANGELOG.md",
  "mandatory": false
}
```

لا يُعدَّل البيان يدويًا بعد بناء APK؛ الـ Workflow يحسب بصمة SHA-256 وينشر البيان والملف معًا. ارفع `versionCode` لكل إصدار جديد. يجب أن يكون APK Release موقّعًا بالمفتاح نفسه.

## حدود ونقاط أمان

- GitHub Pages وReleases يستضيفان ملفات عامة، وليس هناك أي سر داخل التطبيق.
- الفحص اختياري يدوي؛ لا استعلام تلقائي عند فتح التطبيق، ولا رفع بيانات استخدام.
- بصمة SHA-256 تمنع تثبيت ملف تالف أو مختلف عن الملف المعلن؛ HTTPS يحمي النقل.
- بصمة SHA-256 ليست توقيعًا مستقلًا إذا اختُرق كل من GitHub Pages وGitHub Release معًا. يظل Android يتحقق أيضًا من توقيع APK أثناء التحديث. احتفظ بمفتاح keystore خارج GitHub والكود، واستخدم أسرار Actions فقط.
- ينشر الـ Workflow APK الحالي فقط؛ إعداد الـ tag وتوقيع الإصدار يجب أن يطابقا `versionName` و`versionCode` في Gradle.
- تتطلب ميزة تحديث APK إذن «تثبيت تطبيقات غير معروفة» وموافقة المستخدم على شاشة مثبت Android.

## أدوات مساعدة

| الأداة | الغرض |
|---|---|
| `scripts/setup-update-hosting.ps1` | إعداد الأسرار وPages لمرة واحدة (لا ينشر) |
| `scripts/verify-release.ps1` | بوابة جودة للـ APK: بصمة الشهادة + رقم النسخة + وجود رابط البيان داخل الحزمة |
| `scripts/audit_release.py` | تدقيق ما قبل الإصدار: أسرار مسرّبة · تطابق النصوص العربية · صحة XML · روابط التوثيق · سلّم الأرقام |
| `scripts/extract_changelog.py` | استخراج ملاحظات الإصدار من `docs/CHANGELOG.md` — يستدعيه الـ Workflow، ويُشغَّل محليًا قبل النشر |
| `scripts/validate_workflow.py` | يُصرّف كتل Python داخل الـ Workflow ويفحصها نحويًا قبل أول نشر |

## بوابة الجودة قبل النشر

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -ExpectedVersion 2.2.0
```

السكربت يفشل إن اختلفت بصمة الشهادة عن الموثّقة، أو اختلف رقم النسخة، أو **غاب رابط البيان داخل الـ APK**. الفحص الأخير هو الأهم: الرابط يُدمج وقت البناء، فبناء بلا `SAWN_UPDATE_MANIFEST_URL` يُنتج نسخة يبقى زر «تحقّق من وجود تحديث» معطّلاً فيها إلى الأبد — ولا يمكن إصلاحه لاحقاً إلا بإصدار جديد.
