package com.subhashh.lumen.data.ai

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * One text-reading pass: brings [TextStore] in line with the library, newest first: photos, then
 * videos. A video is read frame by frame like Smart Search reads it (Video Detail size), but
 * only lines that changed are read again ([TextReader.readChanged]): captions stay up for many
 * frames. Each video keeps its distinct lines once. The last pass of the background job, under
 * the same rules (charging for the backlog, a few new items otherwise, stops when warm).
 */
class TextIndexer(private val context: Context) {

    /** Reads at most [limit] photos and videos. */
    suspend fun run(limit: Int = Int.MAX_VALUE): Indexer.Outcome {
        if (!OcrModel.installed(context)) return Indexer.Outcome.Done
        OcrModel.removeUnused(context)
        val store = TextStore.of(context)
        val library = Indexer.eligible(context)
        val known = store.indexed()
        store.remove(known.keys - library.mapTo(HashSet()) { it.id })
        val todo = library.filter { known[it.id] != it.modified }
            .sortedWith(compareBy<MediaItem> { it.isVideo }.thenByDescending { it.takenAt })
        val px = LumenPrefs(context).videoDetail.px
        var finished = library.size - todo.size
        SmartIndex.reportText(context, finished, library.size)
        if (todo.isEmpty()) return Indexer.Outcome.Done

        val power = context.getSystemService(PowerManager::class.java)
        val gpu = GpuCheck.useGpu(
            context, GpuCheck.Target("ppocrv6-det", File(OcrModel.dir(context), OcrModel.DetGpu), 960 * 960 * 3),
        )
        Log.i(Tag, "Reading text with detection on the ${if (gpu) "GPU" else "CPU"}")
        TextReader(OcrModel.dir(context), OcrModel.recFile(context), Threads, gpu).use { reader ->
            for ((n, item) in todo.withIndex()) {
                if (n >= limit) return Indexer.Outcome.Partial
                coroutineContext.ensureActive()
                if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return Indexer.Outcome.TooHot
                val started = SystemClock.elapsedRealtime()
                if (item.isVideo) {
                    val lines = LinkedHashSet<String>()
                    var frames = 0
                    var read = 0
                    var previous: TextReader.FrameText? = null
                    runCatching {
                        MediaFrames.forEach(context, item, px) { _, frame ->
                            frames++
                            val text = reader.readChanged(frame, previous)
                            frame.recycle()
                            read += text.read
                            text.lines.filterNotNullTo(lines)
                            previous = text
                            true
                        }
                    }.onFailure {
                        if (it is CancellationException) throw it
                        Log.w(Tag, "Couldn't read ${item.name}", it)
                    }
                    store.put(item.id, item.modified, lines.joinToString("\n"))
                    Log.d(Tag, "${item.name.take(40)}: ${item.durationMs / 1000}s video, $frames frames, $read lines read, " +
                        "${lines.size} kept, ${SystemClock.elapsedRealtime() - started} ms")
                } else {
                    val lines = runCatching {
                        val bitmap = MediaFrames.forReading(context, item, ReadSide)
                        try { reader.read(bitmap) } finally { bitmap.recycle() }
                    }.onFailure { Log.w(Tag, "Couldn't read ${item.name}", it) }.getOrDefault(emptyList())
                    store.put(item.id, item.modified, lines.joinToString("\n"))
                    Log.d(Tag, "${item.name.take(40)}: ${lines.size} lines, ${SystemClock.elapsedRealtime() - started} ms")
                }
                SmartIndex.reportText(context, ++finished, library.size)
            }
        }
        return Indexer.Outcome.Done
    }

    private companion object {
        const val Tag = "LumenText"
        /** Same share of the phone as [Indexer]. */
        val Threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        /** Long side photos are read at: chat-sized letters keep ~30 px of height. */
        const val ReadSide = 2048
    }
}
