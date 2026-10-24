package com.onemessages.app.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun roundTripReturnsTheOriginalBytes() {
        val plaintext = """{"conversations":[],"version":1}""".toByteArray()
        val password = "correct horse battery".toCharArray()

        val blob = BackupCrypto.encrypt(plaintext, password)
        val restored = BackupCrypto.decrypt(blob, password.copyOf())

        assertThat(restored).isEqualTo(plaintext)
        // The ciphertext must not leak the plaintext.
        assertThat(String(blob, Charsets.ISO_8859_1)).doesNotContain("conversations")
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val plaintext = "same input".toByteArray()
        val password = "another password".toCharArray()
        val first = BackupCrypto.encrypt(plaintext, password)
        val second = BackupCrypto.encrypt(plaintext, password)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun wrongPasswordIsRejected() {
        val blob = BackupCrypto.encrypt("secret".toByteArray(), "right password".toCharArray())
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(blob, "wrong password".toCharArray())
        }
    }

    @Test
    fun truncatedFileIsRejected() {
        val blob = BackupCrypto.encrypt("secret".toByteArray(), "right password".toCharArray())
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(blob.copyOfRange(0, 8), "right password".toCharArray())
        }
    }

    @Test
    fun secureWipeClearsTheBuffer() {
        val buffer = byteArrayOf(1, 2, 3, 4)
        BackupCrypto.secureWipe(buffer)
        assertThat(buffer.toList()).containsExactly(0, 0, 0, 0)
    }
}
