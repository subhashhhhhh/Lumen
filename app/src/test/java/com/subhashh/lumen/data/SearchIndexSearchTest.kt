package com.subhashh.lumen.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

/** [SearchIndex.search] end to end: which items come back, and in what order. */
@RunWith(RobolectricTestRunner::class)
class SearchIndexSearchTest {

    private lateinit var savedZone: TimeZone

    // Month and year words come from the phone's time zone; pin it so dates are unambiguous.
    @Before
    fun pinTimeZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(savedZone)

    private fun at(year: Int, month: Int, day: Int) =
        LocalDateTime.of(year, month, day, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val cameraPhoto = media(1, at(2024, 3, 12), "IMG_20240312_101500.jpg", "DCIM/Camera/")
    private val screenshot = media(2, at(2024, 5, 1), "Screenshot_20240501.png", "Pictures/Screenshots/", mimeType = "image/png")
    private val favoriteVideo = media(3, at(2023, 6, 20), "VID_20230620.mp4", "DCIM/Camera/", isFavorite = true)
    private val gif = media(4, at(2022, 1, 5), "funny.gif", "Download/", mimeType = "image/gif")
    private val raw = media(5, at(2021, 8, 9), "DSC_0042.DNG", "DCIM/Raw/", mimeType = "image/x-adobe-dng")
    private val cafe = media(6, at(2020, 11, 30), "dinner.jpg", "Pictures/Café Paris/")

    private val all = listOf(raw, cafe, gif, favoriteVideo, cameraPhoto, screenshot)
    // Lazy: built after the time zone is pinned, since it reads month and year words then.
    private val index by lazy { SearchIndex.build(all) }

    @Test
    fun blankOrPunctuationOnlyQueriesFindNothing() {
        assertEquals(emptyList<MediaItem>(), index.search(""))
        assertEquals(emptyList<MediaItem>(), index.search("   "))
        assertEquals(emptyList<MediaItem>(), index.search("_-./"))
    }

    @Test
    fun unknownWordFindsNothing() {
        assertEquals(emptyList<MediaItem>(), index.search("xylophone"))
    }

    @Test
    fun emptyLibraryFindsNothing() {
        assertEquals(emptyList<MediaItem>(), SearchIndex.build(emptyList()).search("camera"))
    }

    @Test
    fun everyQueryWordMustMatch() {
        assertEquals(listOf(3L), index.search("camera video").ids)
        assertEquals(listOf(1L), index.search("camera photo").ids)
        // One word matches, the other doesn't: nothing, not a partial match.
        assertEquals(emptyList<MediaItem>(), index.search("camera xylophone"))
    }

    @Test
    fun equalScoresComeNewestFirst() {
        // Both are exactly "camera" (folder); the 2024 photo is newer than the 2023 video.
        assertEquals(listOf(1L, 3L), index.search("camera").ids)
    }

    @Test
    fun betterMatchesOutrankNewerOnes() {
        val exactButOld = media(10, at(2010, 1, 1), "cam.jpg", "Pictures/")
        val prefixButNew = media(11, at(2025, 1, 1), "x.jpg", "DCIM/Camera/")
        val results = SearchIndex.build(listOf(exactButOld, prefixButNew)).search("cam")
        assertEquals(listOf(10L, 11L), results.ids)
    }

    @Test
    fun scoresAddUpAcrossQueryWords() {
        // Both match "camera" exactly; only one matches "2024" exactly (the other by a prefix of its file name).
        val exactYear = media(20, at(2024, 2, 2), "a.jpg", "DCIM/Camera/")
        val prefixYear = media(21, at(2025, 2, 2), "20240101.jpg", "DCIM/Camera/")
        assertEquals(listOf(20L, 21L), SearchIndex.build(listOf(prefixYear, exactYear)).search("camera 2024").ids)
    }

    @Test
    fun findsByMonthAndYearOfCapture() {
        assertEquals(listOf(1L), index.search("march 2024").ids)
        assertEquals(listOf(3L), index.search("june").ids)
    }

    @Test
    fun yearSearchFindsOnlyThatYear() {
        // A year is a number: one digit off is a different year, not a typo. Searching 2022 must
        // not also return 2020, 2021, 2023 and 2024 (the library has one photo from each).
        assertEquals(listOf(4L), index.search("2022").ids)
        assertEquals(listOf(5L), index.search("2021").ids)
    }

    @Test
    fun monthFollowsThePhonesTimeZone() {
        // 23:30 UTC on 31 January is already 1 February in Kolkata.
        val lateNight = LocalDateTime.of(2024, 1, 31, 23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val item = media(30, lateNight, "a.jpg", "Pictures/")
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val kolkata = SearchIndex.build(listOf(item))
        assertEquals(listOf(30L), kolkata.search("february").ids)
        assertEquals(emptyList<MediaItem>(), kolkata.search("january"))
    }

    @Test
    fun findsByKind() {
        assertEquals(listOf(3L), index.search("videos").ids)
        assertTrue("a video is not a photo", 3L !in index.search("photo").ids)
        assertEquals(setOf(1L, 2L, 4L, 5L, 6L), index.search("photos").ids.toSet())
        assertEquals(listOf(4L), index.search("gif").ids)
        assertEquals(listOf(5L), index.search("raw").ids)
        assertEquals(listOf(2L), index.search("screenshots").ids)
    }

    @Test
    fun favoritesInEitherSpelling() {
        for (word in listOf("favorite", "favorites", "favourite", "favourites")) {
            assertEquals(word, listOf(3L), index.search(word).ids)
        }
    }

    @Test
    fun findsByExtensionWhateverItsCase() {
        assertEquals(listOf(2L), index.search("png").ids)
        assertEquals(listOf(5L), index.search("dng").ids)
        assertEquals(listOf(5L), index.search("DNG").ids)
    }

    @Test
    fun findsByFolderAndFileNameParts() {
        assertEquals(listOf(5L), index.search("dsc 0042").ids)
        assertEquals(listOf(1L), index.search("20240312").ids)
        assertEquals(listOf(4L), index.search("download").ids)
    }

    @Test
    fun matchesAccentedLettersIgnoringCase() {
        assertEquals(listOf(6L), index.search("café").ids)
        assertEquals(listOf(6L), index.search("CAFÉ").ids)
        assertEquals(listOf(6L), index.search("paris dinner").ids)
    }

    @Test
    fun toleratesOneTypo() {
        assertEquals(listOf(1L, 3L), index.search("camra").ids)
        assertEquals(listOf(2L), index.search("screnshot").ids)
    }

    @Test
    fun eachItemComesBackOnce() {
        // "camera" is both the folder and the bucket name; "c" prefixes many words of the same item.
        for (query in listOf("camera", "c", "dcim camera camera")) {
            val ids = index.search(query).ids
            assertEquals(query, ids.distinct(), ids)
        }
    }

    @Test
    fun repeatingAWordDoesNotChangeTheOrder() {
        assertEquals(index.search("camera").ids, index.search("camera camera").ids)
    }
}
