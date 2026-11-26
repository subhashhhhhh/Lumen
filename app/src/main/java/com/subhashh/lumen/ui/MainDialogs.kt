package com.subhashh.lumen.ui

import android.app.Activity
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.HfTokenStore
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.ui.albums.AlbumPicker
import com.subhashh.lumen.ui.albums.AlbumTarget
import com.subhashh.lumen.ui.components.ConfirmDialog
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.RenameDialog
import com.subhashh.lumen.ui.hidden.HiddenLockScreen
import com.subhashh.lumen.ui.hidden.LockFlow
import com.subhashh.lumen.ui.hidden.VaultProgressScreen
import com.subhashh.lumen.ui.people.MergePeopleDialog
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.UpdateSession
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.CoverTransitionOverlay
import com.subhashh.lumen.ui.editor.PhotoEditorScreen
import com.subhashh.lumen.ui.editor.VideoEditorScreen
import com.subhashh.lumen.ui.viewer.WallpaperScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The dialogs and sheets [MainScaffold] puts over everything else: album pickers, Hidden's lock,
 * confirmations, the rename and name prompts, wallpaper, resize, dates and reminders.
 */
@NonRestartableComposable
@Composable
internal fun BoxScope.MainDialogs(
    s: MainState,
    pick: PickRequest?,
    library: List<MediaItem>?,
    folders: List<MediaCollection>,
    allPeople: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    coverTransition: CoverTransition,
    viewerOpen: Boolean,
    selecting: Boolean,
    activity: Activity?,
    toast: (String) -> Unit,
    endSelection: () -> Unit,
    finish: (String?) -> Unit,
    withWriteAccess: (List<MediaItem>, () -> Unit) -> Unit,
    toAlbum: (AlbumRequest, AlbumTarget) -> Unit,
    updateAlbums: ((AlbumSettings) -> AlbumSettings) -> Unit,
    regroup: () -> kotlinx.coroutines.Job,
    resetLock: () -> Unit,
    runEncryption: (Boolean, String?) -> Unit,
    showHidden: () -> Unit,
) {
    with(s) {
        AlbumPicker(
            open = albumRequest != null,
            title = stringResource(if (albumRequest?.move == true) R.string.main_move_to_album else R.string.main_copy_to_album),
            albums = folders,
            onPick = { target ->
                albumRequest?.let { toAlbum(it, target) }
                albumRequest = null
            },
            onDismiss = { albumRequest = null },
            newAlbumPath = MediaFiles::newAlbumPath,
        )

        AlbumPicker(
            open = excludePicker,
            title = stringResource(R.string.main_exclude_folder),
            albums = folders,
            onPick = { target ->
                excludePicker = false
                viewModel.exclude(listOf(target.relativePath))
            },
            onDismiss = { excludePicker = false },
            newAlbumPath = MediaFiles::newAlbumPath,
            allowNewAlbum = false,
        )

        lockFlow?.let { flow ->
            HiddenLockScreen(
                flow = flow,
                store = lockStore,
                onSecret = { secret ->
                    enteredSecret = secret
                    // A new lock (set up, changed, or after Forgot) reseals the vault's recovery copy;
                    // entering the current one upgrades an older copy to Argon2id. Both take a
                    // second or two, so they run in the background.
                    kotlinx.coroutines.CoroutineScope(Vault.keyWork).launch {
                        runCatching {
                            if (flow == LockFlow.Setup || flow == LockFlow.Change) Vault.changeSecret(appContext, secret)
                            else Vault.upgradeRecovery(appContext, secret)
                        }.onFailure { android.util.Log.w("LumenVault", "Couldn't update the recovery file", it) }
                    }
                },
                onDone = {
                    lockFlow = null
                    lockSet = lockStore.isSet
                    biometrics = lockStore.biometrics
                    // Changing the lock from Settings doesn't open Hidden; setting up or unlocking does.
                    when {
                        pendingEncryption != null -> {
                            val on = pendingEncryption!!
                            pendingEncryption = null
                            runEncryption(on, enteredSecret)
                            enteredSecret = null
                        }
                        setupForAppLock -> {
                            setupForAppLock = false
                            if (lockStore.isSet) {
                                prefs.appLock = true
                                appLockOn = true
                                activity?.let(AppLock::settingsChanged)
                                toast(context.getString(R.string.main_app_lock_on))
                            }
                        }
                        flow != LockFlow.Change -> showHidden()
                        else -> toast(context.getString(R.string.main_lock_changed))
                    }
                },
                onCancel = {
                    lockFlow = null
                    pendingEncryption = null
                    enteredSecret = null
                },
                onForgot = resetLock,
            )
        }

        // A face in the viewer's sheet flying to or from its person's page: above the viewer.
        if (coverTransition.overViewer) CoverTransitionOverlay(coverTransition)

        // A newer Lumen from the daily check: announced once per version, when nothing else is up.
        val announce = UpdateSession.announce
        if (announce != null && confirmation == null && !viewerOpen && !selecting && settingsPage == null && pick == null) {
            LaunchedEffect(announce) {
                UpdateSession.announce = null
                prefs.announcedUpdate = announce.version
                confirmation = Confirmation(
                    title = context.getString(R.string.settings_update_available, announce.version),
                    message = context.getString(R.string.main_update_message),
                    details = com.subhashh.lumen.ui.components.releaseChanges(announce.notes).ifBlank { null },
                    confirm = context.getString(R.string.main_update_view),
                    cancel = context.getString(R.string.main_update_later),
                ) {
                    settingsHighlight = "software-update"
                    settingsPage = settingsCategoryPage(SettingsCategory.About)
                }
            }
        }
        // Hidden on screen (its grid, a folder, or the viewer opened from it): no screenshots unless allowed.
        com.subhashh.lumen.ui.hidden.SecureWhile(
            hiddenUnlocked && (CollectionKeys.isHidden(openCollection) || CollectionKeys.isHidden(viewerSource)) && !prefs.hiddenScreenshots,
        )
        com.subhashh.lumen.ui.components.FaceUpgradeRequest(
            prefs,
            canShow = announce == null && confirmation == null && !viewerOpen && !selecting && settingsPage == null && pick == null,
            show = { confirmation = it },
        )
        com.subhashh.lumen.ui.components.DiscordRequest(
            prefs,
            canShow = announce == null && confirmation == null && !viewerOpen && !selecting && settingsPage == null && pick == null,
            show = { confirmation = it },
        )
        com.subhashh.lumen.ui.components.StarRequest(
            prefs,
            canShow = announce == null && confirmation == null && !viewerOpen && !selecting && settingsPage == null && pick == null,
            show = { confirmation = it },
        )
        ConfirmDialog(confirmation, onDismiss = { confirmation = null })

        RenameDialog(
            currentName = if (enteringHfToken) "" else null,
            title = stringResource(R.string.main_hf_token_title),
            confirm = stringResource(R.string.common_save),
            keepExtension = false,
            onRename = { token ->
                enteringHfToken = false
                HfTokenStore(context).token = token
                hfTokenVersion++
            },
            onDismiss = { enteringHfToken = false },
        )

        // Details → All Metadata: change a field, or empty it to remove it (#9).
        RenameDialog(
            currentName = metadataEdit?.value,
            title = metadataEdit?.tag.orEmpty(),
            confirm = stringResource(R.string.common_save),
            keepExtension = false,
            freeText = true,
            onRename = { text ->
                val edit = metadataEdit
                metadataEdit = null
                if (edit != null) {
                    val value = text.trim().ifEmpty { null }
                    withWriteAccess(listOf(edit.item)) {
                        scope.launch {
                            val ok = com.subhashh.lumen.data.AllMetadata.write(context, edit.item, edit.tag, value)
                            finish(
                                when {
                                    !ok -> context.getString(R.string.main_metadata_failed, edit.tag)
                                    value == null -> context.getString(R.string.main_metadata_removed, edit.tag)
                                    else -> context.getString(R.string.main_metadata_changed, edit.tag)
                                },
                            )
                        }
                    }
                }
            },
            onDismiss = { metadataEdit = null },
        )

        RenameDialog(
            currentName = renameName,
            onRename = { newName ->
                val item = renameItem
                renameItem = null
                if (item != null) {
                    withWriteAccess(listOf(item)) {
                        scope.launch {
                            val ok = MediaFiles.rename(context, item, newName)
                            finish(if (ok) context.getString(R.string.main_renamed_to, newName) else context.getString(R.string.main_rename_failed))
                        }
                    }
                }
            },
            onDismiss = { renameItem = null },
        )

        RenameDialog(
            currentName = personNameRequest?.let { it.name ?: personNameSuggestion ?: "" },
            title = stringResource(if (personNameRequest?.name == null) R.string.main_add_name else R.string.common_rename),
            confirm = stringResource(R.string.common_done),
            keepExtension = false,
            acceptUnchanged = personNameRequest?.name == null,
            onRename = { name ->
                val person = personNameRequest
                personNameRequest = null
                personNameSuggestion = null
                val started = faceStarted != null
                faceStarted = null
                val proposal = person?.let { mergeProposalFor(it, name, allPeople) }
                if (proposal != null) mergeProposal = proposal
                else if (person != null) scope.launch(Dispatchers.IO) {
                    com.subhashh.lumen.ai.FaceStore.of(context).rename(person.id, name)
                    if (started) regroup()
                }
            },
            onDismiss = {
                personNameRequest = null
                personNameSuggestion = null
                // A face made into a person only to be named, and not named after all: it waits again.
                faceStarted?.let { id -> scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).unstartPerson(id) } }
                faceStarted = null
            },
        )
        MergePeopleDialog(
            proposal = mergeProposal,
            onMerge = { p ->
                mergeProposal = null
                // Its page becomes the merged person's.
                if (openCollection == CollectionKeys.person(p.person.id)) openCollection = CollectionKeys.person(p.match.id)
                scope.launch(Dispatchers.IO) {
                    com.subhashh.lumen.ai.FaceStore.of(context).merge(keep = p.match.id, gone = p.person.id)
                    regroup()
                }
            },
            onKeepSeparate = { p ->
                mergeProposal = null
                scope.launch(Dispatchers.IO) { com.subhashh.lumen.ai.FaceStore.of(context).rename(p.person.id, p.name) }
            },
            onDismiss = { mergeProposal = null },
        )
        RenameDialog(
            currentName = albumNameRequest?.let { it.album?.title ?: "" },
            title = stringResource(if (albumNameRequest?.album == null) R.string.main_new_album else R.string.main_rename_album),
            confirm = stringResource(if (albumNameRequest?.album == null) R.string.main_create else R.string.common_rename),
            keepExtension = false,
            onRename = { name ->
                val album = albumNameRequest?.album
                albumNameRequest = null
                val clean = name.trim().replace('/', '_')
                if (album == null) {
                    // New albums go in Pictures/, where any app may add photos without All files access.
                    val path = MediaFiles.newAlbumPath(clean)
                    if (folders.any { it.relativePath.equals(path, ignoreCase = true) }) {
                        toast(context.getString(R.string.main_album_exists, clean))
                    } else {
                        updateAlbums { it.copy(created = it.created + path) }
                        collectionsMenuOpen = false
                        openCollection = CollectionKeys.emptyAlbum(path)
                    }
                } else {
                    val parent = album.relativePath.trimEnd('/').substringBeforeLast('/', "")
                    val newPath = (if (parent.isEmpty()) "" else "$parent/") + clean + "/"
                    when {
                        folders.any { it.relativePath.equals(newPath, ignoreCase = true) } ->
                            toast(context.getString(R.string.main_album_exists_there, clean))
                        album.count == 0 -> {
                            updateAlbums { it.renamed(album.relativePath, newPath) }
                            endSelection()
                        }
                        else -> {
                            val items = library.orEmpty().filter { it.relativePath == album.relativePath }
                            val run = {
                                scope.launch {
                                    val result = MediaFiles.renameAlbum(context, items, album.relativePath, newPath)
                                    if (result.done > 0) updateAlbums { it.renamed(album.relativePath, newPath) }
                                    finish(
                                        when {
                                            result.failed == 0 -> null
                                            result.needsAllFilesAccess ->
                                                context.getString(R.string.main_rename_album_needs_access, album.title)
                                            else -> context.getString(R.string.main_rename_album_partial, result.done, items.size, result.failed)
                                        },
                                    )
                                }
                            }
                            if (Environment.isExternalStorageManager()) run() else withWriteAccess(items) { run() }
                        }
                    }
                }
            },
            onDismiss = { albumNameRequest = null },
        )

        wallpaperItem?.let { item ->
            com.subhashh.lumen.ui.theme.AlwaysDark { WallpaperScreen(
                item = item,
                onDone = { message ->
                    wallpaperItem = null
                    toast(message)
                },
                onCancel = { wallpaperItem = null },
            ) }
        }

        editItem?.let { item ->
            val done = { message: String ->
                editItem = null
                finish(message)
            }
            if (item.isVideo) {
                com.subhashh.lumen.ui.theme.AlwaysDark { VideoEditorScreen(item, onDone = done, onCancel = { editItem = null }) }
            } else {
                com.subhashh.lumen.ui.theme.AlwaysDark { PhotoEditorScreen(item, withWriteAccess = withWriteAccess, onDone = done, onCancel = { editItem = null }) }
            }
        }

        ResizeSheet(resizeRequest, onClose = { resizeRequest = null }, toast = toast, finish = { finish(it) })

        vaultProgress?.let { VaultProgressScreen(it.encrypting, it.progress) }

        AdjustDateSheet(dateRequest, onClose = { dateRequest = null }, withWriteAccess = withWriteAccess, finish = { finish(it) })

        PhotoReminderSheet(remindRequest, onClose = { remindRequest = null }, onSaved = { finish(it) })
    }
}
