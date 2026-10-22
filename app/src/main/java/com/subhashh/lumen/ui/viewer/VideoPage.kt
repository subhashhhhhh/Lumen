package com.subhashh.lumen.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import com.subhashh.lumen.ui.images.ImageKeys

/** The viewer owns a single player; only the current page attaches to it, others show a still frame. */
@Composable
fun VideoPage(
    item: MediaItem,
    player: Player?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {
        if (player != null) {
            ContentFrame(
                player = player,
                // TextureView, not SurfaceView, so the frame follows swipe-to-dismiss transforms.
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                shutter = { StillFrame(item) },
            )
        } else {
            StillFrame(item)
        }
    }
}

@Composable
private fun StillFrame(item: MediaItem) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(item.uri) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .memoryCacheKey(ImageKeys.screen(item.id))
                .placeholderMemoryCacheKey(ImageKeys.thumb(item.id))
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}
