package com.subhashh.lumen.data

import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.media.ExifInterface
import android.media.MediaFormat
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    /** All photos and videos, oldest first — grids show the newest at the bottom, like iOS Photos. */
    suspend fun loadLibrary(): List<MediaItem> = query(trashed = false)

    /** Items in the system trash (Recently Deleted), oldest first. */
    suspend fun loadTrash(): List<MediaItem> = query(trashed = true)

    private suspend fun query(trashed: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
        )
        // MediaStore classifies TypeScript sources (.ts) as MPEG-TS video. Real transport streams
        // get a duration when scanned; source files never do, so drop mp2ts rows without one.
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND NOT (" +
            "${MediaStore.MediaColumns.MIME_TYPE} = 'video/mp2ts' AND " +
            "IFNULL(${MediaStore.MediaColumns.DURATION}, 0) = 0)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val queryArgs = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            if (trashed) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }

        val hdrIds = hdrVideoIds()
        val items = ArrayList<MediaItem>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            queryArgs,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val favoriteCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val rotated = cursor.getInt(orientationCol).let { it == 90 || it == 270 }
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(baseUri, id),
                    mimeType = cursor.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                    isVideo = isVideo,
                    takenAt = captureTime(
                        // MediaStore often leaves RAW files undated (CR2, NEF…); their EXIF knows.
                        takenMs = cursor.getLong(takenCol).takeIf { it > 0 }
                            ?: rawCaptureTime(cursor.getString(mimeCol), cursor.getString(nameCol), id, isVideo)
                            ?: 0L,
                        modifiedSec = cursor.getLong(modifiedCol),
                        addedSec = cursor.getLong(addedCol),
                    ),
                    durationMs = cursor.getLong(durationCol),
                    isFavorite = cursor.getInt(favoriteCol) == 1,
                    relativePath = cursor.getString(pathCol).orEmpty(),
                    bucketId = cursor.getLong(bucketIdCol),
                    bucketName = cursor.getString(bucketNameCol) ?: "Internal storage",
                    width = if (rotated) cursor.getInt(heightCol) else cursor.getInt(widthCol),
                    height = if (rotated) cursor.getInt(widthCol) else cursor.getInt(heightCol),
                    modified = cursor.getLong(modifiedCol),
                    name = cursor.getString(nameCol).orEmpty(),
                    size = cursor.getLong(sizeCol),
                    hdrVideo = isVideo && id in hdrIds,
                )
            }
        }
        items.sortWith(compareBy<MediaItem> { it.takenAt }.thenBy { it.id })
        items
    }

    /**
     * A RAW file's capture time from its EXIF, for the RAW files MediaStore didn't date.
     * Only RAW: reading every undated file this way would slow the library down, and the
     * rest (screenshots, downloads) has no EXIF date anyway.
     */
    private fun rawCaptureTime(mime: String?, name: String?, id: Long, isVideo: Boolean): Long? {
        if (isVideo || mime?.startsWith("image/x-") != true) return null
        if (name?.substringAfterLast('.', "")?.lowercase() !in RawExtensions) return null
        return runCatching {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val stamp = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: return@use null
                val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                val format = java.text.SimpleDateFormat(if (offset != null) "yyyy:MM:dd HH:mm:ssXXX" else "yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                format.parse(stamp + (offset ?: ""))?.time
            }
        }.getOrNull()
    }

    /**
     * Ids of HDR videos (PQ or HLG transfer, as MediaFormat numbers them). Only the video table
     * has the colour columns; asking the Files table for them throws.
     */
    private fun hdrVideoIds(): Set<Long> = runCatching {
        val ids = HashSet<Long>()
        context.contentResolver.query(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Video.VideoColumns._ID),
            "${MediaStore.Video.VideoColumns.COLOR_TRANSFER} IN (?, ?)",
            arrayOf(MediaFormat.COLOR_TRANSFER_ST2084.toString(), MediaFormat.COLOR_TRANSFER_HLG.toString()),
            null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        ids
    }.getOrDefault(emptySet())

    /**
     * EXIF capture time (MediaStore's DATE_TAKEN) when the file has one, else the file's modified
     * time — same as Fossify and Aves. Never DATE_ADDED first: it resets whenever MediaStore
     * rebuilds its index, which would date a whole library to that day.
     */
    private fun captureTime(takenMs: Long, modifiedSec: Long, addedSec: Long): Long = when {
        takenMs > 0 -> takenMs
        modifiedSec > 0 -> modifiedSec * 1000
        else -> addedSec * 1000
    }
}
