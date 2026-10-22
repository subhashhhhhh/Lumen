package com.subhashh.lumen.ui.transition

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * iOS-style shared zoom between a grid thumbnail and the full-screen viewer. A copy of the photo
 * flies between the two rects while the real viewer and grid cell stay hidden, so the photo
 * appears to grow out of — and land back in — its own slot.
 */
@Stable
class PhotoTransition {

    /** Where grid cells are, by item id. Coordinates are kept, not rects: they're read only when flying. */
    private val cells = HashMap<Long, LayoutCoordinates>()

    /** Reverse index: lazy grids recycle a cell for another item, which must evict the old id. */
    private val idOf = HashMap<LayoutCoordinates, Long>()

    /** The item in flight, or null when nothing is animating. */
    var item by mutableStateOf<MediaItem?>(null)
        private set

    /** 0 = at the thumbnail, 1 = at the full-screen rect. Read in layout/draw only. */
    private var progress by mutableFloatStateOf(0f)
    private var thumb = Rect.Zero
    private var full = Rect.Zero
    private var background = Color.Black
    private var backgroundStart = 1f

    /** No cell to land in (scrolled away, different screen): shrink and fade instead. */
    private var landless = false

    fun register(id: Long, coordinates: LayoutCoordinates) {
        val previous = idOf.put(coordinates, id)
        if (previous != null && previous != id && cells[previous] === coordinates) cells.remove(previous)
        cells[id] = coordinates
    }

    fun cellRect(id: Long): Rect? {
        val c = cells[id]?.takeIf { it.isAttached } ?: return null
        return Rect(c.positionInRoot(), c.size.toSize())
    }

    /** Grow from the item's grid cell to [target]. Suspends until the photo is full screen. */
    suspend fun open(item: MediaItem, target: Rect, background: Color) {
        val cell = cellRect(item.id)
        start(item, from = cell, full = target, background = background, backgroundStart = 1f)
        progress = 0f
        animate(0f, 1f, animationSpec = OpenSpring) { value, _ -> progress = value }
    }

    /**
     * Begin flying back from where the viewer left the photo. Call in the same frame the viewer
     * goes away; [land] then finds the cell (after any scroll) and finishes the flight.
     */
    fun beginClose(item: MediaItem, from: Rect, background: Color, backgroundAlpha: Float) {
        start(item, from = null, full = from, background = background, backgroundStart = backgroundAlpha)
        progress = 1f
    }

    suspend fun land(velocityY: Float) {
        val current = item ?: return
        cellRect(current.id)?.let {
            thumb = it
            landless = false
        }
        // Carry the swipe's speed into the flight, as a fraction of the distance to travel.
        val distance = max(abs(full.center.y - thumb.center.y), 1f)
        val initialVelocity = -abs(velocityY) / distance
        animate(progress, 0f, initialVelocity, CloseSpring) { value, _ -> progress = value }
        finish()
    }

    fun finish() {
        item = null
    }

    private fun start(item: MediaItem, from: Rect?, full: Rect, background: Color, backgroundStart: Float) {
        this.full = full
        this.background = background
        this.backgroundStart = backgroundStart
        landless = from == null
        thumb = from ?: shrunkInPlace(full)
        this.item = item
    }

    private fun shrunkInPlace(r: Rect): Rect {
        val w = r.width * 0.6f
        val h = r.height * 0.6f
        return Rect(r.center.x - w / 2f, r.center.y - h / 2f, r.center.x + w / 2f, r.center.y + h / 2f)
    }

    internal fun currentRect(): Rect = lerp(thumb, full, progress)
    internal fun backgroundAlpha(): Float = progress * backgroundStart
    internal fun photoAlpha(): Float = if (landless) progress else 1f
    internal fun backgroundColor(): Color = background

    private companion object {
        /** Quick and smooth with a hint of settle, ~0.35 s. */
        val OpenSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 320f)

        /** A little softer on the way back, so a fast swipe glides into place. */
        val CloseSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 280f)
    }
}

/** Lets [transition] find this grid cell, and hides it while its photo is in flight. */
fun Modifier.transitionCell(transition: PhotoTransition, id: Long): Modifier = this
    .onPlaced { transition.register(id, it) }
    .graphicsLayer { alpha = if (transition.item?.id == id) 0f else 1f }

/** The flying photo and the background fading behind it. Draws nothing when idle. */
@Composable
fun PhotoTransitionOverlay(transition: PhotoTransition) {
    val item = transition.item ?: return
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(transition.backgroundColor(), alpha = transition.backgroundAlpha()) },
    ) {
        AsyncImage(
            model = remember(item.id) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    // Starts as the grid thumbnail instantly; sharpens if the full image decodes in time.
                    .placeholderMemoryCacheKey("thumb:${item.id}")
                    .build()
            },
            contentDescription = null,
            // Crop into a rect morphing from the square cell to the photo's own shape, so the
            // cell's crop opens up into the whole photo as it grows.
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val r = transition.currentRect()
                    val w = r.width.roundToInt().coerceAtLeast(1)
                    val h = r.height.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(r.left.roundToInt(), r.top.roundToInt())
                    }
                }
                .graphicsLayer { alpha = transition.photoAlpha() },
        )
    }
}
