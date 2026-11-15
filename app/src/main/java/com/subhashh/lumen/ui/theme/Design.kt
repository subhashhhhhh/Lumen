package com.subhashh.lumen.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R

/**
 * Settings → Appearance → Style: the design language the whole app is drawn in. [Lumen] is the
 * iOS-like look (with or without Liquid Glass); [Material] is Material 3 Expressive: the phone's
 * wallpaper colours, tonal surfaces, ripples, rounder shapes and springier motion.
 */
enum class DesignStyle(@StringRes val labelRes: Int) {
    Lumen(R.string.app_style_lumen),
    Material(R.string.app_style_material),
}

/**
 * How Material builds its colours out of the one it starts from: how far the other colours stray
 * from it, and how loud they are. Settings → Appearance → Palette.
 */
enum class MaterialPalette(@StringRes val labelRes: Int) {
    Tonal(R.string.app_palette_tonal),
    Vibrant(R.string.app_palette_vibrant),
    Expressive(R.string.app_palette_expressive),
    FruitSalad(R.string.app_palette_fruit_salad),
    Rainbow(R.string.app_palette_rainbow),
    Monochrome(R.string.app_palette_monochrome),
}

/**
 * The chosen [DesignStyle] and Material's own options: [accent] is the colour its scheme grows
 * from (null: the wallpaper's, or Lumen's blue before Android 12), [palette] its character, and
 * [pureBlack] turns the dark theme's backgrounds black for OLED screens.
 */
@Immutable
class StyleSetting(
    val current: DesignStyle,
    val accent: Color? = null,
    val palette: MaterialPalette = MaterialPalette.Tonal,
    val pureBlack: Boolean = false,
    val onChange: (DesignStyle) -> Unit = {},
    val onAccent: (Color?) -> Unit = {},
    val onPalette: (MaterialPalette) -> Unit = {},
    val onPureBlack: (Boolean) -> Unit = {},
) {
    val material: Boolean get() = current == DesignStyle.Material
}

val LocalStyle = staticCompositionLocalOf { StyleSetting(DesignStyle.Lumen) }

/** The colours offered for Material's accent, besides the wallpaper's and one mixed by hand. */
val AccentPresets = listOf(
    Color(0xFF0A84FF), Color(0xFF7C5CFF), Color(0xFFFF4F9A), Color(0xFFE5484D),
    Color(0xFFFF8A00), Color(0xFFF2C200), Color(0xFF2FB344), Color(0xFF00B3A6),
)

/**
 * The colours screens share, named for what they're for. Each style fills them in: iOS system
 * colours for [DesignStyle.Lumen], the colour scheme's roles for [DesignStyle.Material]. Screens
 * read these instead of naming a colour, so they follow the style.
 */
@Immutable
data class LumenColors(
    /** Behind grouped lists (Settings, pickers). */
    val groupedPage: Color,
    /** A grouped list's card, one step up from [groupedPage]. */
    val card: Color,
    /** A grouped list drawn straight on a plain page (Collections' utilities), not on [groupedPage]. */
    val groupOnPage: Color,
    /** A row inside a sheet or dialog that already sits on [card]. */
    val cardRaised: Color,
    /** Alerts and dialogs. */
    val dialog: Color,
    /** Larger dialogs and sheets that hold rows and pickers. */
    val panel: Color,
    /** A bottom sheet (the album picker, a photo's details). */
    val sheet: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    /** Text fields and other wells sunk into a card. */
    val field: Color,
    /** Links, the confirming button, the cursor. */
    val accent: Color,
    val onAccent: Color,
    /** Delete and other actions that can't be undone. */
    val destructive: Color,
    val favorite: Color,
    /** A switch that's on, a finished step. */
    val positive: Color,
    /** Behind a dialog, dimming the page. */
    val scrim: Color,
)

/** Corners, per kind of surface. */
@Immutable
data class LumenShapes(
    val card: Shape,
    val dialog: Shape,
    val menu: Shape,
    val field: Shape,
    /** Album covers and other photo tiles. */
    val cover: Shape,
    /** The small coloured square (or circle) behind a row's icon. */
    val badge: Shape,
    /** Wide buttons (onboarding, sheets). */
    val button: Shape,
)

@Immutable
data class LumenType(
    /** A page's name at its top: "Library", "Settings". */
    val screenTitle: TextStyle,
    /** A section's name inside a page: "Albums", "People". */
    val sectionTitle: TextStyle,
    val dialogTitle: TextStyle,
    /** The weight for names and labels that iOS sets in semibold. */
    val emphasis: FontWeight,
    /** The weight for headings that iOS sets in bold. */
    val strong: FontWeight,
)

val LocalLumenColors = staticCompositionLocalOf { lumenColors(dark = true) }
val LocalLumenShapes = staticCompositionLocalOf { LumenStyleShapes }
val LocalLumenType = staticCompositionLocalOf { LumenStyleType }

