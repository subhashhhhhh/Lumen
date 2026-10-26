package com.subhashh.lumen.data.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The vault's recovery file: the master key, sealed with a key derived from the user's Lumen
 * lock. It sits in the vault folder, so after a reinstall, or on a new install reading the same
 * storage, the same PIN, pattern or password opens the vault again. Day to day the vault opens
 * with the Keystore copy instead (see VaultKeys).
 *
 * Anyone who copies the folder can guess the lock offline, so the derivation is Argon2id: each
 * guess needs 64 MB of memory, which makes a graphics card far slower at it than at PBKDF2.
 * Files from before (format 1, PBKDF2) still open, and are rewritten at the next unlock with the
 * lock ([isCurrent]). Short PINs stay guessable whatever the algorithm: a password is the real fix.
 */
object VaultKeyFile {

    /** RFC 9106's second recommended setting (64 MiB, 3 passes), on one lane. */
    const val ArgonMemoryKiB = 64 * 1024
    const val ArgonPasses = 3
    private const val Argon = "Argon2id"
    private const val Pbkdf2 = "PBKDF2WithHmacSHA256"
    private const val Format = 2

    /** The recovery file's text for [masterKey] under [secret]; tests pass lighter settings. */
    fun write(masterKey: SecretKey, secret: String, memoryKiB: Int = ArgonMemoryKiB, passes: Int = ArgonPasses): String {
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        val kek = argon(secret, salt, memoryKiB, passes)
        return JSONObject()
            .put("format", Format)
            .put("kdf", Argon)
            .put("memoryKiB", memoryKiB)
            .put("passes", passes)
            .put("salt", b64(salt))
            .put("key", b64(VaultCrypto.seal(kek, masterKey.encoded)))
            .toString()
    }

    /** A format 1 file, as Lumen wrote them before 0.1.7; tests use it to check the upgrade path. */
    internal fun writePbkdf2(masterKey: SecretKey, secret: String, iterations: Int): String {
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        return JSONObject()
            .put("format", 1)
            .put("kdf", Pbkdf2)
            .put("iterations", iterations)
            .put("salt", b64(salt))
            .put("key", b64(VaultCrypto.seal(pbkdf2(secret, salt, iterations), masterKey.encoded)))
            .toString()
    }

    /** The master key, if [secret] is the right lock; null if it isn't (or the file is damaged). */
    fun read(text: String, secret: String): SecretKey? = runCatching {
        val json = JSONObject(text)
        val salt = unb64(json.getString("salt"))
        val kek = when (json.optString("kdf", Pbkdf2)) {
            Argon -> argon(secret, salt, json.getInt("memoryKiB"), json.getInt("passes"))
            else -> pbkdf2(secret, salt, json.getInt("iterations"))
        }
        SecretKeySpec(VaultCrypto.open(kek, unb64(json.getString("key"))), "AES")
    }.getOrNull()

    /** Whether [text] already uses Argon2id; an older file is rewritten at the next unlock. */
    fun isCurrent(text: String): Boolean = runCatching { JSONObject(text).optString("kdf") == Argon }.getOrDefault(false)

    private fun argon(secret: String, salt: ByteArray, memoryKiB: Int, passes: Int): SecretKey {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKiB)
            .withIterations(passes)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        val out = ByteArray(32)
        val password = secret.toByteArray(Charsets.UTF_8)
        try {
            Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
            return SecretKeySpec(out, "AES")
        } finally {
            password.fill(0)
        }
    }

    private fun pbkdf2(secret: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance(Pbkdf2).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String) = Base64.getDecoder().decode(text)
}
