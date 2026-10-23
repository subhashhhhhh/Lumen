package com.subhashh.lumen.data.ai

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * One text-reading pass: brings [TextStore] in line with the library, photos only, newest first.
 * Runs after [Indexer] in the same background job, under the same rules (charging for the
 * backlog, a few new photos at a time otherwise, stops when the phone gets warm).
 */
class TextIndexer(private val context: Context) {

    /** Reads at most [limit] photos. */
    suspend fun run(limit: Int = Int.MAX_VALUE): Indexer.Outcome {
        if (!OcrModel.installed(context)) return Indexer.Outcome.Done
        val store = TextStore.of(context)
        val library = Indexer.eligible(context).filterNot { it.isVideo }
        val known = store.indexed()
        store.remove(known.keys - library.mapTo(HashSet()) { it.id })
        val todo = library.filter { known[it.id] != it.modified }.sortedByDescending { it.takenAt }
        var finished = library.size - todo.size
        SmartIndex.reportText(context, finished, library.size)
        if (todo.isEmpty()) return Indexer.Outcome.Done

        val power = context.getSystemService(PowerManager::class.java)
        val gpu = GpuCheck.useGpu(
            context, GpuCheck.Target("ppocrv6-det", java.io.File(OcrModel.dir(context), OcrModel.DetGpu), 960 * 960 * 3),
        )
        Log.i(Tag, "Reading text with detection on the ${if (gpu) "GPU" else "CPU"}")
        TextReader(OcrModel.dir(context), Threads, gpu).use { reader ->
            for ((n, item) in todo.withIndex()) {
                if (n >= limit) return Indexer.Outcome.Partial
                coroutineContext.ensureActive()
                if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return Indexer.Outcome.TooHot
                val started = SystemClock.elapsedRealtime()
                val lines = runCatching {
                    val bitmap = MediaFrames.forReading(context, item, ReadSide)
                    try { reader.read(bitmap) } finally { bitmap.recycle() }
                }.onFailure { Log.w(Tag, "Couldn't read ${item.name}", it) }.getOrDefault(emptyList())
                store.put(item.id, item.modified, lines.joinToString("\n"))
                SmartIndex.reportText(context, ++finished, library.size)
                Log.d(Tag, "${item.name.take(40)}: ${lines.size} lines, ${SystemClock.elapsedRealtime() - started} ms")
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
