package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExcludedFoldersTest {

    private fun at(path: String, id: Long = 1) = media(id, relativePath = path)

    @Test
    fun coversTheFolderAndEverythingInsideIt() {
        val excluded = setOf("Developer/Fastlytics/")
        assertTrue(ExcludedFolders.covers(excluded, at("Developer/Fastlytics/")))
        assertTrue(ExcludedFolders.covers(excluded, at("Developer/Fastlytics/dist/drivers/")))
    }

    @Test
    fun doesNotCoverFoldersThatOnlyStartTheSame() {
        val excluded = setOf("Developer/Fastlytics/")
        assertFalse(ExcludedFolders.covers(excluded, at("Developer/Fastlytics2/")))
        assertFalse(ExcludedFolders.covers(excluded, at("Developer/")))
        assertFalse(ExcludedFolders.covers(excluded, at("Other/Developer/Fastlytics/")))
    }

    @Test
    fun nothingExcludedCoversNothing() {
        assertFalse(ExcludedFolders.covers(emptySet(), at("DCIM/Camera/")))
    }

    @Test
    fun suggestsTheProjectAboveItsFirstCodeFolder() {
        val items = listOf(
            at("Developer/Fastlytics/dist/drivers/", 1),
            at("Developer/Fastlytics/dist/drivers/", 2),
            at("Developer/Fastlytics/public/", 3),
            at("Developer/Fastlytics/src/assets/icons/", 4),
            at("Developer/Site/node_modules/pkg/img/", 5),
            at("Developer/Site/node_modules/pkg/img/", 6),
            at("Developer/Old/build/", 7),
        )
        assertEquals(
            listOf(
                ExclusionSuggestion("Developer/Fastlytics/", 4),
                ExclusionSuggestion("Developer/Site/", 2),
                ExclusionSuggestion("Developer/Old/", 1),
            ),
            ExcludedFolders.suggestions(items, emptySet()),
        )
    }

    @Test
    fun codeFoldersAreRecognisedInAnyCase() {
        assertEquals(
            listOf(ExclusionSuggestion("Projects/App/", 1)),
            ExcludedFolders.suggestions(listOf(at("Projects/App/Build/outputs/")), emptySet()),
        )
    }

    @Test
    fun photoFoldersAreNeverSuggested() {
        val items = listOf(
            at("DCIM/Camera/"), at("Pictures/Screenshots/"), at("Movies/"),
            at("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"),
        )
        assertEquals(emptyList<ExclusionSuggestion>(), ExcludedFolders.suggestions(items, emptySet()))
    }

    @Test
    fun alreadyExcludedProjectsAreLeftOut() {
        val items = listOf(at("Developer/A/dist/", 1), at("Developer/B/dist/", 2))
        assertEquals(
            listOf(ExclusionSuggestion("Developer/B/", 1)),
            ExcludedFolders.suggestions(items, setOf("Developer/A/")),
        )
    }

    @Test
    fun aCodeFolderAtTheTopHasNoProjectToSuggest() {
        assertEquals(emptyList<ExclusionSuggestion>(), ExcludedFolders.suggestions(listOf(at("build/x/")), emptySet()))
    }

    /**
     * A suggestion excludes everything under it. One code-named folder inside a standard media
     * folder ("Download/dist/", "Pictures/Public/") must never get the whole of Download or
     * Pictures suggested, or a single tap hides most of someone's library.
     */
    @Test
    fun neverSuggestsAStandardMediaFolder() {
        val standard = setOf("DCIM/", "Pictures/", "Movies/", "Download/", "Documents/", "Downloads/")
        val items = listOf(
            at("Download/dist/", 1), at("Pictures/Public/", 2), at("DCIM/assets/", 3), at("Documents/static/", 4),
        )
        val suggested = ExcludedFolders.suggestions(items, emptySet()).map { it.folder }
        assertEquals(emptyList<String>(), suggested.filter { it in standard })
    }
}
