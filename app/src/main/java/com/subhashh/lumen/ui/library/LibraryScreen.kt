package com.subhashh.lumen.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grid densities, from most zoomed in to most zoomed out. */
private val ColumnSteps = listOf(1, 3, 5, 9)
private const val DefaultColumnStep = 2

@Composable
fun LibraryScreen(
    items: List<MediaItem>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(DefaultColumnStep) }
    val columns = ColumnSteps[step]
    val gridState = rememberLazyGridState()

    // iOS Photos opens at the newest photo, which sits at the bottom of the grid.
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) gridState.scrollToItem(items.lastIndex)
    }

    val visibleMonth by remember(items) {
        derivedStateOf {
            val info = gridState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) return@derivedStateOf ""
            val middle = info[info.size / 2].index
            items.getOrNull(middle)?.let { MonthFormat.format(Date(it.takenAt)) }.orEmpty()
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .pinchToChangeColumns(
                    onZoomIn = { if (step > 0) step-- },
                    onZoomOut = { if (step < ColumnSteps.lastIndex) step++ },
                ),
        ) {
            items(items, key = { it.id }) { item ->
                Thumbnail(item, showDuration = columns <= 5)
            }
        }

        LibraryHeader(
            subtitle = visibleMonth,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun Thumbnail(item: MediaItem, showDuration: Boolean) {
    val context = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = remember(item.uri) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .memoryCacheKey("thumb:${item.id}")
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo && showDuration) {
            Text(
                text = formatDuration(item.durationMs),
                style = OverlayText,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun LibraryHeader(subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Text("Library", style = OverlayText.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = OverlayText.copy(fontSize = 15.sp, color = Color(0xCCFFFFFF)))
        }
    }
}

/**
 * Two-finger pinch changes grid density, like iOS Photos. One-finger drags pass through to the
 * grid; once a second finger lands we consume events before the grid sees them, so it doesn't scroll.
 */
private fun Modifier.pinchToChangeColumns(onZoomIn: () -> Unit, onZoomOut: () -> Unit) =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            var handled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    if (!handled && zoom > 1.25f) {
                        onZoomIn(); handled = true
                    } else if (!handled && zoom < 0.8f) {
                        onZoomOut(); handled = true
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private val MonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

private val OverlayText = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f),
)

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
