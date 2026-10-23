package com.subhashh.lumen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Favorites from other galleries: Fossify Gallery's export (a .txt with one full path per line)
 * and Aves's backup (.json with "favourites": { volume: [relative paths] }), plus Lumen's own
 * backup. Files are matched by folder and name, so a backup from another storage volume, or from
 * another phone with the same folders, still finds them.
 */
object FavoritesImport {

    /** What was in the file, and what of it is on this phone. */
    data class Found(val listed: Int, val matched: List<MediaItem>)

    /** Reads [uri] and finds its favorites in [library]. Null when it's none of the known formats. */
    suspend fun read(context: Context, uri: Uri, library: List<MediaItem>): Found? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching null
            val wanted = paths(text) ?: return@runCatching null
            val byPath = library.associateBy { (it.relativePath + it.name).lowercase() }
            Found(listed = wanted.size, matched = wanted.mapNotNull { byPath[it.lowercase()] }.distinct())
        }.getOrNull()
    }

    /** Favorites as volume-relative paths ("DCIM/Camera/IMG_1.jpg"), whichever app wrote them. */
    private fun paths(text: String): List<String>? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            // Aves: { "favourites": { "/storage/emulated/0": ["DCIM/…", …] }, "version": 1, … }
            json.optJSONObject("favourites")?.let { volumes ->
                return volumes.keys().asSequence().flatMap { volume ->
                    val list = volumes.getJSONArray(volume)
                    (0 until list.length()).asSequence().map { list.getString(it).trimStart('/') }
                }.toList()
            }
            // Lumen's own backup: "favorites": ["DCIM/Camera/IMG_1.jpg", …]
            json.optJSONArray("favorites")?.let { list ->
                return (0 until list.length()).map { list.getString(it) }
            }
            return null
        }
        // Fossify: one absolute path per line.
        val lines = trimmed.lines().map { it.trim() }.filter { it.startsWith("/") }
        if (lines.isEmpty()) return null
        return lines.mapNotNull(::volumeRelative)
    }

    /** "/storage/emulated/0/DCIM/x.jpg" → "DCIM/x.jpg"; also /sdcard/… and SD cards (/storage/1A2B-3C4D/…). */
    private fun volumeRelative(path: String): String? {
        VolumePrefix.find(path)?.let { return path.substring(it.range.last + 1) }
        return null
    }

    private val VolumePrefix = Regex("^/(?:storage/emulated/\\d+|storage/[^/]+|sdcard|mnt/sdcard)/")
}
