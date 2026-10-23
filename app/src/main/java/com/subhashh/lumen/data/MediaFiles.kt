package com.subhashh.lumen.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Outcome of a batch file operation, for a one-line message to the user. */
data class FileResult(val done: Int, val failed: Int, val needsAllFilesAccess: Boolean)

/**
 * Copying files between albums (device folders).
 *
 * MediaStore lets any app create media only under a few top-level folders (DCIM, Pictures, Movies
 * for video, Download), so those go through MediaStore with no extra permission. Any other folder
 * (WhatsApp, app download folders...) needs All files access, and is written with plain file IO
 * and then scanned.
 */
object MediaFiles {

    /** Folder for a new album; Pictures is open to both photos and videos. */
    fun newAlbumPath(name: String) = "${Environment.DIRECTORY_PICTURES}/${name.trim().replace('/', '_')}/"

    suspend fun copy(context: Context, items: List<MediaItem>, relativePath: String): FileResult =
        withContext(Dispatchers.IO) {
            var done = 0
            var failed = 0
            var needsAccess = false
            for (item in items) {
                val name = displayName(context, item) ?: run { failed++; continue }
                val ok = when {
                    mediaStoreCanWrite(item, relativePath) -> copyViaMediaStore(context, item, name, relativePath)
                    Environment.isExternalStorageManager() -> copyViaFile(context, item, name, relativePath)
                    else -> {
                        needsAccess = true
                        false
                    }
                }
                if (ok) done++ else failed++
            }
            FileResult(done, failed, needsAccess)
        }

    private fun mediaStoreCanWrite(item: MediaItem, relativePath: String): Boolean {
        val top = relativePath.substringBefore('/')
        val allowed = if (item.isVideo) {
            listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MOVIES)
        } else {
            listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES)
        }
        return top in allowed || top == Environment.DIRECTORY_DOWNLOADS
    }

    private fun copyViaMediaStore(context: Context, item: MediaItem, name: String, relativePath: String): Boolean {
        val resolver = context.contentResolver
        val collection = when {
            relativePath.startsWith(Environment.DIRECTORY_DOWNLOADS) ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            item.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            // MediaStore renames on a clash ("IMG_1 (1).jpg"), so no overwrite is possible.
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target: Uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openInputStream(item.uri).use { input ->
                resolver.openOutputStream(target).use { output ->
                    if (input == null || output == null) error("stream unavailable")
                    input.copyTo(output)
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            false
        }
    }

    private suspend fun copyViaFile(context: Context, item: MediaItem, name: String, relativePath: String): Boolean {
        val dir = File(Environment.getExternalStorageDirectory(), relativePath)
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = uniqueFile(dir, name)
        val ok = runCatching {
            context.contentResolver.openInputStream(item.uri).use { input ->
                target.outputStream().use { output -> input?.copyTo(output) ?: error("stream unavailable") }
            }
            // Keep the original's date for files without EXIF, which are dated by modification time.
            target.setLastModified(item.takenAt)
        }.isSuccess
        if (!ok) {
            target.delete()
            return false
        }
        scan(context, target)
        return true
    }

    /** "name.jpg", then "name (1).jpg", "name (2).jpg"... like MediaStore does. */
    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (candidate.exists()) candidate = File(dir, "$base ($n)$ext").also { n++ }
        return candidate
    }

    private suspend fun scan(context: Context, file: File) = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, _ -> cont.resume(Unit) }
    }

    private fun displayName(context: Context, item: MediaItem): String? =
        context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
}
