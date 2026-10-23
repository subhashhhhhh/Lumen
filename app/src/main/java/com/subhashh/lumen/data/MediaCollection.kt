package com.subhashh.lumen.data

import androidx.compose.runtime.Immutable

/** Saveable string keys for every grid the app can show. */
object CollectionKeys {
    const val Library = "library"
    const val Favorites = "favorites"
    const val Videos = "videos"
    const val Screenshots = "screenshots"
    const val RecentlyDeleted = "trash"
    /** Hidden files come from a storage scan, not MediaStore; the caller supplies them. */
    const val Hidden = "hidden"
    private const val FolderPrefix = "folder:"

    fun folder(bucketId: Long) = "$FolderPrefix$bucketId"

    fun items(key: String, library: List<MediaItem>, trash: List<MediaItem>): List<MediaItem> = when {
        key == Library -> library
        key == Favorites -> library.filter { it.isFavorite }
        key == Videos -> library.filter { it.isVideo }
        key == Screenshots -> library.filter { it.isScreenshot }
        key == RecentlyDeleted -> trash
        key.startsWith(FolderPrefix) -> {
            val id = key.removePrefix(FolderPrefix).toLongOrNull()
            library.filter { it.bucketId == id }
        }
        else -> emptyList()
    }
}

internal val MediaItem.isScreenshot: Boolean
    get() = relativePath.contains("screenshot", ignoreCase = true)

@Immutable
data class MediaCollection(
    val key: String,
    val title: String,
    /** Newest item, or null for an empty collection. */
    val cover: MediaItem?,
    val count: Int,
)

object MediaCollections {

    fun utilities(library: List<MediaItem>, trash: List<MediaItem>): List<MediaCollection> =
        listOf(
            CollectionKeys.Favorites to "Favorites",
            CollectionKeys.Videos to "Videos",
            CollectionKeys.Screenshots to "Screenshots",
            CollectionKeys.RecentlyDeleted to "Recently Deleted",
        ).map { (key, title) ->
            val items = CollectionKeys.items(key, library, trash)
            MediaCollection(key, title, items.lastOrNull(), items.size)
        } + MediaCollection(CollectionKeys.Hidden, "Hidden", cover = null, count = LockedCount)

    /** Count shown for Hidden, which stays unknown (and unshown) until unlocked. */
    const val LockedCount = -1

    /** One album per device folder, most recently updated first. */
    fun folders(library: List<MediaItem>): List<MediaCollection> =
        library.groupBy { it.bucketId }
            .map { (bucketId, items) ->
                MediaCollection(
                    key = CollectionKeys.folder(bucketId),
                    title = items.last().bucketName,
                    cover = items.last(),
                    count = items.size,
                )
            }
            .sortedByDescending { it.cover?.takenAt }
}
