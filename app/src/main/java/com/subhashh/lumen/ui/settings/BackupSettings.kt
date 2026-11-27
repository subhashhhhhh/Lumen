package com.subhashh.lumen.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.subhashh.lumen.R
import com.subhashh.lumen.data.backup.LumenBackup
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.DialogButtons
import com.subhashh.lumen.ui.components.DialogMessage
import com.subhashh.lumen.ui.components.DialogTitle
import com.subhashh.lumen.ui.components.dialogAlignment
import com.subhashh.lumen.ui.components.dialogCard
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.Lumen
import kotlinx.coroutines.launch

/**
 * Settings → Backup → Lumen Backup: set up a folder (encrypted or not), choose what goes in,
 * back up now, restore. Stage 1 covers Lumen's own data; photos and videos come next.
 */
@Composable
internal fun BackupGroup(highlight: String?, onConfirm: (Confirmation) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val folder = remember(version) { LumenBackup.folderUri(context) }
    val encrypted = remember(version) { context.getSharedPreferences("lumen-backup", 0).getBoolean("encrypted", false) }
    val prefs = remember { context.getSharedPreferences("lumen-backup-choices", 0) }

    var picked by remember { mutableStateOf<Uri?>(null) }
    var setUpEncrypted by remember { mutableStateOf(true) }
    var asking by remember { mutableStateOf<Ask?>(null) }
    var recoveryShown by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<LumenBackup.Progress?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<List<LumenBackup.Snapshot>?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val existing = LumenBackup.existing(context, uri)
            picked = uri
            asking = when {
                existing == null -> Ask.NewPassword
                existing.encrypted -> Ask.Unlock
                else -> {
                    LumenBackup.connect(context, uri, null, null)
                    version++
                    null
                }
            }
        }
    }

    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Group(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer), note = message) {
        if (folder == null) {
            Anchor("backup-set-up", highlight) {
                SettingsRow(title = stringResource(R.string.backup_set_up), onClick = { message = null; pick.launch(null) }, icon = LumenIcons.Export, iconColor = Green)
            }
            return@Group
        }
        val label = remember(folder) { runCatching { com.subhashh.lumen.data.backup.BackupFolder(context, folder).label() }.getOrDefault("") }
        SettingsRow(
            title = stringResource(R.string.backup_folder),
            value = label,
            subtitle = stringResource(if (encrypted) R.string.backup_encrypted else R.string.backup_not_encrypted),
            onClick = {
                onConfirm(
                    Confirmation(
                        title = context.getString(R.string.backup_disconnect_title),
                        message = context.getString(R.string.backup_disconnect_message),
                        confirm = context.getString(R.string.backup_disconnect),
                        destructive = true,
                    ) { LumenBackup.disconnect(context); version++ },
                )
            },
            icon = LumenIcons.Folder,
            iconColor = Blue,
        )
        for (item in LumenBackup.Item.entries) {
            Divider()
            var on by remember { mutableStateOf(prefs.getBoolean(item.key, item.defaultOn)) }
            ToggleRow(
                title = stringResource(item.label),
                checked = on,
                onCheckedChange = { on = it; prefs.edit { putBoolean(item.key, it) } },
            )
        }
        Divider()
        val p = progress
        val last = remember(version) { LumenBackup.lastBackup(context) }
        SettingsRow(
            title = stringResource(R.string.backup_now),
            subtitle = when {
                p != null && p.total > 0 -> "${context.getString(p.step)} ${(p.done * 100 / p.total).toInt()}%"
                p != null -> context.getString(p.step)
                last > 0 -> context.getString(R.string.backup_last, DateUtils.getRelativeTimeSpanString(last).toString())
                else -> context.getString(R.string.backup_never)
            },
            onClick = if (p != null) null else ({
                message = null
                scope.launch {
                    val items = LumenBackup.Item.entries.filter { prefs.getBoolean(it.key, it.defaultOn) }.toSet()
                    progress = LumenBackup.Progress(R.string.backup_step_preparing, 0, 0)
                    message = runCatching { LumenBackup.backUp(context, items) { progress = it } }
                        .fold(
                            { context.getString(R.string.backup_done, size(it.total), size(it.uploaded)) },
                            { context.getString(R.string.backup_failed, it.message ?: it.javaClass.simpleName) },
                        )
                    progress = null
                    version++
                }
            }),
            icon = LumenIcons.Import,
            iconColor = Green,
        )
        Divider()
        SettingsRow(
            title = stringResource(R.string.backup_restore),
            onClick = if (progress != null) null else ({
                scope.launch { restoring = LumenBackup.snapshots(context) }
            }),
            icon = LumenIcons.Restore,
            iconColor = Orange,
        )
        if (encrypted) {
            Divider()
            SettingsRow(title = stringResource(R.string.backup_change_password), onClick = { asking = Ask.ChangePassword }, icon = LumenIcons.Lock, iconColor = Gray)
            Divider()
            SettingsRow(
                title = stringResource(R.string.backup_new_recovery_key),
                onClick = {
                    onConfirm(
                        Confirmation(
                            title = context.getString(R.string.backup_new_recovery_key),
                            message = context.getString(R.string.backup_new_recovery_key_message),
                            confirm = context.getString(R.string.backup_new_recovery_key_make),
                        ) { scope.launch { recoveryShown = LumenBackup.newRecoveryKey(context) } },
                    )
                },
                icon = LumenIcons.Shield,
                iconColor = Gray,
            )
        }
    }

    // ---- Dialogs ----

    when (val ask = asking) {
        Ask.NewPassword -> PasswordDialog(
            title = stringResource(R.string.backup_encrypt_title),
            message = stringResource(R.string.backup_encrypt_message),
            confirmField = true,
            allowSkip = true,
            onDismiss = { asking = null },
            onSkip = {
                asking = null
                scope.launch { LumenBackup.setUp(context, picked ?: return@launch, null); version++ }
            },
        ) { password, _ ->
            asking = null
            scope.launch {
                recoveryShown = LumenBackup.setUp(context, picked ?: return@launch, password)
                version++
            }
        }
        Ask.Unlock -> PasswordDialog(
            title = stringResource(R.string.backup_unlock_title),
            message = stringResource(R.string.backup_unlock_message),
            confirmField = false,
            allowRecovery = true,
            error = unlockError,
            onDismiss = { asking = null; unlockError = null },
        ) { password, recovery ->
            unlockError = null
            scope.launch {
                val ok = LumenBackup.connect(context, picked ?: return@launch, password, recovery)
                if (ok) { asking = null; message = null; version++ } else unlockError = context.getString(R.string.backup_unlock_wrong)
            }
        }
        Ask.ChangePassword -> PasswordDialog(
            title = stringResource(R.string.backup_change_password),
            message = stringResource(R.string.backup_change_password_message),
            confirmField = true,
            onDismiss = { asking = null },
        ) { password, _ ->
            asking = null
            scope.launch {
                message = context.getString(if (LumenBackup.changePassword(context, password ?: return@launch)) R.string.backup_password_changed else R.string.backup_failed_short)
            }
        }
        null -> Unit
    }

    recoveryShown?.let { key -> RecoveryKeyDialog(key) { recoveryShown = null } }

    restoring?.let { snapshots ->
        RestoreDialog(snapshots, onDismiss = { restoring = null }) { snapshot ->
            restoring = null
            onConfirm(
                Confirmation(
                    title = context.getString(R.string.backup_restore_title),
                    message = context.getString(R.string.backup_restore_message, snapshot.items.joinToString(", ") { context.getString(it.label) }),
                    confirm = context.getString(R.string.backup_restore_go),
                    destructive = true,
                ) {
                    scope.launch {
                        val ok = runCatching { LumenBackup.stageRestore(context, snapshot, snapshot.items.toSet()) { progress = it } }
                        progress = null
                        ok.onSuccess {
                            // Lumen starts again; the restore goes in before anything opens a database.
                            val intent = Intent.makeRestartActivityTask(android.content.ComponentName(context, com.subhashh.lumen.GalleryActivity::class.java))
                            context.startActivity(intent)
                            Runtime.getRuntime().exit(0)
                        }.onFailure { message = context.getString(R.string.backup_failed, it.message ?: it.javaClass.simpleName) }
                    }
                },
            )
        }
    }
}

