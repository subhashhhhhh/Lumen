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
data class GridThumbnail(
    val item: MediaItem,
    val sizePx: Int,
    /** False for screen-size video stills: big, and only worth keeping in memory. */
    val persist: Boolean = true,
)

class GridThumbnailFetcher(
    private val data: GridThumbnail,
    private val options: Options,
    private val cache: DiskCache,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // One entry per file version and size: a rotate or edit changes the version. Videos are
        // v2: first frames now, where v1 had Android's mid-video pick.
        // Images that can be see-through are t1: squares behind them now (#27), where v1 was black.
        val version = when {
            data.item.isVideo -> "v2"
            Checkerboard.mayBeTransparent(data.item) -> "t1"
            else -> "v1"
        }
        val key = "$version:${data.item.id}@${data.item.modified}:${data.sizePx}"
        if (data.persist) cache.openSnapshot(key)?.use { snapshot ->
            BitmapFactory.decodeFile(snapshot.data.toFile().path)?.let { cached ->
                return@withContext ImageFetchResult(cached.asImage(), isSampled = true, dataSource = DataSource.DISK)
            }
        }
        val decoded = if (data.item.mimeType in BundledDecoderTypes || com.subhashh.lumen.data.vault.Vault.isVault(data.item)) {
            decodeWithLoader(data.item, data.sizePx)
        } else {
            create(options.context, data.item, data.sizePx)
        }
        val bitmap = if (Checkerboard.mayBeTransparent(data.item)) Checkerboard.under(decoded) else decoded
        if (data.persist) save(key, bitmap)
        ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /**
     * AVIF and JPEG XL go through Coil's own pipeline, where Lumen's bundled decoders live: the
     * phone's AVIF decoder (used by ImageDecoder and Android's thumbnails) fails on some devices,
     * and no phone has a JPEG XL one.
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

    class Factory(private val context: Context) : Fetcher.Factory<GridThumbnail> {
        override fun create(data: GridThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            GridThumbnailFetcher(data, options, disk(context), imageLoader)

        companion object {
            @Volatile private var shared: DiskCache? = null

            /** One cache for the process, created on first use (off the main thread); size-capped, least recently used goes first. */
            fun disk(context: Context): DiskCache = shared ?: synchronized(this) {
                shared ?: DiskCache.Builder()
                    .directory(File(context.applicationContext.cacheDir, "thumbnails").toOkioPath())
                    .maxSizeBytes(MaxCacheBytes)
                    .build()
                    .also { shared = it }
            }

            /** Settings → Storage: empties the thumbnail cache through its own API (it stays open while Lumen runs). */
            fun clear(context: Context) = disk(context).clear()
        }
    }

    private companion object {
        const val JpegQuality = 85
        val BundledDecoderTypes = setOf("image/avif", "image/jxl")
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
                // Android's thumbnails are JPEG: see-through parts come out black. From the original.
                Checkerboard.mayBeTransparent(item) -> decodeOriginal(ImageDecoder.createSource(context.contentResolver, item.uri), sizePx)
                // Small cells don't need the original: Android's thumbnail cache is sharp enough.
                sizePx <= SystemThumbnailMaxPx -> context.contentResolver.loadThumbnail(item.uri, size, null)
                else -> decodeOriginal(ImageDecoder.createSource(context.contentResolver, item.uri), sizePx)
            }
        }

        /**
         * Frame 0, scaled so its shorter side covers a [sizePx] square. The box is square and as
         * big as the longer side needs, so the shape comes out right whichever way the video is
         * rotated. Null if the video can't be read; the caller falls back to Android's thumbnail.
         *
         * Decoded in software: phones run only a few hardware decoders at once, and a thumbnail
         * taking one makes Android take one back from Lumen, sometimes the playing video's (it
         * stopped while swiping through reels). Videos the software decoder can't do go through
         * MediaMetadataRetriever (hardware), one at a time.
         */
        @OptIn(com.subhashh.lumen.ai.LumenAiEngine::class)
        fun firstFrame(context: Context, item: MediaItem, sizePx: Int): Bitmap? {
            val w = item.width
            val h = item.height
            val box = if (w > 0 && h > 0) (sizePx.toLong() * maxOf(w, h) / minOf(w, h)).toInt() else sizePx
            runCatching { com.subhashh.lumen.ai.KeyframeDecoder(context, item.uri, software = true).firstFrame(box) }
                .onFailure { android.util.Log.i("LumenThumbs", "Software first frame failed for ${item.name}: ${it.message}") }
                .getOrNull()?.let { return it }
            return synchronized(HardwareFrames) { retrieverFrame(context, item, sizePx) }
        }

        /** Hardware first frames, one at a time, so they never hold more than one decoder. */
        private val HardwareFrames = Any()

        private fun retrieverFrame(context: Context, item: MediaItem, sizePx: Int): Bitmap? = runCatching {
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
