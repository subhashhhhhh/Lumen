package com.subhashh.lumen.ui.images

/**
 * Shared Coil memory-cache keys. The same photo is drawn by the grid, the flying transition
 * photo and the viewer; sharing keys lets each pick up what another already decoded instead of
 * starting over (the source of a blurry-then-sharp flash).
 */
object ImageKeys {
    /** Grid-cell sized, from the system thumbnail cache. */
    fun thumb(id: Long) = "thumb:$id"

    /** Screen-sized decode of the original: sharp at 1×, cheap enough to decode during the open flight. */
    fun screen(id: Long) = "screen:$id"

    /** Album cover sized; larger than grid cells, so kept separate from [thumb]. */
    fun cover(id: Long) = "cover:$id"
}
