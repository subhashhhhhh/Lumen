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
    /** The current search results; the caller supplies them. */
    const val Search = "search"
    private const val HiddenFolderPrefix = "hidden:"

    /** One folder inside Hidden: its hidden files only. */
    fun hiddenFolder(bucketId: Long) = "$HiddenFolderPrefix$bucketId"

    fun hiddenFolderId(key: String): Long? = key.removePrefix(HiddenFolderPrefix).toLongOrNull()
        .takeIf { key.startsWith(HiddenFolderPrefix) }

    private const val PersonPrefix = "person:"

    /** One person's photos (Collections → People). */
    fun person(id: Long) = "$PersonPrefix$id"

    fun personId(key: String?): Long? = key?.takeIf { it.startsWith(PersonPrefix) }?.removePrefix(PersonPrefix)?.toLongOrNull()

    private const val EmptyAlbumPrefix = "empty:"

    /** An album made with New Album that has nothing in it yet (so no MediaStore folder). */
    fun emptyAlbum(relativePath: String) = "$EmptyAlbumPrefix$relativePath"

    /** A device folder album, as opposed to a utility or something in Hidden. */
    fun isFolder(key: String?) = key?.startsWith(FolderPrefix) == true

    /** Hidden itself or a folder inside it: everything that must stay behind the lock. */
    fun isHidden(key: String?) = key == Hidden || key?.startsWith(HiddenFolderPrefix) == true
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

/** Camera RAW file extensions Android can show (DNG fully, the others by their embedded preview). */
internal val RawExtensions = setOf(
    "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "sr2", "srf", "raf", "orf", "rw2", "pef", "srw", "x3f", "erf", "kdc", "mrw", "dcr",
)

/** A camera RAW file, by type or, when the type is generic, by extension. */
val MediaItem.isRaw: Boolean
    get() = mimeType == "image/x-adobe-dng" || mimeType.startsWith("image/x-") &&
        name.substringAfterLast('.', "").lowercase() in RawExtensions

internal val MediaItem.isScreenshot: Boolean
    get() = relativePath.contains("screenshot", ignoreCase = true)

@Immutable
data class MediaCollection(
    val key: String,
    val title: String,
    /** Newest item (or the one chosen as cover), or null for an empty collection. */
    val cover: MediaItem?,
    val count: Int,
    /** Folder albums: the folder, e.g. "DCIM/Camera/". Empty for utilities. */
    val relativePath: String = "",
    /** When its newest item was taken; sorts albums by recency. */
    val latestAt: Long = 0,
    val pinned: Boolean = false,
    /** The cover was chosen with Make Album Cover rather than being the newest item. */
    val customCover: Boolean = false,
)

/** How the Albums section is ordered; pinned albums always come first. */
enum class AlbumSort(val label: String) { Recent("Recent"), Name("Name"), Count("Most Items") }

/**
 * The user's album arrangement, keyed by folder path so it survives MediaStore id changes:
 * sort order, pinned albums (in pin order), chosen covers (folder → item id), and albums made
 * with New Album that are still empty.
 */
@Immutable
data class AlbumSettings(
    val sort: AlbumSort = AlbumSort.Recent,
    val pinned: List<String> = emptyList(),
    val covers: Map<String, Long> = emptyMap(),
    val created: List<String> = emptyList(),
) {
    /** The same arrangement after a folder moved from [old] to [new]. */
    fun renamed(old: String, new: String) = copy(
        pinned = pinned.map { if (it == old) new else it },
        covers = covers.mapKeys { (path, _) -> if (path == old) new else path },
        created = created.map { if (it == old) new else it },
    )
}

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

    /**
     * One album per device folder, plus albums made with New Album that are still empty,
     * arranged by [settings]: chosen covers, pinned first (in pin order), then by the sort.
     */
    fun folders(library: List<MediaItem>, settings: AlbumSettings = AlbumSettings()): List<MediaCollection> {
        val real = library.groupBy { it.bucketId }.map { (bucketId, items) ->
            val newest = items.last()
            val path = newest.relativePath
            val chosen = settings.covers[path]?.let { id -> items.firstOrNull { it.id == id } }
            MediaCollection(
                key = CollectionKeys.folder(bucketId),
                title = newest.bucketName,
                cover = chosen ?: newest,
                count = items.size,
                relativePath = path,
                latestAt = newest.takenAt,
                pinned = path in settings.pinned,
                customCover = chosen != null,
            )
        }
        val paths = real.mapTo(HashSet()) { it.relativePath }
        val empty = settings.created.filter { it !in paths }.map { path ->
            MediaCollection(
                key = CollectionKeys.emptyAlbum(path),
                title = path.trimEnd('/').substringAfterLast('/'),
                cover = null,
                count = 0,
                relativePath = path,
                // New albums sort to the top of Recent, where the user expects to find them.
                latestAt = Long.MAX_VALUE,
                pinned = path in settings.pinned,
            )
        }
        val sorted = when (settings.sort) {
            AlbumSort.Recent -> (real + empty).sortedByDescending { it.latestAt }
            AlbumSort.Name -> (real + empty).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            AlbumSort.Count -> (real + empty).sortedByDescending { it.count }
        }
        val (pinned, rest) = sorted.partition { it.pinned }
        return pinned.sortedBy { settings.pinned.indexOf(it.relativePath) } + rest
    }
}
