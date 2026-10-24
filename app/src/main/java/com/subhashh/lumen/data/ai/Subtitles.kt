package com.subhashh.lumen.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.coroutines.coroutineContext

/** One subtitle line and when it's on screen, in ms from the video's start. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Where people speak in 16 kHz audio: Silero VAD v6 (MIT, 1.3 MB) on ONNX Runtime. Whistle
 * invents sentences ("Thank you.", "Vielen Dank.") from music, and most edits are music, so only
 * what this marks as speech is transcribed. Tested on the owner's clips: a music reel 0% speech,
 * two talking ones 74% and 79%.
 */
class SpeechDetector(dir: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) }
    private val session = env.createSession(File(dir, "vad.onnx").path, options)

    /** Speech stretches in ms from the start of [pcm], padded and merged across short pauses. */
    fun speech(pcm: FloatArray): List<LongRange> {
        var state = FloatArray(2 * 128)
        val context = FloatArray(ContextSamples)
        val probs = ArrayList<Float>()
        val input = FloatArray(ContextSamples + Chunk)
        var i = 0
        while (i + Chunk <= pcm.size) {
            context.copyInto(input, 0)
            pcm.copyInto(input, ContextSamples, i, i + Chunk)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, (ContextSamples + Chunk).toLong())).use { x ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { s ->
                    OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(AudioPcm.Rate.toLong())), longArrayOf()).use { sr ->
                        session.run(mapOf("input" to x, "state" to s, "sr" to sr)).use { r ->
                            val out = (r[0] as OnnxTensor).floatBuffer
                            probs += out.get(0)
                            val next = (r[1] as OnnxTensor).floatBuffer
                            state = FloatArray(next.remaining()).also { next.get(it) }
                        }
                    }
                }
            }
            pcm.copyInto(context, 0, i + Chunk - ContextSamples, i + Chunk)
            i += Chunk
        }
        // Speech starts above Start and lasts until it drops below End (hysteresis).
        val ranges = ArrayList<LongRange>()
        var from = -1
        for ((k, p) in probs.withIndex()) {
            if (from < 0 && p >= Start) from = k
            else if (from >= 0 && p < End) {
                ranges += ms(from)..ms(k)
                from = -1
            }
        }
        if (from >= 0) ranges += ms(from)..ms(probs.size)
        // Pad, drop blips, and join stretches split by a breath.
        val merged = ArrayList<LongRange>()
        for (r in ranges.filter { it.last - it.first >= MinSpeechMs }) {
            val padded = (r.first - PadMs).coerceAtLeast(0)..(r.last + PadMs)
            val last = merged.lastOrNull()
            if (last != null && padded.first - last.last <= JoinMs) merged[merged.size - 1] = last.first..padded.last
            else merged += padded
        }
        return merged
    }

    private fun ms(chunk: Int) = chunk.toLong() * Chunk * 1000 / AudioPcm.Rate

    override fun close() {
        session.close()
        options.close()
    }

    private companion object {
        /** 32 ms at 16 kHz, with 64 samples of the chunk before it, as Silero v5+ takes it. */
        const val Chunk = 512
        const val ContextSamples = 64
        const val Start = 0.5f
        const val End = 0.35f
        const val MinSpeechMs = 250L
        const val PadMs = 200L
        const val JoinMs = 600L
    }
}

/**
 * Subtitles made on the phone, on demand (the viewer's CC button): the video's sound in 30 s
 * windows, the one being watched first, then onward, then from the start; in each, only the
 * speech ([SpeechDetector]) is transcribed ([Whistle]) and its words grouped into short lines.
 * Saved per video version, so watching again is instant.
 */
object Subtitles {
    private const val Tag = "LumenSubtitles"
    private const val WindowMs = 30_000L

