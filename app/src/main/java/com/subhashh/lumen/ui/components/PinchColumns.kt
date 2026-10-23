package com.subhashh.lumen.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Two-finger pinch changes a grid's column count, like iOS Photos: spreading zooms in (fewer
 * columns), pinching zooms out. One-finger drags pass through to the grid; once a second finger
 * lands we consume events before the grid sees them, so it doesn't scroll.
 *
 * @param steps column counts, from most zoomed in to most zoomed out
 */
@Composable
fun Modifier.pinchColumns(steps: List<Int>, columns: Int, onColumnsChange: (Int) -> Unit): Modifier {
    // Nearest step, in case the saved value isn't one of today's steps.
    val step = steps.indices.minBy { kotlin.math.abs(steps[it] - columns) }
    // The pinch handler is set up once; it must read the current step, not the first one.
    val currentStep by rememberUpdatedState(step)
    val currentSteps by rememberUpdatedState(steps)
    val currentOnChange by rememberUpdatedState(onColumnsChange)
    val haptics = LocalHapticFeedback.current
    val moveBy = { delta: Int ->
        val next = currentStep + delta
        if (next in currentSteps.indices) {
            currentOnChange(currentSteps[next])
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // One column step per 1.25× of pinch, so a small pinch moves one step and a big
            // one can move several in the same gesture.
            var zoom = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    if (zoom > PinchStep) {
                        moveBy(-1); zoom = 1f
                    } else if (zoom < 1f / PinchStep) {
                        moveBy(1); zoom = 1f
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

private const val PinchStep = 1.25f
