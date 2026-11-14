package com.subhashh.lumen.data.edit

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Everything the photo editor changes, as numbers: nothing here touches pixels, so the preview and
 * the saved file are drawn from the same description and always match.
 *
 * The photo is flipped, then turned by quarter turns, then straightened. Straightening turns it
 * about its centre and zooms in just enough that no empty corner shows; what is left is the
 * *frame*. [crop] is the part of the frame that is kept.
 */
data class PhotoEdit(
    /** Clockwise quarter turns, 0 to 3. */
    val quarterTurns: Int = 0,
    /** Mirrored left to right, before the turns. */
    val flipped: Boolean = false,
    /** Degrees clockwise, between -[MaxStraighten] and [MaxStraighten]. */
    val straighten: Float = 0f,
    val crop: CropRect = CropRect.Full,
    /** Only the ones that were moved. */
    val adjustments: Map<Adjustment, Float> = emptyMap(),
    val filter: PhotoFilter = PhotoFilter.None,
) {
    val isUnchanged: Boolean get() = this == PhotoEdit()

    fun level(adjustment: Adjustment): Float = adjustments[adjustment] ?: 0f

    fun with(adjustment: Adjustment, value: Float): PhotoEdit {
        val clamped = value.coerceIn(adjustment.min, 1f)
        return copy(adjustments = if (clamped == 0f) adjustments - adjustment else adjustments + (adjustment to clamped))
    }

    /** A quarter turn to the left of what is on screen; the crop turns with it. */
    fun rotatedLeft(): PhotoEdit = copy(
        quarterTurns = (quarterTurns + 3) % 4,
        crop = CropRect(crop.top, 1f - crop.right, crop.bottom, 1f - crop.left),
    )

    /** What is on screen, mirrored left to right. A flip undoes the turns' direction, so they swap. */
    fun flippedHorizontally(): PhotoEdit = copy(
        flipped = !flipped,
        quarterTurns = (4 - quarterTurns) % 4,
        straighten = -straighten,
        crop = CropRect(1f - crop.right, crop.top, 1f - crop.left, crop.bottom),
    )

    /** How much straightening zooms in, for a turned photo [width] by [height]. 1 when level. */
    fun straightenScale(width: Float, height: Float): Float {
        if (straighten == 0f || width <= 0f || height <= 0f) return 1f
        val angle = Math.toRadians(abs(straighten).toDouble())
        return (cos(angle) + max(width / height, height / width) * sin(angle)).toFloat()
    }

    /** The frame's size in the source's pixels, for a source [sourceWidth] by [sourceHeight]. */
    fun frameSize(sourceWidth: Float, sourceHeight: Float): Pair<Float, Float> {
        val turned = quarterTurns % 2 == 1
        val width = if (turned) sourceHeight else sourceWidth
        val height = if (turned) sourceWidth else sourceHeight
        val scale = straightenScale(width, height)
        return width / scale to height / scale
    }

    /** The saved photo's size: the crop at the source's own sharpness, never enlarged. */
    fun outputSize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val (width, height) = frameSize(sourceWidth.toFloat(), sourceHeight.toFloat())
        return max(1, (crop.width * width).roundToInt()) to max(1, (crop.height * height).roundToInt())
    }

    /**
     * The colour change as a 4×5 matrix (Android's layout: red, green, blue and alpha rows, the
     * fifth column added on a 0–255 scale), or null when colours are left alone.
     */
    fun colorMatrix(): FloatArray? {
        if (filter == PhotoFilter.None && Adjustment.entries.none { it.inMatrix && level(it) != 0f }) return null
        var matrix = Identity
        fun then(next: FloatArray) { matrix = concat(next, matrix) }
        level(Adjustment.Exposure).let { if (it != 0f) then(gain(2f.pow(it))) }
        level(Adjustment.Brightness).let { if (it != 0f) then(offset(it * 64f)) }
        level(Adjustment.Contrast).let { if (it != 0f) then(contrast(1f + it * 0.5f)) }
        level(Adjustment.Saturation).let { if (it != 0f) then(saturation(1f + it)) }
        level(Adjustment.Warmth).let { if (it != 0f) then(gain(1f + 0.18f * it, 1f, 1f - 0.18f * it)) }
        level(Adjustment.Tint).let { if (it != 0f) then(gain(1f + 0.06f * it, 1f - 0.12f * it, 1f + 0.06f * it)) }
        filter.matrix?.let(::then)
        return matrix
    }

    companion object {
        const val MaxStraighten = 45f

        private val Identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        internal fun gain(red: Float, green: Float = red, blue: Float = red) = floatArrayOf(
            red, 0f, 0f, 0f, 0f,
            0f, green, 0f, 0f, 0f,
            0f, 0f, blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        internal fun offset(amount: Float) = floatArrayOf(
            1f, 0f, 0f, 0f, amount,
            0f, 1f, 0f, 0f, amount,
            0f, 0f, 1f, 0f, amount,
            0f, 0f, 0f, 1f, 0f,
        )

        /** Stretches away from (or squeezes towards) mid grey. */
        internal fun contrast(scale: Float): FloatArray {
            val shift = 127.5f * (1f - scale)
            return floatArrayOf(
                scale, 0f, 0f, 0f, shift,
                0f, scale, 0f, 0f, shift,
                0f, 0f, scale, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            )
        }

        /** 0 is grey, 1 leaves colours alone, more is stronger. Rec. 709 brightness weights. */
        internal fun saturation(amount: Float): FloatArray {
            val r = 0.2126f * (1f - amount)
            val g = 0.7152f * (1f - amount)
            val b = 0.0722f * (1f - amount)
            return floatArrayOf(
                r + amount, g, b, 0f, 0f,
                r, g + amount, b, 0f, 0f,
                r, g, b + amount, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        }

        /** The matrix that does [first], then [second]. */
        internal fun concat(second: FloatArray, first: FloatArray): FloatArray {
            val out = FloatArray(20)
            for (row in 0 until 4) {
                for (column in 0 until 5) {
                    var sum = 0f
                    for (k in 0 until 4) sum += second[row * 5 + k] * first[k * 5 + column]
                    if (column == 4) sum += second[row * 5 + 4]
                    out[row * 5 + column] = sum
                }
            }
            return out
        }
    }
}

/** The sliders under Adjust. All run -1 to 1 with 0 as "unchanged", except those whose [min] is 0. */
enum class Adjustment(val min: Float = -1f, /** Part of [PhotoEdit.colorMatrix]; the rest are drawn on top. */ val inMatrix: Boolean = true) {
    Exposure, Brightness, Contrast, Saturation, Warmth, Tint,
    Vignette(min = 0f, inMatrix = false),
}

/** One-tap looks. Each is a fixed colour matrix applied after the adjustments. */
enum class PhotoFilter(val matrix: FloatArray?) {
    None(null),
    Vivid(PhotoEdit.concat(PhotoEdit.contrast(1.12f), PhotoEdit.saturation(1.35f))),
    Warm(PhotoEdit.concat(PhotoEdit.gain(1.1f, 1.02f, 0.88f), PhotoEdit.saturation(1.1f))),
    Cool(PhotoEdit.concat(PhotoEdit.gain(0.9f, 1f, 1.1f), PhotoEdit.saturation(1.05f))),
    Fade(PhotoEdit.concat(PhotoEdit.offset(18f), PhotoEdit.concat(PhotoEdit.contrast(0.82f), PhotoEdit.saturation(0.8f)))),
    Mono(PhotoEdit.saturation(0f)),
    Noir(PhotoEdit.concat(PhotoEdit.contrast(1.4f), PhotoEdit.saturation(0f))),
    Sepia(
        PhotoEdit.concat(
            floatArrayOf(
                1.07f, 0f, 0f, 0f, 0f,
                0f, 0.95f, 0f, 0f, 0f,
                0f, 0f, 0.74f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
            PhotoEdit.saturation(0f),
        ),
    ),
}

/** Where a drag on the crop box started. */
enum class CropHandle(val left: Boolean = false, val top: Boolean = false, val right: Boolean = false, val bottom: Boolean = false) {
    TopLeft(left = true, top = true), TopRight(top = true, right = true),
    BottomLeft(left = true, bottom = true), BottomRight(right = true, bottom = true),
    Left(left = true), Top(top = true), Right(right = true), Bottom(bottom = true),
    /** Inside the box: it moves whole. */
    Move;

    val isCorner: Boolean get() = (left || right) && (top || bottom)
}

/** A box inside the frame, each side as a fraction (0 to 1) of the frame's width or height. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /**
     * This box after [handle] was dragged by ([dx], [dy]) fractions of the frame. It never leaves
     * the frame or gets smaller than [minSize]. With [aspect] (width over height, in these
     * fractions) the shape is kept: a corner grows from the opposite corner, a side from the
     * middle of the opposite side.
     */
    fun dragged(handle: CropHandle, dx: Float, dy: Float, aspect: Float? = null, minSize: Float = 0.08f): CropRect {
        if (handle == CropHandle.Move) {
            val x = dx.coerceIn(-left, 1f - right)
            val y = dy.coerceIn(-top, 1f - bottom)
            return CropRect(left + x, top + y, right + x, bottom + y)
        }
        if (aspect == null) {
            return CropRect(
                left = if (handle.left) (left + dx).coerceIn(0f, right - minSize) else left,
                top = if (handle.top) (top + dy).coerceIn(0f, bottom - minSize) else top,
                right = if (handle.right) (right + dx).coerceIn(left + minSize, 1f) else right,
                bottom = if (handle.bottom) (bottom + dy).coerceIn(top + minSize, 1f) else bottom,
            )
        }
        // Smallest allowed box of this shape; never bigger than the frame can hold.
        val minWidth = min(max(minSize, minSize * aspect), min(1f, aspect))
        if (handle.isCorner) {
            val anchorX = if (handle.left) right else left
            val anchorY = if (handle.top) bottom else top
            val roomX = if (handle.left) anchorX else 1f - anchorX
            val roomY = if (handle.top) anchorY else 1f - anchorY
            val wantedWidth = if (handle.left) width - dx else width + dx
            val wantedHeight = if (handle.top) height - dy else height + dy
            // Follows whichever way the finger went further.
            val w = max(wantedWidth, wantedHeight * aspect)
                .coerceAtLeast(minWidth)
                .coerceAtMost(min(roomX, roomY * aspect))
            val h = w / aspect
            return CropRect(
                left = if (handle.left) anchorX - w else anchorX,
                top = if (handle.top) anchorY - h else anchorY,
                right = if (handle.left) anchorX else anchorX + w,
                bottom = if (handle.top) anchorY else anchorY + h,
            )
        }
        return if (handle.left || handle.right) {
            val anchorX = if (handle.left) right else left
            val roomX = if (handle.left) anchorX else 1f - anchorX
            val middle = (top + bottom) / 2f
            val w = (if (handle.left) width - dx else width + dx)
                .coerceAtLeast(minWidth)
                .coerceAtMost(min(roomX, 2f * min(middle, 1f - middle) * aspect))
            val h = w / aspect
            CropRect(if (handle.left) anchorX - w else anchorX, middle - h / 2f, if (handle.left) anchorX else anchorX + w, middle + h / 2f)
        } else {
            val anchorY = if (handle.top) bottom else top
            val roomY = if (handle.top) anchorY else 1f - anchorY
            val middle = (left + right) / 2f
            val h = (if (handle.top) height - dy else height + dy)
                .coerceAtLeast(minWidth / aspect)
                .coerceAtMost(min(roomY, 2f * min(middle, 1f - middle) / aspect))
            val w = h * aspect
            CropRect(middle - w / 2f, if (handle.top) anchorY - h else anchorY, middle + w / 2f, if (handle.top) anchorY else anchorY + h)
        }
    }

    companion object {
        val Full = CropRect(0f, 0f, 1f, 1f)

        /** The biggest box of [aspect] (width over height, in frame fractions), in the middle. */
        fun centered(aspect: Float): CropRect {
            val width = min(1f, aspect)
            val height = width / aspect
            return CropRect(0.5f - width / 2f, 0.5f - height / 2f, 0.5f + width / 2f, 0.5f + height / 2f)
        }
    }
}

/** The shapes offered under Crop. [ratio] is the longer side over the shorter; null is any shape. */
enum class CropShape(val ratio: Float?) {
    Free(null), Original(null), Square(1f), FourThree(4f / 3f), ThreeTwo(3f / 2f), SixteenNine(16f / 9f);

    /**
     * The shape as width over height in frame fractions, for a frame [frameWidth] by [frameHeight]
     * (any unit). Tall frames get the tall version (3:4 for 4:3), like the photo itself.
     */
    fun aspect(frameWidth: Float, frameHeight: Float): Float? = when (this) {
        Free -> null
        Original -> 1f
        else -> {
            val pixels = if (frameHeight > frameWidth) 1f / ratio!! else ratio!!
            pixels * frameHeight / frameWidth
        }
    }
}
