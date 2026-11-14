package com.subhashh.lumen.data.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditTest {

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals("left", expected.left, actual.left, 1e-4f)
        assertEquals("top", expected.top, actual.top, 1e-4f)
        assertEquals("right", expected.right, actual.right, 1e-4f)
        assertEquals("bottom", expected.bottom, actual.bottom, 1e-4f)
    }

    private fun assertInside(rect: CropRect) {
        assertTrue("$rect leaves the frame", rect.left >= -1e-4f && rect.top >= -1e-4f && rect.right <= 1.0001f && rect.bottom <= 1.0001f)
        assertTrue("$rect is inside out", rect.width > 0f && rect.height > 0f)
    }

    @Test
    fun untouchedEditChangesNothing() {
        val edit = PhotoEdit()
        assertTrue(edit.isUnchanged)
        assertNull(edit.colorMatrix())
        assertEquals(4000 to 3000, edit.outputSize(4000, 3000))
    }

    @Test
    fun fourLeftTurnsComeBackToTheStart() {
        val start = PhotoEdit(crop = CropRect(0.1f, 0.2f, 0.6f, 0.9f), straighten = 5f)
        var edit = start
        repeat(4) { edit = edit.rotatedLeft() }
        assertEquals(start.quarterTurns, edit.quarterTurns)
        assertRect(start.crop, edit.crop)
    }

    @Test
    fun turningLeftCarriesTheCropWithIt() {
        // A box in the top-left corner ends up bottom-left after a turn to the left.
        val edit = PhotoEdit(crop = CropRect(0f, 0f, 0.25f, 0.5f)).rotatedLeft()
        assertEquals(3, edit.quarterTurns)
        assertRect(CropRect(0f, 0.75f, 0.5f, 1f), edit.crop)
    }

    @Test
    fun flippingTwiceComesBackToTheStart() {
        val start = PhotoEdit(quarterTurns = 1, straighten = 7f, crop = CropRect(0.1f, 0.2f, 0.6f, 0.9f))
        val once = start.flippedHorizontally()
        assertEquals(3, once.quarterTurns)
        assertEquals(-7f, once.straighten, 0f)
        assertRect(CropRect(0.4f, 0.2f, 0.9f, 0.9f), once.crop)
        val twice = once.flippedHorizontally()
        assertEquals(start.copy(crop = twice.crop), twice)
        assertRect(start.crop, twice.crop)
    }

    @Test
    fun aQuarterTurnSwapsTheSavedSize() {
        assertEquals(3000 to 4000, PhotoEdit(quarterTurns = 1).outputSize(4000, 3000))
        assertEquals(1500 to 2000, PhotoEdit(quarterTurns = 3, crop = CropRect(0f, 0f, 0.5f, 0.5f)).outputSize(4000, 3000))
    }

    @Test
    fun straighteningZoomsInJustEnoughToHideTheCorners() {
        val edit = PhotoEdit(straighten = 10f)
        val scale = edit.straightenScale(4000f, 3000f)
        assertTrue(scale > 1f)
        // Every corner of the frame, turned back by the angle, must land inside the photo.
        val (frameWidth, frameHeight) = edit.frameSize(4000f, 3000f)
        val angle = Math.toRadians(-10.0)
        for ((x, y) in listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)) {
            val fx = x * frameWidth / 2.0
            val fy = y * frameHeight / 2.0
            val px = fx * Math.cos(angle) - fy * Math.sin(angle)
            val py = fx * Math.sin(angle) + fy * Math.cos(angle)
            assertTrue("corner outside: $px", Math.abs(px) <= 2000.01)
            assertTrue("corner outside: $py", Math.abs(py) <= 1500.01)
        }
        // And it's the same whichever way it leans.
        assertEquals(scale, PhotoEdit(straighten = -10f).straightenScale(4000f, 3000f), 1e-6f)
    }

    @Test
    fun aFreeCornerDragStopsAtTheFrameAndTheMinimumSize() {
        val box = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertRect(CropRect(0f, 0f, 0.8f, 0.8f), box.dragged(CropHandle.TopLeft, -5f, -5f))
        assertRect(CropRect(0.72f, 0.72f, 0.8f, 0.8f), box.dragged(CropHandle.TopLeft, 5f, 5f))
        assertRect(CropRect(0.2f, 0.2f, 0.9f, 0.8f), box.dragged(CropHandle.Right, 0.1f, 0.3f))
    }

    @Test
    fun movingTheBoxKeepsItsSizeAndStaysInside() {
        val moved = CropRect(0.2f, 0.2f, 0.8f, 0.8f).dragged(CropHandle.Move, 0.5f, -0.5f)
        assertRect(CropRect(0.4f, 0f, 1f, 0.6f), moved)
    }

    @Test
    fun aLockedShapeSurvivesEveryDrag() {
        val aspect = 0.75f
        val start = CropRect.centered(aspect)
        for (handle in CropHandle.entries) {
            for ((dx, dy) in listOf(0.3f to 0.1f, -0.3f to -0.4f, 2f to 2f, -2f to -2f, 0.05f to -0.6f)) {
                val box = start.dragged(handle, dx, dy, aspect)
                assertInside(box)
                assertEquals("$handle by $dx,$dy", aspect, box.width / box.height, 1e-3f)
            }
        }
    }

    @Test
    fun aLockedCornerGrowsFromTheOppositeCorner() {
        val box = CropRect(0.25f, 0.25f, 0.75f, 0.75f).dragged(CropHandle.BottomRight, 0.1f, 0f, aspect = 1f)
        assertRect(CropRect(0.25f, 0.25f, 0.85f, 0.85f), box)
    }

    @Test
    fun shapesFollowTheFramesOrientation() {
        // A wide frame: 16:9 is as wide as the frame and shorter than it.
        val wide = CropRect.centered(CropShape.SixteenNine.aspect(4000f, 3000f)!!)
        assertEquals(16f / 9f, wide.width * 4000f / (wide.height * 3000f), 1e-3f)
        assertEquals(1f, wide.width, 1e-4f)
        // A tall frame gets 9:16.
        val tall = CropRect.centered(CropShape.SixteenNine.aspect(3000f, 4000f)!!)
        assertEquals(9f / 16f, tall.width * 3000f / (tall.height * 4000f), 1e-3f)
        assertEquals(1f, tall.height, 1e-4f)
        assertInside(wide)
        assertInside(tall)
        assertRect(CropRect.Full, CropRect.centered(CropShape.Original.aspect(4000f, 3000f)!!))
        assertNull(CropShape.Free.aspect(4000f, 3000f))
    }

    private fun apply(matrix: FloatArray, r: Float, g: Float, b: Float) = FloatArray(3) { row ->
        matrix[row * 5] * r + matrix[row * 5 + 1] * g + matrix[row * 5 + 2] * b + matrix[row * 5 + 4]
    }

    @Test
    fun adjustmentsMoveColoursTheRightWay() {
        val grey = floatArrayOf(100f, 100f, 100f)
        fun after(edit: PhotoEdit) = apply(edit.colorMatrix()!!, grey[0], grey[1], grey[2])
        assertTrue(after(PhotoEdit().with(Adjustment.Exposure, 1f))[0] > 190f)
        assertTrue(after(PhotoEdit().with(Adjustment.Brightness, -1f))[1] < 50f)
        // Contrast pushes away from mid grey; a darker grey gets darker.
        assertTrue(after(PhotoEdit().with(Adjustment.Contrast, 1f))[2] < 100f)
        val warm = after(PhotoEdit().with(Adjustment.Warmth, 1f))
        assertTrue(warm[0] > warm[1] && warm[1] > warm[2])
        // Grey has no colour to strengthen.
        val saturated = after(PhotoEdit().with(Adjustment.Saturation, 1f))
        assertEquals(100f, saturated[0], 0.01f)
        assertEquals(100f, saturated[2], 0.01f)
    }

    @Test
    fun monoRemovesAllColour() {
        val out = apply(PhotoEdit(filter = PhotoFilter.Mono).colorMatrix()!!, 200f, 40f, 90f)
        assertEquals(out[0], out[1], 0.01f)
        assertEquals(out[1], out[2], 0.01f)
    }

    @Test
    fun aSliderBackAtZeroIsForgotten() {
        val edit = PhotoEdit().with(Adjustment.Contrast, 0.4f).with(Adjustment.Contrast, 0f)
        assertTrue(edit.isUnchanged)
        // Vignette has no negative side.
        assertEquals(0f, PhotoEdit().with(Adjustment.Vignette, -0.5f).level(Adjustment.Vignette), 0f)
        // And on its own it leaves colours alone.
        assertNull(PhotoEdit().with(Adjustment.Vignette, 0.5f).colorMatrix())
    }

    @Test
    fun filtersApplyAfterAdjustments() {
        // Exposure then Mono: still grey, and brighter than Mono alone.
        val both = apply(PhotoEdit(filter = PhotoFilter.Mono).with(Adjustment.Exposure, 1f).colorMatrix()!!, 80f, 20f, 40f)
        val mono = apply(PhotoEdit(filter = PhotoFilter.Mono).colorMatrix()!!, 80f, 20f, 40f)
        assertEquals(both[0], both[2], 0.01f)
        assertEquals(mono[0] * 2f, both[0], 0.05f)
    }
}
