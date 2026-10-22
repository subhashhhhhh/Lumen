package com.subhashh.lumen.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    /** All photos and videos, oldest first — the Library grid shows the newest at the bottom, like iOS Photos. */
    suspend fun loadLibrary(): List<MediaItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
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
        val sortOrder = "COALESCE(${MediaStore.MediaColumns.DATE_TAKEN}, " +
            "${MediaStore.MediaColumns.DATE_ADDED} * 1000) ASC"

        val items = ArrayList<MediaItem>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            args,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val takenAt = if (cursor.isNull(takenCol)) {
                    cursor.getLong(addedCol) * 1000
                } else {
                    cursor.getLong(takenCol)
                }
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(baseUri, id),
                    isVideo = isVideo,
                    takenAt = takenAt,
                    durationMs = cursor.getLong(durationCol),
                )
            }
        }
        items
    }
}
