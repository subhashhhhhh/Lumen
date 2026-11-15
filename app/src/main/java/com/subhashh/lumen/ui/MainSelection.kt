package com.subhashh.lumen.ui

import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.subhashh.lumen.R
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaActions
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.components.GlassTextButton
import com.subhashh.lumen.ui.components.MenuItem
import com.subhashh.lumen.ui.selection.SelectionActionBar
import com.subhashh.lumen.ui.viewer.rotateMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What sits at the bottom of [MainScaffold] while selecting: Merge and Hide for people, the action
 * bar for photos, videos and albums, and its ⋯ menu.
 */
@NonRestartableComposable
@Composable
internal fun BoxScope.MainSelectionBars(
    s: MainState,
    backdrop: LayerBackdrop,
    navBottom: Dp,
    viewerOpen: Boolean,
    selecting: Boolean,
    selectionScope: String?,
    selectableKey: String?,
    selectableItems: List<MediaItem>,
    selectedItems: List<MediaItem>,
    folders: List<MediaCollection>,
    hidden: List<MediaItem>?,
    selectionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<IntentSenderRequest, androidx.activity.result.ActivityResult>,
    personOf: (String?) -> com.subhashh.lumen.ai.Person?,
    toast: (String) -> Unit,
    endSelection: () -> Unit,
    updateAlbums: ((AlbumSettings) -> AlbumSettings) -> Unit,
    openViewer: (String, Int) -> Unit,
    rotate: (List<MediaItem>, Int) -> Unit,
    excludeFolders: (List<MediaItem>) -> Unit,
    excludeFromHidden: (List<MediaItem>) -> Unit,
    hide: (List<MediaItem>) -> Unit,
    hideFolders: (List<MediaItem>) -> Unit,
    unhide: (List<MediaItem>) -> Unit,
    shareHidden: (List<MediaItem>) -> Unit,
    deleteHidden: (List<MediaItem>) -> Unit,
    confirmDelete: (List<MediaItem>, Boolean, () -> Unit) -> Unit,
) {
    with(s) {
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
            // Material's is docked to the bottom edge and pads itself above the gesture bar.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (com.subhashh.lumen.ui.theme.Lumen.material) 0.dp else navBottom + 12.dp),
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
                            scope.launch(Dispatchers.IO) {
                                com.subhashh.lumen.ai.FaceStore.of(context).notThisPerson(person.id, ids)
                                // The faces let go are grouped again now (with others like them, as
                                // someone new), not at the next background pass or restart.
                                runCatching { com.subhashh.lumen.ai.FaceGrouping.run(com.subhashh.lumen.ai.FaceStore.of(context), com.subhashh.lumen.ai.FaceModel.recognizer(context)) }
                            }
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
                listOfNotNull(
                    MenuItem(stringResource(R.string.main_slideshow)) {
                        val key = selectableKey ?: return@MenuItem
                        val ids = selectedItems.mapTo(HashSet()) { it.id }
                        val first = selectableItems.indexOfFirst { it.id in ids }
                        if (first < 0) return@MenuItem
                        slideshowOf = ids
                        endSelection()
                        openViewer(key, first)
                    },
                    if (PhotoReminders.enabled(context)) MenuItem(stringResource(R.string.reminder_menu)) { remindRequest = selectedItems } else null,
                ),
            ),
            origin = TransformOrigin(1f, 1f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = navBottom + 12.dp + 48.dp + 12.dp, end = 16.dp),
        )
    }
}
