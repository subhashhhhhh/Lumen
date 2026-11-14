package com.subhashh.lumen.ui.editor

import androidx.compose.animation.AnimatedVisibility
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
internal val EditorAccent = Color(0xFF0A84FF)
internal val EditorPanel = Color(0xE61C1C1E)

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
            .size(44.dp)
            .pressable(onClick)
            .clip(CircleShape)
            .background(if (active) Color.White else EditorPanel),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A word in a pill: a crop shape, an adjustment. The chosen one is white. */
@Composable
internal fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier
            .pressable(onClick, pressedScale = 0.94f)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else EditorPanel)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (detail != null) {
            Text(detail, color = if (selected) Color.Black.copy(alpha = 0.55f) else EditorAccent, fontSize = 14.sp)
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
        for (tab in tabs) {
            val tint = if (tab == selected) EditorAccent else Color.White.copy(alpha = 0.7f)
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
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
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
                            .pressable(choice.onClick, pressedScale = 0.97f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2C2C2E)),
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