/** Shorthand for the style's tokens: `Lumen.colors.accent`, `Lumen.shapes.card`, `Lumen.material`. */
object Lumen {
    val colors: LumenColors
        @Composable @ReadOnlyComposable get() = LocalLumenColors.current
    val shapes: LumenShapes
        @Composable @ReadOnlyComposable get() = LocalLumenShapes.current
    val type: LumenType
        @Composable @ReadOnlyComposable get() = LocalLumenType.current

    /** Drawing in Material 3 Expressive. */
    val material: Boolean
        @Composable @ReadOnlyComposable get() = LocalStyle.current.material
}

internal fun lumenColors(dark: Boolean) = if (dark) {
    LumenColors(
        groupedPage = Color.Black,
        card = Color(0xFF1C1C1E),
        groupOnPage = Color(0xFF1C1C1E),
        cardRaised = Color(0xFF2C2C2E),
        dialog = Color(0xFF2C2C2E),
        panel = Color(0xFF1C1C1E),
        sheet = Color(0xFF1C1C1E),
        text = Color.White,
        secondary = Color(0x99EBEBF5),
        divider = Color(0x33EBEBF5),
        field = Color(0xFFEBEBF5).copy(alpha = 0.12f),
        accent = Color(0xFF0A84FF),
        onAccent = Color.White,
        destructive = Color(0xFFFF453A),
        favorite = Color(0xFFFF375F),
        positive = Color(0xFF30D158),
        scrim = Color(0x66000000),
    )
} else {
    LumenColors(
        groupedPage = Color(0xFFF2F2F7),
        card = Color.White,
        groupOnPage = Color(0xFFF2F2F7),
        cardRaised = Color.White,
        dialog = Color.White,
        panel = Color.White,
        sheet = Color(0xFFF2F2F7),
        text = Color.Black,
        secondary = Color(0x993C3C43),
        divider = Color(0x333C3C43),
        field = Color(0xFF3C3C43).copy(alpha = 0.12f),
        accent = Color(0xFF0A84FF),
        onAccent = Color.White,
        destructive = Color(0xFFFF453A),
        favorite = Color(0xFFFF375F),
        positive = Color(0xFF30D158),
        scrim = Color(0x66000000),
    )
}

/** Material's roles for the same jobs: tonal surfaces instead of greys, primary instead of blue. */
internal fun materialColors(scheme: ColorScheme): LumenColors = materialColors(scheme, black = scheme.background == Color.Black)

/** [black]: Pure Black. Pages that are normally a tonal grey go black too, their cards one step up. */
private fun materialColors(scheme: ColorScheme, black: Boolean) = LumenColors(
    groupedPage = if (black) Color.Black else scheme.surfaceContainer,
    card = if (black) scheme.surfaceContainer else scheme.surfaceBright,
    groupOnPage = scheme.surfaceContainer,
    cardRaised = scheme.surfaceContainerHighest,
    dialog = scheme.surfaceContainerHigh,
    panel = scheme.surfaceContainerHigh,
    sheet = scheme.surfaceContainerLow,
    text = scheme.onSurface,
    secondary = scheme.onSurfaceVariant,
    divider = scheme.outlineVariant,
    field = scheme.surfaceContainerHighest,
    accent = scheme.primary,
    onAccent = scheme.onPrimary,
    destructive = scheme.error,
    favorite = Color(0xFFFF375F),
    positive = scheme.primary,
    scrim = scheme.scrim.copy(alpha = 0.5f),
)

internal val LumenStyleShapes = LumenShapes(
    card = RoundedCornerShape(16.dp),
    dialog = RoundedCornerShape(22.dp),
    menu = RoundedCornerShape(22.dp),
    field = RoundedCornerShape(10.dp),
    cover = RoundedCornerShape(12.dp),
    badge = RoundedCornerShape(8.dp),
    button = RoundedCornerShape(16.dp),
)

internal val MaterialStyleShapes = LumenShapes(
    card = RoundedCornerShape(24.dp),
    dialog = RoundedCornerShape(28.dp),
    menu = RoundedCornerShape(20.dp),
    field = RoundedCornerShape(16.dp),
    cover = RoundedCornerShape(20.dp),
    badge = CircleShape,
    button = CircleShape,
)

internal val LumenStyleType = LumenType(
    screenTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    sectionTitle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    dialogTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    emphasis = FontWeight.SemiBold,
    strong = FontWeight.Bold,
)

/** Expressive type: large, in medium weight, tighter than the body. */
internal val MaterialStyleType = LumenType(
    screenTitle = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp),
    sectionTitle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    dialogTitle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    emphasis = FontWeight.Medium,
    strong = FontWeight.Medium,
)
