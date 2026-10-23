package com.subhashh.lumen.data.vault

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource as CoilDataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException

/** A vault item for Coil: photos decrypt in memory; videos give a frame, never their whole file. */
data class VaultImage(val item: MediaItem)

class VaultFetcher(private val data: VaultImage, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val item = data.item
        if (item.isVideo) {
            val width = options.size.width.pxOrElse { 1024 }
            val height = options.size.height.pxOrElse { 1024 }
            val frame = frame(item, width, height) ?: throw IOException("No frame for ${item.uri}")
            return@withContext ImageFetchResult(frame.asImage(), isSampled = true, dataSource = CoilDataSource.DISK)
        }
        val buffer = Buffer()
        if (!Vault.decrypt(item, buffer.outputStream())) throw IOException("Vault is locked")
        SourceFetchResult(
            source = ImageSource(buffer, options.fileSystem),
            mimeType = item.mimeType,
            dataSource = CoilDataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<VaultImage> {
        override fun create(data: VaultImage, options: Options, imageLoader: ImageLoader): Fetcher = VaultFetcher(data, options)
    }

    /** Coil sees vault Uris too (the viewer asks for item.uri); this maps them to [VaultImage]. */
    class UriFactory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != Vault.Scheme) return null
            val item = VaultIndex.find(data.toString()) ?: return null
            return VaultFetcher(VaultImage(item), options)
        }
    }

    companion object {
        /** The first frame of a vault video, read straight from the encrypted file. */
        fun frame(item: MediaItem, width: Int, height: Int): Bitmap? {
            val reader = Vault.reader(item) ?: return null
            return reader.use {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(VaultMediaDataSource(reader))
                    val box = maxOf(width, height)
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, box, box)
                }
            }
        }
    }
}

/**
 * Vault items by Uri, so code that only has a Uri (Coil's own Uri path) can find the item.
 * Filled whenever the vault is listed.
 */
object VaultIndex {
    private val byUri = HashMap<String, MediaItem>()

    fun update(items: List<MediaItem>) = synchronized(byUri) {
        byUri.clear()
        items.forEach { byUri[it.uri.toString()] = it }
    }

    fun find(uri: String): MediaItem? = synchronized(byUri) { byUri[uri] }
}

/** Platform media APIs (frame extraction) reading a vault file through its decrypting reader. */
class VaultMediaDataSource(private val reader: VaultCrypto.Reader) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        if (size == 0) 0 else reader.read(position, buffer, offset, size)

    override fun getSize(): Long = reader.length

    override fun close() = Unit
}

/**
 * ExoPlayer reading vault videos: decrypts the chunks it asks for as it plays and seeks, so a
 * plain copy never touches storage. Everything else goes to the default data source.
 */
@UnstableApi
class VaultDataSource private constructor() : BaseDataSource(false) {
    private var reader: VaultCrypto.Reader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        val item = VaultIndex.find(dataSpec.uri.toString()) ?: throw IOException("Not in the vault: ${dataSpec.uri}")
        transferInitializing(dataSpec)
        val r = Vault.reader(item) ?: throw IOException("Vault is locked")
        reader = r
        uri = dataSpec.uri
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else r.length - position
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: return C.RESULT_END_OF_INPUT
        val n = r.read(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (n < 0) return C.RESULT_END_OF_INPUT
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        reader?.close()
        reader = null
        transferEnded()
    }

    /** Vault Uris to [VaultDataSource], everything else to the usual sources. */
    class Factory(context: Context) : DataSource.Factory {
        private val default = DefaultDataSource.Factory(context)
        override fun createDataSource(): DataSource = Routing(default.createDataSource())
    }

    private class Routing(private val default: DataSource) : DataSource by default {
        private var active: DataSource = default

        override fun open(dataSpec: DataSpec): Long {
            active = if (dataSpec.uri.scheme == Vault.Scheme) VaultDataSource() else default
            return active.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)
        override fun getUri(): Uri? = active.uri
        override fun close() = active.close()
    }
}
