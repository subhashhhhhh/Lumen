package com.subhashh.lumen.ui

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.FileResult
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaDates
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.vault.VaultMigration
import com.subhashh.lumen.ui.components.DateTimeDialog
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.people.MergeProposal
import com.subhashh.lumen.ui.settings.ResizeDialog
import com.subhashh.lumen.ui.settings.SettingsCategory
import kotlinx.coroutines.launch

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
internal const val HeroShareWide = 0.32f

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
        // Some went and the rest need All files access: say how many went, or repeating the move
        // makes duplicates of those.
        result.needsAllFilesAccess && result.done > 0 -> res.getQuantityString(
            if (move) R.plurals.main_file_moved_some_need_access else R.plurals.main_file_copied_some_need_access,
            result.done,
            result.done,
            album,
            result.failed,
        )
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


