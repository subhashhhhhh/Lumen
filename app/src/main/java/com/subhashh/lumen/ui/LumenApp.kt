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
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.ui.components.GlassIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.subhashh.lumen.ui.components.RenameDialog
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedContent
import com.subhashh.lumen.ui.settings.SettingsScreen
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.theme.LocalGlassColors
import com.subhashh.lumen.ui.library.ViewMenu
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.LibraryFilter
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
import com.subhashh.lumen.ui.components.GlassTabBar
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.library.LibraryScreen
import com.subhashh.lumen.ui.library.LibraryViewModel
import com.subhashh.lumen.ui.search.SearchScreen

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
fun LumenApp() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            // Platform ImageDecoder handles AVIF/HEIF, which BitmapFactory can't on every device.
            .imageDecoderEnabled(true)
            .components {
                add(MediaThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

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
        MainScaffold(viewModel)
    } else {
        PermissionScreen(onGrant = { launcher.launch(MediaPermissions) })
    }
}

@Composable
private fun MainScaffold(viewModel: LibraryViewModel) {
    val library by viewModel.items.collectAsState()
    val trash by viewModel.trash.collectAsState()
    var tab by rememberSaveable { mutableStateOf(LumenTab.Library) }
    var searching by rememberSaveable { mutableStateOf(false) }
    /** Collection open inside the Collections tab, if any. */
    var openCollection by rememberSaveable { mutableStateOf<String?>(null) }
    /** Settings pages pushed on the Collections tab: "settings", then "licenses". */
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    /** Which collection the viewer pages through, and where it started. */
    var viewerSource by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerStart by rememberSaveable { mutableIntStateOf(0) }

    val backdrop = rememberLayerBackdrop()
    // Hoisted so grids keep their position across tab switches and while the viewer is open.
    val libraryGrid = rememberLazyGridState()
    val collectionGrid = key(openCollection) { rememberLazyGridState() }
    val scope = rememberCoroutineScope()
    val transition = remember { PhotoTransition() }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val viewerBackground = MaterialTheme.colorScheme.background

    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    var filter by remember { mutableStateOf(prefs.libraryFilter) }
    var aspectRatioGrid by remember { mutableStateOf(prefs.aspectRatioGrid) }
    var gridColumns by remember { mutableIntStateOf(prefs.gridColumns) }
    val onGridColumns = { columns: Int ->
        gridColumns = columns
        prefs.gridColumns = columns
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

    val utilities = remember(library, trash) { MediaCollections.utilities(library.orEmpty(), trash) }
    val folders = remember(library) { MediaCollections.folders(library.orEmpty()) }
    // The Library tab is filtered by the ≡ menu; the viewer and selection page through the same list.
    val libraryItems = remember(library, filter) {
        library.orEmpty().let { all -> if (filter == LibraryFilter.All) all else all.filter(filter::matches) }
    }
    val itemsOf = { key: String ->
        if (key == CollectionKeys.Library) libraryItems else CollectionKeys.items(key, library.orEmpty(), trash)
    }
    val months = remember(libraryItems) { periods(libraryItems, byYear = false) }
    val years = remember(libraryItems) { periods(libraryItems, byYear = true) }
    // A new filter starts at its newest item, like opening the Library.
    LaunchedEffect(filter) {
        if (libraryItems.isNotEmpty()) libraryGrid.scrollToItem(libraryItems.lastIndex)
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
    val popSettings = { settingsPage = if (settingsPage == SettingsLicenses) SettingsMain else null }
    BackHandler(enabled = searching && !viewerOpen) { searching = false }
    BackHandler(enabled = inCollection && !viewerOpen) { openCollection = null }
    BackHandler(enabled = inSettings && !viewerOpen) { popSettings() }

    // Multi-select: null when not selecting. Lives here so the header, top controls and the
    // bottom action bar (all outside the grid) can share it.
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    val selectableKey = when {
        searching -> null
        tab == LumenTab.Library && zoomLevel == ZoomLevel.All -> CollectionKeys.Library
        tab == LumenTab.Library -> null
        else -> openCollection
    }
    val selectableItems = selectableKey?.let(itemsOf).orEmpty()
    // Leaving the screen ends selection; refreshes drop ids that no longer exist.
    LaunchedEffect(selectableKey) { selection = null }
    LaunchedEffect(selectableItems) {
        val ids = selectableItems.mapTo(HashSet()) { it.id }
        selection = selection?.filterTo(HashSet()) { it in ids }
    }
    BackHandler(enabled = selection != null && !viewerOpen) { selection = null }
    val selectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refresh()
            selection = null
        }
    }
    val selectedItems = selection?.let { ids -> selectableItems.filter { it.id in ids } }.orEmpty()
    BackHandler(enabled = menuOpen) { menuOpen = false }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = selectionMenuOpen) { selectionMenuOpen = false }
    /** Set when the viewer should open straight into a slideshow of these ids. */
    var slideshowOf by remember { mutableStateOf<Set<Long>?>(null) }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

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
        selection = null
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
        if (transition.item != null) return@open
        val item = itemsOf(key).getOrNull(index) ?: return@open
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
                searching -> SearchScreen()
                library == null -> Unit
                settingsPage == SettingsLicenses -> LicensesScreen(
                    contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                )
                settingsPage == SettingsMain -> SettingsScreen(
                    contentPadding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + 96.dp),
                    onOpenLicenses = { settingsPage = SettingsLicenses },
                )
                tab == LumenTab.Library -> Box(Modifier.nestedScroll(zoomBarScroll)) {
                    LibraryScreen(
                        title = selection?.let { selectionTitle(selectedItems) } ?: "Library",
                        selection = selection,
                        onSelectionChange = { selection = it },
                        aspectRatioGrid = aspectRatioGrid,
                        columns = gridColumns,
                        onColumnsChange = onGridColumns,
                        emptyText = filter.emptyText,
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
                                        scope.launch { libraryGrid.scrollToItem(period.firstIndex) }
                                    }
                                },
                            )
                        } else {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
                openCollection != null -> {
                    val key = openCollection!!
                    LibraryScreen(
                        title = selection?.let { selectionTitle(selectedItems) }
                            ?: (utilities + folders).firstOrNull { it.key == key }?.title.orEmpty(),
                        selection = selection,
                        onSelectionChange = { selection = it },
                        aspectRatioGrid = aspectRatioGrid,
                        columns = gridColumns,
                        onColumnsChange = onGridColumns,
                        items = itemsOf(key),
                        gridState = collectionGrid,
                        contentPadding = PaddingValues(top = statusTop + 152.dp, bottom = navBottom + 96.dp),
                        titleTopPadding = 64.dp,
                        transition = transition,
                        onOpen = { index -> openViewer(key, index) },
                    )
                }
                else -> CollectionsScreen(
                    utilities = utilities,
                    folders = folders,
                    contentPadding = PaddingValues(top = statusTop + 16.dp, bottom = navBottom + 96.dp),
                    onOpen = { openCollection = it },
                )
            }
        }

        if (selection != null && !viewerOpen) {
            SelectionTopControls(
                backdrop = backdrop,
                allSelected = selectedItems.size == selectableItems.size && selectableItems.isNotEmpty(),
                onToggleAll = {
                    selection = if (selectedItems.size == selectableItems.size) emptySet()
                    else selectableItems.mapTo(HashSet()) { it.id }
                },
                onCancel = { selection = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (tab == LumenTab.Library && !searching && selection == null && !viewerOpen && settingsPage == null) {
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

        if (tab == LumenTab.Collections && openCollection == null && settingsPage == null && !searching && !viewerOpen) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.Settings,
                contentDescription = "Settings",
                onClick = { settingsPage = SettingsMain },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if ((inCollection || inSettings) && !viewerOpen) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.Back,
                contentDescription = "Back",
                onClick = { if (inSettings) popSettings() else openCollection = null },
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
        val tabBarHidden = viewerOpen || transition.opening || selection != null
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
                onSearch = { searching = true },
                zoomLevel = zoomLevel.takeIf { tab == LumenTab.Library && !searching },
                onZoomLevel = { zoomLevel = it },
                showZoom = zoomBar || zoomLevel != ZoomLevel.All,
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
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        )

        AnimatedVisibility(
            visible = selection != null && !viewerOpen,
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
                inTrash = selectableKey == CollectionKeys.RecentlyDeleted,
                allFavorite = allFavorite,
                onShare = { MediaActions.share(context, selectedItems) },
                onFavorite = { launch(MediaActions.favoriteRequest(context, selectedItems, favorite = !allFavorite)) },
                onDelete = { launch(MediaActions.trashRequest(context, selectedItems)) },
                onRestore = { launch(MediaActions.restoreRequest(context, selectedItems)) },
                onDeleteForever = { launch(MediaActions.deleteForeverRequest(context, selectedItems)) },
                onMore = { selectionMenuOpen = !selectionMenuOpen },
            )
        }

        GlassMenu(
            backdrop = backdrop,
            open = selectionMenuOpen && selection != null && !viewerOpen,
            onDismiss = { selectionMenuOpen = false },
            sections = listOf(
                listOf(
                    MenuItem("Move to Album…") { albumRequest = AlbumRequest(move = true, items = selectedItems) },
                    MenuItem("Copy to Album…") { albumRequest = AlbumRequest(move = false, items = selectedItems) },
                ),
                listOfNotNull(
                    if (selectedItems.any(MediaFiles::canRotate)) rotateMenu { rotate(selectedItems, it) } else null,
                ),
                listOf(
                    MenuItem("Slideshow") {
                        val key = selectableKey ?: return@MenuItem
                        val ids = selectedItems.mapTo(HashSet()) { it.id }
                        val first = selectableItems.indexOfFirst { it.id in ids }
                        if (first < 0) return@MenuItem
                        slideshowOf = ids
                        selection = null
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
            val list = remember(source, library, trash) { itemsOf(source) }
            val grid = if (source == CollectionKeys.Library) libraryGrid else collectionGrid
            ViewerScreen(
                items = list,
                initialIndex = viewerStart.coerceAtMost(list.lastIndex.coerceAtLeast(0)),
                inTrash = source == CollectionKeys.RecentlyDeleted,
                onClose = { exit ->
                    viewerSource = null
                    slideshowOf = null
                    val item = list.getOrNull(exit.index)
                    val from = exit.photoRect
                    if (item != null && from != null) {
                        // Same frame the viewer goes: the photo keeps flying from where it was.
                        transition.beginClose(item, from, exit.background, exit.backgroundAlpha)
                        scope.launch {
                            // Bring the photo's cell into view first, like iOS, then land in it.
                            if (grid.layoutInfo.visibleItemsInfo.none { it.index == exit.index }) {
                                grid.scrollToItem(exit.index)
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

        PhotoTransitionOverlay(transition)
    }
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

private const val SettingsMain = "settings"
private const val SettingsLicenses = "licenses"

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

/** How far to scroll one way before the bottom bar switches between tabs and Years / Months / All. */
private val ZoomBarSwitchDistance = 40.dp
