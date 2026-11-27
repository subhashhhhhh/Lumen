package com.subhashh.lumen.ui.collections

import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Utility tiles show their newest photo (beda267), except two: Hidden, whose photos are private
 * (a tile in the plain Collections list would show one to anyone holding the phone), and Recently
 * Deleted, whose photos were deleted on purpose.
 */
@RunWith(RobolectricTestRunner::class)
class UtilityThumbnailTest {

    private val photo = media(42)

    private fun tile(key: String, cover: com.subhashh.lumen.data.MediaItem? = photo, count: Int = 3) =
        MediaCollection(key = key, title = key, cover = cover, count = count)

    @Test
    fun hiddenNeverShowsAPhoto() {
        assertNull(utilityThumbnail(tile(CollectionKeys.Hidden)))
        // Locked or not, counted or not.
        assertNull(utilityThumbnail(tile(CollectionKeys.Hidden, count = com.subhashh.lumen.data.MediaCollections.LockedCount)))
    }

    @Test
    fun recentlyDeletedNeverShowsAPhoto() {
        assertNull(utilityThumbnail(tile(CollectionKeys.RecentlyDeleted)))
    }

    @Test
    fun theOtherUtilitiesShowTheirNewest() {
        for (key in listOf(CollectionKeys.Favorites, CollectionKeys.Videos, CollectionKeys.Screenshots, CollectionKeys.Documents)) {
            assertEquals(key, photo, utilityThumbnail(tile(key)))
        }
    }

    @Test
    fun anEmptyUtilityHasNoPhoto() {
        assertNull(utilityThumbnail(tile(CollectionKeys.Favorites, cover = null, count = 0)))
    }
}
