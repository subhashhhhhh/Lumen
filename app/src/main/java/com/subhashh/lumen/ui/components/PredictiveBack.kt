package com.subhashh.lumen.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Going back from one page to the one under it, as it happens. While Android's back gesture is
 * under the thumb, [progress] is how far it has come (0 to 1) and [fromRight] the edge it came
 * from; once it's let go, [commit] runs 0 to 1 as the top page leaves. The two pages read these
 * to draw the preview: the top one pulling away, the one beneath coming forward. (The direction
 * doesn't follow [fromRight]: like Android's own animation, a page always leaves the same way.)
 */
@Stable
class BackGesture {
    var progress by mutableFloatStateOf(0f)
        internal set
    var fromRight by mutableStateOf(false)
        internal set
    var commit by mutableFloatStateOf(0f)
        internal set

    /** A gesture is under way or the top page is leaving: the page beneath must be there to see. */
    var active by mutableStateOf(false)
        internal set

    /** Set as a back completes, for whoever animates new pages in: this one was already on screen. */
    var arrived = false

    /** Eased: most of the movement comes early, then it settles, as the system's own previews do. */
    val eased: Float get() = 1f - (1f - progress) * (1f - progress)
}

/**
 * Back for a page. With a [gesture] it's predictive: progress is reported as the thumb moves,
 * the top page then leaves (animated), and [onBack] runs; if the gesture is given up everything
 * springs back to rest. [onBack] is told whether the page was animated away here, or whether it
 * should do its own leaving (a plain back press when [animatePress] is off). Without a
 * [gesture] it's a plain back: nothing is drawn, [onBack] does it all.
 */
@Composable
fun PageBack(
    enabled: Boolean,
    gesture: BackGesture?,
    /** A back press with no gesture (three-button navigation) also plays the leaving animation. */
    animatePress: Boolean = true,
    onBack: (animated: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    val press by rememberUpdatedState(animatePress)
    val latest by rememberUpdatedState(gesture)
    // A given-up gesture springing back to rest: stopped if the next one starts before it's done.
    val settling = remember { arrayOfNulls<Job>(1) }
    // One handler whether or not it's predictive, so it keeps its place among the others (a menu
    // opened later still gets Back first) when [gesture] comes or goes.
    PredictiveBackHandler(enabled) { events ->
        val gesture = latest
        if (gesture == null) {
            events.collect { }
            back(false)
            return@PredictiveBackHandler
        }
        var moved = false
        try {
            events.collect { event ->
                if (!moved) settling[0]?.cancel()
                moved = true
                gesture.active = true
                gesture.progress = event.progress
                gesture.fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
            }
        } catch (cancelled: CancellationException) {
            settling[0] = scope.launch {
                animate(gesture.progress, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) { value, _ -> gesture.progress = value }
                gesture.active = false
            }
            throw cancelled
        }
        settling[0]?.cancel()
        val animated = moved || press
        try {
            if (animated) {
                gesture.active = true
                animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { value, _ -> gesture.commit = value }
                gesture.arrived = true
            }
            // The page beneath becomes the page, at rest, in the same frame the top one goes.
            back(animated)
        } finally {
            // Also when the leaving was cut short: never left half way, the page beneath showing.
            gesture.progress = 0f
            gesture.commit = 0f
            gesture.active = false
        }
        // Long enough for the page beneath to have been seen arriving, then it no longer applies.
        try {
            kotlinx.coroutines.delay(120)
        } finally {
            gesture.arrived = false
        }
    }
}
