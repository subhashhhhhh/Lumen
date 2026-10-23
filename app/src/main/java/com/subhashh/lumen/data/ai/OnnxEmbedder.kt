package com.subhashh.lumen.data.ai

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

/** One image+text embedding model: photos and queries land in the same space. */
interface Embedder : AutoCloseable {
    val dim: Int
    /** L2-normalized, so a dot product is the cosine similarity. */
    fun embedImage(bitmap: Bitmap): FloatArray
    fun embedText(query: String): FloatArray
}

/** How an ONNX image+text model wants its inputs. */
class OnnxSpec(
    val visualFile: String,
    val textualFile: String,
    val size: Int,
    val mean: FloatArray,
    val std: FloatArray,
    /** Shortest side to [size] then center crop; false squashes the whole photo to a square. */
    val centerCrop: Boolean,
    val dim: Int,
    val tokenizerFiles: List<String>,
    val tokenizer: (File) -> (String) -> IntArray,
) {
    val files get() = listOf(visualFile, textualFile) + tokenizerFiles
}

/**
 * A CLIP-style model exported to ONNX (two encoders, one shared space) on ONNX Runtime's CPU
 * provider. [dir] holds the files named in [spec].
 */
class OnnxEmbedder(dir: File, private val spec: OnnxSpec, threads: Int) : Embedder {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    // Each half loads on first use: indexing never needs the text model, searching never the
    // image one (371 MB for Accurate).
    private val visualSession = lazy { env.createSession(File(dir, spec.visualFile).path, options) }
    private val textualSession = lazy { env.createSession(File(dir, spec.textualFile).path, options) }
    private val visual by visualSession
    private val textual by textualSession
    private val tokenize by lazy { spec.tokenizer(dir) }
    override val dim = spec.dim

    override fun embedImage(bitmap: Bitmap): FloatArray {
        val size = spec.size.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels(bitmap)), longArrayOf(1, 3, size, size)).use { tensor ->
            visual.run(mapOf(visual.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return normalize((result[0].value as Array<FloatArray>)[0])
            }
        }
    }

    override fun embedText(query: String): FloatArray {
        val ids = tokenize(query)
        val name = textual.inputNames.first()
        val type = (textual.inputInfo.getValue(name).info as TensorInfo).type
        val shape = longArrayOf(1, ids.size.toLong())
        val tensor = if (type == OnnxJavaType.INT64) {
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), shape)
        } else {
            OnnxTensor.createTensor(env, IntBuffer.wrap(ids), shape)
        }
        tensor.use {
            textual.run(mapOf(name to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return normalize((result[0].value as Array<FloatArray>)[0])
            }
        }
    }

    override fun close() {
        if (visualSession.isInitialized()) visual.close()
        if (textualSession.isInitialized()) textual.close()
        options.close()
    }

    /** Resize (crop or squash), then CHW floats normalized with the model's mean and std. */
    internal fun pixels(original: Bitmap): FloatArray {
        // Thumbnails can come back as hardware bitmaps, which can't be read pixel by pixel.
        val source = if (original.config == Bitmap.Config.HARDWARE) original.copy(Bitmap.Config.ARGB_8888, false) else original
        val size = spec.size
        val (w, h) = if (spec.centerCrop) {
            val scale = size.toFloat() / minOf(source.width, source.height)
            maxOf(size, Math.round(source.width * scale)) to maxOf(size, Math.round(source.height * scale))
        } else {
            size to size
        }
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val argb = IntArray(size * size)
        scaled.getPixels(argb, 0, size, (w - size) / 2, (h - size) / 2, size, size)
        if (scaled !== source) scaled.recycle()
        if (source !== original) source.recycle()
        val mean = spec.mean
        val std = spec.std
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = argb[i]
            out[i] = ((p shr 16 and 0xFF) / 255f - mean[0]) / std[0]
            out[plane + i] = ((p shr 8 and 0xFF) / 255f - mean[1]) / std[1]
            out[2 * plane + i] = ((p and 0xFF) / 255f - mean[2]) / std[2]
        }
        return out
    }

    companion object {
        private val ClipMean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val ClipStd = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        internal val ClipFiles = listOf("vocab.json", "merges.txt")
        internal val clipBpe: (File) -> (String) -> IntArray = { dir ->
            ClipTokenizer(File(dir, "vocab.json"), File(dir, "merges.txt"))::tokenize.let { t -> { q -> t(q, 77) } }
        }

        /** Smart Search, Fast: OpenCLIP ViT-B-32 laion2b-s34b-b79k, Immich's export, int8. */
        val Clip = OnnxSpec("visual_int8.onnx", "textual_int8.onnx", 224, ClipMean, ClipStd, true, 512, ClipFiles, clipBpe)

        /** Smart Search, Accurate: Google SigLIP 2 B/16 (Immich's export): fp32 image (int8 lost too much), int8 text. */
        val Siglip = OnnxSpec(
            "visual.onnx", "textual_int8.onnx", 224, floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f),
            false, 768, listOf("gemma_bpe.bin"), { dir -> GemmaBpeTokenizer(File(dir, "gemma_bpe.bin"))::tokenize.let { t -> { q -> t(q, 64) } } },
        )
    }
}

fun normalize(v: FloatArray): FloatArray {
    var sum = 0f
    for (x in v) sum += x * x
    val inv = 1f / sqrt(sum)
    return FloatArray(v.size) { v[it] * inv }
}
