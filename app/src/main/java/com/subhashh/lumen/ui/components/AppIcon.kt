package com.subhashh.lumen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LauncherIcon

/**
 * One of Lumen's icons as the system draws it, in the phone's icon shape: [icon], or the one on
 * the Home screen now.
 */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier, icon: LauncherIcon? = null) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val shown = icon ?: remember { LauncherIcon.chosen(context) }
    val bitmap = remember(px, shown) { ContextCompat.getDrawable(context, shown.icon)?.toBitmap(px, px)?.asImageBitmap() }
    if (bitmap != null) Image(bitmap, contentDescription = stringResource(R.string.app_name), modifier = modifier.size(size))
}
