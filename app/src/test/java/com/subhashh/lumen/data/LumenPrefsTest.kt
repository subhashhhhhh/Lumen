package com.subhashh.lumen.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LumenPrefsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = LumenPrefs(context)
    private val raw = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    @Test
    fun freshInstallDefaults() {
        assertEquals(LibraryFilter.All, prefs.libraryFilter)
        assertEquals(LibrarySort.Date, prefs.librarySort)
        assertFalse(prefs.sortDescending)
        assertEquals(5, prefs.gridColumns)
        assertEquals(emptyList<String>(), prefs.recentSearches)
        assertEquals(emptySet<String>(), prefs.excludedFolders)
        assertEquals(AlbumSettings(), prefs.albumSettings)
        assertEquals(0, prefs.daysUsed)
        assertFalse("Hidden is never encrypted unless chosen", prefs.encryptHidden)
        assertFalse("the app lock is off unless chosen", prefs.appLock)
    }

    @Test
    fun everySettingSurvivesANewInstance() {
        prefs.libraryFilter = LibraryFilter.Screenshots
        prefs.librarySort = LibrarySort.Size
        prefs.sortDescending = true
        prefs.gridColumns = 3
        prefs.excludedFolders = setOf("Developer/A/", "Developer/B/")
        val again = LumenPrefs(context)
        assertEquals(LibraryFilter.Screenshots, again.libraryFilter)
        assertEquals(LibrarySort.Size, again.librarySort)
        assertTrue(again.sortDescending)
        assertEquals(3, again.gridColumns)
        assertEquals(setOf("Developer/A/", "Developer/B/"), again.excludedFolders)
    }

    @Test
    fun unknownEnumNamesFallBackToDefaults() {
        // A setting renamed or removed in a later version, or a hand-edited backup.
        raw.edit()
            .putString("library_filter", "Selfies")
            .putString("library_sort", "")
            .putString("album_sort", "Colour")
            .commit()
        assertEquals(LibraryFilter.All, prefs.libraryFilter)
        assertEquals(LibrarySort.Date, prefs.librarySort)
        assertEquals(AlbumSort.Recent, prefs.albumSettings.sort)
    }

    @Test
    fun recentSearchesNewestFirstWithoutRepeats() {
        prefs.addRecentSearch("beach")
        prefs.addRecentSearch("dog")
        prefs.addRecentSearch("Beach") // same search, different case: moves to the front, new spelling
        assertEquals(listOf("Beach", "dog"), prefs.recentSearches)
    }

    @Test
    fun recentSearchesKeepEightAtMost() {
        for (i in 1..12) prefs.addRecentSearch("q$i")
        assertEquals((12 downTo 5).map { "q$it" }, prefs.recentSearches)
    }

    @Test
    fun recentSearchesIgnoreBlankAndFlattenNewlines() {
        prefs.addRecentSearch("   ")
        prefs.addRecentSearch("")
        assertEquals(emptyList<String>(), prefs.recentSearches)
        prefs.addRecentSearch("  red\ncar  ")
        assertEquals(listOf("red car"), prefs.recentSearches)
    }

    @Test
    fun albumSettingsRoundTrip() {
        val settings = AlbumSettings(
            sort = AlbumSort.Count,
            reversed = true,
            pinned = listOf("Pictures/Z/", "DCIM/Camera/", "Pictures/A/"),
            covers = mapOf("DCIM/Camera/" to 123L, "Pictures/Café Paris/" to Long.MAX_VALUE),
            created = listOf("Pictures/New/", "Pictures/Ünïcode album/"),
        )
        prefs.albumSettings = settings
        assertEquals(settings, LumenPrefs(context).albumSettings)
    }

    @Test
    fun albumSettingsAreReplacedNotMerged() {
        prefs.albumSettings = AlbumSettings(pinned = listOf("A/"), covers = mapOf("A/" to 1L), created = listOf("B/"))
        prefs.albumSettings = AlbumSettings()
        assertEquals(AlbumSettings(), prefs.albumSettings)
    }

    @Test
    fun damagedCoverLinesAreSkipped() {
        raw.edit().putString("album_covers", "DCIM/Camera/\t12\nno-tab-here\nPictures/\tnot-a-number\n\nDCIM/X/\t7").commit()
        assertEquals(mapOf("DCIM/Camera/" to 12L, "DCIM/X/" to 7L), prefs.albumSettings.covers)
    }

    @Test
    fun excludedFoldersAreACopy() {
        prefs.excludedFolders = setOf("A/")
        val read = prefs.excludedFolders
        // Changing what was read (if it's mutable at all) must not change what's stored.
        runCatching { (read as MutableSet<String>).add("B/") }
        assertEquals(setOf("A/"), LumenPrefs(context).excludedFolders)
    }

    @Test
    fun aDayIsCountedOnce() {
        prefs.countDayUsed(today = 20_000)
        prefs.countDayUsed(today = 20_000)
        prefs.countDayUsed(today = 20_000)
        assertEquals(1, prefs.daysUsed)
        assertEquals(20_000L, prefs.firstDayUsed)
    }

    @Test
    fun laterDaysAddUpAndTheFirstDayStays() {
        prefs.countDayUsed(today = 20_000)
        prefs.countDayUsed(today = 20_001)
        prefs.countDayUsed(today = 20_005)
        assertEquals(3, prefs.daysUsed)
        assertEquals(20_000L, prefs.firstDayUsed)
    }

    @Test
    fun nullableSettingsStartUnset() {
        assertNull(prefs.availableUpdate)
        assertNull(prefs.announcedUpdate)
    }
}
