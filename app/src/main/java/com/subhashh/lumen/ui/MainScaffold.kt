package com.subhashh.lumen.ui

import android.app.Activity
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.R
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.LibraryFilter
import com.subhashh.lumen.data.LibrarySort
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.collections.heroSubtitle
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.components.GlassTabBar
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.hidden.HiddenFolder
import com.subhashh.lumen.ui.hidden.hiddenFolders
import com.subhashh.lumen.ui.library.GridSections
import com.subhashh.lumen.ui.library.LibraryViewModel
import com.subhashh.lumen.ui.library.SectionMode
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.library.periods
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.UpdateSession
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.CoverTransitionOverlay
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.PhotoTransitionOverlay
import com.subhashh.lumen.ui.transition.viewerFaceKey
import com.subhashh.lumen.ui.viewer.fullScreenRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MainScaffold(
    viewModel: LibraryViewModel,
    pick: PickRequest?,
    onPicked: (List<MediaItem>) -> Unit,
    onCancelPick: () -> Unit,
    /** Onboarding's last screen, to fly from into the grid; null otherwise. */
    entrance: com.subhashh.lumen.ui.onboarding.OnboardingExit? = null,
    onEntranceDone: () -> Unit = {},
) {
    rememberMainState(viewModel, pick).Scaffold(pick, onPicked, onCancelPick, entrance, onEntranceDone)
}

