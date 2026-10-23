package com.subhashh.lumen

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.ui.LumenApp
import com.subhashh.lumen.ui.theme.Appearance
import com.subhashh.lumen.ui.theme.AppearanceSetting
import com.subhashh.lumen.ui.theme.LocalAppearance
import com.subhashh.lumen.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = LumenPrefs(this)
        setContent {
            var appearance by remember { mutableStateOf(prefs.appearance) }
            val dark = when (appearance) {
                Appearance.System -> isSystemInDarkTheme()
                Appearance.Light -> false
                Appearance.Dark -> true
            }
            // Status and navigation bar icons follow Lumen's theme, not the phone's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CompositionLocalProvider(
                LocalAppearance provides AppearanceSetting(appearance) {
                    appearance = it
                    prefs.appearance = it
                },
            ) {
                LumenTheme(darkTheme = dark) {
                    LumenApp()
                }
            }
        }
    }
}
