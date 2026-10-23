package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    @Test
    fun splitsFileNamesIntoWords() {
        assertEquals(listOf("img", "20240312", "101500"), SearchIndex.terms("IMG_20240312_101500"))
        assertEquals(listOf("img", "20240312"), SearchIndex.terms("IMG20240312"))
        assertEquals(
            listOf("screenshot", "2024", "03", "12", "com", "whatsapp"),
            SearchIndex.terms("Screenshot_2024-03-12_com.whatsapp"),
        )
    }

    @Test
    fun matchesExactPrefixInsideAndTypos() {
        assertEquals(4, SearchIndex.matchScore("camera", "camera"))
        assertEquals(3, SearchIndex.matchScore("whats", "whatsapp"))
        assertEquals(2, SearchIndex.matchScore("app", "whatsapp"))
        assertEquals(1, SearchIndex.matchScore("camrea", "camera")) // swapped letters
        assertEquals(1, SearchIndex.matchScore("screnshot", "screenshots")) // typo in a prefix
        assertEquals(3, SearchIndex.matchScore("whatsap", "whatsapp")) // just a shorter prefix
        assertEquals(1, SearchIndex.matchScore("whatsaap", "whatsapp"))
    }

    @Test
    fun shortWordsStayStrict() {
        assertEquals(0, SearchIndex.matchScore("ap", "whatsapp")) // too short to match inside
        assertEquals(0, SearchIndex.matchScore("cat", "car")) // too short for a typo
        assertTrue(SearchIndex.matchScore("2024", "20240312") > 0)
        assertEquals(0, SearchIndex.matchScore("march", "may"))
    }
}
