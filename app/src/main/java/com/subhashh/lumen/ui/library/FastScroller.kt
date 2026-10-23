package com.subhashh.lumen.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A handle on the grid's right edge for crossing years of photos in one drag. It shows while
 * the grid scrolls and fades a moment after; while dragged, a label beside it says where you'll
 * land (the month, a letter, a size) and ticks as that changes.
 *
 * @param label what to show for the item at an index
 * @param topInset / [bottomInset] keep the track clear of the header and the tab bar
 */
@Composable
fun FastScroller(
    gridState: LazyGridState,
    itemCount: Int,
    label: (index: Int) -> String,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    // Not worth a handle for a couple of screens.
    if (itemCount < MinItems) return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentLabel by rememberUpdatedState(label)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }

    // How far through the grid the first visible cell is, 0..1.
    val scrollFraction by remember(gridState, itemCount) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
            val first = gridState.firstVisibleItemIndex
            (first.toFloat() / (itemCount - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }
    val active = dragging || gridState.isScrollInProgress
    LaunchedEffect(active) {
        if (active) {
            shown = true
        } else {
            delay(HideDelayMs)
            shown = false
        }
    }

    BoxWithConstraints(modifier.fillMaxHeight().padding(top = topInset, bottom = bottomInset)) {
        val density = LocalDensity.current
        val trackPx = with(density) { (maxHeight - HandleHeight).toPx() }.coerceAtLeast(1f)
        val fraction = if (dragging) dragFraction else scrollFraction
        val index = (fraction * (itemCount - 1)).roundToInt()
        val text = if (dragging) currentLabel(index) else ""
        var lastText by remember { mutableStateOf("") }
        LaunchedEffect(text) {
            if (dragging && text.isNotEmpty() && text != lastText && lastText.isNotEmpty()) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            lastText = text
        }

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(150)) + slideInHorizontally(tween(200)) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 2 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * trackPx).roundToInt()) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragging && text.isNotEmpty()) {
                    Text(
                        text,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(50))
                            .background(Color(0xE6262628), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                val handleWidth by animateDpAsState(
                    if (dragging) 12.dp else 7.dp,
                    spring(dampingRatio = 0.7f, stiffness = 600f),
                    label = "handleWidth",
                )
                // A wide, invisible touch target around a slim visible handle.
                Box(
                    Modifier
                        .width(TouchWidth)
                        .height(HandleHeight)
                        .pointerInput(itemCount) {
                            detectVerticalDragGestures(
                                onDragStart = { start ->
                                    dragging = true
                                    dragFraction = scrollFraction
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragFraction = (dragFraction + amount / trackPx).coerceIn(0f, 1f)
                                    val target = (dragFraction * (itemCount - 1)).roundToInt()
                                    scope.launch { gridState.scrollToItem(target) }
                                },
                            )
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(width = handleWidth, height = HandleHeight - 8.dp)
                            .shadow(4.dp, CircleShape)
                            .background(Color.White.copy(alpha = 0.92f), CircleShape),
                    )
                }
            }
        }
    }
}

private const val MinItems = 150
private const val HideDelayMs = 1500L
private val HandleHeight = 52.dp
private val TouchWidth = 36.dp
