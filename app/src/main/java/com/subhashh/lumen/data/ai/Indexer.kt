package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * One indexing pass: brings a model's [EmbeddingStore] in line with the library. Items that were
 * deleted (or moved into an excluded folder) are dropped. Then photos, newest first, so search
 * covers them within minutes; then videos, whose frames are decoded on another thread into a
 * small queue while the model works (decoding is mostly the phone's video hardware, the model
 * the CPU, so the two overlap almost for free). Cancellation keeps everything finished so far;
 * the next pass carries on.
 */
class Indexer(private val context: Context, private val model: AiModel) {

    enum class Outcome { Done, Partial, TooHot }

    private sealed interface Queued {
        class Frame(val atMs: Long, val bitmap: Bitmap) : Queued
        class End(val item: MediaItem) : Queued
    }

    /** Embeds at most [limit] items. */
    suspend fun run(limit: Int = Int.MAX_VALUE): Outcome = coroutineScope {
        val store = EmbeddingStore.of(context, model)
        val library = eligible(context)
        val known = store.indexed()
        store.remove(known.keys - library.mapTo(HashSet()) { it.id })
        val todo = library.filter { known[it.id] != it.modified }.sortedByDescending { it.takenAt }
        // Frames the faces pass kept are only for videos still waiting here.
        val waiting = todo.filter { it.isVideo }.associate { it.id to it.modified }
        FrameCache.prune(context) { id, modified -> waiting[id] == modified }
        val (videos, photos) = todo.partition { it.isVideo }
        var finished = library.size - todo.size
        SmartIndex.report(context, finished, library.size)
        if (todo.isEmpty()) return@coroutineScope Outcome.Done

        val power = context.getSystemService(PowerManager::class.java)
        // Leave the phone alone when it's getting warm; the next pass carries on.
        fun tooHot() = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        var budget = limit

        val gpu = GpuCheck.useGpu(context, model)
        Log.i(Tag, "Indexing with ${model.id} on the ${if (gpu) "GPU" else "CPU"}")
        OnnxEmbedder(model.dir(context), model.spec, threads = Threads, gpu = gpu).use { embedder ->
            for (item in photos) {
                if (budget-- <= 0) return@coroutineScope Outcome.Partial
                ensureActive()
                if (tooHot()) return@coroutineScope Outcome.TooHot
                val vector = runCatching { embedder.embedImage(MediaFrames.thumbnail(context, item)) }
                    .onFailure { Log.w(Tag, "Couldn't index ${item.name}", it) }.getOrNull()
                store.put(item.id, item.modified, listOfNotNull(vector?.let { 0L to it }))
                SmartIndex.report(context, ++finished, library.size)
            }

            val batch = videos.take(budget.coerceAtLeast(0))
            if (batch.isEmpty()) return@coroutineScope if (videos.isEmpty()) Outcome.Done else Outcome.Partial
            val queue = Channel<Queued>(capacity = QueueFrames)
            val reader = launch(Dispatchers.IO) {
                try {
                    for (item in batch) {
                        try {
                            // Frames the faces pass already decoded, else decode them here.
                            val kept = FrameCache.frames(context, item.id, item.modified)
                            if (kept != null) {
                                for ((at, file) in kept) FrameCache.read(file)?.let { queue.send(Queued.Frame(at, it)) }
                            } else {
                                MediaFrames.forEach(context, item) { at, frame ->
                                    queue.send(Queued.Frame(at, frame))
                                    true
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(Tag, "Couldn't read ${item.name}", e)
                        }
                        queue.send(Queued.End(item))
                    }
                } finally {
                    queue.close()
                }
            }
            try {
                var frames = ArrayList<Pair<Long, FloatArray>>()
                var started = SystemClock.elapsedRealtime()
                for (message in queue) {
                    when (message) {
                        is Queued.Frame -> {
                            if (tooHot()) return@coroutineScope Outcome.TooHot
                            val vector = runCatching { embedder.embedImage(message.bitmap) }.getOrNull()
                            message.bitmap.recycle()
                            // Pixels can differ while the content doesn't (a pan across one scene).
                            if (vector != null && (frames.isEmpty() || dot(frames.last().second, vector) < SameScene)) {
                                frames += message.atMs to vector
                            }
                        }
                        is Queued.End -> {
                            store.put(message.item.id, message.item.modified, frames)
                            FrameCache.drop(context, message.item.id, message.item.modified)
                            SmartIndex.report(context, ++finished, library.size)
                            Log.d(Tag, "${message.item.name.take(40)}: ${message.item.durationMs / 1000}s video, " +
                                "${frames.size} frames kept, ${SystemClock.elapsedRealtime() - started} ms")
                            frames = ArrayList()
                            started = SystemClock.elapsedRealtime()
                        }
                    }
                }
            } finally {
                reader.cancel()
            }
            if (batch.size < videos.size) Outcome.Partial else Outcome.Done
        }
    }

    companion object {
        private const val Tag = "LumenIndex"
        /** Half the phone's cores: indexing never competes with whatever the user is doing. */
        private val Threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        private const val SameScene = 0.95f
        /** How far the video reader may run ahead of the model: ~16 small frames, a few MB. */
        private const val QueueFrames = 16

        /** What Smart Search covers: the library as the user sees it (no excluded folders, no Hidden). */
        suspend fun eligible(context: Context): List<MediaItem> {
            val excluded = LumenPrefs(context).excludedFolders
            return MediaRepository(context).loadLibrary().filterNot { ExcludedFolders.covers(excluded, it) }
        }

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            for (i in a.indices) sum += a[i] * b[i]
            return sum
        }
    }
}
