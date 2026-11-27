package com.subhashh.lumen.ui.viewer

import androidx.compose.ui.geometry.Rect
import com.subhashh.lumen.data.media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/** Where the viewer shows a photo, the open and close flights' target: fitted both ways, centred. */
@RunWith(RobolectricTestRunner::class)
class ViewerFitTest {

    private fun photo(w: Int, h: Int) = media(1).copy(width = w, height = h)

    private fun near(expected: Rect, actual: Rect) {
        val ok = listOf(expected.left to actual.left, expected.top to actual.top, expected.right to actual.right, expected.bottom to actual.bottom)
            .all { (a, b) -> kotlin.math.abs(a - b) < 0.01f }
        assertTrue("expected $expected, got $actual", ok)
    }

    @Test
    fun aLandscapePhotoOnAPhoneSpansTheWidth() {
        // 1220 × 2712 screen, a 4:3 photo: full width, centred top to bottom.
        near(Rect(0f, 898.5f, 1220f, 1813.5f), fullScreenRect(photo(4000, 3000), 1220f, 2712f))
    }

    @Test
    fun aTallPhotoInAWideWindowIsNarrowerNotFullWidth() {
        // The bug in 214e427's message: a 3:4 portrait in a 2000 × 1200 desktop window.
        near(Rect(550f, 0f, 1450f, 1200f), fullScreenRect(photo(3000, 4000), 2000f, 1200f))
    }

    @Test
    fun anUnknownSizeFillsTheWindow() {
        near(Rect(0f, 0f, 800f, 600f), fullScreenRect(photo(0, 0), 800f, 600f))
        near(Rect(0f, 0f, 800f, 600f), fullScreenRect(photo(100, -1), 800f, 600f))
    }

    @Test
    fun anyPhotoFitsInsideCentredAndKeepsItsShape() {
        val random = Random(5)
        repeat(2000) {
            val w = 100 + random.nextInt(19_900)
            val h = 100 + random.nextInt(19_900)
            val ww = 200f + random.nextInt(3000)
            val wh = 200f + random.nextInt(3000)
            val r = fullScreenRect(photo(w, h), ww, wh)
            val eps = 0.05f
            assertTrue("$w×$h in $ww×$wh: $r", r.left >= -eps && r.top >= -eps && r.right <= ww + eps && r.bottom <= wh + eps)
            // Touches the window on at least one axis: as big as it fits, no bigger.
            assertTrue("$w×$h in $ww×$wh: $r not as big as it fits", kotlin.math.abs(r.width - ww) < eps || kotlin.math.abs(r.height - wh) < eps)
            assertEquals(w.toFloat() / h, r.width / r.height, 0.001f * (w.toFloat() / h))
            assertEquals(ww / 2, r.center.x, eps)
            assertEquals(wh / 2, r.center.y, eps)
        }
    }
}
