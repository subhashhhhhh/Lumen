package com.subhashh.lumen.ui.components

import com.subhashh.lumen.ui.theme.LocalMotion
import androidx.compose.material3.ripple
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.subhashh.lumen.ui.theme.LocalStyle

/**
 * Clickable with iOS-style feedback instead of a ripple: shrinks while the finger is down and
 * springs back with a little bounce on release. Use a smaller squish for big surfaces (cards)
 * than for icon buttons. In the Material style it's Android's own feedback: a ripple.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.88f,
    /** Material's ripple is clipped to this; a softly rounded rectangle when null. */
    shape: Shape? = null,
): Modifier = composed {
    // Material 3 Expressive: a ripple where the finger is, and a small squeeze.
    if (LocalStyle.current.material) {
        val still = LocalMotion.current.reduce
        val source = remember { MutableInteractionSource() }
        val down by source.collectIsPressedAsState()
        val squeeze by animateFloatAsState(
            targetValue = if (down && enabled && !still) 1f - (1f - pressedScale) * 0.4f else 1f,
            animationSpec = if (down) PressIn else Wobble,
            label = "pressSqueeze",
        )
        return@composed this
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .clip(shape ?: RippleShape)
            .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick)
    }
    // Reduce Motion: still a button, just no squish.
    val reduce = LocalMotion.current.reduce
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduce) pressedScale else 1f,
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

private val RippleShape = RoundedCornerShape(12.dp)

/** Material's release: back with the slightest overshoot. */
private val Wobble = spring<Float>(dampingRatio = 0.75f, stiffness = 500f)

/** Quick and firm going down… */
private val PressIn = spring<Float>(dampingRatio = 1f, stiffness = 1400f)

/** …and a soft bounce coming back up. */
private val Release = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)
