package com.subhashh.lumen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Coil model for a grid cell or album cover: [item] at [sizePx] (the cell's size bucket).
 *
 * Lumen keeps these on disk, so after the first time a thumbnail is a small JPEG read straight
 * back, even after Android has closed the app and its memory cache is gone. Before this, cells
 * too big for Android's own thumbnails (three columns and fewer) decoded every original photo
 * again on every launch.
 */
data class GridThumbnail(val item: MediaItem, val sizePx: Int)

class GridThumbnailFetcher(
    private val data: GridThumbnail,
    private val options: Options,
    private val cache: DiskCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // One entry per file version and size: a rotate or edit changes the version.
        val key = "v1:${data.item.id}@${data.item.modified}:${data.sizePx}"
        cache.openSnapshot(key)?.use { snapshot ->
            BitmapFactory.decodeFile(snapshot.data.toFile().path)?.let { cached ->
                return@withContext ImageFetchResult(cached.asImage(), isSampled = true, dataSource = DataSource.DISK)
            }
        }
        val bitmap = create(options.context, data.item, data.sizePx)
        save(key, bitmap)
        ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun save(key: String, bitmap: Bitmap) {
        // Null when another request is writing the same thumbnail right now; it'll be there next time.
        val editor = cache.openEditor(key) ?: return
        try {
            val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            editor.data.toFile().outputStream().use { software.compress(Bitmap.CompressFormat.JPEG, JpegQuality, it) }
            editor.commit()
        } catch (e: Exception) {
            editor.abort()
        }
    }

    class Factory(context: Context) : Fetcher.Factory<GridThumbnail> {
        // Created on first use, off the main thread; size-capped, least recently used goes first.
        private val cache by lazy {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "thumbnails").toOkioPath())
                .maxSizeBytes(MaxCacheBytes)
                .build()
        }

        override fun create(data: GridThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            GridThumbnailFetcher(data, options, cache)
    }

    private companion object {
        const val JpegQuality = 85
        const val MaxCacheBytes = 384L * 1024 * 1024

        /** Makes the thumbnail: Android's thumbnail cache where it's sharp enough, else the original. */
        fun create(context: Context, item: MediaItem, sizePx: Int): Bitmap {
            val size = Size(sizePx, sizePx)
            val isFile = item.uri.scheme == "file"
            return when {
                // Hidden files aren't in MediaStore: no system thumbnails.
                isFile && item.isVideo -> ThumbnailUtils.createVideoThumbnail(File(item.uri.path!!), size, null)
                isFile -> decodeOriginal(ImageDecoder.createSource(File(item.uri.path!!)), sizePx)
                // Video frames come from the system at any size; small cells don't need the original.
                item.isVideo || sizePx <= SystemThumbnailMaxPx -> context.contentResolver.loadThumbnail(item.uri, size, null)
                else -> decodeOriginal(ImageDecoder.createSource(context.contentResolver, item.uri), sizePx)
            }
        }

        /** Decodes just big enough for the shorter side to fill a [sizePx] square crop. EXIF rotation applied. */
        fun decodeOriginal(source: ImageDecoder.Source, sizePx: Int): Bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val shorter = minOf(info.size.width, info.size.height).coerceAtLeast(1)
                val scale = sizePx.toFloat() / shorter
                if (scale < 1f) {
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).roundToInt()),
                        max(1, (info.size.height * scale).roundToInt()),
                    )
                }
                // Software, so it can be written to the cache.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

        /** Matches ImageKeys.SystemThumbnailMaxPx: above it, Android's thumbnails look soft. */
        const val SystemThumbnailMaxPx = 320
    }
}
