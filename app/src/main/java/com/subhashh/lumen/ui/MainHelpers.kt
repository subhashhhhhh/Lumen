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

/** Another person already called [name] (any case), with both faces' photos; null when the name's free. */
internal fun mergeProposalFor(
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
internal data class ViewerReturn(
    val person: String,
    val tab: LumenTab,
    val openCollection: String?,
    val searching: Boolean,
    val personFromPeople: Boolean,
)

/** Share of the screen's height an album's or person's cover takes at the top of its page. */
internal const val HeroShare = 0.46f

/** Naming an album: renaming [album], or a brand-new one when it's null. */
internal data class AlbumNameRequest(val album: MediaCollection?)

/** "Not Chiara", or "Not This Person" before they're named. */
@Composable
internal fun notPersonLabel(person: com.subhashh.lumen.ai.Person) =
    person.name?.let { stringResource(R.string.main_not_person, it) } ?: stringResource(R.string.main_not_this_person)

/** The picker's title: what's asked for, or how many are chosen; null when not picking. */
@Composable
internal fun pickTitle(pick: PickRequest?, pickedCount: Int): String? = pick?.let {
    if (it.multiple && pickedCount > 0) pluralStringResource(R.plurals.main_n_selected, pickedCount, pickedCount)
    else stringResource(it.title)
}

/** "2 Albums Selected", "1 Folder Selected", or "Select Albums" when empty. */
@Composable
internal fun albumSelectionTitle(n: Int, folders: Boolean): String = when {
    n == 0 -> stringResource(if (folders) R.string.main_select_folders else R.string.main_select_albums)
    else -> pluralStringResource(if (folders) R.plurals.main_folders_selected else R.plurals.main_albums_selected, n, n)
}

/** "3 Photos Selected", "1 Video Selected", "5 Items Selected", or "Select Items" when empty. */
@Composable
internal fun selectionTitle(selected: List<MediaItem>): String {
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
internal const val AlbumsScope = "albums"

internal const val SettingsMain = "settings"
internal const val SettingsCategoryPrefix = "settings/"
internal fun settingsCategoryPage(category: SettingsCategory) = SettingsCategoryPrefix + category.key
internal const val SettingsLicenses = "licenses"
internal const val SettingsExcluded = "excluded"

internal data class AlbumRequest(val move: Boolean, val items: List<MediaItem>)

/** One line after a copy or move: what happened, and why anything didn't. */
internal fun fileMessage(context: Context, move: Boolean, result: FileResult, album: String): String {
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

internal val ScrollMonth = java.text.SimpleDateFormat(
    android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMMyyyy"),
    java.util.Locale.getDefault(),
)

/** How far to scroll one way before the bottom bar switches between tabs and Years / Months / All. */
internal val ZoomBarSwitchDistance = 40.dp

/** Moving files into or out of the vault, and how far along (null while it gets ready). */
internal data class VaultProgress(val encrypting: Boolean, val progress: VaultMigration.Progress?)

/** Resize or Convert for [items] (null: closed): saves copies, then [finish] with what happened. */
@Composable
internal fun ResizeSheet(items: List<MediaItem>?, onClose: () -> Unit, toast: (String) -> Unit, finish: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ResizeDialog(
        count = items?.size,
        onSave = { target, format, quality ->
            val chosen = items.orEmpty()
            onClose()
            scope.launch {
                if (chosen.size > 1) toast(context.resources.getQuantityString(R.plurals.main_saving_copies, chosen.size, chosen.size))
                val result = ImageExport.export(context, chosen, target, format, quality)
                val where = result.folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
                finish(
                    when {
                        result.saved == 0 -> context.getString(R.string.main_save_copy_failed)
                        result.failed > 0 -> context.getString(R.string.main_saved_n_of, result.saved, chosen.size)
                        where != null -> if (result.saved == 1) context.getString(R.string.main_saved_copy_in, where)
                        else context.resources.getQuantityString(R.plurals.main_saved_copies_in, result.saved, result.saved, where)
                        else -> context.resources.getQuantityString(R.plurals.main_saved_copies, result.saved, result.saved)
                    },
                )
            }
        },
        onDismiss = onClose,
    )
}

/** Adjust Date & Time for [items] (null: closed): every item moves by the same amount. */
@Composable
internal fun AdjustDateSheet(
    items: List<MediaItem>?,
    onClose: () -> Unit,
    withWriteAccess: (List<MediaItem>, () -> Unit) -> Unit,
    finish: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DateTimeDialog(
        original = items?.minOf { it.takenAt },
        count = items?.size ?: 0,
        onAdjust = { newTime ->
            val chosen = items.orEmpty()
            onClose()
            if (chosen.isNotEmpty()) {
                // Everything moves by the same amount, so the earliest lands where it was set.
                val delta = newTime - chosen.minOf { it.takenAt }
                val run = {
                    scope.launch {
                        val result = MediaDates.shift(context, chosen, delta)
                        finish(
                            when {
                                result.failed == 0 -> context.getString(if (chosen.size == 1) R.string.main_date_changed else R.string.main_dates_changed)
                                result.needsAllFilesAccess ->
                                    context.getString(R.string.main_dates_changed_needs_access, result.done, chosen.size)
                                else -> context.getString(R.string.main_dates_changed_heic, result.done, chosen.size)
                            },
                        )
                    }
                }
                if (Environment.isExternalStorageManager()) run() else withWriteAccess(chosen) { run() }
            }
        },
        onDismiss = onClose,
    )
}


