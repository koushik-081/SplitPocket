package com.example.budgetapptracker.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Locks a backup with a password, so the file is unreadable to anyone it is shared with
// or stored by. Uses AES-256, the standard used for bank and government data.
object BackupCrypto {
    private val MAGIC = "BBK1".toByteArray()
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 120_000

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun isBackup(file: ByteArray): Boolean =
        file.size > MAGIC.size + SALT_BYTES + IV_BYTES && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    // Throws if the password is wrong or the file has been altered.
    fun decrypt(file: ByteArray, password: String): ByteArray {
        var offset = MAGIC.size
        val salt = file.copyOfRange(offset, offset + SALT_BYTES).also { offset += SALT_BYTES }
        val iv = file.copyOfRange(offset, offset + IV_BYTES).also { offset += IV_BYTES }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(file, offset, file.size - offset)
    }

    // Stretches the password into a key slowly on purpose, to make guessing expensive.
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}