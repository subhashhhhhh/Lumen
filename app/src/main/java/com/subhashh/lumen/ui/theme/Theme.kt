package com.subhashh.lumen.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.subhashh.lumen.R

/** Tints for glass surfaces. Below Android 12 there is no blur, so the fallback tint is much more opaque. */
@Immutable
data class GlassColors(
    val tint: Color,
    val fallbackTint: Color,
    val content: Color,
    val contentSecondary: Color,
    val selection: Color,
)

private val DarkGlass = GlassColors(
    tint = Color(0x33FFFFFF),
    fallbackTint = Color(0xE6262628),
    content = Color.White,
    contentSecondary = Color(0x99FFFFFF),
    selection = Color(0x26FFFFFF),
)

private val LightGlass = GlassColors(
    tint = Color(0x80FFFFFF),
    fallbackTint = Color(0xF2F6F6F8),
    content = Color.Black,
    contentSecondary = Color(0x99000000),
    selection = Color(0x14000000),
)

val LocalGlassColors = staticCompositionLocalOf { DarkGlass }

/** Whether Lumen is drawing dark, which can differ from the phone (Settings → Appearance). */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** Settings → Appearance: follow the phone, or always light or dark. */
enum class Appearance(@StringRes val labelRes: Int) {
    System(R.string.app_appearance_system),
    Light(R.string.app_appearance_light),
    Dark(R.string.app_appearance_dark),
}

/** The chosen [Appearance] and how to change it, for the Settings screen. */
@Immutable
class AppearanceSetting(val current: Appearance, val onChange: (Appearance) -> Unit)

val LocalAppearance = staticCompositionLocalOf { AppearanceSetting(Appearance.System) {} }

/**
 * Settings → Appearance → Reduce Motion: no photo flights, springs or bounces; gestures that
 * follow a finger stay. Defaults to Android's "Remove animations" until the user picks.
 */
@Immutable
class MotionSetting(val reduce: Boolean, val onChange: (Boolean) -> Unit)

val LocalMotion = staticCompositionLocalOf { MotionSetting(false) {} }

/**
 * Settings → Appearance → Liquid Glass and Reduce Blur. [liquid] off means no glass anywhere:
 * solid surfaces, plain switches, a flat tab pill. [reduceBlur] only matters with it on: the same
 * glass, bending included, with much less blur behind it (clearer, lighter on the GPU).
 */
@Immutable
class GlassSetting(
    val liquid: Boolean,
    val reduceBlur: Boolean,
    val onLiquid: (Boolean) -> Unit,
    val onReduceBlur: (Boolean) -> Unit,
) {
    /** Glass is drawn at all (lens, blur, the lifting pill, the glass switch knob). */
    val lens: Boolean get() = liquid

    /** How much of each surface's blur to keep. */
    val blurScale: Float get() = if (reduceBlur) 0.3f else 1f
}

val LocalGlass = staticCompositionLocalOf { GlassSetting(liquid = false, reduceBlur = false, onLiquid = {}, onReduceBlur = {}) }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun LumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassColors provides if (darkTheme) DarkGlass else LightGlass,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content,
        )
    }
}
