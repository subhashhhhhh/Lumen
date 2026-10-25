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

/** Lumen's icon as the system draws it: background, flare and the phone's icon shape. */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon = remember(px) { ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(px, px)?.asImageBitmap() }
    if (icon != null) Image(icon, contentDescription = stringResource(R.string.app_name), modifier = modifier.size(size))
}
