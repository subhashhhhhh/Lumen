package com.subhashh.lumen.ui.hidden

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.collections.AlbumCard

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
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
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
        folders.isEmpty() -> Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nothing hidden yet.\nUse ⋯ → Hide or Hide Folder on any photo.",
                color = colors.onBackground.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Hidden",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            items(folders, key = { it.collection.key }) { folder ->
                val n = folder.collection.count
                AlbumCard(
                    collection = folder.collection,
                    onClick = { onOpen(folder.collection.key) },
                    subtitle = if (folder.wholeFolder) "Hidden folder · $n" else "$n hidden ${if (n == 1) "file" else "files"}",
                )
            }
        }
    }
}
