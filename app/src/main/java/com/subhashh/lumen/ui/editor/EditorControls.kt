package com.subhashh.lumen.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.MaterialTheme
import com.subhashh.lumen.ui.components.Spinner
import com.subhashh.lumen.ui.theme.Lumen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.components.pressable
import kotlin.math.abs

/** The editors' accent: the same blue as the rest of Lumen, in its dark-theme shade (they're always dark). */
internal val EditorAccent: Color
    @Composable @ReadOnlyComposable get() = if (Lumen.material) MaterialTheme.colorScheme.primary else Color(0xFF0A84FF)

/** What the editors' round buttons and chips sit on. */
internal val EditorPanel: Color
    @Composable @ReadOnlyComposable get() = if (Lumen.material) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xE61C1C1E)

/** A chosen chip's or lit button's fill, and what's drawn on it: white and black, or Material's primary pair. */
private val EditorChosen: Color
    @Composable @ReadOnlyComposable get() = if (Lumen.material) MaterialTheme.colorScheme.primary else Color.White
private val EditorOnChosen: Color
    @Composable @ReadOnlyComposable get() = if (Lumen.material) MaterialTheme.colorScheme.onPrimary else Color.Black

/** Cancel on the left, Save on the right, and Reset between them once something has changed. */
@Composable
internal fun EditorTopBar(
    changed: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp)) {
        EditorTextButton(stringResource(R.string.common_cancel), Color.White, onCancel, Modifier.align(Alignment.CenterStart))
        if (changed) {
            EditorTextButton(stringResource(R.string.editor_reset), Color.White.copy(alpha = 0.7f), onReset, Modifier.align(Alignment.Center))
        }
        EditorTextButton(
            stringResource(R.string.common_save),
            if (changed) EditorAccent else Color.White.copy(alpha = 0.3f),
            onSave,
            Modifier.align(Alignment.CenterEnd),
            enabled = changed,
            bold = true,
        )
    }
}

