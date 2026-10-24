package com.subhashh.lumen.data.ai

import android.content.Context
import java.io.File

data class ModelFile(val name: String, val bytes: Long, val sha256: String)

/**
 * A downloadable model, pinned to one commit of its Hugging Face repo so the files can't change
 * under us; each file's SHA-256 is checked before it's used.
 */
data class AiModel(
    val id: String,
    /** What users see: the tier, never the model's name. */
    val label: String,
    val repo: String,
    val commit: String,
    val files: List<ModelFile>,
    /** How to run it once downloaded. */
    val spec: OnnxSpec,
) {
    val bytes: Long get() = files.sumOf { it.bytes }

    fun url(file: ModelFile) = "https://huggingface.co/$repo/resolve/$commit/${file.name}"

    /** App-private, never backed up: the files are large and re-downloadable. */
    fun dir(context: Context) = File(context.noBackupFilesDir, "models/$id")

    fun installed(context: Context): Boolean =
        files.all { File(dir(context), it.name).length() == it.bytes }
}

/**
 * Smart Search's quality tiers. Chosen in the Search Lab on the owner's photos; EmbeddingGemma 2,
 * MobileCLIP2 and heavier models (PE-Core, SigLIP 2 So400m, Qwen3-VL-Embedding) were tested or
 * researched and left out. Conversions: tools/models/. Hosting: huggingface.co/subhashhhhhh.
 */
object ModelCatalog {
    /** Google's SigLIP 2 B/16 (Apache 2.0): fp32 image encoder (LiteRT, GPU-ready), int8 text, packed tokenizer. */
    val Accurate = AiModel(
        id = "siglip2-b16",
        label = "Accurate",
        repo = "subhashhhhhh/lumen-siglip2-b16",
        commit = "4b62b4b89ca725a72cde54dc75bb6bb3a4994c63",
        files = listOf(
            ModelFile("visual.tflite", 369_330_540, "b358184561275f176135257853e9927dd5fa0ca6e9cd3f52c63572d5a9740951"),
            ModelFile("textual_int8.onnx", 283_187_112, "6fdbedc85c54262bcbf380cfb048f03e10cf07237333fe4ca1e204a8f954d608"),
            ModelFile("gemma_bpe.bin", 7_134_060, "e9163029a76e66dadb07afd9373531a24687e25d7a027f65d13a2977331f53c2"),
        ),
        spec = OnnxEmbedder.Siglip,
    )

    /** OpenCLIP ViT-B/32 LAION-2B (MIT), int8: about 8x faster to index, English only. */
    val Fast = AiModel(
        id = "clip-vit-b32",
        label = "Fast",
        repo = "subhashhhhhh/lumen-clip-vit-b32",
        commit = "8163ae3be41a5698dad50a5696c9d577d390c91c",
        files = listOf(
            ModelFile("visual_int8.onnx", 95_777_877, "17755a1228aee44f4274eadac14a600a5e45ab9f6325b6ad070b5d0d75e05073"),
            ModelFile("textual_int8.onnx", 140_296_927, "e1631f9b00b2a14e5b268a9c73000b1eec3c79bd7d4876ce10fe4f0de1a03b1e"),
            ModelFile("vocab.json", 862_328, "5047b556ce86ccaf6aa22b3ffccfc52d391ea4accdab9c2f2407da5b742d4363"),
            ModelFile("merges.txt", 524_619, "9fd691f7c8039210e0fced15865466c65820d09b63988b0174bfe25de299051a"),
        ),
        spec = OnnxEmbedder.Clip,
    )

    val all = listOf(Accurate, Fast)

    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Accurate
}

/**
 * Reading text in photos: Baidu's PP-OCRv6 small (Apache 2.0), text detection and recognition,
 * 50 languages (Chinese, English, Japanese, Latin scripts), plus our GPU conversion of the detector
 * (tools/ocr/; the repo also has GPU line readers, tried and left out: no faster on real lines). Downloaded with either Smart Search tier. `rec.yml` carries the recognizer's
 * character list.
 */