    /** Cues so far, each emission a fuller list; finished when every window is done. */
    fun generate(context: Context, item: MediaItem, fromMs: Long): Flow<List<Cue>> = flow {
        val file = file(context, item)
        val saved = load(file)
        val done = saved?.first?.toMutableSet() ?: mutableSetOf()
        val cues = saved?.second?.toMutableList() ?: mutableListOf()
        emit(cues.toList())
        val windows = (0 until ((item.durationMs + WindowMs - 1) / WindowMs).coerceAtLeast(1)).map { it * WindowMs }
        val first = (fromMs / WindowMs * WindowMs).coerceIn(0, windows.last())
        val order = windows.filter { it >= first } + windows.filter { it < first }
        if (order.all { it in done }) return@flow
        check(Whistle.load(File(SpeechModel.dir(context), "whistle.cact"))) { "Speech model unavailable" }
        val keywords = keywords(context, item)
        SpeechDetector(SpeechModel.dir(context)).use { vad ->
            for (start in order) {
                if (start in done) continue
                coroutineContext.ensureActive()
                val pcm = AudioPcm.read(context, item.uri, start, WindowMs)
                for (range in vad.speech(pcm)) {
                    val from = (range.first * AudioPcm.Rate / 1000).toInt().coerceIn(0, pcm.size)
                    val to = (range.last * AudioPcm.Rate / 1000).toInt().coerceIn(from, pcm.size)
                    if (to - from < AudioPcm.Rate / 4) continue
                    val transcript = Whistle.transcribe(pcm.copyOfRange(from, to), keywords = keywords) ?: continue
                    // A short sound read as one of the model's stock phrases was invented.
                    if (range.last - range.first < PhantomMs && transcript.text.trim().lowercase().trimEnd('.', '!') in Phantoms) continue
                    val offset = start + range.first
                    cues += lines(transcript.words.map { it.copy(startMs = it.startMs + offset, endMs = it.endMs + offset) })
                }
                done += start
                cues.sortBy { it.startMs }
                save(file, done, cues)
                emit(cues.toList())
                Log.d(Tag, "${item.name.take(32)}: window ${start / 1000}s done, ${cues.size} lines")
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Names to listen for: the people the user named, and the longer words on screen in this
     * video (captions often name who's speaking). Keyword biasing turned "Now my bike" into
     * "Marquez" on the owner's MotoGP edit.
     */
    private fun keywords(context: Context, item: MediaItem): List<String> {
        val people = runCatching { FaceStore.of(context).people().mapNotNull { it.name } }.getOrDefault(emptyList())
        val onScreen = runCatching { TextStore.of(context).text(item.id) }.getOrNull().orEmpty()
            .split(Regex("[^\\p{L}'’]+"))
            .filter { it.length >= 4 }
            .map { w -> if (w == w.uppercase()) w.lowercase().replaceFirstChar { it.uppercase() } else w }
            .distinct()
            .take(MaxScreenWords)
        return (people + onScreen).distinct()
    }

    /** Stock phrases Whisper-like models produce from noise or music. */
    private val Phantoms = setOf("thank you", "thanks for watching", "thank you for watching", "vielen dank", "sous-titrage st'", "bye")
    private const val PhantomMs = 2_500L
    private const val MaxScreenWords = 40
    /** Abbreviations that end with a full stop but don't end a sentence. */
    private val Abbreviations = setOf("mr.", "mrs.", "ms.", "dr.", "st.", "jr.", "sr.", "vs.", "mt.")

    /** Words into lines: a new line after a pause, a sentence's end, or ~42 characters. */
    private fun lines(words: List<SpokenWord>): List<Cue> {
        val out = ArrayList<Cue>()
        var line = ArrayList<SpokenWord>()
        fun flush() {
            // A line the model was unsure of is likelier invented than heard.
            if (line.isNotEmpty() && line.map { it.probability }.average() >= MinConfidence) {
                out += Cue(line.first().startMs, line.last().endMs + LingerMs, line.joinToString(" ") { it.text.trim() })
            }
            line = ArrayList()
        }
        fun endsSentence(w: SpokenWord): Boolean {
            val t = w.text.trim()
            return (t.endsWith('.') && t.lowercase() !in Abbreviations) || t.endsWith('?') || t.endsWith('!')
        }
        for ((i, w) in words.withIndex()) {
            val last = line.lastOrNull()
            val length = line.sumOf { it.text.length + 1 } + w.text.length
            // A sentence's last word may run a little long rather than sit alone on a line.
            val overflow = length > MaxChars && !(endsSentence(w) && length <= MaxChars + 12)
            if (last != null && (w.startMs - last.endMs > PauseMs || overflow)) flush()
            line += w
            if (endsSentence(w)) flush()
        }
        flush()
        // A line stays until the next starts, never longer than its linger.
        return out.mapIndexed { i, c -> out.getOrNull(i + 1)?.let { n -> c.copy(endMs = minOf(c.endMs, n.startMs)) } ?: c }
    }

    private const val MaxChars = 42
    private const val PauseMs = 700L
    private const val LingerMs = 400L
    private const val MinConfidence = 0.35

    /** Subtitles made so far for [item], and whether they cover the whole video; null if none. */
    fun saved(context: Context, item: MediaItem): Pair<List<Cue>, Boolean>? {
        val (done, cues) = load(file(context, item)) ?: return null
        val windows = ((item.durationMs + WindowMs - 1) / WindowMs).coerceAtLeast(1)
        return cues to (done.size >= windows)
    }

    /** The cues as SubRip (.srt), which nearly every player and editor reads. */
    fun srt(cues: List<Cue>): String = buildString {
        cues.forEachIndexed { i, c ->
            append(i + 1).append('\n')
            append(time(c.startMs, ',')).append(" --> ").append(time(c.endMs, ',')).append('\n')
            append(c.text).append("\n\n")
        }
    }

    /** The cues as WebVTT (.vtt), for browsers and web players. */
    fun vtt(cues: List<Cue>): String = buildString {
        append("WEBVTT\n\n")
        for (c in cues) {
            append(time(c.startMs, '.')).append(" --> ").append(time(c.endMs, '.')).append('\n')
            append(c.text).append("\n\n")
        }
    }

    /** "[0:12] Hello there", one line per cue. */
    fun timestamped(cues: List<Cue>): String = cues.joinToString("\n") { c ->
        val s = c.startMs / 1000
        "[%d:%02d] %s".format(s / 60, s % 60, c.text)
    }

    private fun time(ms: Long, separator: Char) =
        "%02d:%02d:%02d%c%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, separator, ms % 1000)

    private fun file(context: Context, item: MediaItem) =
        // v2: line breaks, stock-phrase filter and keywords; earlier files are made again.
        File(context.noBackupFilesDir, "subtitles/v2/${item.id}-${item.modified}.json").apply { parentFile?.mkdirs() }

    private fun load(file: File): Pair<Set<Long>, List<Cue>>? = runCatching {
        val o = JSONObject(file.readText())
        val done = o.getJSONArray("done").let { a -> List(a.length()) { a.getLong(it) }.toSet() }
        val cues = o.getJSONArray("cues").let { a ->
            List(a.length()) { a.getJSONArray(it).let { c -> Cue(c.getLong(0), c.getLong(1), c.getString(2)) } }
        }
        done to cues
    }.getOrNull()

    private fun save(file: File, done: Set<Long>, cues: List<Cue>) {
        val o = JSONObject()
            .put("done", JSONArray(done.sorted()))
            .put("cues", JSONArray(cues.map { JSONArray().put(it.startMs).put(it.endMs).put(it.text) }))
        file.writeText(o.toString())
    }
}
