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

    val utilities = remember(library, trash) { MediaCollections.utilities(library.orEmpty(), trash) }
    val folders = remember(library) { MediaCollections.folders(library.orEmpty()) }
    val itemsOf = { key: String -> CollectionKeys.items(key, library.orEmpty(), trash) }

    val viewerOpen = viewerSource != null
    val inCollection = tab == LumenTab.Collections && openCollection != null && !searching
    BackHandler(enabled = searching && !viewerOpen) { searching = false }
    BackHandler(enabled = inCollection && !viewerOpen) { openCollection = null }

    // Multi-select: null when not selecting. Lives here so the header, top controls and the
    // bottom action bar (all outside the grid) can share it.
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    val selectableKey = when {
        searching -> null
        tab == LumenTab.Library -> CollectionKeys.Library
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
    val context = LocalContext.current

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
                tab == LumenTab.Library -> LibraryScreen(
                    title = selection?.let { selectionTitle(selectedItems) } ?: "Library",
                    selection = selection,
                    onSelectionChange = { selection = it },
                    items = library.orEmpty(),
                    gridState = libraryGrid,
                    contentPadding = gridPadding,
                    transition = transition,
                    onOpen = { index -> openViewer(CollectionKeys.Library, index) },
                )
                openCollection != null -> {
                    val key = openCollection!!
                    LibraryScreen(
                        title = selection?.let { selectionTitle(selectedItems) }
                            ?: (utilities + folders).firstOrNull { it.key == key }?.title.orEmpty(),
                        selection = selection,
                        onSelectionChange = { selection = it },
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

        if (inCollection && !viewerOpen) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.Back,
                contentDescription = "Back",
                onClick = { openCollection = null },
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
                    tab = selected
                    searching = false
                },
                searchSelected = searching,
                onSearch = { searching = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = tabBarAlpha }
                    .padding(bottom = navBottom + 12.dp),
            )
        }

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
            )
        }

        viewerSource?.let { source ->
            val list = remember(source, library, trash) { itemsOf(source) }
            val grid = if (source == CollectionKeys.Library) libraryGrid else collectionGrid
            ViewerScreen(
                items = list,
                initialIndex = viewerStart.coerceAtMost(list.lastIndex.coerceAtLeast(0)),
                inTrash = source == CollectionKeys.RecentlyDeleted,
                onClose = { exit ->
                    viewerSource = null
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
            )
        }

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
