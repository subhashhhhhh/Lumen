package com.subhashh.lumen.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Android's back gesture while it's still under the thumb (Android 14 and later): how far it
 * has come, 0 to 1, and from which edge. The page being left reads this to pull away as a
 * preview, and goes back to rest if the gesture is given up.
 */
@Stable
class BackGesture {
    var progress by mutableFloatStateOf(0f)
        internal set
    var fromRight by mutableStateOf(false)
        internal set

    /** Eased: most of the movement comes early, then it settles, as the system's own previews do. */
    val eased: Float get() = 1f - (1f - progress) * (1f - progress)
}

/**
 * Back for a page. With a [gesture] it's predictive: the gesture's progress is reported as the
 * thumb moves, [onBack] runs if it's carried through, and the page springs back to rest if it's
 * cancelled. Without one it's a plain back handler. (A phone without the gesture, or with
 * three-button navigation, just gets [onBack].)
 */
@Composable
fun PageBack(enabled: Boolean, gesture: BackGesture?, onBack: () -> Unit) {
    if (gesture == null) {
        BackHandler(enabled, onBack)
        return
    }
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event ->
                gesture.progress = event.progress
                gesture.fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
            }
            back()
            // The page that's now showing arrives at rest.
            gesture.progress = 0f
        } catch (cancelled: CancellationException) {
            scope.launch { animate(gesture.progress, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) { value, _ -> gesture.progress = value } }
            throw cancelled
        }
    }
}
