package com.sawn.domain.feature

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * نسخة احتياطية `.sawnbak` — AES-256-GCM + PBKDF2 بـ 310,000 تكرار.
 * المفتاح من عبارة مرور. لا تشمل PIN أبدًا. لا تشمل أي بيانات تخرج من الجهاز.
 */
object BackupCrypto {

    private const val ITERATIONS = 310_000
    private const val KEY_BYTES = 32 // AES-256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12 // GCM 권장
    private const val TAG_BITS = 128
    private const val ALIAS = "AES/GCM/NoPadding"

    /** تصدير: يُعيد بصيغة [salt][iv][ciphertext]. */
    fun encrypt(plaintext: String, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALIAS)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return salt + iv + ct
    }

    /** استيراد: يفكّ التشفير أو يُعيد null (عبارة خاطئة/ملف عبث به). */
    fun decrypt(payload: ByteArray, passphrase: String): String? {
        if (payload.size < SALT_BYTES + IV_BYTES) return null
        val salt = payload.copyOfRange(0, SALT_BYTES)
        val iv = payload.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val ct = payload.copyOfRange(SALT_BYTES + IV_BYTES, payload.size)
        return try {
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(ALIAS)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BYTES * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        // ناتج PBKDF2 مفتاح_uniform بطول 256 بت بالفعل، فتجزئة extra لا تضيف شيئًا.
        val hash = factory.generateSecret(spec).encoded
        // مسح بايتات كلمة السر من الذاكرة فور استخراج المفتاح.
        spec.clearPassword()
        return SecretKeySpec(hash, "AES")
    }
}
