package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibrarySortFilterTest {

    // Library order: by date, then id (as MediaRepository sorts it). Items 2 and 3 share a time.
    private val library = listOf(
        media(1, takenAt = 100, name = "b.jpg", size = 300),
        media(2, takenAt = 200, name = "C.jpg", size = 100),
        media(3, takenAt = 200, name = "a.jpg", size = 300),
        media(4, takenAt = 300, name = "B.jpg", size = 200),
    )

    @Test
    fun dateOldestFirstKeepsLibraryOrder() {
        assertSame(library, LibrarySort.Date.apply(library, descending = false))
    }

    @Test
    fun dateNewestFirstIsTheExactReverse() {
        assertEquals(listOf(4L, 3L, 2L, 1L), LibrarySort.Date.apply(library, descending = true).ids)
    }

    @Test
    fun nameIgnoresCaseAndBreaksTiesById() {
        // a, b(1), B(4), C: "b" and "B" tie, so the lower id comes first.
        assertEquals(listOf(3L, 1L, 4L, 2L), LibrarySort.Name.apply(library, descending = false).ids)
        assertEquals(listOf(2L, 4L, 1L, 3L), LibrarySort.Name.apply(library, descending = true).ids)
    }

    @Test
    fun sizeBreaksTiesById() {
        assertEquals(listOf(2L, 4L, 1L, 3L), LibrarySort.Size.apply(library, descending = false).ids)
        assertEquals(listOf(3L, 1L, 4L, 2L), LibrarySort.Size.apply(library, descending = true).ids)
    }

    @Test
    fun descendingIsAlwaysTheExactReverseOfAscending() {
        val shuffled = library.shuffled(java.util.Random(7))
        for (sort in LibrarySort.entries) {
            val up = sort.apply(library, descending = false)
            assertEquals(sort.name, up.reversed().ids, sort.apply(shuffled, descending = true).ids)
            if (sort != LibrarySort.Date) assertEquals(sort.name, up.ids, sort.apply(shuffled, descending = false).ids)
        }
    }

    @Test
    fun sortingNeverChangesTheInput() {
        val copy = library.toList()
        for (sort in LibrarySort.entries) for (d in listOf(true, false)) sort.apply(library, d)
        assertEquals(copy, library)
    }

    @Test
    fun onlySizeDefaultsToLargestFirst() {
        assertEquals(listOf(false, false, true), LibrarySort.entries.map { it.defaultDescending })
    }

    @Test
    fun filtersMatchWhatTheyNameAndNothingElse() {
        val photo = media(1, relativePath = "DCIM/Camera/")
        val video = media(2, name = "v.mp4")
        val favorite = media(3, isFavorite = true)
        val screenshot = media(4, relativePath = "Pictures/Screenshots/", mimeType = "image/png")
        val items = listOf(photo, video, favorite, screenshot)
        fun shown(filter: LibraryFilter) = items.filter(filter::matches).ids

        assertEquals(listOf(1L, 2L, 3L, 4L), shown(LibraryFilter.All))
        // Camera photos only (#43): the screenshot isn't one.
        assertEquals(listOf(1L, 3L), shown(LibraryFilter.Photos))
        assertEquals(listOf(2L), shown(LibraryFilter.Videos))
        assertEquals(listOf(3L), shown(LibraryFilter.Favorites))
        assertEquals(listOf(4L), shown(LibraryFilter.Screenshots))
    }

    @Test
    fun photosAreCameraPhotosOnly() {
        assertTrue(LibraryFilter.Photos.matches(media(1, relativePath = "DCIM/Camera/")))
        assertTrue(LibraryFilter.Photos.matches(media(2, relativePath = "DCIM/OpenCamera/")))
        // Downloads, chat images, screenshots kept in DCIM, and camera videos aren't.
        assertFalse(LibraryFilter.Photos.matches(media(3, relativePath = "Download/")))
        assertFalse(LibraryFilter.Photos.matches(media(4, relativePath = "Pictures/WhatsApp/")))
        assertFalse(LibraryFilter.Photos.matches(media(5, relativePath = "DCIM/Screenshots/", mimeType = "image/png")))
        assertFalse(LibraryFilter.Photos.matches(media(6, name = "v.mp4")))
        assertFalse(LibraryFilter.Videos.matches(media(7, mimeType = "image/gif")))
    }
}
