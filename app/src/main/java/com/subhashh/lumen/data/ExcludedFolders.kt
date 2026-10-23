package com.subhashh.lumen.data

import androidx.compose.runtime.Immutable

/** A code project found on the device, offered for exclusion in Settings. */
@Immutable
data class ExclusionSuggestion(val folder: String, val itemCount: Int)

/**
 * Lumen-only exclusions: nothing on disk changes, other apps are unaffected. Folders are
 * MediaStore relative paths ("Developer/Fastlytics/") and cover their subfolders.
 */
object ExcludedFolders {

    fun covers(excluded: Set<String>, item: MediaItem): Boolean =
        excluded.any { item.relativePath.startsWith(it) }

    /** Path segments that mark a software project's build or source tree, not someone's photos. */
    private val CodeSegments = setOf(
        "node_modules", "dist", "build", "src", "out", "target", ".git", "public", "static", "_static",
        "assets", "res", "drawable", "mipmap",
    )

    /**
     * Project folders holding images that are really code assets (icons, build output...): the
     * folder just above the first code-looking segment, e.g. "Developer/Fastlytics/" for
     * "Developer/Fastlytics/dist/drivers/". Already-excluded ones are left out.
     */
    fun suggestions(all: List<MediaItem>, excluded: Set<String>): List<ExclusionSuggestion> {
        val counts = HashMap<String, Int>()
        for (item in all) {
            if (covers(excluded, item)) continue
            val segments = item.relativePath.trimEnd('/').split('/')
            val codeAt = segments.indexOfFirst { it.lowercase() in CodeSegments }
            if (codeAt <= 0) continue
            val root = segments.take(codeAt).joinToString("/", postfix = "/")
            counts[root] = (counts[root] ?: 0) + 1
        }
        return counts.map { (folder, n) -> ExclusionSuggestion(folder, n) }.sortedByDescending { it.itemCount }
    }
}
