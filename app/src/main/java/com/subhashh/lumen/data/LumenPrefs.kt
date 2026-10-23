package com.subhashh.lumen.data

import android.content.Context
import androidx.core.content.edit
import com.subhashh.lumen.ui.theme.Appearance

/**
 * Settings → Photos → Ultra HDR. Match Brightness lets highlights glow within what the screen
 * allows at your brightness; Full HDR asks for all the headroom the photo wants, which can raise
 * the whole screen's brightness (Android 15+), like Chrome.
 */
enum class HdrMode(val label: String) { Off("Off"), MatchBrightness("Match Brightness"), Full("Full HDR") }

/** Small persisted view preferences. */
class LumenPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    var libraryFilter: LibraryFilter
        get() = prefs.getString(KeyFilter, null)
            ?.let { name -> LibraryFilter.entries.firstOrNull { it.name == name } }
            ?: LibraryFilter.All
        set(value) = prefs.edit { putString(KeyFilter, value.name) }

    /** Show whole photos letterboxed in their cells instead of square crops. */
    var aspectRatioGrid: Boolean
        get() = prefs.getBoolean(KeyAspect, false)
        set(value) = prefs.edit { putBoolean(KeyAspect, value) }

    /** Grid columns, shared by the Library and albums. */
    var gridColumns: Int
        get() = prefs.getInt(KeyColumns, 5)
        set(value) = prefs.edit { putInt(KeyColumns, value) }

    /** Search results' columns, separate from the Library's. */
    var searchColumns: Int
        get() = prefs.getInt(KeySearchColumns, 4)
        set(value) = prefs.edit { putInt(KeySearchColumns, value) }

    /** Album columns, shared by Collections and Hidden's folder list. */
    var albumColumns: Int
        get() = prefs.getInt(KeyAlbumColumns, 2)
        set(value) = prefs.edit { putInt(KeyAlbumColumns, value) }

    /** Folders Lumen ignores (relative paths, subfolders included). */
    var excludedFolders: Set<String>
        get() = prefs.getStringSet(KeyExcluded, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KeyExcluded, value) }

    var appearance: Appearance
        get() = prefs.getString(KeyAppearance, null)
            ?.let { name -> Appearance.entries.firstOrNull { it.name == name } }
            ?: Appearance.System
        set(value) = prefs.edit { putString(KeyAppearance, value.name) }

    /** Album sort, pins, covers and still-empty new albums. Lines of tab-separated fields. */
    var albumSettings: AlbumSettings
        get() = AlbumSettings(
            sort = prefs.getString(KeyAlbumSort, null)?.let { name -> AlbumSort.entries.firstOrNull { it.name == name } }
                ?: AlbumSort.Recent,
            pinned = prefs.getString(KeyPinned, null)?.lines()?.filter { it.isNotEmpty() }.orEmpty(),
            covers = prefs.getString(KeyCovers, null)?.lines()?.mapNotNull { line ->
                val path = line.substringBefore('\t')
                line.substringAfter('\t', "").toLongOrNull()?.let { path to it }
            }?.toMap().orEmpty(),
            created = prefs.getString(KeyCreated, null)?.lines()?.filter { it.isNotEmpty() }.orEmpty(),
        )
        set(value) = prefs.edit {
            putString(KeyAlbumSort, value.sort.name)
            putString(KeyPinned, value.pinned.joinToString("\n"))
            putString(KeyCovers, value.covers.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            putString(KeyCreated, value.created.joinToString("\n"))
        }

    var librarySort: LibrarySort
        get() = prefs.getString(KeySort, null)?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }
            ?: LibrarySort.Date
        set(value) = prefs.edit { putString(KeySort, value.name) }

    var sortDescending: Boolean
        get() = prefs.getBoolean(KeySortDescending, false)
        set(value) = prefs.edit { putBoolean(KeySortDescending, value) }

    /** Videos start playing as soon as they're on screen. */
    var videoAutoplay: Boolean
        get() = prefs.getBoolean(KeyAutoplay, true)
        set(value) = prefs.edit { putBoolean(KeyAutoplay, value) }

    /** Videos start over when they end, instead of stopping. */
    var videoLoop: Boolean
        get() = prefs.getBoolean(KeyLoop, true)
        set(value) = prefs.edit { putBoolean(KeyLoop, value) }

    /** The mute button's last state, kept from video to video and across launches. */
    var videoMuted: Boolean
        get() = prefs.getBoolean(KeyMuted, false)
        set(value) = prefs.edit { putBoolean(KeyMuted, value) }

    /** How Ultra HDR photos are shown (Android 14+). */
    var hdrMode: HdrMode
        get() = prefs.getString(KeyHdrMode, null)?.let { name -> HdrMode.entries.firstOrNull { it.name == name } }
            ?: if (prefs.getBoolean(KeyHdr, true)) HdrMode.MatchBrightness else HdrMode.Off
        set(value) = prefs.edit { putString(KeyHdrMode, value.name) }

    /** Seconds each slideshow photo stays up. */
    var slideshowSeconds: Int
        get() = prefs.getInt(KeySlideSeconds, 3)
        set(value) = prefs.edit { putInt(KeySlideSeconds, value) }

    /** Slideshow crossfades instead of sliding. */
    var slideshowFade: Boolean
        get() = prefs.getBoolean(KeySlideFade, false)
        set(value) = prefs.edit { putBoolean(KeySlideFade, value) }

    var slideshowShuffle: Boolean
        get() = prefs.getBoolean(KeySlideShuffle, false)
        set(value) = prefs.edit { putBoolean(KeySlideShuffle, value) }

    /** Slideshow starts over at the end instead of stopping. */
    var slideshowRepeat: Boolean
        get() = prefs.getBoolean(KeySlideRepeat, false)
        set(value) = prefs.edit { putBoolean(KeySlideRepeat, value) }

    private companion object {
        const val KeyHdr = "hdr_photos"
        const val KeyHdrMode = "hdr_mode"
        const val KeySlideSeconds = "slideshow_seconds"
        const val KeySlideFade = "slideshow_fade"
        const val KeySlideShuffle = "slideshow_shuffle"
        const val KeySlideRepeat = "slideshow_repeat"
        const val KeyAutoplay = "video_autoplay"
        const val KeyLoop = "video_loop"
        const val KeyMuted = "video_muted"
        const val KeySort = "library_sort"
        const val KeySortDescending = "library_sort_descending"
        const val KeyAlbumSort = "album_sort"
        const val KeyPinned = "pinned_albums"
        const val KeyCovers = "album_covers"
        const val KeyCreated = "created_albums"
        const val KeyAppearance = "appearance"
        const val KeyExcluded = "excluded_folders"
        const val KeyColumns = "grid_columns"
        const val KeyAlbumColumns = "album_columns"
        const val KeySearchColumns = "search_columns"
        const val KeyFilter = "library_filter"
        const val KeyAspect = "aspect_ratio_grid"
    }
}
