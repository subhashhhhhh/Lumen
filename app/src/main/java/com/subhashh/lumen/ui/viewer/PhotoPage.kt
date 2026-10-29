package com.subhashh.lumen.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    /** Live Text for this page, when it's the one on screen. */
    liveText: LiveTextState? = null,
    /**
     * Touch and hold away from text (Cutout): the press as fractions of the photo's width and
     * height, the press on screen, and where the photo sits on screen.
     */
    onLongPress: ((fraction: Offset, at: Offset, bounds: Rect) -> Unit)? = null,
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

    val haptics = LocalHapticFeedback.current
    // The zoomable image keeps the click handlers it was first given. A neighbour is built off
    // screen, before it's the current page (no Live Text), and kept as you swipe to it, so its
    // handlers read these instead of capturing them; otherwise long-press did nothing there
    // until the viewer was opened on that photo.
    val currentLiveText by rememberUpdatedState(liveText)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val doubleClick = remember { FillOnDoubleClick { viewport } }
    Box(modifier.fillMaxSize().onSizeChanged { viewport = it }) {
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
        onClick = { p ->
            val bounds = state.zoomableState.transformedContentBounds
            val live = currentLiveText
            when {
                // A tap away from a selection drops it, like iOS.
                live?.selection != null -> live.clear()
                live?.showAll == true && live.openAt(context, bounds, p) -> Unit
                else -> currentOnTap()
            }
        },
        onDoubleClick = doubleClick,
        // Long-press on text selects the word there; anywhere else lifts the subject out.
        onLongClick = { p ->
            val bounds = state.zoomableState.transformedContentBounds
            if (currentLiveText?.selectAt(bounds, p) == true) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } else if (bounds.contains(p) && bounds.width > 0f && bounds.height > 0f) {
                currentOnLongPress?.let { press ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    press(Offset((p.x - bounds.left) / bounds.width, (p.y - bounds.top) / bounds.height), p, bounds)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    if (liveText != null && liveText.item?.id == item.id) {
        LiveTextLayer(liveText, state.zoomableState.transformedContentBounds)
    }
    }
}
