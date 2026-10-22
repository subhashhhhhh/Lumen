package com.subhashh.lumen.data

import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coil model for a grid-sized preview served from Android's own thumbnail cache. */
data class MediaThumbnail(val uri: Uri)

/**
 * Uses ContentResolver.loadThumbnail, which MediaStore caches on disk. Far faster than decoding
 * originals, and for videos it avoids extracting a frame with MediaMetadataRetriever every time.
 */
class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val width = options.size.width.pxOrElse { DefaultSize }
        val height = options.size.height.pxOrElse { DefaultSize }
        val bitmap = options.context.contentResolver.loadThumbnail(data.uri, Size(width, height), null)
        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options)
    }

    private companion object {
        const val DefaultSize = 256
    }
}
