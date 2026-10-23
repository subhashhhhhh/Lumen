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
import com.subhashh.lumen.ui.images.versioned
import com.subhashh.lumen.ui.images.version
import coil3.SingletonImageLoader
import com.subhashh.lumen.ui.images.bestPlaceholderKey
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
        state = state,
        onClick = { onTap() },
        modifier = modifier.fillMaxSize(),
    )
}
