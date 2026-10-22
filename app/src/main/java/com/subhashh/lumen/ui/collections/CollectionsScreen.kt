package com.subhashh.lumen.ui.collections

import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.images.ImageKeys
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaThumbnail

@Composable
fun CollectionsScreen(
    utilities: List<MediaCollection>,
    folders: List<MediaCollection>,
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        fullWidth {
            Text(
                "Collections",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }

        fullWidth { SectionHeader("Utilities") }
        items(utilities, key = { it.key }) { collection ->
            UtilityTile(collection, onClick = { onOpen(collection.key) })
        }

        fullWidth { SectionHeader("Albums", trailing = folders.size.toString()) }
        items(folders, key = { it.key }) { collection ->
            AlbumCard(collection, onClick = { onOpen(collection.key) })
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun UtilityTile(collection: MediaCollection, onClick: () -> Unit) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    // Icon above the label so long names like "Recently Deleted" fit a half-width tile.
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(onBackground.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            utilityIcon(collection.key),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                collection.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(collection.count.toString(), fontSize = 13.sp, color = onBackground.copy(alpha = 0.55f))
        }
    }
}

private fun utilityIcon(key: String): ImageVector = when (key) {
    CollectionKeys.Favorites -> LumenIcons.HeartFilled
    CollectionKeys.Videos -> LumenIcons.Video
    CollectionKeys.Screenshots -> LumenIcons.Screenshot
    CollectionKeys.RecentlyDeleted -> LumenIcons.Delete
    else -> LumenIcons.Folder
}

@Composable
private fun AlbumCard(collection: MediaCollection, onClick: () -> Unit) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(onBackground.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            val cover = collection.cover
            if (cover != null) {
                Cover(cover)
            } else {
                Icon(LumenIcons.Folder, contentDescription = null, tint = onBackground.copy(alpha = 0.3f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            collection.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            collection.count.toString(),
            fontSize = 13.sp,
            color = onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun Cover(item: MediaItem) {
    val context = LocalContext.current
    AsyncImage(
        // Own cache key: covers are larger than grid thumbnails, so they must not reuse those bitmaps.
        model = remember(item.uri) {
            ImageRequest.Builder(context)
                .data(MediaThumbnail(item.uri))
                .memoryCacheKey(ImageKeys.cover(item.id))
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
