package com.subhashh.lumen.data.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The vault's master key, kept two ways:
 *  - sealed by a key in Android's hardware Keystore, in Lumen's no-backup storage: opens the
 *    vault day to day without asking for the lock again (Hidden's own lock already did);
 *  - in the recovery file next to the vault ([VaultKeyFile]), sealed by the user's Lumen lock:
 *    opens it after a reinstall, when the Keystore copy is gone with the old install.
 */
class VaultKeys(
    private val context: Context,
    private val vaultDir: File,
    prefsName: String = "vault_keys",
    /** Keystore alias; tests pass their own so they never touch the real vault's key. */
    private val alias: String = "lumen_vault",
) {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val recoveryFile get() = File(vaultDir, RecoveryName)

    /** A vault already exists here (from this install or an earlier one). */
    val exists: Boolean get() = recoveryFile.exists()

    /** Makes a new vault's master key, saved both ways. */
    fun create(secret: String): SecretKey {
        val masterKey = VaultCrypto.newKey()
        vaultDir.mkdirs()
        writeAtomically(recoveryFile, VaultKeyFile.write(masterKey, secret))
        rememberOnDevice(masterKey)
        return masterKey
    }

    /** The master key from the Keystore copy; null after a reinstall (then use [unlockWithSecret]). */
    fun unlockOnDevice(): SecretKey? = runCatching {
        val sealed = prefs.getString(KeyDeviceCopy, null)?.let { Base64.getDecoder().decode(it) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deviceKey(create = false) ?: return null, GCMParameterSpec(128, sealed, 0, 12))
        }
        SecretKeySpec(cipher.doFinal(sealed, 12, sealed.size - 12), "AES")
    }.getOrNull()

    /** The master key from the recovery file and the user's lock; also restores the Keystore copy. */
    fun unlockWithSecret(secret: String): SecretKey? {
        val text = runCatching { recoveryFile.readText() }.getOrNull() ?: return null
        val masterKey = VaultKeyFile.read(text, secret) ?: return null
        rememberOnDevice(masterKey)
        return masterKey
    }

    /** The lock changed: reseal the recovery copy with the new one. */
    fun changeSecret(masterKey: SecretKey, newSecret: String) {
        writeAtomically(recoveryFile, VaultKeyFile.write(masterKey, newSecret))
    }

    private fun rememberOnDevice(masterKey: SecretKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deviceKey(create = true))
        }
        val sealed = cipher.iv + cipher.doFinal(masterKey.encoded)
        prefs.edit(commit = true) { putString(KeyDeviceCopy, Base64.getEncoder().encodeToString(sealed)) }
    }

    /** Lumen's Keystore key: never leaves the phone's secure hardware, gone if Lumen is uninstalled. */
    private fun deviceKey(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    companion object {
        const val RecoveryName = "vault.key"
        private const val KeyDeviceCopy = "device_copy"
    }
}