@Composable
internal fun EditorTextButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
) {
    Text(
        label,
        color = color,
        fontSize = 17.sp,
        fontWeight = if (bold) Lumen.type.emphasis else FontWeight.Normal,
        modifier = modifier.pressable(onClick, enabled = enabled, pressedScale = 0.94f).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** A round icon button on the dark panel colour; [active] lights it up. */
@Composable
internal fun EditorIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Box(
        modifier
            .size(if (Lumen.material) 48.dp else 44.dp)
            .pressable(onClick, shape = CircleShape)
            .clip(CircleShape)
            .background(if (active) EditorChosen else EditorPanel),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (active) EditorOnChosen else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A word in a pill: a crop shape, an adjustment. The chosen one is white. */
@Composable
internal fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier
            .pressable(onClick, pressedScale = 0.94f, shape = RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (selected) EditorChosen else EditorPanel)
            .padding(horizontal = if (Lumen.material) 16.dp else 14.dp, vertical = if (Lumen.material) 10.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) EditorOnChosen else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (detail != null) {
            Text(detail, color = if (selected) EditorOnChosen.copy(alpha = 0.55f) else EditorAccent, fontSize = 14.sp)
        }
    }
}

/** The tabs along the bottom: an icon over a word, the chosen one in the accent colour. */
@Composable
internal fun <T> EditorTabs(
    tabs: List<T>,
    selected: T,
    icon: (T) -> ImageVector,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        val material = Lumen.material
        for (tab in tabs) {
            val chosen = tab == selected
            if (material) {
                // A navigation bar item: the chosen tab's icon sits on a tonal pill.
                val scheme = MaterialTheme.colorScheme
                val pill by animateColorAsState(if (chosen) scheme.secondaryContainer else scheme.secondaryContainer.copy(alpha = 0f), label = "editorTabPill")
                Column(
                    Modifier.pressable({ onSelect(tab) }).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(width = 64.dp, height = 32.dp).clip(CircleShape).background(pill), contentAlignment = Alignment.Center) {
                        Icon(icon(tab), null, tint = if (chosen) scheme.onSecondaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    Text(label(tab), color = if (chosen) scheme.onSurface else scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                continue
            }
            val tint = if (chosen) EditorAccent else Color.White.copy(alpha = 0.7f)
            Column(
                Modifier.pressable({ onSelect(tab) }, pressedScale = 0.92f).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(icon(tab), null, tint = tint, modifier = Modifier.size(24.dp))
                Text(label(tab), color = tint, fontSize = 11.sp)
            }
        }
    }
}

/**
 * A slider from [min] to [max] with its resting point at [zero]: the track lights up between the
 * resting point and the thumb, so it reads as "this much more" or "this much less". Near the
 * resting point the thumb snaps to it.
 */
@Composable
internal fun EditorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = -1f,
    max: Float = 1f,
    zero: Float = 0f,
) {
    val current by rememberUpdatedState(onValueChange)
    val material = Lumen.material
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (material) 44.dp else 40.dp)
            // Material's track has a gap cut out around the handle: that needs its own layer.
            .then(if (material) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
            .pointerInput(min, max, zero) {
                val inset = ThumbRadius.toPx()
                fun set(x: Float) {
                    val fraction = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    val raw = min + fraction * (max - min)
                    current(if (abs(raw - zero) < (max - min) * 0.02f) zero else raw)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    set(down.position.x)
                    drag(down.id) { change ->
                        set(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        val inset = ThumbRadius.toPx()
        val y = size.height / 2f
        fun x(v: Float) = inset + (v - min) / (max - min) * (size.width - 2 * inset)
        if (material) {
            // Material's slider: a thick track, lit between "unchanged" and the handle, and a
            // tall bar for a handle with a little clear space either side of it.
            val track = 16.dp.toPx()
            val gap = 6.dp.toPx()
            val handle = x(value)
            val from = minOf(x(zero), handle)
            val to = maxOf(x(zero), handle)
            clipRect(left = inset, right = size.width - inset) {
                drawLine(scheme.secondaryContainer, Offset(inset, y), Offset(size.width - inset, y), track, StrokeCap.Round)
                if (to - from > 1f) drawLine(scheme.primary, Offset(from, y), Offset(to, y), track, StrokeCap.Butt)
                // The clear space around the handle.
                drawLine(Color.Black, Offset(handle - gap, y), Offset(handle + gap, y), track + 2f, StrokeCap.Butt, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            }
            drawLine(scheme.primary, Offset(handle, y - 18.dp.toPx()), Offset(handle, y + 18.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        drawLine(Color.White.copy(alpha = 0.25f), Offset(inset, y), Offset(size.width - inset, y), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White, Offset(x(zero), y), Offset(x(value), y), 3.dp.toPx(), StrokeCap.Round)
        // A tick where "unchanged" is, when that isn't an end of the track.
        if (zero > min && zero < max) {
            drawLine(Color.White.copy(alpha = 0.6f), Offset(x(zero), y - 6.dp.toPx()), Offset(x(zero), y + 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(Color.White, inset, Offset(x(value), y))
    }
}

private val ThumbRadius = 11.dp

/** One row of a [EditorChoices] sheet. */
internal class EditorChoice(val label: String, val color: Color = Color.White, val onClick: () -> Unit)

/**
 * A short list of big buttons rising from the bottom over a dimmed editor: how to save, or whether
 * to throw the changes away. Tapping outside is [onDismiss].
 */
@Composable
internal fun EditorChoices(choices: List<EditorChoice>?, onDismiss: () -> Unit) {
    // Kept while it slides away, after [choices] has already gone back to null.
    val shown = remember { arrayOfNulls<List<EditorChoice>>(1) }
    if (choices != null) shown[0] = choices
    AnimatedVisibility(visible = choices != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = choices != null,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(160)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (choice in shown[0].orEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .pressable(choice.onClick, pressedScale = 0.97f, shape = if (Lumen.material) CircleShape else RoundedCornerShape(14.dp))
                            .clip(if (Lumen.material) CircleShape else RoundedCornerShape(14.dp))
                            .background(if (Lumen.material) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFF2C2C2E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(choice.label, color = choice.color, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Covers the editor while it saves; [progress] (0 to 1) when the wait can be measured. */
@Composable
internal fun EditorBusy(label: String, progress: Float? = null, onCancel: (() -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallows taps: nothing underneath may change while the file is written.
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (progress != null) {
                com.subhashh.lumen.ui.components.ProgressRing(progress = { progress }, color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
            } else {
                Spinner(color = Color.White)
            }
            Text(label, color = Color.White, fontSize = 15.sp)
            if (onCancel != null) EditorTextButton(stringResource(R.string.common_cancel), EditorAccent, onCancel)
        }
    }
}
