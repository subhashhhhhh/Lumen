package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** [SearchIndex.matchScore] and [SearchIndex.terms] at their boundaries; complements SearchIndexTest. */
class SearchIndexMatchTest {

    private fun score(q: String, w: String) = SearchIndex.matchScore(q, w)

    @Test
    fun oneTypoOfEachKind() {
        assertEquals(1, score("camra", "camera")) // missing letter
        assertEquals(1, score("cammera", "camera")) // extra letter
        assertEquals(1, score("canera", "camera")) // wrong letter
        assertEquals(1, score("caemra", "camera")) // swapped neighbours
    }

    @Test
    fun twoTyposIsNoMatch() {
        assertEquals(0, score("cnmrea", "camera"))
        assertEquals(0, score("kamra", "camera"))
        assertEquals(0, score("cmaear", "camera"))
    }

    @Test
    fun typoNeedsFourLetters() {
        assertEquals(1, score("bech", "beach"))
        assertEquals(0, score("bxh", "bah")) // three letters: one typo is a third of the word
    }

    @Test
    fun matchingInsideAWordNeedsThreeLetters() {
        assertEquals(2, score("ach", "beach"))
        assertEquals(0, score("ch", "beach"))
    }

    @Test
    fun prefixesMatchAtAnyLength() {
        assertEquals(3, score("b", "beach"))
        assertEquals(3, score("be", "beach"))
    }

    @Test
    fun queryLongerThanTheWordIsNotAPrefix() {
        assertEquals(0, score("beaches", "beach"))
        assertEquals(1, score("beachs", "beach")) // one extra letter is a typo
    }

    @Test
    fun scoresAreOrderedExactPrefixInsideTypo() {
        val scores = listOf(score("snow", "snow"), score("sno", "snow"), score("now", "snow"), score("snwo", "snow"))
        assertEquals(listOf(4, 3, 2, 1), scores)
    }

    @Test
    fun termsHandleEdgeCases() {
        assertEquals(emptyList<String>(), SearchIndex.terms(""))
        assertEquals(emptyList<String>(), SearchIndex.terms("--__.."))
        assertEquals(listOf("a", "1", "b", "2", "c"), SearchIndex.terms("a1b2c"))
        assertEquals(listOf("pxl", "20240101", "mp"), SearchIndex.terms("PXL_20240101.MP"))
        assertEquals(listOf("über", "straße"), SearchIndex.terms("Über Straße"))
    }

    /** A Turkish phone must not lowercase "I" to a dotless "ı", or "img" would never match. */
    @Test
    fun termsAreLocaleIndependent() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("img", "1"), SearchIndex.terms("IMG_1"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
