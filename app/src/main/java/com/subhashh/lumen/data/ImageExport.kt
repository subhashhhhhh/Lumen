package com.subhashh.lumen.data

import com.subhashh.lumen.R
import androidx.annotation.StringRes
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Resize / Convert: new copies of photos at another size or in another format. Originals are never touched. */
object ImageExport {

    /** How big the copy is. Named sizes cap the longer side and never enlarge. */
    enum class Target(@StringRes val label: Int, val suffix: String, val longSide: Int?, val percent: Int?) {
        Original(R.string.data_export_original, "copy", null, null),
        UHD(R.string.data_export_4k, "4k", 3840, null),
        FullHD(R.string.data_export_1080p, "1080p", 1920, null),
        HD(R.string.data_export_720p, "720p", 1280, null),
        Half(R.string.data_export_50, "50", null, 50),
        Quarter(R.string.data_export_25, "25", null, 25),
    }

    enum class Format(@StringRes val label: Int, val mime: String?, val extension: String?) {
        Same(R.string.data_export_same, null, null),
        Jpeg(R.string.data_export_jpeg, "image/jpeg", "jpg"),
        Png(R.string.data_export_png, "image/png", "png"),
        Webp(R.string.data_export_webp, "image/webp", "webp"),
    }

    enum class Quality(@StringRes val label: Int, val value: Int) { High(R.string.data_detail_high, 95), Medium(R.string.data_size_medium, 85), Small(R.string.data_size_small, 70) }

    /** Formats Lumen can write; anything else (HEIC, AVIF, RAW…) becomes JPEG under "Same as Original". */
    private val Writable = mapOf("image/jpeg" to Format.Jpeg, "image/png" to Format.Png, "image/webp" to Format.Webp)

    data class Result(val saved: Int, val failed: Int, val folders: Set<String>)

    /** Saves a resized / converted copy of each photo next to it (or in Pictures/Resized). */
    suspend fun export(
        context: Context,
        items: List<MediaItem>,
        target: Target,
        format: Format,
        quality: Quality,
    ): Result = withContext(Dispatchers.IO) {
        var saved = 0
        var failed = 0
        val folders = HashSet<String>()
        for (item in items) {
            val out = if (format == Format.Same) Writable[item.mimeType] ?: Format.Jpeg else format
            val folder = runCatching { save(context, item, target, out, quality) }.getOrNull()
            if (folder != null) {
                saved++
                folders += folder
            } else {
                failed++
            }
        }
        Result(saved, failed, folders)
    }

    /** One copy; returns the folder it went into, or null on failure. */
    private suspend fun save(context: Context, item: MediaItem, target: Target, format: Format, quality: Quality): String? {
        val bitmap = decode(context, item, target) ?: return null
        val base = item.name.substringBeforeLast('.', item.name)
        val sameType = format.mime == item.mimeType
        // IMG_1_1080p.jpg; a straight conversion keeps the name (IMG_1.jpg from IMG_1.heic).
        val name = when {
            target != Target.Original -> "${base}_${target.suffix}.${format.extension}"
            sameType -> "${base}_copy.${format.extension}"
            else -> "$base.${format.extension}"
        }
        // Next to the original if MediaStore lets us add files there, else Pictures/Resized.
        for (folder in listOf(item.relativePath, "Pictures/Resized/")) {
            val uri = runCatching {
                context.contentResolver.insert(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                        // Keeps the copy beside its original in the timeline.
                        put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                )
            }.getOrNull() ?: continue
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(compressFormat(format), quality.value, stream)
                } == true
            }.getOrDefault(false)
            if (ok && format == Format.Jpeg) copyExif(context, item, uri)
            if (!ok) {
                context.contentResolver.delete(uri, null, null)
                return null
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return folder
        }
        return null
    }

    /** Decoded through Coil, so every format Lumen can show (HEIC, AVIF, RAW…) can be converted. */
    private suspend fun decode(context: Context, item: MediaItem, target: Target): Bitmap? {
        val longest = max(item.width, item.height).takeIf { it > 0 }
        val cap = when {
            target.longSide != null -> longest?.let { minOf(it, target.longSide) } ?: target.longSide
            target.percent != null -> longest?.let { (it * target.percent / 100f).roundToInt() }
            else -> null
        }
        val request = ImageRequest.Builder(context)
            .data(item.uri)
            .apply { if (cap != null) size(cap, cap).scale(Scale.FIT) else size(Size.ORIGINAL) }
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        return (SingletonImageLoader.get(context).execute(request).image as? BitmapImage)?.bitmap
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(format: Format): Bitmap.CompressFormat = when (format) {
        Format.Png -> Bitmap.CompressFormat.PNG
        Format.Webp -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }

    /** Camera, date and location carry over to JPEG copies; the pixels are already upright, so orientation resets. */
    private fun copyExif(context: Context, item: MediaItem, uri: android.net.Uri) {
        runCatching {
            val source = context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it) }
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                if (source != null) {
                    for (tag in CopiedTags) source.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
                }
                if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                    exif.setAttribute(
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(item.takenAt)),
                    )
                }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
            }
        }
    }

    private val CopiedTags = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    )
}
