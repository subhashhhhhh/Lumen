package com.subhashh.lumen.ui.hidden

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * The system prompts around Hidden's own lock (platform BiometricPrompt; no androidx.biometric /
 * FragmentActivity needed on API 30+):
 * - fingerprint/face as a shortcut in front of Lumen's PIN, pattern or password
 * - the phone's screen lock, only to authorize resetting a forgotten Lumen lock
 */
object HiddenLock {

    private fun manager(activity: Activity) = activity.getSystemService(BiometricManager::class.java)

    /** Fingerprint or face is enrolled and strong enough to unlock Hidden. */
    fun biometricsAvailable(activity: Activity): Boolean =
        manager(activity)?.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** The phone has a screen lock that can vouch for a reset. */
    fun deviceLockSet(activity: Activity): Boolean =
        manager(activity)?.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /** Fingerprint/face only; [fallback] names the Lumen lock to fall back to ("Use PIN"). */
    fun biometric(activity: Activity, fallback: String, onSuccess: () -> Unit, onFallback: () -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Hidden")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButton(fallback, activity.mainExecutor) { _, _ -> onFallback() }
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback(onSuccess))
    }

    /** The phone's own screen lock (PIN/pattern/password or biometrics), to authorize a reset. */
    fun deviceCredential(activity: Activity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Reset Hidden lock")
            .setSubtitle("Confirm it's you with your phone's screen lock")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback(onSuccess))
    }

    private fun callback(onSuccess: () -> Unit) = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    }
}
