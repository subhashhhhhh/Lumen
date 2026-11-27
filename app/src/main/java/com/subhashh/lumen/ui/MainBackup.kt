package com.subhashh.lumen.ui

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.subhashh.lumen.R
import com.subhashh.lumen.data.FavoritesImport
import com.subhashh.lumen.data.MediaActions
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.SettingsBackup
import kotlinx.coroutines.launch

/** What [MainState.backupLaunchers] hands back to [MainScaffold]. */
internal class BackupLaunchers(
    val favoritesLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    val exportLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    val importLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
)

/**
 * Settings → Backup & Import, as [MainScaffold] runs it: the file pickers for importing favorites
 * (Fossify's or Aves's list), saving Lumen's settings, and restoring them.
 */
@Composable
internal fun MainState.backupLaunchers(
    library: List<MediaItem>?,
    activity: Activity?,
    toast: (String) -> Unit,
    selectionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<IntentSenderRequest, androidx.activity.result.ActivityResult>,
): BackupLaunchers {
    // Settings → Backup → Import Favorites: Fossify's .txt or Aves's .json, matched to files here.
    val favoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val found = FavoritesImport.read(context, uri, library.orEmpty())
            if (found == null) {
                toast(context.getString(R.string.main_favorites_import_bad_file))
                return@launch
            }
            val toMark = found.matched.filterNot { it.isFavorite }
            when {
                found.matched.isEmpty() -> toast(context.resources.getQuantityString(R.plurals.main_favorites_import_none, found.listed, found.listed))
                toMark.isEmpty() -> toast(context.resources.getQuantityString(R.plurals.main_favorites_import_already, found.matched.size, found.matched.size))
                else -> {
                    toast(context.getString(R.string.main_favorites_import_found, found.matched.size, found.listed, toMark.size))
                    selectionLauncher.launch(
                        IntentSenderRequest.Builder(MediaActions.favoriteRequest(context, toMark, favorite = true)).build(),
                    )
                }
            }
        }
    }
    // Settings → Backup. Importing replaces every setting, so the screen restarts afterwards to
    // pick them all up; any favorites to restore are asked for first.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = SettingsBackup.export(context, library.orEmpty().filter { it.isFavorite }, uri)
            toast(context.getString(if (ok) R.string.main_settings_exported else R.string.main_backup_save_failed))
        }
    }
    val restoreFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        activity?.recreate()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = SettingsBackup.import(context, uri)
            if (imported == null) {
                toast(context.getString(R.string.main_backup_bad_file))
                return@launch
            }
            toast(context.getString(R.string.main_settings_restored))
            val toFavorite = library.orEmpty().filter { !it.isFavorite && (it.relativePath + it.name) in imported.favoritePaths }
            if (toFavorite.isEmpty()) {
                activity?.recreate()
            } else {
                restoreFavoritesLauncher.launch(
                    IntentSenderRequest.Builder(MediaActions.favoriteRequest(context, toFavorite, favorite = true)).build(),
                )
            }
        }
    }
    // After a Lumen Backup restore: its favorites, marked once the person agrees (Android asks).
    val restoredFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    androidx.compose.runtime.LaunchedEffect(library != null) {
        val pending = prefs.pendingFavorites
        if (library == null || pending.isEmpty()) return@LaunchedEffect
        prefs.pendingFavorites = emptySet()
        val toFavorite = library.filter { !it.isFavorite && (it.relativePath + it.name) in pending }
        if (toFavorite.isNotEmpty()) {
            restoredFavoritesLauncher.launch(IntentSenderRequest.Builder(MediaActions.favoriteRequest(context, toFavorite, favorite = true)).build())
        }
    }
    return BackupLaunchers(favoritesLauncher, exportLauncher, importLauncher)
}
