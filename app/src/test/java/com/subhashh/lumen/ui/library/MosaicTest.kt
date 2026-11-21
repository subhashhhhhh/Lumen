package com.subhashh.lumen.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Mosaic rows: whole photos at their own shapes, each full row exactly as wide as the grid. */
class MosaicTest {
    private val header = Float.NaN
    private fun shapes(vararg sizes: Pair<Int, Int>) = FloatArray(sizes.size) { shape(sizes[it].first, sizes[it].second) }

    /** Rows as lists of indexes: a row ends where the next photo wouldn't fit or a header comes. */
    private fun rows(layout: MosaicLayout, shapes: FloatArray): List<List<Int>> {
        val rows = ArrayList<List<Int>>()
        var row = ArrayList<Int>()
        var used = 0
        for (i in shapes.indices) {
            if (shapes[i].isNaN() || used + layout.spans[i] > MosaicUnits) {
                if (row.isNotEmpty()) rows += row
                row = ArrayList()
                used = 0
            }
            if (shapes[i].isNaN()) continue
            row += i
            used += layout.spans[i]
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }

    @Test
    fun fullRowsFillTheWidthAndShareOneHeight() {
        val mix = listOf(4000 to 3000, 3000 to 4000, 1920 to 1080, 1080 to 1920, 1000 to 1000)
        val shapes = shapes(*Array(60) { mix[it % mix.size] })
        val layout = mosaicLayout(shapes, columns = 4)
        val rows = rows(layout, shapes)
        for (row in rows.dropLast(1)) {
            assertEquals(MosaicUnits, row.sumOf { layout.spans[it] })
            assertEquals(1, row.map { layout.heights[it] }.distinct().size)
        }
        // About as tall as the square grid's cells: a quarter of the width at 4 across.
        val heights = rows.dropLast(1).map { layout.heights[it.first()] }
        assertTrue("row heights $heights", heights.all { it in 0.18f..0.33f })
    }

    @Test
    fun aPortraitIsNarrowerThanALandscapeInTheSameRow() {
        val shapes = shapes(3000 to 4000, 4000 to 3000, 4000 to 3000, 3000 to 4000, 4000 to 3000)
        val layout = mosaicLayout(shapes, columns = 3)
        // Widths follow the shapes: 3:4 against 4:3 is 0.5625.
        val ratio = layout.spans[0].toFloat() / layout.spans[1]
        assertTrue("ratio $ratio", abs(ratio - 0.5625f) < 0.08f)
    }

    @Test
    fun aHeaderEndsTheRowAndASectionsShortLastRowIsntStretched() {
        val landscape = shape(4000, 3000)
        val shapes = floatArrayOf(header, landscape, header, landscape, landscape, landscape, landscape, landscape)
        val layout = mosaicLayout(shapes, columns = 4)
        assertEquals(MosaicUnits, layout.spans[0])
        // Alone in its section: the usual height (a quarter of the width), at its own shape.
        assertEquals(0.25f, layout.heights[1], 0.001f)
        assertTrue(abs((MosaicUnits * landscape / 4).toInt() - layout.spans[1]) <= 1)
        assertEquals(MosaicUnits, layout.spans[2])
    }

    @Test
    fun extremeShapesAreClampedAndUnknownSizesAreSquare() {
        assertEquals(2.5f, shape(10000, 1000))
        assertEquals(0.5f, shape(1000, 10000))
        assertEquals(1f, shape(0, 0))
        val shapes = shapes(10000 to 1000, 0 to 0, 1000 to 10000)
        val layout = mosaicLayout(shapes, columns = 2)
        assertEquals(3, rows(layout, shapes).flatten().size)
        assertTrue(layout.spans.all { it in 1..MosaicUnits })
    }
}
