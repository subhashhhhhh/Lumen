package com.subhashh.lumen.ui

import android.app.Activity
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subhashh.lumen.R
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.albums.AlbumTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What [MainState.fileActions] hands back to [MainScaffold]. */
internal class FileActions(
    val excludeFolders: (List<MediaItem>) -> Unit,
    val excludeFromHidden: (List<MediaItem>) -> Unit,
    val withWriteAccess: (List<MediaItem>, () -> Unit) -> Unit,
    val finish: (String?) -> Unit,
    val toAlbum: (AlbumRequest, AlbumTarget) -> Unit,
    val rotate: (List<MediaItem>, Int) -> Unit,
)

/**
 * What [MainScaffold] does to files: leaving folders out of Lumen, asking Android for write access
 * when it is needed, moving and copying to albums, rotating, and loading a name to rename.
 */
@Composable
internal fun MainState.fileActions(
    toast: (String) -> Unit,
    endSelection: () -> Unit,
): FileActions {
    val excludeFolders: (List<MediaItem>) -> Unit = { items ->
        val folders = items.map { it.relativePath }.distinct()
        viewModel.exclude(folders)
        endSelection()
        val name = folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
        toast(
            if (name != null) context.getString(R.string.main_excluded_name, name)
            else context.resources.getQuantityString(R.plurals.main_excluded_folders, folders.size, folders.size),
        )
    }

    val excludeFromHidden: (List<MediaItem>) -> Unit = { items ->
        val folders = items.map { it.relativePath }.distinct()
        viewModel.exclude(folders)
        endSelection()
        // A folder whose contents just left Hidden would be empty: step back to the list.
        if (CollectionKeys.hiddenFolderId(openCollection.orEmpty()) != null) openCollection = CollectionKeys.Hidden
        val name = folders.singleOrNull()?.trimEnd('/')?.substringAfterLast('/')
        toast(
            if (name != null) context.getString(R.string.main_excluded_name, name)
            else context.resources.getQuantityString(R.plurals.main_excluded_folders, folders.size, folders.size),
        )
    }

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
        endSelection()
        if (message != null) toast(message)
    }

    val toAlbum: (AlbumRequest, AlbumTarget) -> Unit = { request, target ->
        if (request.move) {
            withWriteAccess(request.items) {
                scope.launch {
                    val result = MediaFiles.move(context, request.items, target.relativePath)
                    finish(fileMessage(context, move = true, result, target.title))
                }
            }
        } else {
            scope.launch {
                val result = MediaFiles.copy(context, request.items, target.relativePath)
                finish(fileMessage(context, move = false, result, target.title))
            }
        }
    }
    val rotate: (List<MediaItem>, Int) -> Unit = { items, degrees ->
        val rotatable = items.filter(MediaFiles::canRotate)
        if (rotatable.isNotEmpty()) {
            withWriteAccess(rotatable) {
                scope.launch {
                    // Videos are remuxed, which takes a moment for long ones.
                    if (rotatable.any { it.isVideo }) toast(context.getString(R.string.main_rotating))
                    val result = MediaFiles.rotate(context, rotatable, degrees)
                    // Success shows itself (the photo turns); only a failure needs a message.
                    finish(if (result.failed == 0) null else context.getString(R.string.main_rotate_failed, result.failed, rotatable.size))
                }
            }
        }
    }
    LaunchedEffect(renameItem) {
        renameName = renameItem?.let { item -> withContext(Dispatchers.IO) { MediaFiles.displayName(context, item) } }
    }

    return FileActions(excludeFolders, excludeFromHidden, withWriteAccess, finish, toAlbum, rotate)
}
