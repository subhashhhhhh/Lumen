package com.subhashh.lumen.data.ai

import android.content.Context
import android.util.LruCache
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Live Text: the text in the photo on screen, with where every character is, so it can be
 * selected and copied. Read on demand at up to [ReadSide] (the search index only keeps the
 * words, at a smaller size), with the same models as Text Reading; results are kept for the
 * last few photos. The reader stays loaded while the viewer is open ([release] when it closes).
 */
object LiveText {
    /** [lines] with corners in pixels of a [width]×[height] image: the photo as displayed. */
    class Result(val lines: List<TextReader.Line>, val width: Int, val height: Int)

    private const val ReadSide = 3072
    private val cache = LruCache<String, Result>(24)
    private val lock = Mutex()
    private var reader: TextReader? = null

    private fun key(item: MediaItem) = "${item.id}@${item.modified}"

    fun cached(item: MediaItem): Result? = cache.get(key(item))

    /** The photo's text, or null when there's no text reader or it can't be read. */
    suspend fun read(context: Context, item: MediaItem): Result? = withContext(Dispatchers.Default) {
        if (item.isVideo || !OcrModel.installed(context)) return@withContext null
        cached(item)?.let { return@withContext it }
        lock.withLock {
            cached(item)?.let { return@withLock it }
            runCatching {
                val r = reader ?: run {
                    val gpu = GpuCheck.useGpu(
                        context, GpuCheck.Target("ppocrv6-det", File(OcrModel.dir(context), OcrModel.DetGpu), 960 * 960 * 3),
                    )
                    TextReader(OcrModel.dir(context), OcrModel.recFile(context), Threads, gpu).also { reader = it }
                }
                val bitmap = MediaFrames.forReading(context, item, ReadSide)
                try {
                    Result(r.readLines(bitmap), bitmap.width, bitmap.height)
                } finally {
                    bitmap.recycle()
                }
            }.getOrNull()?.also { cache.put(key(item), it) }
        }
    }

    /** Frees the text models (the viewer closed); results stay cached. */
    suspend fun release() = withContext(Dispatchers.Default) {
        lock.withLock {
            reader?.close()
            reader = null
        }
    }

    /** The photo on screen gets the phone's attention: more threads than background reading. */
    private val Threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
}
