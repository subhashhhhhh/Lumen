package com.subhashh.lumen.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One Smart Search match: a library item, how well it matched, and for videos the best moment. */
data class SmartHit(val mediaId: Long, val score: Float, val atMs: Long)

/**
 * Searching by meaning: the query goes through the model's text half (tens of ms) and is
 * compared with every indexed photo and video frame (a flat scan; a few ms per thousand). A
 * video scores as its best-matching frame. The vectors stay in memory between searches and are
 * reloaded only when the index has changed.
 */
object SmartSearch {
    private val lock = Mutex()
    private var model: AiModel? = null
    private var embedder: OnnxEmbedder? = null
    private var cache: Embeddings? = null
    private var cacheVersion = -1

    /** Best matches first; empty when nothing clears the bar; null when Smart Search isn't set up. */
    suspend fun search(context: Context, query: String): List<SmartHit>? = withContext(Dispatchers.Default) {
        val installed = ModelDownloads.installed(context) ?: return@withContext null
        lock.withLock {
            if (model != installed) {
                embedder?.close()
                embedder = OnnxEmbedder(installed.dir(context), installed.spec, threads = 4)
                model = installed
                cache = null
            }
            val store = EmbeddingStore.of(context, installed)
            if (cache == null || cacheVersion != store.version) {
                cacheVersion = store.version
                cache = store.loadAll()
            }
            val all = cache!!
            if (all.size == 0) return@withContext emptyList()
            val q = embedder!!.embedText(query)

            // Best score per item, and the frame it came from.
            val best = HashMap<Long, SmartHit>()
            val dim = all.dim
            for (i in 0 until all.size) {
                var dot = 0f
                val base = i * dim
                for (d in 0 until dim) dot += q[d] * all.vectors[base + d]
                val id = all.mediaIds[i]
                val previous = best[id]
                if (previous == null || dot > previous.score) best[id] = SmartHit(id, dot, all.atMs[i])
            }
            val ranked = best.values.sortedByDescending { it.score }
            // Scores mean little in absolute terms and differ per model, so keep what's close to
            // the best match: a query with one great hit shows few results, a broad one many.
            val top = ranked.first().score
            val bar = top - Spread * (top - ranked[ranked.size / 2].score)
            ranked.takeWhile { it.score >= bar }.take(MaxResults)
        }
    }

    /** Frees the models and vectors (Smart Search removed, or memory is tight). */
    suspend fun release() = lock.withLock {
        embedder?.close()
        embedder = null
        model = null
        cache = null
    }

    /** How far below the best match (as a share of best minus median) results may go. */
    private const val Spread = 0.5f
    private const val MaxResults = 500
}
