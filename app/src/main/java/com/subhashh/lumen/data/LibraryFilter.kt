package com.subhashh.lumen.data

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
