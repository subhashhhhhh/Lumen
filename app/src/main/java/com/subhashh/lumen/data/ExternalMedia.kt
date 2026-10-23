package com.subhashh.lumen.data

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos and videos other apps hand to Lumen to open ("Open with", camera review). */
object ExternalMedia {

    /** A Uri points into MediaStore when Android's media provider serves it. */
    fun isMediaStore(uri: Uri) = uri.authority == MediaStore.AUTHORITY

    /**
     * Everything the viewer needs about [uri], read from whatever the sending app offers: its
     * type, name and size, and the picture's own shape. Null when it isn't a photo or video we
     * can read (no permission, gone, or another type).
     */
    suspend fun item(context: Context, uri: Uri, typeHint: String?): MediaItem? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = typeHint?.takeIf { it.startsWith("image/") || it.startsWith("video/") }
                ?: resolver.getType(uri)
                ?: return@runCatching null
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@runCatching null
            val isVideo = mime.startsWith("video/")

            var name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
            if (uri.scheme == "content") {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { name = it }
                }
            }

            var width = 0
            var height = 0
            var durationMs = 0L
            var takenAt = System.currentTimeMillis()
            if (isVideo) {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(context, uri)
                    fun int(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
                    val sideways = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 == 90
                    val w = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    width = if (sideways) h else w
                    height = if (sideways) w else h
                    durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val orientation = runCatching {
                    resolver.openInputStream(uri)?.use {
                        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
                val sideways = orientation in setOf(
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                    ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
                )
                width = (if (sideways) bounds.outHeight else bounds.outWidth).coerceAtLeast(0)
                height = (if (sideways) bounds.outWidth else bounds.outHeight).coerceAtLeast(0)
            }

            // A MediaStore item keeps its real id, so details and dates come from MediaStore.
            val mediaStoreId = if (isMediaStore(uri)) runCatching { ContentUris.parseId(uri) }.getOrNull() else null
            if (mediaStoreId != null) {
                resolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val taken = c.getLong(0)
                        val modified = c.getLong(1)
                        takenAt = if (taken > 0) taken else if (modified > 0) modified * 1000 else takenAt
                    }
                }
            }

            MediaItem(
                // Not in MediaStore: a negative id that can't collide with one.
                id = mediaStoreId ?: (-(uri.toString().hashCode().toLong() and 0x7fffffffL) - 1),
                uri = uri,
                mimeType = mime,
                isVideo = isVideo,
                takenAt = takenAt,
                durationMs = durationMs,
                isFavorite = false,
                relativePath = "",
                bucketId = 0,
                bucketName = "",
                width = width,
                height = height,
                modified = takenAt / 1000,
                name = name,
            )
        }.getOrNull()
    }
}