object OcrModel {
    const val Repo = "subhashhhhhh/lumen-ppocrv6-small"
    const val Commit = "508f47d58c5f66babaf11c78f902b8cdefa5e5c6"

    /** Detection for LiteRT's GPU, 960×960; used where [GpuCheck] passes. */
    const val DetGpu = "det_gpu.tflite"

    /** What text reading needs; the GPU files only make it faster. */
    private val core = listOf(
        ModelFile("det.onnx", 9_880_512, "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"),
        ModelFile("rec.onnx", 21_159_378, "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"),
        ModelFile("rec.yml", 150_579, "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"),
    )

    val files = core + listOf(
        ModelFile(DetGpu, 9_869_976, "8a7676cbc5a01ca47544bf95af2196c8b1b6c7b75beec9ad9b17857dffb71d1e"),
    )
    val bytes: Long get() = files.sumOf { it.bytes }

    fun url(file: ModelFile) = "https://huggingface.co/$Repo/resolve/$Commit/${file.name}"

    fun dir(context: Context) = File(context.noBackupFilesDir, "models/ppocrv6-small")

    /** Text can be read: an older download without the newer GPU files still reads, on the CPU. */
    fun installed(context: Context): Boolean = core.all { File(dir(context), it.name).length() == it.bytes }

    /** Every file of this version is in place; otherwise Settings offers the rest as an update. */
    fun complete(context: Context): Boolean = files.all { File(dir(context), it.name).length() == it.bytes }

    /** Deletes files this version doesn't use (the GPU line readers, briefly downloaded). */
    fun removeUnused(context: Context) {
        val wanted = files.flatMapTo(HashSet()) { listOf(it.name, it.name + ".part") }
        dir(context).listFiles()?.filter { it.name !in wanted }?.forEach { it.deleteRecursively() }
    }
}

/**
 * Grouping photos by person: YuNet finds faces and their landmarks (MIT), SFace turns each
 * straightened face into a 128-number fingerprint (Apache 2.0). From OpenCV Zoo, unchanged;
 * chosen on the owner's photos over AuraFace and InsightFace's (tools/faces/). Downloaded with
 * either Smart Search tier.
 */
object FaceModel {
    const val Repo = "subhashhhhhh/lumen-faces"
    const val Commit = "009b05388d32e9d7d487fa2a78fb9e4a3c30b6f3"

    val files = listOf(
        ModelFile("yunet.onnx", 229_738, "ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0"),
        ModelFile("sface.onnx", 38_696_353, "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"),
    )
    val bytes: Long get() = files.sumOf { it.bytes }

    fun url(file: ModelFile) = "https://huggingface.co/$Repo/resolve/$Commit/${file.name}"

    fun dir(context: Context) = File(context.noBackupFilesDir, "models/faces")

    fun installed(context: Context): Boolean = files.all { File(dir(context), it.name).length() == it.bytes }
}

/**
 * Subtitles: Cactus Compute's Whistle (speech to text, Apache 2.0) and Silero VAD v6.2.3 (where
 * people speak, MIT), so music isn't "transcribed" into invented lines.
 */
object SpeechModel {
    /** Settings' choices, by quality: Whistle for now; Whisper or Parakeet if they earn a place. */
    class Tier(val id: String, val label: String)
    val tiers = listOf(Tier("whistle", "Fast"))

    val files = listOf(
        ModelFile("whistle.cact", 16_919_407, "b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb"),
        ModelFile("vad.onnx", 1_289_603, "7ed98ddbad84ccac4cd0aeb3099049280713df825c610a8ed34543318f1b2c49"),
    )
    val bytes: Long get() = files.sumOf { it.bytes }

    fun dir(context: Context) = File(context.noBackupFilesDir, "models/speech")

    fun installed(context: Context): Boolean = files.all { File(dir(context), it.name).length() == it.bytes }
}
