package com.subhashh.lumen.ui

import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
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

/**
 * Everything [MainScaffold] remembers: which tab, page and viewer are open, what is selected, which
 * menus and dialogs are up, and the view options read from settings. One holder, so the scaffold
 * and its parts (in their own files) can share it. Values worked out from it and from the library
 * (lists, sections, actions) stay in the scaffold and are handed to the parts that need them.
 */
@Stable
internal class MainState(
    val context: Context,
    val appContext: Context,
    val prefs: LumenPrefs,
    val lockStore: HiddenLockStore,
    val viewModel: LibraryViewModel,
    val scope: CoroutineScope,
    pick: PickRequest?,
    tabState: MutableState<LumenTab>,
    searchingState: MutableState<Boolean>,
    searchQueryState: MutableState<String>,
    openCollectionState: MutableState<String?>,
    settingsPageState: MutableState<String?>,
    viewerSourceState: MutableState<String?>,
    viewerStartState: androidx.compose.runtime.MutableIntState,
    zoomLevelState: MutableState<ZoomLevel>,
    zoomBarState: MutableState<Boolean>,
    mergeFromState: MutableState<Long?>,
    showHiddenPeopleState: MutableState<Boolean>,
    personFromPeopleState: MutableState<Boolean>,
    searchSectionState: MutableState<String?>,
) {
    /** Picking several: what's chosen so far, in order, kept across screens. */
    var picked by mutableStateOf<Map<Long, MediaItem>>(emptyMap())
    /** Hidden opens only after biometric unlock and re-locks whenever it's left. */
    var hiddenUnlocked by mutableStateOf(false)
    var confirmation by mutableStateOf<Confirmation?>(null)
    var enteringHfToken by mutableStateOf(false)
    var hfTokenVersion by mutableIntStateOf(0)
    /** Hidden's own lock screen, when up; and whether a lock exists yet (for "Set Up"). */
    var lockFlow by mutableStateOf<LockFlow?>(null)
    var lockSet by mutableStateOf(lockStore.isSet)
    /** Mirrors lockStore.biometrics so the Settings toggle redraws the moment it changes. */
    var biometrics by mutableStateOf(lockStore.biometrics)
    var appLockOn by mutableStateOf(LumenPrefs(appContext).appLock)
    /** A lock is being set up so App Lock can turn on (not to open Hidden). */
    var setupForAppLock by mutableStateOf(false)
    var excludePicker by mutableStateOf(false)
    var tab by tabState
    var searching by searchingState
    var searchQuery by searchQueryState
    /** Keyboard up when search is opened, but not when the field returns after the viewer. */
    var focusSearch by mutableStateOf(false)
    /** Collection open inside the Collections tab, if any. */
    var openCollection by openCollectionState
    /** Settings pages pushed on the Collections tab: "settings", then "licenses". */
    var settingsPage by settingsPageState
    /** A setting found with Settings' search, to bring into view on its page. */
    var settingsHighlight by mutableStateOf<String?>(null)
    /** Which collection the viewer pages through, and where it started. */
    var viewerSource by viewerSourceState
    var viewerStart by viewerStartState
    /** A new viewer each time one opens (a parked one is replaced, not reused). */
    var viewerSession by mutableIntStateOf(0)
    /** A person opened from the viewer's details sheet: the viewer waits, parked, to come back to. */
    var viewerReturn by mutableStateOf<ViewerReturn?>(null)
    var coverOpening by mutableStateOf(false)
    var screenSize by mutableStateOf(IntSize.Zero)
    // A picker starts unfiltered: the asked-for types already narrow it, and a saved
    // "Videos" filter would leave a photo picker empty.
    var filter by mutableStateOf(if (pick != null) LibraryFilter.All else prefs.libraryFilter)
    var aspectRatioGrid by mutableStateOf(prefs.aspectRatioGrid)
    var sort by mutableStateOf(prefs.librarySort)
    var sortDescending by mutableStateOf(prefs.sortDescending)
    // A person's Show filter is for this visit: opening another person starts at All again.
    var personFilter by mutableStateOf(LibraryFilter.All)
    var personSort by mutableStateOf(prefs.personSort)
    var personSortDescending by mutableStateOf(prefs.personSortDescending)
    var gridColumns by mutableIntStateOf(prefs.gridColumns)
    var searchColumns by mutableIntStateOf(prefs.searchColumns)
    var albumColumns by mutableIntStateOf(prefs.albumColumns)
    var menuOpen by mutableStateOf(false)
    var zoomLevel by zoomLevelState
    // The Library opens with the tabs showing; scrolling up (back in time) folds them into the
    // Years / Months / All control, scrolling down brings them back. Years and Months keep the
    // control, since that's what they're for.
    var zoomBar by zoomBarState
    var albumSettings by mutableStateOf(prefs.albumSettings)
    /** Merge with…: the People page picks who [mergeFrom] joins. */
    var mergeFrom by mergeFromState
    var showHiddenPeople by showHiddenPeopleState
    var personNameRequest by mutableStateOf<com.subhashh.lumen.ai.Person?>(null)
    /** The name request is for a face just made into a person ([com.subhashh.lumen.ai.FaceStore.startPerson]). */
    var faceStarted by mutableStateOf<Long?>(null)
    /** A name that another person already has: merge them, or keep both with it. */
    var mergeProposal by mutableStateOf<MergeProposal?>(null)
    var personMenuOpen by mutableStateOf(false)
    var personViewMenuOpen by mutableStateOf(false)
    var peopleMenuOpen by mutableStateOf(false)
    var peopleSquare by mutableStateOf(prefs.peopleSquare)
    /** The open person came from the People page, so Back returns there. */
    var personFromPeople by personFromPeopleState
    var collectionsMenuOpen by mutableStateOf(false)
    /** Photos waiting for Resize or Convert's choices. */
    var resizeRequest by mutableStateOf<List<MediaItem>?>(null)
    /** The photo being framed in Set as Wallpaper. */
    var wallpaperItem by mutableStateOf<MediaItem?>(null)
    /** Items waiting for a new date from Adjust Date & Time. */
    var dateRequest by mutableStateOf<List<MediaItem>?>(null)
    /** Items waiting for Remind Me of This… (Developer Options → Photo Reminders). */
    var remindRequest by mutableStateOf<List<MediaItem>?>(null)
    /** Album name prompt: renaming [album], or making a new one when null. */
    var albumNameRequest by mutableStateOf<AlbumNameRequest?>(null)
    /** Sections opened with See All; closed again for each new search. */
    var searchExpanded by mutableStateOf(emptySet<String>())
    /** What "Add Name" from Search fills in: the search, as a name. */
    var personNameSuggestion by mutableStateOf<String?>(null)
    /** The section the viewer was opened from: it pages through that section. */
    var searchSection by searchSectionState
    var recentSearches by mutableStateOf(prefs.recentSearches)
    // Date headers (Settings → Library), only when the grid is in date order.
    var sectionMode by mutableStateOf(prefs.sectionMode)
    var curvedEdge by mutableStateOf(prefs.curvedEdge)
    // Multi-select: null when not selecting. Lives here so the header, top controls and the
    // bottom action bar (all outside the grid) can share it.
    var selection by mutableStateOf<Set<Long>?>(null)
    /** Album multi-select on Collections and Hidden's folder list: selected album keys. */
    var albumSelection by mutableStateOf<Set<String>?>(null)
    /** People page multi-select (to merge or hide several): selected person keys. */
    var peopleSelection by mutableStateOf<Set<String>?>(null)
    var selectionMenuOpen by mutableStateOf(false)
    /** Set when the viewer should open straight into a slideshow of these ids. */
    var slideshowOf by mutableStateOf<Set<Long>?>(null)
    var encryptHidden by mutableStateOf(LumenPrefs(appContext).encryptHidden)
    /** Turning Encrypt Hidden on (true) or off (false), waiting for the lock to be confirmed. */
    var pendingEncryption by mutableStateOf<Boolean?>(null)
    /** Moving files into or out of the vault: what's happening and how far along, or null. */
    var vaultProgress by mutableStateOf<VaultProgress?>(null)
    /** The lock as last typed, kept only until Hidden opens: the vault's recovery path needs it. */
    var enteredSecret by mutableStateOf<String?>(null)
    // Changing files Lumen didn't create: with All files access go straight ahead; otherwise ask
    // MediaStore for write access first (one dialog per batch, none with Media management on).
    var pendingWrite by mutableStateOf<(() -> Unit)?>(null)
    /** Items waiting for an album to be picked, and whether to move or copy them. */
    var albumRequest by mutableStateOf<AlbumRequest?>(null)
    /** The item being renamed and its current file name (null while loading / closed). */
    var renameItem by mutableStateOf<MediaItem?>(null)
    var renameName by mutableStateOf<String?>(null)
}

