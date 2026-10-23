package com.subhashh.lumen.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's optional Hugging Face token (faster, less rate-limited downloads). Stored encrypted
 * with a Keystore key that never leaves the phone's secure hardware, and never backed up.
 */
class HfTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("lumen_secrets", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KeyToken, null)?.let { stored ->
            runCatching {
                val bytes = Base64.decode(stored, Base64.NO_WRAP)
                val cipher = Cipher.getInstance(Transformation)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IvSize))
                String(cipher.doFinal(bytes, IvSize, bytes.size - IvSize), Charsets.UTF_8)
            }.getOrNull()
        }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) {
                remove(KeyToken)
            } else {
                val cipher = Cipher.getInstance(Transformation)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val sealed = cipher.iv + cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
                putString(KeyToken, Base64.encodeToString(sealed, Base64.NO_WRAP))
            }
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(Alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val Alias = "lumen_hf_token"
        const val KeyToken = "hf_token"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
    }
}
