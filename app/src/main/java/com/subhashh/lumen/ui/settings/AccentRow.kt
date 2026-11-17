package com.subhashh.lumen.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.AccentPresets
import com.subhashh.lumen.ui.theme.LocalMotion

/**
 * Material's accent colour: the wallpaper's (or Lumen's blue before Android 12), a few ready-made
 * ones, or one mixed with the hue slider that Custom opens. [accent] null is the first of those.
 * The chosen swatch grows and takes a tick.
 */
@Composable
internal fun AccentRow(accent: Color?, onAccent: (Color?) -> Unit) {
    val colors = groupColors()
    val context = LocalContext.current
    val wallpaper = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val automatic = if (wallpaper) Color(context.getColor(android.R.color.system_accent1_500)) else AccentPresets.first()
    val preset = accent != null && AccentPresets.any { it.toArgb() == accent.toArgb() }
    // Open while a hand-mixed colour is in use, or once Custom is tapped.
    var mixing by rememberSaveable { mutableStateOf(accent != null && !preset) }
    var hue by remember { mutableFloatStateOf(accent?.let(::hueOf) ?: 210f) }
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            stringResource(R.string.settings_accent),
            fontSize = 16.sp,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Swatch(
                automatic,
                selected = accent == null,
                label = stringResource(if (wallpaper) R.string.settings_accent_wallpaper else R.string.settings_accent_default),
                icon = if (wallpaper) LumenIcons.Image else null,
            ) {
                mixing = false
                onAccent(null)
            }
            // Before Android 12 the first swatch already is Lumen's blue.
            for (color in if (wallpaper) AccentPresets else AccentPresets.drop(1)) {
                Swatch(color, selected = accent?.toArgb() == color.toArgb(), label = null, icon = null, describe = stringResource(R.string.settings_accent_preset, AccentPresets.indexOf(color) + 1)) {
                    mixing = false
                    onAccent(color)
                }
            }
            Swatch(
                Color.hsv(hue, 0.72f, 1f),
                selected = accent != null && !preset,
                label = stringResource(R.string.settings_accent_custom),
                icon = LumenIcons.Palette,
            ) {
                mixing = true
                onAccent(Color.hsv(hue, 0.72f, 1f))
            }
        }
        AnimatedVisibility(mixing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            // The swatch follows the finger; the colour is saved, and the app re-themed, on letting go.
            HueSlider(hue, onHue = { hue = it }, onDone = { onAccent(Color.hsv(hue, 0.72f, 1f)) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Swatch(color: Color, selected: Boolean, label: String?, icon: ImageVector?, describe: String? = label, onClick: () -> Unit) {
    val reduce = LocalMotion.current.reduce
    // Grows a little as it's chosen.
    val chosen by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) spring(stiffness = 10_000f) else spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "swatchChosen",
    )
    val shape = CircleShape
    val on = if (color.luminance() > 0.45f) Color.Black else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer {
                    val scale = 0.86f + 0.14f * chosen
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(color)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
                .semantics {
                    this.selected = selected
                    if (describe != null) contentDescription = describe
                },
            contentAlignment = Alignment.Center,
        ) {
            val mark = if (selected) LumenIcons.Check else icon
            if (mark != null) Icon(mark, null, tint = on, modifier = Modifier.size(22.dp))
        }
        if (label != null) {
            Text(label, fontSize = 11.sp, color = groupColors().secondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Every hue along a rounded bar, with a tall handle to drag. */
@Composable
private fun HueSlider(hue: Float, onHue: (Float) -> Unit, onDone: () -> Unit) {
    val current by rememberUpdatedState(onHue)
    val done by rememberUpdatedState(onDone)
    val handle = MaterialTheme.colorScheme.onSurface
    val gap = groupColors().card
    val label = stringResource(R.string.settings_accent_hue)
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(44.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                fun set(x: Float) = current((x / size.width).coerceIn(0f, 1f) * 359f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    set(down.position.x)
                    drag(down.id) { change ->
                        set(change.position.x)
                        change.consume()
                    }
                    done()
                }
            },
    ) {
        val track = 20.dp.toPx()
        val top = (size.height - track) / 2
        drawRoundRect(
            Brush.horizontalGradient(List(13) { Color.hsv(it * 30f % 360f, 0.72f, 1f) }),
            topLeft = Offset(0f, top),
            size = Size(size.width, track),
            cornerRadius = CornerRadius(track / 2),
        )
        val x = hue / 359f * size.width
        val bar = 6.dp.toPx()
        // A clear margin around the handle, then the handle itself.
        drawRoundRect(gap, Offset(x - bar, 0f), Size(bar * 2, size.height), CornerRadius(bar))
        drawRoundRect(handle, Offset(x - bar / 2, 2.dp.toPx()), Size(bar, size.height - 4.dp.toPx()), CornerRadius(bar / 2))
    }
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}
