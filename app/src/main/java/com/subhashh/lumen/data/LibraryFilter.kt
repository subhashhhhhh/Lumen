package com.subhashh.lumen.data

import androidx.annotation.StringRes
import com.subhashh.lumen.R

/**
 * Grid order, from the ≡ menu; applies to the Library and album grids. [descending] flips it:
 * newest, Z–A or largest first. Date ascending (newest at the bottom, like iOS) is the default
 * and the only order Years / Months make sense in.
 */
enum class LibrarySort(@StringRes val label: Int, @StringRes val ascendingLabel: Int, @StringRes val descendingLabel: Int, val defaultDescending: Boolean) {
    Date(R.string.data_sort_date, R.string.data_sort_oldest, R.string.data_sort_newest, false),
    Name(R.string.data_sort_name, R.string.data_sort_a_z, R.string.data_sort_z_a, false),
    Size(R.string.data_sort_size, R.string.data_sort_smallest, R.string.data_sort_largest, true);

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
enum class LibraryFilter(@StringRes val label: Int, @StringRes val emptyText: Int) {
    All(R.string.data_filter_all, R.string.data_empty_all),
    Photos(R.string.data_filter_photos, R.string.data_empty_photos),
    Videos(R.string.data_filter_videos, R.string.data_empty_videos),
    Favorites(R.string.data_filter_favorites, R.string.data_empty_favorites),
    Screenshots(R.string.data_filter_screenshots, R.string.data_empty_screenshots);

    fun matches(item: MediaItem): Boolean = when (this) {
        All -> true
        Photos -> !item.isVideo
        Videos -> item.isVideo
        Favorites -> item.isFavorite
        Screenshots -> item.isScreenshot
    }
}
