package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The viewer's vertical gestures: pull up for the details sheet, pull down to dismiss.
 *
 * Positions are plain state written synchronously on every pointer event, so the photo stays
 * glued to the finger; only the release starts an animation. (Launching a coroutine per drag
 * event let stale updates cancel the settle and leave the sheet stuck half open.)
 *
 * Dismissing is a free 2D drag, like iOS: the photo follows the finger in any direction and
 * shrinks around the touch point so it stays under the thumb.
 */
@Stable
class ViewerMotion(
    private val scope: CoroutineScope,
    /** Release speed, px/s, that counts as a flick regardless of position. */
    private val flingVelocity: Float,
    /** The details sheet was let go and is snapping open or shut (for a haptic tick). */
    private val onSheetSnap: () -> Unit = {},
) {
    /** Photo offset while being dragged toward dismissal, px. */
    var dismissX by mutableFloatStateOf(0f)
        private set
    var dismissY by mutableFloatStateOf(0f)
        private set

    /** Scale pivot while dismissing: where the finger first touched. */
    var pivot by mutableStateOf(TransformOrigin.Center)
        private set

    /** True while a finger is dragging the photo; paging is paused meanwhile. */
    var dragging by mutableStateOf(false)
        private set

    /** How far the details sheet is pulled up, px (0 = closed). */
    var reveal by mutableFloatStateOf(0f)
        private set

    /** Where the sheet is headed; drives back handling and the info button. */
    var infoOpen by mutableStateOf(false)
        private set

    var sheetHeight = 1f

    private enum class Mode { None, Reveal, Dismiss }

    private var mode = Mode.None
    private var settle: Job? = null
    private var startReveal = 0f
    private var startNanos = 0L

    fun onDragStart(touch: Offset, size: IntSize, firstDelta: Offset) {
        settle?.cancel()
        startReveal = reveal
        startNanos = System.nanoTime()
        // Up from rest, or any drag while the sheet is out, moves the sheet.
        mode = if (reveal > 0f || firstDelta.y < 0f) Mode.Reveal else Mode.Dismiss
        if (mode == Mode.Dismiss) {
            pivot = TransformOrigin(
                (touch.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                (touch.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
            )
        }
        dragging = true
        onDrag(firstDelta)
    }

    fun onDrag(delta: Offset) {
        when (mode) {
            Mode.Reveal -> reveal = (reveal - delta.y).coerceIn(0f, sheetHeight)
            Mode.Dismiss -> {
                dismissX += delta.x
                dismissY += delta.y
            }
            Mode.None -> Unit
        }
    }

    /** @return true when the gesture asks to dismiss the viewer */
    fun onDragStop(velocity: Velocity, dismissDistance: Float): Boolean {
        dragging = false
        val wasMode = mode
        mode = Mode.None
        when (wasMode) {
            Mode.Reveal -> settleReveal(releaseSpeed(velocity.y))
            Mode.Dismiss -> {
                // Down past the threshold, or flicked downward, dismisses; anything else snaps back.
                if (dismissY > dismissDistance || velocity.y > flingVelocity * 1.5f) return true
                springBack(velocity)
            }
            Mode.None -> Unit
        }
        return false
    }

    fun openInfo() = animateReveal(open = true, velocityY = 0f)

    fun closeInfo() = animateReveal(open = false, velocityY = 0f)

    /** Lets the sheet's own content close it once scrolled to the top, and reopen it on the way up. */
    val sheetScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && reveal < sheetHeight) {
                settle?.cancel()
                val consumed = available.y.coerceAtLeast(reveal - sheetHeight)
                reveal -= consumed
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && source == NestedScrollSource.UserInput) {
                settle?.cancel()
                reveal = (reveal - available.y).coerceIn(0f, sheetHeight)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (reveal > 0f && reveal < sheetHeight) {
                settleReveal(available.y)
                return available
            }
            return Velocity.Zero
        }
    }

    /**
     * The finger's speed as it lifted, or its average over the whole pull if that was faster:
     * a quick short swipe that slows at the very end is still a flick.
     */
    private fun releaseSpeed(liftVelocity: Float): Float {
        val seconds = (System.nanoTime() - startNanos) / 1e9f
        val average = if (seconds > 0f) -(reveal - startReveal) / seconds else 0f
        return if (abs(average) > abs(liftVelocity)) average else liftVelocity
    }

    private fun settleReveal(velocityY: Float) {
        // A flick decides on its own; otherwise go to whichever end is nearer.
        val open = if (abs(velocityY) >= flingVelocity) velocityY < 0f else reveal > sheetHeight / 2f
        onSheetSnap()
        animateReveal(open, velocityY)
    }

    private fun animateReveal(open: Boolean, velocityY: Float) {
        settle?.cancel()
        infoOpen = open
        settle = scope.launch {
            // The finger's speed carries into the spring, so a flick keeps going and a slow pull eases in.
            animate(
                initialValue = reveal,
                targetValue = if (open) sheetHeight else 0f,
                initialVelocity = -velocityY,
                animationSpec = SheetSpring,
            ) { value, _ -> reveal = value }
        }
    }

    private fun springBack(velocity: Velocity) {
        settle?.cancel()
        settle = scope.launch {
            launch { animate(dismissX, 0f, velocity.x, SnapBackSpring) { value, _ -> dismissX = value } }
            launch { animate(dismissY, 0f, velocity.y, SnapBackSpring) { value, _ -> dismissY = value } }
        }
    }

    private companion object {
        /** No bounce, ~0.4 s response: how iOS sheets settle. */
        val SheetSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 250f)

        /** A touch of overshoot when a short dismiss drag snaps back. */
        val SnapBackSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    }
}

/**
 * Vertical-start drags anywhere on the viewer: dismiss (free 2D) or the details sheet. Horizontal
 * drags are left to the pager and multi-finger gestures to the zoomable image. The drag counts from
 * the touch-down point, slop included, so nothing jumps when it begins.
 *
 * Runs in the Initial pass — before the pager and the zoomable image see the events — so a fast
 * vertical flick is claimed here first instead of being swallowed below. Taps and horizontal
 * drags are never consumed, so they pass through untouched.
 */
fun Modifier.viewerDrag(
    motion: ViewerMotion,
    enabled: () -> Boolean,
    dismissDistance: () -> Float,
    onDismiss: (Velocity) -> Unit,
): Modifier = pointerInput(motion) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()
        tracker.addPointerInputChange(down)
        var total = Offset.Zero
        var active = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (event.changes.count { it.pressed } > 1) {
                // A second finger means pinch-zoom; hand it over.
                if (active && motion.onDragStop(Velocity.Zero, dismissDistance())) onDismiss(Velocity.Zero)
                break
            }
            tracker.addPointerInputChange(change)
            if (!change.pressed) {
                if (active) {
                    val v = tracker.calculateVelocity()
                    if (motion.onDragStop(v, dismissDistance())) onDismiss(v)
                }
                break
            }
            val delta = change.positionChange()
            if (active) {
                motion.onDrag(delta)
                change.consume()
                continue
            }
            // Something above us claimed it (e.g. the details sheet scrolling).
            if (change.isConsumed) break
            total += delta
            if (abs(total.y) > slop && abs(total.y) > abs(total.x)) {
                active = true
                motion.onDragStart(down.position, size, total)
                change.consume()
            } else if (abs(total.x) > slop) {
                break
            }
        }
    }
}
