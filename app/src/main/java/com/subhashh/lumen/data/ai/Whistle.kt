package com.subhashh.lumen.data.ai

import android.util.Log
import org.json.JSONObject
import java.io.File

/** One transcribed word, times in ms from the start of the audio given. */
data class SpokenWord(val text: String, val startMs: Long, val endMs: Long, val probability: Float)

class Transcript(val text: String, val language: String, val words: List<SpokenWord>)

/**
 * Speech to text with Cactus Compute's Whistle (Apache 2.0, 16.9 MB): English, German, French,
 * Spanish, Italian, Dutch and Polish, with word timings. Runs on Cactus's needle engine, a
 * native library (src/main/cpp), CPU only. The engine keeps one model for the whole process and
 * isn't thread-safe, so every call here is serialized.
 */
object Whistle {
    private const val Tag = "LumenSpeech"
    private var loaded: File? = null
    private val available: Boolean = runCatching { System.loadLibrary("lumen_speech") }
        .onFailure { Log.w(Tag, "No speech engine on this device", it) }.isSuccess

    @JvmStatic private external fun nativeLoad(model: ByteArray): String?
    @JvmStatic private external fun nativeTranscribe(pcm: FloatArray, language: String?, keywords: String?): ByteArray?
    @JvmStatic private external fun nativeLastError(): String

    /** Loads [model] (a .cact file) once; false if this device can't run it. */
    @Synchronized
    fun load(model: File): Boolean {
        if (!available) return false
        if (loaded != null) return loaded == model
        val error = nativeLoad(model.readBytes())
        if (error != null) {
            Log.w(Tag, "Couldn't load ${model.name}: $error")
            return false
        }
        loaded = model
        return true
    }

    /**
     * Transcribes 16 kHz mono audio in [-1, 1]; [language] "en" etc., or null to detect it.
     * [keywords]: words and names to listen for (keyword biasing); names come out far better
     * with them. Null if the engine failed.
     */
    @Synchronized
    fun transcribe(pcm: FloatArray, language: String? = null, keywords: List<String> = emptyList()): Transcript? {
        check(loaded != null) { "Load the model first" }
        val json = nativeTranscribe(pcm, language, keywords.takeIf { it.isNotEmpty() }?.joinToString("\n")) ?: run {
            Log.w(Tag, "Transcription failed: ${nativeLastError()}")
            return null
        }
        val o = JSONObject(String(json, Charsets.UTF_8))
        val words = o.optJSONArray("words")?.let { a ->
            List(a.length()) { i ->
                val w = a.getJSONObject(i)
                SpokenWord(
                    w.getString("word"),
                    (w.getDouble("start") * 1000).toLong(),
                    (w.getDouble("end") * 1000).toLong(),
                    w.optDouble("probability", 0.0).toFloat(),
                )
            }
        }.orEmpty()
        return Transcript(o.optString("text"), o.optString("language"), words)
    }
}
