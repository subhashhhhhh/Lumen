package com.subhashh.lumen.data.backup

import android.util.Base64
import com.subhashh.lumen.data.vault.VaultCrypto
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * An encrypted backup's keys. One random **backup key** encrypts everything; it's stored in the
 * backup folder twice, locked by the person's **password** (Argon2id, as the vault's recovery
 * file) and by a **recovery key** shown once at setup. Either opens the backup; changing the
 * password or making a new recovery key relocks only that copy, nothing is encrypted again.
 * Lumen keeps neither the password nor the recovery key: lose both and the backup can't be opened.
 */
object BackupCrypto {
    const val ArgonMemoryKiB = 64 * 1024
    const val ArgonPasses = 3

    /** What a backup key opens: content keys for chunks and their names, derived once. */
    class Keys(backupKey: ByteArray) {
        val data: SecretKey = SecretKeySpec(hkdf(backupKey, "lumen-backup data"), "AES")
        private val names: ByteArray = hkdf(backupKey, "lumen-backup names")

        /** A chunk's name: a keyed hash, so the folder reveals neither content nor which photos are in it. */
        fun name(plain: ByteArray): String = hex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(names, "HmacSHA256")) }.doFinal(plain))
    }

    fun newBackupKey(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    /** An unencrypted backup names chunks by their SHA-256. */
    fun plainName(plain: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(plain))

    // ---- The password lock ----

    class PasswordLock(val salt: ByteArray, val memoryKiB: Int, val passes: Int, val sealed: ByteArray)

    fun lockWithPassword(backupKey: ByteArray, password: String, memoryKiB: Int = ArgonMemoryKiB, passes: Int = ArgonPasses): PasswordLock {
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        return PasswordLock(salt, memoryKiB, passes, VaultCrypto.seal(argon(password, salt, memoryKiB, passes), backupKey))
    }

    /** The backup key, or null if [password] is wrong. */
    fun unlockWithPassword(lock: PasswordLock, password: String): ByteArray? =
        runCatching { VaultCrypto.open(argon(password, lock.salt, lock.memoryKiB, lock.passes), lock.sealed) }.getOrNull()

    // ---- The recovery key ----

    /** 20 random bytes, written as 8 groups of 4 letters and digits (no 0/O or 1/I to confuse). */
    fun newRecoveryKey(): String {
        val bytes = ByteArray(20).also(SecureRandom()::nextBytes)
        return base32(bytes).chunked(4).joinToString("-")
    }

    fun lockWithRecoveryKey(backupKey: ByteArray, recoveryKey: String): ByteArray =
        VaultCrypto.seal(recoveryKek(recoveryKey) ?: error("Bad recovery key"), backupKey)

    /** The backup key, or null if [recoveryKey] is wrong or mistyped. */
    fun unlockWithRecoveryKey(sealed: ByteArray, recoveryKey: String): ByteArray? =
        runCatching { VaultCrypto.open(recoveryKek(recoveryKey) ?: return null, sealed) }.getOrNull()

    /** The recovery key is random enough (160 bits) that a plain key derivation is enough; no Argon2. */
    private fun recoveryKek(recoveryKey: String): SecretKey? {
        val bytes = unbase32(recoveryKey.uppercase().filter { it.isLetterOrDigit() }) ?: return null
        if (bytes.size != 20) return null
        return SecretKeySpec(hkdf(bytes, "lumen-backup recovery"), "AES")
    }

    // ---- Helpers ----

    private fun argon(password: String, salt: ByteArray, memoryKiB: Int, passes: Int): SecretKey {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKiB)
            .withIterations(passes)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        val out = ByteArray(32)
        val secret = password.toByteArray(Charsets.UTF_8)
        try {
            Argon2BytesGenerator().apply { init(params) }.generateBytes(secret, out)
            return SecretKeySpec(out, "AES")
        } finally {
            secret.fill(0)
        }
    }

    /** HKDF-SHA256 (RFC 5869) with an empty salt, one 32-byte block. */
    internal fun hkdf(input: ByteArray, info: String): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32), "HmacSHA256")) }.doFinal(input)
        return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
            .doFinal(info.toByteArray() + byteArrayOf(1))
    }

    private const val Alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 32 symbols, no I, O, 0, 1

    internal fun base32(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(Alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(Alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    internal fun unbase32(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = Alphabet.indexOf(c).takeIf { it >= 0 } ?: return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
