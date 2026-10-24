package com.subhashh.lumen.data

import com.subhashh.lumen.data.ai.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryPartsTest {
    private fun person(id: Long, name: String?) =
        Person(id, name, hidden = false, mediaIds = setOf(id), faceCount = 1, coverMediaId = id, coverBox = emptyList(), coverFaceId = id, coverInVideo = false)

    private val people = listOf(person(1, "Lewis Hamilton"), person(2, "Rosberg"), person(3, "Max Verstappen"), person(4, null))

    @Test fun phrasesIgnorePunctuation() {
        val p = QueryParts.phrases("\"so baby come light me up\"")!!
        assertTrue(QueryParts.contains("So baby, come light me up!", p))
        assertFalse(QueryParts.contains("so baby please come light me up", p))
    }

    @Test fun openQuoteMatchesPrefix() {
        val p = QueryParts.phrases("\"so baby co")!!
        assertTrue(p.open)
        assertTrue(QueryParts.contains("so baby come here", p))
    }

    @Test fun noQuotesNoPhrases() = assertNull(QueryParts.phrases("so baby"))

    @Test fun twoPeople() {
        assertEquals(listOf(1L, 2L), QueryParts.people("hamilton and rosberg", people)!!.map { it.id }.sorted())
        assertEquals(listOf(1L, 2L), QueryParts.people("lewis hamilton & rosberg", people)!!.map { it.id }.sorted())
        assertEquals(listOf(2L, 3L), QueryParts.people("verstappen with rosberg", people)!!.map { it.id }.sorted())
    }

    @Test fun onePersonOrOtherWordsAreNotAGroup() {
        assertNull(QueryParts.people("max verstappen", people))
        assertNull(QueryParts.people("hamilton", people))
        assertNull(QueryParts.people("hamilton cat", people))
    }
}
