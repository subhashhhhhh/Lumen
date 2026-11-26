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
        assertTrue(Exclusions(excluded).covers(at("Developer/Fastlytics/")))
        assertTrue(Exclusions(excluded).covers(at("Developer/Fastlytics/dist/drivers/")))
    }

    @Test
    fun doesNotCoverFoldersThatOnlyStartTheSame() {
        val excluded = setOf("Developer/Fastlytics/")
        assertFalse(Exclusions(excluded).covers(at("Developer/Fastlytics2/")))
        assertFalse(Exclusions(excluded).covers(at("Developer/")))
        assertFalse(Exclusions(excluded).covers(at("Other/Developer/Fastlytics/")))
    }

    @Test
    fun nothingExcludedCoversNothing() {
        assertFalse(Exclusions().covers(at("DCIM/Camera/")))
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
            ExcludedFolders.suggestions(items, Exclusions()),
        )
    }

    @Test
    fun codeFoldersAreRecognisedInAnyCase() {
        assertEquals(
            listOf(ExclusionSuggestion("Projects/App/", 1)),
            ExcludedFolders.suggestions(listOf(at("Projects/App/Build/outputs/")), Exclusions()),
        )
    }

    @Test
    fun photoFoldersAreNeverSuggested() {
        val items = listOf(
            at("DCIM/Camera/"), at("Pictures/Screenshots/"), at("Movies/"),
            at("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"),
        )
        assertEquals(emptyList<ExclusionSuggestion>(), ExcludedFolders.suggestions(items, Exclusions()))
    }

    @Test
    fun alreadyExcludedProjectsAreLeftOut() {
        val items = listOf(at("Developer/A/dist/", 1), at("Developer/B/dist/", 2))
        assertEquals(
            listOf(ExclusionSuggestion("Developer/B/", 1)),
            ExcludedFolders.suggestions(items, Exclusions(setOf("Developer/A/"))),
        )
    }

    @Test
    fun aCodeFolderAtTheTopHasNoProjectToSuggest() {
        assertEquals(emptyList<ExclusionSuggestion>(), ExcludedFolders.suggestions(listOf(at("build/x/")), Exclusions()))
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
        val suggested = ExcludedFolders.suggestions(items, Exclusions()).map { it.folder }
        assertEquals(emptyList<String>(), suggested.filter { it in standard })
    }

    /** #45: exclude Download/Telegram/ but keep Telegram Images in it. The deepest rule wins. */
    @Test
    fun aFolderShownAnywayInsideAnExcludedOneIsShown() {
        val rules = Exclusions(excluded = setOf("Download/Telegram/"), shown = setOf("Download/Telegram/Telegram Images/"))
        assertTrue(rules.covers(at("Download/Telegram/")))
        assertTrue(rules.covers(at("Download/Telegram/Telegram Video/")))
        assertFalse(rules.covers(at("Download/Telegram/Telegram Images/")))
        assertFalse(rules.covers(at("Download/Telegram/Telegram Images/2026/")))
        assertFalse(rules.covers(at("Download/")))
    }

    @Test
    fun anExclusionInsideAShownFolderHidesAgain() {
        val rules = Exclusions(excluded = setOf("Download/", "Download/Keep/Junk/"), shown = setOf("Download/Keep/"))
        assertTrue(rules.covers(at("Download/Other/")))
        assertFalse(rules.covers(at("Download/Keep/")))
        assertTrue(rules.covers(at("Download/Keep/Junk/")))
        assertEquals("Download/Keep/Junk/", rules.ruleOver("Download/Keep/Junk/x/"))
        assertEquals(null, rules.ruleOver("Download/Keep/"))
    }

    @Test
    fun aShownFolderOutsideAnyExcludedOneChangesNothing() {
        assertFalse(Exclusions(shown = setOf("DCIM/")).covers(at("DCIM/Camera/")))
    }

    /** #44: parents that hold only folders are there to browse and exclude, with counts below them. */
    @Test
    fun theFolderTreeHasParentsAndCountsWhatsBelow() {
        val tree = FolderTree.of(listOf(at("Download/Telegram/Images/", 1), at("Download/Telegram/Video/", 2), at("DCIM/Camera/", 3)))
        assertEquals(listOf("DCIM/", "Download/"), tree.children(""))
        assertEquals(listOf("Download/Telegram/"), tree.children("Download/"))
        assertEquals(listOf("Download/Telegram/Images/", "Download/Telegram/Video/"), tree.children("Download/Telegram/"))
        assertEquals(2, tree.counts["Download/"])
        assertEquals(1, tree.counts["Download/Telegram/Video/"])
    }
}
