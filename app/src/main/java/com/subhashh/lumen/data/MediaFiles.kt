package com.subhashh.lumen.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

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

    /**
     * Rotation without quality loss: JPEG via its EXIF orientation flag; PNG and WebP by re-saving
     * the rotated pixels losslessly; MP4 video by rewriting the container's rotation flag (streams
     * copied, not re-encoded). HEIC/AVIF/GIF can't be written back reliably, so they aren't offered.
     */
    fun canRotate(item: MediaItem) = rotationKind(item) != null

    private enum class RotationKind { ExifFlag, Reencode, VideoFlag }

    private fun rotationKind(item: MediaItem): RotationKind? = when (item.mimeType) {
        "image/jpeg" -> RotationKind.ExifFlag
        "image/png", "image/webp" -> RotationKind.Reencode
        "video/mp4", "video/3gpp", "video/quicktime" -> RotationKind.VideoFlag
        else -> null
    }

    /** Turns each item [degrees] clockwise (90, 180 or 270). */
    suspend fun rotate(context: Context, items: List<MediaItem>, degrees: Int): FileResult =
        withContext(Dispatchers.IO) {
            var done = 0
            var failed = 0
            for (item in items) {
                val kind = rotationKind(item) ?: continue
                val ok = runCatching {
                    when (kind) {
                        RotationKind.ExifFlag -> rotateExif(context, item, degrees)
                        RotationKind.Reencode -> rotatePixels(context, item, degrees)
                        RotationKind.VideoFlag -> rotateVideo(context, item, degrees)
                    }
                    // Re-index so MediaStore picks up the new orientation, size and modified time.
                    filePath(context, item)?.let { scan(context, File(it)) }
                }.isSuccess
                if (ok) done++ else failed++
            }
            FileResult(done, failed, needsAllFilesAccess = false)
        }

    private fun rotateExif(context: Context, item: MediaItem, degrees: Int) {
        context.contentResolver.openFileDescriptor(item.uri, "rw").use { pfd ->
            val exif = ExifInterface(pfd!!.fileDescriptor)
            // Many files carry 0 ("undefined"), which ExifInterface.rotate() leaves untouched:
            // the file was rewritten but the photo never turned. Treat it as upright instead.
            val current = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, rotatedOrientation(current, degrees).toString())
            exif.saveAttributes()
        }
    }

    /** EXIF orientation after turning [degrees] clockwise, keeping any mirroring. */
    private fun rotatedOrientation(current: Int, degrees: Int): Int {
        val upright = listOf(
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_ROTATE_270,
        )
        val mirrored = listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSVERSE,
        )
        val steps = ((degrees / 90) % 4 + 4) % 4
        return when (current) {
            in mirrored -> mirrored[(mirrored.indexOf(current) + steps) % 4]
            in upright -> upright[(upright.indexOf(current) + steps) % 4]
            else -> upright[steps] // undefined (0) counts as upright
        }
    }

    private fun rotatePixels(context: Context, item: MediaItem, degrees: Int) {
        val resolver = context.contentResolver
        val source = resolver.openInputStream(item.uri).use { BitmapFactory.decodeStream(it) }
            ?: error("can't decode")
        val rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees.toFloat()) }, true,
        )
        // Encode fully before touching the file, so a failure can't leave it truncated.
        val bytes = ByteArrayOutputStream().use { out ->
            val format = if (item.mimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.WEBP_LOSSLESS
            check(rotated.compress(format, 100, out))
            out.toByteArray()
        }
        resolver.openOutputStream(item.uri, "rwt").use { it!!.write(bytes) }
        source.recycle()
        rotated.recycle()
    }

    /**
     * Copies every audio/video track into a new MP4 with a new rotation flag, then writes it back
     * over the original. Samples are copied as-is: no re-encode, no quality loss.
     */
    private fun rotateVideo(context: Context, item: MediaItem, degrees: Int) {
        val resolver = context.contentResolver
        val current = MediaMetadataRetriever().use { r ->
            r.setDataSource(context, item.uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        }
        val temp = File.createTempFile("rotate", ".mp4", context.cacheDir)
        try {
            val extractor = MediaExtractor()
            resolver.openFileDescriptor(item.uri, "r").use { pfd -> extractor.setDataSource(pfd!!.fileDescriptor) }
            val muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint((current + degrees) % 360)
            val trackMap = HashMap<Int, Int>()
            var bufferSize = 1 shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(i)
                trackMap[i] = muxer.addTrack(format)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            check(trackMap.isNotEmpty())
            muxer.start()
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(trackMap.getValue(extractor.sampleTrackIndex), buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            extractor.release()
            resolver.openOutputStream(item.uri, "rwt").use { out -> temp.inputStream().use { it.copyTo(out!!) } }
        } finally {
            temp.delete()
        }
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
    fun filePath(context: Context, item: MediaItem): String? =
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

    /**
     * Renames an album's folder from [oldPath] to [newPath] (relative paths). With All files
     * access the folder itself is renamed, everything in it included, and rescanned; otherwise
     * its photos and videos move through MediaStore (the caller gets the write grant), leaving
     * the old folder behind with anything that isn't media.
     */
    suspend fun renameAlbum(context: Context, items: List<MediaItem>, oldPath: String, newPath: String): FileResult =
        withContext(Dispatchers.IO) {
            if (Environment.isExternalStorageManager()) {
                val root = Environment.getExternalStorageDirectory()
                val from = File(root, oldPath)
                val to = File(root, newPath)
                if (!to.exists() && from.renameTo(to)) {
                    val files = items.flatMap { listOf(File(from, it.name), File(to, it.name)) }
                    if (files.isNotEmpty()) scan(context, *files.toTypedArray())
                    return@withContext FileResult(done = items.size, failed = 0, needsAllFilesAccess = false)
                }
            }
            move(context, items, newPath)
        }

    fun displayName(context: Context, item: MediaItem): String? =
        context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
}
