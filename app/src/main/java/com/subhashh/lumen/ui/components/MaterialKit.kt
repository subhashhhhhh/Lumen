package com.subhashh.lumen.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import com.subhashh.lumen.ui.theme.Lumen
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhashh.lumen.ui.theme.LocalMotion

/*
 * Material 3 Expressive's building blocks, for the components that draw themselves in both
 * styles (Settings → Appearance → Style). Only used while the style is Material.
 */

/** A solid tonal surface floating over the page: a soft shadow and a fill, in [shape]. */
fun Modifier.materialSurface(color: Color, shape: Shape, elevation: Dp = 3.dp): Modifier =
    shadow(elevation, shape, clip = false).background(color, shape)

/** The quick, slightly bouncy spring Expressive moves shapes and positions with; none with Reduce Motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> expressiveSpatial(): FiniteAnimationSpec<T> =
    if (LocalMotion.current.reduce) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

/** The same, a little slower: for things that travel further (a selection sliding along a bar). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> expressiveSpatialDefault(): FiniteAnimationSpec<T> =
    if (LocalMotion.current.reduce) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

/**
 * An Expressive button surface: round at rest, squaring off under the finger and springing back,
 * with a ripple. [size] is its height (and width, unless [modifier] says otherwise after it).
 */
@Composable
fun MaterialMorphSurface(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    /** Corner radius at rest; half the height (a circle or capsule) by default. */
    restCorner: Dp = size / 2,
    pressedCorner: Dp = if (size >= 56.dp) 16.dp else 12.dp,
    enabled: Boolean = true,
    elevation: Dp = 3.dp,
    square: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed && enabled) pressedCorner else restCorner, expressiveSpatial(), label = "morphCorner")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .then(if (square) Modifier.size(size) else Modifier)
            .materialSurface(color, shape, elevation)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** "Working on it", with no known end. Material: the shape-shifting loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) {
    if (Lumen.material) LoadingIndicator(modifier, color = color)
    else CircularProgressIndicator(modifier, color = color, strokeWidth = strokeWidth)
}

/** Progress around a ring, 0 to 1. Material: the wavy ring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f),
) {
    if (Lumen.material) CircularWavyProgressIndicator(progress, modifier, color = color, trackColor = trackColor)
    else CircularProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
}
