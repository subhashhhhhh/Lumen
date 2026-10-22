package com.subhashh.lumen.ui

import com.subhashh.lumen.ui.icons.LumenIcons
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.subhashh.lumen.ui.viewer.ViewerScreen
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

    val utilities = remember(library, trash) { MediaCollections.utilities(library.orEmpty(), trash) }
    val folders = remember(library) { MediaCollections.folders(library.orEmpty()) }
    val itemsOf = { key: String -> CollectionKeys.items(key, library.orEmpty(), trash) }

    val viewerOpen = viewerSource != null
    val inCollection = tab == LumenTab.Collections && openCollection != null && !searching
    BackHandler(enabled = searching && !viewerOpen) { searching = false }
    BackHandler(enabled = inCollection && !viewerOpen) { openCollection = null }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridPadding = PaddingValues(top = statusTop + 96.dp, bottom = navBottom + 96.dp)

    Box(Modifier.fillMaxSize()) {
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
                    title = "Library",
                    items = library.orEmpty(),
                    gridState = libraryGrid,
                    contentPadding = gridPadding,
                    onOpen = { index -> viewerStart = index; viewerSource = CollectionKeys.Library },
                )
                openCollection != null -> {
                    val key = openCollection!!
                    LibraryScreen(
                        title = (utilities + folders).firstOrNull { it.key == key }?.title.orEmpty(),
                        items = itemsOf(key),
                        gridState = collectionGrid,
                        contentPadding = PaddingValues(top = statusTop + 152.dp, bottom = navBottom + 96.dp),
                        titleTopPadding = 64.dp,
                        onOpen = { index -> viewerStart = index; viewerSource = key },
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

        if (!viewerOpen) {
            GlassTabBar(
                backdrop = backdrop,
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
                    .padding(bottom = navBottom + 12.dp),
            )
        }

        AnimatedVisibility(
            visible = viewerOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            // Keep the source during the exit animation, after viewerSource is cleared.
            val source = remember { viewerSource ?: CollectionKeys.Library }
            val start = remember { viewerStart }
            val list = remember(source, library, trash) { itemsOf(source) }
            val grid = if (source == CollectionKeys.Library) libraryGrid else collectionGrid
            ViewerScreen(
                items = list,
                initialIndex = start.coerceAtMost(list.lastIndex.coerceAtLeast(0)),
                inTrash = source == CollectionKeys.RecentlyDeleted,
                onClose = { closedAt ->
                    viewerSource = null
                    // Bring the photo the user ended on into view, like iOS does.
                    val visible = grid.layoutInfo.visibleItemsInfo.map { it.index }
                    if (closedAt !in visible) scope.launch { grid.scrollToItem(closedAt) }
                },
                onMediaChanged = viewModel::refresh,
            )
        }
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
