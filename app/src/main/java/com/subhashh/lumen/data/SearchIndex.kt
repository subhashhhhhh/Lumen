package com.subhashh.lumen.data

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Basic, typo-tolerant search over the library, all in memory. Every item is described by
 * words: its file name ("IMG_20240312_101500" → img, 20240312, 101500), its folders, its month
 * and year, and what it is (photo, video, screenshot, favorite, png...). Each word is stored once
 * with the items that have it, so a query compares against the distinct words (tens of
 * thousands), not every item, and answers in a few milliseconds.
 *
 * Every query word must match some word of an item: exactly, as a prefix ("whats" → whatsapp),
 * inside a longer word ("app" → whatsapp), or with one typo ("screnshot"). Results rank by how
 * well the words matched, then newest first.
 */
class SearchIndex private constructor(
    private val items: List<MediaItem>,
    private val words: Array<String>,
    /** For each word, the indexes of the items that have it, ascending. */
    private val postings: Array<IntArray>,
) {

    fun search(query: String): List<MediaItem> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        var scores: HashMap<Int, Int>? = null
        for (term in terms) {
            // Best score per item for this term.
            val best = HashMap<Int, Int>()
            for (w in words.indices) {
                val score = matchScore(term, words[w])
                if (score == 0) continue
                for (item in postings[w]) {
                    if ((best[item] ?: 0) < score) best[item] = score
                }
            }
            // Every term must match: keep only items matched so far, adding up their scores.
            scores = scores?.let { previous ->
                HashMap<Int, Int>().apply {
                    for ((item, score) in previous) best[item]?.let { put(item, score + it) }
                }
            } ?: best
            if (scores.isEmpty()) return emptyList()
        }
        return scores.orEmpty().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { items[it.key].takenAt })
            .map { items[it.key] }
    }

    companion object {

        fun build(items: List<MediaItem>): SearchIndex {
            val byWord = HashMap<String, ArrayList<Int>>()
            val calendar = Calendar.getInstance()
            items.forEachIndexed { index, item ->
                val itemWords = HashSet<String>()
                itemWords += terms(item.name.substringBeforeLast('.'))
                itemWords += item.name.substringAfterLast('.', "").lowercase()
                itemWords += terms(item.relativePath)
                itemWords += terms(item.bucketName)
                calendar.timeInMillis = item.takenAt
                itemWords += Months[calendar.get(Calendar.MONTH)]
                itemWords += calendar.get(Calendar.YEAR).toString()
                itemWords += if (item.isVideo) VideoWords else PhotoWords
                if (item.mimeType == "image/gif") itemWords += "gif"
                if (item.isScreenshot) itemWords += ScreenshotWords
                if (item.isFavorite) itemWords += FavoriteWords
                if (item.isRaw) itemWords += "raw"
                for (word in itemWords) {
                    if (word.isNotEmpty()) byWord.getOrPut(word) { ArrayList() } += index
                }
            }
            val words = byWord.keys.toTypedArray()
            return SearchIndex(items, words, Array(words.size) { byWord.getValue(words[it]).toIntArray() })
        }

        /**
         * Words of [text], lowercased: split on anything that isn't a letter or digit, and where
         * letters meet digits ("IMG20240312" → img, 20240312), so names without separators still split.
         */
        fun terms(text: String): List<String> =
            text.lowercase(Locale.ROOT)
                .split(NonWord)
                .flatMap { it.split(LetterDigit) }
                .filter { it.isNotEmpty() }

        /**
         * How well query word [q] matches index word [w]: 4 exact, 3 prefix, 2 inside it,
         * 1 one typo away (from its start), 0 no match. Short words must match more strictly,
         * or "a" would match everything.
         */
        internal fun matchScore(q: String, w: String): Int = when {
            w == q -> 4
            w.startsWith(q) -> 3
            q.length >= 3 && w.contains(q) -> 2
            q.length >= 4 && abs(w.length - q.length) <= 1 && typos(q, w) <= 1 -> 1
            // A typo in a prefix: "whatsap" for whatsapp, "screnshot" for screenshots.
            // A missing or extra letter shifts the rest, so try prefixes one shorter and longer too.
            q.length >= 5 && w.length > q.length &&
                (q.length - 1..minOf(q.length + 1, w.length)).any { typos(q, w.substring(0, it)) <= 1 } -> 1
            else -> 0
        }

        /** Edits between [a] and [b], counting a swap of two neighbours as one; stops counting past 2. */
        private fun typos(a: String, b: String): Int {
            if (abs(a.length - b.length) > 1) return 2
            val rows = Array(3) { IntArray(b.length + 1) }
            for (j in 0..b.length) rows[0][j] = j
            for (i in 1..a.length) {
                val row = rows[i % 3]
                val prev = rows[(i - 1) % 3]
                row[0] = i
                var rowMin = row[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = min(min(prev[j] + 1, row[j - 1] + 1), prev[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, rows[(i - 2) % 3][j - 2] + 1)
                    row[j] = v
                    rowMin = min(rowMin, v)
                }
                if (rowMin > 1) return 2
            }
            return rows[a.length % 3][b.length]
        }

        private val NonWord = Regex("[^\\p{L}\\p{N}]+")
        private val LetterDigit = Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
        private val Months = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        private val VideoWords = listOf("video", "videos")
        private val PhotoWords = listOf("photo", "photos", "image", "images", "picture", "pictures")
        private val ScreenshotWords = listOf("screenshot", "screenshots")
        private val FavoriteWords = listOf("favorite", "favorites", "favourite", "favourites")
    }
}
