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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
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

    /** True from the moment a photo starts growing until the viewer takes over. */
    var opening by mutableStateOf(false)
        private set

    /**
     * The photo's center and how grown it is (0 = cell size, 1 = full size) are animated
     * separately: position springs carry the finger's speed in each axis, so a flick keeps
     * going the way it was thrown before curving into the cell. Read in layout/draw only.
     */
    private var centerX by mutableFloatStateOf(0f)
    private var centerY by mutableFloatStateOf(0f)
    private var grown by mutableFloatStateOf(0f)
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
        start(item, from = cellRect(item.id), full = target, background = background, backgroundStart = 1f)
        opening = true
        centerX = thumb.center.x
        centerY = thumb.center.y
        grown = 0f
        coroutineScope {
            launch { animate(centerX, full.center.x, animationSpec = OpenPosition) { v, _ -> centerX = v } }
            launch { animate(centerY, full.center.y, animationSpec = OpenPosition) { v, _ -> centerY = v } }
            launch { animate(0f, 1f, animationSpec = OpenSize) { v, _ -> grown = v } }
        }
    }

    /**
     * Begin flying back from where the viewer left the photo. Call in the same frame the viewer
     * goes away; [land] then finds the cell (after any scroll) and finishes the flight.
     */
    fun beginClose(item: MediaItem, from: Rect, background: Color, backgroundAlpha: Float) {
        start(item, from = null, full = from, background = background, backgroundStart = backgroundAlpha)
        opening = false
        centerX = from.center.x
        centerY = from.center.y
        grown = 1f
    }

    suspend fun land(velocity: Velocity) {
        val current = item ?: return
        cellRect(current.id)?.let {
            thumb = it
            landless = false
        }
        val vx = velocity.x.coerceIn(-MaxThrow, MaxThrow)
        val vy = velocity.y.coerceIn(-MaxThrow, MaxThrow)
        coroutineScope {
            launch { animate(centerX, thumb.center.x, vx, LandPositionSpring) { v, _ -> centerX = v } }
            launch { animate(centerY, thumb.center.y, vy, LandPositionSpring) { v, _ -> centerY = v } }
            // Size never overshoots: shrinking past the cell and back reads as a second animation.
            launch { animate(grown, 0f, animationSpec = LandSizeSpring) { v, _ -> grown = v } }
        }
        finish()
    }

    fun finish() {
        item = null
        opening = false
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

    internal fun currentRect(): Rect {
        val g = grown.coerceIn(0f, 1f)
        val w = thumb.width + (full.width - thumb.width) * g
        val h = thumb.height + (full.height - thumb.height) * g
        return Rect(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
    }

    internal fun backgroundAlpha(): Float = grown.coerceIn(0f, 1f) * backgroundStart
    internal fun photoAlpha(): Float = if (landless) grown.coerceIn(0f, 1f) else 1f
    internal fun backgroundColor(): Color = background

    private companion object {
        // Springs settle to their visibility threshold before the animation ends. The default
        // (0.01) means 0.01 px for positions: an invisible tail that delayed the viewer by
        // ~0.3 s after the photo had visibly arrived. Half a pixel / 0.2% is indistinguishable.
        const val PxThreshold = 0.5f
        const val FractionThreshold = 0.002f

        /** Quick and smooth with a hint of settle, ~0.35 s. */
        val OpenPosition = spring(dampingRatio = 0.9f, stiffness = 320f, visibilityThreshold = PxThreshold)
        val OpenSize = spring(dampingRatio = 0.9f, stiffness = 320f, visibilityThreshold = FractionThreshold)

        /** Loose enough that a throw visibly carries before curving home; a trace of settle. */
        val LandPositionSpring = spring(dampingRatio = 0.82f, stiffness = 230f, visibilityThreshold = PxThreshold)

        val LandSizeSpring = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 260f,
            visibilityThreshold = FractionThreshold,
        )

        /** px/s; beyond this a hard flick would sail off screen before coming back. */
        const val MaxThrow = 5000f
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
