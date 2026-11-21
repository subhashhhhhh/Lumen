package com.subhashh.lumen.ui.library

import android.content.Context
import com.subhashh.lumen.data.LumenPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.math.abs
import kotlin.random.Random

/**
 * The mosaic as the grid actually lays it out. LazyVerticalGrid packs items greedily: an item
 * goes on the current line if its span fits, else it starts a new one; a header takes a whole
 * line. Every line drawn must be one of the mosaic's rows: one height, and the full width unless
 * it is a section's last.
 */
@RunWith(RobolectricTestRunner::class)
class MosaicPropertiesTest {

    private val header = Float.NaN

    /** The lines LazyVerticalGrid makes from these spans: indexes of the photos on each (headers left out). */
    private fun gridLines(layout: MosaicLayout, shapes: FloatArray): List<List<Int>> {
        val lines = ArrayList<List<Int>>()
        var line = ArrayList<Int>()
        var used = 0
        for (i in shapes.indices) {
            val span = layout.spans[i]
            if (used + span > MosaicUnits) {
                if (line.isNotEmpty()) lines += line
                line = ArrayList()
                used = 0
            }
            used += span
            if (shapes[i].isNaN()) {
                // A header fills its line on its own.
                if (line.isNotEmpty()) lines += line
                line = ArrayList()
                used = MosaicUnits
            } else {
                line += i
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /** Photos at random shapes (including clamped extremes and unknown sizes), with headers now and then. */
    private fun library(random: Random, size: Int, headers: Boolean): FloatArray = FloatArray(size) {
        when {
            headers && random.nextInt(9) == 0 -> header
            random.nextInt(20) == 0 -> shape(0, 0)
            random.nextInt(15) == 0 -> shape(1 + random.nextInt(20_000), 1 + random.nextInt(300))
            else -> shape(500 + random.nextInt(4000), 500 + random.nextInt(4000))
        }
    }

    /** The photo just before the next header (or the end), i.e. its section's last. */
    private fun sectionEnds(shapes: FloatArray): Set<Int> {
        val ends = HashSet<Int>()
        var last = -1
        for (i in shapes.indices) {
            if (shapes[i].isNaN()) {
                if (last >= 0) ends += last
                last = -1
            } else {
                last = i
            }
        }
        if (last >= 0) ends += last
        return ends
    }

    @Test
    fun everyLineTheGridDrawsIsOneRowAtOneHeight() {
        for (seed in 0 until 300) {
            val random = Random(seed)
            val shapes = library(random, 1 + random.nextInt(150), headers = true)
            val columns = 1 + random.nextInt(8)
            val layout = mosaicLayout(shapes, columns)
            val ends = sectionEnds(shapes)
            for (line in gridLines(layout, shapes)) {
                val heights = line.map { layout.heights[it] }.distinct()
                assertEquals("seed $seed columns $columns: one line, several heights $heights", 1, heights.size)
                val width = line.sumOf { layout.spans[it] }
                assertTrue("seed $seed: line wider than the grid ($width)", width <= MosaicUnits)
                // Only a section's last line may leave room, and it then keeps the usual height.
                if (width < MosaicUnits) {
                    assertTrue("seed $seed columns $columns: a short line ($width) in the middle of a section", line.last() in ends)
                    assertEquals("seed $seed: a short line is a square cell's height", 1f / columns, heights.single(), 1e-6f)
                }
            }
        }
    }

    @Test
    fun eachPhotoIsShownAtItsOwnShape() {
        // The cell is span/MosaicUnits of the width across and height × width tall. Rounding to
        // whole units may cost at most a unit or so; anything more crops the photo visibly.
        for (seed in 0 until 300) {
            val random = Random(seed)
            val shapes = library(random, 1 + random.nextInt(150), headers = true)
            val columns = 1 + random.nextInt(8)
            val layout = mosaicLayout(shapes, columns)
            for (i in shapes.indices) {
                if (shapes[i].isNaN()) continue
                val width = layout.spans[i].toFloat() / MosaicUnits
                val wanted = shapes[i] * layout.heights[i]
                assertTrue(
                    "seed $seed columns $columns photo $i: ${layout.spans[i]} units for shape ${shapes[i]} at height ${layout.heights[i]}",
                    abs(width - wanted) <= 1.5f / MosaicUnits,
                )
            }
        }
    }

    @Test
    fun rowsStayNearTheSquareGridsHeight() {
        // Never under half nor over twice a square cell's height, whatever the shapes. (One
        // exception: at 1 across, a panorama alone in its row is as flat as its own shape.)
        for (seed in 0 until 300) {
            val random = Random(seed)
            val shapes = library(random, 1 + random.nextInt(150), headers = false)
            val columns = 1 + random.nextInt(8)
            val layout = mosaicLayout(shapes, columns)
            for (i in shapes.indices) {
                val relative = layout.heights[i] * columns
                assertTrue("seed $seed columns $columns photo $i: $relative of a square cell", relative in minOf(0.5f, columns / 2.5f) - 1e-6f..2.0f)
            }
        }
    }

    @Test
    fun headersTakeTheWholeWidth() {
        val shapes = floatArrayOf(header, 1f, 1.5f, header, header, 0.75f)
        val layout = mosaicLayout(shapes, columns = 3)
        assertEquals(MosaicUnits, layout.spans[0])
        assertEquals(MosaicUnits, layout.spans[3])
        assertEquals(MosaicUnits, layout.spans[4])
    }

    @Test
    fun newPhotosAtTheEndOnlyReflowTheLastRow() {
        // Oldest first, a new photo arrives at the bottom: everything above must stay where it was,
        // or the grid jumps under the person looking at it.
        for (seed in 0 until 200) {
            val random = Random(seed)
            val shapes = library(random, 5 + random.nextInt(120), headers = true)
            val extra = library(random, 1 + random.nextInt(10), headers = false)
            val columns = 2 + random.nextInt(6)
            val before = mosaicLayout(shapes, columns)
            val after = mosaicLayout(shapes + extra, columns)
            val lines = gridLines(before, shapes)
            val settled = lines.dropLast(1).flatten()
            for (i in settled) {
                assertEquals("seed $seed photo $i: span moved", before.spans[i], after.spans[i])
                assertEquals("seed $seed photo $i: height moved", before.heights[i], after.heights[i])
            }
        }
    }

    @Test
    fun aManyColumnGridOfNarrowPhotosStillFillsEachRowExactly() {
        // 8 across, all 1:2 portraits: about 16 to a row, each about 7.5 units, so rounding adds up.
        val shapes = FloatArray(200) { shape(1000, 2000) }
        val layout = mosaicLayout(shapes, columns = 8)
        val lines = gridLines(layout, shapes)
        for (line in lines.dropLast(1)) assertEquals(MosaicUnits, line.sumOf { layout.spans[it] })
        assertEquals(200, lines.flatten().size)
        assertTrue(layout.spans.all { it >= 1 })
    }

    @Test
    fun oneColumnGivesEachPhotoNearlyTheWholeWidth() {
        val shapes = floatArrayOf(shape(3000, 4000), shape(4000, 3000), shape(1080, 1920), shape(1920, 1080))
        val layout = mosaicLayout(shapes, columns = 1)
        // One photo to a row: its shape makes up the whole row on its own.
        for (line in gridLines(layout, shapes)) assertEquals(1, line.size)
    }

    // --- The setting ---

    private val context: Context = RuntimeEnvironment.getApplication()
    private val raw = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    @Test
    fun theOldLetterboxSwitchCarriesOver() {
        raw.edit().clear().putBoolean("aspect_ratio_grid", true).commit()
        assertEquals(GridStyle.AspectRatio, LumenPrefs(context).gridStyle)
        raw.edit().clear().putBoolean("aspect_ratio_grid", false).commit()
        assertEquals(GridStyle.Square, LumenPrefs(context).gridStyle)
        raw.edit().clear().commit()
        assertEquals(GridStyle.Square, LumenPrefs(context).gridStyle)
    }

    @Test
    fun aChosenStyleWinsOverTheOldSwitchAndSurvivesARestart() {
        raw.edit().clear().putBoolean("aspect_ratio_grid", true).commit()
        LumenPrefs(context).gridStyle = GridStyle.Mosaic
        assertEquals(GridStyle.Mosaic, LumenPrefs(context).gridStyle)
        LumenPrefs(context).gridStyle = GridStyle.Square
        assertEquals(GridStyle.Square, LumenPrefs(context).gridStyle)
    }

    @Test
    fun anUnknownStyleFromANewerVersionFallsBackInsteadOfCrashing() {
        // A backup made by a later Lumen may name a style this one doesn't have.
        raw.edit().clear().putString("grid_style", "Hexagons").putBoolean("aspect_ratio_grid", true).commit()
        assertEquals(GridStyle.AspectRatio, LumenPrefs(context).gridStyle)
    }
}
