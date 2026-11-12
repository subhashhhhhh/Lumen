package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaFilesTest {

    @Test
    fun newAlbumGoesInPictures() {
        assertEquals("Pictures/Trip/", MediaFiles.newAlbumPath("Trip"))
        assertEquals("Pictures/Trip to Rome/", MediaFiles.newAlbumPath("  Trip to Rome  "))
        assertEquals("Pictures/Été 東京/", MediaFiles.newAlbumPath("Été 東京"))
    }

    @Test
    fun aSlashInTheNameDoesNotMakeASubfolder() {
        assertEquals("Pictures/2024_03/", MediaFiles.newAlbumPath("2024/03"))
        assertEquals("Pictures/_etc_/", MediaFiles.newAlbumPath("/etc/"))
    }

    /**
     * The album picker accepts any non-blank name. "." and ".." are names too, but as path
     * segments they mean "this folder" and "the folder above": photos copied into a ".." album
     * would land in the root of shared storage, outside Pictures.
     */
    @Test
    fun newAlbumNeverEscapesPictures() {
        for (name in listOf("..", ".", " .. ", "../..", "./")) {
            val path = MediaFiles.newAlbumPath(name)
            val segments = path.trimEnd('/').split('/')
            assertEquals("'$name' → $path", "Pictures", segments.first())
            assertTrue("'$name' → $path", segments.size == 2)
            assertFalse("'$name' → $path", segments.any { it == "." || it == ".." || it.isEmpty() })
        }
    }

    @Test
    fun rotationIsOfferedOnlyWhereItCanBeSaved() {
        assertTrue(MediaFiles.canRotate(media(1, mimeType = "image/jpeg")))
        assertTrue(MediaFiles.canRotate(media(1, mimeType = "image/png")))
        assertTrue(MediaFiles.canRotate(media(1, mimeType = "image/webp")))
        assertTrue(MediaFiles.canRotate(media(1, name = "v.mp4", mimeType = "video/mp4")))
        assertTrue(MediaFiles.canRotate(media(1, name = "v.mov", mimeType = "video/quicktime")))
        for (type in listOf("image/heic", "image/avif", "image/gif", "image/x-adobe-dng", "video/webm", "video/x-matroska")) {
            assertFalse(type, MediaFiles.canRotate(media(1, mimeType = type)))
        }
    }
}
