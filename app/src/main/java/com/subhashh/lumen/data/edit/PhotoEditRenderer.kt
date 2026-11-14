package com.subhashh.lumen.data.edit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

/** Draws a [PhotoEdit]. The editor's preview and the saved file both come from [draw]. */
object PhotoEditRenderer {

    /** The paint that carries [edit]'s colours. Make it once per edit, not once per frame. */
    fun paint(edit: PhotoEdit): Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = edit.colorMatrix()?.let(::ColorMatrixColorFilter)
    }

    /**
     * Draws the [region] of [edit]'s frame into a box [width] by [height] at the canvas's origin.
     * [source] may be any size: a small preview copy gives the same picture as the original.
     * [finished] adds what belongs to the final picture only (the vignette), which the crop view
     * leaves off since it shows more than the picture.
     */
    fun draw(
        canvas: Canvas,
        source: Bitmap,
        edit: PhotoEdit,
        region: CropRect,
        width: Float,
        height: Float,
        paint: Paint,
        finished: Boolean,
    ) {
        val (frameWidth, frameHeight) = edit.frameSize(source.width.toFloat(), source.height.toFloat())
        val matrix = Matrix().apply {
            // The source's centre to the origin, flip, turn, then onto the frame's centre.
            postTranslate(-source.width / 2f, -source.height / 2f)
            if (edit.flipped) postScale(-1f, 1f)
            postRotate(90f * edit.quarterTurns + edit.straighten)
            postTranslate(frameWidth / 2f, frameHeight / 2f)
            // The region of the frame, stretched over the box.
            postTranslate(-region.left * frameWidth, -region.top * frameHeight)
            postScale(width / (region.width * frameWidth), height / (region.height * frameHeight))
        }
        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.drawBitmap(source, matrix, paint)
        val vignette = edit.level(Adjustment.Vignette)
        if (finished && vignette > 0f) {
            val radius = hypot(width, height) / 2f
            val dark = Paint().apply {
                shader = RadialGradient(
                    width / 2f, height / 2f, radius,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((vignette * 215).toInt(), 0, 0, 0)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width, height, dark)
        }
        canvas.restore()
    }

    /** [source] with [edit] applied, cropped, at the source's own sharpness. */
    fun render(source: Bitmap, edit: PhotoEdit): Bitmap {
        val (width, height) = edit.outputSize(source.width, source.height)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(out), source, edit, edit.crop, width.toFloat(), height.toFloat(), paint(edit), finished = true)
        return out
    }
}

/** Saves an edited photo: as a new file beside the original, or over it. */
object PhotoEditSaver {

    private data class Output(val mime: String, val extension: String, val format: Bitmap.CompressFormat)

