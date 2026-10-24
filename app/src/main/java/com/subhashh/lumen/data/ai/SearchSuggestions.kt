package com.subhashh.lumen.data.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/** A suggested search: something this library has plenty of, and its best-matching photo. */
data class Suggestion(val term: String, val coverId: Long)

/**
 * What a library (and each item in it) is about, from a vocabulary of everyday words: the
 * search model's shipped one ([VocabularyFile], ~4,200 words) or, before that's downloaded, the
 * small built-in [Vocabulary]. Every word is scored against every item once, in the background
 * after indexing, and summed up in small files; refreshed as the library grows.
 *
 * - Suggestions: words whose ten best items stand far above the library's typical score for
 *   them (in standard deviations). Things a library really has stand out; things it doesn't
 *   (no dogs in it, say) don't.
 * - Looks like ([looksLike]): an item's own best words.
 */
object SearchSuggestions {
    private const val Tag = "LumenSuggest"
    private const val Keep = 24
    /** Bumped when the stats file's layout changes, so it's worked out again. */
    private const val StatsVersion = "v2"

    private fun file(context: Context, model: AiModel) = File(context.noBackupFilesDir, "search/suggestions-${model.id}.json")

    /** The best ones for this library, strongest first; empty until computed. */
    fun load(context: Context, model: AiModel): List<Suggestion> = runCatching {
        val json = JSONObject(file(context, model).readText())
        val list = json.getJSONArray("suggestions")
        List(list.length()) { i -> list.getJSONObject(i).let { Suggestion(it.getString("term"), it.getLong("cover")) } }
    }.getOrDefault(emptyList())

    private fun shipped(context: Context, model: AiModel) = File(model.dir(context), "vocabulary.bin")

    /** Which vocabulary the stats were made from: the shipped file (by size) or the built-in list. */
    private fun source(context: Context, model: AiModel): String =
        StatsVersion + ";" + (shipped(context, model).takeIf { it.isFile }?.let { "shipped:${it.length()}" } ?: "builtin:${Vocabulary.size}")

    /** Recomputes when missing, when the vocabulary changed, or when the index grew or shrank noticeably. */
    fun refreshIfStale(context: Context, model: AiModel) {
        val count = EmbeddingStore.of(context, model).count()
        if (count == 0) return
        val previous = runCatching { JSONObject(file(context, model).readText()).getInt("indexed") }.getOrNull()
            ?.takeIf { stats(context, model)?.source == source(context, model) }
        if (previous != null && kotlin.math.abs(count - previous) < maxOf(50, previous / 50)) return
        refresh(context, model, count)
    }

    /** The vocabulary's words and text vectors: shipped with the model, else embedded here. */
    private fun words(context: Context, model: AiModel): Pair<List<String>, List<FloatArray>> =
        VocabularyFile.read(shipped(context, model)) ?: OnnxEmbedder(model.dir(context), model.spec, threads = 2).use { e ->
            Vocabulary to Vocabulary.map { e.embedText(it) }
        }

