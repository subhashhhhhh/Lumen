package com.subhashh.lumen.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CaptionStoreTest {

    private lateinit var store: CaptionStore

    // CaptionStore is a process-wide singleton; each test gets a fresh one on a fresh database.
    private fun resetSingleton() {
        val field = CaptionStore::class.java.getDeclaredField("open").apply { isAccessible = true }
        (field.get(null) as CaptionStore?)?.close()
        field.set(null, null)
    }

    @Before
    fun open() {
        resetSingleton()
        store = CaptionStore.of(RuntimeEnvironment.getApplication())
    }

    @After
    fun close() = resetSingleton()

    @Test
    fun noCaptionIsNull() {
        assertNull(store.get(1))
    }

    @Test
    fun setTrimsAndReplaces() {
        store.set(1, "  Lunch in Rome  ")
        assertEquals("Lunch in Rome", store.get(1))
        store.set(1, "Dinner in Paris")
        assertEquals("Dinner in Paris", store.get(1))
        assertNull(store.get(2))
    }

    @Test
    fun blankRemoves() {
        store.set(1, "Something")
        store.set(1, " \n\t ")
        assertNull(store.get(1))
        store.set(2, "") // removing what isn't there is fine
        assertNull(store.get(2))
    }

    @Test
    fun keepsTextExactly() {
        val text = "It's \"quoted\"; DROP TABLE captions; -- 東京 🍜\nsecond line"
        store.set(7, text)
        assertEquals(text, store.get(7))
        assertEquals(listOf(7L), store.search("drop table"))
    }

    @Test
    fun searchNeedsEveryWordInAnyOrderAnyCase() {
        store.set(1, "Birthday cake at grandma's")
        store.set(2, "Cake shop")
        store.set(3, "Grandma in the garden")
        assertEquals(listOf(1L), store.search("CAKE grandma"))
        assertEquals(setOf(1L, 2L), store.search("cake").toSet())
        assertEquals(emptyList<Long>(), store.search("cake garden"))
    }

    @Test
    fun searchIgnoresExtraSpacesAndBlankQueries() {
        store.set(1, "Beach day")
        assertEquals(listOf(1L), store.search("  beach   day  "))
        assertEquals(emptyList<Long>(), store.search(""))
        assertEquals(emptyList<Long>(), store.search("   "))
    }

    @Test
    fun searchLowercasesAccentedLetters() {
        store.set(1, "ÉTÉ À PARIS")
        assertEquals(listOf(1L), store.search("été à paris"))
    }

    @Test
    fun removedCaptionsAreNotFound() {
        store.set(1, "Beach")
        store.set(1, "")
        assertEquals(emptyList<Long>(), store.search("beach"))
    }

    @Test
    fun manyItemsKeepTheirOwnCaptions() {
        for (id in 1L..200L) store.set(id, "caption $id")
        assertEquals("caption 137", store.get(137))
        assertEquals(listOf(137L), store.search("137"))
        assertEquals(200, store.search("caption").size)
    }

    @Test
    fun captionsSurviveReopening() {
        store.set(5, "Kept")
        resetSingleton()
        assertEquals("Kept", CaptionStore.of(RuntimeEnvironment.getApplication()).get(5))
    }
}
