package com.onemessages.app.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption for local backups.
 *
 * AES-256-GCM with a PBKDF2-HMAC-SHA256 key (random 16-byte salt, 210k iterations) and a
 * random 12-byte IV per file. Standard primitives only, no home-grown cryptography.
 *
 * File layout: MAGIC(8) | VERSION(1) | salt(16) | iv(12) | ciphertext+tag
 *
 * Pure JVM code so the round-trip and wrong-password behaviour are unit tested.
 */
object BackupCrypto {

    val MAGIC = byteArrayOf(0x31, 0x4D, 0x53, 0x47, 0x42, 0x41, 0x4B, 0x31) // "1MSGBAK1"
    const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    const val MIN_PASSWORD_LENGTH = 8

    class WrongPasswordException : Exception("wrong password or corrupted backup")

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "password too short" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + byteArrayOf(VERSION) + salt + iv + ciphertext
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size < MAGIC.size + 1 + SALT_SIZE + IV_SIZE) throw WrongPasswordException()
        if (!blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw WrongPasswordException()
        val version = blob[MAGIC.size]
        if (version != VERSION) throw WrongPasswordException()
        var offset = MAGIC.size + 1
        val salt = blob.copyOfRange(offset, offset + SALT_SIZE); offset += SALT_SIZE
        val iv = blob.copyOfRange(offset, offset + IV_SIZE); offset += IV_SIZE
        val ciphertext = blob.copyOfRange(offset, blob.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // GCM authentication failure means a wrong key or a damaged file. We never fall
            // back to partial plaintext.
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun secureWipe(bytes: ByteArray) {
        bytes.fill(0)
    }
}
