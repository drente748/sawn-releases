package com.sawn.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

sealed interface InstallUpdateResult {
    data class Ready(val packageUri: Uri) : InstallUpdateResult
    data class Failed(val reason: String) : InstallUpdateResult
}

/** Downloads only HTTPS APKs, verifies the manifest checksum, then hands off to Android. */
object UpdateInstaller {
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val APKS_DIR = "updates"

    suspend fun downloadAndVerify(context: Context, info: UpdateInfo): InstallUpdateResult =
        withContext(Dispatchers.IO) {
            if (!info.apkUrl.startsWith("https://") || !info.sha256.matches(Regex("[a-fA-F0-9]{64}"))) {
                return@withContext InstallUpdateResult.Failed("Invalid update manifest")
            }
            var connection: java.net.HttpURLConnection? = null
            try {
                val root = File(context.cacheDir, APKS_DIR).apply { mkdirs() }
                val temporary = File(root, "update-${info.versionCode}.download")
                val apk = File(root, "Sawn-${info.versionCode}.apk")
                connection = URL(info.apkUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    return@withContext InstallUpdateResult.Failed("HTTP ${connection.responseCode}")
                }
                if (connection.url.protocol != "https") throw SecurityException("APK redirect must use HTTPS")
                val length = connection.contentLengthLong
                if (length > MAX_APK_BYTES) return@withContext InstallUpdateResult.Failed("APK is too large")
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            if (count > MAX_APK_BYTES || (length > 0 && count > length)) {
                                throw IllegalStateException("Invalid APK size")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (length >= 0 && count != length) throw IllegalStateException("Incomplete download")
                if (count == 0L) throw IllegalStateException("Downloaded APK is empty")
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(actualHash.toByteArray(), info.sha256.lowercase().toByteArray())) {
                    throw SecurityException("SHA-256 verification failed")
                }
                val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getPackageArchiveInfo(temporary.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(temporary.absolutePath, 0)
                }
                if (packageInfo?.packageName != context.packageName) throw SecurityException("APK package does not match this app")
                val apkVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                if (apkVersionCode != info.versionCode.toLong()) throw SecurityException("APK version does not match manifest")
                val installedPackageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val installedVersion = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    installedPackageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    installedPackageInfo.versionCode.toLong()
                }
                if (apkVersionCode <= installedVersion) throw SecurityException("APK version is not newer")
                if (apk.exists() && !apk.delete()) throw IllegalStateException("Could not replace cached APK")
                if (!temporary.renameTo(apk)) throw IllegalStateException("Could not prepare APK")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
                InstallUpdateResult.Ready(uri)
            } catch (error: Exception) {
                File(context.cacheDir, APKS_DIR).listFiles()?.filter { it.name.endsWith(".download") }?.forEach { it.delete() }
                InstallUpdateResult.Failed(error.message ?: "Download failed")
            } finally {
                connection?.disconnect()
            }
        }

    fun install(context: Context, packageUri: Uri) {
        // ACTION_INSTALL_PACKAGE is deprecated in favour of the PackageInstaller API, but
        // it stays the safer choice here: ACTION_VIEW with the APK mime type can be
        // intercepted by another app that registers for the same type, and this installer
        // hands over an already-verified file. The system screen still asks the user to
        // confirm — the app cannot install without them.
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(packageUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * URI عام لملف APK مُتحقَّق منه، عبر FileProvider.
     *
     * لا يُمنح أي تطبيق آخر وصولاً إلى ذاكرة التطبيق: URI محصور بصلاحية
     * مؤقتة على ملف واحد. هذا ما يسمح للإشعار بأن يفتح المثبّت مباشرةً.
     */
    fun uriFor(context: Context, apkPath: String): Uri? {
        val file = File(apkPath)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    /** المسار الذي يكتب فيه [downloadAndVerify] الملف المُتحقَّق منه. */
    fun apkFileFor(context: Context, versionCode: Int): File =
        File(File(context.cacheDir, APKS_DIR), "Sawn-$versionCode.apk")

    /** يحذف ملفات التحديث القديمة التي لم تعد مطلوبة. */
    fun pruneOldApks(context: Context, keepVersionCode: Int) {
        runCatching {
            File(context.cacheDir, APKS_DIR).listFiles()
                ?.filter { it.name.startsWith("Sawn-") && it.name.endsWith(".apk") }
                ?.filter { !it.name.equals("Sawn-$keepVersionCode.apk", ignoreCase = true) }
                ?.forEach { it.delete() }
        }
    }
}
