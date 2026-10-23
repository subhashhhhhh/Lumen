package com.subhashh.lumen.data

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** How the Hidden section is locked. */
enum class LockType(val label: String) { Pin("PIN"), Pattern("Pattern"), Password("Password") }

/**
 * Lumen's own lock for Hidden, separate from the phone's screen lock (whoever knows the phone's PIN
 * shouldn't get in for free). Only a salted PBKDF2 hash of the secret is stored, never the secret.
 */
class HiddenLockStore(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_lock", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains(KeyHash)

    val type: LockType?
        get() = prefs.getString(KeyType, null)?.let { name -> LockType.entries.firstOrNull { it.name == name } }

    /** PIN length, so the pad can check as soon as enough digits are in (like iOS). */
    val pinLength: Int get() = prefs.getInt(KeyLength, 0)

    var biometrics: Boolean
        get() = prefs.getBoolean(KeyBiometrics, false)
        set(value) = prefs.edit { putBoolean(KeyBiometrics, value) }

    fun set(type: LockType, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KeyType, type.name)
            putString(KeySalt, salt.toHex())
            putString(KeyHash, hash(secret, salt).toHex())
            putInt(KeyLength, if (type == LockType.Pin) secret.length else 0)
        }
    }

    fun verify(secret: String): Boolean {
        val salt = prefs.getString(KeySalt, null)?.fromHex() ?: return false
        val expected = prefs.getString(KeyHash, null)?.fromHex() ?: return false
        // Constant-time compare.
        return MessageDigest.isEqual(hash(secret, salt), expected)
    }

    fun clear() = prefs.edit { clear() }

    // Throttling: a few tries, then a pause.
    fun failedAttempts() = prefs.getInt(KeyFailures, 0)

    fun lockedOutUntil() = prefs.getLong(KeyLockout, 0L)

    fun recordFailure() {
        val failures = failedAttempts() + 1
        prefs.edit {
            putInt(KeyFailures, failures)
            if (failures % MaxAttempts == 0) putLong(KeyLockout, System.currentTimeMillis() + LockoutMs)
        }
    }

    fun recordSuccess() = prefs.edit {
        remove(KeyFailures)
        remove(KeyLockout)
    }

    private fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, Iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        const val MaxAttempts = 5
        const val LockoutMs = 30_000L
        private const val Iterations = 120_000
        private const val KeyType = "type"
        private const val KeySalt = "salt"
        private const val KeyHash = "hash"
        private const val KeyLength = "pin_length"
        private const val KeyBiometrics = "biometrics"
        private const val KeyFailures = "failures"
        private const val KeyLockout = "lockout_until"
    }
}
