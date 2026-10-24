package com.subhashh.lumen.ui.library

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.subhashh.lumen.ui.selection.dragToSelect
import com.subhashh.lumen.ui.components.pinchColumns
import com.subhashh.lumen.ui.images.version
import com.subhashh.lumen.ui.icons.LumenIcons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.ui.images.bestPlaceholderKey
import coil3.SingletonImageLoader
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.isRaw
import com.subhashh.lumen.ui.images.ImageKeys
import com.subhashh.lumen.data.GridThumbnail
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.transitionCell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grid densities, from most zoomed in to most zoomed out. */
internal val ColumnSteps = listOf(1, 2, 3, 4, 5, 6, 8)
private const val DefaultColumnStep = 4 // 5 columns

@Composable
fun LibraryScreen(
    title: String,
    items: List<MediaItem>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    transition: PhotoTransition,
    onOpen: (index: Int) -> Unit,
    /** Selected ids while selecting; null when not in selection mode. */
    selection: Set<Long>?,
    onSelectionChange: (Set<Long>?) -> Unit,
    modifier: Modifier = Modifier,
    /** Extra space above the title, e.g. for an album's back button. */
    titleTopPadding: Dp = 16.dp,
    /** Whole photos letterboxed in their cells instead of square crops. */
    aspectRatioGrid: Boolean = false,
    emptyText: String = "No photos or videos",
    /** Columns, saved by the caller so the choice survives restarts. */
    columns: Int = ColumnSteps[DefaultColumnStep],
    onColumnsChange: (Int) -> Unit = {},
    /** Open scrolled to the end (newest at the bottom, the default order) rather than the top. */
    startAtEnd: Boolean = true,
    /** The month under the title; meaningless when sorted by name or size. */
    showMonth: Boolean = true,
    /** What the fast-scroll handle says for an item: its month by default. */
    scrollLabel: (MediaItem) -> String = { MonthFormat.format(Date(it.takenAt)) },
    /** Date headers mixed into the grid; none when null. Callers scrolling the grid use its [GridSections.gridIndexOf]. */
    sections: GridSections? = null,
    /** Settings → Library → Curved Edge Display. */
    curvedEdge: Boolean = false,
    /**
     * An album's or person's cover across the top, scrolling with the grid; [contentPadding]'s
     * top should leave [heroHeight] for it. The title shows once it's scrolled away.
     */
    hero: (@Composable (Modifier) -> Unit)? = null,
    heroHeight: Dp = 0.dp,
) {
    val grid = sections ?: remember(items) { GridSections.of(items, SectionMode.None) }
    val entries = grid.entries
    val haptics = LocalHapticFeedback.current
    // Decode thumbnails for the cell size actually shown, so fewer, bigger cells stay sharp.
    val cellPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() } / columns
    val thumbBucket = ImageKeys.thumbBucket(cellPx)

    // iOS Photos opens at the newest photo, which sits at the bottom of the grid. Only on first
    // load — later refreshes (a deletion, returning to the app) must keep the scroll position.
    var scrolledToNewest by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && !scrolledToNewest) {
            gridState.scrollToItem(if (startAtEnd) entries.lastIndex else 0)
            scrolledToNewest = true
        }
    }

    val visibleMonth by remember(grid) {
        derivedStateOf {
            val info = gridState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) return@derivedStateOf ""
            val middle = info[info.size / 2].index
            grid.itemAt(middle)?.let { MonthFormat.format(Date(it.takenAt)) }.orEmpty()
        }
    }

    val density = LocalDensity.current
    val edgeTop = with(density) { contentPadding.calculateTopPadding().toPx() }
    val edgeBottom = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val currentSelection by rememberUpdatedState(selection)
    // The release of a long-press reaches the cell's tap handler before the drag handler ends
    // the gesture; without this it would count as a tap and undo the selection it just made.
    val longPressActive = remember { booleanArrayOf(false) }

    Box(modifier.fillMaxSize()) {
        if (hero != null) {
            val heroPx = with(density) { heroHeight.roundToPx() }
            // Behind the grid, moved by the grid's scroll: no extra grid item, so grid indices
            // (drag-select, the viewer's close flight) stay those of the photos.
            hero(
                Modifier
                    .height(heroHeight)
                    .offset {
                        val first = gridState.firstVisibleItemIndex == 0
                        IntOffset(0, if (first) -gridState.firstVisibleItemScrollOffset else -heroPx)
                    },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .pinchColumns(ColumnSteps, columns, onColumnsChange)
                .dragToSelect(
                    gridState = gridState,
                    // Headers have no id: a range drag skips them.
                    ids = { entries.map { (it as? GridEntry.Cell)?.item?.id } },
                    selection = { currentSelection.orEmpty() },
                    onSelectionChange = onSelectionChange,
                    onStart = {
                        longPressActive[0] = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onEnd = { longPressActive[0] = false },
                    edgeTop = edgeTop,
                    edgeBottom = edgeBottom,
                ),
        ) {
            items(
                count = entries.size,
                key = { i ->
                    when (val entry = entries[i]) {
                        is GridEntry.Header -> "h${entry.key}"
                        is GridEntry.Cell -> entry.item.id
                    }
                },
                span = { i -> if (entries[i] is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { i -> if (entries[i] is GridEntry.Header) 0 else 1 },
            ) { i ->
                val entry = entries[i]
                if (entry is GridEntry.Header) {
                    SectionHeader(entry.label)
                    return@items
                }
                val cell = entry as GridEntry.Cell
                val item = cell.item
                val index = cell.itemIndex
                Thumbnail(
                    item = item,
                    bucket = thumbBucket,
                    fit = aspectRatioGrid,
                    showDuration = columns <= 5 && selection == null,
                    selected = selection?.let { item.id in it },
                    onClick = click@{
                        if (longPressActive[0]) return@click
                        val current = selection
                        if (current == null) {
                            onOpen(index)
                        } else {
                            onSelectionChange(if (item.id in current) current - item.id else current + item.id)
                        }
                    },
                    modifier = Modifier.transitionCell(transition, item.id),
                )
            }
        }

        if (items.isEmpty()) {
            Text(
                emptyText,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        FastScroller(
            gridState = gridState,
            itemCount = entries.size,
            label = { index -> grid.itemAt(index)?.let(scrollLabel).orEmpty() },
            topInset = contentPadding.calculateTopPadding(),
            bottomInset = contentPadding.calculateBottomPadding(),
            modifier = Modifier.align(Alignment.TopEnd),
            curvedEdge = curvedEdge,
        )

        val collapseFrom = with(density) { (heroHeight - 240.dp).toPx() }
        val collapseOver = with(density) { 80.dp.toPx() }
        LibraryHeader(
            title = title,
            subtitle = if (showMonth) visibleMonth else "",
            topPadding = titleTopPadding,
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                    // Under a cover, the title (with its scrim) arrives as the cover leaves; while
                    // selecting it's needed for the count.
                    alpha = when {
                        hero == null || currentSelection != null -> 1f
                        gridState.firstVisibleItemIndex > 0 -> 1f
                        else -> ((gridState.firstVisibleItemScrollOffset - collapseFrom) / collapseOver).coerceIn(0f, 1f)
                    }
                },
        )
    }
}

/** A date header across the grid: "October 2026", "Yesterday". */
@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Thumbnail(
    item: MediaItem,
    bucket: Int,
    fit: Boolean,
    showDuration: Boolean,
    /** null when not selecting. */
    selected: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Selected cells shrink a little inside their slot, like iOS.
    val scale by animateFloatAsState(
        if (selected == true) 0.86f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "selectScale",
    )
    Box(
        modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                // Letterboxed cells stay clear around the photo; square crops get a loading tint.
                .then(if (fit) Modifier else Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))),
        ) {
            AsyncImage(
                model = remember(item.version, bucket) {
                    ImageRequest.Builder(context)
                        // Saved on disk after the first time (see GridThumbnail), so a relaunch
                        // doesn't decode originals again.
                        .data(GridThumbnail(item, bucket))
                        .size(bucket, bucket)
                        .memoryCacheKey(ImageKeys.thumb(item, bucket))
                        // While a bigger size decodes after a pinch, keep showing the one we have.
                        .placeholderMemoryCacheKey(bestPlaceholderKey(SingletonImageLoader.get(context), item))
                        .build()
                },
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.mimeType == "image/gif") {
                // GIFs play in their cells, like Fossify and Aves: decoded at cell size over the
                // cached still, which stays visible until the first frame is ready.
                AsyncImage(
                    model = remember(item.version, bucket) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(bucket, bucket)
                            .memoryCacheKey("anim:${item.version}:$bucket")
                            .build()
                    },
                    contentDescription = null,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showDuration && (item.isVideo || item.mimeType == "image/gif" || item.isRaw)) {
                Text(
                    // GIFs move and RAW files are special; say so, as iOS does.
                    text = when {
                        item.isVideo -> formatDuration(item.durationMs)
                        item.isRaw -> "RAW"
                        else -> "GIF"
                    },
                    style = OverlayText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                )
            }
            if (item.isFavorite && showDuration) {
                // A small white heart, bottom-left, like iOS Photos.
                Icon(
                    LumenIcons.HeartFilled,
                    contentDescription = "Favorite",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .size(14.dp)
                        .shadow(3.dp, CircleShape, clip = false),
                )
            }
        }
        if (selected != null) SelectionMark(selected, Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

/** iOS-style marker: an empty ring when selectable, a filled check when selected. */
@Composable
internal fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(
                LumenIcons.Selected,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Icon(
            LumenIcons.Unselected,
            contentDescription = null,
            tint = Color.White,
            modifier = modifier
                .size(22.dp)
                .shadow(2.dp, CircleShape, clip = false),
        )
    }
}

@Composable
internal fun LibraryHeader(title: String, subtitle: String, topPadding: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            // Stays dark behind the title and subtitle, then eases out well below them. An
            // eased curve rather than a linear one: a linear fade is already thin at the text
            // and leaves a visible edge where it ends.
            .background(Brush.verticalGradient(*HeaderScrim))
            .statusBarsPadding()
            .padding(top = topPadding, start = 20.dp, end = 20.dp, bottom = 72.dp),
    ) {
        Text(title, style = OverlayText.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = OverlayText.copy(fontSize = 15.sp, color = Color(0xCCFFFFFF)))
        }
    }
}

private val HeaderScrim = arrayOf(
    0.00f to Color.Black.copy(alpha = 0.70f),
    0.45f to Color.Black.copy(alpha = 0.58f),
    0.62f to Color.Black.copy(alpha = 0.40f),
    0.78f to Color.Black.copy(alpha = 0.18f),
    0.90f to Color.Black.copy(alpha = 0.06f),
    1.00f to Color.Transparent,
)

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
