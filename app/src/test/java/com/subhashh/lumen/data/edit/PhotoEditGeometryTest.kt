package com.subhashh.lumen.data.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The photo editor's geometry checked against a model of where each point of the photo lands on
 * screen, not against hand-picked numbers: whatever the turns, flips and crop, turning or flipping
 * what's on screen must move the picture and its crop exactly as the screen moved.
 */
class PhotoEditGeometryTest {

    private val eps = 1e-4f

    private data class P(val x: Float, val y: Float)

    /** Where a point of the source (fractions) is on screen, level photos only: flip, then clockwise turns. */
    private fun screen(edit: PhotoEdit, p: P): P {
        var q = if (edit.flipped) P(1f - p.x, p.y) else p
        repeat(edit.quarterTurns) { q = P(1f - q.y, q.x) }
        return q
    }

    private fun turnLeft(p: P) = P(p.y, 1f - p.x)
    private fun mirror(p: P) = P(1f - p.x, p.y)

    private fun rectOf(a: P, b: P) = CropRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

    private fun assertRect(message: String, expected: CropRect, actual: CropRect) {
        val ok = abs(expected.left - actual.left) < eps && abs(expected.top - actual.top) < eps &&
            abs(expected.right - actual.right) < eps && abs(expected.bottom - actual.bottom) < eps
        assertTrue("$message: expected $expected, was $actual", ok)
    }

    private fun randomEdit(r: Random): PhotoEdit {
        val l = r.nextFloat() * 0.5f
        val t = r.nextFloat() * 0.5f
        return PhotoEdit(
            quarterTurns = r.nextInt(4),
            flipped = r.nextBoolean(),
            crop = CropRect(l, t, l + 0.1f + r.nextFloat() * (0.9f - l), t + 0.1f + r.nextFloat() * (0.9f - t)),
        )
    }

    private val points = listOf(P(0f, 0f), P(1f, 0f), P(0f, 1f), P(1f, 1f), P(0.2f, 0.7f), P(0.9f, 0.35f))

    @Test
    fun turningLeftTurnsThePictureLeft() {
        val r = Random(1)
        repeat(200) {
            val edit = randomEdit(r)
            val turned = edit.rotatedLeft()
            for (p in points) {
                val expected = turnLeft(screen(edit, p))
                val actual = screen(turned, p)
                assertEquals("$edit at $p (x)", expected.x, actual.x, eps)
                assertEquals("$edit at $p (y)", expected.y, actual.y, eps)
            }
        }
    }

    @Test
    fun turningLeftKeepsTheSamePartOfThePhoto() {
        val r = Random(2)
        repeat(200) {
            val edit = randomEdit(r)
            val c = edit.crop
            val expected = rectOf(turnLeft(P(c.left, c.top)), turnLeft(P(c.right, c.bottom)))
            assertRect("$edit", expected, edit.rotatedLeft().crop)
        }
    }

    @Test
    fun flippingMirrorsThePictureOnScreen() {
        val r = Random(3)
        repeat(200) {
            val edit = randomEdit(r)
            val flipped = edit.flippedHorizontally()
            for (p in points) {
                val expected = mirror(screen(edit, p))
                val actual = screen(flipped, p)
                assertEquals("$edit at $p (x)", expected.x, actual.x, eps)
                assertEquals("$edit at $p (y)", expected.y, actual.y, eps)
            }
            val c = edit.crop
            assertRect("$edit crop", rectOf(mirror(P(c.left, c.top)), mirror(P(c.right, c.bottom))), flipped.crop)
        }
    }

    @Test
    fun turnAndFlipInAnyOrderCancelOut() {
        val r = Random(4)
        repeat(100) {
            val edit = randomEdit(r).copy(straighten = r.nextFloat() * 90f - 45f)
            // Turn left, flip, turn left, flip: the same as doing nothing.
            val back = edit.rotatedLeft().flippedHorizontally().rotatedLeft().flippedHorizontally()
            assertEquals(edit.quarterTurns, back.quarterTurns)
            assertEquals(edit.flipped, back.flipped)
            assertEquals(edit.straighten, back.straighten, eps)
            assertRect("$edit", edit.crop, back.crop)
        }
    }

    @Test
    fun straighteningIsTheSameZoomWhicheverWay() {
        val edit = PhotoEdit(straighten = 12f)
        val other = PhotoEdit(straighten = -12f)
        assertEquals(edit.straightenScale(4000f, 3000f), other.straightenScale(4000f, 3000f), eps)
        // A portrait frame needs the same zoom as the landscape one.
        assertEquals(edit.straightenScale(4000f, 3000f), edit.straightenScale(3000f, 4000f), eps)
        assertTrue(edit.straightenScale(4000f, 3000f) > 1f)
    }

    @Test
    fun aStraightenedFrameFitsInsideTheTurnedPhoto() {
        // Each corner of the frame, turned back by the angle, must land inside the photo.
        for (angle in listOf(1f, 7.5f, 20f, 33f, 45f, -45f, -3f)) {
            for ((w, h) in listOf(4000f to 3000f, 3000f to 4000f, 1000f to 1000f, 6000f to 1000f)) {
                val edit = PhotoEdit(straighten = angle)
                val (fw, fh) = edit.frameSize(w, h)
                val a = Math.toRadians(angle.toDouble())
                for ((cx, cy) in listOf(fw / 2 to fh / 2, -fw / 2 to fh / 2)) {
                    val x = cx * Math.cos(a) + cy * Math.sin(a)
                    val y = -cx * Math.sin(a) + cy * Math.cos(a)
                    assertTrue("$angle° $w×$h: corner x $x outside ${w / 2}", abs(x) <= w / 2 + 0.01)
                    assertTrue("$angle° $w×$h: corner y $y outside ${h / 2}", abs(y) <= h / 2 + 0.01)
                }
                assertEquals("$angle° keeps the photo's shape", w / h, fw / fh, 1e-3f)
            }
        }
    }

