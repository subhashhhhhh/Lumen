package com.subhashh.lumen.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The last Hidden scan, saved so Hidden opens with its folders already there while a fresh scan
 * runs behind them. One line per file: path, type, modified millis, width, height, duration.
 *
 * In no-backup storage: it lists hidden paths, which must never end up in a cloud backup.
 */
object HiddenIndex {

    // v2: sizes now have EXIF rotation applied, so v1's sideways sizes are never reused.
    private fun file(context: Context) = File(context.noBackupFilesDir, "hidden_index_v2.tsv")

    /** The saved list, or null when there's none (or it can't be read). */
    suspend fun load(context: Context): List<MediaItem>? = withContext(Dispatchers.IO) {
        runCatching {
            val index = file(context)
            if (!index.exists()) return@runCatching null
            index.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 6) return@mapNotNull null
                // Saved before Lumen knew Android's trash names: never show those as hidden.
                if (HiddenMedia.isSystemFile(File(parts[0]).name)) return@mapNotNull null
                HiddenMedia.item(
                    File(parts[0]),
                    type = parts[1],
                    modified = parts[2].toLongOrNull() ?: return@mapNotNull null,
                    width = parts[3].toIntOrNull() ?: 0,
                    height = parts[4].toIntOrNull() ?: 0,
                    durationMs = parts[5].toLongOrNull() ?: 0,
                )
            }
        }.getOrNull()
    }

    suspend fun save(context: Context, items: List<MediaItem>) = withContext(Dispatchers.IO) {
        runCatching {
            val text = items.joinToString("\n") { item ->
                "${item.uri.path}\t${item.mimeType}\t${item.takenAt}\t${item.width}\t${item.height}\t${item.durationMs}"
            }
            // Write then rename, so a crash mid-write never leaves half an index.
            val temp = File(context.noBackupFilesDir, "hidden_index.tmp")
            temp.writeText(text)
            temp.renameTo(file(context))
            File(context.noBackupFilesDir, "hidden_index.tsv").delete()
        }
    }
}
