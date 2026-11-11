package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A backup the user believes in must actually round-trip. The shipped screen exported only
 * the literal "sawn-backup-export", so "restore" could never bring anything back.
 */
class BackupCryptoTest {

    private val pass = "correct horse battery staple"

    @Test
    fun `round trip preserves utf8 payload`() {
        val payload = """{"version":1,"settings":{"locale":"ar"},"note":"صَوْن"}"""
        val sealed = BackupCrypto.encrypt(payload, pass)
        assertEquals(payload, BackupCrypto.decrypt(sealed, pass))
    }

    @Test
    fun `wrong passphrase returns null`() {
        val sealed = BackupCrypto.encrypt("secret", pass)
        assertNull(BackupCrypto.decrypt(sealed, "wrong passphrase"))
    }

    @Test
    fun `tampered ciphertext returns null`() {
        val sealed = BackupCrypto.encrypt("secret", pass)
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()
        assertNull(BackupCrypto.decrypt(sealed, pass))
    }

    @Test
    fun `short payload returns null`() {
        assertNull(BackupCrypto.decrypt(ByteArray(4), pass))
    }

    @Test
    fun `same payload encrypts differently each time`() {
        assertNotEquals(
            BackupCrypto.encrypt("x", pass).toList(),
            BackupCrypto.encrypt("x", pass).toList(),
        )
    }
}
