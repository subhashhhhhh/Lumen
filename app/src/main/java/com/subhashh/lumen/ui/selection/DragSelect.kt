package com.subhashh.lumen.ui.selection

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Long-press a cell to select it, then keep the finger down and drag to select the whole range
 * from there, Google Photos-style. Long-pressing an already selected cell drags to deselect
 * instead. Near the top or bottom edge the grid scrolls on its own so a range can span screens.
 *
 * @param ids item ids in grid order; null for cells that can't be selected (headers, tiles)
 * @param edgeTop / [edgeBottom] distance from the grid's edges, px, where auto-scroll kicks in
 *   (covers the header and the bottom bar, which sit over the grid)
 */
@Composable
fun <T : Any> Modifier.dragToSelect(
    gridState: LazyGridState,
    ids: () -> List<T?>,
    selection: () -> Set<T>,
    onSelectionChange: (Set<T>) -> Unit,
    onStart: () -> Unit,
    /** Gesture over (lift or cancel). Children see the lift first, so they can tell a long-press's release from a tap. */
    onEnd: () -> Unit,
    edgeTop: Float,
    edgeBottom: Float,
): Modifier {
    // The gesture outlives recompositions: read the latest list and callbacks, or a library that
    // changed since (a new photo) maps the finger to a stale list and long-press does nothing.
    val ids by rememberUpdatedState(ids)
    val selection by rememberUpdatedState(selection)
    val onSelectionChange by rememberUpdatedState(onSelectionChange)
    val onStart by rememberUpdatedState(onStart)
    val onEnd by rememberUpdatedState(onEnd)
    val edgeTop by rememberUpdatedState(edgeTop)
    val edgeBottom by rememberUpdatedState(edgeBottom)
    return pointerInput(gridState) {
        coroutineScope {
            var anchor = -1
            var current = -1
            var base = emptySet<T>()
            var removing = false
            var pointer = Offset.Zero
            var dragging = false

            fun apply() {
                val list = ids()
                if (anchor < 0 || current < 0) return
                val range = (min(anchor, current)..max(anchor, current)).mapNotNull { list.getOrNull(it) }
                onSelectionChange(if (removing) base - range.toSet() else base + range)
            }

            fun track(position: Offset) {
                pointer = position
                val index = gridState.itemIndexAt(position) ?: return
                if (index != current) {
                    current = index
                    apply()
                }
            }

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val index = gridState.itemIndexAt(offset) ?: return@detectDragGesturesAfterLongPress
                    val id = ids().getOrNull(index) ?: return@detectDragGesturesAfterLongPress
                    base = selection()
                    removing = id in base
                    anchor = index
                    current = index
                    pointer = offset
                    dragging = true
                    onStart()
                    apply()
                    launch {
                        val maxStep = 28.dp.toPx()
                        while (dragging) {
                            // Faster the deeper the finger is in the edge zone.
                            val step = when {
                                pointer.y < edgeTop -> -maxStep * ((edgeTop - pointer.y) / edgeTop).coerceIn(0.15f, 1f)
                                pointer.y > size.height - edgeBottom ->
                                    maxStep * ((pointer.y - (size.height - edgeBottom)) / edgeBottom).coerceIn(0.15f, 1f)
                                else -> 0f
                            }
                            if (step != 0f) {
                                gridState.scrollBy(step)
                                track(pointer)
                            }
                            withFrameNanos { }
                        }
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    track(change.position)
                },
                onDragEnd = { dragging = false; onEnd() },
                onDragCancel = { dragging = false; onEnd() },
            )
        }
    }
}

/**
 * Grid index under [position] (in the grid's own coordinates). Item offsets are relative to the
 * content start, which sits [androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo.viewportStartOffset]
 * (negative by the top content padding) from the grid's top edge.
 */
internal fun LazyGridState.itemIndexAt(position: Offset): Int? {
    val info = layoutInfo
    val y = position.y + info.viewportStartOffset
    return info.visibleItemsInfo.firstOrNull { item ->
        position.x >= item.offset.x && position.x < item.offset.x + item.size.width &&
            y >= item.offset.y && y < item.offset.y + item.size.height
    }?.index
}
