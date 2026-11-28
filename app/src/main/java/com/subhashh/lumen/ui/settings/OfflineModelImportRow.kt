package com.subhashh.lumen.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.ModelImports
import com.subhashh.lumen.ui.icons.LumenIcons

private object OpenModelDocuments : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}

@Composable
internal fun OfflineModelImportRow(
    session: ModelImportSession = ModelImportSession.process,
) {
    val context = LocalContext.current
    val state by session.state.collectAsState()
    val importing = state is ModelImportSession.State.Importing
    val picker = rememberLauncherForActivityResult(OpenModelDocuments) { uris ->
        session.start(context, uris)
    }
    val title = stringResource(R.string.smart_import_files)
    ActionRow(
        title = title,
        subtitle = stringResource(if (importing) R.string.smart_import_working else R.string.smart_import_help),
        action = LumenIcons.Import,
        actionLabel = title,
        onAction = if (importing) null else ({ picker.launch(arrayOf("*/*")) }),
        icon = LumenIcons.Import,
        iconColor = Blue,
    )
    (state as? ModelImportSession.State.Finished)?.let { finished ->
        val message = when (val outcome = finished.outcome) {
            is ModelImportSession.Outcome.Imported -> importMessage(context, outcome.result)
            ModelImportSession.Outcome.Busy -> stringResource(R.string.smart_import_busy)
            ModelImportSession.Outcome.AccessFailed -> stringResource(R.string.smart_import_io)
            ModelImportSession.Outcome.UnexpectedFailure -> stringResource(R.string.smart_import_unexpected)
            ModelImportSession.Outcome.Cancelled -> stringResource(R.string.smart_import_cancelled)
        }
        val dismiss = { session.dismiss(finished) }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.smart_import_result)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

private fun importMessage(context: Context, imported: ModelImports.Result): String =
    with(context) {
        val rejection = imported.rejection
        if (rejection != null) {
            val (name, error) = rejection
            val reason = when (error) {
                ModelImports.Error.UnknownFile -> R.string.smart_import_unknown
                ModelImports.Error.SizeMismatch -> R.string.smart_import_size
                ModelImports.Error.ChecksumMismatch -> R.string.smart_import_checksum
                ModelImports.Error.Archive -> R.string.smart_import_archive
                ModelImports.Error.DuplicateFile -> R.string.smart_import_duplicate
                ModelImports.Error.ReadOrWrite -> R.string.smart_import_io
                ModelImports.Error.RollbackFailed -> R.string.smart_import_rollback
            }
            if (error == ModelImports.Error.RollbackFailed) {
                getString(reason)
            } else {
                getString(R.string.smart_import_failed) + "\n" +
                    getString(R.string.smart_import_error, name, getString(reason))
            }
        } else buildString {
            append(resources.getQuantityString(R.plurals.smart_import_count, imported.verified, imported.verified))
            val ready = imported.ready.sortedBy { it.ordinal }.joinToString { getString(packLabel(it)) }
            if (ready.isNotEmpty()) {
                append('\n')
                append(getString(R.string.smart_import_ready, ready))
            }
            if (imported.activationFailed) {
                append('\n')
                append(getString(R.string.smart_import_activation_failed))
            } else if (ready.isEmpty() || imported.incomplete.isNotEmpty()) {
                append('\n')
                append(getString(R.string.smart_import_incomplete))
            }
        }
    }
