package com.subhashh.lumen.ui.hidden

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
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
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    /** "Hidden", or the selection count while selecting. */
    title: String,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    /** Selected folder keys while selecting; null when not in selection mode. */
    selection: Set<String>?,
    onSelectionChange: (Set<String>?) -> Unit,
    /** Encrypt Hidden is on: loading means unlocking the vault, and says so. */
    encrypted: Boolean = false,
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
            if (encrypted) {
                UnlockingVault()
                Spacer(Modifier.height(18.dp))
                Text("Unlocking your vault…", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Your files are safe; they're just encrypted.", color = colors.onBackground.copy(alpha = 0.55f), fontSize = 13.sp)
            } else {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.height(14.dp))
                Text("Looking for hidden photos…", color = colors.onBackground.copy(alpha = 0.6f), fontSize = 15.sp)
            }
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
        }
    }
}

/** A lock in a slowly turning ring: the vault opening, calmly. */
@Composable
private fun UnlockingVault() {
    val reduce = com.subhashh.lumen.ui.theme.LocalMotion.current.reduce
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "vaultUnlock")
    val angle by spin.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "unlockAngle",
    )
    val accent = androidx.compose.ui.graphics.Color(0xFF30D158)
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (reduce) 0f else angle },
        ) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(
                androidx.compose.ui.graphics.Brush.sweepGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, accent)),
                0f, 300f, false, topLeft, arc,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        androidx.compose.material3.Icon(
            com.subhashh.lumen.ui.icons.LumenIcons.Lock,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(32.dp),
        )
    }
}
