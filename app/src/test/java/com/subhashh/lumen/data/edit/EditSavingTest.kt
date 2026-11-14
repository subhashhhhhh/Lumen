package com.subhashh.lumen.data.edit

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.data.media
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.TimeZone

/** Which photos and videos the editors take, and how a saved edit is dated. */
@RunWith(RobolectricTestRunner::class)
class EditSavingTest {

    private lateinit var zone: TimeZone

    @Before
    fun pinZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(zone)

    // --- what can be edited, and what can be written over ---

    @Test
    fun onlyFormatsAndroidCanWriteAreReplaced() {
        for (type in listOf("image/jpeg", "image/png", "image/webp")) {
            assertTrue(type, PhotoEditSaver.canReplace(media(1, mimeType = type)))
        }
        for (type in listOf("image/heic", "image/heif", "image/avif", "image/jxl", "image/x-adobe-dng", "image/gif", "image/bmp")) {
            assertFalse(type, PhotoEditSaver.canReplace(media(1, mimeType = type)))
        }
    }

    @Test
    fun aFileOutsideMediaStoreIsNeverReplaced() {
        val hidden = media(1, mimeType = "image/jpeg").copy(uri = Uri.fromFile(File("/storage/emulated/0/P/.a.jpg")))
        assertFalse(PhotoEditSaver.canReplace(hidden))
    }

    @Test
    fun stillPhotosOnly() {
        assertTrue(PhotoEditSaver.canEdit(media(1, mimeType = "image/heic")))
        assertTrue(PhotoEditSaver.canEdit(media(1, mimeType = "image/jxl")))
        assertFalse(PhotoEditSaver.canEdit(media(1, mimeType = "image/gif")))
        assertFalse(PhotoEditSaver.canEdit(media(1, mimeType = "image/svg+xml")))
        assertFalse(PhotoEditSaver.canEdit(media(1, name = "v.mp4", mimeType = "video/mp4")))
        assertTrue(VideoEditSaver.canEdit(media(1, name = "v.mp4", mimeType = "video/mp4")))
        assertFalse(VideoEditSaver.canEdit(media(1, mimeType = "image/jpeg")))
    }

    // --- ImageExport.stampDate, used by edited and resized copies ---

    private fun jpeg(): ExifInterface {
        val file = File.createTempFile("tiny", ".jpg")
        javaClass.getResourceAsStream("/edit/tiny.jpg")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
            setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, null)
        }
    }

    // 2024-06-01 01:00:00 UTC.
    private val instant = 1_717_203_600_000L

    @Test
    fun anUndatedCopyGetsTheTimeAndZone() {
        val exif = jpeg()
        ImageExport.stampDate(exif, instant)
        assertEquals("2024:06:01 06:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+05:30", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals(instant, exif.dateTimeOriginal)
    }

    @Test
    fun utcIsWrittenAsAnOffset() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val exif = jpeg()
        ImageExport.stampDate(exif, instant)
        assertEquals("+00:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals(instant, exif.dateTimeOriginal)
    }

    @Test
    fun summerTimeIsTheOffsetOnTheDayNotToday() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        val exif = jpeg()
        ImageExport.stampDate(exif, instant) // June: British Summer Time
        assertEquals("+01:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        val winter = jpeg()
        ImageExport.stampDate(winter, 1_704_070_800_000L) // 2024-01-01 01:00 UTC
        assertEquals("+00:00", winter.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
    }

    @Test
    fun aFullyDatedCopyIsLeftAlone() {
        val exif = jpeg()
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:06:01 10:00:00")
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+09:00")
        ImageExport.stampDate(exif, instant + 3_600_000)
        assertEquals("2024:06:01 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+09:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
    }

    /**
     * Most cameras write the time of day but no zone. A photo taken at 10:00 in Tokyo, edited
     * back home in India: the copy's own date is rewritten from the library's instant in today's
     * zone, so the copy now says it was taken at 06:30. The moment is the same, but the time on
     * the photo isn't the one the camera showed. Keeping the camera's time and adding the zone
     * that matches the instant ("+09:00") keeps both.
     */
    @Test
    fun theCamerasTimeOfDayIsKept() {
        val exif = jpeg()
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:06:01 10:00:00")
        ImageExport.stampDate(exif, instant) // MediaStore's instant for it: 01:00 UTC, so Tokyo time
        assertEquals("2024:06:01 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+09:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals(instant, exif.dateTimeOriginal)
    }
}
