package com.subhashh.lumen.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The mosaic in a wide window (desktop mode, tablets): since 3ad42aa it gets the window's column
 * count, so a 1,100 dp window lays out rows for ~14 columns and a big monitor ~40, far past the
 * phone's 1–8. Rows must still be exactly one grid line each and photos still their own shape.
 * Plain JVM: runs on the build server.
 */
class MosaicWideWindowTest {

    private fun library(random: Random, size: Int): FloatArray = FloatArray(size) {
        when {
            random.nextInt(10) == 0 -> Float.NaN // a date header
            random.nextInt(20) == 0 -> shape(0, 0)
            random.nextInt(15) == 0 -> shape(1 + random.nextInt(20_000), 1 + random.nextInt(300))
            else -> shape(500 + random.nextInt(4000), 500 + random.nextInt(4000))
        }
    }

    /** What LazyVerticalGrid draws: greedy lines, headers alone on theirs. */
    private fun lines(layout: MosaicLayout, shapes: FloatArray): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        var line = ArrayList<Int>()
        var used = 0
        for (i in shapes.indices) {
            if (used + layout.spans[i] > MosaicUnits || shapes[i].isNaN()) {
                if (line.isNotEmpty()) out += line
                line = ArrayList()
                used = 0
            }
            if (shapes[i].isNaN()) { used = MosaicUnits; continue }
            line += i
            used += layout.spans[i]
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    @Test
    fun wideWindowsStillGetWholeRowsOfWholePhotos() {
        for (seed in 0 until 200) {
            val random = Random(seed)
            val shapes = library(random, 50 + random.nextInt(400))
            val columns = 9 + random.nextInt(32) // 9..40
            val layout = mosaicLayout(shapes, columns)
            for (line in lines(layout, shapes)) {
                val heights = line.map { layout.heights[it] }.distinct()
                assertEquals("seed $seed columns $columns: one line, several heights", 1, heights.size)
                val width = line.sumOf { layout.spans[it] }
                assertTrue("seed $seed columns $columns: line $width units wide", width <= MosaicUnits)
                for (i in line) {
                    assertTrue("seed $seed columns $columns: a photo with no width", layout.spans[i] >= 1)
                    // Shape kept within rounding: a unit is 1/120 of the window here, still small.
                    val drawn = layout.spans[i].toFloat() / MosaicUnits
                    assertTrue(
                        "seed $seed columns $columns photo $i: ${layout.spans[i]} units for shape ${shapes[i]}",
                        abs(drawn - shapes[i] * layout.heights[i]) <= 1.5f / MosaicUnits,
                    )
                }
            }
        }
    }

    @Test
    fun theSameColumnsGiveTheSameLayout() {
        // A recomposition (or a second window of the same width) mustn't reflow the grid.
        val shapes = library(Random(1), 300)
        val a = mosaicLayout(shapes, 14)
        val b = mosaicLayout(shapes.copyOf(), 14)
        assertTrue(a.spans.contentEquals(b.spans))
        assertTrue(a.heights.contentEquals(b.heights))
    }
}
