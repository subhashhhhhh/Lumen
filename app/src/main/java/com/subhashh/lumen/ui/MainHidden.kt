package com.subhashh.lumen.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subhashh.lumen.R
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.MediaActions
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.data.vault.VaultMigration
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.hidden.HiddenLock
import com.subhashh.lumen.ui.hidden.LockFlow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What [MainState.hiddenActions] hands back to [MainScaffold]. */
internal class HiddenActions(
    val needsAllFiles: () -> Boolean,
    val openHidden: () -> Unit,
    val showHidden: () -> Unit,
    val resetLock: () -> Unit,
    val unhide: (List<MediaItem>) -> Unit,
    val hide: (List<MediaItem>) -> Unit,
    val hideFolders: (List<MediaItem>) -> Unit,
    val runEncryption: (Boolean, String?) -> Unit,
    val shareHidden: (List<MediaItem>) -> Unit,
    val deleteHidden: (List<MediaItem>) -> Unit,
    val confirmDelete: (List<MediaItem>, Boolean, () -> Unit) -> Unit,
)

/**
 * Hidden, as [MainScaffold] drives it: opening it behind Lumen's lock, hiding and unhiding photos
 * and folders (into the encrypted vault when Encrypt Hidden is on), turning that encryption on or
 * off, sharing and deleting from Hidden, and locking it again whenever it is left. Also the
 * confirmation before Lumen itself trashes or deletes.
 */
