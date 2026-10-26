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

    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAccess = context.hasMediaAccess()
        if (hasAccess) {
            viewModel.refresh()
        } else if (activity != null && MediaPermissions.none { activity.shouldShowRequestPermissionRationale(it) }) {
            // Denied twice, Android answers at once without asking, so the button would do nothing:
            // App Info is the only way left. Coming back re-checks access (on resume, below).
            context.openSpecial(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    // Re-check on resume: access can change in system settings, and new photos may have been taken.
    LifecycleResumeEffect(Unit) {
        hasAccess = context.hasMediaAccess()
        if (hasAccess) viewModel.refresh()
        onPauseOrDispose { }
    }

    LifecycleStartEffect(hasAccess) {
        viewModel.watchMedia(hasAccess)
        onStopOrDispose { viewModel.watchMedia(false) }
    }

    // First launch: onboarding. Someone who already had access before onboarding existed has
    // been using Lumen, so they skip it.
    val prefs = remember { com.subhashh.lumen.data.LumenPrefs(context) }
    // Read from prefs each time the screen is built (not saved state): Show Onboarding in
    // Developer Options clears it and restarts the activity.
    /** Onboarding just ended: its photos fly into the Library's grid. */
    var entrance by remember { mutableStateOf<com.subhashh.lumen.ui.onboarding.OnboardingExit?>(null) }
    var onboarded by remember {
        mutableStateOf(prefs.onboardingDone ?: context.hasMediaAccess().also { prefs.onboardingDone = it })
    }
    val showOnboarding = !onboarded && pick == null
    Box(Modifier.fillMaxSize()) {
        // One call site for the app whether onboarding is up or not: it's built underneath once
        // access is granted, so when onboarding ends its grid is already laid out and scrolled
        // for the photos to fly into, with nothing to build at that moment.
        if (hasAccess) {
            MainScaffold(viewModel, pick, onPicked, onCancelPick, entrance, onEntranceDone = { entrance = null })
        } else if (!showOnboarding) {
            PermissionScreen(onGrant = { launcher.launch(MediaPermissions) })
        }
        // Another app asking for a photo goes straight to the picker (or the access prompt).
        if (showOnboarding) {
            val items by viewModel.items.collectAsState()
            com.subhashh.lumen.ui.onboarding.Onboarding(
                hasAccess = hasAccess,
                onRequestAccess = { launcher.launch(MediaPermissions) },
                // Oldest to newest, as the grid runs: the newest lands bottom right.
                photos = remember(items) { items.orEmpty().sortedBy { it.takenAt }.takeLast(40) },
                onFinish = { exit ->
                    prefs.onboardingDone = true
                    entrance = exit
                    onboarded = true
                },
            )
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: LibraryViewModel,
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
    /** Picking several: what's chosen so far, in order, kept across screens. */
    var picked by remember { mutableStateOf<Map<Long, MediaItem>>(emptyMap()) }
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
    var viewerStart by rememberSaveable { mutableIntStateOf(0) }
    /** A new viewer each time one opens (a parked one is replaced, not reused). */
    var viewerSession by remember { mutableIntStateOf(0) }
    /** A person opened from the viewer's details sheet: the viewer waits, parked, to come back to. */
    var viewerReturn by remember { mutableStateOf<ViewerReturn?>(null) }

    val backdrop = rememberLayerBackdrop()
    // Hoisted so grids keep their position across tab switches and while the viewer is open.
    val libraryGrid = rememberLazyGridState()
    val collectionGrid = key(openCollection) { rememberLazyGridState() }
    // Collections and Hidden's folder list keep their place while a folder is open.
    val collectionsGrid = rememberLazyGridState()
    val hiddenGrid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val transition = remember { PhotoTransition() }
    val coverTransition = remember { CoverTransition() }
    var coverOpening by remember { mutableStateOf(false) }
    val reduceMotion = com.subhashh.lumen.ui.theme.LocalMotion.current.reduce
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val viewerBackground = MaterialTheme.colorScheme.background

    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    // An update is waiting (found by Software Update, now or on an earlier day): a dot on Settings.
    val installedVersion = remember { com.subhashh.lumen.data.Updater.installedVersion(context) }
    val updateWaiting = (((UpdateSession.state as? UpdateSession.State.Result)?.check as? com.subhashh.lumen.data.Updater.Check.Available)?.release?.version
        ?: prefs.availableUpdate)?.let { com.subhashh.lumen.data.Updater.isNewer(it, installedVersion) } == true
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
        MediaCollections.utilities(context, library.orEmpty(), trash).filter {
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
    val peopleRows by produceState(emptyList<com.subhashh.lumen.ai.Person>()) {
        val load = suspend {
            withContext(Dispatchers.IO) {
                if (!com.subhashh.lumen.ai.FaceModel.installed(context)) emptyList()
                else com.subhashh.lumen.ai.FaceStore.of(context).people()
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
    /** Merge with…: the People page picks who [mergeFrom] joins. */
    var mergeFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHiddenPeople by rememberSaveable { mutableStateOf(false) }
    var personNameRequest by remember { mutableStateOf<com.subhashh.lumen.ai.Person?>(null) }
    /** A name that another person already has: merge them, or keep both with it. */
    var mergeProposal by remember { mutableStateOf<MergeProposal?>(null) }
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
    /** Sections opened with See All; closed again for each new search. */
    var searchExpanded by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(searchQuery) { searchExpanded = emptySet() }
    // A face group, not yet named, that Smart Search's matches are about: offered in the People row.
    // Smart Search's result is from before any naming, so once it's named it shows as a match instead.
    val unnamedSubject = remember(smart, allPeople) {
        val id = smart?.second?.unnamedSubject?.id ?: return@remember null
        allPeople.firstOrNull { it.first.id == id && !it.first.hidden && it.first.name == null }
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
        // Words the search index knows (in English for now), so they stay untranslated.
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
            CollectionKeys.Search -> searchSections?.firstOrNull { it.id == searchSection }?.items.orEmpty()
            CollectionKeys.Hidden -> if (hiddenUnlocked) hidden.orEmpty() else emptyList()
            else -> personOf(key)?.let { person ->
                sort.apply(library.orEmpty().filter { it.id in person.mediaIds }, coverPageDescending)
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
    // Date headers (Settings → Library), only when the grid is in date order.
    var sectionMode by remember { mutableStateOf(prefs.sectionMode) }
    var curvedEdge by remember { mutableStateOf(prefs.curvedEdge) }
    LaunchedEffect(settingsPage) {
        sectionMode = prefs.sectionMode
        curvedEdge = prefs.curvedEdge
    }
    val gridSectionMode = if (sort == LibrarySort.Date) sectionMode else SectionMode.None
    val librarySections = remember(libraryItems, gridSectionMode) { GridSections.of(context, libraryItems, gridSectionMode) }
    /** The open album's photos and their headers, shared by its grid and the viewer's close flight. */
    val openItems = remember(openCollection, library, trash, hidden, hiddenUnlocked, sort, sortDescending, allPeople) {
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
            // Its own lambda (a separate JVM method): MainScaffold is at the 64 KB method limit.
            val screens: @Composable () -> Unit = {
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
                                HiddenLock.biometric(host, context.getString(R.string.common_cancel), onSuccess = {
                                    lockStore.biometrics = true
                                    biometrics = true
                                }, onFallback = {})
                            } else {
                                toast(context.getString(R.string.main_biometrics_missing))
                            }
                        },
                        excludedCount = excluded.size,
                        encryptHidden = encryptHidden,
                        onToggleEncryptHidden = toggle@{ on ->
                            if (!lockStore.isSet) {
                                toast(context.getString(R.string.main_lock_needed_for_encrypt))
                                return@toggle
                            }
                            if (needsAllFiles()) return@toggle
                            confirmation = if (on) {
                                Confirmation(
                                    title = context.getString(R.string.main_encrypt_hidden_title),
                                    message = hidden?.filterNot(Vault::isVault)?.size?.let { context.resources.getQuantityString(R.plurals.main_encrypt_hidden_message_count, it, it) }
                                        ?: context.getString(R.string.main_encrypt_hidden_message_all),
                                    confirm = context.getString(R.string.main_encrypt),
                                ) {
                                    pendingEncryption = true
                                    lockFlow = LockFlow.Confirm
                                }
                            } else {
                                Confirmation(
                                    title = context.getString(R.string.main_decrypt_hidden_title),
                                    message = context.getString(R.string.main_decrypt_hidden_message),
                                    confirm = context.getString(R.string.main_decrypt),
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
                                title = context.getString(R.string.main_import_settings_title),
                                message = context.getString(R.string.main_import_settings_message),
                                confirm = context.getString(R.string.main_import),
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
                            title = pickTitle(pick, picked.size) ?: selection?.let { selectionTitle(selectedItems) } ?: stringResource(R.string.main_library),
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
                            emptyText = stringResource(filter.emptyText),
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
                                    emptyText = stringResource(filter.emptyText),
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
                        title = albumSelection?.let { albumSelectionTitle(it.size, folders = true) } ?: stringResource(R.string.main_hidden),
                        columns = albumColumns,
                        onColumnsChange = onAlbumColumns,
                        selection = albumSelection,
                        onSelectionChange = { if (pick == null) albumSelection = it },
                        contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                        onOpen = { openCollection = it },
                    )
                    else -> {
                        // Collections and People keep one place here, under an album or person
                        // opened from them (see coverUnderlay): never rebuilt as its cover flies.
                        val key = openCollection
                        val fromPeople = CollectionKeys.personId(key) != null && personFromPeople
                        val coverPage = key != null && (CollectionKeys.isFolder(key) || CollectionKeys.personId(key) != null)
                        if (key == null || (coverPage && !fromPeople)) {
                            collectionsPage(if (key == null) Modifier else Modifier.coverUnderlay(coverTransition))
                        }
                        if (key == CollectionKeys.People || (coverPage && fromPeople)) {
                            peoplePage(if (key == CollectionKeys.People) Modifier else Modifier.coverUnderlay(coverTransition))
                        }
                        if (key != null && key != CollectionKeys.People) {
                            val cover = heroWithLabel(key)
                            val items = openItems ?: itemsOf(key)
                            LibraryScreen(
                                title = pickTitle(pick, picked.size) ?: selection?.let { selectionTitle(selectedItems) }
                                    ?: personOf(key)?.let { it.name ?: stringResource(R.string.main_add_name) }
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
                                items = items,
                                sections = collectionSections,
                                curvedEdge = curvedEdge,
                                startAtEnd = (chronological && cover == null) || key == CollectionKeys.RecentlyDeleted,
                                showMonth = sort == LibrarySort.Date || key == CollectionKeys.RecentlyDeleted,
                                scrollLabel = if (key == CollectionKeys.RecentlyDeleted) { item -> ScrollMonth.format(java.util.Date(item.takenAt)) } else scrollLabel,
                                gridState = collectionGrid,
                                contentPadding = PaddingValues(top = if (cover != null) heroHeight + 2.dp else statusTop + 152.dp, bottom = navBottom + 96.dp),
                                titleTopPadding = 64.dp,
                                hero = cover?.let { c -> { heroModifier -> CoverHeader(c, coverTransition, heroModifier) } },
                                heroHeight = heroHeight,
                                modifier = Modifier.coverPage(coverTransition),
                                emptyText = stringResource(
                                    if (key.startsWith("empty:")) R.string.main_album_empty_hint else R.string.main_no_photos_or_videos,
                                ),
                                transition = transition,
                                onOpen = { index -> openViewer(key, index) },
                            )
                        }
                    }
                }
            }
            screens()
            if (!coverTransition.overViewer) CoverTransitionOverlay(coverTransition)
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
                text = stringResource(R.string.main_empty_trash),
                onClick = {
                    val n = trash.size
                    // With Media management on, Android deletes without asking; so Lumen always asks.
                    confirmation = Confirmation(
                        title = context.getString(R.string.main_empty_trash_title),
                        message = if (n == 1) context.getString(R.string.main_empty_trash_message_one)
                        else context.resources.getQuantityString(R.plurals.main_empty_trash_message, n, n),
                        confirm = context.getString(R.string.main_delete_forever_title_case),
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
                text = stringResource(R.string.common_cancel),
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
                text = stringResource(R.string.main_pick_add_count, picked.size),
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
                    contentDescription = stringResource(R.string.main_view_options),
                    onClick = { menuOpen = !menuOpen },
                    // Blue while a filter is on, like iOS, so a filtered library is never a surprise.
                    tint = if (filter != LibraryFilter.All) MaterialTheme.colorScheme.primary else LocalGlassColors.current.content,
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.Settings,
                    contentDescription = stringResource(R.string.common_settings),
                    onClick = { settingsPage = SettingsMain },
                    badge = updateWaiting,
                )
            }
        }

        // While an album's or person's cover flies, Collections' buttons go with the page under it.
        val coverFromPeople = CollectionKeys.personId(openCollection) != null && personFromPeople
        val coverOverCollections = coverTransition.flying && !coverFromPeople && !coverTransition.overViewer
        if (tab == LumenTab.Collections && (openCollection == null || coverOverCollections) && settingsPage == null && !searching && !viewerOpen && !selecting && pick == null) {
            val leaving = Modifier.graphicsLayer {
                val c = if (openCollection == null) 0f else coverTransition.chrome()
                alpha = 1f - c
                scaleX = 1f - 0.3f * c
                scaleY = 1f - 0.3f * c
            }
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
                    contentDescription = stringResource(R.string.main_album_options),
                    onClick = { collectionsMenuOpen = !collectionsMenuOpen },
                    modifier = leaving,
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.Settings,
                    contentDescription = stringResource(R.string.common_settings),
                    onClick = { settingsPage = SettingsMain },
                    badge = updateWaiting,
                    // A person's ⋯ grows out of this one instead (below), in its place.
                    modifier = if (openCollection != null && CollectionKeys.personId(openCollection) != null) Modifier.graphicsLayer { alpha = 0f } else leaving,
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
                    MenuItem(context.getString(R.string.main_sort_by, context.getString(sort.label)), checked = sort == albumSettings.sort) {
                        updateAlbums { it.copy(sort = sort) }
                    }
                },
                listOf(MenuItem(stringResource(R.string.main_new_album_ellipsis)) { albumNameRequest = AlbumNameRequest(album = null) }),
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
                contentDescription = stringResource(R.string.main_people_options),
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
                listOf(false to R.string.main_people_circles, true to R.string.main_people_squares).map { (square, label) ->
                    MenuItem(context.getString(label), checked = peopleSquare == square) {
                        peopleSquare = square
                        prefs.peopleSquare = square
                    }
                },
                listOf(MenuItem(stringResource(R.string.main_show_hidden_people), checked = showHiddenPeople) { showHiddenPeople = !showHiddenPeople }),
            ),
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        // A person's album: ⋯ for naming, merging and hiding them.
        val openPerson = if (inCollection && !viewerOpen && !selecting && pick == null) personOf(openCollection) else null
        if (openPerson != null && coverTransition.flying && !coverTransition.overViewer) {
            // The page underneath's rightmost button (Settings, or People's options) turns into ⋯.
            GlassMorphButton(
                backdrop = backdrop,
                from = if (coverFromPeople) LumenIcons.Filter else LumenIcons.Settings,
                to = LumenIcons.More,
                fraction = { coverTransition.chrome() },
                contentDescription = stringResource(R.string.main_person_options),
                onClick = {},
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else if (openPerson != null) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.More,
                contentDescription = stringResource(R.string.main_person_options),
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
                    MenuItem(stringResource(if (openPerson?.name == null) R.string.main_add_name_ellipsis else R.string.main_rename_ellipsis)) { personNameRequest = openPerson },
                    MenuItem(stringResource(R.string.main_merge_with_ellipsis)) {
                        mergeFrom = openPerson?.id
                        openCollection = CollectionKeys.People
                    },
                ),
                listOf(
                    if (openPerson?.hidden == true) MenuItem(stringResource(R.string.main_unhide_person)) {
                        val id = openPerson.id
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).setHidden(id, false) }
                    } else MenuItem(stringResource(R.string.main_hide_person)) {
                        val id = openPerson?.id ?: return@MenuItem
                        openCollection = if (personFromPeople) CollectionKeys.People else null
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).setHidden(id, true) }
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
                contentDescription = stringResource(R.string.common_back),
                onClick = {
                    when {
                        inSettings -> popSettings()
                        openCollection == CollectionKeys.People && mergeFrom != null -> {
                            openCollection = CollectionKeys.person(mergeFrom!!)
                            mergeFrom = null
                        }
                        CollectionKeys.hiddenFolderId(openCollection.orEmpty()) != null -> openCollection = CollectionKeys.Hidden
                        CollectionKeys.personId(openCollection) != null && personFromPeople -> closeCollection(CollectionKeys.People)
                        else -> closeCollection(null)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    // Collections has no Back: it comes and goes with an album's or person's flight.
                    .graphicsLayer {
                        val c = if (coverOverCollections || coverTransition.overViewer) coverTransition.chrome() else 1f
                        alpha = c
                        scaleX = 0.7f + 0.3f * c
                        scaleY = 0.7f + 0.3f * c
                    },
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
                GlassTextButton(backdrop, stringResource(R.string.main_merge), onClick = merge@{
                    if (chosen.size < 2) {
                        toast(context.getString(R.string.main_merge_select_two))
                        return@merge
                    }
                    // The named one stays (else whoever has the most photos); the rest join them.
                    val keep = chosen.sortedWith(compareBy<com.subhashh.lumen.ai.Person> { it.name == null }.thenByDescending { it.mediaIds.size }).first()
                    val names = chosen.mapNotNull { it.name }.distinct()
                    confirmation = Confirmation(
                        title = context.resources.getQuantityString(R.plurals.main_merge_people_count_title, chosen.size, chosen.size),
                        message = when {
                            keep.name == null -> context.getString(R.string.main_merge_many_message)
                            names.size > 1 -> context.getString(
                                R.string.main_merge_many_message_named_others,
                                keep.name,
                                names.filter { it != keep.name }.joinToString(),
                            )
                            else -> context.getString(R.string.main_merge_many_message_named, keep.name)
                        },
                        confirm = context.getString(R.string.main_merge),
                        onConfirm = {
                            endSelection()
                            scope.launch(Dispatchers.IO) {
                                val store = com.subhashh.lumen.ai.FaceStore.of(context)
                                for (p in chosen) if (p.id != keep.id) store.merge(keep = keep.id, gone = p.id)
                            }
                        },
                    )
                })
                GlassTextButton(backdrop, stringResource(if (chosen.isNotEmpty() && chosen.all { it.hidden }) R.string.common_unhide else R.string.common_hide), onClick = hide@{
                    if (chosen.isEmpty()) return@hide
                    val hide = !chosen.all { it.hidden }
                    endSelection()
                    scope.launch(Dispatchers.IO) {
                        val store = com.subhashh.lumen.ai.FaceStore.of(context)
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
                onFavorite = {
                    if (MediaActions.setFavoriteDirectly(context, selectedItems, !allFavorite)) {
                        viewModel.refresh()
                        endSelection()
                    }
                    else launch(MediaActions.favoriteRequest(context, selectedItems, favorite = !allFavorite))
                },
                onDelete = {
                    // Empty new albums have nothing to trash; deleting one just forgets it.
                    val emptyPaths = folders.filter { it.key in albumSelection.orEmpty() && it.count == 0 }.map { it.relativePath }.toSet()
                    if (emptyPaths.isNotEmpty()) updateAlbums { it.copy(created = it.created.filterNot(emptyPaths::contains)) }
                    if (selectedItems.isNotEmpty()) {
                        val items = selectedItems
                        confirmDelete(items, false) { launch(MediaActions.trashRequest(context, items)) }
                    } else {
                        endSelection()
                    }
                },
                onRestore = { launch(MediaActions.restoreRequest(context, selectedItems)) },
                onDeleteForever = {
                    val items = selectedItems
                    if (CollectionKeys.isHidden(selectionScope)) deleteHidden(items)
                    else confirmDelete(items, true) { launch(MediaActions.deleteForeverRequest(context, items)) }
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
                    listOf(MenuItem(stringResource(if (many) R.string.main_exclude_folders else R.string.main_exclude_folder)) { excludeFromHidden(selectedItems) }),
                )
            } else if (albumSelection != null) {
                // Whole albums: arrange them, move or copy their contents, or hide / exclude the folders.
                val chosen = folders.filter { it.key in albumSelection.orEmpty() }
                listOf(
                    listOfNotNull(
                        chosen.singleOrNull()?.let { album -> MenuItem(stringResource(R.string.main_rename_album_ellipsis)) { albumNameRequest = AlbumNameRequest(album) } },
                        if (chosen.isNotEmpty() && chosen.all { it.pinned }) {
                            MenuItem(stringResource(R.string.main_unpin)) {
                                val paths = chosen.map { it.relativePath }.toSet()
                                updateAlbums { it.copy(pinned = it.pinned.filterNot(paths::contains)) }
                                endSelection()
                            }
                        } else {
                            MenuItem(stringResource(R.string.main_pin_to_top)) {
                                val add = chosen.filterNot { it.pinned }.map { it.relativePath }
                                updateAlbums { it.copy(pinned = it.pinned + add) }
                                endSelection()
                            }
                        },
                        if (chosen.any { it.customCover }) {
                            MenuItem(stringResource(R.string.main_reset_cover)) {
                                val paths = chosen.map { it.relativePath }.toSet()
                                updateAlbums { it.copy(covers = it.covers.filterKeys { p -> p !in paths }) }
                                endSelection()
                            }
                        } else null,
                    ),
                    listOf(
                        MenuItem(stringResource(R.string.main_move_to_album_ellipsis)) { albumRequest = AlbumRequest(move = true, items = selectedItems) },
                        MenuItem(stringResource(R.string.main_copy_to_album_ellipsis)) { albumRequest = AlbumRequest(move = false, items = selectedItems) },
                    ),
                    listOf(
                        MenuItem(stringResource(if (albumSelection.orEmpty().size > 1) R.string.main_hide_folders else R.string.main_hide_folder)) { hideFolders(selectedItems) },
                        MenuItem(stringResource(if (albumSelection.orEmpty().size > 1) R.string.main_exclude_folders else R.string.main_exclude_folder)) {
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
                            scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).notThisPerson(person.id, ids) }
                        },
                    )
                },
                listOf(
                    MenuItem(stringResource(R.string.main_move_to_album_ellipsis)) { albumRequest = AlbumRequest(move = true, items = selectedItems) },
                    MenuItem(stringResource(R.string.main_copy_to_album_ellipsis)) { albumRequest = AlbumRequest(move = false, items = selectedItems) },
                ),
                listOfNotNull(
                    if (selectedItems.any(MediaFiles::canRotate)) rotateMenu { rotate(selectedItems, it) } else null,
                    MenuItem(stringResource(R.string.main_adjust_date_time_ellipsis)) { dateRequest = selectedItems },
                    if (selectedItems.any { !it.isVideo }) MenuItem(stringResource(R.string.main_resize_or_convert_ellipsis)) {
                        resizeRequest = selectedItems.filterNot { it.isVideo }
                    } else null,
                ),
                listOf(MenuItem(stringResource(R.string.common_hide)) { hide(selectedItems) }),
                listOf(
                    MenuItem(stringResource(R.string.main_slideshow)) {
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

        viewerSource?.let { source -> key(viewerSession) {
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
                people = allPeople,
                onSearchText = { query ->
                    // Live Text's Search: the selected words, in Lumen's search.
                    viewerSource = null
                    slideshowOf = null
                    settingsPage = null
                    searchQuery = query
                    searching = true
                },
                onOpenPerson = open@{ person ->
                    // That person's page over the viewer, its face growing into the header. The
                    // viewer waits underneath (parked) for Back, from wherever it was opened.
                    if (coverTransition.flying || coverOpening) return@open
                    val key = CollectionKeys.person(person.id)
                    val cover = heroWithLabel(key)?.let { it.copy(cellKey = viewerFaceKey(key)) }
                    coverOpening = true
                    scope.launch {
                        try {
                            val flies = cover != null && coverTransition.prepare(context, cover, heroRect(), overViewer = true)
                            viewerReturn = ViewerReturn(key, tab, openCollection, searching, personFromPeople)
                            slideshowOf = null
                            searching = false
                            settingsPage = null
                            tab = LumenTab.Collections
                            personFromPeople = false
                            openCollection = key
                            if (flies) coverTransition.open()
                        } finally {
                            coverTransition.finish()
                            coverOpening = false
                        }
                    }
                },
                onUnhide = { unhide(listOf(it)) },
                onDeleteHidden = { deleteHidden(listOf(it)) },
                onConfirmDelete = { item, forever, proceed -> confirmDelete(listOf(item), forever, proceed) },
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
                        toast(context.getString(R.string.main_album_cover_updated))
                    }
                    personOf(source) != null -> { item ->
                        val id = personOf(source)!!.id
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).setCover(id, item.id) }
                        toast(context.getString(R.string.main_cover_updated))
                    }
                    else -> null
                },
                coverLabel = stringResource(if (personOf(source) != null) R.string.main_make_person_cover else R.string.main_make_album_cover),
                notPerson = personOf(source)?.let { person ->
                    notPersonLabel(person) to { item ->
                        scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).notThisPerson(person.id, listOf(item.id)) }
                        toast(context.getString(R.string.main_removed_from, person.name ?: context.getString(R.string.main_this_person)))
                    }
                },
                onHide = { hide(listOf(it)) },
                onHideFolder = { hideFolders(listOf(it)) },
                slideshowOf = slideshowOf,
                coverTransition = coverTransition,
                parked = viewerReturn != null,
                modifier = Modifier.parkedViewer(coverTransition, parked = viewerReturn != null),
            )
        } }

        AlbumPicker(
            open = albumRequest != null,
            title = stringResource(if (albumRequest?.move == true) R.string.main_move_to_album else R.string.main_copy_to_album),
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
            title = stringResource(R.string.main_exclude_folder),
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
                    // A new lock (set up, changed, or after Forgot) reseals the vault's recovery copy;
                    // entering the current one upgrades an older copy to Argon2id. Both take a
                    // second or two, so they run in the background.
                    kotlinx.coroutines.CoroutineScope(Vault.keyWork).launch {
                        runCatching {
                            if (flow == LockFlow.Setup || flow == LockFlow.Change) Vault.changeSecret(appContext, secret)
                            else Vault.upgradeRecovery(appContext, secret)
                        }.onFailure { android.util.Log.w("LumenVault", "Couldn't update the recovery file", it) }
                    }
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
                                toast(context.getString(R.string.main_app_lock_on))
                            }
                        }
                        flow != LockFlow.Change -> showHidden()
                        else -> toast(context.getString(R.string.main_lock_changed))
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

        // A face in the viewer's sheet flying to or from its person's page: above the viewer.
        if (coverTransition.overViewer) CoverTransitionOverlay(coverTransition)

        // A newer Lumen from the daily check: announced once per version, when nothing else is up.
        val announce = UpdateSession.announce
        if (announce != null && confirmation == null && !viewerOpen && !selecting && settingsPage == null && pick == null) {
            LaunchedEffect(announce) {
                UpdateSession.announce = null
                prefs.announcedUpdate = announce.version
                confirmation = Confirmation(
                    title = context.getString(R.string.settings_update_available, announce.version),
                    message = context.getString(R.string.main_update_message),
                    details = com.subhashh.lumen.ui.components.releaseChanges(announce.notes).ifBlank { null },
                    confirm = context.getString(R.string.main_update_view),
                    cancel = context.getString(R.string.main_update_later),
                ) {
                    settingsHighlight = "software-update"
                    settingsPage = settingsCategoryPage(SettingsCategory.About)
                }
            }
        }
        // Hidden on screen (its grid, a folder, or the viewer opened from it): no screenshots unless allowed.
        com.subhashh.lumen.ui.hidden.SecureWhile(
            hiddenUnlocked && (CollectionKeys.isHidden(openCollection) || CollectionKeys.isHidden(viewerSource)) && !prefs.hiddenScreenshots,
        )
        com.subhashh.lumen.ui.components.StarRequest(
            prefs,
            canShow = announce == null && confirmation == null && !viewerOpen && !selecting && settingsPage == null && pick == null,
            show = { confirmation = it },
        )
        ConfirmDialog(confirmation, onDismiss = { confirmation = null })

        RenameDialog(
            currentName = if (enteringHfToken) "" else null,
            title = stringResource(R.string.main_hf_token_title),
            confirm = stringResource(R.string.common_save),
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
                            finish(if (ok) context.getString(R.string.main_renamed_to, newName) else context.getString(R.string.main_rename_failed))
                        }
                    }
                }
            },
            onDismiss = { renameItem = null },
        )

        RenameDialog(
            currentName = personNameRequest?.let { it.name ?: personNameSuggestion ?: "" },
            title = stringResource(if (personNameRequest?.name == null) R.string.main_add_name else R.string.common_rename),
            confirm = stringResource(R.string.common_done),
            keepExtension = false,
            acceptUnchanged = personNameRequest?.name == null,
            onRename = { name ->
                val person = personNameRequest
                personNameRequest = null
                personNameSuggestion = null
                val proposal = person?.let { mergeProposalFor(it, name, allPeople) }
                if (proposal != null) mergeProposal = proposal
                else if (person != null) scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).rename(person.id, name) }
            },
            onDismiss = {
                personNameRequest = null
                personNameSuggestion = null
            },
        )
        MergePeopleDialog(
            proposal = mergeProposal,
            onMerge = { p ->
                mergeProposal = null
                // Its page becomes the merged person's.
                if (openCollection == CollectionKeys.person(p.person.id)) openCollection = CollectionKeys.person(p.match.id)
                scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).merge(keep = p.match.id, gone = p.person.id) }
            },
            onKeepSeparate = { p ->
                mergeProposal = null
                scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).rename(p.person.id, p.name) }
            },
            onDismiss = { mergeProposal = null },
        )
        RenameDialog(
            currentName = albumNameRequest?.let { it.album?.title ?: "" },
            title = stringResource(if (albumNameRequest?.album == null) R.string.main_new_album else R.string.main_rename_album),
            confirm = stringResource(if (albumNameRequest?.album == null) R.string.main_create else R.string.common_rename),
            keepExtension = false,
            onRename = { name ->
                val album = albumNameRequest?.album
                albumNameRequest = null
                val clean = name.trim().replace('/', '_')
                if (album == null) {
                    // New albums go in Pictures/, where any app may add photos without All files access.
                    val path = MediaFiles.newAlbumPath(clean)
                    if (folders.any { it.relativePath.equals(path, ignoreCase = true) }) {
                        toast(context.getString(R.string.main_album_exists, clean))
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
                            toast(context.getString(R.string.main_album_exists_there, clean))
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
                                                context.getString(R.string.main_rename_album_needs_access, album.title)
                                            else -> context.getString(R.string.main_rename_album_partial, result.done, items.size, result.failed)
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
                    if (items.size > 1) toast(context.resources.getQuantityString(R.plurals.main_saving_copies, items.size, items.size))
                    val result = ImageExport.export(context, items, target, format, quality)
                    val where = result.folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
                    finish(
                        when {
                            result.saved == 0 -> context.getString(R.string.main_save_copy_failed)
                            result.failed > 0 -> context.getString(R.string.main_saved_n_of, result.saved, items.size)
                            where != null -> if (result.saved == 1) context.getString(R.string.main_saved_copy_in, where)
                            else context.resources.getQuantityString(R.plurals.main_saved_copies_in, result.saved, result.saved, where)
                            else -> context.resources.getQuantityString(R.plurals.main_saved_copies, result.saved, result.saved)
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
                                    result.failed == 0 -> context.getString(if (items.size == 1) R.string.main_date_changed else R.string.main_dates_changed)
                                    result.needsAllFilesAccess ->
                                        context.getString(R.string.main_dates_changed_needs_access, result.done, items.size)
                                    else -> context.getString(R.string.main_dates_changed_heic, result.done, items.size)
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
        entrance?.let { exit ->
            com.subhashh.lumen.ui.onboarding.Entrance(exit, cellOf = { transition.cellRect(it) }, onDone = onEntranceDone)
        }
    }
}

/** Another person already called [name] (any case), with both faces' photos; null when the name's free. */
private fun mergeProposalFor(
    person: com.subhashh.lumen.ai.Person,
    name: String,
    people: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
): MergeProposal? {
    val wanted = name.trim()
    if (wanted.isEmpty()) return null
    val match = people.firstOrNull { (other, _) -> other.id != person.id && other.name?.trim().equals(wanted, ignoreCase = true) } ?: return null
    val cover = people.firstOrNull { it.first.id == person.id }?.second ?: return null
    return MergeProposal(person, cover, match.first, match.second, match.first.name!!.trim())
}

/** Where a person opened from the viewer's details sheet goes back to: the viewer, over this. */
private data class ViewerReturn(
    val person: String,
    val tab: LumenTab,
    val openCollection: String?,
    val searching: Boolean,
    val personFromPeople: Boolean,
)

/** Share of the screen's height an album's or person's cover takes at the top of its page. */
private const val HeroShare = 0.46f

/** Naming an album: renaming [album], or a brand-new one when it's null. */
private data class AlbumNameRequest(val album: MediaCollection?)

/** "Not Chiara", or "Not This Person" before they're named. */
@Composable
private fun notPersonLabel(person: com.subhashh.lumen.ai.Person) =
    person.name?.let { stringResource(R.string.main_not_person, it) } ?: stringResource(R.string.main_not_this_person)

/** The picker's title: what's asked for, or how many are chosen; null when not picking. */
@Composable
private fun pickTitle(pick: PickRequest?, pickedCount: Int): String? = pick?.let {
    if (it.multiple && pickedCount > 0) pluralStringResource(R.plurals.main_n_selected, pickedCount, pickedCount)
    else stringResource(it.title)
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.main_permission_message),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.main_allow_access)) }
    }
}

