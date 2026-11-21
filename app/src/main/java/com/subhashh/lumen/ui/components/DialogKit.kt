package com.subhashh.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.ui.theme.Lumen

/*
 * The pieces Lumen's dialogs are built from, drawn in the current style (Settings → Appearance →
 * Style). Lumen: an iOS alert, centred, with hairlines and text buttons across the bottom.
 * Material: start-aligned, a large headline, and buttons at the bottom right.
 */

/** Dims the page behind a dialog; a tap on it dismisses. [strong]: a touch darker, behind the big dialogs. */
@Composable
fun DialogScrim(open: Boolean, onDismiss: () -> Unit, strong: Boolean = false) {
    AnimatedVisibility(visible = open, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (strong && !Lumen.material) Color.Black.copy(alpha = 0.45f) else Lumen.colors.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
}

/** A dialog's card: clipped, filled, and swallowing taps so they don't reach the scrim. */
@Composable
fun Modifier.dialogCard(color: Color = Lumen.colors.dialog, shape: Shape = Lumen.shapes.dialog): Modifier =
    clip(shape)
        .background(color)
        .clickable(remember { MutableInteractionSource() }, indication = null) {}

/** How a dialog lines up its title and message: centred (Lumen) or from the start (Material). */
val dialogAlignment: Alignment.Horizontal
    @Composable get() = if (Lumen.material) Alignment.Start else Alignment.CenterHorizontally

val dialogTextAlign: TextAlign
    @Composable get() = if (Lumen.material) TextAlign.Start else TextAlign.Center

/** Side padding for a dialog's title and message. */
val dialogInset: Dp
    @Composable get() = if (Lumen.material) 24.dp else 18.dp

/** [oneLine]: a short title that never wraps (Rename), which Lumen leaves to the column to centre. */
@Composable
fun DialogTitle(text: String, modifier: Modifier = Modifier, oneLine: Boolean = false) {
    val material = Lumen.material
    Text(
        text,
        color = Lumen.colors.text,
        style = Lumen.type.dialogTitle,
        textAlign = if (oneLine && !material) TextAlign.Unspecified else dialogTextAlign,
        modifier = modifier
            .then(if (material) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = dialogInset),
    )
}

@Composable
fun DialogMessage(text: String, modifier: Modifier = Modifier, color: Color = Lumen.colors.secondary) {
    val material = Lumen.material
    Text(
        text,
        color = color,
        fontSize = 14.sp,
        lineHeight = if (material) 20.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        textAlign = dialogTextAlign,
        modifier = modifier
            .then(if (material) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = dialogInset),
    )
}

/**
 * Cancel and one action, closing a dialog. Lumen: two halves under a hairline. Material: a text
 * button and a filled one at the bottom right.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogButtons(
    cancel: String,
    onCancel: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    /** Lumen's row height. */
    height: Dp = 48.dp,
    /** Lumen's hairlines; a faded secondary by default. */
    line: Color = Lumen.colors.secondary.copy(alpha = 0.3f),
) {
    val colors = Lumen.colors
    if (Lumen.material) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(cancel) }
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                enabled = enabled,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(containerColor = colors.destructive, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirm) }
        }
        return
    }
    val pad = (height - 22.dp) / 2
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(line))
    Row(Modifier.fillMaxWidth().height(height)) {
        Text(
            cancel,
            color = colors.accent,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onCancel)
                .padding(vertical = pad),
        )
        Box(Modifier.width(0.5.dp).height(height).background(line))
        Text(
            confirm,
            color = when {
                !enabled -> colors.secondary
                destructive -> colors.destructive
                else -> colors.accent
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, onClick = onConfirm)
                .padding(vertical = pad),
        )
    }
}

@Immutable
class DialogAction(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * Three or more choices, one under the other. Lumen: text rows split by hairlines. Material: a
 * filled button for the main one, text buttons for the rest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogActionList(actions: List<DialogAction>) {
    val colors = Lumen.colors
    if (Lumen.material) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (action in actions) {
                if (action.primary) {
                    Button(onClick = action.onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                } else {
                    TextButton(onClick = action.onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                }
            }
        }
        return
    }
    val line = colors.secondary.copy(alpha = 0.3f)
    for (action in actions) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(line))
        Text(
            action.label,
            color = colors.accent,
            fontSize = 17.sp,
            fontWeight = if (action.primary) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = action.onClick)
                .padding(vertical = 13.dp),
        )
    }
}
