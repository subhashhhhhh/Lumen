package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * iOS-style video scrubber: a thin bar with no thumb that thickens while touched. Tap or drag
 * anywhere on it to seek. [onScrub] reports a 0..1 fraction; [onScrubEnd] fires on release.
 */
@Composable
fun Scrubber(
    progress: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf(false) }
    val barHeight by animateDpAsState(if (active) 10.dp else 5.dp, label = "scrubberHeight")

    Box(
        modifier
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    active = true
                    onScrub((down.position.x / size.width).coerceIn(0f, 1f))
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (change.pressed) {
                            onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    active = false
                    onScrubEnd()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(color),
            )
        }
    }
}
