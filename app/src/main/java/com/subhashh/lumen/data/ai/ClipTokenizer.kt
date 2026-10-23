/*
 * Ported from OpenAI CLIP's simple_tokenizer.py, https://github.com/openai/CLIP
 * Copyright (c) 2021 OpenAI. MIT License.
 */
package com.subhashh.lumen.data.ai

import org.json.JSONObject
import java.io.File

/**
 * CLIP's byte-level BPE: text → 77 token ids, `<|startoftext|> … <|endoftext|>`, zero-padded
 * (OpenCLIP's padding, which Immich's exports expect). Reads the `vocab.json` and `merges.txt`
 * shipped with the model. Skips ftfy's mojibake repair, which only matters for broken input.
 */
class ClipTokenizer(vocabFile: File, mergesFile: File) {

    private val encoder: Map<String, Int> = JSONObject(vocabFile.readText()).let { json ->
        HashMap<String, Int>(json.length()).apply { json.keys().forEach { put(it, json.getInt(it)) } }
    }
    private val bpeRanks: Map<Pair<String, String>, Int> = HashMap<Pair<String, String>, Int>().apply {
        mergesFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#version") }
            .forEachIndexed { rank, line ->
                val (a, b) = line.split(' ', limit = 2)
                put(a to b, rank)
            }
    }
    private val byteEncoder: Array<Char> = bytesToUnicode()
    private val cache = HashMap<String, List<String>>()
    private val startToken = encoder.getValue("<|startoftext|>")
    private val endToken = encoder.getValue("<|endoftext|>")

    fun tokenize(text: String, contextLength: Int = 77): IntArray {
        val ids = ArrayList<Int>()
        ids += startToken
        val clean = text.replace(Whitespace, " ").trim().lowercase()
        for (match in Pattern.findAll(clean)) {
            val token = match.value.toByteArray(Charsets.UTF_8)
                .joinToString("") { byteEncoder[it.toInt() and 0xFF].toString() }
            for (piece in bpe(token)) encoder[piece]?.let { ids += it }
        }
        if (ids.size > contextLength - 1) ids.subList(contextLength - 1, ids.size).clear()
        ids += endToken
        return IntArray(contextLength) { ids.getOrElse(it) { 0 } }
    }

    private fun bpe(token: String): List<String> = cache.getOrPut(token) {
        var word = token.dropLast(1).map { it.toString() } + (token.last() + "</w>")
        while (word.size > 1) {
            val bigram = word.zipWithNext().minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (bigram !in bpeRanks) break
            val merged = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == bigram.first && word[i + 1] == bigram.second) {
                    merged += bigram.first + bigram.second
                    i += 2
                } else {
                    merged += word[i]
                    i += 1
                }
            }
            word = merged
        }
        word
    }

    private companion object {
        val Whitespace = Regex("\\s+")
        val Pattern = Regex(
            "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+",
            RegexOption.IGNORE_CASE,
        )

        /** GPT-2's reversible byte → printable character table. */
        fun bytesToUnicode(): Array<Char> {
            val printable = ('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)
            val table = arrayOfNulls<Char>(256)
            printable.forEach { table[it] = it.toChar() }
            var n = 0
            for (b in 0 until 256) if (table[b] == null) table[b] = (256 + n++).toChar()
            return table.requireNoNulls()
        }
    }
}
