package com.subhashh.lumen.data

import com.subhashh.lumen.data.ai.Person

/**
 * What a search asks for beyond plain words:
 * - **"Quoted phrases"**: only items whose text (read in them, or their caption) has exactly
 *   that phrase, punctuation aside, like Google Photos. A quote still being typed counts.
 * - **Several people** ("hamilton and rosberg", "lewis hamilton & nico"): only items with all
 *   of them, when every word names one of the people, and they're at least two.
 */
object QueryParts {

    /** The phrases in quotes, each as its words; [open] when the last quote isn't closed yet. */
    class Phrases(val phrases: List<List<String>>, val open: Boolean)

    private val Quoted = Regex("\"([^\"]*)(\"|$)")

    fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    fun phrases(query: String): Phrases? {
        if ('"' !in query) return null
        val matches = Quoted.findAll(query).toList()
        val phrases = matches.map { words(it.groupValues[1]) }.filter { it.isNotEmpty() }
        if (phrases.isEmpty()) return null
        return Phrases(phrases, open = matches.last().groupValues[2].isEmpty())
    }

    /** Whether [text] has every phrase, word for word, punctuation and case aside. */
    fun contains(text: String, p: Phrases): Boolean {
        val t = " " + words(text).joinToString(" ") + " "
        return p.phrases.withIndex().all { (i, phrase) ->
            val s = phrase.joinToString(" ")
            // The phrase still being typed may end mid-word.
            if (p.open && i == p.phrases.lastIndex) " $s" in t else " $s " in t
        }
    }

    private val Joiners = setOf("and", "with", "n")

    /**
     * The people a query names, when it names two or more and nothing else: each word must
     * start a word of exactly one chosen person's name. Null otherwise (one person, or other
     * words in it), so plain searches work as before.
     */
    fun people(query: String, people: List<Person>): List<Person>? {
        val terms = words(query).filterNot { it in Joiners }
        if (terms.size < 2) return null
        val named = people.filter { it.name != null && !it.hidden }
        val covers = named.associateWith { p ->
            val nameWords = words(p.name!!)
            terms.filter { t -> nameWords.any { it.startsWith(t) } }.toSet()
        }.filterValues { it.isNotEmpty() }
        // Fewest people covering every word: the person covering most of what's left, first.
        val left = terms.toMutableSet()
        val chosen = ArrayList<Person>()
        while (left.isNotEmpty()) {
            val next = covers.entries.filter { it.key !in chosen }.maxByOrNull { (it.value intersect left).size } ?: return null
            if ((next.value intersect left).isEmpty()) return null
            chosen += next.key
            left -= next.value
        }
        return chosen.takeIf { it.size >= 2 }
    }
}
