package com.subhashh.lumen.data

import android.content.Context
import androidx.core.content.edit

/** Small persisted view preferences. */
class LumenPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    var libraryFilter: LibraryFilter
        get() = prefs.getString(KeyFilter, null)
            ?.let { name -> LibraryFilter.entries.firstOrNull { it.name == name } }
            ?: LibraryFilter.All
        set(value) = prefs.edit { putString(KeyFilter, value.name) }

    /** Show whole photos letterboxed in their cells instead of square crops. */
    var aspectRatioGrid: Boolean
        get() = prefs.getBoolean(KeyAspect, false)
        set(value) = prefs.edit { putBoolean(KeyAspect, value) }

    /** Grid columns, shared by the Library and albums. */
    var gridColumns: Int
        get() = prefs.getInt(KeyColumns, 5)
        set(value) = prefs.edit { putInt(KeyColumns, value) }

    /** Folders Lumen ignores (relative paths, subfolders included). */
    var excludedFolders: Set<String>
        get() = prefs.getStringSet(KeyExcluded, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KeyExcluded, value) }

    private companion object {
        const val KeyExcluded = "excluded_folders"
        const val KeyColumns = "grid_columns"
        const val KeyFilter = "library_filter"
        const val KeyAspect = "aspect_ratio_grid"
    }
}
