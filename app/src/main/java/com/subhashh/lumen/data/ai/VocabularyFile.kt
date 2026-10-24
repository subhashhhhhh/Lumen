package com.subhashh.lumen.data.ai

import android.util.Half
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * The "Looks like" vocabulary as shipped with a search model (vocabulary.bin): words from
 * Recognize Anything's tag list (Apache 2.0, cleaned up) and each one's text vector, made by
 * this app's own text model and tokenizer (Search Lab), so they match searches exactly.
 * Format: "LUMV", version, count, dimensions, then per word its UTF-8 text and fp16 vector.
 */
object VocabularyFile {
    private const val Magic = 0x4C554D56 // "LUMV"
    private const val Version = 1

    fun write(file: File, words: List<String>, vectors: List<FloatArray>) {
        file.parentFile?.mkdirs()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(Magic)
            out.writeInt(Version)
            out.writeInt(words.size)
            out.writeInt(vectors.firstOrNull()?.size ?: 0)
            for ((w, v) in words.zip(vectors)) {
                out.writeUTF(w)
                for (x in v) out.writeShort(Half.toHalf(x).toInt())
            }
        }
    }

    /** The words and their vectors, or null when the file isn't there or isn't one of these. */
    fun read(file: File): Pair<List<String>, List<FloatArray>>? = runCatching {
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != Magic || input.readInt() != Version) return null
            val n = input.readInt()
            val dim = input.readInt()
            val words = ArrayList<String>(n)
            val vectors = ArrayList<FloatArray>(n)
            repeat(n) {
                words += input.readUTF()
                vectors += FloatArray(dim) { Half.toFloat(input.readShort()) }
            }
            words to vectors
        }
    }.getOrNull()
}
