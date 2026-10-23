package com.subhashh.lumen.ui.hidden

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * Gate for the Hidden section: fingerprint or face, with the screen-lock PIN/pattern as fallback.
 * Uses the platform prompt (no androidx.biometric / FragmentActivity needed on API 30+).
 */
object HiddenLock {

    private const val Authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** False when the phone has no screen lock, so Hidden can't actually be protected. */
    fun canLock(activity: Activity): Boolean =
        activity.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(Authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun unlock(activity: Activity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Hidden")
            .setSubtitle("Hidden photos stay locked when you leave them")
            .setAllowedAuthenticators(Authenticators)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            },
        )
    }
}
