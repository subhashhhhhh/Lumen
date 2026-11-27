package com.subhashh.lumen.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Wide windows (tablets, desktop mode, landscape): layouts were drawn for a phone about
 * [PhoneWidth] across. Grids keep a phone's cell size by showing more columns, and lists keep a
 * readable width instead of stretching edge to edge.
 */
val PhoneWidth: Dp = 400.dp

/** The widest a list (Settings, utilities) is drawn; centred in a wider window. */
val ReadableWidth: Dp = 680.dp

/** The window's width (a desktop-mode or split-screen window, not the display's). */
@Composable
fun windowWidth(): Dp = LocalConfiguration.current.screenWidthDp.dp

/**
 * [phoneColumns] (what the person chose, pinching on a phone) for this window: the same cell size
 * as on a phone, so a 1,100 dp window shows about 14 columns where a phone shows 5. Never fewer
 * than [phoneColumns]. Pinch-to-zoom keeps stepping through the phone values.
 */
@Composable
fun scaledColumns(phoneColumns: Int): Int {
    val width = windowWidth()
    if (width <= PhoneWidth * 1.2f) return phoneColumns
    return maxOf(phoneColumns, (phoneColumns * (width / PhoneWidth)).roundToInt())
}

/** Centres the content at most [ReadableWidth] wide (fills the width on a phone). */
fun Modifier.readableWidth(): Modifier = fillMaxWidth().wrapContentWidth().widthIn(max = ReadableWidth).fillMaxWidth()
