package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.snap
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import com.subhashh.lumen.ui.icons.LumenIcons
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
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
    /** Pinch-zoomed in: the pager and swipe-to-dismiss stay still meanwhile. */
    onZoomedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Size the video area to the video's shape from the start. Until the player decodes a first
    // frame it doesn't know the shape and lays its surface out full-screen, so the first frame
    // flashed stretched to the screen's shape before snapping to the real one. MediaStore already
    // knows the shape; once the player reports its own, that wins (MediaStore can be wrong).
    val playerAspect = player?.let { rememberVideoAspect(it) }
    val aspect = playerAspect ?: item.aspectRatio()
    val haptics = LocalHapticFeedback.current
    /** The last double-tap, for its flash ("−10 s", play, pause…); a new id restarts it. */
    var flash by remember { mutableStateOf<Flash?>(null) }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(600)
            flash = null
        }
    }
    // Pinch to zoom, as for photos. Double-tap doesn't zoom here: the middle plays or pauses,
    // the outer edges skip back or ahead 10 s, like YouTube. A single tap still shows or hides
    // the controls.
    val zoom = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
    var page by remember { mutableStateOf(IntSize.Zero) }
    // Where the video sits on the page: its real fitted size, so zoom starts at 1× and panning
    // stops at the picture's edges, not the black bars'.
    LaunchedEffect(aspect, page) {
        if (aspect == null || page.width == 0 || page.height == 0) return@LaunchedEffect
        val fitted = if (aspect >= page.width.toFloat() / page.height) {
            Size(page.width.toFloat(), page.width / aspect)
        } else {
            Size(page.height * aspect, page.height.toFloat())
        }
        zoom.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(fitted))
    }
    LaunchedEffect(player != null, zoom) {
        // Only the page with the player (the one on screen) locks the pager.
        if (player == null) return@LaunchedEffect
        snapshotFlow { (zoom.zoomFraction ?: 0f) > 0.01f }.collect(onZoomedChange)
    }
    // Leaving the page (another becomes current) zooms back out.
    LaunchedEffect(player == null) { if (player == null) zoom.resetZoom(animationSpec = snap()) }
    val doubleTap = remember(player) {
        object : DoubleClickToZoomListener {
            override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
                val p = player ?: return
                val width = page.width
                val edge = width * EdgeShare
                val kind = when {
                    centroid.x < edge -> FlashKind.Back
                    centroid.x > width - edge -> FlashKind.Ahead
                    p.isPlaying -> FlashKind.Pause
                    else -> FlashKind.Play
                }
                when (kind) {
                    FlashKind.Back, FlashKind.Ahead -> {
                        val end = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        p.seekTo((p.currentPosition + if (kind == FlashKind.Back) -SkipMs else SkipMs).coerceIn(0L, end))
                    }
                    FlashKind.Pause -> p.pause()
                    FlashKind.Play -> {
                        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                        p.play()
                    }
                }
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                flash = Flash(kind, (flash?.id ?: 0) + 1)
            }
        }
    }
    Box(modifier.fillMaxSize().onSizeChanged { page = it }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .zoomable(zoom, onClick = { onTap() }, onDoubleClick = doubleTap),
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
        }
        // Remembered while it fades out, so it doesn't jump to the middle as it goes.
        var shown by remember { mutableStateOf(FlashKind.Play) }
        flash?.let { shown = it.kind }
        AnimatedVisibility(
            visible = flash != null,
            enter = fadeIn(tween(90)) + scaleIn(tween(160), initialScale = 0.8f),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(
                when (shown) {
                    FlashKind.Back -> Alignment.CenterStart
                    FlashKind.Ahead -> Alignment.CenterEnd
                    else -> Alignment.Center
                },
            ),
        ) {
            Box(
                Modifier
                    .padding(horizontal = 36.dp)
                    .size(96.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (shown) {
                    FlashKind.Back, FlashKind.Ahead -> Text(
                        if (shown == FlashKind.Back) "−10 s" else "+10 s",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    else -> Icon(
                        if (shown == FlashKind.Pause) LumenIcons.Pause else LumenIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

private enum class FlashKind { Back, Ahead, Play, Pause }

private data class Flash(val kind: FlashKind, val id: Int)

/** Share of the width at each side where a double-tap skips instead of playing or pausing. */
private const val EdgeShare = 0.28f

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
