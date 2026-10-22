package com.subhashh.lumen.ui.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.images.ImageKeys
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun PhotoPage(
    item: MediaItem,
    isCurrent: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = rememberZoomableImageState(
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)),
    )

    // Only the visible page decides whether the pager and swipe-to-dismiss are locked.
    LaunchedEffect(isCurrent, state) {
        if (!isCurrent) return@LaunchedEffect
        snapshotFlow { (state.zoomableState.zoomFraction ?: 0f) > 0.01f }
            .collect(onZoomedChange)
    }

    ZoomableAsyncImage(
        model = remember(item.uri) {
            ImageRequest.Builder(context)
                .data(item.uri)
                // The open transition already decoded this at screen size under the same key, so
                // the photo is sharp the moment the viewer appears; neighbors fall back to the
                // grid thumbnail while they decode.
                .memoryCacheKey(ImageKeys.screen(item.id))
                .placeholderMemoryCacheKey(ImageKeys.thumb(item.id))
                .build()
        },
        contentDescription = null,
        state = state,
        onClick = { onTap() },
        modifier = modifier.fillMaxSize(),
    )
}
