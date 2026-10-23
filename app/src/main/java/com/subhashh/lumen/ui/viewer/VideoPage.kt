package com.subhashh.lumen.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
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
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val frame = if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize()
        if (player != null) {
            ContentFrame(
                player = player,
                // TextureView, not SurfaceView, so the frame follows swipe-to-dismiss transforms.
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
                modifier = frame,
                shutter = { StillFrame(item) },
            )
        } else {
            Box(frame) { StillFrame(item) }
        }
    }
}

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
        model = remember(item.uri) {
            ImageRequest.Builder(context)
                .data(item.uri)
                // Sharp from the first frame when the open transition already decoded it at
                // screen size; neighbors not decoded yet start from the grid thumbnail.
                .placeholderMemoryCacheKey(bestPlaceholderKey(SingletonImageLoader.get(context), item.id))
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}
