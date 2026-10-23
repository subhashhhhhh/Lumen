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
 * Search suggestions from what's actually in the library. Each everyday thing in [Vocabulary]
 * is scored against the index: how far its ten best-matching items stand above the library's
 * typical score for it (in standard deviations). Things a library really has stand out; things
 * it doesn't (no dogs in it, say) don't, because nothing matches them much better than average.
 * Computed in the background after indexing and kept in a small file; refreshed as the library grows.
 */
object SearchSuggestions {
    private const val Tag = "LumenSuggest"
    private const val Keep = 24

    private fun file(context: Context, model: AiModel) = File(context.noBackupFilesDir, "search/suggestions-${model.id}.json")

    /** The best ones for this library, strongest first; empty until computed. */
    fun load(context: Context, model: AiModel): List<Suggestion> = runCatching {
        val json = JSONObject(file(context, model).readText())
        val list = json.getJSONArray("suggestions")
        List(list.length()) { i -> list.getJSONObject(i).let { Suggestion(it.getString("term"), it.getLong("cover")) } }
    }.getOrDefault(emptyList())

    /** Recomputes when missing or when the index has grown or shrunk noticeably since. */
    fun refreshIfStale(context: Context, model: AiModel) {
        val count = EmbeddingStore.of(context, model).count()
        if (count == 0) return
        val previous = runCatching { JSONObject(file(context, model).readText()).getInt("indexed") }.getOrNull()
        if (previous != null && kotlin.math.abs(count - previous) < maxOf(50, previous / 50)) return
        refresh(context, model, count)
    }

    private fun refresh(context: Context, model: AiModel, indexed: Int) {
        val start = SystemClock.elapsedRealtime()
        val all = EmbeddingStore.of(context, model).loadAll()
        if (all.size == 0) return
        // Which item each row belongs to: one row per photo, one per kept frame of a video.
        val index = HashMap<Long, Int>()
        val itemIds = ArrayList<Long>()
        val itemOf = IntArray(all.size) { i -> index.getOrPut(all.mediaIds[i]) { itemIds.add(all.mediaIds[i]); itemIds.size - 1 } }
        val dim = all.dim
        val scored = ArrayList<Triple<String, Double, Long>>()
        OnnxEmbedder(model.dir(context), model.spec, threads = 2).use { embedder ->
            val itemScores = FloatArray(itemIds.size)
            for (term in Vocabulary) {
                val q = embedder.embedText(term)
                itemScores.fill(Float.NEGATIVE_INFINITY)
                for (i in 0 until all.size) {
                    var dot = 0f
                    val base = i * dim
                    for (d in 0 until dim) dot += q[d] * all.vectors[base + d]
                    if (dot > itemScores[itemOf[i]]) itemScores[itemOf[i]] = dot
                }
                val order = itemScores.indices.sortedByDescending { itemScores[it] }
                val top = order.take(10).map { itemScores[it].toDouble() }.average()
                val mean = itemScores.average()
                val sd = sqrt(itemScores.sumOf { (it - mean) * (it - mean) } / itemScores.size)
                if (sd > 0) scored += Triple(term, (top - mean) / sd, itemIds[order.first()])
            }
        }
        // Strongest first; one suggestion per cover photo (cat and kitten would share one).
        val covers = HashSet<Long>()
        val best = scored.sortedByDescending { it.second }.filter { covers.add(it.third) }.take(Keep)
        val json = JSONObject()
            .put("indexed", indexed)
            .put("suggestions", JSONArray().apply {
                best.forEach { put(JSONObject().put("term", it.first).put("cover", it.third).put("strength", it.second)) }
            })
        file(context, model).apply { parentFile?.mkdirs() }.writeText(json.toString())
        Log.i(Tag, "Suggestions for ${model.id} in ${SystemClock.elapsedRealtime() - start} ms: " +
            best.take(12).joinToString { "${it.first} %.1f".format(it.second) })
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
        "temple", "church", "mosque", "monument", "museum", "painting", "drawing", "book", "document", "receipt",
        "ticket", "id card", "money", "laptop", "computer screen", "code on a screen", "phone", "screenshot of a chat",
        "meme", "tweet", "map", "whiteboard", "classroom", "office", "bedroom", "kitchen", "restaurant", "market",
        "shopping", "shoes", "clothes", "dress", "bikini", "sunglasses", "watch", "jewelry", "makeup", "tattoo",
        "guitar", "piano", "video game", "anime", "cartoon", "movie poster", "tv show", "celebrity", "model posing",
        "mirror selfie", "hotel room", "airport", "aquarium", "zoo", "farm", "camping", "hiking", "snowboarding",
    )
}