    @Suppress("DEPRECATION")
    private fun output(item: MediaItem): Output = when (item.mimeType) {
        "image/png" -> Output("image/png", "png", Bitmap.CompressFormat.PNG)
        "image/webp" -> Output(
            "image/webp", "webp",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP,
        )
        // Everything Android can't write (HEIC, AVIF, JPEG XL, RAW…) is saved as JPEG.
        else -> Output("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG)
    }

    /** Whether the edit can be written back into [item]'s own file: only the formats Android can write. */
    fun canReplace(item: MediaItem): Boolean =
        item.mimeType in setOf("image/jpeg", "image/png", "image/webp") && item.uri.scheme == "content"

    /** Photos the editor opens: anything Lumen shows as a still picture, outside Hidden's vault. */
    fun canEdit(item: MediaItem): Boolean =
        !item.isVideo && item.mimeType != "image/gif" && item.mimeType != "image/svg+xml" && !Vault.isVault(item)

    /**
     * [item]'s pixels, upright, no longer than [maxSide] on the long side (never enlarged), as a
     * bitmap that can be drawn anywhere. Through Coil, so every format Lumen shows can be edited.
     */
    suspend fun decode(context: Context, item: MediaItem, maxSide: Int): Bitmap? {
        val longest = max(item.width, item.height)
        val request = ImageRequest.Builder(context)
            .data(item.uri)
            .apply {
                // Only ever scaled down. A photo whose size MediaStore doesn't know (JPEG XL) comes whole.
                if (longest > maxSide) size(maxSide, maxSide).scale(Scale.FIT) else size(Size.ORIGINAL)
            }
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        return (SingletonImageLoader.get(context).execute(request).image as? BitmapImage)?.bitmap
    }

    /** A new photo next to [item] (or in Pictures/Edited), named after it. Returns whether it was saved. */
    suspend fun saveCopy(context: Context, item: MediaItem, edit: PhotoEdit): Boolean = withContext(Dispatchers.IO) {
        val output = output(item)
        val file = encode(context, item, edit, output) ?: return@withContext false
        try {
            val name = "${item.name.substringBeforeLast('.', item.name)}_edited.${output.extension}"
            val resolver = context.contentResolver
            // Next to the original if MediaStore lets Lumen add files there, else Pictures/Edited.
            for (folder in listOf(item.relativePath, "Pictures/Edited/")) {
                val uri = runCatching {
                    resolver.insert(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, output.mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                            // Keeps the copy beside its original in the timeline.
                            put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        },
                    )
                }.getOrNull() ?: continue
                val ok = runCatching {
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
                if (!ok) {
                    resolver.delete(uri, null, null)
                    return@withContext false
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return@withContext true
            }
            false
        } finally {
            file.delete()
        }
    }

    /**
     * Writes the edit over [item]'s own file. The caller must hold write access to it. The new
     * picture is fully made first, so a failure part-way leaves the original as it was.
     */
    suspend fun replace(context: Context, item: MediaItem, edit: PhotoEdit): Boolean = withContext(Dispatchers.IO) {
        if (!canReplace(item)) return@withContext false
        val file = encode(context, item, edit, output(item)) ?: return@withContext false
        try {
            runCatching {
                context.contentResolver.openOutputStream(item.uri, "rwt").use { out ->
                    checkNotNull(out) { "Couldn't write ${item.name}" }
                    file.inputStream().use { it.copyTo(out) }
                }
                // MediaStore reads the new size and modified time, which also refreshes every cached image.
                MediaFiles.rescan(context, item)
            }.isSuccess
        } finally {
            file.delete()
        }
    }

    /** The edited picture as a finished file in Lumen's cache, EXIF included; null if it couldn't be made. */
    private suspend fun encode(context: Context, item: MediaItem, edit: PhotoEdit, output: Output): File? {
        val source = runCatching { decode(context, item, MaxSide) }.getOrNull() ?: return null
        val file = File.createTempFile("edit", ".${output.extension}", context.cacheDir)
        val ok = runCatching {
            val edited = PhotoEditRenderer.render(source, edit)
            source.recycle()
            file.outputStream().use { check(edited.compress(output.format, Quality, it)) }
            val width = edited.width
            val height = edited.height
            edited.recycle()
            copyExif(context, item, file, width, height)
        }.isSuccess
        if (!ok) file.delete()
        return file.takeIf { ok }
    }

    /** Camera, date and place carry over. The pixels are upright now, so the orientation resets. */
    private fun copyExif(context: Context, item: MediaItem, file: File, width: Int, height: Int) {
        runCatching {
            val source = context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it) }
            val exif = ExifInterface(file)
            if (source != null) {
                for (tag in ImageExport.CopiedTags) source.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
            }
            if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                // Without it a rescan would date the photo to the moment it was edited.
                exif.setAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(item.takenAt)),
                )
            }
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
            exif.saveAttributes()
        }
    }

    /**
     * The longest side an edit is saved at. Bigger photos (50 MP phone cameras) are scaled to it:
     * the original and the edited picture are both in memory at once while saving.
     */
    const val MaxSide = 6144
    private const val Quality = 95
}
