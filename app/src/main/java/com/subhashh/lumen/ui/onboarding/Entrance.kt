package com.subhashh.lumen.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

/** One photo of onboarding's last screen as Let's Go was tapped: where, and how tilted. */
class ExitTile(val item: MediaItem, val rect: Rect, val rotation: Float)

/** Onboarding's last frame, handed to the Library to fly out of: its photos and the tap. */
class OnboardingExit(val tiles: List<ExitTile>, val tap: Offset, val background: Color)

/**
 * Into the Library: every photo from onboarding's last screen flies to its own cell in the grid
 * (laid out underneath already), straightening as it lands, nearest the tap first so the grid
 * fills in like a wave; the dark backdrop fades to reveal the Library. [cellOf] is where a photo's
 * grid cell is (root coordinates), or null when it's off screen: that photo fades on its way.
 */
@Composable
fun Entrance(exit: OnboardingExit, cellOf: (MediaItem) -> Rect?, onDone: () -> Unit) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val progress = remember(exit) { exit.tiles.map { Animatable(0f) } }
    val backdrop = remember(exit) { Animatable(1f) }
    val targets = remember(exit) { arrayOfNulls<Rect>(exit.tiles.size) }
    LaunchedEffect(exit) {
        // The Library lays its grid out underneath and scrolls to the newest: wait until most of
        // the photos have their cell (a few frames), no longer than ~0.2 s.
        for (frame in 0 until 12) {
            withFrameNanos { }
            exit.tiles.forEachIndexed { i, t -> targets[i] = cellOf(t.item) }
            if (targets.count { it != null } >= exit.tiles.size * 3 / 4) break
        }
        val far = exit.tiles.maxOfOrNull { hypot(it.rect.center.x - exit.tap.x, it.rect.center.y - exit.tap.y) }?.coerceAtLeast(1f) ?: 1f
        coroutineScope {
            val fade = async {
                delay(260)
                backdrop.animateTo(0f, tween(520))
            }
            exit.tiles.mapIndexed { i, t ->
                async {
                    // A wave out from the finger: the farthest leave ~0.22 s after the nearest.
                    delay((220 * hypot(t.rect.center.x - exit.tap.x, t.rect.center.y - exit.tap.y) / far).toLong())
                    progress[i].animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
                }
            }.awaitAll()
            fade.await()
        }
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawRect(exit.background.copy(alpha = backdrop.value)) }
        for ((i, t) in exit.tiles.withIndex()) {
            val p = progress[i].value
            // Where its cell is now: the grid may still be settling, so follow it every frame.
            val to = cellOf(t.item) ?: targets[i]
            // No cell on screen: drift down a little and fade instead of landing.
            val end = to ?: t.rect.translate(0f, t.rect.height)
            val rect = Rect(
                lerp(t.rect.left, end.left, p),
                lerp(t.rect.top, end.top, p),
                lerp(t.rect.right, end.right, p),
                lerp(t.rect.bottom, end.bottom, p),
            )
            val corner = with(density) { lerp(10.dp.toPx(), 0f, p).coerceAtLeast(0f).toDp() }
            AsyncImage(
                model = remember(t.item.id) { mosaicRequest(context, t.item) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                    .graphicsLayer {
                        rotationZ = lerp(t.rotation, 0f, p)
                        alpha = if (to == null) 1f - p else 1f
                    }
                    .clip(RoundedCornerShape(corner)),
            )
        }
    }
}
