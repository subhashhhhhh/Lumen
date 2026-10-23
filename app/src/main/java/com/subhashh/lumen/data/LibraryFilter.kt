package com.subhashh.lumen.data

/**
 * Grid order, from the ≡ menu; applies to the Library and album grids. [descending] flips it:
 * newest, Z–A or largest first. Date ascending (newest at the bottom, like iOS) is the default
 * and the only order Years / Months make sense in.
 */
enum class LibrarySort(val label: String, val ascendingLabel: String, val descendingLabel: String, val defaultDescending: Boolean) {
    Date("Date", "Oldest First", "Newest First", false),
    Name("Name", "A to Z", "Z to A", false),
    Size("Size", "Smallest First", "Largest First", true);

    fun apply(items: List<MediaItem>, descending: Boolean): List<MediaItem> {
        if (this == Date && !descending) return items // already in this order
        val order = when (this) {
            Date -> compareBy<MediaItem> { it.takenAt }.thenBy { it.id }
            Name -> compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
            Size -> compareBy<MediaItem> { it.size }.thenBy { it.id }
        }
        return items.sortedWith(if (descending) order.reversed() else order)
    }
}

/** What the Library tab shows, from the ≡ menu. */
enum class LibraryFilter(val label: String, val emptyText: String) {
    All("All Items", "No photos or videos"),
    Photos("Photos", "No photos"),
    Videos("Videos", "No videos"),
    Favorites("Favorites", "No favorites"),
    Screenshots("Screenshots", "No screenshots");

    fun matches(item: MediaItem): Boolean = when (this) {
        All -> true
        Photos -> !item.isVideo
        Videos -> item.isVideo
        Favorites -> item.isFavorite
        Screenshots -> item.isScreenshot
    }
}
