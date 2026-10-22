package com.subhashh.lumen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Clickable with iOS-style feedback instead of a ripple: shrinks while the finger is down and
 * springs back with a little bounce on release. Use a smaller squish for big surfaces (cards)
 * than for icon buttons.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.88f,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = if (pressed) PressIn else Release,
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Quick and firm going down… */
private val PressIn = spring<Float>(dampingRatio = 1f, stiffness = 1400f)

/** …and a soft bounce coming back up. */
private val Release = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)
