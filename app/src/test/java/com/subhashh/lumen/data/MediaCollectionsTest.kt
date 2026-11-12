package com.subhashh.lumen.data

import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCollectionsTest {

    private val camera = "DCIM/Camera/"
    private val shots = "Pictures/Screenshots/"
    private val whatsapp = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"
    private val beach = "Pictures/beach/"

    // Oldest first, as MediaRepository loads it.
    private val library = listOf(
        media(1, relativePath = camera),
        media(2, relativePath = shots),
        media(3, relativePath = whatsapp),
        media(4, relativePath = camera, isFavorite = true),
        media(5, relativePath = shots),
        media(6, relativePath = shots, name = "clip.mp4"),
        media(7, relativePath = beach),
    )

    private fun folders(settings: AlbumSettings = AlbumSettings()) = MediaCollections.folders(library, settings)
    private fun List<MediaCollection>.titles() = map { it.title }

    // --- CollectionKeys ---

    @Test
    fun keysRoundTripAndNeverCollide() {
        assertEquals(42L, CollectionKeys.hiddenFolderId(CollectionKeys.hiddenFolder(42)))
        assertEquals(-5L, CollectionKeys.hiddenFolderId(CollectionKeys.hiddenFolder(-5)))
        assertEquals(7L, CollectionKeys.personId(CollectionKeys.person(7)))
        val keys = listOf(CollectionKeys.folder(1), CollectionKeys.hiddenFolder(1), CollectionKeys.person(1), CollectionKeys.emptyAlbum("1"))
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun idParsersRejectOtherKeys() {
        assertNull(CollectionKeys.hiddenFolderId("42")) // no prefix
        assertNull(CollectionKeys.hiddenFolderId(CollectionKeys.folder(42)))
        assertNull(CollectionKeys.hiddenFolderId("hidden:"))
        assertNull(CollectionKeys.hiddenFolderId("hidden:abc"))
        assertNull(CollectionKeys.hiddenFolderId(CollectionKeys.Hidden))
        assertNull(CollectionKeys.personId(null))
        assertNull(CollectionKeys.personId("42"))
        assertNull(CollectionKeys.personId(CollectionKeys.folder(42)))
        assertNull(CollectionKeys.personId(CollectionKeys.People))
    }

    @Test
    fun hiddenKeysAreExactlyHiddenAndItsFolders() {
        assertTrue(CollectionKeys.isHidden(CollectionKeys.Hidden))
        assertTrue(CollectionKeys.isHidden(CollectionKeys.hiddenFolder(3)))
        assertFalse(CollectionKeys.isHidden(null))
        assertFalse(CollectionKeys.isHidden("hiddenx"))
        assertFalse(CollectionKeys.isHidden("Hidden"))
        assertFalse(CollectionKeys.isHidden(CollectionKeys.folder(3)))
        assertFalse(CollectionKeys.isHidden(CollectionKeys.Library))
    }

    @Test
    fun folderKeysAreOnlyDeviceFolders() {
        assertTrue(CollectionKeys.isFolder(CollectionKeys.folder(3)))
        assertFalse(CollectionKeys.isFolder(null))
        assertFalse(CollectionKeys.isFolder(CollectionKeys.hiddenFolder(3)))
        assertFalse(CollectionKeys.isFolder(CollectionKeys.emptyAlbum(camera)))
        assertFalse(CollectionKeys.isFolder(CollectionKeys.Library))
    }

    @Test
    fun itemsForEachUtility() {
        val trash = listOf(media(99))
        assertSame(library, CollectionKeys.items(CollectionKeys.Library, library, trash))
        assertEquals(listOf(4L), CollectionKeys.items(CollectionKeys.Favorites, library, trash).ids)
        assertEquals(listOf(6L), CollectionKeys.items(CollectionKeys.Videos, library, trash).ids)
        assertEquals(listOf(2L, 5L, 6L), CollectionKeys.items(CollectionKeys.Screenshots, library, trash).ids)
        assertEquals(listOf(99L), CollectionKeys.items(CollectionKeys.RecentlyDeleted, library, trash).ids)
    }

    @Test
    fun itemsForAFolderKeepLibraryOrder() {
        val key = CollectionKeys.folder(camera.hashCode().toLong())
        assertEquals(listOf(1L, 4L), CollectionKeys.items(key, library, emptyList()).ids)
    }

    @Test
    fun itemsForKeysTheCallerSuppliesAreEmpty() {
        for (key in listOf(
            CollectionKeys.Hidden, CollectionKeys.Search, CollectionKeys.People, CollectionKeys.person(1),
            CollectionKeys.hiddenFolder(1), CollectionKeys.emptyAlbum(camera), "folder:abc", "folder:", "nonsense",
        )) {
            assertEquals(key, emptyList<MediaItem>(), CollectionKeys.items(key, library, library))
        }
    }

    // --- isRaw / isScreenshot ---

    @Test
    fun rawByTypeOrByExtensionWhenTheTypeIsGeneric() {
        assertTrue(media(1, name = "a.dng", mimeType = "image/x-adobe-dng").isRaw)
        assertTrue(media(1, name = "a.CR2", mimeType = "image/x-canon-cr2").isRaw)
        assertTrue(media(1, name = "a.nef", mimeType = "image/x-nikon-nef").isRaw)
        assertFalse("an x- type alone isn't RAW", media(1, name = "a.jpg", mimeType = "image/x-ms-bmp").isRaw)
        assertFalse("a RAW extension on a JPEG isn't RAW", media(1, name = "a.dng", mimeType = "image/jpeg").isRaw)
        assertFalse(media(1, name = "dng", mimeType = "image/x-foo").isRaw) // no extension at all
    }

    @Test
    fun screenshotsByFolderAnyCase() {
        assertTrue(media(1, relativePath = "Pictures/Screenshots/").isScreenshot)
        assertTrue(media(1, relativePath = "DCIM/SCREENSHOT/").isScreenshot)
        assertFalse(media(1, relativePath = "DCIM/Camera/", name = "Screenshot_1.png").isScreenshot)
    }

    // --- AlbumSettings.renamed ---

    @Test
    fun renameMovesPinsCoversAndCreatedAlbums() {
        val before = AlbumSettings(
            sort = AlbumSort.Name,
            reversed = true,
            pinned = listOf(beach, "Pictures/Trips/"),
            covers = mapOf("Pictures/Trips/" to 9L, camera to 1L),
            created = listOf("Pictures/Trips/"),
        )
        val after = before.renamed("Pictures/Trips/", "Pictures/Travel/")
        assertEquals(AlbumSort.Name, after.sort)
        assertTrue(after.reversed)
        assertEquals(listOf(beach, "Pictures/Travel/"), after.pinned) // pin order kept
        assertEquals(mapOf("Pictures/Travel/" to 9L, camera to 1L), after.covers)
        assertEquals(listOf("Pictures/Travel/"), after.created)
    }

    @Test
    fun renameLeavesSimilarlyNamedFoldersAlone() {
        val before = AlbumSettings(pinned = listOf("Pictures/Trips2/"), covers = mapOf("Pictures/Trips2/" to 1L))
        assertEquals(before, before.renamed("Pictures/Trips/", "Pictures/Travel/"))
    }

    /**
     * Renaming a folder on disk moves its subfolders too (MediaFiles.renameAlbum renames the
     * directory). An album inside it must keep its pin and cover at its new path.
     */
    @Test
    fun renameCarriesSubfolderAlbumsAlong() {
        val before = AlbumSettings(
            pinned = listOf("Pictures/Trips/Rome/"),
            covers = mapOf("Pictures/Trips/Rome/" to 5L),
        )
        val after = before.renamed("Pictures/Trips/", "Pictures/Travel/")
        assertEquals(listOf("Pictures/Travel/Rome/"), after.pinned)
        assertEquals(mapOf("Pictures/Travel/Rome/" to 5L), after.covers)
    }

    // --- MediaCollections.folders ---

    @Test
    fun oneAlbumPerFolderWithNewestCoverAndCount() {
        val albums = folders().associateBy { it.relativePath }
        assertEquals(setOf(camera, shots, whatsapp, beach), albums.keys)
        val cam = albums.getValue(camera)
        assertEquals(CollectionKeys.folder(camera.hashCode().toLong()), cam.key)
        assertEquals("Camera", cam.title)
        assertEquals(2, cam.count)
        assertEquals(4L, cam.cover?.id)
        assertEquals(4000L, cam.latestAt)
        assertFalse(cam.customCover)
        assertFalse(cam.pinned)
        assertEquals(library.size, albums.values.sumOf { it.count })
    }

    @Test
    fun sortsByRecencyNameAndCountEitherWay() {
        assertEquals(listOf("beach", "Screenshots", "Camera", "WhatsApp Images"), folders().titles())
        assertEquals(
            listOf("WhatsApp Images", "Camera", "Screenshots", "beach"),
            folders(AlbumSettings(reversed = true)).titles(),
        )
        // Case-insensitive: "beach" before "Camera".
        assertEquals(
            listOf("beach", "Camera", "Screenshots", "WhatsApp Images"),
            folders(AlbumSettings(sort = AlbumSort.Name)).titles(),
        )
        assertEquals(
            listOf("WhatsApp Images", "Screenshots", "Camera", "beach"),
            folders(AlbumSettings(sort = AlbumSort.Name, reversed = true)).titles(),
        )
        assertEquals("Screenshots", folders(AlbumSettings(sort = AlbumSort.Count)).first().title)
        assertEquals("Camera", folders(AlbumSettings(sort = AlbumSort.Count))[1].title)
        assertEquals("Screenshots", folders(AlbumSettings(sort = AlbumSort.Count, reversed = true)).last().title)
    }

    @Test
    fun pinnedComeFirstInPinOrderWhateverTheSort() {
        val pinned = listOf(whatsapp, camera, "Pictures/Gone/")
        for (sort in AlbumSort.entries) for (reversed in listOf(false, true)) {
            val albums = folders(AlbumSettings(sort = sort, reversed = reversed, pinned = pinned))
            val label = "$sort reversed=$reversed"
            assertEquals(label, listOf("WhatsApp Images", "Camera"), albums.take(2).titles())
            assertEquals(label, listOf(true, true, false, false), albums.map { it.pinned })
            assertEquals(label, 4, albums.size) // a pin for a folder that's gone adds nothing
        }
    }

    @Test
    fun chosenCoverIsUsedOnlyIfItIsInThatFolder() {
        fun cover(id: Long) = folders(AlbumSettings(covers = mapOf(camera to id))).first { it.relativePath == camera }

        val chosen = cover(1)
        assertEquals(1L, chosen.cover?.id)
        assertTrue(chosen.customCover)
        assertEquals("recency still comes from the newest item", 4000L, chosen.latestAt)

        val deleted = cover(999)
        assertEquals(4L, deleted.cover?.id)
        assertFalse(deleted.customCover)

        val elsewhere = cover(5) // a screenshot, not in Camera
        assertEquals(4L, elsewhere.cover?.id)
        assertFalse(elsewhere.customCover)
    }

    @Test
    fun emptyNewAlbumsShowUntilSomethingIsInThem() {
        val albums = folders(AlbumSettings(created = listOf("Pictures/Trip/", camera)))
        assertEquals(5, albums.size)
        assertEquals(1, albums.count { it.relativePath == camera }) // not duplicated
        val trip = albums.single { it.relativePath == "Pictures/Trip/" }
        assertEquals(CollectionKeys.emptyAlbum("Pictures/Trip/"), trip.key)
        assertEquals("Trip", trip.title)
        assertEquals(0, trip.count)
        assertNull(trip.cover)
        assertEquals("a new album is at the top of Recent", trip, albums.first())
    }

    @Test
    fun emptyNewAlbumCanBePinned() {
        val albums = folders(AlbumSettings(sort = AlbumSort.Count, created = listOf("Pictures/Trip/"), pinned = listOf("Pictures/Trip/")))
        assertEquals("Trip", albums.first().title)
        assertTrue(albums.first().pinned)
    }

    @Test
    fun noLibraryNoAlbums() {
        assertEquals(emptyList<MediaCollection>(), MediaCollections.folders(emptyList()))
    }

    // --- MediaCollections.utilities ---

    @Test
    fun utilitiesCountAndCoverWithTheNewestItem() {
        val context = RuntimeEnvironment.getApplication()
        val trash = listOf(media(50), media(51))
        val utilities = MediaCollections.utilities(context, library, trash)
        assertEquals(
            listOf(CollectionKeys.Favorites, CollectionKeys.Videos, CollectionKeys.Screenshots, CollectionKeys.RecentlyDeleted, CollectionKeys.Hidden),
            utilities.map { it.key },
        )
        val byKey = utilities.associateBy { it.key }
        assertEquals(1, byKey.getValue(CollectionKeys.Favorites).count)
        assertEquals(4L, byKey.getValue(CollectionKeys.Favorites).cover?.id)
        assertEquals(3, byKey.getValue(CollectionKeys.Screenshots).count)
        assertEquals(6L, byKey.getValue(CollectionKeys.Screenshots).cover?.id)
        assertEquals(51L, byKey.getValue(CollectionKeys.RecentlyDeleted).cover?.id)
        assertEquals(MediaCollections.LockedCount, byKey.getValue(CollectionKeys.Hidden).count)
        assertNull("Hidden never shows a cover while locked", byKey.getValue(CollectionKeys.Hidden).cover)
        assertTrue(utilities.all { it.title.isNotBlank() })
    }

    @Test
    fun utilitiesOfAnEmptyLibraryHaveNoCovers() {
        val context = RuntimeEnvironment.getApplication()
        val utilities = MediaCollections.utilities(context, emptyList(), emptyList())
        assertTrue(utilities.all { it.cover == null })
        assertEquals(listOf(0, 0, 0, 0, MediaCollections.LockedCount), utilities.map { it.count })
    }
}
