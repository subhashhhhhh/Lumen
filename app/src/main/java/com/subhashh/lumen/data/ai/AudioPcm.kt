package com.subhashh.lumen.data.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * A stretch of a video's sound as 16 kHz mono floats in [-1, 1], what speech models take:
 * decoded by the phone's audio decoder, channels averaged, resampled linearly.
 */
object AudioPcm {
    const val Rate = 16_000

    /** [fromMs] to [fromMs] + [lengthMs]; empty if the video has no sound. */
    fun read(context: Context, uri: Uri, fromMs: Long, lengthMs: Long): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return FloatArray(0)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(format, null, null, 0)
                start()
            }
            val endUs = (fromMs + lengthMs) * 1000
            val info = MediaCodec.BufferInfo()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            // Mono samples at the source rate, and the time of the first one.
            var mono = FloatArray(rate * ((lengthMs / 1000).toInt() + 2))
            var count = 0
            var firstUs = -1L
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else if (out >= 0) {
                    val buffer = codec.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    if (info.size > 0) {
                        if (firstUs < 0) firstUs = info.presentationTimeUs
                        val frames = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) info.size / 4 / channels else info.size / 2 / channels
                        if (count + frames > mono.size) mono = mono.copyOf(maxOf(mono.size * 2, count + frames))
                        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val fb = buffer.asFloatBuffer()
                            for (i in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += fb.get()
                                mono[count++] = sum / channels
                            }
                        } else {
                            val sb = buffer.asShortBuffer()
                            for (i in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += sb.get() / 32768f
                                mono[count++] = sum / channels
                            }
                        }
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            if (count == 0 || firstUs < 0) return FloatArray(0)
            // Trim to the asked-for stretch (decoding starts at the keyframe before it).
            val skip = (((fromMs * 1000 - firstUs).coerceAtLeast(0)) * rate / 1_000_000).toInt().coerceAtMost(count)
            val wanted = (lengthMs * rate / 1000).toInt().coerceAtMost(count - skip)
            return resample(mono, skip, wanted, rate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /**
     * To [Rate] by averaging the source samples each output sample covers: a box low-pass, so
     * sound above 8 kHz doesn't fold down into the speech band.
     */
    private fun resample(src: FloatArray, from: Int, length: Int, rate: Int): FloatArray {
        if (rate == Rate) return src.copyOfRange(from, from + length)
        val n = (length.toLong() * Rate / rate).toInt()
        val step = rate.toDouble() / Rate
        return FloatArray(n) { i ->
            val a = from + floor(i * step).toInt()
            val b = minOf(from + floor((i + 1) * step).toInt(), from + length).coerceAtLeast(a + 1)
            var sum = 0f
            for (j in a until b) sum += src[j]
            sum / (b - a)
        }
    }
}
