package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.GridThumbnail
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.images.ImageKeys
import com.subhashh.lumen.ui.images.bestPlaceholderKey
import com.subhashh.lumen.ui.images.version
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * iOS Photos' strip of thumbnails above the viewer's buttons: thin slices of the photos around
 * this one, which opens up to its own shape in the middle. It follows the pager as you swipe;
 * drag or flick it to skim (the photo above keeps up, with a tick per photo), tap one to go there.
 */
@Composable
fun FilmStrip(items: List<MediaItem>, pager: PagerState, height: Dp, modifier: Modifier = Modifier) {
    val sliceDp = height.value * SliceShare
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    /** While skimming: the strip's own position, in pages, which the pager follows. */
    var skim by remember { mutableStateOf<Float?>(null) }
    var skimJob by remember { mutableStateOf<Job?>(null) }

    val position = { skim ?: (pager.currentPage + pager.currentPageOffsetFraction) }
    // Which thumbnails exist: a screenful either side of the photo shown (recomposes per page).
    val centre = (skim?.roundToInt() ?: pager.currentPage).coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val reach = (LocalConfiguration.current.screenWidthDp / (sliceDp + GapDp) / 2f).toInt() + 3
    val window = (centre - reach).coerceAtLeast(0)..(centre + reach).coerceAtMost(items.lastIndex)

    /** Moves the photo above to the one under the strip's middle, with a tick for each new one. */
    fun follow(to: Float) {
        val clamped = to.coerceIn(0f, (currentItems.size - 1).toFloat())
        skim = clamped
        val page = clamped.roundToInt()
        if (page != pager.currentPage) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            scope.launch { pager.scrollToPage(page) }
        }
    }

    Layout(
        content = {
            for (i in window) {
                val item = items[i]
                key(item.id) { Thumb(item) }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Thumbnails fade out towards both edges instead of being cut off.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.08f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .pointerInput(Unit) {
                val step = (sliceDp + GapDp).dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    skimJob?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var start = position()
                    var moved = 0f
                    val dragged = horizontalDrag(down.id) { change ->
                        val dx = change.positionChange().x
                        moved += abs(dx)
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        start -= dx / step
                        follow(start)
                    }
                    if (moved < viewConfiguration.touchSlop) {
                        // A tap: go to the thumbnail under the finger.
                        val target = indexAt(down.position.x, size.width.toFloat(), position(), currentItems, height, this@pointerInput)
                        skim = null
                        if (target != null && target != pager.currentPage) scope.launch { pager.animateScrollToPage(target) }
                        return@awaitEachGesture
                    }
                    // Flicked: keep skimming, slowing down, then settle on the nearest photo.
                    val velocity = -tracker.calculateVelocity().x / step
                    skimJob = scope.launch {
                        try {
                            if (dragged && abs(velocity) > 0.5f) {
                                AnimationState(start, velocity).animateDecay(exponentialDecay(frictionMultiplier = 2.2f)) {
                                    follow(value)
                                    if (value <= 0f || value >= currentItems.size - 1f) cancelAnimation()
                                }
                            }
                            val from = skim ?: return@launch
                            AnimationState(from).animateTo(from.roundToInt().toFloat().coerceIn(0f, currentItems.size - 1f), spring(stiffness = 500f)) {
                                follow(value)
                            }
                        } finally {
                            skim = null
                        }
                    }
                }
            },
    ) { measurables, constraints ->
        val p = position()
        val strip = constraints.maxWidth.toFloat()
        val slots = slots(window, p, items, height, this)
        val placeables = measurables.mapIndexed { n, m ->
            val w = slots[n].width.roundToInt().coerceAtLeast(1)
            m.measure(Constraints.fixed(w, constraints.maxHeight))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { n, placeable ->
                val left = strip / 2f + slots[n].center - slots[n].width / 2f
                if (left < strip && left + slots[n].width > 0f) placeable.place(left.roundToInt(), 0)
            }
        }
    }
}

/** One thumbnail: a centre crop, sized by the strip. */
@Composable
private fun Thumb(item: MediaItem) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(item.version) {
            ImageRequest.Builder(context)
                .data(GridThumbnail(item, ThumbPx))
                .size(ThumbPx, ThumbPx)
                .memoryCacheKey(ImageKeys.thumb(item, ThumbPx))
                .placeholderMemoryCacheKey(bestPlaceholderKey(SingletonImageLoader.get(context), item))
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    )
}

private class Slot(val center: Float, val width: Float)

/**
 * Where each thumbnail in [window] goes, as its centre's distance from the strip's middle, with
 * the strip at [p] pages: slices everywhere, opening up to the photo's own shape (and a little
 * more room around it) as it nears the middle.
 */
private fun slots(window: IntRange, p: Float, items: List<MediaItem>, stripHeight: Dp, density: androidx.compose.ui.unit.Density): List<Slot> {
    if (window.isEmpty()) return emptyList()
    with(density) {
        val slice = stripHeight.toPx() * SliceShare
        val gap = GapDp.dp.toPx()
        val height = stripHeight.toPx()
        fun openness(i: Int) = (1f - abs(i - p)).coerceIn(0f, 1f)
        fun width(i: Int): Float {
            val item = items[i]
            val aspect = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 1f
            val open = (height * aspect).coerceIn(slice, height * MaxOpenAspect)
            return slice + (open - slice) * openness(i)
        }
        fun space(i: Int) = gap + (CurrentGapDp.dp.toPx() - gap) * openness(i)

        val widths = window.associateWith(::width)
        // Centres with the first thumbnail at 0, each next one a half-width, gap and half-width on.
        val centres = HashMap<Int, Float>()
        var x = 0f
        for (i in window) {
            if (i > window.first) x += widths.getValue(i - 1) / 2f + maxOf(space(i - 1), space(i)) + widths.getValue(i) / 2f
            centres[i] = x
        }
        // The strip's middle sits between the two thumbnails around p.
        val a = floor(p).toInt().coerceIn(window.first, window.last)
        val b = ceil(p).toInt().coerceIn(window.first, window.last)
        val f = p - floor(p)
        val middle = centres.getValue(a) + (centres.getValue(b) - centres.getValue(a)) * f
        return window.map { Slot(centres.getValue(it) - middle, widths.getValue(it)) }
    }
}

/** The thumbnail under [x] on a strip [stripWidth] wide at position [p], if any. */
private fun indexAt(x: Float, stripWidth: Float, p: Float, items: List<MediaItem>, stripHeight: Dp, density: androidx.compose.ui.unit.Density): Int? {
    val reach = (stripWidth / with(density) { stripHeight.toPx() * SliceShare + GapDp.dp.toPx() } / 2f).toInt() + 3
    val centre = p.roundToInt().coerceIn(0, items.lastIndex)
    val window = (centre - reach).coerceAtLeast(0)..(centre + reach).coerceAtMost(items.lastIndex)
    val slots = slots(window, p, items, stripHeight, density)
    val offset = x - stripWidth / 2f
    return window.zip(slots).firstOrNull { (_, s) -> abs(offset - s.center) <= s.width / 2f + 2f }?.first
}

/** A thumbnail's slice width, as a share of the strip's height. */
private const val SliceShare = 0.62f
private const val GapDp = 2f
/** Extra room either side of the photo shown. */
private const val CurrentGapDp = 6f
/** The photo shown opens up to at most this wide for its height (panoramas stay reasonable). */
private const val MaxOpenAspect = 1.8f
private const val ThumbPx = 192
