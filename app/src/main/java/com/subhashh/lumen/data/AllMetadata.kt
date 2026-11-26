package com.subhashh.lumen.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.subhashh.lumen.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Every tag in a file, for Details → All Metadata: EXIF for photos; for videos, the container's
 * metadata and each stream's format. Names as the standards spell them.
 */
object AllMetadata {

    /** ExifInterface's tag names, read once from its TAG_ constants. */
    private val ExifTags: List<String> by lazy {
        ExifInterface::class.java.fields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull { runCatching { it.get(null) as String }.getOrNull() }
            // Big binary blocks, not readable as text.
            .filterNot { it in setOf(ExifInterface.TAG_XMP, ExifInterface.TAG_MAKER_NOTE, "ThumbnailImage") }
            .distinct()
            .sorted()
    }

    private val RetrieverKeys: List<Pair<String, Int>> by lazy {
        MediaMetadataRetriever::class.java.fields
            .filter { it.name.startsWith("METADATA_KEY_") && it.type == Int::class.javaPrimitiveType }
            .mapNotNull { f -> runCatching { f.name.removePrefix("METADATA_KEY_") to f.getInt(null) }.getOrNull() }
            .sortedBy { it.first }
    }

    suspend fun read(context: Context, item: MediaItem, canReadLocation: Boolean): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val uri = if (canReadLocation && ExternalMedia.isMediaStore(item.uri)) MediaStore.setRequireOriginal(item.uri) else item.uri
        runCatching {
            if (item.isVideo) {
                buildList {
                    MediaMetadataRetriever().use { r ->
                        r.setDataSource(context, uri)
                        for ((name, key) in RetrieverKeys) r.extractMetadata(key)?.takeIf { it.isNotBlank() }?.let { add(name to it) }
                    }
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(context, uri, null)
                        for (i in 0 until extractor.trackCount) {
                            // "{mime=video/hevc, width=1920, …}": one row per stream.
                            add(context.getString(R.string.data_metadata_track, i + 1) to extractor.getTrackFormat(i).toString().removePrefix("{").removeSuffix("}"))
                        }
                    } finally {
                        extractor.release()
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    ExifTags.mapNotNull { tag -> exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { tag to it.take(300) } }
                }.orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    /** Types ExifInterface can save: field edits are offered for these (#9). Not encrypted Hidden files. */
    private val WritableTypes = setOf("image/jpeg", "image/png", "image/webp")

    fun editable(item: MediaItem): Boolean =
        !item.isVideo && item.mimeType in WritableTypes && !com.subhashh.lumen.data.vault.Vault.isVault(item)

    /**
     * Sets EXIF [tag] to [value], or removes it when [value] is null, in the file itself; then
     * MediaStore reads the file again. The caller has write access. False when it couldn't.
     */
    suspend fun write(context: Context, item: MediaItem, tag: String, value: String?): Boolean = withContext(Dispatchers.IO) {
        val saved = runCatching {
            context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(tag, value)
                exif.saveAttributes()
                true
            } ?: false
        }.onFailure { android.util.Log.w("LumenMetadata", "Couldn't write $tag to ${item.name}", it) }.getOrDefault(false)
        if (saved) {
            MediaFiles.filePath(context, item)?.let { path ->
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> if (cont.isActive) cont.resume(Unit) }
                }
            }
        }
        saved
    }
}
