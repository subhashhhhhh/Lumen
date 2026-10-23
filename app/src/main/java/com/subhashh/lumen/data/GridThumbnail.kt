package com.subhashh.lumen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
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
import coil3.BitmapImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
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
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // One entry per file version and size: a rotate or edit changes the version. Videos are
        // v2: first frames now, where v1 had Android's mid-video pick.
        val key = "${if (data.item.isVideo) "v2" else "v1"}:${data.item.id}@${data.item.modified}:${data.sizePx}"
        cache.openSnapshot(key)?.use { snapshot ->
            BitmapFactory.decodeFile(snapshot.data.toFile().path)?.let { cached ->
                return@withContext ImageFetchResult(cached.asImage(), isSampled = true, dataSource = DataSource.DISK)
            }
        }
        val bitmap = if (data.item.mimeType == "image/avif") {
            decodeWithLoader(data.item, data.sizePx)
        } else {
            create(options.context, data.item, data.sizePx)
        }
        save(key, bitmap)
        ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /**
     * AVIF goes through Coil's own pipeline, where Lumen's bundled AVIF decoder lives; the
     * phone's decoder (used by ImageDecoder and Android's thumbnails) fails on some devices.
     */
    private suspend fun decodeWithLoader(item: MediaItem, sizePx: Int): Bitmap {
        val result = imageLoader.execute(
            ImageRequest.Builder(options.context)
                .data(item.uri)
                .size(sizePx, sizePx)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build(),
        )
        return (result.image as? BitmapImage)?.bitmap ?: error("Couldn't decode ${item.uri}")
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
            GridThumbnailFetcher(data, options, cache, imageLoader)
    }

    private companion object {
        const val JpegQuality = 85
        const val MaxCacheBytes = 384L * 1024 * 1024

        /** Makes the thumbnail: Android's thumbnail cache where it's sharp enough, else the original. */
        fun create(context: Context, item: MediaItem, sizePx: Int): Bitmap {
            val size = Size(sizePx, sizePx)
            val isFile = item.uri.scheme == "file"
            return when {
                // The first frame, which is what playback starts on, so opening a video from the
                // grid doesn't jump to a different picture. Android's own pick is often mid-video.
                item.isVideo -> firstFrame(context, item, sizePx)
                    ?: if (isFile) ThumbnailUtils.createVideoThumbnail(File(item.uri.path!!), size, null)
                    else context.contentResolver.loadThumbnail(item.uri, size, null)
                // Hidden files aren't in MediaStore: no system thumbnails.
                isFile -> decodeOriginal(ImageDecoder.createSource(File(item.uri.path!!)), sizePx)
                // Small cells don't need the original: Android's thumbnail cache is sharp enough.
                sizePx <= SystemThumbnailMaxPx -> context.contentResolver.loadThumbnail(item.uri, size, null)
                else -> decodeOriginal(ImageDecoder.createSource(context.contentResolver, item.uri), sizePx)
            }
        }

        /**
         * Frame 0, scaled so its shorter side covers a [sizePx] square. The box is square and as
         * big as the longer side needs, so the shape comes out right whichever way the video is
         * rotated. Null if the video can't be read; the caller falls back to Android's thumbnail.
         */
        fun firstFrame(context: Context, item: MediaItem, sizePx: Int): Bitmap? = runCatching {
            MediaMetadataRetriever().use { retriever ->
                if (item.uri.scheme == "file") retriever.setDataSource(item.uri.path) else retriever.setDataSource(context, item.uri)
                fun int(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
                val w = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val box = if (w > 0 && h > 0) (sizePx.toLong() * maxOf(w, h) / minOf(w, h)).toInt() else sizePx
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, box, box)
            }
        }.getOrNull()

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
