package com.subhashh.lumen.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.subhashh.lumen.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What Lumen keeps on the phone, for Settings → Storage: the app itself, then its own folders
 * measured on disk and grouped the way people think of them. Sizes are what the files take, so
 * they add up to about what Android's App Info shows.
 */
@Immutable
data class StorageUsage(
    val kinds: List<Kind>,
    /** A search index (and its model, if left behind) from a search model no longer in use. */
    val oldSearch: Long,
    val thumbnails: Long,
    /** Photos and videos in Recently Deleted: the phone's storage, not Lumen's. */
    val trash: Long,
    val phoneFree: Long,
    val phoneTotal: Long,
) {
    val total: Long get() = kinds.sumOf { it.bytes }

    @Immutable
    data class Kind(val type: Type, val bytes: Long, val parts: List<Part>)

    @Immutable
    data class Part(@StringRes val label: Int, val bytes: Long)

    enum class Type { App, Models, Indexes, Caches, Other }

    companion object {
        suspend fun measure(context: Context): StorageUsage = withContext(Dispatchers.IO) {
            val trash = runCatching { MediaRepository(context).loadTrash().sumOf { it.size } }.getOrDefault(0L)
            val noBackup = context.noBackupFilesDir
            val stats = runCatching {
                context.getSystemService(StorageStatsManager::class.java)
                    .queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
            }.getOrNull()

            // Models, by what they're for.
            val models = File(noBackup, "models").listFiles().orEmpty()
            fun modelBytes(match: (String) -> Boolean) = models.filter { match(it.name) }.sumOf { size(it) }
            val current = runCatching { com.subhashh.lumen.ai.ModelDownloads.installed(context)?.id }.getOrNull()
            val searchIds = com.subhashh.lumen.ai.ModelCatalog.all.map { it.id }.toSet()
            val modelParts = listOf(
                Part(R.string.storage_part_search, modelBytes { it in searchIds && it == current }),
                Part(R.string.storage_part_faces, modelBytes { it.startsWith("faces") }),
                Part(R.string.storage_part_cutout, modelBytes { it.startsWith("cutout") }),
                Part(R.string.storage_part_text, modelBytes { it.startsWith("ppocr") }),
                Part(R.string.storage_part_subtitles, modelBytes { it.startsWith("speech") }),
                Part(R.string.storage_part_npu, size(File(context.filesDir, "npu-skel"))),
            )

            // Indexes: search (the model in use), text, faces. Another model's files are left over.
            val search = File(noBackup, "search").listFiles().orEmpty()
            fun ofModel(file: File, id: String) = file.name.startsWith("$id.") || file.name.endsWith("-$id.bin") || file.name.endsWith("-$id.json")
            val oldIds = searchIds - setOfNotNull(current)
            val oldSearch = search.filter { f -> oldIds.any { ofModel(f, it) } }.sumOf { size(it) } +
                models.filter { it.name in oldIds }.sumOf { size(it) }
            val indexParts = listOf(
                Part(R.string.storage_part_search, current?.let { id -> search.filter { ofModel(it, id) }.sumOf { size(it) } } ?: 0L),
                Part(R.string.storage_part_faces, search.filter { it.name.startsWith("faces.db") }.sumOf { size(it) } + size(File(noBackup, "faces"))),
                Part(R.string.storage_part_text, search.filter { it.name.startsWith("text.db") }.sumOf { size(it) }),
                Part(R.string.storage_part_subtitles, size(File(noBackup, "subtitles"))),
                Part(R.string.storage_part_old_search, oldSearch),
            )

            val thumbnails = size(File(context.cacheDir, "thumbnails"))
            val cacheParts = listOf(
                Part(R.string.storage_part_thumbnails, thumbnails),
                Part(R.string.storage_part_gpu, size(File(noBackup, "gpu-cache"))),
                Part(R.string.storage_part_frames, size(File(noBackup, "frames"))),
                Part(R.string.storage_part_other_cache, size(context.cacheDir) - thumbnails),
            )

            val known = modelParts.sumOf { it.bytes } + indexParts.sumOf { it.bytes } + cacheParts.sumOf { it.bytes }
            // Everything Lumen keeps (Android's count when it gives one), less what's sorted above.
            val data = stats?.let { it.dataBytes } ?: (size(context.dataDir) + size(context.externalCacheDir))
            val other = (data - known).coerceAtLeast(0L)
            val free = runCatching { context.filesDir.usableSpace }.getOrDefault(0L)
            val totalSpace = runCatching { context.filesDir.totalSpace }.getOrDefault(0L)

            StorageUsage(
                kinds = listOf(
                    Kind(Type.App, stats?.appBytes ?: 0L, emptyList()),
                    Kind(Type.Models, modelParts.sumOf { it.bytes }, modelParts.filter { it.bytes > 0 }),
                    Kind(Type.Indexes, indexParts.sumOf { it.bytes }, indexParts.filter { it.bytes > 0 }),
                    Kind(Type.Caches, cacheParts.sumOf { it.bytes }, cacheParts.filter { it.bytes > 0 }),
                    Kind(Type.Other, other, emptyList()),
                ),
                oldSearch = oldSearch,
                thumbnails = thumbnails,
                trash = trash,
                phoneFree = free,
                phoneTotal = totalSpace,
            )
        }

        /**
         * Deletes what a search model no longer in use left behind: its index and suggestions,
         * and its model files if they're still there. The model in use is never touched.
         */
        suspend fun removeOldSearch(context: Context) = withContext(Dispatchers.IO) {
            val current = com.subhashh.lumen.ai.ModelDownloads.installed(context)?.id ?: return@withContext
            val old = com.subhashh.lumen.ai.ModelCatalog.all.map { it.id }.filter { it != current }
            for (file in File(context.noBackupFilesDir, "search").listFiles().orEmpty()) {
                if (old.any { id -> file.name.startsWith("$id.") || file.name.endsWith("-$id.bin") || file.name.endsWith("-$id.json") }) file.delete()
            }
            for (id in old) File(context.noBackupFilesDir, "models/$id").deleteRecursively()
        }

        /** Space a file or folder takes on disk. */
        fun size(file: File?): Long {
            if (file == null || !file.exists()) return 0L
            if (file.isFile) return file.length()
            return file.listFiles().orEmpty().sumOf { size(it) }
        }
    }
}
