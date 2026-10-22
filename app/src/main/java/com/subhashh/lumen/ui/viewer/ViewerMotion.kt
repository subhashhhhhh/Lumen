package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The viewer's vertical gestures: pull up for the details sheet, pull down to dismiss.
 *
 * Positions are plain state written synchronously on every drag event, so the photo stays glued
 * to the finger; only the release starts an animation. (Launching a coroutine per drag event let
 * stale updates cancel the settle and leave the sheet stuck half open.)
 */
@Stable
class ViewerMotion(
    private val scope: CoroutineScope,
    /** Release speed, px/s, that counts as a flick regardless of position. */
    private val flingVelocity: Float,
) {
    /** Downward drag of the photo toward dismissal, px. */
    var dismiss by mutableFloatStateOf(0f)
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

    fun onDragStart() {
        settle?.cancel()
        mode = Mode.None
        startReveal = reveal
        startNanos = System.nanoTime()
    }

    fun onDrag(deltaY: Float) {
        if (mode == Mode.None) {
            // Up from rest, or any drag while the sheet is out, moves the sheet.
            mode = if (reveal > 0f || deltaY < 0f) Mode.Reveal else Mode.Dismiss
        }
        when (mode) {
            Mode.Reveal -> reveal = (reveal - deltaY).coerceIn(0f, sheetHeight)
            Mode.Dismiss -> dismiss += deltaY
            Mode.None -> Unit
        }
    }

    /**
     * @param velocityY finger speed as it lifted (px/s, down positive)
     * @return true when the gesture asks to dismiss the viewer
     */
    fun onDragStop(velocityY: Float, dismissDistance: Float): Boolean {
        when (mode) {
            Mode.Reveal -> settleReveal(releaseSpeed(velocityY))
            Mode.Dismiss -> {
                if (abs(dismiss) > dismissDistance || abs(velocityY) > flingVelocity * 2f) return true
                springBack(velocityY)
            }
            Mode.None -> Unit
        }
        mode = Mode.None
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

    private fun springBack(velocityY: Float) {
        settle?.cancel()
        settle = scope.launch {
            animate(dismiss, 0f, velocityY, SnapBackSpring) { value, _ -> dismiss = value }
        }
    }

    private companion object {
        /** No bounce, ~0.4 s response: how iOS sheets settle. */
        val SheetSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 250f)

        /** A touch of overshoot when a short dismiss drag snaps back. */
        val SnapBackSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    }
}
