package com.subhashh.lumen.ui.images

import coil3.ImageLoader
import coil3.memory.MemoryCache

/**
 * Shared Coil memory-cache keys. The same photo is drawn by the grid, the flying transition
 * photo and the viewer; sharing keys lets each pick up what another already decoded instead of
 * starting over (the source of a blurry-then-sharp flash).
 *
 * Explicit keys bypass Coil's size check, so every key must encode the size it was decoded at —
 * a size-less thumbnail key handed small bitmaps to big cells, blurring 1–3 column grids.
 */
object ImageKeys {
    /** Thumbnail decode sizes, px (square cells). A cell uses the smallest bucket that covers it. */
    val ThumbBuckets = intArrayOf(192, 320, 512, 768, 1280)

    /** Up to this size, Android's thumbnail cache is sharp enough (and much faster than decoding). */
    const val SystemThumbnailMaxPx = 320

    fun thumbBucket(cellPx: Int): Int = ThumbBuckets.firstOrNull { it >= cellPx } ?: ThumbBuckets.last()

    fun thumb(id: Long, bucket: Int) = "thumb:$id:$bucket"

    /** Screen-sized decode of the original: sharp at 1×, cheap enough to decode during the open flight. */
    fun screen(id: Long) = "screen:$id"

    /** Album cover sized; larger than grid cells, so kept separate. */
    fun cover(id: Long) = "cover:$id"

    /** Full-width Years/Months card. */
    fun card(id: Long, heightPx: Int) = "card:$id:$heightPx"
}

/**
 * The sharpest version of [id] already in memory — the screen-size decode, else the largest
 * thumbnail — to stand in while a bigger image loads. Large photos are shown via tiled decoding
 * and the placeholder stays up until the tiles arrive, so a small one there reads as a blurry flash.
 */
fun bestPlaceholderKey(loader: ImageLoader, id: Long): String? {
    val cache = loader.memoryCache ?: return null
    val candidates = sequenceOf(ImageKeys.screen(id)) +
        ImageKeys.ThumbBuckets.reversed().asSequence().map { ImageKeys.thumb(id, it) }
    return candidates.firstOrNull { cache[MemoryCache.Key(it)] != null }
}
