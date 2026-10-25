package com.subhashh.lumen.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.ui.theme.Appearance
import com.subhashh.lumen.ui.theme.AppearanceSetting
import com.subhashh.lumen.ui.theme.LocalAppearance
import com.subhashh.lumen.ui.theme.LocalMotion
import com.subhashh.lumen.ui.theme.GlassSetting
import com.subhashh.lumen.ui.theme.LocalGlass
import com.subhashh.lumen.ui.theme.MotionSetting
import android.provider.Settings
import com.subhashh.lumen.ui.theme.LumenTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.subhashh.lumen.data.HiddenLockStore
import com.subhashh.lumen.ui.hidden.HiddenLock
import com.subhashh.lumen.ui.hidden.HiddenLockScreen
import com.subhashh.lumen.ui.hidden.LockFlow

/**
 * Edge-to-edge content in Lumen's theme, honouring Settings → Appearance. Call before
 * super.onCreate's content is set, in place of setContent.
 */
fun ComponentActivity.setLumenContent(content: @Composable () -> Unit) {
    enableEdgeToEdge()
    val prefs = LumenPrefs(this)
    setContent {
        var appearance by remember { mutableStateOf(prefs.appearance) }
        // Android's "Remove animations" (animator duration scale 0) until the user picks in Settings.
        val systemReduce = remember {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        var reduceMotion by remember { mutableStateOf(prefs.reduceMotion ?: systemReduce) }
        var liquidGlass by remember { mutableStateOf(prefs.liquidGlass) }
        var reduceBlur by remember { mutableStateOf(prefs.reduceBlur) }
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
            LocalMotion provides MotionSetting(reduceMotion) {
                reduceMotion = it
                prefs.reduceMotion = it
            },
            LocalGlass provides GlassSetting(
                liquid = liquidGlass,
                reduceBlur = reduceBlur,
                onLiquid = {
                    liquidGlass = it
                    prefs.liquidGlass = it
                },
                onReduceBlur = {
                    reduceBlur = it
                    prefs.reduceBlur = it
                },
            ),
        ) {
            LumenTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    // App Lock covers whatever Lumen screen this is; it takes every touch.
                    if (AppLock.locked) {
                        val activity = this@setLumenContent
                        val store = remember { HiddenLockStore(activity) }
                        HiddenLockScreen(
                            flow = LockFlow.Unlock,
                            store = store,
                            lockedName = "Lumen",
                            onDone = { AppLock.unlock() },
                            onCancel = { activity.finish() },
                            onForgot = {
                                // The phone's own screen lock can vouch for whoever forgot Lumen's.
                                if (HiddenLock.deviceLockSet(activity)) HiddenLock.deviceCredential(activity, "Unlock Lumen") { AppLock.unlock() }
                            },
                        )
                    }
                }
            }
        }
    }
}
