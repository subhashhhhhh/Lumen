package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Video frames kept between passes, so a video is decoded once: the faces pass saves each frame
 * small (shortest side [Side], what Smart Search's models look at), the Smart Search pass reads
 * them back and deletes them. Decoding was most of a video's cost (~0.3 s a frame); saving and
 * reading a small JPEG is a few ms. One folder per video; `done` marks a complete set, so a pass
 * stopped mid-video doesn't leave Smart Search with half its frames.
 */
object FrameCache {
    private const val Side = 224
    private const val Done = "done"

    private fun root(context: Context) = File(context.noBackupFilesDir, "frames")

    private fun dir(context: Context, mediaId: Long, modified: Long) = File(root(context), "$mediaId-$modified")

    /** Starts saving a video's frames, dropping any earlier attempt. */
    fun begin(context: Context, mediaId: Long, modified: Long): File =
        dir(context, mediaId, modified).apply {
            deleteRecursively()
            mkdirs()
        }

    fun save(folder: File, atMs: Long, frame: Bitmap) {
        val scale = Side.toFloat() / minOf(frame.width, frame.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(frame, maxOf(Side, Math.round(frame.width * scale)), maxOf(Side, Math.round(frame.height * scale)), true)
        } else frame
        File(folder, "$atMs.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (small !== frame) small.recycle()
    }

    fun finish(folder: File) {
        File(folder, Done).createNewFile()
    }

    /** A complete set of frames for this version of the video, oldest first; null if there isn't one. */
    fun frames(context: Context, mediaId: Long, modified: Long): List<Pair<Long, File>>? {
        val folder = dir(context, mediaId, modified)
        if (!File(folder, Done).isFile) return null
        return folder.listFiles { f -> f.name.endsWith(".jpg") }.orEmpty()
            .mapNotNull { f -> f.nameWithoutExtension.toLongOrNull()?.let { it to f } }
            .sortedBy { it.first }
    }

    fun read(file: File): Bitmap? = BitmapFactory.decodeFile(file.path)

    fun drop(context: Context, mediaId: Long, modified: Long) {
        dir(context, mediaId, modified).deleteRecursively()
    }

    /** Folders for videos that are gone, edited, or already indexed by Smart Search. */
    fun prune(context: Context, keep: (mediaId: Long, modified: Long) -> Boolean) {
        root(context).listFiles()?.forEach { folder ->
            val parts = folder.name.split('-')
            val id = parts.getOrNull(0)?.toLongOrNull()
            val modified = parts.getOrNull(1)?.toLongOrNull()
            if (id == null || modified == null || !keep(id, modified)) folder.deleteRecursively()
        }
    }

    fun clear(context: Context) {
        root(context).deleteRecursively()
    }
}
