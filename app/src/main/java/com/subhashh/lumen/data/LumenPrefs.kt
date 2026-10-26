package com.subhashh.lumen.data

import com.subhashh.lumen.R
import androidx.annotation.StringRes
import com.subhashh.lumen.ui.library.SectionMode
import android.content.Context
import androidx.core.content.edit
import com.subhashh.lumen.ui.theme.Appearance

/**
 * Settings → Photos → Ultra HDR. Match Brightness lets highlights glow within what the screen
 * allows at your brightness; Full HDR turns the screen all the way up while an HDR photo or
 * video is shown, like Chrome.
 */
enum class HdrMode(@StringRes val label: Int) { Off(R.string.common_off), MatchBrightness(R.string.data_hdr_match), Full(R.string.data_hdr_full) }

/** The viewer's film strip: its height, in dp; Off hides it. */
enum class FilmStripSize(@StringRes val label: Int, val heightDp: Int) { Off(R.string.common_off, 0), Small(R.string.data_size_small, 32), Medium(R.string.data_size_medium, 42), Large(R.string.data_size_large, 56) }

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

    /** Recent searches, newest first (at most [MaxRecentSearches]). */
    var recentSearches: List<String>
        get() = prefs.getString(KeyRecentSearches, null)?.lines()?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit { putString(KeyRecentSearches, value.take(MaxRecentSearches).joinToString("\n")) }

    fun addRecentSearch(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        recentSearches = listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) }
    }

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

    var filmStrip: FilmStripSize
        get() = prefs.getString(KeyFilmStrip, null)?.let { name -> FilmStripSize.entries.firstOrNull { it.name == name } } ?: FilmStripSize.Medium
        set(value) = prefs.edit { putString(KeyFilmStrip, value.name) }

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

    /** Settings → Appearance → Liquid Glass: glass that bends what's behind it. */
    var liquidGlass: Boolean
        get() = prefs.getBoolean(KeyLiquidGlass, false)
        set(value) = prefs.edit { putBoolean(KeyLiquidGlass, value) }

    /** Settings → Appearance → Reduce Blur: solid fills instead of frosted glass. */
    var reduceBlur: Boolean
        get() = prefs.getBoolean(KeyReduceBlur, false)
        set(value) = prefs.edit { putBoolean(KeyReduceBlur, value) }

    /** Reduce Motion as chosen in Settings; null until the user picks (then Android's setting decides). */
    var reduceMotion: Boolean?
        get() = if (prefs.contains(KeyReduceMotion)) prefs.getBoolean(KeyReduceMotion, false) else null
        set(value) = prefs.edit { if (value == null) remove(KeyReduceMotion) else putBoolean(KeyReduceMotion, value) }

    /** Date headers in photo grids (Settings → Library). */
    var sectionMode: SectionMode
        get() = prefs.getString(KeySections, null)?.let { name -> SectionMode.entries.firstOrNull { it.name == name } }
            ?: SectionMode.Months
        set(value) = prefs.edit { putString(KeySections, value.name) }

    /** The phone's screen curves over its sides: keep edge controls off the curve. */
    /** Smart Search's tier (an AiModel id); null means the default, Accurate. */
    var searchModel: String?
        get() = prefs.getString(KeySearchModel, null)
        set(value) = prefs.edit { putString(KeySearchModel, value) }

    /** Smart Search model download waits for an unmetered network. */
    var modelWifiOnly: Boolean
        get() = prefs.getBoolean(KeyModelWifiOnly, true)
        set(value) = prefs.edit { putBoolean(KeyModelWifiOnly, value) }

    /** Details → Location shows a map (tiles from OpenFreeMap); off: place name and coordinates only. */
    var showMaps: Boolean
        get() = prefs.getBoolean(KeyShowMaps, true)
        set(value) = prefs.edit { putBoolean(KeyShowMaps, value) }

    /**
     * Settings → Smart Features → Scheduled Indexing: the backlog runs only between
     * [indexScheduleStart] and [indexScheduleEnd] (minutes after midnight; may cross it), on
     * the charger or not.
     */
    var indexScheduleOn: Boolean
        get() = prefs.getBoolean(KeyIndexScheduleOn, false)
        set(value) = prefs.edit { putBoolean(KeyIndexScheduleOn, value) }
    var indexScheduleStart: Int
        get() = prefs.getInt(KeyIndexScheduleStart, 60)
        set(value) = prefs.edit { putInt(KeyIndexScheduleStart, value) }
    var indexScheduleEnd: Int
        get() = prefs.getInt(KeyIndexScheduleEnd, 7 * 60)
        set(value) = prefs.edit { putInt(KeyIndexScheduleEnd, value) }

    /** Onboarding finished; null before this version recorded it (existing users decide by access). */
    var onboardingDone: Boolean?
        get() = if (prefs.contains(KeyOnboardingDone)) prefs.getBoolean(KeyOnboardingDone, false) else null
        set(value) = prefs.edit { if (value == null) remove(KeyOnboardingDone) else putBoolean(KeyOnboardingDone, value) }

    /** Before Lumen AI kept it: which way videos' text was read. Only read once, to hand over. */
    val legacyTextVideoVersion: Int
        get() = prefs.getInt(KeyTextVideoVersion, 1)

    /** About → Software Update → Check Automatically: once a day, on launch. */
    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean(KeyAutoUpdate, true)
        set(value) = prefs.edit { putBoolean(KeyAutoUpdate, value) }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KeyLastUpdateCheck, 0L)
        set(value) = prefs.edit { putLong(KeyLastUpdateCheck, value) }

    /** A newer version found by the last check, shown at the top of Settings; null when none. */
    var availableUpdate: String?
        get() = prefs.getString(KeyAvailableUpdate, null)
        set(value) = prefs.edit { putString(KeyAvailableUpdate, value) }

    /** The newest version the update popup has shown, so each version is announced once. */
    var announcedUpdate: String?
        get() = prefs.getString(KeyAnnouncedUpdate, null)
        set(value) = prefs.edit { putString(KeyAnnouncedUpdate, value) }

    /** Settings → Developer Options is shown (tapping Version seven times in About). */
    var developerOptions: Boolean
        get() = prefs.getBoolean(KeyDeveloperOptions, false)
        set(value) = prefs.edit { putBoolean(KeyDeveloperOptions, value) }

    /** Developer Options → Search Details: results split by how they matched, with scores. */
    var searchDetails: Boolean
        get() = prefs.getBoolean(KeySearchDetails, false)
        set(value) = prefs.edit { putBoolean(KeySearchDetails, value) }

    /** Before Lumen AI kept it: packs waiting to download. Only read once, to hand over. */
    val legacyModelQueue: List<String>
        get() = prefs.getString(KeyModelQueue, null)?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()

    /** Lumen AI moved out of the app (25 Oct 2026): background work queued before then is redone once. */
    var aiMoved: Boolean
        get() = prefs.getBoolean(KeyAiMoved, false)
        set(value) = prefs.edit { putBoolean(KeyAiMoved, value) }

    var curvedEdge: Boolean
        get() = prefs.getBoolean(KeyCurvedEdge, false)
        set(value) = prefs.edit { putBoolean(KeyCurvedEdge, value) }

    /** Settings → Smart Search → Video Detail: frame size for faces (and text) in videos. */
    var videoDetail: VideoDetail
        get() = VideoDetail.entries.firstOrNull { it.name == prefs.getString(KeyVideoDetail, null) } ?: VideoDetail.High
        set(value) = prefs.edit { putString(KeyVideoDetail, value.name) }

    /** Settings → Smart Search → Index on Battery: the backlog runs without the charger too. */
    var indexOnBattery: Boolean
        get() = prefs.getBoolean(KeyIndexOnBattery, false)
        set(value) = prefs.edit { putBoolean(KeyIndexOnBattery, value) }

    /** Collections → People: faces in circles (default) or rounded squares. */
    var peopleSquare: Boolean
        get() = prefs.getBoolean(KeyPeopleSquare, false)
        set(value) = prefs.edit { putBoolean(KeyPeopleSquare, value) }

    /** Settings → Privacy → App Lock: Lumen asks for its lock when opened. */
    var appLock: Boolean
        get() = prefs.getBoolean(KeyAppLock, false)
        set(value) = prefs.edit { putBoolean(KeyAppLock, value) }

    /** Minutes in the background before App Lock asks again; 0 = every time. */
    var appLockMinutes: Int
        get() = prefs.getInt(KeyAppLockMinutes, 1)
        set(value) = prefs.edit { putInt(KeyAppLockMinutes, value) }

    /** Settings → Privacy → Encrypt Hidden: hidden items live in the encrypted vault. */
    var encryptHidden: Boolean
        get() = prefs.getBoolean(KeyEncryptHidden, false)
        set(value) = prefs.edit { putBoolean(KeyEncryptHidden, value) }

    private companion object {
        const val KeyEncryptHidden = "encrypt_hidden"
        const val KeyAppLock = "app_lock"
        const val KeyAppLockMinutes = "app_lock_minutes"
        const val KeyCurvedEdge = "curved_edge"
        const val KeyPeopleSquare = "people_square"
        const val KeyVideoDetail = "video_detail"
        const val KeyIndexOnBattery = "index_on_battery"
        const val KeyModelWifiOnly = "model_wifi_only"
        const val KeyModelQueue = "model_queue"
        const val KeyAiMoved = "ai_moved"
        const val KeyDeveloperOptions = "developer_options"
        const val KeyAutoUpdate = "auto_update_check"
        const val KeyLastUpdateCheck = "last_update_check"
        const val KeyAvailableUpdate = "available_update"
        const val KeyShowMaps = "show_maps"
        const val KeyTextVideoVersion = "text_video_version"
        const val KeyOnboardingDone = "onboarding_done"
        const val KeyAnnouncedUpdate = "announced_update"
        const val KeyIndexScheduleOn = "index_schedule_on"
        const val KeyIndexScheduleStart = "index_schedule_start"
        const val KeyIndexScheduleEnd = "index_schedule_end"
        const val KeySearchDetails = "search_details"
        const val KeySearchModel = "search_model"
        const val KeySections = "section_mode"
        const val KeyReduceMotion = "reduce_motion"
        const val KeyHdr = "hdr_photos"
        const val KeyHdrMode = "hdr_mode"
        const val KeyFilmStrip = "film_strip"
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
        const val KeyLiquidGlass = "liquid_glass"
        const val KeyReduceBlur = "reduce_blur"
        const val KeyExcluded = "excluded_folders"
        const val KeyColumns = "grid_columns"
        const val KeyAlbumColumns = "album_columns"
        const val KeySearchColumns = "search_columns"
        const val KeyRecentSearches = "recent_searches"
        const val MaxRecentSearches = 8
        const val KeyFilter = "library_filter"
        const val KeyAspect = "aspect_ratio_grid"
    }
}

/**
 * How big video frames are when looking for faces and text: small faces in group shots and small
 * captions need pixels. Smart Search itself looks at 224 px either way. Applies to videos
 * scanned after a change.
 */
enum class VideoDetail(@StringRes val label: Int, val px: Int) { High(R.string.data_detail_high, 1280), Medium(R.string.data_size_medium, 960), Low(R.string.data_detail_low, 640) }