/** "2 Albums Selected", "1 Folder Selected", or "Select Albums" when empty. */
@Composable
private fun albumSelectionTitle(n: Int, folders: Boolean): String = when {
    n == 0 -> stringResource(if (folders) R.string.main_select_folders else R.string.main_select_albums)
    else -> pluralStringResource(if (folders) R.plurals.main_folders_selected else R.plurals.main_albums_selected, n, n)
}

/** "3 Photos Selected", "1 Video Selected", "5 Items Selected", or "Select Items" when empty. */
@Composable
private fun selectionTitle(selected: List<MediaItem>): String {
    val n = selected.size
    if (n == 0) return stringResource(R.string.main_select_items)
    val plurals = when {
        selected.all { !it.isVideo } -> R.plurals.main_photos_selected
        selected.all { it.isVideo } -> R.plurals.main_videos_selected
        else -> R.plurals.main_items_selected
    }
    return pluralStringResource(plurals, n, n)
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
private fun fileMessage(context: Context, move: Boolean, result: FileResult, album: String): String {
    val res = context.resources
    return when {
        result.failed == 0 && result.done == 0 -> context.getString(R.string.main_file_already_in, album)
        result.failed == 0 ->
            res.getQuantityString(if (move) R.plurals.main_file_moved else R.plurals.main_file_copied, result.done, result.done, album)
        result.needsAllFilesAccess ->
            context.getString(if (move) R.string.main_file_move_needs_access else R.string.main_file_copy_needs_access, album)
        result.done == 0 -> context.getString(if (move) R.string.main_file_move_failed else R.string.main_file_copy_failed, album)
        else -> res.getQuantityString(
            if (move) R.plurals.main_file_moved_some_failed else R.plurals.main_file_copied_some_failed,
            result.done,
            result.done,
            album,
            result.failed,
        )
    }
}

private val ScrollMonth = java.text.SimpleDateFormat(
    android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMMyyyy"),
    java.util.Locale.getDefault(),
)

/** How far to scroll one way before the bottom bar switches between tabs and Years / Months / All. */
private val ZoomBarSwitchDistance = 40.dp

/** Moving files into or out of the vault, and how far along (null while it gets ready). */
private data class VaultProgress(val encrypting: Boolean, val progress: VaultMigration.Progress?)
