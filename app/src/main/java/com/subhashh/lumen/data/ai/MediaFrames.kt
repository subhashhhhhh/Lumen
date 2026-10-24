package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Size
import com.subhashh.lumen.data.MediaItem
import kotlin.math.abs

/**
 * The stills Smart Search looks at. A photo: Android's cached thumbnail. A video: one frame a
 * second up to two minutes (at the nearest keyframes), then [MaxFrames] spread evenly, so a long
 * video costs about what a two-minute one does. Frames that look like the last one kept are
 * skipped before the model ever sees them: a talking head or a still shot doesn't fill the index
 * with copies.
 */
object MediaFrames {
    /** Big enough for every model's input (224–256 px) and served from Android's thumbnail cache. */
    private const val Px = 320
    private const val MaxFrames = 120
    private const val EverySecondUpTo = 120_000L
    /** Mean absolute difference of 16×16 grey thumbnails (0–255) below which a frame is "the same". */
    private const val SameFrame = 6

    /**
     * A photo at up to [maxSide] on its long side, for reading its text: thumbnails are too
     * small for chat-sized letters. Software pixels, first frame of an animation.
     */
    fun forReading(context: Context, item: MediaItem, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            if (long > maxSide) {
                val scale = maxSide.toFloat() / long
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * scale).toInt()),
                    maxOf(1, (info.size.height * scale).toInt()),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** A photo's one still: Android's cached thumbnail. */
    fun thumbnail(context: Context, item: MediaItem): Bitmap =
        context.contentResolver.loadThumbnail(item.uri, Size(Px, Px), null)

    /**
     * Calls [onFrame] with each frame's time (0 for a photo) and pixels, at most [px] on their
     * long side; stops if it returns false.
     */
    suspend fun forEach(context: Context, item: MediaItem, px: Int = Px, onFrame: suspend (atMs: Long, frame: Bitmap) -> Boolean) {
        if (!item.isVideo) {
            onFrame(0, thumbnail(context, item))
            return
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: item.durationMs
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            var last: IntArray? = null
            for (at in keyframes(context, item, times(duration))) {
                val frame = retriever.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px, params)
                    ?: continue
                val signature = signature(frame)
                if (last != null && difference(last, signature) < SameFrame) {
                    frame.recycle()
                    continue
                }
                last = signature
                if (!onFrame(at, frame)) return
            }
        } finally {
            retriever.release()
        }
    }

    /** Frame times in ms: every second for short videos, else evenly spread mid-segment points. */
    fun times(durationMs: Long): List<Long> = when {
        durationMs <= 0 -> listOf(0L)
        durationMs <= EverySecondUpTo -> (0 until durationMs step 1000).toList()
        else -> List(MaxFrames) { i -> (durationMs * (2 * i + 1)) / (2 * MaxFrames) }
    }

    /**
     * The keyframes nearest [times], each once. Frames are taken at keyframes, not the exact
     * moment: decoding forward to the exact frame took ~0.8 s a frame, twice the model. Phone
     * videos have a keyframe every 1–3 s, so a one-a-second plan lands on the same keyframe
     * repeatedly; the extractor reads that from the file's index without decoding anything.
     */
    private fun keyframes(context: Context, item: MediaItem, times: List<Long>): List<Long> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, item.uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return times
            extractor.selectTrack(track)
            times.mapNotNull { at ->
                extractor.seekTo(at * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                extractor.sampleTime.takeIf { it >= 0 }?.let { it / 1000 }
            }.distinct()
        } catch (e: Exception) {
            times
        } finally {
            extractor.release()
        }
    }

    private fun signature(frame: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(frame, 16, 16, true)
        val pixels = IntArray(256)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (small !== frame) small.recycle()
        return IntArray(256) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
    }

    private fun difference(a: IntArray, b: IntArray): Int {
        var sum = 0
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / a.size
    }
}
