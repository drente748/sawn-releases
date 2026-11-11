package com.sawn.app.update

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات صيغة البيان.
 *
 * الجسم المستخدم هنا هو نفسه ما يكتبه مسار النشر في
 * `.github/workflows/release-apk.yml` — الحقول نفسها، والقيمة نفسها،
 * وملاحظات عربية. لو غيّر المسار حقلاً أو اسم حقل، يسقط هذا الاختبار.
 *
 * Robolectric هنا لأن `org.json` في اختبارات JVM بدونه مُهيَّى كـ stub لا
 * يُنفَّذ — وهذا بالضبط ما نريد اختباره.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class UpdateCheckerTest {

    /** بصمة ملف APK — مثال ثابت، ما يهمّ هو كونها 64 محرفاً hex. */
    private val fileDigest = "a3f5c8e21b7d9046f2a8c6e35b1d79f0426ac8e35b1d79f0426ac8e35b1d79f0"

    /** البيان كما ينتجه مسار النشر فعلياً. */
    private val publishedManifest = """
        {
          "versionCode": 31,
          "versionName": "2.2.0",
          "apkUrl": "https://github.com/drente748/warden/releases/download/v2.2.0/app-release.apk",
          "sha256": "$fileDigest",
          "changelog": "ثغرات تجاوز الحماية\nأنهيارات\nشبكة/نفق",
          "mandatory": false
        }
    """.trimIndent()

    @Test
    fun blankManifestDisablesNetworkChecks() = runBlocking {
        assertEquals(UpdateStatus.Disabled, RemoteManifestUpdateChecker("").check(25))
    }

    @Test
    fun insecureManifestIsRejectedBeforeOpeningAConnection() = runBlocking {
        val result = RemoteManifestUpdateChecker("http://updates.example.test/manifest.json").check(25)
        assertEquals(UpdateStatus.Error("Manifest must use HTTPS"), result)
    }

    @Test
    fun publishedManifestIsParsedIntoAnUpdate() {
        val available = parseManifest(publishedManifest, currentVersionCode = 30) as UpdateStatus.Available
        assertEquals(31, available.info.versionCode)
        assertEquals("2.2.0", available.info.versionName)
        assertTrue(available.info.apkUrl.startsWith("https://github.com/"))
        assertEquals(64, available.info.sha256.length)
        assertEquals(fileDigest, available.info.sha256)
        assertTrue(available.info.changelog.contains("ثغرات تجاوز الحماية"))
        assertFalse(available.info.mandatory)
    }

    @Test
    fun sameVersionIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, parseManifest(publishedManifest, currentVersionCode = 31))
    }

    @Test
    fun newerInstalledVersionIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, parseManifest(publishedManifest, currentVersionCode = 40))
    }

    @Test
    fun zeroVersionCodeIsRejected() {
        val manifest = publishedManifest.replace("\"versionCode\": 31", "\"versionCode\": 0")
        assertEquals(UpdateStatus.Error("Invalid versionCode"), parseManifest(manifest, 30))
    }

    @Test
    fun missingVersionCodeIsRejected() {
        val manifest = publishedManifest.replace("\"versionCode\": 31,", "")
        assertEquals(UpdateStatus.Error("Invalid versionCode"), parseManifest(manifest, 30))
    }

    @Test
    fun nonHttpsApkUrlIsRejected() {
        val manifest = publishedManifest.replace(
            "https://github.com/drente748/warden/releases/download/v2.2.0/app-release.apk",
            "http://example.test/app-release.apk",
        )
        assertEquals(
            UpdateStatus.Error("Invalid APK URL or SHA-256 checksum"),
            parseManifest(manifest, 30),
        )
    }

    @Test
    fun malformedChecksumIsRejected() {
        val manifest = publishedManifest.replace(fileDigest, "not-a-sha256")
        assertEquals(
            UpdateStatus.Error("Invalid APK URL or SHA-256 checksum"),
            parseManifest(manifest, 30),
        )
    }

    @Test
    fun uppercaseChecksumIsAccepted() {
        val manifest = publishedManifest.replace(fileDigest, fileDigest.uppercase())
        val available = parseManifest(manifest, 30) as UpdateStatus.Available
        assertEquals(fileDigest, available.info.sha256)
    }

    @Test
    fun longChangelogIsTruncatedToTheDialogLimit() {
        val long = "سطر طويل. ".repeat(500)
        val manifest = publishedManifest.replace(
            "ثغرات تجاوز الحماية\\nأنهيارات\\nشبكة/نفق",
            long.replace("\\", "\\\\").replace("\"", "\\\""),
        )
        val available = parseManifest(manifest, 30) as UpdateStatus.Available
        assertTrue(available.info.changelog.length <= 2_000)
    }
}
