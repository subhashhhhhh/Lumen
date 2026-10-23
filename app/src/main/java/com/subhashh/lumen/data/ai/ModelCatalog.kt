package com.subhashh.lumen.data.ai

import android.content.Context
import java.io.File

/** [url]: where it comes from, when that isn't its model's own repo. */
data class ModelFile(val name: String, val bytes: Long, val sha256: String, val url: String? = null)

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

    fun url(file: ModelFile) = file.url ?: "https://huggingface.co/$repo/resolve/$commit/${file.name}"

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
 * 50 languages (Chinese, English, Japanese, Latin scripts). Downloaded with either Smart Search
 * tier, straight from PaddlePaddle's own repos at pinned commits. `rec.yml` carries the
 * recognizer's character list.
 */
object OcrModel {
    private const val Hf = "https://huggingface.co/PaddlePaddle"
    val files = listOf(
        ModelFile(
            "det.onnx", 9_880_512, "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
            "$Hf/PP-OCRv6_small_det_onnx/resolve/28fe5895c24fd108c19eb3e8479f4ab385fbfc62/inference.onnx",
        ),
        ModelFile(
            "rec.onnx", 21_159_378, "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
            "$Hf/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.onnx",
        ),
        ModelFile(
            "rec.yml", 150_579, "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1",
            "$Hf/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.yml",
        ),
    )
    val bytes: Long get() = files.sumOf { it.bytes }

    fun dir(context: Context) = File(context.noBackupFilesDir, "models/ppocrv6-small")

    fun installed(context: Context): Boolean = files.all { File(dir(context), it.name).length() == it.bytes }
}
