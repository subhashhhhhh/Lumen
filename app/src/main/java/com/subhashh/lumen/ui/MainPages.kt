package com.subhashh.lumen.ui

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subhashh.lumen.R
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.LibrarySort
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.ui.collections.coverUnderlay
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.hidden.HiddenFolder
import com.subhashh.lumen.ui.hidden.HiddenLock
import com.subhashh.lumen.ui.hidden.HiddenScreen
import com.subhashh.lumen.ui.hidden.LockFlow
import com.subhashh.lumen.ui.library.GridSections
import com.subhashh.lumen.ui.library.LibraryScreen
import com.subhashh.lumen.ui.library.TimelineScreen
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.library.periods
import com.subhashh.lumen.ui.search.SearchScreen
import com.subhashh.lumen.ui.search.SearchSection
import com.subhashh.lumen.ui.settings.ExcludedFoldersScreen
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.SettingsScreen
import com.subhashh.lumen.ui.transition.CoverHeader
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.coverPage
import kotlinx.coroutines.launch

/**
 * The page [MainScaffold] shows under its glass controls: Search, Settings and its sub-pages, the
 * Library with Years and Months, Hidden, or Collections, People and an open album or person.
 */
@NonRestartableComposable
@Composable
internal fun BoxScope.MainPages(
    s: MainState,
    pick: PickRequest?,
    library: List<MediaItem>?,
    allItems: List<MediaItem>?,
    excluded: Set<String>,
    hidden: List<MediaItem>?,
    hiddenAll: List<MediaItem>?,
    hiddenFolderList: List<HiddenFolder>?,
    utilities: List<MediaCollection>,
    folders: List<MediaCollection>,
    libraryItems: List<MediaItem>,
    librarySections: GridSections,
    openItems: List<MediaItem>?,
    collectionSections: GridSections?,
    months: List<com.subhashh.lumen.ui.library.Period>,
    years: List<com.subhashh.lumen.ui.library.Period>,
    selectedItems: List<MediaItem>,
    searchSections: List<SearchSection>?,
    searchAlbums: List<MediaCollection>,
    personMatches: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    unnamedSubject: Pair<com.subhashh.lumen.ai.Person, MediaItem>?,
    smartPending: Boolean,
    smartSuggestions: List<Pair<String, MediaItem>>,
    searchSuggestions: List<String>,
    chronological: Boolean,
    scrollLabel: (MediaItem) -> String,
    statusTop: Dp,
    navBottom: Dp,
    gridPadding: PaddingValues,
    heroHeight: Dp,
    libraryGrid: LazyGridState,
    collectionGrid: LazyGridState,
    hiddenGrid: LazyGridState,
    searchGrid: LazyGridState,
    monthsList: LazyListState,
    yearsList: LazyListState,
    zoomBarScroll: NestedScrollConnection,
    transition: PhotoTransition,
    coverTransition: CoverTransition,
    activity: Activity?,
    exportLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    favoritesLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    importLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    itemsOf: (String) -> List<MediaItem>,
    personOf: (String?) -> com.subhashh.lumen.ai.Person?,
    heroWithLabel: (String?) -> HeroCover?,
    setPicked: (Set<Long>?, List<MediaItem>) -> Unit,
    onGridColumns: (Int) -> Unit,
    onAlbumColumns: (Int) -> Unit,
    rememberSearch: (String) -> Unit,
    toast: (String) -> Unit,
    needsAllFiles: () -> Boolean,
    openHidden: () -> Unit,
    openViewer: (String, Int) -> Unit,
    collectionsPage: @Composable (Modifier) -> Unit,
    peoplePage: @Composable (Modifier) -> Unit,
) {
    with(s) {
        // Its own lambda, as it was inside MainScaffold: a page's own changes rebuild only the page.
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
                            contentPadding = PaddingValues(top = if (cover != null) heroHeight + 2.dp else statusTop + (if (com.subhashh.lumen.ui.theme.Lumen.material) 68.dp else 152.dp), bottom = navBottom + 96.dp),
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
    }
}