/** [MainState] for this screen; what must survive the activity being rebuilt is saved. */
@Composable
internal fun rememberMainState(viewModel: LibraryViewModel, pick: PickRequest?): MainState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val tab = rememberSaveable { mutableStateOf(LumenTab.Library) }
    val searching = rememberSaveable { mutableStateOf(false) }
    val searchQuery = rememberSaveable { mutableStateOf("") }
    val openCollection = rememberSaveable { mutableStateOf<String?>(null) }
    val settingsPage = rememberSaveable { mutableStateOf<String?>(null) }
    val viewerSource = rememberSaveable { mutableStateOf<String?>(null) }
    val viewerStart = rememberSaveable { mutableIntStateOf(0) }
    val zoomLevel = rememberSaveable { mutableStateOf(ZoomLevel.All) }
    val zoomBar = rememberSaveable { mutableStateOf(false) }
    val mergeFrom = rememberSaveable { mutableStateOf<Long?>(null) }
    val showHiddenPeople = rememberSaveable { mutableStateOf(false) }
    val personFromPeople = rememberSaveable { mutableStateOf(false) }
    val searchSection = rememberSaveable { mutableStateOf<String?>(null) }
    return remember {
        MainState(
            context, appContext, LumenPrefs(context), HiddenLockStore(appContext), viewModel, scope, pick,
            tab, searching, searchQuery, openCollection, settingsPage, viewerSource, viewerStart, zoomLevel, zoomBar, mergeFrom, showHiddenPeople, personFromPeople, searchSection,
        )
    }
}
