package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings → Excluded Folders: the folder tree to browse (#44) and the suggestions beside it. */
@RunWith(RobolectricTestRunner::class)
class FolderBrowsingTest {

    private var next = 1L
    private fun at(path: String, n: Int = 1) = List(n) { media(next++, relativePath = path) }

    @Test
    fun everyParentIsThereWithEverythingBelowItCounted() {
        val tree = FolderTree.of(at("Download/Telegram/Telegram Images/", 3) + at("Download/", 2) + at("DCIM/Camera/", 4))
        assertEquals(5, tree.counts["Download/"])
        assertEquals(3, tree.counts["Download/Telegram/"])
        assertEquals(3, tree.counts["Download/Telegram/Telegram Images/"])
        assertEquals(4, tree.counts["DCIM/"])
        // A parent holding only folders is still there to open.
        assertEquals(listOf("Download/Telegram/"), tree.children("Download/"))
        assertEquals(listOf("Download/Telegram/Telegram Images/"), tree.children("Download/Telegram/"))
        assertEquals(emptyList<String>(), tree.children("Download/Telegram/Telegram Images/"))
    }

    @Test
    fun theTopListsOnlyTopFoldersByNameIgnoringCase() {
        val tree = FolderTree.of(at("pictures/") + at("DCIM/Camera/") + at("Movies/") + at("aves/x/"))
        assertEquals(listOf("aves/", "DCIM/", "Movies/", "pictures/"), tree.children(""))
    }

    @Test
    fun foldersAreOrderedByTheirOwnNameNotTheirPath() {
        val tree = FolderTree.of(at("A/zeta/") + at("A/Beta/") + at("A/alpha/"))
        assertEquals(listOf("A/alpha/", "A/Beta/", "A/zeta/"), tree.children("A/"))
    }

    @Test
    fun filesAtTheTopOfStorageAreLeftOut() {
        val tree = FolderTree.of(at("", 3) + at("DCIM/"))
        assertEquals(listOf("DCIM/"), tree.children(""))
        assertFalse(tree.counts.containsKey(""))
    }

    @Test
    fun aFolderAppearsOnceHoweverManyPhotosItHas() {
        val tree = FolderTree.of(at("DCIM/Camera/", 50))
        assertEquals(listOf("DCIM/"), tree.children(""))
        assertEquals(listOf("DCIM/Camera/"), tree.children("DCIM/"))
    }

    @Test
    fun excludingAFolderFromTheBrowserCoversWhatTheTreeCountsUnderIt() {
        val items = at("Download/Telegram/Telegram Images/", 3) + at("Download/Telegram/Telegram Video/", 2) + at("Download/", 1)
        val tree = FolderTree.of(items)
        val rules = Exclusions(excluded = setOf("Download/Telegram/"), shown = setOf("Download/Telegram/Telegram Images/"))
        val hidden = items.count { rules.covers(it) }
        // What's hidden = the excluded folder's count minus the shown subfolder's.
        assertEquals(tree.counts.getValue("Download/Telegram/") - tree.counts.getValue("Download/Telegram/Telegram Images/"), hidden)
    }

    // --- Suggestions ---

    @Test
    fun aFolderShownAnywayIsNeverSuggestedForExcluding() {
        // Developer/ is excluded, but the person chose to see Developer/App/ anyway. Its icons live
        // in Developer/App/src/…: suggesting Developer/App/ again, and so undoing that choice with
        // one tap (exclude() drops it from the shown list), mustn't happen.
        val items = at("Developer/App/src/main/res/", 12) + at("Developer/Other/", 2)
        val rules = Exclusions(excluded = setOf("Developer/"), shown = setOf("Developer/App/"))
        val suggested = ExcludedFolders.suggestions(items, rules).map { it.folder }
        assertFalse("suggested $suggested", "Developer/App/" in suggested)
        for (folder in suggested) assertFalse("$folder would hide the shown Developer/App/", "Developer/App/".startsWith(folder))
    }

    @Test
    fun suggestionsLeaveOutWhatIsAlreadyHidden() {
        val items = at("Developer/App/build/icons/", 5) + at("Projects/Site/dist/", 3)
        val rules = Exclusions(excluded = setOf("Developer/"))
        val suggested = ExcludedFolders.suggestions(items, rules)
        assertEquals(listOf("Projects/Site/"), suggested.map { it.folder })
        assertEquals(3, suggested.single().itemCount)
    }

    @Test
    fun suggestionsComeMostItemsFirst() {
        val items = at("A/src/", 2) + at("B/build/", 7) + at("C/node_modules/x/", 4)
        assertEquals(listOf("B/", "C/", "A/"), ExcludedFolders.suggestions(items, Exclusions()).map { it.folder })
        assertTrue(ExcludedFolders.suggestions(emptyList(), Exclusions()).isEmpty())
    }
}
