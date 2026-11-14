package com.subhashh.lumen.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** What Lumen hands Lumen AI: each item's fields, and JPEG XL decoding only where it's needed. */
@RunWith(RobolectricTestRunner::class)
class AiHostTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun aRefCarriesTheItem() {
        val item = media(42, takenAt = 1_700_000_000_000, name = "IMG_42.jpg").copy(modified = 1_700_000_100, durationMs = 0)
        val ref = item.toRef()
        assertEquals(42L, ref.id)
        assertEquals(item.uri, ref.uri)
        assertEquals("image/jpeg", ref.mimeType)
        assertEquals(false, ref.isVideo)
        assertEquals(1_700_000_000_000L, ref.takenAt)
        assertEquals(1_700_000_100L, ref.modified)
        assertEquals("IMG_42.jpg", ref.name)
    }

    @Test
    fun videosKeepTheirLength() {
        val ref = media(7, name = "v.mp4").copy(durationMs = 12_345, modified = 5).toRef()
        assertEquals(true, ref.isVideo)
        assertEquals(12_345L, ref.durationMs)
        assertEquals(5L, ref.modified)
    }

    @Test
    fun onlyJpegXlGetsItsOwnVersion() {
        // Indexes are keyed by (id, modified): a new version has JPEG XL read again, and nothing else.
        assertEquals(101L, media(1, mimeType = "image/jxl").copy(modified = 100).toRef().modified)
        for (type in listOf("image/jpeg", "image/heic", "image/avif", "image/png", "video/mp4")) {
            assertEquals(type, 100L, media(1, mimeType = type).copy(modified = 100).toRef().modified)
        }
    }

    @Test
    fun theVersionIsStableBetweenPasses() {
        // The same file must give the same key every pass, or it would be indexed again each time.
        val item = media(1, mimeType = "image/jxl").copy(modified = 100)
        assertEquals(item.toRef().modified, item.toRef().modified)
    }

    @Test
    fun otherFormatsAreLeftToAndroid() {
        val host = AiHost(context)
        for (type in listOf("image/jpeg", "image/heic", "image/avif", "image/png")) {
            assertNull(type, host.photo(context, media(1, mimeType = type).toRef(), 512))
        }
    }

    @Test
    fun jpegXlOutsideMediaStoreIsLeftAlone() {
        val hidden = media(1, mimeType = "image/jxl").copy(uri = Uri.fromFile(File("/storage/emulated/0/P/.a.jxl")))
        assertNull(AiHost(context).photo(context, hidden.toRef(), 512))
    }
}