    private fun refresh(context: Context, model: AiModel, indexed: Int) {
        val start = SystemClock.elapsedRealtime()
        val source = source(context, model)
        val all = EmbeddingStore.of(context, model).loadAll()
        if (all.size == 0) return
        // Which item each row belongs to: one row per photo, one per kept frame of a video.
        val index = HashMap<Long, Int>()
        val itemIds = ArrayList<Long>()
        val itemOf = IntArray(all.size) { i -> index.getOrPut(all.mediaIds[i]) { itemIds.add(all.mediaIds[i]); itemIds.size - 1 } }
        val (words, vectors) = words(context, model)
        val n = words.size
        val dim = all.dim
        val median = FloatArray(n)
        val strength = DoubleArray(n)
        val cover = LongArray(n)
        // Each item's best word score above that word's median: sets how sure "Looks like" must be.
        val itemTop = FloatArray(itemIds.size) { Float.NEGATIVE_INFINITY }
        // Thousands of words × every vector: spread over the cores.
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val threads = List(workers) { w ->
            Thread {
                val scores = FloatArray(itemIds.size)
                val localTop = FloatArray(itemIds.size) { Float.NEGATIVE_INFINITY }
                for (t in w until n step workers) {
                    val q = vectors[t]
                    scores.fill(Float.NEGATIVE_INFINITY)
                    for (i in 0 until all.size) {
                        var dot = 0f
                        val base = i * dim
                        for (d in 0 until dim) dot += q[d] * all.vectors[base + d]
                        if (dot > scores[itemOf[i]]) scores[itemOf[i]] = dot
                    }
                    val sorted = scores.sortedArray()
                    median[t] = sorted[sorted.size / 2]
                    val mean = scores.average()
                    val sd = sqrt(scores.sumOf { (it - mean) * (it - mean) } / scores.size)
                    val top = (sorted.size - 1 downTo maxOf(0, sorted.size - 10)).map { sorted[it].toDouble() }.average()
                    strength[t] = if (sd > 0) (top - mean) / sd else 0.0
                    cover[t] = itemIds[scores.indices.maxBy { scores[it] }]
                    for (i in scores.indices) localTop[i] = maxOf(localTop[i], scores[i] - median[t])
                }
                synchronized(itemTop) { for (i in itemTop.indices) itemTop[i] = maxOf(itemTop[i], localTop[i]) }
            }.apply { start() }
        }
        threads.forEach { it.join() }
        // When two words name the same thing: this model's text vectors are all fairly alike (an
        // unrelated pair is ~0.84 for SigLIP 2, ~0.70 for CLIP), so 80% of the way from a typical
        // pair to identical: "chicken" and "poultry" are one, "tennis" and "badminton" aren't.
        val sample = java.util.Random(7).let { r -> FloatArray(2000) { cosine(vectors[r.nextInt(n)], vectors[r.nextInt(n)]) } }.sortedArray()
        val typical = sample[sample.size / 2]
        val same = typical + 0.8f * (1f - typical)
        // Strongest first; one suggestion per cover photo (cat and kitten would share one), and a
        // word only once ("group" after "group photo", "poultry" after "chicken").
        val covers = HashSet<Long>()
        val best = ArrayList<Int>()
        for (t in (0 until n).sortedByDescending { strength[it] }) {
            if (best.size >= Keep) break
            if (cover[t] in covers || best.any { similar(words[it], vectors[it], words[t], vectors[t], same) }) continue
            covers += cover[t]
            best += t
        }
        val json = JSONObject()
            .put("indexed", indexed)
            .put("suggestions", JSONArray().apply {
                best.forEach { put(JSONObject().put("term", words[it]).put("cover", cover[it]).put("strength", strength[it])) }
            })
        file(context, model).apply { parentFile?.mkdirs() }.writeText(json.toString())
        // "Looks like" names a word for about 70% of items: the bar is the 30th percentile of
        // items' best scores; words close behind the best (a third of the middle spread) join it.
        val tops = itemTop.sortedArray()
        val floor = tops[(tops.size * 0.3).toInt().coerceIn(0, tops.lastIndex)]
        val margin = 0.3f * (tops[(tops.size * 0.75).toInt().coerceIn(0, tops.lastIndex)] - tops[(tops.size * 0.25).toInt()])
        saveStats(context, model, Stats(source, floor, margin, same, words, vectors, median))
        Log.i(Tag, "Suggestions for ${model.id} from $n words in ${SystemClock.elapsedRealtime() - start} ms: " +
            best.take(12).joinToString { "${words[it]} %.1f".format(strength[it]) })
    }

    /** Each word, its vector, and its median score over the library; the bar and margin for [looksLike]. */
    private class Stats(
        val source: String,
        val floor: Float,
        val margin: Float,
        /** Text vectors at least this alike name the same thing. */
        val same: Float,
        val words: List<String>,
        val vectors: List<FloatArray>,
        val median: FloatArray,
    )

    private fun statsFile(context: Context, model: AiModel) = File(context.noBackupFilesDir, "search/looks-like-${model.id}.bin")

    private fun saveStats(context: Context, model: AiModel, s: Stats) {
        java.io.DataOutputStream(statsFile(context, model).outputStream().buffered()).use { out ->
            out.writeUTF(s.source)
            out.writeFloat(s.floor)
            out.writeFloat(s.margin)
            out.writeFloat(s.same)
            out.writeInt(s.words.size)
            out.writeInt(s.vectors.firstOrNull()?.size ?: 0)
            for (i in s.words.indices) {
                out.writeUTF(s.words[i])
                out.writeFloat(s.median[i])
                for (x in s.vectors[i]) out.writeShort(android.util.Half.toHalf(x).toInt())
            }
        }
        cachedStats = null
    }

    @Volatile private var cachedStats: Pair<String, Stats?>? = null

