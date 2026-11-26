package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Excluded folders with exceptions (#44, #45), on paths alone: plain JVM, so it also runs on the
 * build server. The deepest rule over a path decides, however the rules nest.
 */
class ExclusionRulesTest {

    @Test
    fun rulesNestAsDeepAsTheyGo() {
        val rules = Exclusions(
            excluded = setOf("Download/", "Download/Telegram/Telegram Images/Stickers/"),
            shown = setOf("Download/Telegram/", "Download/Telegram/Telegram Images/Stickers/Mine/"),
        )
        assertTrue(rules.covers("Download/"))
        assertTrue(rules.covers("Download/Other/"))
        assertFalse(rules.covers("Download/Telegram/"))
        assertFalse(rules.covers("Download/Telegram/Telegram Images/"))
        assertTrue(rules.covers("Download/Telegram/Telegram Images/Stickers/"))
        assertTrue(rules.covers("Download/Telegram/Telegram Images/Stickers/Old/"))
        assertFalse(rules.covers("Download/Telegram/Telegram Images/Stickers/Mine/"))
        assertFalse(rules.covers("Download/Telegram/Telegram Images/Stickers/Mine/2024/"))
    }

    @Test
    fun theOrderRulesWereAddedInDoesntMatter() {
        val paths = listOf("A/", "A/B/", "A/B/C/", "A/B/C/D/", "A/X/")
        val excluded = listOf("A/", "A/B/C/")
        val shown = listOf("A/B/", "A/B/C/D/")
        val one = Exclusions(LinkedHashSet(excluded), LinkedHashSet(shown))
        val other = Exclusions(LinkedHashSet(excluded.reversed()), LinkedHashSet(shown.reversed()))
        for (p in paths) assertEquals(p, one.covers(p), other.covers(p))
        assertEquals(listOf(true, false, true, false, true), paths.map { one.covers(it) })
    }

    @Test
    fun aFolderBothExcludedAndShownStaysHidden() {
        // Can only come from an old or edited backup; hidden is the safe reading.
        val rules = Exclusions(excluded = setOf("Private/"), shown = setOf("Private/"))
        assertTrue(rules.covers("Private/"))
        assertTrue(rules.covers("Private/Sub/"))
    }

    @Test
    fun foldersThatOnlyStartTheSameAreSeparate() {
        val rules = Exclusions(excluded = setOf("Download/Tele/"), shown = setOf("Download/Tele/Keep/"))
        assertFalse(rules.covers("Download/Telegram/"))
        assertTrue(rules.covers("Download/Tele/Keeper/"))
        assertFalse(rules.covers("Download/Tele/Keep/"))
    }

    @Test
    fun theRuleNamedIsTheDeepestExcludedOneThatApplies() {
        val rules = Exclusions(excluded = setOf("A/", "A/B/C/"), shown = setOf("A/B/"))
        assertEquals("A/", rules.ruleOver("A/X/"))
        assertEquals("A/B/C/", rules.ruleOver("A/B/C/D/"))
        // Shown anyway: nothing hides it, so no rule is named.
        assertNull(rules.ruleOver("A/B/"))
        assertNull(rules.ruleOver("A/B/Y/"))
        assertNull(rules.ruleOver("Elsewhere/"))
    }

    @Test
    fun shownFoldersAloneHideNothing() {
        val rules = Exclusions(shown = setOf("A/", "B/"))
        assertTrue(rules.isEmpty)
        assertFalse(rules.covers("A/"))
        assertFalse(rules.covers("C/"))
        assertNull(rules.ruleOver("A/"))
    }

    @Test
    fun theTopOfTheStorageIsNeverCoveredByAFolderRule() {
        // Files right at the top have an empty relative path.
        val rules = Exclusions(excluded = setOf("A/"))
        assertFalse(rules.covers(""))
    }
}
