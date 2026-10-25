package com.subhashh.lumen.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.images.version
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.images.ImageKeys
import androidx.annotation.StringRes
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.text.format.DateFormat
import java.util.Locale

/** The Library's zoom levels, from the bottom bar's segmented control. */
enum class ZoomLevel(@StringRes val label: Int) { Years(R.string.main_zoom_years), Months(R.string.main_zoom_months), All(R.string.main_zoom_all) }

/** One Years/Months card: a stretch of the library with a key photo. */
@Immutable
data class Period(
    val title: String,
    val subtitle: String,
    val cover: MediaItem,
    /** Index of the period's first item in the library list. */
    val firstIndex: Int,
    val year: Int,
)

/** Groups [items] (oldest first) into months or years, oldest first, like the grid. */
fun periods(context: Context, items: List<MediaItem>, byYear: Boolean): List<Period> {
    if (items.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val monthFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
    val result = ArrayList<Period>()
    var start = 0
    var currentKey = -1
    fun close(end: Int) {
        val slice = items.subList(start, end)
        val date = Instant.ofEpochMilli(slice.first().takenAt).atZone(zone)
        // Prefer a photo for the cover: video frames are often black or blurry.
        val cover = slice.lastOrNull { !it.isVideo } ?: slice.last()
        val count = slice.size
        result += Period(
            title = if (byYear) date.year.toString()
            else monthFormat.format(date),
            subtitle = context.resources.getQuantityString(R.plurals.main_item_count, count, count),
            cover = cover,
            firstIndex = start,
            year = date.year,
        )
    }
    items.forEachIndexed { i, item ->
        val date = Instant.ofEpochMilli(item.takenAt).atZone(zone)
        val key = if (byYear) date.year else date.year * 12 + date.monthValue
        if (key != currentKey) {
            if (currentKey != -1) close(i)
            currentKey = key
            start = i
        }
    }
    close(items.size)
    return result
}

/**
 * Years or Months: a column of large photo cards, newest at the bottom like the grid. Tapping a
 * card zooms one level in at that period. The caller owns [listState] and its scroll position
 * (it starts each list at the newest card, and jumps Months to a tapped year).
 */
@Composable
fun TimelineScreen(
    periods: List<Period>,
    level: ZoomLevel,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpen: (Period) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val cardHeight = if (level == ZoomLevel.Years) 320.dp else 220.dp

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Sits over the (hidden) All grid: be a touch target so taps never fall through to it.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            items(periods, key = { it.firstIndex }) { period ->
                PeriodCard(period, cardHeight, onClick = { onOpen(period) })
            }
        }
        if (periods.isEmpty()) {
            Text(
                emptyText,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        LibraryHeader(title = stringResource(R.string.main_library), subtitle = "", topPadding = 16.dp, modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun PeriodCard(period: Period, height: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val heightPx = with(LocalDensity.current) { height.roundToPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pressable(onClick, pressedScale = 0.97f)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = remember(period.cover.version, heightPx) {
                ImageRequest.Builder(context)
                    .data(period.cover.uri)
                    .size(widthPx, heightPx)
                    .memoryCacheKey(ImageKeys.card(period.cover, heightPx))
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * 0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
        ) {
            Text(period.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(period.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
    }
}
