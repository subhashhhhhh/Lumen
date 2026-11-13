package com.subhashh.lumen.ui.search

import com.subhashh.lumen.data.ids
import com.subhashh.lumen.data.media
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Search results by date (#11): the one list's order, and that only the one list is reordered. */
@RunWith(RobolectricTestRunner::class)
class SearchOrderTest {

    // Best-match order as Search hands it over: deliberately not by date.
    private val best = listOf(media(3, takenAt = 3_000), media(1, takenAt = 1_000), media(5, takenAt = 5_000), media(2, takenAt = 2_000), media(4, takenAt = 4_000))

    @Before
    fun reset() {
        SearchOrder.current = SearchOrder.Best
    }

    @After
    fun restore() {
        SearchOrder.current = SearchOrder.Best
    }

    @Test
    fun bestKeepsTheMatchOrder() {
        assertEquals(listOf(3L, 1L, 5L, 2L, 4L), SearchOrder.Best.sorted(best).ids)
    }

    @Test
    fun newestAndOldest() {
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), SearchOrder.Newest.sorted(best).ids)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), SearchOrder.Oldest.sorted(best).ids)
    }

    @Test
    fun sortingDoesNotChangeTheInput() {
        SearchOrder.Newest.sorted(best)
        assertEquals(listOf(3L, 1L, 5L, 2L, 4L), best.ids)
    }

    @Test
    fun emptyAndSingle() {
        for (order in SearchOrder.entries) {
            assertEquals(emptyList<Long>(), order.sorted(emptyList()).ids)
            assertEquals(listOf(9L), order.sorted(listOf(media(9))).ids)
        }
    }

    @Test
    fun undatedItemsGoLastWhenNewestFirst() {
        // takenAt 0: MediaStore had no date. It is the oldest possible, so it sorts to the end.
        val items = listOf(media(1, takenAt = 0), media(2, takenAt = 2_000), media(3, takenAt = 1_000))
        assertEquals(listOf(2L, 3L, 1L), SearchOrder.Newest.sorted(items).ids)
        assertEquals(listOf(1L, 3L, 2L), SearchOrder.Oldest.sorted(items).ids)
    }

    /**
     * Burst shots and edited copies often share a timestamp. The Library breaks such ties by id
     * (LibrarySort.Date: takenAt, then id), so the same photos stand in the same order everywhere,
     * and Newest is exactly Oldest backwards. Search keeps their match order instead, so a burst
     * reads one way in the Library and another way in Search, and flipping Newest/Oldest doesn't
     * mirror the list.
     */
    @Test
    fun sameMomentPhotosKeepTheLibrarysOrder() {
        val burst = listOf(media(12, takenAt = 7_000), media(10, takenAt = 7_000), media(11, takenAt = 7_000), media(1, takenAt = 1_000))
        assertEquals(listOf(1L, 10L, 11L, 12L), SearchOrder.Oldest.sorted(burst).ids)
        assertEquals(listOf(12L, 11L, 10L, 1L), SearchOrder.Newest.sorted(burst).ids)
    }

    @Test
    fun onlyTheOneListIsReordered() {
        SearchOrder.current = SearchOrder.Oldest
        for (id in listOf(SearchSection.Names, SearchSection.People, SearchSection.Text, SearchSection.Smart)) {
            val section = SearchSection(id, 0, best)
            assertSame(id, section, section.ordered())
        }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), SearchSection(SearchSection.All, 0, best).ordered().items.ids)
    }

    @Test
    fun bestLeavesTheOneListAsItIs() {
        val section = SearchSection(SearchSection.All, 0, best)
        assertSame(section, section.ordered())
    }

    @Test
    fun reorderingKeepsTheScoresAndTitle() {
        SearchOrder.current = SearchOrder.Newest
        val scores = best.associate { it.id to it.id / 10f }
        val ordered = SearchSection(SearchSection.All, 7, best, scores).ordered()
        assertEquals(scores, ordered.scores)
        assertEquals(7, ordered.title)
        assertEquals(best.toSet(), ordered.items.toSet())
    }

    @Test
    fun tappingCyclesThroughAllThreeAndBack() {
        // The same step SearchScreen's header takes on a tap.
        val seen = mutableListOf<SearchOrder>()
        repeat(4) {
            seen += SearchOrder.current
            SearchOrder.current = SearchOrder.entries[(SearchOrder.current.ordinal + 1) % SearchOrder.entries.size]
        }
        assertEquals(listOf(SearchOrder.Best, SearchOrder.Newest, SearchOrder.Oldest, SearchOrder.Best), seen)
    }
}
