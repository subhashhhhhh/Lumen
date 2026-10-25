package com.subhashh.lumen.data.ai

import android.content.Context
import android.util.LruCache
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Live Text: the text in the photo on screen, with where every character is, so it can be
 * selected and copied. Text Reading keeps each photo's lines as it indexes ([TextStore.lines]),
 * so an indexed photo has them straight away; others are read on demand at up to [ReadSide],
 * and kept the same way. The last few are also held in memory. The reader loads as the viewer
 * opens ([warm]) and stays a little after it closes ([release]).
 */
object LiveText {
    /** [lines] with corners in pixels of a [width]×[height] image: the photo as displayed. */
    class Result(val lines: List<TextReader.Line>, val width: Int, val height: Int)

    private const val ReadSide = 3072
    /** How long the reader stays loaded after the viewer closes, for the next photo opened. */
    private const val KeepMs = 30_000L
    private val cache = LruCache<String, Result>(24)
    private val lock = Mutex()
    private var reader: TextReader? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var releasing: Job? = null

    private fun key(item: MediaItem) = "${item.id}@${item.modified}"

    fun cached(item: MediaItem): Result? = cache.get(key(item))

    /**
     * What's known without reading: the lines kept on disk (null when there are none), and
     * whether the index found no words at all, so there's nothing to read.
     */
    suspend fun stored(context: Context, item: MediaItem): Pair<Result?, TextStore.Known> = withContext(Dispatchers.IO) {
        if (item.isVideo) return@withContext null to TextStore.Known.NoText
        val store = TextStore.of(context)
        val result = store.lines(item.id, item.modified)?.let { runCatching { Result(decode(it.data), it.width, it.height) }.getOrNull() }
        result?.also { cache.put(key(item), it) } to store.known(item.id, item.modified)
    }

    /** Loads the reader ahead of the first photo (the viewer opened). */
    fun warm(context: Context) {
        releasing?.cancel()
        if (!OcrModel.installed(context)) return
        scope.launch { lock.withLock { loadedReader(context) } }
    }

    /** The photo's text, or null when there's no text reader or it can't be read. */
    suspend fun read(context: Context, item: MediaItem): Result? = withContext(Dispatchers.Default) {
        if (item.isVideo || !OcrModel.installed(context)) return@withContext null
        cached(item)?.let { return@withContext it }
        lock.withLock {
            cached(item)?.let { return@withLock it }
            runCatching {
                val bitmap = MediaFrames.forReading(context, item, ReadSide)
                try {
                    Result(loadedReader(context).readLines(bitmap), bitmap.width, bitmap.height)
                } finally {
                    bitmap.recycle()
                }
            }.getOrNull()?.also { result ->
                cache.put(key(item), result)
                // Kept like the indexer's, so it's there next time even after Lumen restarts.
                withContext(Dispatchers.IO) {
                    runCatching { TextStore.of(context).putLines(item.id, item.modified, result.width, result.height, encode(result.lines)) }
                }
            }
        }
    }

    /**
     * The text in [item] (a video) at [atMs]: the frame on screen while paused. Not kept on
     * disk (every moment is a different frame); the last few stay in memory.
     */
    suspend fun readVideoFrame(context: Context, item: MediaItem, atMs: Long): Result? = withContext(Dispatchers.Default) {
        if (!item.isVideo || !OcrModel.installed(context)) return@withContext null
        val key = "${key(item)}#$atMs"
        cache.get(key)?.let { return@withContext it }
        lock.withLock {
            cache.get(key)?.let { return@withLock it }
            runCatching {
                val frame = MediaFrames.videoFrameAt(context, item, atMs, VideoSide) ?: return@runCatching null
                try {
                    Result(loadedReader(context).readLines(frame), frame.width, frame.height)
                } finally {
                    frame.recycle()
                }
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** Paused frames are read at most this big: 1080p and 4K video both read well at it. */
    private const val VideoSide = 2048

    private suspend fun loadedReader(context: Context): TextReader = reader ?: run {
        val gpu = GpuCheck.useGpu(
            context, GpuCheck.Target("ppocrv6-det", File(OcrModel.dir(context), OcrModel.DetGpu), 960 * 960 * 3),
        )
        TextReader(OcrModel.dir(context), OcrModel.recFile(context), Threads, gpu).also { reader = it }
    }

    /** The viewer closed: frees the text models after [KeepMs] unless it opens again first. */
    fun release() {
        releasing?.cancel()
        releasing = scope.launch {
            delay(KeepMs)
            lock.withLock {
                reader?.close()
                reader = null
            }
        }
    }

    /** Lines to bytes for [TextStore]: text, corners, and each character's span. */
    fun encode(lines: List<TextReader.Line>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(lines.size)
            for (line in lines) {
                out.writeUTF(line.text)
                line.corners.forEach(out::writeFloat)
                out.writeInt(line.starts.size)
                line.starts.forEach(out::writeFloat)
                line.ends.forEach(out::writeFloat)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(data: ByteArray): List<TextReader.Line> = DataInputStream(ByteArrayInputStream(data)).use { input ->
        List(input.readInt()) {
            val text = input.readUTF()
            val corners = FloatArray(8) { input.readFloat() }
            val n = input.readInt()
            val starts = FloatArray(n) { input.readFloat() }
            val ends = FloatArray(n) { input.readFloat() }
            TextReader.Line(text, corners, starts, ends)
        }
    }

    /** The photo on screen gets the phone's attention: more threads than background reading. */
    private val Threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
}
