package com.subhashh.lumen.ui.hidden

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Marks the window secure while [on]: Android then blocks screenshots and screen recordings of it,
 * and shows a blank card in recent apps (Settings → Hidden → Allow Screenshots).
 */
@Composable
fun SecureWhile(on: Boolean) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(on, window) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (on) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