private enum class Ask { NewPassword, Unlock, ChangePassword }

/** A password, twice when it's new; or, to open a backup, a password or its recovery key. */
@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirmField: Boolean,
    onDismiss: () -> Unit,
    allowSkip: Boolean = false,
    allowRecovery: Boolean = false,
    error: String? = null,
    onSkip: () -> Unit = {},
    onDone: (password: String?, recoveryKey: String?) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf(false) }
    val valid = if (recovery) first.isNotBlank() else first.length >= 8 && (!confirmField || first == second)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(320.dp).dialogCard().padding(top = 22.dp), horizontalAlignment = dialogAlignment) {
            DialogTitle(title)
            Spacer(Modifier.height(8.dp))
            DialogMessage(if (recovery) stringResource(R.string.backup_recovery_enter) else message)
            Spacer(Modifier.height(14.dp))
            Field(first, { first = it }, if (recovery) stringResource(R.string.backup_recovery_key) else stringResource(R.string.backup_password), secret = !recovery)
            if (confirmField && !recovery) {
                Spacer(Modifier.height(8.dp))
                Field(second, { second = it }, stringResource(R.string.backup_password_again), secret = true)
            }
            if (error != null) {
                Text(error, color = Lumen.colors.destructive, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp))
            }
            if (!recovery && confirmField && first.isNotEmpty() && first.length < 8) {
                Text(stringResource(R.string.backup_password_short), color = Lumen.colors.destructive, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp))
            }
            val extra = when {
                allowRecovery -> stringResource(if (recovery) R.string.backup_use_password else R.string.backup_use_recovery)
                allowSkip -> stringResource(R.string.backup_no_encryption)
                else -> null
            }
            if (extra != null) {
                Text(
                    extra,
                    color = Lumen.colors.accent,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable { if (allowRecovery) { recovery = !recovery; first = "" } else onSkip() }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                cancel = stringResource(R.string.common_cancel),
                onCancel = onDismiss,
                confirm = stringResource(R.string.common_done),
                onConfirm = { if (valid) onDone(first.takeIf { !recovery }, first.takeIf { recovery }) },
                enabled = valid,
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean) {
    val colors = Lumen.colors
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(Lumen.shapes.field)
            .background(colors.field)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (value.isEmpty()) Text(hint, color = colors.secondary, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The recovery key, shown once: copy it, then say it's kept. */
@Composable
private fun RecoveryKeyDialog(key: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    // A small text file, wherever the person chooses (a USB stick, a cloud folder, printed later).
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.getString(R.string.backup_recovery_file, key)
        saved = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(Modifier.width(320.dp).dialogCard().padding(top = 22.dp), horizontalAlignment = dialogAlignment) {
            DialogTitle(stringResource(R.string.backup_recovery_title))
            Spacer(Modifier.height(8.dp))
            DialogMessage(stringResource(R.string.backup_recovery_message))
            Spacer(Modifier.height(14.dp))
            Text(
                key,
                color = Lumen.colors.text,
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(Lumen.shapes.field)
                    .background(Lumen.colors.field)
                    .padding(12.dp),
            )
            Text(
                stringResource(R.string.backup_recovery_save_file),
                color = Lumen.colors.accent,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable { save.launch(context.getString(R.string.backup_recovery_file_name)) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
            when {
                saved -> DialogMessage(stringResource(R.string.backup_recovery_file_saved), Modifier.padding(top = 4.dp))
                copied -> DialogMessage(stringResource(R.string.backup_recovery_copied), Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                cancel = stringResource(R.string.common_copy),
                onCancel = {
                    // Marked sensitive, so Android doesn't show it in the clipboard preview.
                    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                        android.content.ClipData.newPlainText("Lumen recovery key", key).apply {
                            description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        },
                    )
                    copied = true
                },
                confirm = stringResource(R.string.backup_recovery_saved),
                onConfirm = onDone,
                enabled = copied || saved,
            )
        }
    }
}

/** The backups in the folder, newest first: pick one to restore. */
@Composable
private fun RestoreDialog(snapshots: List<LumenBackup.Snapshot>, onDismiss: () -> Unit, onPick: (LumenBackup.Snapshot) -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(320.dp).dialogCard().padding(top = 22.dp), horizontalAlignment = dialogAlignment) {
            DialogTitle(stringResource(R.string.backup_restore))
            Spacer(Modifier.height(8.dp))
            if (snapshots.isEmpty()) DialogMessage(stringResource(R.string.backup_restore_none))
            Column(Modifier.padding(vertical = 6.dp)) {
                for (s in snapshots.take(20)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(s) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            DateUtils.formatDateTime(context, s.created, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR),
                            color = Lumen.colors.text,
                            fontSize = 16.sp,
                        )
                        Text(s.device, color = Lumen.colors.secondary, fontSize = 13.sp)
                    }
                }
            }
            Text(
                stringResource(R.string.common_cancel),
                color = Lumen.colors.accent,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
