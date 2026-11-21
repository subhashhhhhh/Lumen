package com.subhashh.lumen.ui.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.subhashh.lumen.R
import kotlin.math.abs
import kotlin.math.ln

/** The Library's grid (≡ → Grid). */
enum class GridStyle(@StringRes val label: Int) {
    /** Square crops. */
    Square(R.string.main_view_square),
    /** Whole photos letterboxed in square cells. */
    AspectRatio(R.string.main_view_aspect_ratio),
    /** Rows of whole photos at their own shapes, each row as wide as the screen. */
    Mosaic(R.string.main_view_mosaic),
}

/**
 * Mosaic rows for a grid's entries. The grid is [MosaicUnits] columns wide; each photo spans as
 * many as its shape needs in its row ([spans]), and every photo in a row has the row's height
 * ([heights], as a fraction of the grid's width). Headers span the whole width.
 *
 * Justified rows, as in Google Photos: a row takes photos until it's about as tall as a square
 * cell would be at [columns] across, then stretches to fill the width. Order stays exactly the
 * grid's, so drag-select, the fast scroller and headers work as in the square grid. A section's
 * last row isn't stretched: it keeps the usual height and leaves the rest of the width empty.
 */
@Immutable
class MosaicLayout(val spans: IntArray, val heights: FloatArray)

/** Columns the mosaic grid is cut into: fine enough that rounding a photo's width is invisible. */
const val MosaicUnits = 120

/** Shapes beyond these are cropped to them: a panorama doesn't fill a row alone, nor a sliver of a screenshot vanish. */
private const val Narrowest = 0.5f
private const val Widest = 2.5f

fun mosaicLayout(entries: List<GridEntry>, columns: Int): MosaicLayout =
    mosaicLayout(FloatArray(entries.size) { i -> (entries[i] as? GridEntry.Cell)?.let { aspect(it) } ?: Float.NaN }, columns)

/** The same over each entry's shape (width / height), NaN for a header. */
internal fun mosaicLayout(shapes: FloatArray, columns: Int): MosaicLayout {
    val spans = IntArray(shapes.size) { MosaicUnits }
    val heights = FloatArray(shapes.size)
    // A row aims at the square grid's cell height: its photos' shapes add up to [columns].
    val target = columns.toFloat()
    val row = ArrayList<Int>()
    var sum = 0f

    fun place(last: Boolean) {
        if (row.isEmpty()) return
        // Rows are stretched to fill the width; a section's last, if short, keeps the target height.
        val full = !last || sum >= target
        val width = if (full) sum else target
        val height = 1f / width
        val raw = FloatArray(row.size) { shapes[row[it]] / width * MosaicUnits }
        val units = IntArray(row.size) { maxOf(1, raw[it].toInt()) }
        var left = (if (full) MosaicUnits else minOf(MosaicUnits, raw.sum().toInt())) - units.sum()
        // Largest remainders get the units left over.
        for (k in row.indices.sortedByDescending { raw[it] - units[it] }) {
            if (left <= 0) break
            units[k]++
            left--
        }
        // Too many units handed out (each photo takes at least one): take them from the widest.
        while (left < 0) {
            val k = units.indices.maxBy { units[it] }
            units[k]--
            left++
        }
        row.forEachIndexed { k, i ->
            spans[i] = units[k]
            heights[i] = height
        }
        row.clear()
        sum = 0f
    }

    for (i in shapes.indices) {
        if (shapes[i].isNaN()) {
            place(last = true)
            continue
        }
        val a = shapes[i]
        // Close the row before this photo when that leaves it nearer the target height than taking it.
        if (row.isNotEmpty() && abs(ln(sum / target)) < abs(ln((sum + a) / target)) && sum + a > target) {
            place(last = false)
        }
        row += i
        sum += a
        if (row.size >= MosaicUnits) place(last = false)
    }
    place(last = true)
    return MosaicLayout(spans, heights)
}

private fun aspect(cell: GridEntry.Cell): Float = shape(cell.item.width, cell.item.height)

/** A photo's shape for the mosaic: width / height within [Narrowest]..[Widest], square when unknown. */
internal fun shape(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 1f else (width.toFloat() / height).coerceIn(Narrowest, Widest)
