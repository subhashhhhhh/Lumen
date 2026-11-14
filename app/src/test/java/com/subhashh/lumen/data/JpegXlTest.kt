package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** JPEG XL (#15): the parts outside the decoder itself. */
@RunWith(RobolectricTestRunner::class)
class JpegXlTest {

    @Test
    fun hiddenJpegXlFilesAreFoundAnyCase() {
        assertEquals("image/jxl", HiddenMedia.typeOf(File(".photo.jxl")))
        assertEquals("image/jxl", HiddenMedia.typeOf(File("Pictures/.private/IMG_1.JXL")))
        assertEquals("image/jxl", HiddenMedia.typeOf(File("a.Jxl")))
    }

    @Test
    fun onlyTheExtensionCounts() {
        assertEquals(null, HiddenMedia.typeOf(File("jxl")))
        assertEquals(null, HiddenMedia.typeOf(File("photo.jxl.txt")))
        assertEquals(null, HiddenMedia.typeOf(File("photo.jx")))
    }

    @Test
    fun aHiddenJpegXlIsAPhoto() {
        val item = HiddenMedia.item(File("/storage/emulated/0/Pictures/.p/.a.jxl"), "image/jxl", 0)
        assertFalse(item.isVideo)
        assertEquals("image/jxl", item.mimeType)
    }

    @Test
    fun rotationIsNotOfferedBecauseItCantBeSaved() {
        // Nothing on Android can write a JPEG XL, and it has no EXIF flag Lumen can set.
        assertFalse(MediaFiles.canRotate(media(1, name = "a.jxl", mimeType = "image/jxl")))
    }
}
