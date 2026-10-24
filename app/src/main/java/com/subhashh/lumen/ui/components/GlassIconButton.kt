package com.subhashh.lumen.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.ui.theme.LocalGlassColors

@Composable
fun GlassIconButton(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlassColors.current.content,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .pressable(onClick)
            .glass(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/**
 * One glass button whose icon turns from [from] into [to] as [fraction] goes 0 → 1 (read in
 * draw only), for a button that stays put while the page under it changes. Two buttons
 * cross-fading would dim the glass halfway.
 */
@Composable
fun GlassMorphButton(
    backdrop: Backdrop,
    from: ImageVector,
    to: ImageVector,
    fraction: () -> Float,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlassColors.current.content,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .pressable(onClick)
            .glass(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            from,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                val f = fraction()
                alpha = 1f - f
                scaleX = 1f - 0.4f * f
                scaleY = 1f - 0.4f * f
            },
        )
        Icon(
            to,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                val f = fraction()
                alpha = f
                scaleX = 0.6f + 0.4f * f
                scaleY = 0.6f + 0.4f * f
            },
        )
    }
}
