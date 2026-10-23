package com.subhashh.lumen.ui.images

import coil3.ImageLoader
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaItem
import coil3.memory.MemoryCache

/** Identifies one version of a file: a rotate or edit changes [MediaItem.modified], and with it every key. */
val MediaItem.version: String get() = "$id@$modified"

/**
 * Requests without an explicit memory key (the zoomable viewer, video frames) still need the
 * version, or they'd serve a cached decode of the file from before it changed.
 */
fun ImageRequest.Builder.versioned(item: MediaItem): ImageRequest.Builder = this
    .memoryCacheKeyExtra("version", item.modified.toString())
    .diskCacheKey("${item.uri}#${item.modified}")

/**
 * Shared Coil memory-cache keys. The same photo is drawn by the grid, the flying transition
 * photo and the viewer; sharing keys lets each pick up what another already decoded instead of
 * starting over (the source of a blurry-then-sharp flash).
 *
 * Keys are per file version (see [version]) so a changed file never shows a stale image.
 * Explicit keys bypass Coil's size check, so every key must encode the size it was decoded at —
 * a size-less thumbnail key handed small bitmaps to big cells, blurring 1–3 column grids.
 */
object ImageKeys {
    /** Thumbnail decode sizes, px (square cells). A cell uses the smallest bucket that covers it. */
    val ThumbBuckets = intArrayOf(192, 320, 512, 768, 1280)

    /** Up to this size, Android's thumbnail cache is sharp enough (and much faster than decoding). */
    const val SystemThumbnailMaxPx = 320

    fun thumbBucket(cellPx: Int): Int = ThumbBuckets.firstOrNull { it >= cellPx } ?: ThumbBuckets.last()

    fun thumb(item: MediaItem, bucket: Int) = "thumb:${item.version}:$bucket"

    /** Screen-sized decode of the original: sharp at 1×, cheap enough to decode during the open flight. */
    fun screen(item: MediaItem) = "screen:${item.version}"

    /** Album cover sized; larger than grid cells, so kept separate. */
    fun cover(item: MediaItem) = "cover:${item.version}"

    /** Full-width Years/Months card. */
    fun card(item: MediaItem, heightPx: Int) = "card:${item.version}:$heightPx"
}

/**
 * The sharpest version of [id] already in memory — the screen-size decode, else the largest
 * thumbnail — to stand in while a bigger image loads. Large photos are shown via tiled decoding
 * and the placeholder stays up until the tiles arrive, so a small one there reads as a blurry flash.
 */
fun bestPlaceholderKey(loader: ImageLoader, item: MediaItem): String? {
    val cache = loader.memoryCache ?: return null
    val candidates = sequenceOf(ImageKeys.screen(item)) +
        ImageKeys.ThumbBuckets.reversed().asSequence().map { ImageKeys.thumb(item, it) }
    return candidates.firstOrNull { cache[MemoryCache.Key(it)] != null }
}
