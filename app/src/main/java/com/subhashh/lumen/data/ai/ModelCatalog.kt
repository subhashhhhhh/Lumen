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
    val title: String,
    val repo: String,
    val commit: String,
    val files: List<ModelFile>,
) {
    val bytes: Long get() = files.sumOf { it.bytes }

    fun url(file: ModelFile) = "https://huggingface.co/$repo/resolve/$commit/${file.name}"

    /** App-private, never backed up: the files are large and re-downloadable. */
    fun dir(context: Context) = File(context.noBackupFilesDir, "models/$id")

    fun installed(context: Context): Boolean =
        files.all { File(dir(context), it.name).length() == it.bytes }
}

object ModelCatalog {
    /**
     * Google's SigLIP 2 B/16 (Apache 2.0), converted for phones: fp32 image encoder, int8 text
     * encoder, packed tokenizer. Chosen in the Search Lab over CLIP, MobileCLIP2 and
     * EmbeddingGemma 2. Conversion: tools/models/.
     */
    val Siglip2 = AiModel(
        id = "siglip2-b16",
        title = "SigLIP 2",
        // TODO: the owner's Hugging Face repo and the commit the files were uploaded in.
        repo = "OWNER/lumen-siglip2-b16",
        commit = "main",
        files = listOf(
            ModelFile("visual.onnx", 371_674_292, "fee10c729875dd203d94f396ac3d664e301d39c977ef6d92e7f467a2a716f0b0"),
            ModelFile("textual_int8.onnx", 283_187_112, "6fdbedc85c54262bcbf380cfb048f03e10cf07237333fe4ca1e204a8f954d608"),
            ModelFile("gemma_bpe.bin", 7_134_060, "e9163029a76e66dadb07afd9373531a24687e25d7a027f65d13a2977331f53c2"),
        ),
    )
}
