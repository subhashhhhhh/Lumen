package com.subhashh.lumen.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** HiddenMedia's pure parts and HiddenIndex, on a fake shared storage under Robolectric. */
@RunWith(RobolectricTestRunner::class)
class HiddenMediaTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var root: File

    @Before
    fun storage() {
        root = Environment.getExternalStorageDirectory().apply { mkdirs() }
    }

    private fun file(path: String) = File(root, path).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
    private fun dir(path: String) = File(root, path).apply { mkdirs() }
    private fun noMedia(path: String) = file("$path/.nomedia")

    @Test
    fun systemTrashAndPendingNamesAreNotHiddenFiles() {
        assertTrue(HiddenMedia.isSystemFile(".trashed-1712345678-IMG_1.jpg"))
        assertTrue(HiddenMedia.isSystemFile(".pending-1712345678-VID_1.mp4"))
        assertFalse(HiddenMedia.isSystemFile(".IMG_1.jpg"))
        assertFalse(HiddenMedia.isSystemFile("trashed-IMG_1.jpg"))
        assertFalse(HiddenMedia.isSystemFile(".trashed_IMG.jpg"))
    }

    @Test
    fun typesByExtensionAnyCase() {
        assertEquals("image/jpeg", HiddenMedia.typeOf(File("a.JPG")))
        assertEquals("image/jpeg", HiddenMedia.typeOf(File(".a.jpeg")))
        assertEquals("video/quicktime", HiddenMedia.typeOf(File("clip.MOV")))
        assertEquals("image/x-adobe-dng", HiddenMedia.typeOf(File("raw.dng")))
        assertNull(HiddenMedia.typeOf(File("notes.txt")))
        assertNull(HiddenMedia.typeOf(File("jpg")))
        assertNull(HiddenMedia.typeOf(File(".nomedia")))
    }

    @Test
    fun itemDescribesTheFile() {
        val f = file("Pictures/.private/IMG_1.jpg")
        val item = HiddenMedia.item(f, "image/jpeg", modified = 1_700_000_123_456, width = 40, height = 30)
        assertTrue(HiddenMedia.isHidden(item))
        assertEquals(f, HiddenMedia.file(item))
        assertEquals("Pictures/.private/", item.relativePath)
        assertEquals(".private", item.bucketName)
        assertEquals("IMG_1.jpg", item.name)
        assertEquals(1_700_000_123_456, item.takenAt)
        assertEquals(1_700_000_123L, item.modified)
        assertFalse(item.isVideo)
        assertFalse(item.isFavorite)
        assertEquals(40, item.width)
    }

    @Test
    fun itemIdsAreNegativeAndStable() {
        val paths = (1..500).map { "Folder$it/.IMG_$it.jpg" }
        val ids = paths.map { HiddenMedia.item(File(root, it), "image/jpeg", 0).id }
        assertTrue("MediaStore ids are positive; hidden ones must never collide", ids.all { it < 0 })
        assertEquals(ids, paths.map { HiddenMedia.item(File(root, it), "image/jpeg", 0).id })
    }

    @Test
    fun fileAtTheTopOfStorageHasNoFolder() {
        val item = HiddenMedia.item(file(".top.jpg"), "image/jpeg", 0)
        assertEquals("", item.relativePath)
    }

    @Test
    fun videosAreVideos() {
        assertTrue(HiddenMedia.item(file("Movies/.v.mp4"), "video/mp4", 0, durationMs = 5000).isVideo)
    }

    @Test
    fun itemsInTheSameFolderShareABucket() {
        val a = HiddenMedia.item(file("Pictures/.p/a.jpg"), "image/jpeg", 0)
        val b = HiddenMedia.item(file("Pictures/.p/b.jpg"), "image/jpeg", 0)
        val c = HiddenMedia.item(file("Pictures/.q/a.jpg"), "image/jpeg", 0)
        assertEquals(a.bucketId, b.bucketId)
        assertTrue(a.bucketId != c.bucketId)
    }

    // --- isHiddenFolder / unhidePlan ---

    @Test
    fun hiddenFolderByNomediaOrDotNameHereOrAbove() {
        noMedia("Pictures/Private")
        assertTrue(HiddenMedia.isHiddenFolder(dir("Pictures/Private")))
        assertTrue(HiddenMedia.isHiddenFolder(dir("Pictures/Private/Sub")))
        assertTrue(HiddenMedia.isHiddenFolder(dir("Pictures/.secret")))
        assertTrue(HiddenMedia.isHiddenFolder(dir("Pictures/.secret/Sub")))
        assertFalse(HiddenMedia.isHiddenFolder(dir("Pictures/Public")))
        assertFalse(HiddenMedia.isHiddenFolder(dir("Pictures")))
        assertFalse(HiddenMedia.isHiddenFolder(root))
    }

    @Test
    fun unhidingADotFileRenamesIt() {
        val f = file("DCIM/Camera/.IMG_1.jpg")
        assertEquals(HiddenMedia.Unhide.File(f), HiddenMedia.unhidePlan(HiddenMedia.item(f, "image/jpeg", 0)))
    }

    @Test
    fun unhidingInANomediaFolderRemovesTheClosestMarker() {
        noMedia("Pictures/Private")
        noMedia("Pictures/Private/Sub")
        val f = file("Pictures/Private/Sub/a.jpg")
        assertEquals(HiddenMedia.Unhide.Folder(File(root, "Pictures/Private/Sub")), HiddenMedia.unhidePlan(HiddenMedia.item(f, "image/jpeg", 0)))
    }

    @Test
    fun unhidingFindsAMarkerHigherUp() {
        noMedia("Pictures/Private")
        val f = file("Pictures/Private/Sub/a.jpg")
        assertEquals(HiddenMedia.Unhide.Folder(File(root, "Pictures/Private")), HiddenMedia.unhidePlan(HiddenMedia.item(f, "image/jpeg", 0)))
    }

    @Test
    fun aDotFolderIsNotRenamed() {
        val f = file("WhatsApp/.Statuses/a.jpg")
        assertEquals(HiddenMedia.Unhide.DotFolder(File(root, "WhatsApp/.Statuses")), HiddenMedia.unhidePlan(HiddenMedia.item(f, "image/jpeg", 0)))
    }

    @Test
    fun aVisibleFileHasNothingToUnhide() {
        val f = file("DCIM/Camera/a.jpg")
        assertNull(HiddenMedia.unhidePlan(HiddenMedia.item(f, "image/jpeg", 0)))
    }

    @Test
    fun unhidingNeverLooksAboveStorage() {
        // A .nomedia at the storage root would cover everything; Lumen must not offer to delete it.
        noMedia(".")
        val f = file("DCIM/Camera/a.jpg")
        assertNull(HiddenMedia.unhidePlan(HiddenMedia.item(f, "image/jpeg", 0)))
    }

    // --- HiddenIndex ---

    @Test
    fun indexRoundTrips() = runBlocking {
        val items = listOf(
            HiddenMedia.item(file("Pictures/.p/a.jpg"), "image/jpeg", 1_700_000_000_000, width = 4000, height = 3000),
            HiddenMedia.item(file("Movies/.m/clip.mp4"), "video/mp4", 1_600_000_000_000, width = 1920, height = 1080, durationMs = 12_345),
            HiddenMedia.item(file("Pictures/Café Paris/.été 2024 (1).jpg"), "image/jpeg", 5),
        )
        HiddenIndex.save(context, items)
        assertEquals(items, HiddenIndex.load(context))
    }

    @Test
    fun noIndexYetIsNull() = runBlocking {
        assertNull(HiddenIndex.load(context))
    }

    @Test
    fun emptyIndexIsEmptyNotNull() = runBlocking {
        HiddenIndex.save(context, emptyList())
        assertEquals(emptyList<MediaItem>(), HiddenIndex.load(context))
    }

    @Test
    fun indexIsKeptOutOfBackups() = runBlocking {
        HiddenIndex.save(context, listOf(HiddenMedia.item(file("P/.a.jpg"), "image/jpeg", 0)))
        val written = context.noBackupFilesDir.walk().filter { it.isFile }.toList()
        assertTrue("index not in no-backup storage", written.any { it.readText().contains(".a.jpg") })
        val backedUp = context.filesDir.walk().filter { it.isFile }.toList()
        assertTrue("hidden paths in backed-up storage", backedUp.none { it.readText().contains(".a.jpg") })
    }

    @Test
    fun damagedLinesAndSystemFilesAreDropped() = runBlocking {
        val good = HiddenMedia.item(file("P/.ok.jpg"), "image/jpeg", 1000)
        HiddenIndex.save(context, listOf(good))
        val index = File(context.noBackupFilesDir, "hidden_index_v2.tsv")
        index.appendText(
            "\nonly\tthree\tfields" +
                "\n${File(root, "P/.bad.jpg").path}\timage/jpeg\tnot-a-time\t0\t0\t0" +
                "\n${File(root, "P/.trashed-1712345678-x.jpg").path}\timage/jpeg\t1\t0\t0\t0",
        )
        assertEquals(listOf(good), HiddenIndex.load(context))
    }

    @Test
    fun savingReplacesTheWholeIndex() = runBlocking {
        HiddenIndex.save(context, listOf(HiddenMedia.item(file("P/.a.jpg"), "image/jpeg", 0)))
        val b = HiddenMedia.item(file("P/.b.jpg"), "image/jpeg", 0)
        HiddenIndex.save(context, listOf(b))
        assertEquals(listOf(b), HiddenIndex.load(context))
    }
}
