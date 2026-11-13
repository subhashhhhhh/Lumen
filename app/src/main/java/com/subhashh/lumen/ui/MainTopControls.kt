package com.subhashh.lumen.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.imageDecoderEnabled
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.HfTokenStore
import com.subhashh.lumen.ai.SmartHit
import com.subhashh.lumen.ai.SmartSearch
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.AlbumSort
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.FavoritesImport
import com.subhashh.lumen.data.FileResult
import com.subhashh.lumen.data.GridThumbnailFetcher
import com.subhashh.lumen.data.HiddenLockStore
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.data.LibraryFilter
import com.subhashh.lumen.data.LibrarySort
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.MediaActions
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.data.MediaDates
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaThumbnailFetcher
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.data.SearchIndex
import com.subhashh.lumen.data.SettingsBackup
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.data.vault.VaultMigration
import com.subhashh.lumen.ui.albums.AlbumPicker
import com.subhashh.lumen.ui.albums.AlbumTarget
import com.subhashh.lumen.ui.collections.CollectionsScreen
import com.subhashh.lumen.ui.collections.coverUnderlay
import com.subhashh.lumen.ui.collections.heroSubtitle
import com.subhashh.lumen.ui.components.ConfirmDialog
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.DateTimeDialog
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.components.GlassMorphButton
import com.subhashh.lumen.ui.components.GlassTabBar
import com.subhashh.lumen.ui.components.GlassTextButton
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.components.MenuItem
import com.subhashh.lumen.ui.components.RenameDialog
import com.subhashh.lumen.ui.hidden.HiddenFolder
import com.subhashh.lumen.ui.hidden.HiddenLock
import com.subhashh.lumen.ui.hidden.HiddenLockScreen
import com.subhashh.lumen.ui.hidden.HiddenScreen
import com.subhashh.lumen.ui.hidden.LockFlow
import com.subhashh.lumen.ui.hidden.VaultProgressScreen
import com.subhashh.lumen.ui.hidden.hiddenFolders
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.GridSections
import com.subhashh.lumen.ui.library.LibraryScreen
import com.subhashh.lumen.ui.library.LibraryViewModel
import com.subhashh.lumen.ui.library.SectionMode
import com.subhashh.lumen.ui.library.TimelineScreen
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.library.periods
import com.subhashh.lumen.ui.onboarding.openSpecial
import com.subhashh.lumen.ui.people.MergePeopleDialog
import com.subhashh.lumen.ui.people.MergeProposal
import com.subhashh.lumen.ui.people.PeopleScreen
import com.subhashh.lumen.ui.search.SearchField
import com.subhashh.lumen.ui.search.SearchScreen
import com.subhashh.lumen.ui.search.SearchSection
import com.subhashh.lumen.ui.search.searchGridIndex
import com.subhashh.lumen.ui.selection.SelectionActionBar
import com.subhashh.lumen.ui.selection.SelectionTopControls
import com.subhashh.lumen.ui.settings.ExcludedFoldersScreen
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.settings.ResizeDialog
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.SettingsScreen
import com.subhashh.lumen.ui.settings.UpdateSession
import com.subhashh.lumen.ui.theme.LocalGlassColors
import com.subhashh.lumen.ui.transition.CoverHeader
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.CoverTransitionOverlay
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.PhotoTransitionOverlay
import com.subhashh.lumen.ui.transition.coverPage
import com.subhashh.lumen.ui.transition.parkedViewer
import com.subhashh.lumen.ui.transition.viewerFaceKey
import com.subhashh.lumen.ui.viewer.ViewerScreen
import com.subhashh.lumen.ui.viewer.WallpaperScreen
import com.subhashh.lumen.ui.viewer.fullScreenRect
import com.subhashh.lumen.ui.viewer.rotateMenu
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The glass controls across the top of [MainScaffold], whichever page is showing: Select All and
 * Cancel, the search field, Empty Trash, the picker's buttons, the view and settings buttons with
 * their menus (Library, Collections, People, a person), and Back.
 */
@NonRestartableComposable
@Composable
internal fun BoxScope.MainTopControls(
    s: MainState,
    pick: PickRequest?,
    onPicked: (List<MediaItem>) -> Unit,
    onCancelPick: () -> Unit,
    backdrop: LayerBackdrop,
    navBottom: Dp,
    trash: List<MediaItem>,
    allPeople: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    people: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    albumKeys: List<String>,
    selectableItems: List<MediaItem>,
    selectedItems: List<MediaItem>,
    selecting: Boolean,
    viewerOpen: Boolean,
    inCollection: Boolean,
    inSettings: Boolean,
    updateWaiting: Boolean,
    coverTransition: CoverTransition,
    selectionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<IntentSenderRequest, androidx.activity.result.ActivityResult>,
    personOf: (String?) -> com.subhashh.lumen.ai.Person?,
    rememberSearch: (String) -> Unit,
    endSelection: () -> Unit,
    updateAlbums: ((AlbumSettings) -> AlbumSettings) -> Unit,
    popSettings: () -> Unit,
    closeCollection: (String?) -> Unit,
) {
    with(s) {
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
                // Tapping the current sort reverses it; another one starts in its usual order. The
                // menu stays open, so the order can be flipped right away.
                AlbumSort.entries.map { sort ->
                    val current = sort == albumSettings.sort
                    MenuItem(
                        context.getString(sort.label),
                        direction = if (current) !albumSettings.reversed else null,
                        keepOpen = true,
                    ) {
                        updateAlbums { it.copy(sort = sort, reversed = current && !it.reversed) }
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
            // Show and Sort, as in the Library (blue while a filter is on), then ⋯ for the person.
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
                    onClick = {
                        personMenuOpen = false
                        personViewMenuOpen = !personViewMenuOpen
                    },
                    tint = if (personFilter != LibraryFilter.All) MaterialTheme.colorScheme.primary else LocalGlassColors.current.content,
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = LumenIcons.More,
                    contentDescription = stringResource(R.string.main_person_options),
                    onClick = {
                        personViewMenuOpen = false
                        personMenuOpen = !personMenuOpen
                    },
                )
            }
        }
        BackHandler(enabled = personViewMenuOpen) { personViewMenuOpen = false }
        GlassMenu(
            backdrop = backdrop,
            open = personViewMenuOpen && openPerson != null,
            onDismiss = { personViewMenuOpen = false },
            sections = listOf(
                com.subhashh.lumen.ui.library.viewMenuItems(
                    filter = personFilter,
                    onFilter = { personFilter = it },
                    sort = personSort,
                    descending = personSortDescending,
                    onSort = { newSort, descending ->
                        personSort = newSort
                        personSortDescending = descending
                        prefs.personSort = newSort
                        prefs.personSortDescending = descending
                    },
                ),
            ),
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp + 48.dp + 10.dp),
        )
        BackHandler(enabled = personMenuOpen) { personMenuOpen = false }
        // Its own lambda (a separate JVM method), like the selection menu below.
        val personMenu: @Composable BoxScope.() -> Unit = {
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
        }
        personMenu()

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
    }
}
