package com.subhashh.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subhashh.lumen.R
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.library.GridSections
import com.subhashh.lumen.ui.search.SearchSection
import com.subhashh.lumen.ui.search.searchGridIndex
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.parkedViewer
import com.subhashh.lumen.ui.transition.viewerFaceKey
import com.subhashh.lumen.ui.viewer.ViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The viewer over [MainScaffold]'s pages, for whichever list it was opened from: what its buttons
 * and menus do, and the flight back into the grid when it closes.
 */
@NonRestartableComposable
@Composable
internal fun BoxScope.MainViewer(
    s: MainState,
    library: List<MediaItem>?,
    trash: List<MediaItem>,
    allPeople: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    searchSections: List<SearchSection>?,
    searchAlbums: List<MediaCollection>,
    personMatches: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    unnamedSubject: Pair<com.subhashh.lumen.ai.Person, MediaItem>?,
    smartPending: Boolean,
    librarySections: GridSections,
    collectionSections: GridSections?,
    libraryGrid: LazyGridState,
    searchGrid: LazyGridState,
    collectionGrid: LazyGridState,
    transition: PhotoTransition,
    coverTransition: CoverTransition,
    reduceMotion: Boolean,
    itemsOf: (String) -> List<MediaItem>,
    personOf: (String?) -> com.subhashh.lumen.ai.Person?,
    heroWithLabel: (String?) -> HeroCover?,
    heroRect: () -> androidx.compose.ui.geometry.Rect,
    toast: (String) -> Unit,
    updateAlbums: ((AlbumSettings) -> AlbumSettings) -> Unit,
    rotate: (List<MediaItem>, Int) -> Unit,
    excludeFolders: (List<MediaItem>) -> Unit,
    hide: (List<MediaItem>) -> Unit,
    hideFolders: (List<MediaItem>) -> Unit,
    unhide: (List<MediaItem>) -> Unit,
    shareHidden: (List<MediaItem>) -> Unit,
    deleteHidden: (List<MediaItem>) -> Unit,
    confirmDelete: (List<MediaItem>, Boolean, () -> Unit) -> Unit,
) {
    with(s) {
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
                onNameFace = { item, face ->
                    scope.launch {
                        val person = withContext(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).startPerson(face, item.id) }
                        faceStarted = person.id
                        personNameRequest = person
                    }
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
                onEdit = { editItem = it },
                onResize = { resizeRequest = listOf(it) },
                onRemind = if (PhotoReminders.enabled(context)) { item -> remindRequest = listOf(item) } else null,
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
                        scope.launch(Dispatchers.IO) {
                            com.subhashh.lumen.ai.FaceStore.of(context).notThisPerson(person.id, listOf(item.id))
                            runCatching { com.subhashh.lumen.ai.FaceGrouping.run(com.subhashh.lumen.ai.FaceStore.of(context), com.subhashh.lumen.ai.FaceModel.recognizer(context)) }
                        }
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
    }
}
