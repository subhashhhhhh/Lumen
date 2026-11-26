package com.subhashh.lumen.data

import androidx.compose.runtime.Immutable

/** A code project found on the device, offered for exclusion in Settings. */
@Immutable
data class ExclusionSuggestion(val folder: String, val itemCount: Int)

/**
 * What Lumen ignores: [excluded] folders with everything inside them, except subfolders in
 * [shown] (#45: exclude Download/Telegram/ but keep Download/Telegram/Telegram Images/). Folders
 * are MediaStore relative paths ("Download/Telegram/"). The deepest rule over a path wins.
 */
@Immutable
data class Exclusions(val excluded: Set<String> = emptySet(), val shown: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = excluded.isEmpty()

    fun covers(item: MediaItem): Boolean = covers(item.relativePath)

    fun covers(path: String): Boolean {
        if (excluded.isEmpty()) return false
        var deepest = -1
        var hidden = false
        for (folder in excluded) if (folder.length > deepest && path.startsWith(folder)) {
            deepest = folder.length
            hidden = true
        }
        if (!hidden) return false
        for (folder in shown) if (folder.length > deepest && path.startsWith(folder)) {
            deepest = folder.length
            hidden = false
        }
        return hidden
    }

    /** The excluded folder whose rule hides [path], if any (for "Excluded with …"). */
    fun ruleOver(path: String): String? =
        excluded.filter { path.startsWith(it) }.maxByOrNull { it.length }?.takeIf { covers(path) }
}

/**
 * Every folder that holds photos or videos, with its parents (which may hold only folders): what
 * Settings → Excluded Folders lets you browse. [counts] includes subfolders.
 */
@Immutable
class FolderTree(val counts: Map<String, Int>, private val children: Map<String, List<String>>) {
    /** Folders directly inside [folder] ("" for the top), by name. */
    fun children(folder: String): List<String> = children[folder].orEmpty()

    companion object {
        fun of(items: List<MediaItem>): FolderTree {
            val counts = HashMap<String, Int>()
            val children = HashMap<String, MutableSet<String>>()
            for (item in items) {
                val path = item.relativePath
                if (path.isEmpty()) continue
                var parent = ""
                var at = path.indexOf('/')
                while (at >= 0) {
                    val folder = path.substring(0, at + 1)
                    counts[folder] = (counts[folder] ?: 0) + 1
                    children.getOrPut(parent) { HashSet() } += folder
                    parent = folder
                    at = path.indexOf('/', at + 1)
                }
            }
            val sorted = children.mapValues { (_, set) -> set.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.trimEnd('/').substringAfterLast('/') }) }
            return FolderTree(counts, sorted)
        }
    }
}

/**
 * Lumen-only exclusions: nothing on disk changes, other apps are unaffected.
 */
object ExcludedFolders {

    /** Path segments that mark a software project's build or source tree, not someone's photos. */
    private val CodeSegments = setOf(
        "node_modules", "dist", "build", "src", "out", "target", ".git", "public", "static", "_static",
        "assets", "res", "drawable", "mipmap",
    )

    /** Android's shared top-level folders, where people's own photos and files live. */
    private val StandardFolders = setOf(
        "dcim", "pictures", "movies", "download", "downloads", "documents", "music", "android",
    )

    /**
     * Project folders holding images that are really code assets (icons, build output...): the
     * folder just above the first code-looking segment, e.g. "Developer/Fastlytics/" for
     * "Developer/Fastlytics/dist/drivers/". Already-excluded ones are left out.
     */
    fun suggestions(all: List<MediaItem>, exclusions: Exclusions): List<ExclusionSuggestion> {
        val counts = HashMap<String, Int>()
        for (item in all) {
            if (exclusions.covers(item)) continue
            val segments = item.relativePath.trimEnd('/').split('/')
            val codeAt = segments.indexOfFirst { it.lowercase() in CodeSegments }
            if (codeAt <= 0) continue
            // A suggestion hides everything under it: never the whole of Download or Pictures
            // because one folder in it has a code-like name ("Download/dist/", "Pictures/Public/").
            if (codeAt == 1 && segments[0].lowercase() in StandardFolders) continue
            val root = segments.take(codeAt).joinToString("/", postfix = "/")
            counts[root] = (counts[root] ?: 0) + 1
        }
        return counts.map { (folder, n) -> ExclusionSuggestion(folder, n) }.sortedByDescending { it.itemCount }
    }
}
