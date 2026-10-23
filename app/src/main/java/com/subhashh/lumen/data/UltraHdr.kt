package com.subhashh.lumen.data

import android.content.Context
import android.graphics.ImageDecoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ultra HDR photos: an ordinary image plus a gain map that tells HDR screens how much brighter
 * each part may go. Pixels and recent Samsungs shoot them; the viewer switches the window to HDR
 * for these only, since HDR mode changes how the whole screen is driven.
 */
object UltraHdr {

    /** Formats that can carry a gain map Android understands. */
    private val Candidates = setOf("image/jpeg", "image/heic", "image/heif")

    /** True when [item] has a gain map. A tiny decode is enough to find out. Android 14+ only. */
    suspend fun hasGainmap(context: Context, item: MediaItem): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        if (item.isVideo || item.mimeType !in Candidates) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val source = if (item.uri.scheme == "file") {
                    ImageDecoder.createSource(java.io.File(item.uri.path!!))
                } else {
                    ImageDecoder.createSource(context.contentResolver, item.uri)
                }
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(32)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.hasGainmap()
            }.getOrDefault(false)
        }
    }
}
