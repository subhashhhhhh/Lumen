package com.subhashh.lumen.data.ai

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
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
 * A CLIP-style model (two encoders, one shared space). Text always runs on ONNX Runtime's CPU
 * provider. The image encoder does too, unless [OnnxSpec.visualFile] is a LiteRT `.tflite`: then
 * it runs on the GPU when [gpu] is set (only after [GpuCheck] passed on this phone) and on
 * LiteRT's CPU path otherwise. [dir] holds the files named in [spec].
 */
class OnnxEmbedder(dir: File, private val spec: OnnxSpec, threads: Int, gpu: Boolean = false) : Embedder {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    // Each half loads on first use: indexing never needs the text model, searching never the
    // image one (369 MB for Accurate).
    private val visualEncoder = lazy {
        val file = File(dir, spec.visualFile)
        if (file.name.endsWith(".tflite")) LiteRtImage(file, gpu, threads) else OrtImage(env, file, options, spec.size)
    }
    private val textualSession = lazy { env.createSession(File(dir, spec.textualFile).path, options) }
    private val visual by visualEncoder
    private val textual by textualSession
    private val tokenize by lazy { spec.tokenizer(dir) }
    override val dim = spec.dim

    override fun embedImage(bitmap: Bitmap): FloatArray = normalize(visual.run(pixels(bitmap)))

    /** Loads the text model and runs it once: its first run sets up memory and is slow. */
    fun warmText() {
        embedText("a photo")
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
        if (visualEncoder.isInitialized()) visual.close()
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

        /**
         * Smart Search, Accurate: Google SigLIP 2 B/16. Image: our LiteRT export (fused attention,
         * GPU-ready); int8 lost too much, so fp32 weights. Text: Immich's ONNX export, int8.
         */
        val Siglip = OnnxSpec(
            "visual.tflite", "textual_int8.onnx", 224, floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f),
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

/** An image encoder: normalized CHW pixels in, raw embedding out. */
private interface ImageEncoder : AutoCloseable {
    fun run(pixels: FloatArray): FloatArray
}

private class OrtImage(
    private val env: OrtEnvironment,
    file: File,
    options: OrtSession.SessionOptions,
    private val size: Int,
) : ImageEncoder {
    private val session = env.createSession(file.path, options)

    override fun run(pixels: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, size.toLong(), size.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<FloatArray>)[0]
            }
        }
    }

    override fun close() = session.close()
}

/**
 * LiteRT. On the GPU always at fp32: SigLIP 2 overflows half precision (fp16 gave vectors at
 * cosine 0.55 to the right ones on the edge 50; fp32 matches exactly and was 2.7x the CPU's speed).
 */
private class LiteRtImage(file: File, gpu: Boolean, threads: Int) : ImageEncoder {
    private val environment = Environment.create()
    private val model = CompiledModel.create(
        file.path,
        CompiledModel.Options(if (gpu) Accelerator.GPU else Accelerator.CPU).apply {
            if (gpu) gpuOptions = CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32)
            else cpuOptions = CompiledModel.CpuOptions(numThreads = threads)
        },
        environment,
    )
    private val inputs = model.createInputBuffers(0)
    private val outputs = model.createOutputBuffers(0)

    override fun run(pixels: FloatArray): FloatArray {
        inputs[0].writeFloat(pixels)
        model.run(inputs, outputs, 0)
        return outputs[0].readFloat()
    }

    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
        environment.close()
    }
}
