package com.subhashh.lumen.ui.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.collections.AlbumCard
import com.subhashh.lumen.ui.collections.ScreenTitle
import com.subhashh.lumen.ui.collections.rememberAlbumGridGestures

/** A folder in Hidden: hidden as a whole, or a normal folder holding some hidden files. */
@Immutable
data class HiddenFolder(val collection: MediaCollection, val wholeFolder: Boolean)

/** Groups hidden items by folder, most recently changed first. Touches the disk: call off the main thread. */
fun hiddenFolders(items: List<MediaItem>): List<HiddenFolder> =
    items.groupBy { it.bucketId }
        .map { (bucketId, folderItems) ->
            val folder = HiddenMedia.file(folderItems.last())?.parentFile
            HiddenFolder(
                collection = MediaCollection(
                    key = CollectionKeys.hiddenFolder(bucketId),
                    title = folderItems.last().bucketName,
                    cover = folderItems.last(),
                    count = folderItems.size,
                ),
                wholeFolder = folder != null && HiddenMedia.isHiddenFolder(folder),
            )
        }
        .sortedByDescending { it.collection.cover?.takenAt }

/**
 * Hidden's top level: every folder with hidden content as an album. Whole hidden folders
 * (.nomedia, dot-names) show everything in them; normal folders like Download show only their
 * hidden files. [folders] is null while the storage scan runs.
 */
@Composable
fun HiddenScreen(
    folders: List<HiddenFolder>?,
    gridState: LazyGridState,
    /** Folders left out of Hidden (relative paths), listed at the bottom to bring back. */
    excluded: List<String>,
    onInclude: (folder: String) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    /** "Hidden", or the selection count while selecting. */
    title: String,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    /** Selected folder keys while selecting; null when not in selection mode. */
    selection: Set<String>?,
    onSelectionChange: (Set<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Grid order: title, then the folders.
    val gestures = rememberAlbumGridGestures(
        gridState = gridState,
        keys = { listOf<String?>(null) + folders.orEmpty().map { it.collection.key } },
        columns = columns,
        onColumnsChange = onColumnsChange,
        selection = selection,
        onSelectionChange = onSelectionChange,
        contentPadding = contentPadding,
    )
    when {
        folders == null -> Column(
            modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.height(14.dp))
            Text("Looking for hidden photos…", color = colors.onBackground.copy(alpha = 0.6f), fontSize = 15.sp)
        }
        folders.isEmpty() && excluded.isEmpty() -> Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nothing hidden yet.\nUse ⋯ → Hide or Hide Folder on any photo.",
                color = colors.onBackground.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .then(gestures.modifier),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle(title) }
            items(folders, key = { it.collection.key }) { folder ->
                val n = folder.collection.count
                AlbumCard(
                    collection = folder.collection,
                    selected = selection?.let { folder.collection.key in it },
                    onClick = { gestures.click(folder.collection.key) { onOpen(folder.collection.key) } },
                    subtitle = if (folder.wholeFolder) "Hidden folder · $n" else "$n hidden ${if (n == 1) "file" else "files"}",
                )
            }
            if (excluded.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ExcludedSection(excluded, onInclude) }
            }
        }
    }
}

/** Folders left out of Hidden via ⋯ → Exclude Folder, each with Include to bring it back. */
@Composable
private fun ExcludedSection(excluded: List<String>, onInclude: (String) -> Unit) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            "Excluded from Hidden",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(onBackground.copy(alpha = 0.07f)),
        ) {
            excluded.forEachIndexed { i, folder ->
                if (i > 0) {
                    Box(
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(onBackground.copy(alpha = 0.15f)),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.trimEnd('/').substringAfterLast('/'),
                            fontSize = 16.sp,
                            color = onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            folder.trimEnd('/'),
                            fontSize = 13.sp,
                            color = onBackground.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Include",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onInclude(folder) },
                    )
                }
            }
        }
    }
}
