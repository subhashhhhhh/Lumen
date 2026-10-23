package com.subhashh.lumen.data

import android.content.Context
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/** Changing when photos and videos were taken ("Adjust Date & Time"). */
object MediaDates {

    /** Formats ExifInterface can write the date into. */
    private val ExifWritable = setOf("image/jpeg", "image/png", "image/webp")

    /** Formats with their own date that Android can't rewrite, so changing them isn't offered as working. */
    private val Unwritable = setOf("image/heic", "image/heif", "image/avif")

    data class Result(val done: Int, val failed: Int, val needsAllFilesAccess: Boolean)

    /**
     * Moves each item's date by [deltaMs]. JPEG, PNG and WebP get the new capture date written
     * into their EXIF, so every app sees it; other files (videos, GIFs…) get their modified time
     * changed instead, which needs All files access. MediaStore is then rescanned so the new
     * dates show up. The caller must hold write access (a MediaStore write grant or All files).
     */
    suspend fun shift(context: Context, items: List<MediaItem>, deltaMs: Long): Result = withContext(Dispatchers.IO) {
        var done = 0
        var failed = 0
        var needsAccess = false
        val scanned = ArrayList<String>()
        for (item in items) {
            val newTime = item.takenAt + deltaMs
            val path = MediaFiles.filePath(context, item)
            val ok = when {
                item.mimeType in ExifWritable -> writeExifDate(context, item, newTime)
                item.mimeType in Unwritable -> false
                !Environment.isExternalStorageManager() -> {
                    needsAccess = true
                    false
                }
                else -> path?.let { File(it).setLastModified(newTime) } == true
            }
            if (ok) {
                done++
                path?.let(scanned::add)
            } else {
                failed++
            }
        }
        if (scanned.isNotEmpty()) scan(context, scanned)
        Result(done, failed, needsAccess)
    }

    /** Capture date, digitized date and file date, with the time zone the date falls in. */
    private fun writeExifDate(context: Context, item: MediaItem, time: Long): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            val stamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(time))
            val offset = offsetString(time)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
            exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
            // Without an offset, readers have to guess the time zone (MediaStore assumes UTC).
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
            exif.saveAttributes()
            true
        } ?: false
    }.getOrDefault(false)

    /** "+05:30" for this phone's time zone on that date (so summer time is right). */
    private fun offsetString(time: Long): String {
        val minutes = TimeZone.getDefault().getOffset(time) / 60_000
        val sign = if (minutes < 0) "-" else "+"
        val abs = kotlin.math.abs(minutes)
        return "%s%02d:%02d".format(Locale.US, sign, abs / 60, abs % 60)
    }

    private suspend fun scan(context: Context, paths: List<String>) = suspendCancellableCoroutine { cont ->
        var remaining = paths.size
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
            if (--remaining == 0 && cont.isActive) cont.resume(Unit)
        }
    }
}