@Composable
internal fun MainState.hiddenActions(
    library: List<MediaItem>?,
    hidden: List<MediaItem>?,
    excluded: com.subhashh.lumen.data.Exclusions,
    activity: Activity?,
    toast: (String) -> Unit,
    endSelection: () -> Unit,
): HiddenActions {
    /** Hidden content lives outside MediaStore; without All files access, explain and link to it. */
    val needsAllFiles: () -> Boolean = {
        val missing = !HiddenMedia.available()
        if (missing) {
            confirmation = Confirmation(
                title = context.getString(R.string.main_all_files_title),
                message = context.getString(R.string.main_all_files_message),
                confirm = context.getString(R.string.main_open_settings),
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri()),
                    )
                }
            }
        }
        missing
    }
    val openHidden: () -> Unit = open@{
        if (needsAllFiles()) return@open
        // First time: set up Lumen's own lock; afterwards: unlock with it.
        lockFlow = if (lockStore.isSet) LockFlow.Unlock else LockFlow.Setup
    }
    val showHidden = {
        hiddenUnlocked = true
        // The vault (Encrypt Hidden) opens with Hidden: from the Keystore, or after a reinstall
        // from its recovery file with the lock just typed.
        Vault.open(appContext, enteredSecret)
        enteredSecret = null
        viewModel.loadHidden()
        tab = LumenTab.Collections
        openCollection = CollectionKeys.Hidden
    }
    // Forgot the Lumen lock: the phone's own screen lock vouches for a reset.
    val resetLock: () -> Unit = reset@{
        val host = activity ?: return@reset
        val reset = {
            lockStore.clear()
            lockSet = false
            biometrics = false
            lockFlow = LockFlow.Setup
        }
        if (HiddenLock.deviceLockSet(host)) {
            HiddenLock.deviceCredential(host, onSuccess = reset)
        } else {
            confirmation = Confirmation(
                title = context.getString(R.string.main_reset_lock_title),
                message = context.getString(R.string.main_reset_lock_message),
                confirm = context.getString(R.string.main_reset),
                destructive = true,
            ) { reset() }
        }
    }
    val afterHiddenChange = { message: String ->
        viewModel.refresh()
        viewModel.loadHidden()
        endSelection()
        toast(message)
    }
    val unhide: (List<MediaItem>) -> Unit = unhide@{ all ->
        // Encrypted items decrypt back to their folder, visible.
        val (vaulted, items) = all.partition(Vault::isVault)
        if (vaulted.isNotEmpty()) {
            scope.launch {
                val restored = vaulted.count { Vault.restore(context, it, hidden = false) != null }
                viewModel.loadVault()
                if (items.isEmpty()) afterHiddenChange(if (restored == vaulted.size) context.getString(R.string.main_unhidden) else context.getString(R.string.main_unhide_failed_count, vaulted.size - restored))
            }
            if (items.isEmpty()) return@unhide
        }
        val plans = items.mapNotNull(HiddenMedia::unhidePlan).distinct()
        val doable = plans.filterNot { it is HiddenMedia.Unhide.DotFolder }
        if (doable.isEmpty()) {
            val folder = plans.filterIsInstance<HiddenMedia.Unhide.DotFolder>().firstOrNull()?.folder?.name
            toast(context.getString(R.string.main_unhide_dot_folder, folder))
            return@unhide
        }
        val run = { scope.launch { afterHiddenChange(context.getString(if (HiddenMedia.unhide(context, doable) > 0) R.string.main_unhidden else R.string.main_unhide_failed)) } }
        val folders = doable.filterIsInstance<HiddenMedia.Unhide.Folder>()
        if (folders.isEmpty()) {
            run()
        } else {
            val folderSet = folders.mapTo(HashSet()) { it.folder.absolutePath }
            val affected = hidden.orEmpty().count { HiddenMedia.file(it)?.parentFile?.absolutePath in folderSet }
            val name = folders.singleOrNull()?.folder?.name
            confirmation = Confirmation(
                title = if (name != null) context.getString(R.string.main_unhide_folder_title, name)
                else context.resources.getQuantityString(R.plurals.main_unhide_folders_title, folders.size, folders.size),
                message = context.resources.getQuantityString(R.plurals.main_unhide_folders_message, affected, affected),
                confirm = context.getString(R.string.common_unhide),
            ) { run() }
        }
    }
    val hide: (List<MediaItem>) -> Unit = hide@{ items ->
        if (needsAllFiles()) return@hide
        // Encrypt Hidden on: straight into the vault, which opens from the Keystore for this.
        if (encryptHidden && Vault.open(appContext)) {
            scope.launch {
                val n = VaultMigration.hideIntoVault(context, items) { MediaFiles.filePath(context, it) }
                if (!hiddenUnlocked) Vault.close()
                afterHiddenChange(if (n == items.size) context.getString(R.string.main_hidden_encrypted_find) else context.getString(R.string.main_hid_n_of, n, items.size))
            }
            return@hide
        }
        scope.launch {
            val paths = withContext(Dispatchers.IO) { items.mapNotNull { MediaFiles.filePath(context, it) } }
            val n = HiddenMedia.hide(context, paths)
            afterHiddenChange(if (n == items.size) context.getString(R.string.main_hidden_find) else context.getString(R.string.main_hid_n_of, n, items.size))
        }
    }
    val hideFolders: (List<MediaItem>) -> Unit = hide@{ items ->
        if (needsAllFiles()) return@hide
        scope.launch {
            val folders = withContext(Dispatchers.IO) {
                items.mapNotNull { MediaFiles.filePath(context, it)?.let(::File)?.parentFile }.distinctBy { it.absolutePath }
            }
            if (folders.isEmpty()) return@launch
            val paths = folders.mapTo(HashSet()) { it.absolutePath }
            val root = Environment.getExternalStorageDirectory()
            val affected = library.orEmpty().count { File(root, it.relativePath).absolutePath in paths }
            val name = folders.singleOrNull()?.name
            val encrypt = encryptHidden
            confirmation = Confirmation(
                title = if (name != null) context.getString(R.string.main_hide_folder_title, name)
                else context.resources.getQuantityString(R.plurals.main_hide_folders_title, folders.size, folders.size),
                message = context.resources.getQuantityString(
                    when {
                        encrypt && name != null -> R.plurals.main_hide_folder_encrypt_message
                        encrypt -> R.plurals.main_hide_folders_encrypt_message
                        name != null -> R.plurals.main_hide_folder_message
                        else -> R.plurals.main_hide_folders_message
                    },
                    affected,
                    affected,
                ),
                confirm = context.getString(R.string.common_hide),
            ) {
                if (encrypt && Vault.open(appContext)) {
                    scope.launch {
                        val inFolders = library.orEmpty().filter { File(root, it.relativePath).absolutePath in paths }
                        val n = VaultMigration.hideIntoVault(context, inFolders) { MediaFiles.filePath(context, it) }
                        if (!hiddenUnlocked) Vault.close()
                        afterHiddenChange(if (n == inFolders.size) context.getString(R.string.main_hidden_and_encrypted) else context.getString(R.string.main_hid_n_of, n, inFolders.size))
                    }
                    return@Confirmation
                }
                scope.launch {
                    val n = HiddenMedia.hideFolders(context, folders)
                    afterHiddenChange(context.getString(if (n == paths.size) R.string.main_hidden_find else R.string.main_hide_some_folders_failed))
                }
            }
        }
    }
    /**
     * Encrypt Hidden switched on or off, once the lock is confirmed: moves everything into or out of
     * the vault with a progress screen. [secret] makes the vault's recovery key (or opens an
     * existing vault from an earlier install).
     */
    val runEncryption: (on: Boolean, secret: String?) -> Unit = { on, secret ->
        scope.launch {
            val opened = when {
                Vault.open(appContext, secret) -> true
                on && secret != null && !Vault.exists(appContext) -> {
                    withContext(Dispatchers.Default) { Vault.create(appContext, secret) }
                    true
                }
                else -> false
            }
            if (!opened) {
                toast(context.getString(R.string.main_vault_wrong_lock))
                return@launch
            }
            vaultProgress = VaultProgress(encrypting = on, progress = null)
            val result = if (on) {
                VaultMigration.encryptAll(context, excluded) { vaultProgress = VaultProgress(encrypting = true, progress = it) }
            } else {
                VaultMigration.decryptAll(context) { vaultProgress = VaultProgress(encrypting = false, progress = it) }
            }
            vaultProgress = null
            // On even with some failures: those files stay hidden the old way, nothing is lost.
            prefs.encryptHidden = on
            encryptHidden = on
            if (!hiddenUnlocked) Vault.close()
            viewModel.loadHidden()
            viewModel.refresh()
            toast(
                when {
                    result.failed > 0 -> context.getString(
                        if (on) R.string.main_vault_partial_encrypt else R.string.main_vault_partial_decrypt,
                        result.moved,
                        result.failed,
                    )
                    on -> if (result.moved == 0) context.getString(R.string.main_encrypt_hidden_on)
                    else context.resources.getQuantityString(R.plurals.main_encrypted_items, result.moved, result.moved)
                    else -> context.resources.getQuantityString(R.plurals.main_decrypted_items, result.moved, result.moved)
                },
            )
        }
    }
    /** Share from Hidden: encrypted items go out as decrypted copies in Lumen's private cache. */
    val shareHidden: (List<MediaItem>) -> Unit = { items ->
        scope.launch {
            val shareable = items.mapNotNull { item ->
                if (!Vault.isVault(item)) item
                else Vault.cachedCopy(context, item)?.let { item.copy(uri = android.net.Uri.fromFile(it)) }
            }
            MediaActions.share(context, shareable)
        }
    }
    val deleteHidden: (List<MediaItem>) -> Unit = { items ->
        val n = items.size
        confirmation = Confirmation(
            title = if (n == 1) context.getString(R.string.main_delete_this_item) else context.resources.getQuantityString(R.plurals.main_delete_items_title, n, n),
            message = context.getString(R.string.main_delete_hidden_message),
            confirm = context.getString(R.string.common_delete),
            destructive = true,
        ) {
            scope.launch {
                val (vaulted, plain) = items.partition(Vault::isVault)
                val deleted = HiddenMedia.delete(plain) + vaulted.count(Vault::delete)
                if (vaulted.isNotEmpty()) viewModel.loadVault()
                afterHiddenChange(if (deleted == n) context.getString(R.string.main_deleted) else context.getString(R.string.main_delete_failed, n - deleted, n))
            }
        }
    }
    // With Media management on, Android trashes and deletes without asking, so Lumen asks instead.
    // Without it Android shows its own prompt; a second one from Lumen would be one too many.
    val confirmDelete: (List<MediaItem>, Boolean, () -> Unit) -> Unit = { items, forever, proceed ->
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || !android.provider.MediaStore.canManageMedia(context)) {
            proceed()
        } else {
            val n = items.size
            confirmation = Confirmation(
                title = if (n == 1) context.getString(R.string.main_delete_this_item) else context.resources.getQuantityString(R.plurals.main_delete_items_title, n, n),
                message = when {
                    !forever -> context.resources.getQuantityString(R.plurals.main_trash_message, n, n)
                    n == 1 -> context.getString(R.string.main_empty_trash_message_one)
                    else -> context.resources.getQuantityString(R.plurals.main_delete_forever_message, n, n)
                },
                confirm = context.getString(if (forever) R.string.main_delete_forever_title_case else R.string.common_delete),
                destructive = true,
                onConfirm = proceed,
            )
        }
    }
    // Re-lock as soon as Hidden is left: its grid and the viewer over it are the only ways in.
    LaunchedEffect(openCollection, viewerSource) {
        if (!CollectionKeys.isHidden(openCollection) && !CollectionKeys.isHidden(viewerSource)) hiddenUnlocked = false
    }
    // Locking Hidden locks the vault too: key forgotten, decrypted share copies gone.
    LaunchedEffect(hiddenUnlocked) {
        if (!hiddenUnlocked) {
            Vault.close()
            Vault.clearCache(appContext)
            viewModel.clearVault()
        }
    }
    // Leaving the app locks it too, closing anything that shows hidden photos.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (hiddenUnlocked) {
            if (CollectionKeys.isHidden(viewerSource)) viewerSource = null
            if (CollectionKeys.isHidden(openCollection)) openCollection = null
            hiddenUnlocked = false
        }
    }
    // After a restart, never land inside Hidden without unlocking (its list is empty until then).
    LaunchedEffect(openCollection, hiddenUnlocked) {
        if (CollectionKeys.isHidden(openCollection) && !hiddenUnlocked) openCollection = null
    }

    return HiddenActions(needsAllFiles, openHidden, showHidden, resetLock, unhide, hide, hideFolders, runEncryption, shareHidden, deleteHidden, confirmDelete)
}
