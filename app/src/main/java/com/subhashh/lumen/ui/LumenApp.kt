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
import com.subhashh.lumen.ui.transition.PhotoTransitionOverlay
import com.subhashh.lumen.ui.viewer.fullScreenRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.Dispatchers
import com.subhashh.lumen.ui.components.RenameDialog
import com.subhashh.lumen.data.ai.HfTokenStore
import com.subhashh.lumen.ui.components.DateTimeDialog
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
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.theme.LocalGlassColors
import com.subhashh.lumen.ui.library.ViewMenu
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
import com.subhashh.lumen.data.ai.SmartHit
import com.subhashh.lumen.data.ai.SmartSearch

private val MediaPermissions = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Full or partial ("Select photos") access both count — partial users just see fewer items. */
private fun Context.hasMediaAccess(): Boolean = MediaPermissions.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun LumenApp(
    /** Set when another app asked Lumen to pick photos or videos. */
    pick: PickRequest? = null,
    onPicked: (List<MediaItem>) -> Unit = {},
    onCancelPick: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel()
    var hasAccess by rememberSaveable { mutableStateOf(context.hasMediaAccess()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAccess = context.hasMediaAccess()
        if (hasAccess) viewModel.refresh()
    }

    // Re-check on resume: access can change in system settings, and new photos may have been taken.
    LifecycleResumeEffect(Unit) {
        hasAccess = context.hasMediaAccess()
        if (hasAccess) viewModel.refresh()
        onPauseOrDispose { }
    }

    if (hasAccess) {
        MainScaffold(viewModel, pick, onPicked, onCancelPick)
    } else {
        PermissionScreen(onGrant = { launcher.launch(MediaPermissions) })
    }
}

@Composable
private fun MainScaffold(
    viewModel: LibraryViewModel,
    pick: PickRequest?,
    onPicked: (List<MediaItem>) -> Unit,
    onCancelPick: () -> Unit,
) {
    val loadedLibrary by viewModel.items.collectAsState()
    // A picker shows only the asked-for types, everywhere: grids, albums, search.
    val library = remember(loadedLibrary, pick) { pick?.let { p -> loadedLibrary?.filter(p::accepts) } ?: loadedLibrary }
    /** Picking several: what's chosen so far, in order, kept across screens. */
    var picked by remember { mutableStateOf<Map<Long, MediaItem>>(emptyMap()) }
    /** A grid's selection changed to [ids]: update the picks from that grid, keep the others. */
    val setPicked = { ids: Set<Long>?, gridItems: List<MediaItem> ->
        val chosen = ids.orEmpty()
        val gridIds = gridItems.mapTo(HashSet()) { it.id }
        picked = picked.filterKeys { it in chosen || it !in gridIds } + gridItems.filter { it.id in chosen }.associateBy { it.id }
    }
    val pickTitle = pick?.let { if (it.multiple && picked.isNotEmpty()) "${picked.size} Selected" else it.title }
    val allItems by viewModel.allItems.collectAsState()
    val excluded by viewModel.excluded.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val hiddenAll by viewModel.hiddenAll.collectAsState()
    // Folder grouping stats each folder for .nomedia, so it runs off the main thread.
    val hiddenFolderList by produceState<List<HiddenFolder>?>(null, hidden) {
        value = hidden?.let { withContext(Dispatchers.IO) { hiddenFolders(it) } }
    }
    /** Hidden opens only after biometric unlock and re-locks whenever it's left. */
    var hiddenUnlocked by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var enteringHfToken by remember { mutableStateOf(false) }
    var hfTokenVersion by remember { mutableIntStateOf(0) }
    val appContext = LocalContext.current.applicationContext
    val lockStore = remember { HiddenLockStore(appContext) }
    /** Hidden's own lock screen, when up; and whether a lock exists yet (for "Set Up"). */
    var lockFlow by remember { mutableStateOf<LockFlow?>(null) }
    var lockSet by remember { mutableStateOf(lockStore.isSet) }
    /** Mirrors lockStore.biometrics so the Settings toggle redraws the moment it changes. */
    var biometrics by remember { mutableStateOf(lockStore.biometrics) }
    var appLockOn by remember { mutableStateOf(LumenPrefs(appContext).appLock) }
    /** A lock is being set up so App Lock can turn on (not to open Hidden). */
    var setupForAppLock by remember { mutableStateOf(false) }
    var excludePicker by remember { mutableStateOf(false) }
    val trash by viewModel.trash.collectAsState()
    var tab by rememberSaveable { mutableStateOf(LumenTab.Library) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    /** Keyboard up when search is opened, but not when the field returns after the viewer. */
    var focusSearch by remember { mutableStateOf(false) }
    /** Collection open inside the Collections tab, if any. */
    var openCollection by rememberSaveable { mutableStateOf<String?>(null) }
    /** Settings pages pushed on the Collections tab: "settings", then "licenses". */
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    /** A setting found with Settings' search, to bring into view on its page. */
    var settingsHighlight by remember { mutableStateOf<String?>(null) }
    /** Which collection the viewer pages through, and where it started. */
    var viewerSource by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerStart by rememberSaveable { mutableIntStateOf(0) }

    val backdrop = rememberLayerBackdrop()
    // Hoisted so grids keep their position across tab switches and while the viewer is open.
    val libraryGrid = rememberLazyGridState()
    val collectionGrid = key(openCollection) { rememberLazyGridState() }
    // Collections and Hidden's folder list keep their place while a folder is open.
    val collectionsGrid = rememberLazyGridState()
    val hiddenGrid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val transition = remember { PhotoTransition() }
    val reduceMotion = com.subhashh.lumen.ui.theme.LocalMotion.current.reduce
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val viewerBackground = MaterialTheme.colorScheme.background

    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    // A picker starts unfiltered: the asked-for types already narrow it, and a saved
    // "Videos" filter would leave a photo picker empty.
    var filter by remember { mutableStateOf(if (pick != null) LibraryFilter.All else prefs.libraryFilter) }
    var aspectRatioGrid by remember { mutableStateOf(prefs.aspectRatioGrid) }
    var sort by remember { mutableStateOf(prefs.librarySort) }
    var sortDescending by remember { mutableStateOf(prefs.sortDescending) }
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
    var gridColumns by remember { mutableIntStateOf(prefs.gridColumns) }
    val onGridColumns = { columns: Int ->
        gridColumns = columns
        prefs.gridColumns = columns
    }
    var searchColumns by remember { mutableIntStateOf(prefs.searchColumns) }
    var albumColumns by remember { mutableIntStateOf(prefs.albumColumns) }
    val onAlbumColumns = { columns: Int ->
        albumColumns = columns
        prefs.albumColumns = columns
    }
    var menuOpen by remember { mutableStateOf(false) }
    var zoomLevel by rememberSaveable { mutableStateOf(ZoomLevel.All) }
    // The Library opens with the tabs showing; scrolling up (back in time) folds them into the
    // Years / Months / All control, scrolling down brings them back. Years and Months keep the
    // control, since that's what they're for.
    var zoomBar by rememberSaveable { mutableStateOf(false) }
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
        MediaCollections.utilities(library.orEmpty(), trash).filter {
            // Nothing to pick from the trash, and Hidden stays locked away.
            pick == null || (it.key != CollectionKeys.RecentlyDeleted && it.key != CollectionKeys.Hidden)
        }
    }
    var albumSettings by remember { mutableStateOf(prefs.albumSettings) }
    val updateAlbums = { change: (AlbumSettings) -> AlbumSettings ->
        albumSettings = change(albumSettings)
        prefs.albumSettings = albumSettings
    }
    val folders = remember(library, albumSettings) { MediaCollections.folders(library.orEmpty(), albumSettings) }
    // People (Settings → Smart Search → Faces), reloaded as the face pass finds them: at most
    // every couple of seconds, since it saves after every photo.
    val peopleRows by produceState(emptyList<com.subhashh.lumen.data.ai.Person>()) {
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        com.subhashh.lumen.data.ai.FaceStore.changes.debounce(1_500).collect {
            value = withContext(Dispatchers.IO) {
                if (!com.subhashh.lumen.data.ai.FaceModel.installed(context)) emptyList()
                else com.subhashh.lumen.data.ai.FaceStore.of(context).people()
            }
        }
    }
    // Only photos the library shows (no excluded folders, Hidden or trash); named people first.
    val allPeople = remember(peopleRows, library) {
        val byId = library.orEmpty().associateBy { it.id }
        peopleRows.asSequence()
            .mapNotNull { p -> byId[p.coverMediaId]?.let { p.copy(mediaIds = p.mediaIds.filterTo(HashSet()) { it in byId }) to it } }
            .filter { it.first.mediaIds.isNotEmpty() }
            .sortedWith(compareBy<Pair<com.subhashh.lumen.data.ai.Person, MediaItem>> { it.first.name == null }.thenByDescending { it.first.mediaIds.size })
            .toList()
    }
    val people = remember(allPeople) { allPeople.filterNot { it.first.hidden } }
    val personOf = { key: String? -> CollectionKeys.personId(key)?.let { id -> allPeople.firstOrNull { it.first.id == id }?.first } }
    /** Merge with…: the People page picks who [mergeFrom] joins. */
    var mergeFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHiddenPeople by rememberSaveable { mutableStateOf(false) }
    var personNameRequest by remember { mutableStateOf<com.subhashh.lumen.data.ai.Person?>(null) }
    var personMenuOpen by remember { mutableStateOf(false) }
    var peopleMenuOpen by remember { mutableStateOf(false) }
    var peopleSquare by remember { mutableStateOf(prefs.peopleSquare) }
    /** The open person came from the People page, so Back returns there. */
    var personFromPeople by rememberSaveable { mutableStateOf(false) }
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
    var collectionsMenuOpen by remember { mutableStateOf(false) }
    /** Photos waiting for Resize or Convert's choices. */
    var resizeRequest by remember { mutableStateOf<List<MediaItem>?>(null) }
    /** The photo being framed in Set as Wallpaper. */
    var wallpaperItem by remember { mutableStateOf<MediaItem?>(null) }
    /** Items waiting for a new date from Adjust Date & Time. */
    var dateRequest by remember { mutableStateOf<List<MediaItem>?>(null) }
    /** Album name prompt: renaming [album], or making a new one when null. */
    var albumNameRequest by remember { mutableStateOf<AlbumNameRequest?>(null) }
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
    // Words read in photos (Settings → Smart Search → Text Read): exact, so right after the
    // name and date matches. A full-text lookup, a few ms, so no typing pause.
    val textMatches by produceState<List<Long>?>(null, searchQuery) {
        val query = searchQuery.trim()
        value = if (query.length < 2) null else withContext(Dispatchers.IO) {
            if (!com.subhashh.lumen.data.ai.OcrModel.installed(context)) null
            else com.subhashh.lumen.data.ai.TextStore.of(context).search(query)
        }
    }
    // Opening Search loads Smart Search's text model and vectors while the user types.
    LaunchedEffect(searching) { if (searching) SmartSearch.prepare(context) }
    // Smart Search: by what's in the photos, once a model is downloaded and photos are indexed.
    // A short pause after typing, so the text model runs once per word, not once per letter.
    // Paired with the query it answers, so the screen can tell when it's still working.
    val smart by produceState<Pair<String, com.subhashh.lumen.data.ai.SmartResult?>?>(null, searchQuery) {
        if (searchQuery.isBlank()) {
            value = null
            return@produceState
        }
        delay(250)
        value = searchQuery to SmartSearch.search(context, searchQuery.trim())
    }
    val smartHits = smart?.second?.hits
    val smartPending = searchQuery.isNotBlank() && smart?.first != searchQuery &&
        remember(searching) { com.subhashh.lumen.data.ai.ModelDownloads.installed(context) != null }
    // People named like the search (every word starts one of the name's words): exact, so
    // right after name and date matches.
    val personMatches = remember(people, searchQuery) {
        val terms = SearchIndex.terms(searchQuery)
        if (terms.isEmpty()) emptyList()
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
    val searchSections = remember(keywordResults, personMatches, textMatches, smartHits, library, searchDetails) {
        val keyword = keywordResults ?: return@remember null
        val byId = library.orEmpty().associateBy { it.id }
        if (!searchDetails) {
            val seen = keyword.mapTo(HashSet()) { it.id }
            val ofPeople = personMatches.flatMap { it.first.mediaIds }.mapNotNull { byId[it] }
                .filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofText = textMatches.orEmpty().mapNotNull { byId[it] }.filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofSmart = smartHits.orEmpty().mapNotNull { hit -> byId[hit.mediaId]?.takeIf { seen.add(it.id) } }
            return@remember listOf(SearchSection(SearchSection.All, "", keyword + ofPeople + ofText + ofSmart))
        }
        listOf(
            SearchSection(SearchSection.Names, "Names, Folders and Dates", keyword),
            SearchSection(
                SearchSection.People, "People",
                personMatches.flatMap { it.first.mediaIds }.distinct().mapNotNull { byId[it] }.sortedByDescending { it.takenAt },
            ),
            SearchSection(
                SearchSection.Text, "Text in Photos",
                textMatches.orEmpty().mapNotNull { byId[it] }.sortedByDescending { it.takenAt },
            ),
            SearchSection(
                SearchSection.Smart, "Smart Search", smartHits.orEmpty().mapNotNull { byId[it.mediaId] },
                scores = smartHits.orEmpty().associate { it.mediaId to it.score },
            ),
        )
    }
    /** Sections opened with See All; closed again for each new search. */
    var searchExpanded by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(searchQuery) { searchExpanded = emptySet() }
    // A face group, not yet named, that Smart Search's matches are about: offered in the People row.
    val unnamedSubject = remember(smart, allPeople) {
        val id = smart?.second?.unnamedSubject?.id ?: return@remember null
        allPeople.firstOrNull { it.first.id == id && !it.first.hidden }
    }
    /** What "Add Name" from Search fills in: the search, as a name. */
    var personNameSuggestion by remember { mutableStateOf<String?>(null) }
    /** The section the viewer was opened from: it pages through that section. */
    var searchSection by rememberSaveable { mutableStateOf<String?>(null) }
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
        listOf("Videos", "Screenshots", "Favorites") + years + folders.take(3).map { it.title }
    }
    var recentSearches by remember { mutableStateOf(prefs.recentSearches) }
    val rememberSearch = { query: String ->
        prefs.addRecentSearch(query)
        recentSearches = prefs.recentSearches
    }
    // Suggested searches from what this library has (Smart Search), each with its best photo;
    // a different handful each time Search opens.
    val smartSuggestions by produceState<List<Pair<String, MediaItem>>>(emptyList(), searching, library) {
        if (!searching) return@produceState
        value = withContext(Dispatchers.IO) {
            val model = com.subhashh.lumen.data.ai.ModelDownloads.installed(context) ?: return@withContext emptyList()
            val byId = library.orEmpty().associateBy { it.id }
            com.subhashh.lumen.data.ai.SearchSuggestions.load(context, model)
                .mapNotNull { s -> byId[s.coverId]?.let { s.term to it } }
                .shuffled()
                .take(8)
        }
    }
    val searchGrid = rememberLazyGridState()
    LaunchedEffect(searchQuery) { searchGrid.scrollToItem(0) }
    val itemsOf = { key: String ->
        when (key) {
            CollectionKeys.Library -> libraryItems
            CollectionKeys.Search -> searchSections?.firstOrNull { it.id == searchSection }?.items.orEmpty()
            CollectionKeys.Hidden -> if (hiddenUnlocked) hidden.orEmpty() else emptyList()
            else -> personOf(key)?.let { person ->
                sort.apply(library.orEmpty().filter { it.id in person.mediaIds }, sortDescending)
            } ?: CollectionKeys.hiddenFolderId(key)?.let { id ->
                if (hiddenUnlocked) hidden.orEmpty().filter { it.bucketId == id } else emptyList()
            } ?: CollectionKeys.items(key, library.orEmpty(), trash).let {
                // Recently Deleted stays in deletion order; albums follow the Library's sort.
                if (key == CollectionKeys.RecentlyDeleted) it else sort.apply(it, sortDescending)
            }
        }
    }
    // Date headers (Settings → Library), only when the grid is in date order.
    var sectionMode by remember { mutableStateOf(prefs.sectionMode) }
    var curvedEdge by remember { mutableStateOf(prefs.curvedEdge) }
    LaunchedEffect(settingsPage) {
        sectionMode = prefs.sectionMode
        curvedEdge = prefs.curvedEdge
    }
    val gridSectionMode = if (sort == LibrarySort.Date) sectionMode else SectionMode.None
    val librarySections = remember(libraryItems, gridSectionMode) { GridSections.of(libraryItems, gridSectionMode) }
    /** The open album's photos and their headers, shared by its grid and the viewer's close flight. */
    val openItems = remember(openCollection, library, trash, hidden, hiddenUnlocked, sort, sortDescending, allPeople) {
        openCollection?.takeIf { it != CollectionKeys.Hidden }?.let(itemsOf)
    }
    val collectionSections = remember(openItems, gridSectionMode, openCollection) {
        openItems?.let { GridSections.of(it, if (openCollection == CollectionKeys.RecentlyDeleted) SectionMode.None else gridSectionMode) }
    }
    val months = remember(libraryItems) { periods(libraryItems, byYear = false) }
    val years = remember(libraryItems) { periods(libraryItems, byYear = true) }
    // A new filter starts at its newest item, like opening the Library.
    LaunchedEffect(filter, sort, sortDescending) {
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

    val viewerOpen = viewerSource != null
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
        openCollection = when {
            openCollection == CollectionKeys.People && mergeFrom != null -> CollectionKeys.person(mergeFrom!!).also { mergeFrom = null }
            CollectionKeys.hiddenFolderId(openCollection.orEmpty()) != null -> CollectionKeys.Hidden
            CollectionKeys.personId(openCollection) != null && personFromPeople -> CollectionKeys.People
            else -> null
        }
    }
    BackHandler(enabled = inSettings && !viewerOpen) { popSettings() }

    // Multi-select: null when not selecting. Lives here so the header, top controls and the
    // bottom action bar (all outside the grid) can share it.
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    /** Album multi-select on Collections and Hidden's folder list: selected album keys. */
    var albumSelection by remember { mutableStateOf<Set<String>?>(null) }
    /** People page multi-select (to merge or hide several): selected person keys. */
    var peopleSelection by remember { mutableStateOf<Set<String>?>(null) }
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
    var selectionMenuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = selectionMenuOpen) { selectionMenuOpen = false }
    /** Set when the viewer should open straight into a slideshow of these ids. */
    var slideshowOf by remember { mutableStateOf<Set<Long>?>(null) }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val activity = LocalActivity.current
    // Settings → Backup → Import Favorites: Fossify's .txt or Aves's .json, matched to files here.
    val favoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val found = FavoritesImport.read(context, uri, library.orEmpty())
            if (found == null) {
                toast("That isn't a Fossify or Aves favorites file")
                return@launch
            }
            val toMark = found.matched.filterNot { it.isFavorite }
            when {
                found.matched.isEmpty() -> toast("None of its ${found.listed} favorites are on this phone")
                toMark.isEmpty() -> toast("All ${found.matched.size} are already favorites")
                else -> {
                    toast("Found ${found.matched.size} of ${found.listed}; marking ${toMark.size} as favorites")
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
            toast(if (ok) "Settings exported" else "Couldn't save the backup")
        }
    }
    val restoreFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        activity?.recreate()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = SettingsBackup.import(context, uri)
            if (imported == null) {
                toast("That isn't a Lumen backup")
                return@launch
            }
            toast("Settings restored")
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
                title = "Hiding needs All files access",
                message = "Android's media index skips hidden photos on purpose, so Lumen has to work with storage " +
                    "directly to hide and show them. Nothing ever leaves your phone.",
                confirm = "Open Settings",
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
    var encryptHidden by remember { mutableStateOf(LumenPrefs(appContext).encryptHidden) }
    /** Turning Encrypt Hidden on (true) or off (false), waiting for the lock to be confirmed. */
    var pendingEncryption by remember { mutableStateOf<Boolean?>(null) }
    /** Moving files into or out of the vault: what's happening and how far along, or null. */
    var vaultProgress by remember { mutableStateOf<VaultProgress?>(null) }
    /** The lock as last typed, kept only until Hidden opens: the vault's recovery path needs it. */
    var enteredSecret by remember { mutableStateOf<String?>(null) }
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
                title = "Reset Hidden lock?",
                message = "Your phone has no screen lock, so anyone holding it could do this. Hidden photos stay hidden; " +
                    "you'll set a new lock next.",
                confirm = "Reset",
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
                if (items.isEmpty()) afterHiddenChange(if (restored == vaulted.size) "Unhidden" else "Couldn't unhide ${vaulted.size - restored}")
            }
            if (items.isEmpty()) return@unhide
        }
        val plans = items.mapNotNull(HiddenMedia::unhidePlan).distinct()
        val doable = plans.filterNot { it is HiddenMedia.Unhide.DotFolder }
        if (doable.isEmpty()) {
            val folder = plans.filterIsInstance<HiddenMedia.Unhide.DotFolder>().firstOrNull()?.folder?.name
            toast("“$folder” is hidden by its dot-name; rename that folder in a file manager to unhide it")
            return@unhide
        }
        val run = { scope.launch { afterHiddenChange(if (HiddenMedia.unhide(context, doable) > 0) "Unhidden" else "Couldn't unhide") } }
        val folders = doable.filterIsInstance<HiddenMedia.Unhide.Folder>()
        if (folders.isEmpty()) {
            run()
        } else {
            val folderSet = folders.mapTo(HashSet()) { it.folder.absolutePath }
            val affected = hidden.orEmpty().count { HiddenMedia.file(it)?.parentFile?.absolutePath in folderSet }
            val name = folders.singleOrNull()?.folder?.name
            confirmation = Confirmation(
                title = if (name != null) "Unhide “$name”?" else "Unhide ${folders.size} folders?",
                message = "These are hidden as whole folders, so all $affected items in them show up again " +
                    "in Lumen's library and in other gallery apps.",
                confirm = "Unhide",
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
                afterHiddenChange(if (n == items.size) "Hidden and encrypted. Find it in Collections → Hidden" else "Hid $n of ${items.size}")
            }
            return@hide
        }
        scope.launch {
            val paths = withContext(Dispatchers.IO) { items.mapNotNull { MediaFiles.filePath(context, it) } }
            val n = HiddenMedia.hide(context, paths)
            afterHiddenChange(if (n == items.size) "Hidden. Find it in Collections → Hidden" else "Hid $n of ${items.size}")
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
                title = if (name != null) "Hide “$name”?" else "Hide ${folders.size} folders?",
                message = if (encrypt) {
                    "All $affected items in ${if (name != null) "it" else "them"} will be encrypted into Lumen's vault: " +
                        "no other app can see them. They'll be in Collections → Hidden."
                } else {
                    "All $affected items in ${if (name != null) "it" else "them"} will be hidden from Lumen's library and " +
                        "from apps that use Android's media index, like Google Photos. File managers, and galleries that " +
                        "look at storage directly, can still see them. They'll be in Collections → Hidden."
                },
                confirm = "Hide",
            ) {
                if (encrypt && Vault.open(appContext)) {
                    scope.launch {
                        val inFolders = library.orEmpty().filter { File(root, it.relativePath).absolutePath in paths }
                        val n = VaultMigration.hideIntoVault(context, inFolders) { MediaFiles.filePath(context, it) }
                        if (!hiddenUnlocked) Vault.close()
                        afterHiddenChange(if (n == inFolders.size) "Hidden and encrypted" else "Hid $n of ${inFolders.size}")
                    }
                    return@Confirmation
                }
                scope.launch {
                    val n = HiddenMedia.hideFolders(context, folders)
                    afterHiddenChange(if (n == paths.size) "Hidden. Find it in Collections → Hidden" else "Couldn't hide some folders")
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
                toast("This vault was made with a different Lumen lock; enter that one to open it")
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
                    result.failed > 0 -> "${result.moved} done, ${result.failed} couldn't be ${if (on) "encrypted" else "decrypted"} and stayed as they were"
                    on -> if (result.moved == 0) "Encrypt Hidden is on" else "Encrypted ${result.moved} hidden items"
                    else -> "Decrypted ${result.moved} items; they're hidden the usual way again"
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
            title = if (n == 1) "Delete this item?" else "Delete $n items?",
            message = "Hidden items can't go to Recently Deleted, so this is permanent.",
            confirm = "Delete",
            destructive = true,
        ) {
            scope.launch {
                val (vaulted, plain) = items.partition(Vault::isVault)
                val deleted = HiddenMedia.delete(plain) + vaulted.count(Vault::delete)
                if (vaulted.isNotEmpty()) viewModel.loadVault()
                afterHiddenChange(if (deleted == n) "Deleted" else "Couldn't delete ${n - deleted} of $n")
            }
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
            (if (name != null) "Excluded “$name”." else "Excluded ${folders.size} folders.") +
                " Undo in Settings → Excluded folders",
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
            (if (name != null) "Excluded “$name”." else "Excluded ${folders.size} folders.") +
                " Undo in Settings → Excluded folders",
        )
    }

    // Changing files Lumen didn't create: with All files access go straight ahead; otherwise ask
    // MediaStore for write access first (one dialog per batch, none with Media management on).
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
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

    /** Items waiting for an album to be picked, and whether to move or copy them. */
    var albumRequest by remember { mutableStateOf<AlbumRequest?>(null) }
    val toAlbum: (AlbumRequest, AlbumTarget) -> Unit = { request, target ->
        if (request.move) {
            withWriteAccess(request.items) {
                scope.launch {
                    val result = MediaFiles.move(context, request.items, target.relativePath)
                    finish(fileMessage("Moved", "move", result, target.title))
                }
            }
        } else {
            scope.launch {
                val result = MediaFiles.copy(context, request.items, target.relativePath)
                finish(fileMessage("Copied", "copy", result, target.title))
            }
        }
    }
    val rotate: (List<MediaItem>, Int) -> Unit = { items, degrees ->
        val rotatable = items.filter(MediaFiles::canRotate)
        if (rotatable.isNotEmpty()) {
            withWriteAccess(rotatable) {
                scope.launch {
                    // Videos are remuxed, which takes a moment for long ones.
                    if (rotatable.any { it.isVideo }) toast("Rotating…")
                    val result = MediaFiles.rotate(context, rotatable, degrees)
                    // Success shows itself (the photo turns); only a failure needs a message.
                    finish(if (result.failed == 0) null else "Couldn't rotate ${result.failed} of ${rotatable.size}")
                }
            }
        }
    }
    /** The item being renamed and its current file name (null while loading / closed). */
    var renameItem by remember { mutableStateOf<MediaItem?>(null) }
    var renameName by remember { mutableStateOf<String?>(null) }
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
        // Reduce Motion: the viewer just appears, no flight out of the cell.
        if (reduceMotion) {
            viewerStart = index
            viewerSource = key
            return@open
        }
        scope.launch {
            val target = fullScreenRect(item, screenSize.width.toFloat(), screenSize.height.toFloat())
            transition.open(item, target, viewerBackground)
            viewerStart = index
            viewerSource = key
            withFrameNanos { }
            withFrameNanos { }
            transition.finish()
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
        // Everything the glass controls refract lives inside this layer.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                searching -> SearchScreen(
                    query = searchQuery,
                    sections = searchSections,
                    lookingInside = smartPending,
                    expanded = searchExpanded,
                    onToggleSection = { id -> searchExpanded = if (id in searchExpanded) searchExpanded - id else searchExpanded + id },
                    activeSection = searchSection,
                    albums = searchAlbums,
                    people = personMatches,
                    unnamed = unnamedSubject,
                    onOpenPerson = { key ->
                        rememberSearch(searchQuery)
                        searching = false
                        settingsPage = null
                        tab = LumenTab.Collections
                        openCollection = key
                    },
                    onNamePerson = { person ->
                        personNameSuggestion = searchQuery.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                        personNameRequest = person
                    },
                    recent = recentSearches,
                    suggested = smartSuggestions,
                    suggestions = if (smartSuggestions.isEmpty()) searchSuggestions else emptyList(),
                    onClearRecent = {
                        prefs.recentSearches = emptyList()
                        recentSearches = emptyList()
                    },
                    gridState = searchGrid,
                    contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                    transition = transition,
                    onOpen = { section, index ->
                        rememberSearch(searchQuery)
                        searchSection = section
                        openViewer(CollectionKeys.Search, index)
                    },
                    onOpenAlbum = { key ->
                        searching = false
                        settingsPage = null
                        tab = LumenTab.Collections
                        openCollection = key
                    },
                    onSuggestion = { searchQuery = it },
                    picked = if (pick?.multiple == true) picked.keys else null,
                    columns = searchColumns,
                    onColumnsChange = {
                        searchColumns = it
                        prefs.searchColumns = it
                    },
                )
                library == null -> Unit
                settingsPage == SettingsLicenses -> LicensesScreen(
                    contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                )
                settingsPage == SettingsMain || settingsPage?.startsWith(SettingsCategoryPrefix) == true -> SettingsScreen(
                    category = SettingsCategory.of(settingsPage?.removePrefix(SettingsCategoryPrefix)),
                    highlight = settingsHighlight,
                    onOpenCategory = { category, highlight ->
                        settingsHighlight = highlight
                        settingsPage = settingsCategoryPage(category)
                    },
                    contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                    onOpenLicenses = { settingsPage = SettingsLicenses },
                    onConfirm = { confirmation = it },
                    onEnterHfToken = { enteringHfToken = true },
                    hfTokenVersion = hfTokenVersion,
                    onOpenExcluded = { settingsPage = SettingsExcluded },
                    onOpenHidden = {
                        // Hidden lives in Collections; go there, then unlock.
                        settingsPage = null
                        tab = LumenTab.Collections
                        openHidden()
                    },
                    hiddenLockSet = lockSet,
                    onChangeLock = { lockFlow = if (lockSet) LockFlow.Change else LockFlow.Setup },
                    biometricsOn = lockSet && biometrics,
                    onToggleBiometrics = toggle@{
                        val host = activity ?: return@toggle
                        if (biometrics) {
                            lockStore.biometrics = false
                            biometrics = false
                        } else if (HiddenLock.biometricsAvailable(host)) {
                            // Turning it on proves the fingerprint or face works first.
                            HiddenLock.biometric(host, "Cancel", onSuccess = {
                                lockStore.biometrics = true
                                biometrics = true
                            }, onFallback = {})
                        } else {
                            toast("Add a fingerprint or face in your phone's settings first")
                        }
                    },
                    excludedCount = excluded.size,
                    encryptHidden = encryptHidden,
                    onToggleEncryptHidden = toggle@{ on ->
                        if (!lockStore.isSet) {
                            toast("Set up a Lumen lock first (Privacy → Set Up Lumen Lock)")
                            return@toggle
                        }
                        if (needsAllFiles()) return@toggle
                        confirmation = if (on) {
                            Confirmation(
                                title = "Encrypt Hidden?",
                                message = (hidden?.filterNot(Vault::isVault)?.size?.let { "$it hidden items move" } ?: "Everything in Hidden moves") +
                                    " into an encrypted vault that no other app or file manager can read (excluded folders stay as they are). " +
                                    "It stays on your storage, so it survives uninstalling Lumen; your Lumen lock opens it again.\n\n" +
                                    "If you uninstall Lumen and forget your lock, these files can't be recovered by anyone. " +
                                    "A password is safer than a short PIN.",
                                confirm = "Encrypt",
                            ) {
                                pendingEncryption = true
                                lockFlow = LockFlow.Confirm
                            }
                        } else {
                            Confirmation(
                                title = "Stop encrypting Hidden?",
                                message = "Everything in the vault is decrypted back to where it was, hidden the usual way: " +
                                    "out of your library, but visible to file managers and some galleries.",
                                confirm = "Decrypt",
                            ) {
                                pendingEncryption = false
                                lockFlow = LockFlow.Confirm
                            }
                        }
                    },
                    appLockOn = appLockOn,
                    onToggleAppLock = { on ->
                        if (on && !lockStore.isSet) {
                            // App Lock uses the same lock as Hidden: set one up first.
                            setupForAppLock = true
                            lockFlow = LockFlow.Setup
                        } else {
                            prefs.appLock = on
                            appLockOn = on
                            activity?.let(AppLock::settingsChanged)
                        }
                    },
                    onExport = {
                        exportLauncher.launch("Lumen settings ${java.time.LocalDate.now()}.json")
                    },
                    onImportFavorites = {
                        favoritesLauncher.launch(arrayOf("text/plain", "application/json", "application/octet-stream"))
                    },
                    onImport = {
                        confirmation = Confirmation(
                            title = "Import settings?",
                            message = "Your current settings, album arrangement and exclusions are replaced by the backup's. " +
                                "Favorites in it are added to yours.",
                            confirm = "Import",
                        ) { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                    },
                )
                settingsPage == SettingsExcluded -> {
                    val all = allItems.orEmpty()
                    // Hidden folders count too, once Hidden has been scanned.
                    val hiddenFiles = hiddenAll.orEmpty()
                    ExcludedFoldersScreen(
                        excluded = remember(excluded, all, hiddenFiles) {
                            excluded.sorted().map { folder ->
                                folder to (all.count { it.relativePath.startsWith(folder) } +
                                    hiddenFiles.count { it.relativePath.startsWith(folder) })
                            }
                        },
                        suggestions = remember(excluded, all) { ExcludedFolders.suggestions(all, excluded) },
                        contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                        onInclude = viewModel::include,
                        onExclude = { viewModel.exclude(listOf(it)) },
                        onChooseFolder = { excludePicker = true },
                    )
                }
                tab == LumenTab.Library -> Box(Modifier.nestedScroll(zoomBarScroll)) {
                    LibraryScreen(
                        title = pickTitle ?: selection?.let { selectionTitle(selectedItems) } ?: "Library",
                        selection = if (pick?.multiple == true) picked.keys else selection,
                        onSelectionChange = {
                            when {
                                pick?.multiple == true -> setPicked(it, libraryItems)
                                pick == null -> selection = it
                            }
                        },
                        aspectRatioGrid = aspectRatioGrid,
                        columns = gridColumns,
                        onColumnsChange = onGridColumns,
                        emptyText = filter.emptyText,
                        startAtEnd = chronological,
                        showMonth = sort == LibrarySort.Date,
                        scrollLabel = scrollLabel,
                        sections = librarySections,
                        curvedEdge = curvedEdge,
                        items = libraryItems,
                        gridState = libraryGrid,
                        contentPadding = gridPadding,
                        transition = transition,
                        onOpen = { index -> openViewer(CollectionKeys.Library, index) },
                        // Stays composed under Years/Months (hidden, so not drawn): coming back to
                        // All is instant and keeps its scroll position.
                        modifier = Modifier.graphicsLayer { alpha = if (zoomLevel == ZoomLevel.All) 1f else 0f },
                    )
                    AnimatedContent(
                        targetState = zoomLevel,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(160)))
                        },
                        label = "zoomLevel",
                    ) { level ->
                        if (level != ZoomLevel.All) {
                            TimelineScreen(
                                periods = if (level == ZoomLevel.Years) years else months,
                                level = level,
                                listState = if (level == ZoomLevel.Years) yearsList else monthsList,
                                contentPadding = gridPadding,
                                emptyText = filter.emptyText,
                                onOpen = { period ->
                                    if (level == ZoomLevel.Years) {
                                        zoomLevel = ZoomLevel.Months
                                        val first = months.indexOfFirst { it.year == period.year }
                                        if (first >= 0) scope.launch { monthsList.scrollToItem(first) }
                                    } else {
                                        zoomLevel = ZoomLevel.All
                                        scope.launch { libraryGrid.scrollToItem(librarySections.gridIndexOf(period.firstIndex)) }
                                    }
                                },
                            )
                        } else {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
                openCollection == CollectionKeys.Hidden -> HiddenScreen(
                    encrypted = encryptHidden,
                    folders = hiddenFolderList,
                    gridState = hiddenGrid,
                    title = albumSelection?.let { albumSelectionTitle(it.size, "Folder") } ?: "Hidden",
                    columns = albumColumns,
                    onColumnsChange = onAlbumColumns,
                    selection = albumSelection,
                    onSelectionChange = { if (pick == null) albumSelection = it },
                    contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                    onOpen = { openCollection = it },
                )
                openCollection == CollectionKeys.People -> {
                    val from = mergeFrom?.let { id -> allPeople.firstOrNull { it.first.id == id }?.first }
                    PeopleScreen(
                        people = when {
                            from != null -> allPeople.filter { it.first.id != from.id && (!it.first.hidden || showHiddenPeople) }
                            showHiddenPeople -> allPeople
                            else -> people
                        },
                        title = when {
                            from != null -> "Merge with…"
                            peopleSelection.isNullOrEmpty() -> if (peopleSelection != null) "Select People" else "People"
                            else -> "${peopleSelection!!.size} Selected"
                        },
                        selection = if (from == null) peopleSelection else null,
                        onSelectionChange = { if (from == null && pick == null) peopleSelection = it },
                        square = peopleSquare,
                        gridState = peopleGrid,
                        contentPadding = PaddingValues(top = statusTop + 64.dp, bottom = navBottom + 96.dp),
                        onOpen = { key ->
                            val target = personOf(key)
                            if (from != null && target != null) {
                                val fromName = from.name ?: "this person"
                                confirmation = Confirmation(
                                    title = "Merge People?",
                                    message = "Photos of $fromName and ${target.name ?: "the person you chose"} will show as one person" +
                                        ((target.name ?: from.name)?.let { ", named $it." } ?: "."),
                                    confirm = "Merge",
                                    onConfirm = {
                                        mergeFrom = null
                                        personFromPeople = false
                                        openCollection = key
                                        scope.launch(Dispatchers.IO) {
                                            com.subhashh.lumen.data.ai.FaceStore.of(context).merge(keep = target.id, gone = from.id)
                                        }
                                    },
                                )
                            } else {
                                personFromPeople = true
                                openCollection = key
                            }
                        },
                    )
                }
                openCollection != null -> {
                    val key = openCollection!!
                    LibraryScreen(
                        title = pickTitle ?: selection?.let { selectionTitle(selectedItems) }
                            ?: personOf(key)?.let { it.name ?: "Add Name" }
                            ?: (utilities + folders).firstOrNull { it.key == key }?.title
                            ?: hiddenFolderList?.firstOrNull { it.collection.key == key }?.collection?.title.orEmpty(),
                        selection = if (pick?.multiple == true) picked.keys else selection,
                        onSelectionChange = {
                            when {
                                pick?.multiple == true -> setPicked(it, itemsOf(key))
                                pick == null -> selection = it
                            }
                        },
                        aspectRatioGrid = aspectRatioGrid,
                        columns = gridColumns,
                        onColumnsChange = onGridColumns,
                        items = openItems ?: itemsOf(key),
                        sections = collectionSections,
                        curvedEdge = curvedEdge,
                        startAtEnd = chronological || key == CollectionKeys.RecentlyDeleted,
                        showMonth = sort == LibrarySort.Date || key == CollectionKeys.RecentlyDeleted,
                        scrollLabel = if (key == CollectionKeys.RecentlyDeleted) { item -> ScrollMonth.format(java.util.Date(item.takenAt)) } else scrollLabel,
                        gridState = collectionGrid,
                        contentPadding = PaddingValues(top = statusTop + 152.dp, bottom = navBottom + 96.dp),
                        titleTopPadding = 64.dp,
                        emptyText = if (key.startsWith("empty:")) "This album is empty.\nAdd photos with ⋯ → Move to Album or Copy to Album."
                        else "No photos or videos",
                        transition = transition,
                        onOpen = { index -> openViewer(key, index) },
                    )
                }
                else -> CollectionsScreen(
                    title = albumSelection?.let { albumSelectionTitle(it.size, "Album") } ?: "Collections",
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
                    onOpen = { key ->
                        personFromPeople = false
                        if (key == CollectionKeys.Hidden) openHidden() else openCollection = key
                    },
                )
            }
        }


        if (selecting && !viewerOpen) {
            val albums = albumSelection
            val chosenPeople = peopleSelection
            val peopleKeys = (if (showHiddenPeople) allPeople else people).map { CollectionKeys.person(it.first.id) }
            SelectionTopControls(
                backdrop = backdrop,
                allSelected = when {
                    chosenPeople != null -> chosenPeople.size == peopleKeys.size && peopleKeys.isNotEmpty()
                    albums != null -> albums.size == albumKeys.size && albumKeys.isNotEmpty()
                    else -> selectedItems.size == selectableItems.size && selectableItems.isNotEmpty()
                },
                onToggleAll = {
                    if (chosenPeople != null) {
                        peopleSelection = if (chosenPeople.size == peopleKeys.size) emptySet() else peopleKeys.toHashSet()
                    } else if (albums != null) {
                        albumSelection = if (albums.size == albumKeys.size) emptySet() else albumKeys.toHashSet()
                    } else {
                        selection = if (selectedItems.size == selectableItems.size) emptySet()
                        else selectableItems.mapTo(HashSet()) { it.id }
                    }
                },
                onCancel = { endSelection() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (searching && !viewerOpen) {
            SearchField(
                backdrop = backdrop,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                autoFocus = focusSearch,
                onAutoFocused = { focusSearch = false },
                onSubmit = { rememberSearch(searchQuery) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (openCollection == CollectionKeys.RecentlyDeleted && tab == LumenTab.Collections && trash.isNotEmpty() &&
            !selecting && !viewerOpen && !searching && settingsPage == null && pick == null
        ) {
            GlassTextButton(
                backdrop = backdrop,
                text = "Empty",
                onClick = {
                    val n = trash.size
                    // With Media management on, Android deletes without asking; so Lumen always asks.
                    confirmation = Confirmation(
                        title = "Empty Recently Deleted?",
                        message = if (n == 1) "This item will be deleted forever." else "All $n items will be deleted forever.",
                        confirm = "Delete Forever",
                        destructive = true,
                    ) {
                        selectionLauncher.launch(
                            IntentSenderRequest.Builder(MediaActions.deleteForeverRequest(context, trash)).build(),
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (pick != null && !searching && !viewerOpen) {
            GlassTextButton(
                backdrop = backdrop,
                text = "Cancel",
                onClick = onCancelPick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (pick?.multiple == true && picked.isNotEmpty()) {
            GlassTextButton(
                backdrop = backdrop,
                text = "Add ${picked.size}",
                onClick = { onPicked(picked.values.toList()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = navBottom + 12.dp + 66.dp + 12.dp),
            )
        }

        if (tab == LumenTab.Library && !searching && selection == null && !viewerOpen && settingsPage == null && pick == null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.Filter,
                    contentDescription = "View options",
                    onClick = { menuOpen = !menuOpen },
                    // Blue while a filter is on, like iOS, so a filtered library is never a surprise.
                    tint = if (filter != LibraryFilter.All) MaterialTheme.colorScheme.primary else LocalGlassColors.current.content,
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.Settings,
                    contentDescription = "Settings",
                    onClick = { settingsPage = SettingsMain },
                )
            }
        }

        if (tab == LumenTab.Collections && openCollection == null && settingsPage == null && !searching && !viewerOpen && !selecting && pick == null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.Filter,
                    contentDescription = "Album options",
                    onClick = { collectionsMenuOpen = !collectionsMenuOpen },
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.Settings,
                    contentDescription = "Settings",
                    onClick = { settingsPage = SettingsMain },
                )
            }
        }
        BackHandler(enabled = collectionsMenuOpen) { collectionsMenuOpen = false }
        GlassMenu(
            backdrop = backdrop,
            open = collectionsMenuOpen && tab == LumenTab.Collections && openCollection == null && !viewerOpen && settingsPage == null,
            onDismiss = { collectionsMenuOpen = false },
            sections = listOf(
                AlbumSort.entries.map { sort ->
                    MenuItem("Sort by ${sort.label}", checked = sort == albumSettings.sort) {
                        updateAlbums { it.copy(sort = sort) }
                    }
                },
                listOf(MenuItem("New Album…") { albumNameRequest = AlbumNameRequest(album = null) }),
            ),
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        // The People page: circles or rounded squares.
        if (inCollection && openCollection == CollectionKeys.People && !viewerOpen && !selecting) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.Filter,
                contentDescription = "People options",
                onClick = { peopleMenuOpen = !peopleMenuOpen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        BackHandler(enabled = peopleMenuOpen) { peopleMenuOpen = false }
        GlassMenu(
            backdrop = backdrop,
            open = peopleMenuOpen && openCollection == CollectionKeys.People && inCollection,
            onDismiss = { peopleMenuOpen = false },
            sections = listOf(
                listOf(false to "Circles", true to "Squares").map { (square, label) ->
                    MenuItem(label, checked = peopleSquare == square) {
                        peopleSquare = square
                        prefs.peopleSquare = square
                    }
                },
                listOf(MenuItem("Show Hidden People", checked = showHiddenPeople) { showHiddenPeople = !showHiddenPeople }),
            ),
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        // A person's album: ⋯ for naming, merging and hiding them.
        val openPerson = if (inCollection && !viewerOpen && !selecting && pick == null) personOf(openCollection) else null
        if (openPerson != null) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.More,
                contentDescription = "Person options",
                onClick = { personMenuOpen = !personMenuOpen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        BackHandler(enabled = personMenuOpen) { personMenuOpen = false }
        GlassMenu(
            backdrop = backdrop,
            open = personMenuOpen && openPerson != null,
            onDismiss = { personMenuOpen = false },
            sections = listOf(
                listOf(
                    MenuItem(if (openPerson?.name == null) "Add Name…" else "Rename…") { personNameRequest = openPerson },
                    MenuItem("Merge with…") {
                        mergeFrom = openPerson?.id
                        openCollection = CollectionKeys.People
                    },
                ),
                listOf(
                    if (openPerson?.hidden == true) MenuItem("Unhide Person") {
                        val id = openPerson.id
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.data.ai.FaceStore.of(context).setHidden(id, false) }
                    } else MenuItem("Hide Person") {
                        val id = openPerson?.id ?: return@MenuItem
                        openCollection = if (personFromPeople) CollectionKeys.People else null
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.data.ai.FaceStore.of(context).setHidden(id, true) }
                    },
                ),
            ),
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        if ((inCollection || inSettings) && !viewerOpen) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.Back,
                contentDescription = "Back",
                onClick = {
                    when {
                        inSettings -> popSettings()
                        openCollection == CollectionKeys.People && mergeFrom != null -> {
                            openCollection = CollectionKeys.person(mergeFrom!!)
                            mergeFrom = null
                        }
                        CollectionKeys.hiddenFolderId(openCollection.orEmpty()) != null -> openCollection = CollectionKeys.Hidden
                        CollectionKeys.personId(openCollection) != null && personFromPeople -> openCollection = CollectionKeys.People
                        else -> openCollection = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

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

        ViewMenu(
            backdrop = backdrop,
            open = menuOpen && tab == LumenTab.Library && !viewerOpen && settingsPage == null,
            onDismiss = { menuOpen = false },
            filter = filter,
            onFilter = {
                filter = it
                prefs.libraryFilter = it
            },
            aspectRatioGrid = aspectRatioGrid,
            onAspectRatioGrid = {
                aspectRatioGrid = it
                prefs.aspectRatioGrid = it
            },
            sort = sort,
            descending = sortDescending,
            onSort = { newSort, descending ->
                sort = newSort
                sortDescending = descending
                prefs.librarySort = newSort
                prefs.sortDescending = descending
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        AnimatedVisibility(
            visible = peopleSelection != null && !viewerOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 12.dp),
        ) {
            val chosen = peopleSelection.orEmpty().mapNotNull { personOf(it) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                GlassTextButton(backdrop, "Merge", onClick = merge@{
                    if (chosen.size < 2) {
                        toast("Select two or more people to merge")
                        return@merge
                    }
                    // The named one stays (else whoever has the most photos); the rest join them.
                    val keep = chosen.sortedWith(compareBy<com.subhashh.lumen.data.ai.Person> { it.name == null }.thenByDescending { it.mediaIds.size }).first()
                    val names = chosen.mapNotNull { it.name }.distinct()
                    confirmation = Confirmation(
                        title = "Merge ${chosen.size} People?",
                        message = "Their photos will show as one person" + (keep.name?.let { ", named $it." } ?: ".") +
                            if (names.size > 1) " The other names (${names.filter { it != keep.name }.joinToString()}) go." else "",
                        confirm = "Merge",
                        onConfirm = {
                            endSelection()
                            scope.launch(Dispatchers.IO) {
                                val store = com.subhashh.lumen.data.ai.FaceStore.of(context)
                                for (p in chosen) if (p.id != keep.id) store.merge(keep = keep.id, gone = p.id)
                            }
                        },
                    )
                })
                GlassTextButton(backdrop, if (chosen.isNotEmpty() && chosen.all { it.hidden }) "Unhide" else "Hide", onClick = hide@{
                    if (chosen.isEmpty()) return@hide
                    val hide = !chosen.all { it.hidden }
                    endSelection()
                    scope.launch(Dispatchers.IO) {
                        val store = com.subhashh.lumen.data.ai.FaceStore.of(context)
                        for (p in chosen) store.setHidden(p.id, hide)
                    }
                })
            }
        }

        AnimatedVisibility(
            visible = selecting && peopleSelection == null && !viewerOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 12.dp),
        ) {
            val launch = { sender: android.content.IntentSender ->
                selectionLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            val allFavorite = selectedItems.isNotEmpty() && selectedItems.all { it.isFavorite }
            SelectionActionBar(
                backdrop = backdrop,
                hasSelection = selectedItems.isNotEmpty(),
                inTrash = selectionScope == CollectionKeys.RecentlyDeleted,
                inHidden = CollectionKeys.isHidden(selectionScope),
                // Favoriting whole albums isn't a thing.
                showFavorite = albumSelection == null,
                allFavorite = allFavorite,
                onShare = {
                    if (CollectionKeys.isHidden(selectionScope)) shareHidden(selectedItems)
                    else MediaActions.share(context, selectedItems)
                },
                onFavorite = { launch(MediaActions.favoriteRequest(context, selectedItems, favorite = !allFavorite)) },
                onDelete = {
                    // Empty new albums have nothing to trash; deleting one just forgets it.
                    val emptyPaths = folders.filter { it.key in albumSelection.orEmpty() && it.count == 0 }.map { it.relativePath }.toSet()
                    if (emptyPaths.isNotEmpty()) updateAlbums { it.copy(created = it.created.filterNot(emptyPaths::contains)) }
                    if (selectedItems.isNotEmpty()) launch(MediaActions.trashRequest(context, selectedItems)) else endSelection()
                },
                onRestore = { launch(MediaActions.restoreRequest(context, selectedItems)) },
                onDeleteForever = {
                    if (CollectionKeys.isHidden(selectionScope)) deleteHidden(selectedItems)
                    else launch(MediaActions.deleteForeverRequest(context, selectedItems))
                },
                onUnhide = { unhide(selectedItems) },
                onMore = { selectionMenuOpen = !selectionMenuOpen },
            )
        }

        GlassMenu(
            backdrop = backdrop,
            open = selectionMenuOpen && selecting && !viewerOpen,
            onDismiss = { selectionMenuOpen = false },
            sections = if (CollectionKeys.isHidden(selectionScope)) {
                // Hidden files can't be moved around like library ones; they can be left out of Hidden.
                val many = if (albumSelection != null) albumSelection.orEmpty().size > 1
                else selectedItems.map { it.relativePath }.distinct().size > 1
                listOf(
                    listOf(MenuItem(if (many) "Exclude Folders" else "Exclude Folder") { excludeFromHidden(selectedItems) }),
                )
            } else if (albumSelection != null) {
                // Whole albums: arrange them, move or copy their contents, or hide / exclude the folders.
                val chosen = folders.filter { it.key in albumSelection.orEmpty() }
                listOf(
                    listOfNotNull(
                        chosen.singleOrNull()?.let { album -> MenuItem("Rename Album…") { albumNameRequest = AlbumNameRequest(album) } },
                        if (chosen.isNotEmpty() && chosen.all { it.pinned }) {
                            MenuItem("Unpin") {
                                val paths = chosen.map { it.relativePath }.toSet()
                                updateAlbums { it.copy(pinned = it.pinned.filterNot(paths::contains)) }
                                endSelection()
                            }
                        } else {
                            MenuItem("Pin to Top") {
                                val add = chosen.filterNot { it.pinned }.map { it.relativePath }
                                updateAlbums { it.copy(pinned = it.pinned + add) }
                                endSelection()
                            }
                        },
                        if (chosen.any { it.customCover }) {
                            MenuItem("Reset Cover") {
                                val paths = chosen.map { it.relativePath }.toSet()
                                updateAlbums { it.copy(covers = it.covers.filterKeys { p -> p !in paths }) }
                                endSelection()
                            }
                        } else null,
                    ),
                    listOf(
                        MenuItem("Move to Album…") { albumRequest = AlbumRequest(move = true, items = selectedItems) },
                        MenuItem("Copy to Album…") { albumRequest = AlbumRequest(move = false, items = selectedItems) },
                    ),
                    listOf(
                        MenuItem(if (albumSelection.orEmpty().size > 1) "Hide Folders" else "Hide Folder") { hideFolders(selectedItems) },
                        MenuItem(if (albumSelection.orEmpty().size > 1) "Exclude Folders" else "Exclude Folder") {
                            excludeFolders(selectedItems)
                        },
                    ),
                )
            } else listOfNotNull(
                // In a person's album: these photos aren't them.
                personOf(selectableKey)?.let { person ->
                    listOf(
                        MenuItem(notPersonLabel(person)) {
                            val ids = selectedItems.map { it.id }
                            endSelection()
                            scope.launch(Dispatchers.IO) { com.subhashh.lumen.data.ai.FaceStore.of(context).notThisPerson(person.id, ids) }
                        },
                    )
                },
                listOf(
                    MenuItem("Move to Album…") { albumRequest = AlbumRequest(move = true, items = selectedItems) },
                    MenuItem("Copy to Album…") { albumRequest = AlbumRequest(move = false, items = selectedItems) },
                ),
                listOfNotNull(
                    if (selectedItems.any(MediaFiles::canRotate)) rotateMenu { rotate(selectedItems, it) } else null,
                    MenuItem("Adjust Date & Time…") { dateRequest = selectedItems },
                    if (selectedItems.any { !it.isVideo }) MenuItem("Resize or Convert…") {
                        resizeRequest = selectedItems.filterNot { it.isVideo }
                    } else null,
                ),
                listOf(MenuItem("Hide") { hide(selectedItems) }),
                listOf(
                    MenuItem("Slideshow") {
                        val key = selectableKey ?: return@MenuItem
                        val ids = selectedItems.mapTo(HashSet()) { it.id }
                        val first = selectableItems.indexOfFirst { it.id in ids }
                        if (first < 0) return@MenuItem
                        slideshowOf = ids
                        endSelection()
                        openViewer(key, first)
                    },
                ),
            ),
            origin = TransformOrigin(1f, 1f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = navBottom + 12.dp + 48.dp + 12.dp, end = 16.dp),
        )

        viewerSource?.let { source ->
            val list = remember(source, library, trash, searchSections, searchSection) { itemsOf(source) }
            val grid = when (source) {
                CollectionKeys.Library -> libraryGrid
                CollectionKeys.Search -> searchGrid
                else -> collectionGrid
            }
            ViewerScreen(
                items = list,
                initialIndex = viewerStart.coerceAtMost(list.lastIndex.coerceAtLeast(0)),
                inTrash = source == CollectionKeys.RecentlyDeleted,
                inHidden = CollectionKeys.isHidden(source),
                onShareHidden = { shareHidden(listOf(it)) },
                onUnhide = { unhide(listOf(it)) },
                onDeleteHidden = { deleteHidden(listOf(it)) },
                onClose = { exit ->
                    viewerSource = null
                    slideshowOf = null
                    val item = list.getOrNull(exit.index)
                    val from = exit.photoRect
                    if (item != null && from != null && !reduceMotion) {
                        // Same frame the viewer goes: the photo keeps flying from where it was.
                        transition.beginClose(item, from, exit.background, exit.backgroundAlpha)
                        scope.launch {
                            // Bring the photo's cell into view first, like iOS, then land in it.
                            // Grids with date headers count them too.
                            val gridIndex = when (source) {
                                CollectionKeys.Library -> librarySections.gridIndexOf(exit.index)
                                CollectionKeys.Search -> {
                                    // Paged past the section's first row: open it, so the photo has a cell to land in.
                                    val section = searchSection
                                    if (section != null && exit.index >= searchColumns && section !in searchExpanded) {
                                        searchExpanded = searchExpanded + section
                                        withFrameNanos { } // lay the opened section out first
                                    }
                                    searchGridIndex(
                                        searchAlbums.isNotEmpty(), personMatches.isNotEmpty() || unnamedSubject != null,
                                        searchSections.orEmpty(), smartPending,
                                        searchExpanded, searchColumns, section.orEmpty(), exit.index,
                                    )
                                }
                                else -> collectionSections?.gridIndexOf(exit.index) ?: exit.index
                            }
                            if (grid.layoutInfo.visibleItemsInfo.none { it.index == gridIndex }) {
                                grid.scrollToItem(gridIndex)
                            }
                            withFrameNanos { }
                            transition.land(exit.velocity)
                        }
                    }
                },
                onMediaChanged = viewModel::refresh,
                onCopyToAlbum = { albumRequest = AlbumRequest(move = false, items = listOf(it)) },
                onMoveToAlbum = { albumRequest = AlbumRequest(move = true, items = listOf(it)) },
                onRename = { renameItem = it },
                onRotate = { item, degrees -> rotate(listOf(item), degrees) },
                onExcludeFolder = { excludeFolders(listOf(it)) },
                onAdjustDate = { dateRequest = listOf(it) },
                onSetWallpaper = { wallpaperItem = it },
                onResize = { resizeRequest = listOf(it) },
                // Inside a folder album: make this photo its cover, like iOS's Make Key Photo.
                onMakeCover = when {
                    CollectionKeys.isFolder(source) -> { item ->
                        updateAlbums { it.copy(covers = it.covers + (item.relativePath to item.id)) }
                        toast("Album cover updated")
                    }
                    personOf(source) != null -> { item ->
                        val id = personOf(source)!!.id
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.data.ai.FaceStore.of(context).setCover(id, item.id) }
                        toast("Cover updated")
                    }
                    else -> null
                },
                coverLabel = if (personOf(source) != null) "Make Person Cover" else "Make Album Cover",
                notPerson = personOf(source)?.let { person ->
                    notPersonLabel(person) to { item ->
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.data.ai.FaceStore.of(context).notThisPerson(person.id, listOf(item.id)) }
                        toast("Removed from ${person.name ?: "this person"}")
                    }
                },
                onHide = { hide(listOf(it)) },
                onHideFolder = { hideFolders(listOf(it)) },
                slideshowOf = slideshowOf,
            )
        }

        AlbumPicker(
            open = albumRequest != null,
            title = if (albumRequest?.move == true) "Move to Album" else "Copy to Album",
            albums = folders,
            onPick = { target ->
                albumRequest?.let { toAlbum(it, target) }
                albumRequest = null
            },
            onDismiss = { albumRequest = null },
            newAlbumPath = MediaFiles::newAlbumPath,
        )

        AlbumPicker(
            open = excludePicker,
            title = "Exclude Folder",
            albums = folders,
            onPick = { target ->
                excludePicker = false
                viewModel.exclude(listOf(target.relativePath))
            },
            onDismiss = { excludePicker = false },
            newAlbumPath = MediaFiles::newAlbumPath,
            allowNewAlbum = false,
        )

        lockFlow?.let { flow ->
            HiddenLockScreen(
                flow = flow,
                store = lockStore,
                onSecret = { secret ->
                    enteredSecret = secret
                    // A new lock (set up, changed, or after Forgot) reseals the vault's recovery copy.
                    if (flow == LockFlow.Setup || flow == LockFlow.Change) Vault.changeSecret(appContext, secret)
                },
                onDone = {
                    lockFlow = null
                    lockSet = lockStore.isSet
                    biometrics = lockStore.biometrics
                    // Changing the lock from Settings doesn't open Hidden; setting up or unlocking does.
                    when {
                        pendingEncryption != null -> {
                            val on = pendingEncryption!!
                            pendingEncryption = null
                            runEncryption(on, enteredSecret)
                            enteredSecret = null
                        }
                        setupForAppLock -> {
                            setupForAppLock = false
                            if (lockStore.isSet) {
                                prefs.appLock = true
                                appLockOn = true
                                activity?.let(AppLock::settingsChanged)
                                toast("App Lock is on")
                            }
                        }
                        flow != LockFlow.Change -> showHidden()
                        else -> toast("Lumen lock changed")
                    }
                },
                onCancel = {
                    lockFlow = null
                    pendingEncryption = null
                    enteredSecret = null
                },
                onForgot = resetLock,
            )
        }

        ConfirmDialog(confirmation, onDismiss = { confirmation = null })

        RenameDialog(
            currentName = if (enteringHfToken) "" else null,
            title = "Hugging Face Token",
            confirm = "Save",
            keepExtension = false,
            onRename = { token ->
                enteringHfToken = false
                HfTokenStore(context).token = token
                hfTokenVersion++
            },
            onDismiss = { enteringHfToken = false },
        )

        RenameDialog(
            currentName = renameName,
            onRename = { newName ->
                val item = renameItem
                renameItem = null
                if (item != null) {
                    withWriteAccess(listOf(item)) {
                        scope.launch {
                            val ok = MediaFiles.rename(context, item, newName)
                            finish(if (ok) "Renamed to $newName" else "Couldn't rename: that name may already be taken")
                        }
                    }
                }
            },
            onDismiss = { renameItem = null },
        )

        RenameDialog(
            currentName = personNameRequest?.let { it.name ?: personNameSuggestion ?: "" },
            title = if (personNameRequest?.name == null) "Add Name" else "Rename",
            confirm = "Done",
            keepExtension = false,
            onRename = { name ->
                val person = personNameRequest
                personNameRequest = null
                personNameSuggestion = null
                if (person != null) scope.launch(Dispatchers.IO) { com.subhashh.lumen.data.ai.FaceStore.of(context).rename(person.id, name) }
            },
            onDismiss = {
                personNameRequest = null
                personNameSuggestion = null
            },
        )
        RenameDialog(
            currentName = albumNameRequest?.let { it.album?.title ?: "" },
            title = if (albumNameRequest?.album == null) "New Album" else "Rename Album",
            confirm = if (albumNameRequest?.album == null) "Create" else "Rename",
            keepExtension = false,
            onRename = { name ->
                val album = albumNameRequest?.album
                albumNameRequest = null
                val clean = name.trim().replace('/', '_')
                if (album == null) {
                    // New albums go in Pictures/, where any app may add photos without All files access.
                    val path = MediaFiles.newAlbumPath(clean)
                    if (folders.any { it.relativePath.equals(path, ignoreCase = true) }) {
                        toast("There's already an album called “$clean”")
                    } else {
                        updateAlbums { it.copy(created = it.created + path) }
                        collectionsMenuOpen = false
                        openCollection = CollectionKeys.emptyAlbum(path)
                    }
                } else {
                    val parent = album.relativePath.trimEnd('/').substringBeforeLast('/', "")
                    val newPath = (if (parent.isEmpty()) "" else "$parent/") + clean + "/"
                    when {
                        folders.any { it.relativePath.equals(newPath, ignoreCase = true) } ->
                            toast("There's already an album called “$clean” there")
                        album.count == 0 -> {
                            updateAlbums { it.renamed(album.relativePath, newPath) }
                            endSelection()
                        }
                        else -> {
                            val items = library.orEmpty().filter { it.relativePath == album.relativePath }
                            val run = {
                                scope.launch {
                                    val result = MediaFiles.renameAlbum(context, items, album.relativePath, newPath)
                                    if (result.done > 0) updateAlbums { it.renamed(album.relativePath, newPath) }
                                    finish(
                                        when {
                                            result.failed == 0 -> null
                                            result.needsAllFilesAccess ->
                                                "Couldn't rename “${album.title}”: that folder needs All files access (Settings → Permissions)"
                                            else -> "Renamed ${result.done} of ${items.size}; ${result.failed} couldn't move"
                                        },
                                    )
                                }
                            }
                            if (Environment.isExternalStorageManager()) run() else withWriteAccess(items) { run() }
                        }
                    }
                }
            },
            onDismiss = { albumNameRequest = null },
        )

        wallpaperItem?.let { item ->
            WallpaperScreen(
                item = item,
                onDone = { message ->
                    wallpaperItem = null
                    toast(message)
                },
                onCancel = { wallpaperItem = null },
            )
        }

        ResizeDialog(
            count = resizeRequest?.size,
            onSave = { target, format, quality ->
                val items = resizeRequest.orEmpty()
                resizeRequest = null
                scope.launch {
                    if (items.size > 1) toast("Saving ${items.size} copies…")
                    val result = ImageExport.export(context, items, target, format, quality)
                    val where = result.folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
                    finish(
                        when {
                            result.saved == 0 -> "Couldn't save the copy"
                            result.failed > 0 -> "Saved ${result.saved} of ${items.size}"
                            where != null -> if (result.saved == 1) "Saved a copy in $where" else "Saved ${result.saved} copies in $where"
                            else -> "Saved ${result.saved} copies"
                        },
                    )
                }
            },
            onDismiss = { resizeRequest = null },
        )

        vaultProgress?.let { VaultProgressScreen(it.encrypting, it.progress) }

        DateTimeDialog(
            original = dateRequest?.minOf { it.takenAt },
            count = dateRequest?.size ?: 0,
            onAdjust = { newTime ->
                val items = dateRequest.orEmpty()
                dateRequest = null
                if (items.isNotEmpty()) {
                    // Everything moves by the same amount, so the earliest lands where it was set.
                    val delta = newTime - items.minOf { it.takenAt }
                    val run = {
                        scope.launch {
                            val result = MediaDates.shift(context, items, delta)
                            finish(
                                when {
                                    result.failed == 0 -> if (items.size == 1) "Date changed" else "Dates changed"
                                    result.needsAllFilesAccess ->
                                        "Changed ${result.done} of ${items.size}: videos and GIFs need All files access (Settings → Permissions)"
                                    else -> "Changed ${result.done} of ${items.size}: HEIC and AVIF photos can't store a new date"
                                },
                            )
                        }
                    }
                    if (Environment.isExternalStorageManager()) run() else withWriteAccess(items) { run() }
                }
            },
            onDismiss = { dateRequest = null },
        )

        PhotoTransitionOverlay(transition)
    }
}

/** Naming an album: renaming [album], or a brand-new one when it's null. */
private data class AlbumNameRequest(val album: MediaCollection?)

/** "Not Chiara", or "Not This Person" before they're named. */
private fun notPersonLabel(person: com.subhashh.lumen.data.ai.Person) = person.name?.let { "Not $it" } ?: "Not This Person"

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Lumen", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Lumen needs access to your photos and videos. Everything stays on this device.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Allow access") }
    }
}

/** "2 Albums Selected", "1 Folder Selected", or "Select Albums" when empty. */
private fun albumSelectionTitle(n: Int, noun: String): String = when (n) {
    0 -> "Select ${noun}s"
    1 -> "1 $noun Selected"
    else -> "$n ${noun}s Selected"
}

/** "3 Photos Selected", "1 Video Selected", "5 Items Selected", or "Select Items" when empty. */
private fun selectionTitle(selected: List<MediaItem>): String {
    val n = selected.size
    if (n == 0) return "Select Items"
    val noun = when {
        selected.all { !it.isVideo } -> if (n == 1) "Photo" else "Photos"
        selected.all { it.isVideo } -> if (n == 1) "Video" else "Videos"
        else -> if (n == 1) "Item" else "Items"
    }
    return "$n $noun Selected"
}

/** [albumScope] for Collections' album list. */
private const val AlbumsScope = "albums"

private const val SettingsMain = "settings"
private const val SettingsCategoryPrefix = "settings/"
private fun settingsCategoryPage(category: SettingsCategory) = SettingsCategoryPrefix + category.key
private const val SettingsLicenses = "licenses"
private const val SettingsExcluded = "excluded"

private data class AlbumRequest(val move: Boolean, val items: List<MediaItem>)

/** One line after a copy or move: what happened, and why anything didn't. */
private fun fileMessage(past: String, verb: String, result: FileResult, album: String): String {
    val count = if (result.done == 1) "1 item" else "${result.done} items"
    return when {
        result.failed == 0 && result.done == 0 -> "Already in $album"
        result.failed == 0 -> "$past $count to $album"
        result.needsAllFilesAccess ->
            "Couldn't $verb into $album: that folder needs All files access (Settings → Permissions)"
        result.done == 0 -> "Couldn't $verb to $album"
        else -> "$past $count to $album; ${result.failed} failed"
    }
}

private val ScrollMonth = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())

/** How far to scroll one way before the bottom bar switches between tabs and Years / Months / All. */
private val ZoomBarSwitchDistance = 40.dp

/** Moving files into or out of the vault, and how far along (null while it gets ready). */
private data class VaultProgress(val encrypting: Boolean, val progress: VaultMigration.Progress?)