    @Test
    fun theSavedSizeIsNeverBiggerThanTheSource() {
        val r = Random(5)
        repeat(200) {
            val edit = randomEdit(r).copy(straighten = r.nextFloat() * 90f - 45f)
            val (w, h) = edit.outputSize(4000, 3000)
            val turned = edit.quarterTurns % 2 == 1
            assertTrue("$edit → $w×$h", w >= 1 && h >= 1)
            assertTrue("$edit → $w×$h", w <= (if (turned) 3000 else 4000) && h <= (if (turned) 4000 else 3000))
        }
    }

    // --- crop box drags, everywhere ---

    @Test
    fun noDragEverLeavesTheFrameOrGetsTooSmall() {
        val r = Random(6)
        repeat(3000) {
            val start = CropRect(0.1f, 0.2f, 0.7f, 0.9f).dragged(CropHandle.Move, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f)
            val handle = CropHandle.entries[r.nextInt(CropHandle.entries.size)]
            val out = start.dragged(handle, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1)
            val msg = "$start $handle → $out"
            assertTrue(msg, out.left >= -eps && out.top >= -eps && out.right <= 1 + eps && out.bottom <= 1 + eps)
            assertTrue(msg, out.width >= 0.08f - eps && out.height >= 0.08f - eps)
        }
    }

    @Test
    fun aLockedShapeNeverLeavesTheFrameOrChangesShape() {
        val r = Random(7)
        for (aspect in listOf(1f, 0.75f, 4f / 3f, 16f / 9f, 9f / 16f, 3.5f, 0.3f)) {
            repeat(1000) {
                val base = CropRect.centered(aspect)
                val shrink = 0.3f + r.nextFloat() * 0.7f
                val start = CropRect(
                    0.5f - base.width * shrink / 2, 0.5f - base.height * shrink / 2,
                    0.5f + base.width * shrink / 2, 0.5f + base.height * shrink / 2,
                ).dragged(CropHandle.Move, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f)
                val handle = CropHandle.entries[r.nextInt(CropHandle.entries.size)]
                val out = start.dragged(handle, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, aspect)
                val msg = "aspect $aspect $start $handle → $out"
                assertTrue(msg, out.left >= -eps && out.top >= -eps && out.right <= 1 + eps && out.bottom <= 1 + eps)
                assertEquals(msg, aspect, out.width / out.height, 1e-3f * aspect)
                assertTrue(msg, out.width > 0f && out.height > 0f)
                if (handle.isCorner) {
                    // The opposite corner stays where it was.
                    if (handle.left) assertEquals(msg, start.right, out.right, eps) else assertEquals(msg, start.left, out.left, eps)
                    if (handle.top) assertEquals(msg, start.bottom, out.bottom, eps) else assertEquals(msg, start.top, out.top, eps)
                }
            }
        }
    }

    @Test
    fun theCentredBoxIsTheBiggestOfItsShape() {
        for (aspect in listOf(0.2f, 0.75f, 1f, 4f / 3f, 5f)) {
            val box = CropRect.centered(aspect)
            assertEquals(aspect, box.width / box.height, 1e-4f)
            assertTrue("$aspect touches two sides", abs(box.width - 1f) < eps || abs(box.height - 1f) < eps)
            assertEquals(0.5f, (box.left + box.right) / 2, eps)
            assertEquals(0.5f, (box.top + box.bottom) / 2, eps)
        }
    }

    // --- sliders ---

    @Test
    fun vignetteCantGoBelowZero() {
        assertEquals(PhotoEdit(), PhotoEdit().with(Adjustment.Vignette, -0.6f))
        assertEquals(0.4f, PhotoEdit().with(Adjustment.Vignette, 0.4f).level(Adjustment.Vignette), 0f)
        assertEquals(1f, PhotoEdit().with(Adjustment.Exposure, 7f).level(Adjustment.Exposure), 0f)
        assertEquals(-1f, PhotoEdit().with(Adjustment.Exposure, -7f).level(Adjustment.Exposure), 0f)
    }

    @Test
    fun aVignetteAloneLeavesTheColoursAlone() {
        val edit = PhotoEdit().with(Adjustment.Vignette, 0.5f)
        assertEquals(null, edit.colorMatrix())
        assertTrue(!edit.isUnchanged)
    }

    @Test
    fun filtersLeaveGreyGreyExceptTheTintedOnes() {
        // Mono and Noir must not tint; a mid grey stays neutral (equal channels) through them.
        for (filter in listOf(PhotoFilter.Mono, PhotoFilter.Noir, PhotoFilter.Vivid, PhotoFilter.Fade)) {
            val m = PhotoEdit(filter = filter).colorMatrix()!!
            val out = FloatArray(3) { row -> m[row * 5] * 128 + m[row * 5 + 1] * 128 + m[row * 5 + 2] * 128 + m[row * 5 + 4] }
            assertEquals("$filter", out[0], out[1], 0.01f)
            assertEquals("$filter", out[1], out[2], 0.01f)
        }
    }
}
