package com.subhashh.lumen.ui.components

import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.subhashh.lumen.ui.theme.LocalGlassColors

/**
 * Liquid glass surface. Android 13+ gets blur plus lens refraction, Android 12 gets blur only
 * (backdrop skips effects the OS can't render), and Android 11 falls back to an opaque tint.
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    blurRadius: Dp = 4.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
): Modifier {
    val colors = LocalGlassColors.current
    val tint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) colors.tint else colors.fallbackTint
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            lens(refractionHeight.toPx(), refractionAmount.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )
}
