package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.ContinuationInterceptor
import com.subhashh.lumen.data.MediaItem
import kotlin.math.abs

/**
 * The stills Smart Search looks at. A photo: Android's cached thumbnail. A video: one frame a
 * second up to two minutes (exact moments, not just keyframes), then [MaxFrames] spread evenly, so a long
 * video costs about what a two-minute one does. Frames that look like the last one kept are
 * skipped before the model ever sees them: a talking head or a still shot doesn't fill the index
 * with copies.
 */
object MediaFrames {
    /** Big enough for every model's input (224–256 px) and served from Android's thumbnail cache. */
    private const val Px = 320
    private const val MaxFrames = 120
    private const val EverySecondUpTo = 120_000L
    /** Videos up to this long can be read more than once a second ([forEach]'s perSecond). */
    private const val DenseUpTo = 60_000L
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
     * long side; stops if it returns false. [skipSame]: leave out frames that look like the last
     * one (Smart Search, faces); the text pass wants them all, since a new caption over the same
     * shot barely changes a small thumbnail, and it skips unchanged lines itself.
     */
    suspend fun forEach(
        context: Context,
        item: MediaItem,
        px: Int = Px,
        skipSame: Boolean = true,
        /** Frames a second for videos up to [DenseUpTo]: the text pass reads captions twice a second. */
        perSecond: Int = 1,
        onFrame: suspend (atMs: Long, frame: Bitmap) -> Boolean,
    ) {
        if (!item.isVideo) {
            onFrame(0, thumbnail(context, item))
            return
        }
        val duration = item.durationMs.takeIf { it > 0 } ?: MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(context, item.uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                r.release()
            }
        }
        // Exact moments: the hardware decoder runs forward from each keyframe ([KeyframeDecoder]).
        val wanted = times(duration, perSecond)
        var last: IntArray? = null
        var stop = false
        /** Skips a frame like the last one kept; else hands it on. False to stop. */
        suspend fun deliver(at: Long, frame: Bitmap): Boolean {
            val signature = signature(frame)
            if (skipSame && last != null && difference(last!!, signature) < SameFrame) {
                frame.recycle()
                return true
            }
            last = signature
            return onFrame(at, frame).also { if (!it) stop = true }
        }
        // The hardware decoder, once for the whole video ([KeyframeDecoder]); one call on one
        // thread, as its GL context needs, handing each frame on before decoding the next.
        var lastAt = -1L
        // The hand-off runs as part of this coroutine's job, so stopping the pass stops it too.
        val job = currentCoroutineContext().minusKey(ContinuationInterceptor)
        val fast = try {
            KeyframeDecoder(context, item.uri).forEach(wanted, px) { at, frame ->
                lastAt = at
                runBlocking(job) { deliver(at, frame) }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClosedSendChannelException) {
            throw CancellationException("Frames no longer wanted").apply { initCause(e) }
        } catch (e: Exception) {
            Log.w("LumenFrames", "Fast decoding failed for ${item.name}; frame by frame instead", e)
            false
        }
        if (fast || stop) return
        // Frame by frame, from where the fast way stopped.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            // Frame by frame is slow to decode exact moments (~0.8 s each): nearest keyframes here.
            for (at in keyframes(context, item, wanted.filter { it > lastAt })) {
                val frame = retriever.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px, params)
                    ?: continue
                if (!deliver(at, frame)) return
            }
        } finally {
            retriever.release()
        }
    }

    /** Frame times in ms: every second for short videos, else evenly spread mid-segment points. */
    fun times(durationMs: Long, perSecond: Int = 1): List<Long> = when {
        durationMs <= 0 -> listOf(0L)
        // Captions in edits come in word by word: twice a second catches whole lines.
        perSecond > 1 && durationMs <= DenseUpTo -> (0 until durationMs step 1000L / perSecond).toList()
        durationMs <= EverySecondUpTo -> (0 until durationMs step 1000).toList()
        else -> List(MaxFrames) { i -> (durationMs * (2 * i + 1)) / (2 * MaxFrames) }
    }

    /**
     * The keyframes nearest [times], each once: for the slow frame-by-frame fallback, where
     * decoding forward to the exact frame took ~0.8 s a frame. The extractor reads them from the
     * file's index without decoding anything.
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
