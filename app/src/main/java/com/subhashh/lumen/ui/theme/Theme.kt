package com.subhashh.lumen.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
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

/**
 * Glass over photos and videos (the viewer's controls). Dark mode's glass is a light veil with
 * white icons, which vanishes over a white photo or screenshot (#6): over media it's smoked
 * instead, so the white icons read on white and on black. Light mode's carries black icons already.
 */
@Composable
fun overMediaGlass(): GlassColors {
    val colors = LocalGlassColors.current
    return if (LocalDarkTheme.current && !LocalStyle.current.material) colors.copy(tint = Color(0x8C1C1C1E)) else colors
}

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

/**
 * Material's colours when they can't come from the wallpaper (Android 11, or Dynamic Colour off):
 * a scheme built around Lumen's blue.
 */
private val MaterialDarkScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0), onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C), onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2E3036), inversePrimary = Color(0xFF415F91),
    surfaceDim = Color(0xFF111318), surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

private val MaterialLightScheme = lightColorScheme(
    primary = Color(0xFF415F91), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF284777),
    secondary = Color(0xFF565F71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFF705575), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAD8FD), onTertiaryContainer = Color(0xFF573E5C),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2E3036), inverseOnSurface = Color(0xFFF0F0F7), inversePrimary = Color(0xFFAAC7FF),
    surfaceDim = Color(0xFFD9D9E0), surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4), surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

/** Material's floating controls are solid and tonal: the "glass" colours are just that surface. */
private fun materialGlass(scheme: ColorScheme) = GlassColors(
    tint = scheme.surfaceContainerHigh,
    fallbackTint = scheme.surfaceContainerHigh,
    content = scheme.onSurface,
    contentSecondary = scheme.onSurfaceVariant,
    selection = scheme.secondaryContainer,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val style = LocalStyle.current
    if (style.material) {
        val context = LocalContext.current
        val scheme = when {
            // The wallpaper's colours (Material You), from Android 12.
            style.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> MaterialDarkScheme
            else -> MaterialLightScheme
        }
        CompositionLocalProvider(
            LocalGlassColors provides materialGlass(scheme),
            LocalDarkTheme provides darkTheme,
            LocalLumenColors provides materialColors(scheme),
            LocalLumenShapes provides MaterialStyleShapes,
        ) {
            MaterialExpressiveTheme(
                colorScheme = scheme,
                motionScheme = MotionScheme.expressive(),
            ) { WithType(MaterialStyleType, content) }
        }
        return
    }
    CompositionLocalProvider(
        LocalGlassColors provides if (darkTheme) DarkGlass else LightGlass,
        LocalDarkTheme provides darkTheme,
        LocalLumenColors provides lumenColors(darkTheme),
        LocalLumenShapes provides LumenStyleShapes,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme) { WithType(LumenStyleType, content) }
    }
}

/**
 * Provides [type] laid over the theme's body text style, which is what a plain Text starts from:
 * a token that only names a size and weight keeps the body's line height and spacing.
 */
@Composable
private fun WithType(type: LumenType, content: @Composable () -> Unit) {
    val body = LocalTextStyle.current
    val merged = remember(type, body) {
        type.copy(
            screenTitle = body.merge(type.screenTitle),
            sectionTitle = body.merge(type.sectionTitle),
            dialogTitle = body.merge(type.dialogTitle),
        )
    }
    CompositionLocalProvider(LocalLumenType provides merged, content = content)
}
