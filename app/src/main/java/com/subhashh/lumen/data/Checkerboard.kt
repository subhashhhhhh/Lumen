package com.subhashh.lumen.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * The squares shown behind see-through parts of a picture (#27), as image editors do: a black
 * signature on a transparent PNG was invisible on the dark theme, and its thumbnail (JPEG, which
 * has no transparency) a black square.
 */
object Checkerboard {
    const val Light = 0xFFE6E6E6.toInt()
    const val Dark = 0xFFB8B8B8.toInt()

    /** Images of these types can have see-through pixels. Screenshots never do. */
    private val AlphaTypes = setOf("image/png", "image/webp", "image/gif", "image/heif", "image/heic", "image/avif")

    fun mayBeTransparent(item: MediaItem): Boolean = !item.isVideo && item.mimeType in AlphaTypes && !item.isScreenshot

    /** [bitmap] over squares if any of it is see-through; otherwise [bitmap] itself. */
    fun under(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha() || bitmap.config == Bitmap.Config.HARDWARE) return bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        if (pixels.none { it ushr 24 != 0xFF }) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Light)
        // About 12 squares across the shorter side, whatever the thumbnail's size.
        val cell = (minOf(bitmap.width, bitmap.height) / 12f).coerceAtLeast(4f)
        val paint = Paint().apply { color = Dark }
        var y = 0
        while (y * cell < bitmap.height) {
            var x = y % 2
            while (x * cell < bitmap.width) {
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
                x += 2
            }
            y++
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return out
    }
}