@Composable
private fun MainState.Scaffold(
    pick: PickRequest?,
    onPicked: (List<MediaItem>) -> Unit,
    onCancelPick: () -> Unit,
    /** Onboarding's last screen, to fly from into the grid; null otherwise. */
    entrance: com.subhashh.lumen.ui.onboarding.OnboardingExit? = null,
    onEntranceDone: () -> Unit = {},
) {
    val loadedLibrary by viewModel.items.collectAsState()
    // A picker shows only the asked-for types, everywhere: grids, albums, search.
    val library = remember(loadedLibrary, pick) { pick?.let { p -> loadedLibrary?.filter(p::accepts) } ?: loadedLibrary }
    /** A grid's selection changed to [ids]: update the picks from that grid, keep the others. */
    val setPicked = { ids: Set<Long>?, gridItems: List<MediaItem> ->
        val chosen = ids.orEmpty()
        val gridIds = gridItems.mapTo(HashSet()) { it.id }
        picked = picked.filterKeys { it in chosen || it !in gridIds } + gridItems.filter { it.id in chosen }.associateBy { it.id }
    }
    val allItems by viewModel.allItems.collectAsState()
    val excluded by viewModel.excluded.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val hiddenAll by viewModel.hiddenAll.collectAsState()
    // Folder grouping stats each folder for .nomedia, so it runs off the main thread.
    val hiddenFolderList by produceState<List<HiddenFolder>?>(null, hidden) {
        value = hidden?.let { withContext(Dispatchers.IO) { hiddenFolders(it) } }
    }
    val trash by viewModel.trash.collectAsState()
    // Out of onboarding: always the Library's root, whatever was open before it restarted (Show
    // Onboarding comes from Settings), so the photos have their cells to land in.
    LaunchedEffect(entrance) {
        if (entrance == null) return@LaunchedEffect
        settingsPage = null
        openCollection = null
        searching = false
        viewerSource = null
        tab = LumenTab.Library
    }

    val backdrop = rememberLayerBackdrop()
    // Hoisted so grids keep their position across tab switches and while the viewer is open.
    val libraryGrid = rememberLazyGridState()
    val collectionGrid = key(openCollection) { rememberLazyGridState() }
    // Collections and Hidden's folder list keep their place while a folder is open.
    val collectionsGrid = rememberLazyGridState()
    val hiddenGrid = rememberLazyGridState()
    val transition = remember { PhotoTransition() }
    val coverTransition = remember { CoverTransition() }
    val reduceMotion = com.subhashh.lumen.ui.theme.LocalMotion.current.reduce
    val viewerBackground = MaterialTheme.colorScheme.background

    // An update is waiting (found by Software Update, now or on an earlier day): a dot on Settings.
    val installedVersion = remember { com.subhashh.lumen.data.Updater.installedVersion(context) }
    val updateWaiting = (((UpdateSession.state as? UpdateSession.State.Result)?.check as? com.subhashh.lumen.data.Updater.Check.Available)?.release?.version
        ?: prefs.availableUpdate)?.let { com.subhashh.lumen.data.Updater.isNewer(it, installedVersion) } == true
    /** The fast-scroll label in the current sort: month, first letter, or size. */
    val scrollLabel: (MediaItem) -> String = remember(sort) {
        when (sort) {
            LibrarySort.Date -> { item -> ScrollMonth.format(java.util.Date(item.takenAt)) }
            LibrarySort.Name -> { item -> item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            LibrarySort.Size -> { item -> android.text.format.Formatter.formatShortFileSize(context, item.size) }
        }
    }
    /** Oldest first by date: the order Years / Months and "newest at the bottom" are built on. */
    val chronological = sort == LibrarySort.Date && !sortDescending
    val material = com.subhashh.lumen.ui.theme.Lumen.material
    // Until an order is chosen it follows the style (newest first in Material): take it up on a change.
    LaunchedEffect(material) { sortDescending = prefs.sortDescending }
    /** Years and Months: oldest first by date; in the Material style, by date either way round. */
    val zoomable = chronological || (material && sort == LibrarySort.Date)
    val onGridColumns = { columns: Int ->
        gridColumns = columns
        prefs.gridColumns = columns
    }
    val onAlbumColumns = { columns: Int ->
        albumColumns = columns
        prefs.albumColumns = columns
    }
    val switchDistance = with(LocalDensity.current) { ZoomBarSwitchDistance.toPx() }
    val zoomBarScroll = remember(switchDistance) {
        object : NestedScrollConnection {
            // Distance dragged in the current direction; a nudge shouldn't flip the bar.
            var run = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || consumed.y == 0f) return Offset.Zero
                run = if (run == 0f || (run > 0f) == (consumed.y > 0f)) run + consumed.y else consumed.y
                if (run > switchDistance) zoomBar = true
                else if (run < -switchDistance) zoomBar = false
                return Offset.Zero
            }
        }
    }
    val monthsList = rememberLazyListState()
    val yearsList = rememberLazyListState()
    SideEffect { transition.fitCells = aspectRatioGrid }

    val utilities = remember(library, trash, pick) {
        MediaCollections.utilities(context, library.orEmpty(), trash).filter {
            // Nothing to pick from the trash, and Hidden stays locked away.
            pick == null || (it.key != CollectionKeys.RecentlyDeleted && it.key != CollectionKeys.Hidden)
        }
    }
    val updateAlbums = { change: (AlbumSettings) -> AlbumSettings ->
        albumSettings = change(albumSettings)
        prefs.albumSettings = albumSettings
    }
    val folders = remember(library, albumSettings) { MediaCollections.folders(library.orEmpty(), albumSettings) }
    // People (Settings → Smart Search → Faces), reloaded as the face pass finds them: at most
    // every couple of seconds, since it saves after every photo. Also when the library changes:
    // covers come only from photos it shows, so a deleted cover is replaced at once.
    val peopleRows by produceState(emptyList<com.subhashh.lumen.ai.Person>(), library) {
        val shown = library?.mapTo(HashSet()) { it.id }
        val load = suspend {
            withContext(Dispatchers.IO) {
                if (!com.subhashh.lumen.ai.FaceModel.installed(context)) emptyList()
                else com.subhashh.lumen.ai.FaceStore.of(context).people { shown == null || it in shown }
            }
        }
        // Right away at launch, so Collections has People from its first frame; after that only
        // once the face pass pauses its saving (it saves after every photo).
        value = load()
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        com.subhashh.lumen.ai.FaceStore.changes.drop(1).debounce(1_500).collect { value = load() }
    }
    // Only photos the library shows (no excluded folders, Hidden or trash); named people first.
    val allPeople = remember(peopleRows, library) {
        val byId = library.orEmpty().associateBy { it.id }
        peopleRows.asSequence()
            .mapNotNull { p -> byId[p.coverMediaId]?.let { p.copy(mediaIds = p.mediaIds.filterTo(HashSet()) { it in byId }) to it } }
            .filter { it.first.mediaIds.isNotEmpty() }
            .sortedWith(compareBy<Pair<com.subhashh.lumen.ai.Person, MediaItem>> { it.first.name == null }.thenByDescending { it.first.mediaIds.size })
            .toList()
    }
    val people = remember(allPeople) { allPeople.filterNot { it.first.hidden } }
    val personOf = { key: String? -> CollectionKeys.personId(key)?.let { id -> allPeople.firstOrNull { it.first.id == id }?.first } }
    /** The photo across the top of an album's or person's page; null for pages without one. */
    val heroOf = { key: String? ->
        when {
            key == null -> null
            CollectionKeys.isFolder(key) -> folders.firstOrNull { it.key == key }?.cover?.let { HeroCover(key, it) }
            else -> CollectionKeys.personId(key)?.let { id -> allPeople.firstOrNull { it.first.id == id } }?.let { (person, cover) ->
                if (person.coverInVideo) HeroCover(key, cover, crop = com.subhashh.lumen.ai.FaceStore.cropFile(context, person.coverFaceId))
                else HeroCover(key, cover, face = person.coverBox)
            }
        }
    }
    /** After naming a face, grouping gathers that person's other faces that match (in the background). */
    val regroup = { scope.launch(Dispatchers.IO) { runCatching { com.subhashh.lumen.ai.FaceGrouping.run(com.subhashh.lumen.ai.FaceStore.of(context), com.subhashh.lumen.ai.FaceModel.recognizer(context)) } } }
    LaunchedEffect(CollectionKeys.personId(openCollection)) { personFilter = LibraryFilter.All }
    val peopleGrid = rememberLazyGridState()
    // A person who's gone (merged away, last photo taken out) can't stay open.
    LaunchedEffect(openCollection, peopleRows) {
        val id = CollectionKeys.personId(openCollection) ?: return@LaunchedEffect
        if (peopleRows.isNotEmpty() && peopleRows.none { it.id == id }) {
            openCollection = if (personFromPeople) CollectionKeys.People else null
        }
    }
    // A new album stops being "created" once something is in it: it's a real folder then.
    LaunchedEffect(folders) {
        val real = folders.filter { it.count > 0 }.mapTo(HashSet()) { it.relativePath }
        if (albumSettings.created.any { it in real }) updateAlbums { it.copy(created = it.created.filterNot(real::contains)) }
    }
    // The Library tab is filtered by the ≡ menu; the viewer and selection page through the same list.
    val libraryItems = remember(library, filter, sort, sortDescending) {
        library.orEmpty()
            .let { all -> if (filter == LibraryFilter.All) all else all.filter(filter::matches) }
            .let { sort.apply(it, sortDescending) }
    }
    val search = searchResults(library, folders, people, allPeople)
    val searchSections = search.searchSections
    val smartPending = search.smartPending
    val personMatches = search.personMatches
    val unnamedSubject = search.unnamedSubject
    val searchAlbums = search.searchAlbums
    val searchSuggestions = search.searchSuggestions
    val rememberSearch = search.rememberSearch
    val smartSuggestions = search.smartSuggestions
    val searchGrid = rememberLazyGridState()
    LaunchedEffect(searchQuery) { searchGrid.scrollToItem(0) }
    // Pages that open with a cover across the top start at the top, so by date the newest
    // photos come first, right under it.
    val coverPageDescending = sort == LibrarySort.Date || sortDescending
    val itemsOf = { key: String ->
        when (key) {
            CollectionKeys.Library -> libraryItems
            CollectionKeys.Search -> searchSections?.firstOrNull { it.id == searchSection }?.ordered()?.items.orEmpty()
            CollectionKeys.Hidden -> if (hiddenUnlocked) hidden.orEmpty() else emptyList()
            else -> personOf(key)?.let { person ->
                // A person's page has its own Show and Sort (its ⋯ menu).
                personSort.apply(
                    library.orEmpty().filter { it.id in person.mediaIds && (personFilter == LibraryFilter.All || personFilter.matches(it)) },
                    personSortDescending,
                )
            } ?: CollectionKeys.hiddenFolderId(key)?.let { id ->
                if (hiddenUnlocked) hidden.orEmpty().filter { it.bucketId == id } else emptyList()
            } ?: CollectionKeys.documentQuery(key)?.let { documentItems[it].orEmpty() }
            ?: CollectionKeys.items(key, library.orEmpty(), trash).let {
                // Recently Deleted stays in deletion order; albums follow the Library's sort,
                // except that by date they read newest first, under their cover.
                when {
                    key == CollectionKeys.RecentlyDeleted -> it
                    CollectionKeys.isFolder(key) -> sort.apply(it, coverPageDescending)
                    else -> sort.apply(it, sortDescending)
                }
            }
        }
    }
    LaunchedEffect(settingsPage) {
        sectionMode = prefs.sectionMode
        curvedEdge = prefs.curvedEdge
    }
    val gridSectionMode = if (sort == LibrarySort.Date) sectionMode else SectionMode.None
    val librarySections = remember(libraryItems, gridSectionMode) { GridSections.of(context, libraryItems, gridSectionMode) }
    /** The open album's photos and their headers, shared by its grid and the viewer's close flight. */
    val openItems = remember(documentItems, openCollection, library, trash, hidden, hiddenUnlocked, sort, sortDescending, allPeople, personFilter, personSort, personSortDescending) {
        openCollection?.takeIf { it != CollectionKeys.Hidden }?.let(itemsOf)
    }
    val collectionSections = remember(openItems, gridSectionMode, openCollection) {
        openItems?.let { GridSections.of(context, it, if (openCollection == CollectionKeys.RecentlyDeleted) SectionMode.None else gridSectionMode) }
    }
    /** [heroOf] with the name and count it shows. */
    val heroWithLabel = { key: String? ->
        heroOf(key)?.let { cover ->
            val name = personOf(key)?.let { it.name ?: context.getString(R.string.main_add_name) } ?: folders.firstOrNull { it.key == key }?.title.orEmpty()
            cover.copy(title = name, subtitle = heroSubtitle(context, if (key == openCollection) openItems.orEmpty() else itemsOf(key!!)))
        }
    }
    // An album's or person's cover: the top 46% of the screen.
    val heroHeight = with(LocalDensity.current) { (screenSize.height * HeroShare).toDp() }
    val heroRect = { androidx.compose.ui.geometry.Rect(0f, 0f, screenSize.width.toFloat(), screenSize.height * HeroShare) }
    /** Opens [key], growing its cover out of its card when it has one on screen. */
    val openCollectionFromCard: (key: String) -> Unit = open@{ key ->
        if (coverTransition.flying || coverOpening) return@open
        val cover = heroWithLabel(key) ?: run {
            openCollection = key
            return@open
        }
        coverOpening = true
        scope.launch {
            try {
                if (coverTransition.prepare(context, cover, heroRect())) {
                    openCollection = key
                    coverTransition.open()
                } else {
                    openCollection = key
                }
            } finally {
                coverOpening = false
                coverTransition.finish()
            }
        }
    }
    /** Leaves the open album or person for [target], shrinking its cover back into its card. */
    val closeCollection: (target: String?) -> Unit = close@{ target ->
        if (coverTransition.flying || coverOpening) return@close
        val back = viewerReturn
        if (back != null && openCollection == back.person) {
            // Opened from the viewer's details sheet: back into its face there, the viewer as it was.
            val cover = heroWithLabel(openCollection)?.let { it.copy(cellKey = viewerFaceKey(it.key)) }
            coverOpening = true
            coverTransition.overViewer = true
            scope.launch {
                try {
                    if (cover != null) coverTransition.close(cover, heroRect())
                } finally {
                    tab = back.tab
                    openCollection = back.openCollection
                    searching = back.searching
                    personFromPeople = back.personFromPeople
                    viewerReturn = null
                    coverTransition.finish()
                    coverOpening = false
                }
            }
            return@close
        }
        val cover = heroWithLabel(openCollection) ?: run {
            openCollection = target
            return@close
        }
        coverOpening = true
        scope.launch {
            try {
                coverTransition.close(cover, heroRect())
            } finally {
                openCollection = target
                coverTransition.finish()
                coverOpening = false
            }
        }
    }
    val months = remember(libraryItems) { periods(context, libraryItems, byYear = false) }
    val years = remember(libraryItems) { periods(context, libraryItems, byYear = true) }
    // A new filter starts at its newest item, like opening the Library.
    // Also out of onboarding: the newest photos are the ones it showed, so they're on screen.
    LaunchedEffect(filter, sort, sortDescending, entrance) {
        if (libraryItems.isNotEmpty()) libraryGrid.scrollToItem(if (chronological) librarySections.entries.lastIndex else 0)
    }
    // Years / Months only make sense oldest-first by date; any other order goes back to All.
    LaunchedEffect(zoomable) {
        if (!zoomable) {
            zoomLevel = ZoomLevel.All
            zoomBar = false
        }
    }
    // Years and Months start at the newest card too: once when first loaded, and on a new filter.
    LaunchedEffect(months.isNotEmpty(), filter, chronological) {
        if (months.isNotEmpty()) monthsList.scrollToItem(if (chronological) months.lastIndex else 0)
    }
    LaunchedEffect(years.isNotEmpty(), filter, chronological) {
        if (years.isNotEmpty()) yearsList.scrollToItem(if (chronological) years.lastIndex else 0)
    }

    val viewerOpen = viewerSource != null && viewerReturn == null
    // Gone somewhere else from the person opened in the viewer: the parked viewer isn't coming back.
    LaunchedEffect(tab, openCollection, searching) {
        val back = viewerReturn ?: return@LaunchedEffect
        if (!coverOpening && (tab != LumenTab.Collections || openCollection != back.person || searching)) {
            viewerReturn = null
            viewerSource = null
        }
    }
    val inCollection = tab == LumenTab.Collections && openCollection != null && !searching
    // Settings opens from either tab and covers it; Back returns to where it was opened.
    val inSettings = settingsPage != null && !searching
    // Back goes up one level: a sub-page to its category, a category to Settings, Settings out.
    val popSettings = {
        settingsHighlight = null
        settingsPage = when {
            settingsPage == SettingsLicenses -> settingsCategoryPage(SettingsCategory.About)
            settingsPage == SettingsExcluded -> settingsCategoryPage(SettingsCategory.Library)
            settingsPage?.startsWith(SettingsCategoryPrefix) == true -> SettingsMain
            else -> null
        }
    }
    BackHandler(enabled = searching && !viewerOpen) { searching = false }
    BackHandler(enabled = inCollection && !viewerOpen) {
        // A folder inside Hidden steps back to Hidden's list, not out of Hidden.
        closeCollection(when {
            openCollection == CollectionKeys.People && mergeFrom != null -> CollectionKeys.person(mergeFrom!!).also { mergeFrom = null }
            CollectionKeys.hiddenFolderId(openCollection.orEmpty()) != null -> CollectionKeys.Hidden
            CollectionKeys.personId(openCollection) != null && personFromPeople -> CollectionKeys.People
            else -> null
        })
    }
    BackHandler(enabled = inSettings && !viewerOpen) { popSettings() }

    val endSelection = {
        selection = null
        albumSelection = null
        peopleSelection = null
    }
    val selectableKey = when {
        searching -> null
        tab == LumenTab.Library && zoomLevel == ZoomLevel.All -> CollectionKeys.Library
        tab == LumenTab.Library -> null
        // Hidden's top level is a list of folders, not photos.
        openCollection == CollectionKeys.Hidden -> null
        else -> openCollection
    }
    val selectableItems = selectableKey?.let(itemsOf).orEmpty()
    // Which album list is on screen, if any: Collections' albums or Hidden's folders.
    val albumScope = when {
        searching || settingsPage != null -> null
        tab == LumenTab.Collections && openCollection == null -> AlbumsScope
        openCollection == CollectionKeys.Hidden && hiddenUnlocked -> CollectionKeys.Hidden
        else -> null
    }
    val albumKeys = when (albumScope) {
        AlbumsScope -> folders.map { it.key }
        CollectionKeys.Hidden -> hiddenFolderList.orEmpty().map { it.collection.key }
        else -> emptyList()
    }
    LaunchedEffect(albumScope) { albumSelection = null }
    LaunchedEffect(albumKeys) {
        val keys = albumKeys.toHashSet()
        albumSelection = albumSelection?.filterTo(HashSet()) { it in keys }
    }
    val selecting = selection != null || albumSelection != null || peopleSelection != null
    LaunchedEffect(openCollection, tab) { if (openCollection != CollectionKeys.People || tab != LumenTab.Collections) peopleSelection = null }
    /** Where the selection is: a grid's key, or the album list's scope. */
    val selectionScope = if (albumSelection != null) albumScope else selectableKey
    // Leaving the screen ends selection; refreshes drop ids that no longer exist.
    LaunchedEffect(selectableKey) { selection = null }
    LaunchedEffect(selectableItems) {
        val ids = selectableItems.mapTo(HashSet()) { it.id }
        selection = selection?.filterTo(HashSet()) { it in ids }
    }
    BackHandler(enabled = selecting && !viewerOpen) { endSelection() }
    val selectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refresh()
            endSelection()
        }
    }
    val selectedItems = remember(selection, selectableItems, albumSelection, library, hidden) {
        val albums = albumSelection
        when {
            albums == null -> selection?.let { ids -> selectableItems.filter { it.id in ids } }.orEmpty()
            albumScope == CollectionKeys.Hidden -> hidden.orEmpty().filter { CollectionKeys.hiddenFolder(it.bucketId) in albums }
            else -> library.orEmpty().filter { CollectionKeys.folder(it.bucketId) in albums }
        }
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    BackHandler(enabled = selectionMenuOpen) { selectionMenuOpen = false }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val activity = LocalActivity.current
    val backup = backupLaunchers(library, activity, toast, selectionLauncher)
    val favoritesLauncher = backup.favoritesLauncher
    val exportLauncher = backup.exportLauncher
    val importLauncher = backup.importLauncher
    val hiddenActions = hiddenActions(library, hidden, excluded, activity, toast, endSelection)
    val needsAllFiles = hiddenActions.needsAllFiles
    val openHidden = hiddenActions.openHidden
    val showHidden = hiddenActions.showHidden
    val resetLock = hiddenActions.resetLock
    val unhide = hiddenActions.unhide
    val hide = hiddenActions.hide
    val hideFolders = hiddenActions.hideFolders
    val runEncryption = hiddenActions.runEncryption
    val shareHidden = hiddenActions.shareHidden
    val deleteHidden = hiddenActions.deleteHidden
    val confirmDelete = hiddenActions.confirmDelete
    val fileActions = fileActions(toast, endSelection)
    val excludeFolders = fileActions.excludeFolders
    val excludeFromHidden = fileActions.excludeFromHidden
    val withWriteAccess = fileActions.withWriteAccess
    val finish = fileActions.finish
    val toAlbum = fileActions.toAlbum
    val rotate = fileActions.rotate
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Material's Photos page has no title over it: the grid starts right under the top buttons.
    val gridPadding = PaddingValues(top = statusTop + (if (material) 64.dp else 96.dp), bottom = navBottom + 96.dp)

    // Grow the tapped photo out of its cell, then build the viewer behind it before revealing,
    // so the viewer's setup cost never stutters the animation.
    val openViewer: (key: String, index: Int) -> Unit = open@{ key, index ->
        // Picking: a tap chooses instead of opening.
        if (pick != null) {
            val item = itemsOf(key).getOrNull(index) ?: return@open
            if (!pick.multiple) onPicked(listOf(item))
            else picked = if (item.id in picked) picked - item.id else picked + (item.id to item)
            return@open
        }
        if (transition.item != null) return@open
        val item = itemsOf(key).getOrNull(index) ?: return@open
        if (viewerReturn != null) {
            viewerReturn = null
            viewerSource = null
        }
        viewerSession++
        // Reduce Motion: the viewer just appears, no flight out of the cell.
        if (reduceMotion) {
            viewerStart = index
            viewerSource = key
            return@open
        }
        transition.flight = scope.launch {
            val target = fullScreenRect(item, screenSize.width.toFloat(), screenSize.height.toFloat())
            transition.open(item, target, viewerBackground)
            viewerStart = index
            viewerSource = key
            withFrameNanos { }
            withFrameNanos { }
            transition.finish()
        }
    }

    // A photo reminder's notification was tapped: open its photo once the library is in.
    OpenFromReminder(ready = library != null) { id ->
        val index = itemsOf(CollectionKeys.Library).indexOfFirst { it.id == id }
        if (index >= 0) openViewer(CollectionKeys.Library, index)
        index >= 0
    }

    val collectionPages = collectionPages(pick, utilities, folders, people, allPeople, statusTop, navBottom, collectionsGrid, peopleGrid, coverTransition, personOf, onAlbumColumns, openHidden, openCollectionFromCard)
    val peoplePage = collectionPages.peoplePage
    val collectionsPage = collectionPages.collectionsPage
    Box(Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
        // Everything the glass controls refract lives inside this layer.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Material: a page that's newly on screen springs up into place. Albums and people
            // arrive on their covers instead, so they (and the page under them) are left alone.
            val coverKey = openCollection?.takeIf { CollectionKeys.isFolder(it) || CollectionKeys.personId(it) != null }
            val pageKey = when {
                searching -> "search"
                settingsPage != null -> settingsPage
                tab == LumenTab.Library -> "library"
                coverKey != null -> null
                else -> openCollection ?: "collections"
            }
            val arrival = remember { androidx.compose.animation.core.Animatable(1f) }
            val lastPage = remember { arrayOfNulls<String>(1) }
            LaunchedEffect(pageKey) {
                if (pageKey == null) return@LaunchedEffect
                val first = lastPage[0] == null
                val changed = lastPage[0] != pageKey
                lastPage[0] = pageKey
                if (material && changed && !first && !reduceMotion) {
                    arrival.snapTo(0f)
                    arrival.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 380f))
                }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val a = arrival.value
                    if (a != 1f) {
                        alpha = a.coerceIn(0f, 1f)
                        translationY = 20.dp.toPx() * (1f - a)
                    }
                },
            ) {
            MainPages(this@Scaffold, pick, library, allItems, excluded, hidden, hiddenAll, hiddenFolderList, utilities, folders, libraryItems, librarySections, openItems, collectionSections, months, years, selectedItems, searchSections, searchAlbums, personMatches, unnamedSubject, smartPending, smartSuggestions, searchSuggestions, chronological, scrollLabel, statusTop, navBottom, gridPadding, heroHeight, libraryGrid, collectionGrid, hiddenGrid, searchGrid, monthsList, yearsList, zoomBarScroll, transition, coverTransition, activity, exportLauncher, favoritesLauncher, importLauncher, itemsOf, personOf, heroWithLabel, setPicked, onGridColumns, onAlbumColumns, rememberSearch, toast, needsAllFiles, openHidden, openViewer, collectionsPage, peoplePage, people)
            }
            if (!coverTransition.overViewer) CoverTransitionOverlay(coverTransition)
        }


        MainTopControls(this@Scaffold, pick, onPicked, onCancelPick, backdrop, navBottom, trash, allPeople, people, albumKeys, selectableItems, selectedItems, selecting, viewerOpen, inCollection, inSettings, updateWaiting, coverTransition, selectionLauncher, personOf, rememberSearch, endSelection, updateAlbums, popSettings, closeCollection)

        // Always composed, even under the viewer: recreating it means recompiling its glass
        // shaders right as a photo flies home.
        // Hidden (alpha 0, so the GPU skips its blur entirely) from the instant a photo starts
        // opening until one flies back home, where it fades in again.
        // Settings is a pushed page, like iOS: no tab bar under it.
        // Material's search is a screen of its own, the field at the bottom where the bar was.
        val tabBarHidden = viewerOpen || transition.opening || selecting || inSettings || (material && searching)
        val tabBarAlpha by animateFloatAsState(
            targetValue = if (tabBarHidden) 0f else 1f,
            animationSpec = if (tabBarHidden) snap() else tween(durationMillis = 220),
            label = "tabBarAlpha",
        )
        run {
            GlassTabBar(
                backdrop = backdrop,
                enabled = !tabBarHidden,
                selected = tab.takeUnless { searching },
                onSelect = { selected ->
                    // Tapping the current tab again pops back to its root, like iOS.
                    if (selected == tab && !searching) openCollection = null
                    // Settings covers whichever tab opened it; any tab tap leaves it.
                    settingsPage = null
                    tab = selected
                    searching = false
                },
                searchSelected = searching,
                onSearch = {
                    if (!searching) focusSearch = true
                    searching = true
                },
                zoomLevel = zoomLevel.takeIf { tab == LumenTab.Library && !searching },
                onZoomLevel = { zoomLevel = it },
                showZoom = zoomable && (zoomBar || zoomLevel != ZoomLevel.All),
                onShowTabs = {
                    zoomBar = false
                    zoomLevel = ZoomLevel.All
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = tabBarAlpha }
                    .padding(start = 16.dp, end = 16.dp, bottom = navBottom + 12.dp),
            )
        }

        GlassMenu(
            backdrop = backdrop,
            open = menuOpen && tab == LumenTab.Library && !viewerOpen && settingsPage == null,
            onDismiss = { menuOpen = false },
            sections = listOf(
                com.subhashh.lumen.ui.library.viewMenuItems(
                    filter = filter,
                    onFilter = {
                        filter = it
                        prefs.libraryFilter = it
                    },
                    sort = sort,
                    descending = sortDescending,
                    onSort = { newSort, descending ->
                        sort = newSort
                        sortDescending = descending
                        prefs.librarySort = newSort
                        prefs.sortDescending = descending
                    },
                    aspectRatioGrid = aspectRatioGrid,
                    onAspectRatioGrid = {
                        aspectRatioGrid = it
                        prefs.aspectRatioGrid = it
                    },
                ),
            ),
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        MainSelectionBars(this@Scaffold, backdrop, navBottom, viewerOpen, selecting, selectionScope, selectableKey, selectableItems, selectedItems, folders, hidden, selectionLauncher, personOf, toast, endSelection, updateAlbums, openViewer, rotate, excludeFolders, excludeFromHidden, hide, hideFolders, unhide, shareHidden, deleteHidden, confirmDelete)

        MainViewer(this@Scaffold, library, trash, allPeople, searchSections, searchAlbums, personMatches, unnamedSubject, smartPending, librarySections, collectionSections, libraryGrid, searchGrid, collectionGrid, transition, coverTransition, reduceMotion, itemsOf, personOf, heroWithLabel, heroRect, toast, updateAlbums, rotate, excludeFolders, hide, hideFolders, unhide, shareHidden, deleteHidden, confirmDelete)

        MainDialogs(this@Scaffold, pick, library, folders, allPeople, coverTransition, viewerOpen, selecting, activity, toast, endSelection, finish, withWriteAccess, toAlbum, updateAlbums, regroup, resetLock, runEncryption, showHidden)

        PhotoTransitionOverlay(transition)
        entrance?.let { exit ->
            com.subhashh.lumen.ui.onboarding.Entrance(exit, cellOf = { transition.cellRect(it) }, onDone = onEntranceDone)
        }
    }
}
