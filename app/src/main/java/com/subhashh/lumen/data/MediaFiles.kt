package com.subhashh.lumen.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Outcome of a batch file operation, for a one-line message to the user. */
data class FileResult(val done: Int, val failed: Int, val needsAllFilesAccess: Boolean)

/**
 * Copying, moving, renaming and rotating files.
 *
 * Changing a file Lumen didn't create needs either All files access (then plain file IO + a media
 * scan works anywhere) or a write grant from MediaStore.createWriteRequest, which the caller must
 * obtain first (auto-approved when the user enabled Media management).
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

    /** Moves into [relativePath]. Items already there are skipped. */
    suspend fun move(context: Context, items: List<MediaItem>, relativePath: String): FileResult =
        withContext(Dispatchers.IO) {
            var done = 0
            var failed = 0
            var needsAccess = false
            for (item in items) {
                if (item.relativePath == relativePath) continue
                val ok = when {
                    Environment.isExternalStorageManager() -> moveViaFile(context, item, relativePath)
                    mediaStoreCanWrite(item, relativePath) -> runCatching {
                        // MediaStore moves the file itself when its folder changes.
                        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
                        context.contentResolver.update(item.uri, values, null, null) > 0
                    }.getOrDefault(false)
                    else -> {
                        needsAccess = true
                        false
                    }
                }
                if (ok) done++ else failed++
            }
            FileResult(done, failed, needsAccess)
        }

    /** Renames to [newName] (with extension) in the same folder. False if taken or not allowed. */
    suspend fun rename(context: Context, item: MediaItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (Environment.isExternalStorageManager()) {
            val source = filePath(context, item)?.let(::File) ?: return@withContext false
            val target = File(source.parentFile, newName)
            if (target.exists() || !source.renameTo(target)) return@withContext false
            scan(context, source, target)
            true
        } else {
            runCatching {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
                context.contentResolver.update(item.uri, values, null, null) > 0
            }.getOrDefault(false)
        }
    }

    /** Rotation is lossless: only the EXIF orientation flag changes, so it's offered for JPEGs only. */
    fun canRotate(item: MediaItem) = !item.isVideo && item.mimeType == "image/jpeg"

    /** Turns each JPEG [degrees] clockwise by rewriting its EXIF orientation, never re-encoding pixels. */
    suspend fun rotate(context: Context, items: List<MediaItem>, degrees: Int = 90): FileResult =
        withContext(Dispatchers.IO) {
            var done = 0
            var failed = 0
            for (item in items.filter(::canRotate)) {
                val ok = runCatching {
                    val path = filePath(context, item)
                    if (Environment.isExternalStorageManager() && path != null) {
                        ExifInterface(path).apply { rotate(degrees) }.saveAttributes()
                    } else {
                        context.contentResolver.openFileDescriptor(item.uri, "rw").use { pfd ->
                            ExifInterface(pfd!!.fileDescriptor).apply { rotate(degrees) }.saveAttributes()
                        }
                    }
                    // Re-index so MediaStore picks up the new orientation and modified time.
                    path?.let { scan(context, File(it)) }
                }.isSuccess
                if (ok) done++ else failed++
            }
            FileResult(done, failed, needsAllFilesAccess = false)
        }

    private suspend fun moveViaFile(context: Context, item: MediaItem, relativePath: String): Boolean {
        val source = filePath(context, item)?.let(::File) ?: return false
        val dir = File(Environment.getExternalStorageDirectory(), relativePath)
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = uniqueFile(dir, source.name)
        if (!source.renameTo(target)) {
            // Different volume (SD card): copy, then remove the original.
            val copied = runCatching { source.copyTo(target); target.setLastModified(source.lastModified()) }.isSuccess
            if (!copied || !source.delete()) {
                target.delete()
                return false
            }
        }
        scan(context, source, target)
        return true
    }

    /** The file's path, for file IO under All files access (MediaStore's _data column). */
    @Suppress("DEPRECATION")
    private fun filePath(context: Context, item: MediaItem): String? =
        context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

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

    /** Re-indexes [files]; scanning a path that no longer exists removes its stale entry. */
    private suspend fun scan(context: Context, vararg files: File) = suspendCancellableCoroutine { cont ->
        var remaining = files.size
        MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null) { _, _ ->
            if (--remaining == 0 && cont.isActive) cont.resume(Unit)
        }
    }

    fun displayName(context: Context, item: MediaItem): String? =
        context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
}
