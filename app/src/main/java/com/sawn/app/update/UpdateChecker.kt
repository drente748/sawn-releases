package com.sawn.app.update

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * يتفقد تحديثًا عامًا عند طلب المستخدم، ولا يرفع أي بيانات إلى الخادم.
 * التنزيل والتحقق من بصمة APK والتثبيت بموافقة المستخدم ضمن [UpdateInstaller].
 * يبقى URL الفحص فارغًا حتى يُحدد SAWN_UPDATE_MANIFEST_URL وقت بناء النسخة.
 * راجع docs/update-hosting.md لإعداد GitHub Pages وGitHub Releases مجانًا.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String,
    val sha256: String,
    /** تحديث إجباري (إن وُجدت إصلاحات حرجة في حماية الأطفال) — لا يُستخدم الآن. */
    val mandatory: Boolean = false,
)

sealed interface UpdateStatus {
    data object Checking : UpdateStatus
    data object Downloading : UpdateStatus

    /** The APK is verified and handed to Android's installer; awaiting the user's approval. */
    data object Installing : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data object Disabled : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/** عقد التحقق من وجود تحديث. */
interface UpdateChecker {
    suspend fun check(currentVersionCode: Int): UpdateStatus
}

/** الرد الافتراضي: لا شيء — لا يُظهر أي تنبيه، لا يتصل بالإنترنت. */
object DisabledUpdateChecker : UpdateChecker {
    override suspend fun check(currentVersionCode: Int): UpdateStatus = UpdateStatus.Disabled
}

/**
 * قارئ مانيفيست واحد من عنوان HTTPS عام.
 * - لا تبعية شبكة جديدة (HttpURLConnection مدمج).
 * - العنوان يأتي من `BuildConfig.UPDATE_MANIFEST_URL` عبر `AppModule`؛ لا يوجد عنوان
 *   مكتوب هنا — الرابط يُدمج وقت البناء ولا يُجلب وقت التشغيل.
 * - مهلة 10 ثوانٍ، لا إعادة محاولة (التحقق (check) يُستدعى عند فتح التطبيق).
 * - عند فشل الشبكة: [UpdateStatus.Error] (لا crash، لا toast).
 */
class RemoteManifestUpdateChecker(
    private val manifestUrl: String,
) : UpdateChecker {

    override suspend fun check(currentVersionCode: Int): UpdateStatus {
        if (manifestUrl.isBlank()) return UpdateStatus.Disabled
        if (!manifestUrl.startsWith("https://")) return UpdateStatus.Error("Manifest must use HTTPS")
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(manifestUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return UpdateStatus.Error("HTTP ${connection.responseCode}")
            }
            if (connection.url.protocol != "https") return UpdateStatus.Error("Manifest redirect must use HTTPS")
            if (connection.contentLengthLong > MAX_MANIFEST_BYTES) {
                return UpdateStatus.Error("Manifest too large")
            }
            val body = connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > MAX_MANIFEST_BYTES) return UpdateStatus.Error("Manifest too large")
                text
            }
            parseManifest(body, currentVersionCode)
        } catch (t: Exception) {
            Log.w("SawnUpdate", "manifest check failed", t)
            UpdateStatus.Error(t.message ?: "unknown")
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 64 * 1024
    }

}

/** أقصى طول لملاحظات الإصدار المقروءة من البيان. */
private const val MAX_CHANGELOG_CHARS = 2_000

/**
 * يقرأ بيان التحديث ويحوّله إلى حالة.
 *
 * منفصلة عن الشبكة عمداً: التقسيم يعني أن صيغة البيان التي يكتبها مسار النشر
 * تُختبر مباشرةً ضد الكود الذي يقرأها، فلا تتفرّقان بصمت. انظر
 * `UpdateCheckerTest` و`docs/update-hosting.md`.
 */
internal fun parseManifest(body: String, currentVersionCode: Int): UpdateStatus {
    val json = JSONObject(body)
    val remoteCode = json.optInt("versionCode", 0)
    if (remoteCode <= 0) return UpdateStatus.Error("Invalid versionCode")
    if (remoteCode <= currentVersionCode) return UpdateStatus.UpToDate
    val apkUrl = json.optString("apkUrl", "")
    val digest = json.optString("sha256", "").lowercase()
    if (!apkUrl.startsWith("https://") || !digest.matches(Regex("[a-f0-9]{64}"))) {
        return UpdateStatus.Error("Invalid APK URL or SHA-256 checksum")
    }
    return UpdateStatus.Available(
        UpdateInfo(
            versionName = json.optString("versionName", "").take(40),
            versionCode = remoteCode,
            apkUrl = apkUrl,
            changelog = json.optString("changelog", "").take(MAX_CHANGELOG_CHARS),
            sha256 = digest,
            mandatory = json.optBoolean("mandatory", false),
        ),
    )
}
