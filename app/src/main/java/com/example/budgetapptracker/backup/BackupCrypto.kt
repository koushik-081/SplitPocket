package com.example.budgetapptracker.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Locks a backup with a password, so the file is unreadable to anyone it is shared with
// or stored by. Uses AES-256, the standard used for bank and government data.
//
// File layout, version 2:  "BBK2" | hint length (2 bytes) | hint | salt | iv | locked data
// File layout, version 1:  "BBK1" | salt | iv | locked data        (older backups, no hint)
object BackupCrypto {
    private val MAGIC_V1 = "BBK1".toByteArray()
    private val MAGIC_V2 = "BBK2".toByteArray()
    private const val MAGIC_BYTES = 4
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 120_000
    const val MAX_HINT_LENGTH = 60

    fun encrypt(plain: ByteArray, password: String, hint: String = ""): ByteArray {
        val hintBytes = hint.trim().take(MAX_HINT_LENGTH).toByteArray()
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        // The hint is readable, but tied to the locked data so it cannot be swapped unnoticed.
        cipher.updateAAD(hintBytes)
        val length = byteArrayOf((hintBytes.size shr 8).toByte(), hintBytes.size.toByte())
        return MAGIC_V2 + length + hintBytes + salt + iv + cipher.doFinal(plain)
    }

    fun isBackup(file: ByteArray): Boolean = layout(file) != null

    // The hint saved with a backup, or an empty string if it has none.
    fun hintOf(file: ByteArray): String = layout(file)?.hint?.let { String(it) }.orEmpty()

    // Throws if the password is wrong or the file has been altered.
    fun decrypt(file: ByteArray, password: String): ByteArray {
        val layout = requireNotNull(layout(file)) { "Not a backup file" }
        var offset = layout.bodyStart
        val salt = file.copyOfRange(offset, offset + SALT_BYTES).also { offset += SALT_BYTES }
        val iv = file.copyOfRange(offset, offset + IV_BYTES).also { offset += IV_BYTES }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        if (layout.hint != null) cipher.updateAAD(layout.hint)
        return cipher.doFinal(file, offset, file.size - offset)
    }

    // Where the hint is (version 2 only) and where the salt begins.
    private class Layout(val hint: ByteArray?, val bodyStart: Int)

    private fun layout(file: ByteArray): Layout? {
        if (file.size <= MAGIC_BYTES) return null
        val magic = file.copyOfRange(0, MAGIC_BYTES)
        val result = when {
            magic.contentEquals(MAGIC_V1) -> Layout(hint = null, bodyStart = MAGIC_BYTES)
            magic.contentEquals(MAGIC_V2) -> {
                if (file.size < MAGIC_BYTES + 2) return null
                val length = ((file[MAGIC_BYTES].toInt() and 0xFF) shl 8) or (file[MAGIC_BYTES + 1].toInt() and 0xFF)
                val hintStart = MAGIC_BYTES + 2
                if (file.size < hintStart + length) return null
                Layout(hint = file.copyOfRange(hintStart, hintStart + length), bodyStart = hintStart + length)
            }
            else -> return null
        }
        // There must be room for the salt, the iv and at least some locked data.
        return result.takeIf { file.size > it.bodyStart + SALT_BYTES + IV_BYTES }
    }

    // Stretches the password into a key slowly on purpose, to make guessing expensive.
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}