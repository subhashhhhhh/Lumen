package com.subhashh.lumen.data.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private var cacheLoadedAt = 0L

    /**
     * Loads the text model and the vectors ahead of time (Search opening), so the first query
     * doesn't wait for them. Safe to call often.
     */
    suspend fun prepare(context: Context) {
        withContext(Dispatchers.Default) {
            val installed = ModelDownloads.installed(context) ?: return@withContext
            lock.withLock { ready(context, installed).also { embedder?.warmText() } }
            // First time after an update or a fresh index: work out suggestions in the background.
            if (SearchSuggestions.load(context, installed).isEmpty()) {
                background.launch { SearchSuggestions.refreshIfStale(context, installed) }
            }
        }
    }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Best matches first; empty when nothing clears the bar; null when Smart Search isn't set up. */
    suspend fun search(context: Context, query: String): List<SmartHit>? = withContext(Dispatchers.Default) {
        val installed = ModelDownloads.installed(context) ?: return@withContext null
        lock.withLock {
            val start = SystemClock.elapsedRealtime()
            val all = ready(context, installed)
            val loaded = SystemClock.elapsedRealtime()
            if (all.size == 0) return@withContext emptyList()
            val q = embedder!!.embedText(query)
            val embedded = SystemClock.elapsedRealtime()
            val hits = rank(all, q)
            Log.d(Tag, "“$query”: ${hits.size} hits; ready ${loaded - start} ms, text ${embedded - loaded} ms, " +
                "scan ${SystemClock.elapsedRealtime() - embedded} ms over ${all.size} vectors")
            hits
        }
    }

    /** Every item ranked, no cutoff (Search Lab's score curves). */
    suspend fun rankedAll(context: Context, query: String): List<SmartHit>? = withContext(Dispatchers.Default) {
        val installed = ModelDownloads.installed(context) ?: return@withContext null
        lock.withLock {
            val all = ready(context, installed)
            if (all.size == 0) return@withContext emptyList()
            scored(all, embedder!!.embedText(query))
        }
    }

    /** The model and vectors for [installed], loading what's missing or stale. Call under [lock]. */
    private fun ready(context: Context, installed: AiModel): Embeddings {
        if (model != installed) {
            embedder?.close()
            embedder = OnnxEmbedder(installed.dir(context), installed.spec, threads = 4)
            model = installed
            cache = null
        }
        val store = EmbeddingStore.of(context, installed)
        val now = SystemClock.elapsedRealtime()
        // While indexing runs the index changes every second or so; reloading everything for each
        // search would cost more than the search. New items show up within a minute instead.
        val stale = cacheVersion != store.version && now - cacheLoadedAt > ReloadAfterMs
        if (cache == null || stale) {
            val start = SystemClock.elapsedRealtime()
            cacheVersion = store.version
            cache = store.loadAll()
            cacheLoadedAt = SystemClock.elapsedRealtime()
            Log.d(Tag, "Loaded ${cache!!.size} vectors in ${cacheLoadedAt - start} ms")
        }
        return cache!!
    }

    private fun rank(all: Embeddings, q: FloatArray): List<SmartHit> {
        val ranked = scored(all, q)
        // Scores mean little in absolute terms and differ per model, so keep what's close to
        // the best match: a query with one great hit shows few results, a broad one many.
        val top = ranked.first().score
        val bar = top - Spread * (top - ranked[ranked.size / 2].score)
        return ranked.takeWhile { it.score >= bar }.take(MaxResults)
    }

    /** Each item's best score (and the frame it came from), best first. */
    private fun scored(all: Embeddings, q: FloatArray): List<SmartHit> {
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
        return best.values.sortedByDescending { it.score }
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
    private const val ReloadAfterMs = 60_000L
    private const val Tag = "LumenSearch"
}
