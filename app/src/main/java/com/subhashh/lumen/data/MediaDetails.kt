package com.subhashh.lumen.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class GeoPoint(val latitude: Double, val longitude: Double)

@Immutable
data class CameraInfo(
    val device: String?,
    val lens: String?,
    val aperture: Double?,
    /** Seconds. */
    val exposure: Double?,
    val iso: Int?,
    val focalLengthMm: Double?,
)

@Immutable
data class MediaDetails(
    val fileName: String,
    val folder: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val camera: CameraInfo?,
    val location: GeoPoint?,
    /** False when GPS may exist but Android redacted it (ACCESS_MEDIA_LOCATION not granted). */
    val locationReadable: Boolean,
    val frameRate: Float?,
)

/** Reads per-item details on demand; too slow to do for every grid item. */
object MediaDetailsLoader {

    suspend fun load(context: Context, item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = ""
        val folder = item.relativePath
        var size = 0L
        var width = 0
        var height = 0
        var orientation = 0
        resolver.query(
            ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), item.id),
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.ORIENTATION,
            ),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0).orEmpty()
                size = c.getLong(1)
                width = c.getInt(2)
                height = c.getInt(3)
                orientation = c.getInt(4)
            }
        }
        // MediaStore reports stored dimensions; show them as the photo is displayed.
        if (orientation == 90 || orientation == 270) {
            width = height.also { height = width }
        }

        // Without ACCESS_MEDIA_LOCATION, Android strips GPS from the stream we're given.
        val canReadLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val sourceUri = if (canReadLocation) MediaStore.setRequireOriginal(item.uri) else item.uri

        var camera: CameraInfo? = null
        var location: GeoPoint? = null
        var frameRate: Float? = null

        if (item.isVideo) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, sourceUri)
                    location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        ?.let(::parseIso6709)
                    val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                        ?.toLongOrNull()
                    if (frames != null && item.durationMs > 0) frameRate = frames * 1000f / item.durationMs
                }
            }
        } else {
            runCatching {
                resolver.openInputStream(sourceUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    location = exif.latLong?.let { GeoPoint(it[0], it[1]) }
                    camera = CameraInfo(
                        device = deviceName(
                            exif.getAttribute(ExifInterface.TAG_MAKE),
                            exif.getAttribute(ExifInterface.TAG_MODEL),
                        ),
                        lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf { it.isNotEmpty() },
                        aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 },
                        exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 },
                        iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
                        focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 },
                    ).takeIf { it.device != null || it.aperture != null || it.iso != null }
                }
            }
        }

        MediaDetails(
            fileName = name,
            folder = folder,
            sizeBytes = size,
            width = width,
            height = height,
            mimeType = item.mimeType,
            camera = camera,
            location = location,
            locationReadable = canReadLocation,
            frameRate = frameRate,
        )
    }

    /** "Google Pixel 9" rather than "Google Google Pixel 9" when the model repeats the make. */
    private fun deviceName(make: String?, model: String?): String? {
        val m = make?.trim().orEmpty()
        val d = model?.trim().orEmpty()
        return when {
            d.isEmpty() -> m.ifEmpty { null }
            m.isEmpty() || d.startsWith(m, ignoreCase = true) -> d
            else -> "$m $d"
        }
    }

    /** Video location metadata, e.g. "+37.4219-122.0840/" or "+37.4219-122.0840+010.000/". */
    private fun parseIso6709(value: String): GeoPoint? {
        val match = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(value) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat == 0.0 && lon == 0.0) null else GeoPoint(lat, lon)
    }
}
