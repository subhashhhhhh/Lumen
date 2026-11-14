package com.subhashh.lumen.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Material 3 Expressive's top app bar. Lumen's own style has no bar: each page draws a large
 * title in its content and glass buttons float over it. In the Material style those same titles
 * are handed to the bar instead (reportBarTitle), so a page says what it's called once and the
 * style decides where that goes.
 */

/** What a page shows in the bar. [visibility] is 0 while the page's cover still fills the top. */
class BarTitle(val title: String, val subtitle: String, val overCover: Boolean, val visibility: () -> Float)

@Stable
class MaterialBarState {
    private class Entry(val owner: Any, var value: BarTitle)

    // Pages stay composed under the one opened from them (Collections under an album): the
    // newest arrival is the one on top, and leaving hands the bar back to the page beneath.
    private val entries = mutableStateListOf<Entry>()

    val current: BarTitle? get() = entries.lastOrNull()?.value
    internal val owner: Any? get() = entries.lastOrNull()?.owner

    internal fun set(owner: Any, value: BarTitle) {
        val index = entries.indexOfFirst { it.owner === owner }
        if (index < 0) entries += Entry(owner, value)
        else if (entries[index].value.let { it.title != value.title || it.subtitle != value.subtitle || it.overCover != value.overCover }) entries[index] = Entry(owner, value)
        else entries[index].value = value
    }

    internal fun remove(owner: Any) {
        entries.removeAll { it.owner === owner }
    }
}

/** The bar pages report to; null in Lumen's own style (and wherever there's no bar). */
val LocalMaterialBar = staticCompositionLocalOf<MaterialBarState?> { null }

/** True for controls drawn on the opaque bar: they drop their own surface and sit on its. */
val LocalOnBar = staticCompositionLocalOf { false }

/**
 * Gives this page's title to the Material top bar, if there is one. Returns whether it was taken,
 * in which case the page doesn't draw the title itself.
 */
@Composable
fun reportBarTitle(title: String, subtitle: String = "", overCover: Boolean = false, visibility: () -> Float = { 1f }): Boolean {
    val bar = LocalMaterialBar.current ?: return false
    val owner = remember { Any() }
    SideEffect { bar.set(owner, BarTitle(title, subtitle, overCover, visibility)) }
    DisposableEffect(bar) { onDispose { bar.remove(owner) } }
    return true
}

val MaterialBarHeight = 64.dp

/**
 * The bar itself: the status bar's space, then the title (after the Back button when [pushed]).
 * The buttons are Lumen's usual top controls, drawn over it by the caller. [below] is a second
 * row belonging to the bar (the Library's Years / Months / All).
 */
@Composable
fun MaterialTopBar(
    state: MaterialBarState,
    statusTop: Dp,
    pushed: Boolean,
    /** Nothing to name (search has its field there), but the bar is still drawn. */
    untitled: Boolean,
    modifier: Modifier = Modifier,
    below: (@Composable () -> Unit)? = null,
) {
    val entry = state.current
    if (entry == null && !untitled) return
    val visibility = entry?.visibility ?: { 1f }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = visibility() }
            .background(scheme.surface)
            // Taps on the bar stay on the bar: there are photos scrolled up under it.
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = entry?.overCover != true) {}
            .padding(top = statusTop),
    ) {
        Box(Modifier.fillMaxWidth().height(MaterialBarHeight), contentAlignment = Alignment.CenterStart) {
            // A new page's name rises into place; a count changing on the same page just changes.
            val rise = expressiveSpatialDefault<androidx.compose.ui.unit.IntOffset>()
            AnimatedContent(
                targetState = state.owner,
                transitionSpec = {
                    (slideInVertically(rise) { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "barTitle",
            ) { owner ->
                val shown = state.current?.takeIf { state.owner === owner } ?: return@AnimatedContent
                Column(Modifier.padding(start = if (pushed) 76.dp else 24.dp, end = 124.dp)) {
                    Text(
                        shown.title,
                        color = scheme.onSurface,
                        fontSize = if (pushed) 22.sp else 28.sp,
                        lineHeight = if (pushed) 28.sp else 34.sp,
                        fontWeight = if (pushed) FontWeight.Normal else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (shown.subtitle.isNotEmpty()) {
                        Text(shown.subtitle, color = scheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                }
            }
        }
        below?.invoke()
    }
}
