package com.subhashh.lumen.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.collections.AlbumCard
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.images.ImageKeys
import com.subhashh.lumen.ui.library.Thumbnail
import com.subhashh.lumen.ui.library.ColumnSteps
import com.subhashh.lumen.ui.components.pinchColumns
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.transitionCell

/**
 * Search results under the glass search field (which lives outside the page layer, see
 * LumenApp): matching albums in a row, then matching photos and videos, newest and best first.
 * With nothing typed it offers a few starting points.
 */
@Composable
fun SearchScreen(
    query: String,
    /** null while the index is being built. */
    results: List<MediaItem>?,
    /** Smart Search is still working on this query; its matches will follow these. */
    lookingInside: Boolean,
    albums: List<MediaCollection>,
    suggestions: List<String>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    transition: PhotoTransition,
    onOpen: (index: Int) -> Unit,
    onOpenAlbum: (key: String) -> Unit,
    onSuggestion: (String) -> Unit,
    /** Picking several: ticks on what's chosen. */
    picked: Set<Long>?,
    /** Columns, saved separately from the Library's; pinch changes them. */
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    // Scrolling the results puts the keyboard away, like iOS.
    val hideKeyboard = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    val cellPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() } / columns
    val bucket = ImageKeys.thumbBucket(cellPx)

    if (query.isBlank()) {
        Suggestions(suggestions, contentPadding, onSuggestion, modifier)
        return
    }
    val photos = results.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(hideKeyboard)
            .pinchColumns(ColumnSteps, columns, onColumnsChange),
    ) {
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Albums") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(albums, key = { it.key }) { album ->
                        Box(Modifier.width(120.dp)) { AlbumCard(album, onClick = { onOpenAlbum(album.key) }) }
                    }
                }
            }
        }
        if (results != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(
                    when {
                        lookingInside && photos.isEmpty() -> "Looking inside photos…"
                        photos.isEmpty() -> "No photos or videos"
                        photos.size == 1 -> "1 Result"
                        else -> "${photos.size} Results"
                    },
                    detail = if (lookingInside && photos.isNotEmpty()) "Looking inside photos…" else null,
                )
            }
        }
        itemsIndexed(photos, key = { _, item -> item.id }) { index, item ->
            Thumbnail(
                item = item,
                bucket = bucket,
                fit = false,
                showDuration = columns <= 5,
                selected = picked?.let { item.id in it },
                onClick = {
                    keyboard?.hide()
                    onOpen(index)
                },
                modifier = Modifier.transitionCell(transition, item.id),
            )
        }
        if (results != null && photos.isEmpty() && albums.isEmpty() && !lookingInside) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Try describing what's in the photo, like “cat on a sofa” (with Smart Search in " +
                        "Settings), or a file name, a folder, a month or a year.",
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, detail: String? = null) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(detail, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

/** Starting points while nothing is typed: tap one to search for it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    suggestions: List<String>,
    contentPadding: PaddingValues,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    Box(modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Search",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            Text(
                "Try",
                fontSize = 15.sp,
                color = onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    Row(
                        Modifier
                            .pressable({ onSuggestion(suggestion) }, pressedScale = 0.94f)
                            .clip(CircleShape)
                            .background(onBackground.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            LumenIcons.Search,
                            contentDescription = null,
                            tint = onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(suggestion, fontSize = 15.sp, color = onBackground)
                    }
                }
            }
        }
    }
}
