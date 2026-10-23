package com.subhashh.lumen.data.vault

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The vault's recovery file: the master key, sealed with a key derived from the user's Lumen
 * lock (PBKDF2-HMAC-SHA256). It sits in the vault folder, so after a reinstall, or on a new
 * install reading the same storage, the same PIN, pattern or password opens the vault again.
 * Day to day the vault opens with the Keystore copy instead (see VaultKeys).
 */
object VaultKeyFile {

    /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256: about a second on a phone, once per recovery. */
    const val Iterations = 600_000
    private const val Format = 1

    /** The recovery file's text for [masterKey] under [secret]. */
    fun write(masterKey: SecretKey, secret: String, iterations: Int = Iterations): String {
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        val kek = derive(secret, salt, iterations)
        return JSONObject()
            .put("format", Format)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", iterations)
            .put("salt", b64(salt))
            .put("key", b64(VaultCrypto.seal(kek, masterKey.encoded)))
            .toString()
    }

    /** The master key, if [secret] is the right lock; null if it isn't (or the file is damaged). */
    fun read(text: String, secret: String): SecretKey? = runCatching {
        val json = JSONObject(text)
        val kek = derive(secret, unb64(json.getString("salt")), json.getInt("iterations"))
        SecretKeySpec(VaultCrypto.open(kek, unb64(json.getString("key"))), "AES")
    }.getOrNull()

    private fun derive(secret: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String) = Base64.getDecoder().decode(text)
}
