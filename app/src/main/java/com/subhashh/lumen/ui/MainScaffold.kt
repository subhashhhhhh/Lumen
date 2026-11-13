package com.subhashh.lumen.ui

import com.subhashh.lumen.ui.icons.LumenIcons
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.subhashh.lumen.ui.viewer.ViewerScreen
import com.subhashh.lumen.ui.viewer.WallpaperScreen
import com.subhashh.lumen.ui.viewer.rotateMenu
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.CoverTransitionOverlay
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.collections.coverUnderlay
import com.subhashh.lumen.ui.collections.heroSubtitle
import com.subhashh.lumen.ui.transition.CoverHeader
import com.subhashh.lumen.ui.transition.coverPage
import com.subhashh.lumen.ui.transition.viewerFaceKey
import com.subhashh.lumen.ui.transition.parkedViewer
import com.subhashh.lumen.ui.components.GlassMorphButton
import com.subhashh.lumen.ui.people.MergePeopleDialog
import com.subhashh.lumen.ui.people.MergeProposal
import com.subhashh.lumen.ui.transition.PhotoTransitionOverlay
import com.subhashh.lumen.ui.viewer.fullScreenRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaThumbnailFetcher
import com.subhashh.lumen.data.GridThumbnailFetcher
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.AlbumSort
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.GlassTextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.subhashh.lumen.ui.hidden.hiddenFolders
import com.subhashh.lumen.ui.hidden.HiddenScreen
import com.subhashh.lumen.ui.hidden.HiddenFolder
import androidx.compose.runtime.produceState
import com.subhashh.lumen.ui.hidden.LockFlow
import com.subhashh.lumen.ui.hidden.HiddenLockScreen
import com.subhashh.lumen.data.HiddenLockStore
import java.io.File
import com.subhashh.lumen.ui.hidden.HiddenLock
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.ConfirmDialog
import com.subhashh.lumen.data.HiddenMedia
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.net.toUri
import androidx.activity.compose.LocalActivity
import android.provider.Settings
import com.subhashh.lumen.ui.onboarding.openSpecial
import android.content.Intent
import com.subhashh.lumen.ui.settings.ExcludedFoldersScreen
import com.subhashh.lumen.data.ExcludedFolders
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Dispatchers
import com.subhashh.lumen.ui.components.RenameDialog
import com.subhashh.lumen.ai.HfTokenStore
import com.subhashh.lumen.ui.components.DateTimeDialog
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.data.MediaDates
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.data.vault.VaultMigration
import com.subhashh.lumen.ui.hidden.VaultProgressScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import com.subhashh.lumen.data.SettingsBackup
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.ui.settings.ResizeDialog
import com.subhashh.lumen.data.FavoritesImport
import android.provider.MediaStore
import android.os.Environment
import com.subhashh.lumen.ui.components.MenuItem
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.albums.AlbumTarget
import com.subhashh.lumen.ui.albums.AlbumPicker
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.FileResult
import androidx.compose.ui.graphics.TransformOrigin
import android.widget.Toast
import com.subhashh.lumen.ui.library.periods
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.library.TimelineScreen
import com.subhashh.lumen.ui.library.GridSections
import com.subhashh.lumen.ui.library.SectionMode
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedContent
import com.subhashh.lumen.ui.settings.SettingsScreen
import com.subhashh.lumen.ui.settings.UpdateSession
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.theme.LocalGlassColors
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.LibraryFilter
import com.subhashh.lumen.data.LibrarySort
import androidx.compose.runtime.SideEffect
import com.subhashh.lumen.ui.selection.SelectionTopControls
import com.subhashh.lumen.ui.selection.SelectionActionBar
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.imageDecoderEnabled
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.ui.collections.CollectionsScreen
import com.subhashh.lumen.ui.people.PeopleScreen
import com.subhashh.lumen.ui.components.GlassTabBar
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.library.LibraryScreen
import com.subhashh.lumen.ui.library.LibraryViewModel
import com.subhashh.lumen.ui.search.SearchScreen
import com.subhashh.lumen.ui.search.SearchSection
import com.subhashh.lumen.ui.search.searchGridIndex
import com.subhashh.lumen.ui.search.SearchField
import com.subhashh.lumen.data.SearchIndex
import com.subhashh.lumen.ai.SmartHit
import com.subhashh.lumen.ai.SmartSearch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R

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
    // Search: an index rebuilt off the main thread whenever the library changes, queried on
    // every keystroke (a few ms). The previous results stay up while the next ones compute.
    val searchIndex by produceState<SearchIndex?>(null, library) {
        value = library?.let { withContext(Dispatchers.Default) { SearchIndex.build(it) } }
    }
    val keywordResults by produceState<List<MediaItem>?>(null, searchIndex, searchQuery) {
        val index = searchIndex ?: return@produceState
        value = withContext(Dispatchers.Default) { index.search(searchQuery) }
    }
    // "Quoted phrases": only items whose read text or caption has exactly that, like Google Photos.
    val queryPhrases = remember(searchQuery) { com.subhashh.lumen.ai.QueryParts.phrases(searchQuery) }
    // Words read in photos (Settings → Smart Search → Text Read): exact, so right after the
    // name and date matches. A full-text lookup, a few ms, so no typing pause.
    val textMatches by produceState<List<Long>?>(null, searchQuery) {
        val query = searchQuery.trim()
        val phrases = queryPhrases
        value = if (query.length < 2) null else withContext(Dispatchers.IO) {
            if (!com.subhashh.lumen.ai.OcrModel.installed(context)) null
            else if (phrases != null) com.subhashh.lumen.ai.TextStore.of(context).searchPhrases(phrases)
            else com.subhashh.lumen.ai.TextStore.of(context).search(query)
        }
    }
    // Captions the user wrote (Details → Add a Caption): their own words, so exact matches.
    val captionMatches by produceState<List<Long>?>(null, searchQuery) {
        val query = searchQuery.trim()
        val phrases = queryPhrases
        value = if (query.length < 2) null else withContext(Dispatchers.IO) {
            if (phrases != null) com.subhashh.lumen.data.CaptionStore.of(context).searchPhrases(phrases)
            else com.subhashh.lumen.data.CaptionStore.of(context).search(query)
        }
    }
    // Opening Search loads Smart Search's text model and vectors while the user types.
    LaunchedEffect(searching) { if (searching) SmartSearch.prepare(context) }
    // Smart Search: by what's in the photos, once a model is downloaded and photos are indexed.
    // A short pause after typing, so the text model runs once per word, not once per letter.
    // Paired with the query it answers, so the screen can tell when it's still working.
    // Several people named ("hamilton and rosberg"): only items with all of them.
    val peopleGroup = remember(people, searchQuery) { com.subhashh.lumen.ai.QueryParts.people(searchQuery, people.map { it.first }) }
    val smart by produceState<Pair<String, com.subhashh.lumen.ai.SmartResult?>?>(null, searchQuery, peopleGroup) {
        if (searchQuery.isBlank()) {
            value = null
            return@produceState
        }
        // Exact text and named groups are answers already; Smart Search would only add guesses.
        if (queryPhrases != null || peopleGroup != null) {
            value = searchQuery to null
            return@produceState
        }
        delay(250)
        value = searchQuery to SmartSearch.search(context, searchQuery.trim())
    }
    val smartHits = smart?.second?.hits
    val smartPending = searchQuery.isNotBlank() && smart?.first != searchQuery &&
        remember(searching) { com.subhashh.lumen.ai.ModelDownloads.installed(context) != null }
    // People named like the search (every word starts one of the name's words): exact, so
    // right after name and date matches.
    val personMatches = remember(people, searchQuery, peopleGroup) {
        val terms = SearchIndex.terms(searchQuery)
        val group = peopleGroup
        if (group != null) people.filter { (p, _) -> group.any { it.id == p.id } }
        else if (terms.isEmpty() || queryPhrases != null) emptyList()
        else people.filter { (person, _) ->
            val words = SearchIndex.terms(person.name.orEmpty())
            terms.all { term -> words.any { it.startsWith(term) } }
        }
    }
    // Settings → Developer Options → Search Details; read again each time Search opens.
    val searchDetails = remember(searching) { prefs.searchDetails }
    // Exact name, folder and date matches first, then people of that name and photos with the
    // words in them (newest first), then Smart Search's, best first; each photo once.
    // With Search Details, one section per way of matching instead, each complete on its own.
    val searchSections = remember(keywordResults, captionMatches, personMatches, peopleGroup, queryPhrases, textMatches, smartHits, library, searchDetails) {
        // Quoted: exact text only, no name or date matches.
        val keyword = if (queryPhrases != null) emptyList() else keywordResults ?: return@remember null
        val byId = library.orEmpty().associateBy { it.id }
        // A named group: the items all of them are in; one person: theirs.
        val personIds = peopleGroup?.map { it.mediaIds }?.reduce { a, b -> a intersect b }?.toList()
            ?: personMatches.flatMap { it.first.mediaIds }
        if (!searchDetails) {
            val seen = keyword.mapTo(HashSet()) { it.id }
            val ofCaptions = captionMatches.orEmpty().mapNotNull { byId[it] }.filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofPeople = personIds.mapNotNull { byId[it] }
                .filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofText = textMatches.orEmpty().mapNotNull { byId[it] }.filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofSmart = smartHits.orEmpty().mapNotNull { hit -> byId[hit.mediaId]?.takeIf { seen.add(it.id) } }
            return@remember listOf(SearchSection(SearchSection.All, 0, keyword + ofCaptions + ofPeople + ofText + ofSmart))
        }
        listOf(
            SearchSection(SearchSection.Names, R.string.main_search_section_names, keyword),
            SearchSection("captions", R.string.main_search_section_captions, captionMatches.orEmpty().mapNotNull { byId[it] }.sortedByDescending { it.takenAt }),
            SearchSection(
                SearchSection.People, R.string.main_people,
                personIds.distinct().mapNotNull { byId[it] }.sortedByDescending { it.takenAt },
            ),
            SearchSection(
                SearchSection.Text, R.string.main_search_section_text,
                textMatches.orEmpty().mapNotNull { byId[it] }.sortedByDescending { it.takenAt },
            ),
            SearchSection(
                SearchSection.Smart, R.string.main_search_section_smart, smartHits.orEmpty().mapNotNull { byId[it.mediaId] },
                scores = smartHits.orEmpty().associate { it.mediaId to it.score },
            ),
        )
    }
    LaunchedEffect(searchQuery) { searchExpanded = emptySet() }
    // A face group, not yet named, that Smart Search's matches are about: offered in the People row.
    // Smart Search's result is from before any naming, so once it's named it shows as a match instead.
    val unnamedSubject = remember(smart, allPeople) {
        val id = smart?.second?.unnamedSubject?.id ?: return@remember null
        allPeople.firstOrNull { it.first.id == id && !it.first.hidden && it.first.name == null }
    }
    val searchAlbums = remember(folders, searchQuery) {
        val terms = SearchIndex.terms(searchQuery)
        if (terms.isEmpty()) emptyList()
        else folders.filter { album ->
            val words = SearchIndex.terms(album.title)
            terms.all { term -> words.any { SearchIndex.matchScore(term, it) > 0 } }
        }
    }
    val searchSuggestions = remember(library, folders) {
        val years = library.orEmpty().asReversed().asSequence()
            .map { java.util.Calendar.getInstance().apply { timeInMillis = it.takenAt }.get(java.util.Calendar.YEAR).toString() }
            .distinct().take(2).toList()
        // Words the search index knows (in English for now), so they stay untranslated.
        listOf("Videos", "Screenshots", "Favorites") + years + folders.take(3).map { it.title }
    }
    val rememberSearch = { query: String ->
        prefs.addRecentSearch(query)
        recentSearches = prefs.recentSearches
    }
    // Suggested searches from what this library has (Smart Search), each with its best photo;
    // a different handful each time Search opens.
    val smartSuggestions by produceState<List<Pair<String, MediaItem>>>(emptyList(), searching, library) {
        if (!searching) return@produceState
        value = withContext(Dispatchers.IO) {
            val model = com.subhashh.lumen.ai.ModelDownloads.installed(context) ?: return@withContext emptyList()
            val byId = library.orEmpty().associateBy { it.id }
            com.subhashh.lumen.ai.SearchSuggestions.load(context, model)
                .mapNotNull { s -> byId[s.coverId]?.let { s.term to it } }
                .shuffled()
                .take(8)
        }
    }
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
            } ?: CollectionKeys.items(key, library.orEmpty(), trash).let {
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
    val openItems = remember(openCollection, library, trash, hidden, hiddenUnlocked, sort, sortDescending, allPeople, personFilter, personSort, personSortDescending) {
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
    LaunchedEffect(chronological) {
        if (!chronological) {
            zoomLevel = ZoomLevel.All
            zoomBar = false
        }
    }
    // Years and Months start at the newest card too: once when first loaded, and on a new filter.
    LaunchedEffect(months.isNotEmpty(), filter) {
        if (months.isNotEmpty()) monthsList.scrollToItem(months.lastIndex)
    }
    LaunchedEffect(years.isNotEmpty(), filter) {
        if (years.isNotEmpty()) yearsList.scrollToItem(years.lastIndex)
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
    // Settings → Backup → Import Favorites: Fossify's .txt or Aves's .json, matched to files here.
    val favoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val found = FavoritesImport.read(context, uri, library.orEmpty())
            if (found == null) {
                toast(context.getString(R.string.main_favorites_import_bad_file))
                return@launch
            }
            val toMark = found.matched.filterNot { it.isFavorite }
            when {
                found.matched.isEmpty() -> toast(context.resources.getQuantityString(R.plurals.main_favorites_import_none, found.listed, found.listed))
                toMark.isEmpty() -> toast(context.resources.getQuantityString(R.plurals.main_favorites_import_already, found.matched.size, found.matched.size))
                else -> {
                    toast(context.getString(R.string.main_favorites_import_found, found.matched.size, found.listed, toMark.size))
                    selectionLauncher.launch(
                        IntentSenderRequest.Builder(MediaActions.favoriteRequest(context, toMark, favorite = true)).build(),
                    )
                }
            }
        }
    }
    // Settings → Backup. Importing replaces every setting, so the screen restarts afterwards to
    // pick them all up; any favorites to restore are asked for first.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = SettingsBackup.export(context, library.orEmpty().filter { it.isFavorite }, uri)
            toast(context.getString(if (ok) R.string.main_settings_exported else R.string.main_backup_save_failed))
        }
    }
    val restoreFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        activity?.recreate()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = SettingsBackup.import(context, uri)
            if (imported == null) {
                toast(context.getString(R.string.main_backup_bad_file))
                return@launch
            }
            toast(context.getString(R.string.main_settings_restored))
            val toFavorite = library.orEmpty().filter { !it.isFavorite && (it.relativePath + it.name) in imported.favoritePaths }
            if (toFavorite.isEmpty()) {
                activity?.recreate()
            } else {
                restoreFavoritesLauncher.launch(
                    IntentSenderRequest.Builder(MediaActions.favoriteRequest(context, toFavorite, favorite = true)).build(),
                )
            }
        }
    }
    /** Hidden content lives outside MediaStore; without All files access, explain and link to it. */
    val needsAllFiles: () -> Boolean = {
        val missing = !HiddenMedia.available()
        if (missing) {
            confirmation = Confirmation(
                title = context.getString(R.string.main_all_files_title),
                message = context.getString(R.string.main_all_files_message),
                confirm = context.getString(R.string.main_open_settings),
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri()),
                    )
                }
            }
        }
        missing
    }
    val openHidden: () -> Unit = open@{
        if (needsAllFiles()) return@open
        // First time: set up Lumen's own lock; afterwards: unlock with it.
        lockFlow = if (lockStore.isSet) LockFlow.Unlock else LockFlow.Setup
    }
    val showHidden = {
        hiddenUnlocked = true
        // The vault (Encrypt Hidden) opens with Hidden: from the Keystore, or after a reinstall
        // from its recovery file with the lock just typed.
        Vault.open(appContext, enteredSecret)
        enteredSecret = null
        viewModel.loadHidden()
        tab = LumenTab.Collections
        openCollection = CollectionKeys.Hidden
    }
    // Forgot the Lumen lock: the phone's own screen lock vouches for a reset.
    val resetLock: () -> Unit = reset@{
        val host = activity ?: return@reset
        val reset = {
            lockStore.clear()
            lockSet = false
            biometrics = false
            lockFlow = LockFlow.Setup
        }
        if (HiddenLock.deviceLockSet(host)) {
            HiddenLock.deviceCredential(host, onSuccess = reset)
        } else {
            confirmation = Confirmation(
                title = context.getString(R.string.main_reset_lock_title),
                message = context.getString(R.string.main_reset_lock_message),
                confirm = context.getString(R.string.main_reset),
                destructive = true,
            ) { reset() }
        }
    }
    val afterHiddenChange = { message: String ->
        viewModel.refresh()
        viewModel.loadHidden()
        endSelection()
        toast(message)
    }
    val unhide: (List<MediaItem>) -> Unit = unhide@{ all ->
        // Encrypted items decrypt back to their folder, visible.
        val (vaulted, items) = all.partition(Vault::isVault)
        if (vaulted.isNotEmpty()) {
            scope.launch {
                val restored = vaulted.count { Vault.restore(context, it, hidden = false) != null }
                viewModel.loadVault()
                if (items.isEmpty()) afterHiddenChange(if (restored == vaulted.size) context.getString(R.string.main_unhidden) else context.getString(R.string.main_unhide_failed_count, vaulted.size - restored))
            }
            if (items.isEmpty()) return@unhide
        }
        val plans = items.mapNotNull(HiddenMedia::unhidePlan).distinct()
        val doable = plans.filterNot { it is HiddenMedia.Unhide.DotFolder }
        if (doable.isEmpty()) {
            val folder = plans.filterIsInstance<HiddenMedia.Unhide.DotFolder>().firstOrNull()?.folder?.name
            toast(context.getString(R.string.main_unhide_dot_folder, folder))
            return@unhide
        }
        val run = { scope.launch { afterHiddenChange(context.getString(if (HiddenMedia.unhide(context, doable) > 0) R.string.main_unhidden else R.string.main_unhide_failed)) } }
        val folders = doable.filterIsInstance<HiddenMedia.Unhide.Folder>()
        if (folders.isEmpty()) {
            run()
        } else {
            val folderSet = folders.mapTo(HashSet()) { it.folder.absolutePath }
            val affected = hidden.orEmpty().count { HiddenMedia.file(it)?.parentFile?.absolutePath in folderSet }
            val name = folders.singleOrNull()?.folder?.name
            confirmation = Confirmation(
                title = if (name != null) context.getString(R.string.main_unhide_folder_title, name)
                else context.resources.getQuantityString(R.plurals.main_unhide_folders_title, folders.size, folders.size),
                message = context.resources.getQuantityString(R.plurals.main_unhide_folders_message, affected, affected),
                confirm = context.getString(R.string.common_unhide),
            ) { run() }
        }
    }
    val hide: (List<MediaItem>) -> Unit = hide@{ items ->
        if (needsAllFiles()) return@hide
        // Encrypt Hidden on: straight into the vault, which opens from the Keystore for this.
        if (encryptHidden && Vault.open(appContext)) {
            scope.launch {
                val n = VaultMigration.hideIntoVault(context, items) { MediaFiles.filePath(context, it) }
                if (!hiddenUnlocked) Vault.close()
                afterHiddenChange(if (n == items.size) context.getString(R.string.main_hidden_encrypted_find) else context.getString(R.string.main_hid_n_of, n, items.size))
            }
            return@hide
        }
        scope.launch {
            val paths = withContext(Dispatchers.IO) { items.mapNotNull { MediaFiles.filePath(context, it) } }
            val n = HiddenMedia.hide(context, paths)
            afterHiddenChange(if (n == items.size) context.getString(R.string.main_hidden_find) else context.getString(R.string.main_hid_n_of, n, items.size))
        }
    }
    val hideFolders: (List<MediaItem>) -> Unit = hide@{ items ->
        if (needsAllFiles()) return@hide
        scope.launch {
            val folders = withContext(Dispatchers.IO) {
                items.mapNotNull { MediaFiles.filePath(context, it)?.let(::File)?.parentFile }.distinctBy { it.absolutePath }
            }
            if (folders.isEmpty()) return@launch
            val paths = folders.mapTo(HashSet()) { it.absolutePath }
            val root = Environment.getExternalStorageDirectory()
            val affected = library.orEmpty().count { File(root, it.relativePath).absolutePath in paths }
            val name = folders.singleOrNull()?.name
            val encrypt = encryptHidden
            confirmation = Confirmation(
                title = if (name != null) context.getString(R.string.main_hide_folder_title, name)
                else context.resources.getQuantityString(R.plurals.main_hide_folders_title, folders.size, folders.size),
                message = context.resources.getQuantityString(
                    when {
                        encrypt && name != null -> R.plurals.main_hide_folder_encrypt_message
                        encrypt -> R.plurals.main_hide_folders_encrypt_message
                        name != null -> R.plurals.main_hide_folder_message
                        else -> R.plurals.main_hide_folders_message
                    },
                    affected,
                    affected,
                ),
                confirm = context.getString(R.string.common_hide),
            ) {
                if (encrypt && Vault.open(appContext)) {
                    scope.launch {
                        val inFolders = library.orEmpty().filter { File(root, it.relativePath).absolutePath in paths }
                        val n = VaultMigration.hideIntoVault(context, inFolders) { MediaFiles.filePath(context, it) }
                        if (!hiddenUnlocked) Vault.close()
                        afterHiddenChange(if (n == inFolders.size) context.getString(R.string.main_hidden_and_encrypted) else context.getString(R.string.main_hid_n_of, n, inFolders.size))
                    }
                    return@Confirmation
                }
                scope.launch {
                    val n = HiddenMedia.hideFolders(context, folders)
                    afterHiddenChange(context.getString(if (n == paths.size) R.string.main_hidden_find else R.string.main_hide_some_folders_failed))
                }
            }
        }
    }
    /**
     * Encrypt Hidden switched on or off, once the lock is confirmed: moves everything into or out of
     * the vault with a progress screen. [secret] makes the vault's recovery key (or opens an
     * existing vault from an earlier install).
     */
    val runEncryption: (on: Boolean, secret: String?) -> Unit = { on, secret ->
        scope.launch {
            val opened = when {
                Vault.open(appContext, secret) -> true
                on && secret != null && !Vault.exists(appContext) -> {
                    withContext(Dispatchers.Default) { Vault.create(appContext, secret) }
                    true
                }
                else -> false
            }
            if (!opened) {
                toast(context.getString(R.string.main_vault_wrong_lock))
                return@launch
            }
            vaultProgress = VaultProgress(encrypting = on, progress = null)
            val result = if (on) {
                VaultMigration.encryptAll(context, excluded) { vaultProgress = VaultProgress(encrypting = true, progress = it) }
            } else {
                VaultMigration.decryptAll(context) { vaultProgress = VaultProgress(encrypting = false, progress = it) }
            }
            vaultProgress = null
            // On even with some failures: those files stay hidden the old way, nothing is lost.
            prefs.encryptHidden = on
            encryptHidden = on
            if (!hiddenUnlocked) Vault.close()
            viewModel.loadHidden()
            viewModel.refresh()
            toast(
                when {
                    result.failed > 0 -> context.getString(
                        if (on) R.string.main_vault_partial_encrypt else R.string.main_vault_partial_decrypt,
                        result.moved,
                        result.failed,
                    )
                    on -> if (result.moved == 0) context.getString(R.string.main_encrypt_hidden_on)
                    else context.resources.getQuantityString(R.plurals.main_encrypted_items, result.moved, result.moved)
                    else -> context.resources.getQuantityString(R.plurals.main_decrypted_items, result.moved, result.moved)
                },
            )
        }
    }
    /** Share from Hidden: encrypted items go out as decrypted copies in Lumen's private cache. */
    val shareHidden: (List<MediaItem>) -> Unit = { items ->
        scope.launch {
            val shareable = items.mapNotNull { item ->
                if (!Vault.isVault(item)) item
                else Vault.cachedCopy(context, item)?.let { item.copy(uri = android.net.Uri.fromFile(it)) }
            }
            MediaActions.share(context, shareable)
        }
    }
    val deleteHidden: (List<MediaItem>) -> Unit = { items ->
        val n = items.size
        confirmation = Confirmation(
            title = if (n == 1) context.getString(R.string.main_delete_this_item) else context.resources.getQuantityString(R.plurals.main_delete_items_title, n, n),
            message = context.getString(R.string.main_delete_hidden_message),
            confirm = context.getString(R.string.common_delete),
            destructive = true,
        ) {
            scope.launch {
                val (vaulted, plain) = items.partition(Vault::isVault)
                val deleted = HiddenMedia.delete(plain) + vaulted.count(Vault::delete)
                if (vaulted.isNotEmpty()) viewModel.loadVault()
                afterHiddenChange(if (deleted == n) context.getString(R.string.main_deleted) else context.getString(R.string.main_delete_failed, n - deleted, n))
            }
        }
    }
    // With Media management on, Android trashes and deletes without asking, so Lumen asks instead.
    // Without it Android shows its own prompt; a second one from Lumen would be one too many.
    val confirmDelete: (List<MediaItem>, Boolean, () -> Unit) -> Unit = { items, forever, proceed ->
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || !android.provider.MediaStore.canManageMedia(context)) {
            proceed()
        } else {
            val n = items.size
            confirmation = Confirmation(
                title = if (n == 1) context.getString(R.string.main_delete_this_item) else context.resources.getQuantityString(R.plurals.main_delete_items_title, n, n),
                message = when {
                    !forever -> context.resources.getQuantityString(R.plurals.main_trash_message, n, n)
                    n == 1 -> context.getString(R.string.main_empty_trash_message_one)
                    else -> context.resources.getQuantityString(R.plurals.main_delete_forever_message, n, n)
                },
                confirm = context.getString(if (forever) R.string.main_delete_forever_title_case else R.string.common_delete),
                destructive = true,
                onConfirm = proceed,
            )
        }
    }
    // Re-lock as soon as Hidden is left: its grid and the viewer over it are the only ways in.
    LaunchedEffect(openCollection, viewerSource) {
        if (!CollectionKeys.isHidden(openCollection) && !CollectionKeys.isHidden(viewerSource)) hiddenUnlocked = false
    }
    // Locking Hidden locks the vault too: key forgotten, decrypted share copies gone.
    LaunchedEffect(hiddenUnlocked) {
        if (!hiddenUnlocked) {
            Vault.close()
            Vault.clearCache(appContext)
            viewModel.clearVault()
        }
    }
    // Leaving the app locks it too, closing anything that shows hidden photos.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (hiddenUnlocked) {
            if (CollectionKeys.isHidden(viewerSource)) viewerSource = null
            if (CollectionKeys.isHidden(openCollection)) openCollection = null
            hiddenUnlocked = false
        }
    }
    // After a restart, never land inside Hidden without unlocking (its list is empty until then).
    LaunchedEffect(openCollection, hiddenUnlocked) {
        if (CollectionKeys.isHidden(openCollection) && !hiddenUnlocked) openCollection = null
    }

    val excludeFolders: (List<MediaItem>) -> Unit = { items ->
        val folders = items.map { it.relativePath }.distinct()
        viewModel.exclude(folders)
        endSelection()
        val name = folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
        toast(
            if (name != null) context.getString(R.string.main_excluded_name, name)
            else context.resources.getQuantityString(R.plurals.main_excluded_folders, folders.size, folders.size),
        )
    }

    val excludeFromHidden: (List<MediaItem>) -> Unit = { items ->
        val folders = items.map { it.relativePath }.distinct()
        viewModel.exclude(folders)
        endSelection()
        // A folder whose contents just left Hidden would be empty: step back to the list.
        if (CollectionKeys.hiddenFolderId(openCollection.orEmpty()) != null) openCollection = CollectionKeys.Hidden
        val name = folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
        toast(
            if (name != null) context.getString(R.string.main_excluded_name, name)
            else context.resources.getQuantityString(R.plurals.main_excluded_folders, folders.size, folders.size),
        )
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pendingWrite
        pendingWrite = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
    }
    val withWriteAccess: (List<MediaItem>, () -> Unit) -> Unit = { items, action ->
        if (Environment.isExternalStorageManager()) {
            action()
        } else {
            pendingWrite = action
            val request = MediaStore.createWriteRequest(context.contentResolver, items.map { it.uri })
            writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
    /** Refresh and leave selection; [message] only when there's something worth saying. */
    val finish = { message: String? ->
        viewModel.refresh()
        endSelection()
        if (message != null) toast(message)
    }

    val toAlbum: (AlbumRequest, AlbumTarget) -> Unit = { request, target ->
        if (request.move) {
            withWriteAccess(request.items) {
                scope.launch {
                    val result = MediaFiles.move(context, request.items, target.relativePath)
                    finish(fileMessage(context, move = true, result, target.title))
                }
            }
        } else {
            scope.launch {
                val result = MediaFiles.copy(context, request.items, target.relativePath)
                finish(fileMessage(context, move = false, result, target.title))
            }
        }
    }
    val rotate: (List<MediaItem>, Int) -> Unit = { items, degrees ->
        val rotatable = items.filter(MediaFiles::canRotate)
        if (rotatable.isNotEmpty()) {
            withWriteAccess(rotatable) {
                scope.launch {
                    // Videos are remuxed, which takes a moment for long ones.
                    if (rotatable.any { it.isVideo }) toast(context.getString(R.string.main_rotating))
                    val result = MediaFiles.rotate(context, rotatable, degrees)
                    // Success shows itself (the photo turns); only a failure needs a message.
                    finish(if (result.failed == 0) null else context.getString(R.string.main_rotate_failed, result.failed, rotatable.size))
                }
            }
        }
    }
    LaunchedEffect(renameItem) {
        renameName = renameItem?.let { item -> withContext(Dispatchers.IO) { MediaFiles.displayName(context, item) } }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridPadding = PaddingValues(top = statusTop + 96.dp, bottom = navBottom + 96.dp)

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

    // Collections and People: also drawn under an album or person while its cover flies.
    val peoplePage: @Composable (Modifier) -> Unit = { pageModifier ->
            val from = mergeFrom?.let { id -> allPeople.firstOrNull { it.first.id == id }?.first }
            PeopleScreen(
                people = when {
                    from != null -> allPeople.filter { it.first.id != from.id && (!it.first.hidden || showHiddenPeople) }
                    showHiddenPeople -> allPeople
                    else -> people
                },
                title = when {
                    from != null -> stringResource(R.string.main_merge_with_ellipsis)
                    peopleSelection.isNullOrEmpty() -> stringResource(if (peopleSelection != null) R.string.main_select_people else R.string.main_people)
                    else -> pluralStringResource(R.plurals.main_n_selected, peopleSelection!!.size, peopleSelection!!.size)
                },
                selection = if (from == null) peopleSelection else null,
                onSelectionChange = { if (from == null && pick == null) peopleSelection = it },
                square = peopleSquare,
                gridState = peopleGrid,
                contentPadding = PaddingValues(top = statusTop + 64.dp, bottom = navBottom + 96.dp),
                coverTransition = coverTransition,
                modifier = pageModifier,
                onOpen = { key ->
                    val target = personOf(key)
                    if (from != null && target != null) {
                        val fromName = from.name ?: context.getString(R.string.main_this_person)
                        val targetName = target.name ?: context.getString(R.string.main_the_person_you_chose)
                        confirmation = Confirmation(
                            title = context.getString(R.string.main_merge_people_title),
                            message = (target.name ?: from.name)?.let { context.getString(R.string.main_merge_people_message_named, fromName, targetName, it) }
                                ?: context.getString(R.string.main_merge_people_message, fromName, targetName),
                            confirm = context.getString(R.string.main_merge),
                            onConfirm = {
                                mergeFrom = null
                                personFromPeople = false
                                openCollection = key
                                scope.launch(Dispatchers.IO) {
                                    com.subhashh.lumen.ai.FaceStore.of(context).merge(keep = target.id, gone = from.id)
                                }
                            },
                        )
                    } else {
                        personFromPeople = true
                        openCollectionFromCard(key)
                    }
                },
            )
    }
    val collectionsPage: @Composable (Modifier) -> Unit = { pageModifier ->
        CollectionsScreen(
            title = albumSelection?.let { albumSelectionTitle(it.size, folders = false) } ?: stringResource(R.string.main_collections),
            columns = albumColumns,
            onColumnsChange = onAlbumColumns,
            selection = albumSelection,
            onSelectionChange = { if (pick == null) albumSelection = it },
            utilities = utilities,
            hiddenLockSet = lockSet,
            folders = folders,
            people = people,
            gridState = collectionsGrid,
            contentPadding = PaddingValues(top = statusTop + 16.dp, bottom = navBottom + 96.dp),
            coverTransition = coverTransition,
            modifier = pageModifier,
            onOpen = { key ->
                personFromPeople = false
                if (key == CollectionKeys.Hidden) openHidden() else openCollectionFromCard(key)
            },
        )
    }

    Box(Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
        // Everything the glass controls refract lives inside this layer.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            MainPages(this@Scaffold, pick, library, allItems, excluded, hidden, hiddenAll, hiddenFolderList, utilities, folders, libraryItems, librarySections, openItems, collectionSections, months, years, selectedItems, searchSections, searchAlbums, personMatches, unnamedSubject, smartPending, smartSuggestions, searchSuggestions, chronological, scrollLabel, statusTop, navBottom, gridPadding, heroHeight, libraryGrid, collectionGrid, hiddenGrid, searchGrid, monthsList, yearsList, zoomBarScroll, transition, coverTransition, activity, exportLauncher, favoritesLauncher, importLauncher, itemsOf, personOf, heroWithLabel, setPicked, onGridColumns, onAlbumColumns, rememberSearch, toast, needsAllFiles, openHidden, openViewer, collectionsPage, peoplePage)
            if (!coverTransition.overViewer) CoverTransitionOverlay(coverTransition)
        }


        MainTopControls(this@Scaffold, pick, onPicked, onCancelPick, backdrop, navBottom, trash, allPeople, people, albumKeys, selectableItems, selectedItems, selecting, viewerOpen, inCollection, inSettings, updateWaiting, coverTransition, selectionLauncher, personOf, rememberSearch, endSelection, updateAlbums, popSettings, closeCollection)

        // Always composed, even under the viewer: recreating it means recompiling its glass
        // shaders right as a photo flies home.
        // Hidden (alpha 0, so the GPU skips its blur entirely) from the instant a photo starts
        // opening until one flies back home, where it fades in again.
        // Settings is a pushed page, like iOS: no tab bar under it.
        val tabBarHidden = viewerOpen || transition.opening || selecting || inSettings
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
                showZoom = chronological && (zoomBar || zoomLevel != ZoomLevel.All),
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
