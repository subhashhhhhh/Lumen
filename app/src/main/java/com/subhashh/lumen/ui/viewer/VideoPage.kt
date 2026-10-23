package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.media3.common.VideoSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.images.versioned
import com.subhashh.lumen.ui.images.version
import coil3.SingletonImageLoader
import com.subhashh.lumen.ui.images.bestPlaceholderKey

/** The viewer owns a single player; only the current page attaches to it, others show a still frame. */
@Composable
fun VideoPage(
    item: MediaItem,
    player: Player?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Size the video area to the video's shape from the start. Until the player decodes a first
    // frame it doesn't know the shape and lays its surface out full-screen, so the first frame
    // flashed stretched to the screen's shape before snapping to the real one. MediaStore already
    // knows the shape; once the player reports its own, that wins (MediaStore can be wrong).
    val playerAspect = player?.let { rememberVideoAspect(it) }
    val aspect = playerAspect ?: item.aspectRatio()
    val haptics = LocalHapticFeedback.current
    /** The last double-tap skip, for the "−10 s / +10 s" flash; a new id restarts it. */
    var skip by remember { mutableStateOf<Skip?>(null) }
    LaunchedEffect(skip) {
        if (skip != null) {
            delay(600)
            skip = null
        }
    }
    Box(
        modifier
            .fillMaxSize()
            // Double-tap the left or right half to skip back or ahead 10 s, like YouTube. A single
            // tap still shows or hides the controls (a beat later, to tell it from a double).
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        val p = player ?: return@detectTapGestures
                        val back = offset.x < size.width / 2f
                        val end = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        p.seekTo((p.currentPosition + if (back) -SkipMs else SkipMs).coerceIn(0L, end))
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        skip = Skip(back, (skip?.id ?: 0) + 1)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val frame = if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize()
        if (player != null) {
            ContentFrame(
                player = player,
                // TextureView follows every swipe-to-dismiss transform but always shows standard
                // range, squashing HDR video flat. HDR video gets a SurfaceView, the only surface
                // Android shows HDR on; it tracks moves and scaling too (Android 7+).
                surfaceType = if (item.hdrVideo) SURFACE_TYPE_SURFACE_VIEW else SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
                modifier = frame,
                shutter = { StillFrame(item) },
            )
        } else {
            Box(frame) { StillFrame(item) }
        }
        val shown = skip
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn(tween(90)) + scaleIn(tween(160), initialScale = 0.8f),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(if (shown?.back != false) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            Box(
                Modifier
                    .padding(horizontal = 36.dp)
                    .size(96.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (shown?.back != false) "−10 s" else "+10 s",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private data class Skip(val back: Boolean, val id: Int)

private const val SkipMs = 10_000L

private fun MediaItem.aspectRatio(): Float? =
    if (width > 0 && height > 0) width.toFloat() / height else null

/** The playing video's display aspect ratio, once the player knows it. */
@Composable
private fun rememberVideoAspect(player: Player): Float? {
    var aspect by remember(player) { mutableStateOf(player.videoSize.displayAspect()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                aspect = videoSize.displayAspect()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return aspect
}

private fun VideoSize.displayAspect(): Float? {
    if (width <= 0 || height <= 0) return null
    val ratio = width * pixelWidthHeightRatio / height
    // Rotation the decoder didn't apply turns the frame on its side.
    return if (unappliedRotationDegrees % 180 == 90) 1f / ratio else ratio
}

@Composable
private fun StillFrame(item: MediaItem) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(item.version) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .versioned(item)
                // Sharp from the first frame when the open transition already decoded it at
                // screen size; neighbors not decoded yet start from the grid thumbnail.
                .placeholderMemoryCacheKey(bestPlaceholderKey(SingletonImageLoader.get(context), item))
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}