    private fun stats(context: Context, model: AiModel): Stats? {
        cachedStats?.takeIf { it.first == model.id }?.let { return it.second }
        val stats = runCatching {
            java.io.DataInputStream(statsFile(context, model).inputStream().buffered()).use { input ->
                val source = input.readUTF()
                val floor = input.readFloat()
                val margin = input.readFloat()
                if (!source.startsWith("$StatsVersion;")) return@use null
                val same = input.readFloat()
                val n = input.readInt()
                val dim = input.readInt()
                val words = ArrayList<String>(n)
                val vectors = ArrayList<FloatArray>(n)
                val median = FloatArray(n)
                for (i in 0 until n) {
                    words += input.readUTF()
                    median[i] = input.readFloat()
                    vectors += FloatArray(dim) { android.util.Half.toFloat(input.readShort()) }
                }
                Stats(source, floor, margin, same, words, vectors, median)
            }
        }.getOrNull()
        cachedStats = model.id to stats
        return stats
    }

    /**
     * What an item looks like: each word's score for it minus the word's median over the
     * library (words that match everything a little don't win), then its best words: the top
     * one above the library's bar and any close behind, a word only once ("tennis" and "tennis
     * match" share one, as do near-synonyms), at most [max]. Ranking within the item keeps what
     * a library is full of nameable: tennis videos in a tennis-heavy library still get "tennis".
     */
    fun looksLike(context: Context, model: AiModel, mediaId: Long, max: Int = 3): List<String> {
        val s = stats(context, model) ?: return emptyList()
        val vectors = EmbeddingStore.of(context, model).vectorsOf(mediaId)
        if (vectors.isEmpty()) return emptyList()
        val score = FloatArray(s.words.size) { t ->
            val q = s.vectors[t]
            vectors.maxOf { v -> var dot = 0f; for (d in v.indices) dot += v[d] * q[d]; dot } - s.median[t]
        }
        val order = score.indices.sortedByDescending { score[it] }
        val top = score[order.first()]
        if (top < s.floor) return emptyList()
        val picked = ArrayList<Int>()
        for (t in order) {
            if (picked.size >= max || score[t] < maxOf(s.floor, top - s.margin)) break
            if (picked.none { similar(s.words[it], s.vectors[it], s.words[t], s.vectors[t], s.same) }) picked += t
        }
        return picked.map { s.words[it] }
    }

    /** The same thing named twice: a shared word ("tennis", "tennis match") or near-identical meaning. */
    private fun similar(a: String, va: FloatArray, b: String, vb: FloatArray, same: Float): Boolean {
        val words = a.lowercase().split(' ').toSet()
        return b.lowercase().split(' ').any { it in words } || cosine(va, vb) >= same
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return dot / (sqrt(na) * sqrt(nb))
    }

    /** Everyday things people photograph and search for. */
    private val Vocabulary = listOf(
        "cat", "kitten", "dog", "puppy", "bird", "horse", "cow", "fish", "butterfly", "monkey", "elephant", "rabbit",
        "beach", "sunset", "sunrise", "mountains", "snow", "rain", "forest", "flowers", "trees", "river", "lake", "sea",
        "clouds", "night sky", "moon", "city at night", "skyscrapers", "street", "bridge", "waterfall", "desert", "park",
        "garden", "plants", "car", "motorbike", "bicycle", "bus", "train", "airplane", "boat", "traffic", "road trip",
        "food", "pizza", "burger", "cake", "coffee", "tea", "ice cream", "fruit", "drinks", "biryani", "street food",
        "party", "birthday", "wedding", "concert", "festival", "fireworks", "christmas tree", "diwali lights", "holi colors",
        "selfie", "group photo", "friends", "family", "baby", "kids", "couple", "dancing", "people laughing",
        "football", "cricket", "tennis", "basketball", "formula 1 car", "racing", "gym", "running", "swimming pool",
        "motorcycle racing", "cycling race", "car racing", "badminton", "golf", "boxing",
        "temple", "church", "mosque", "monument", "museum", "painting", "drawing", "book", "document", "receipt",
        "ticket", "id card", "money", "laptop", "computer screen", "code on a screen", "phone", "screenshot of a chat",
        "meme", "tweet", "map", "whiteboard", "classroom", "office", "bedroom", "kitchen", "restaurant", "market",
        "shopping", "shoes", "clothes", "dress", "bikini", "sunglasses", "watch", "jewelry", "makeup", "tattoo",
        "guitar", "piano", "video game", "anime", "cartoon", "movie poster", "tv show", "celebrity", "model posing",
        "mirror selfie", "hotel room", "airport", "aquarium", "zoo", "farm", "camping", "hiking", "snowboarding",
    )
}
