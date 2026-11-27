package com.subhashh.lumen.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.ModelImports
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlinx.coroutines.launch

private object OpenModelDocuments : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}

@Composable
internal fun OfflineModelImportRow(
    importFiles: suspend (Context, List<Uri>) -> ModelImports.Result? = ModelImports::importFiles,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ModelImports.Result?>(null) }
    val picker = rememberLauncherForActivityResult(OpenModelDocuments) { uris ->
        if (uris.isNotEmpty() && !importing) {
            importing = true
            scope.launch {
                try {
                    val imported = importFiles(context, uris)
                    if (imported == null) {
                        Toast.makeText(context, R.string.smart_import_busy, Toast.LENGTH_LONG).show()
                    } else {
                        result = imported
                    }
                } finally {
                    importing = false
                }
            }
        }
    }
    val title = stringResource(R.string.smart_import_files)
    ActionRow(
        title = title,
        subtitle = stringResource(if (importing) R.string.smart_import_working else R.string.smart_import_help),
        action = LumenIcons.Import,
        actionLabel = title,
        onAction = { if (!importing) picker.launch(arrayOf("*/*")) },
        icon = LumenIcons.Import,
        iconColor = Blue,
    )
    result?.let { imported ->
        val message = buildString {
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
                    append(context.getString(reason))
                } else {
                    appendLine(context.getString(R.string.smart_import_failed))
                    append(context.getString(R.string.smart_import_error, name, context.getString(reason)))
                }
            } else {
                appendLine(context.resources.getQuantityString(R.plurals.smart_import_count, imported.verified, imported.verified))
                val ready = imported.ready.sortedBy { it.ordinal }.joinToString { context.getString(packLabel(it)) }
                val status = if (ready.isEmpty()) context.getString(R.string.smart_import_incomplete)
                    else context.getString(R.string.smart_import_ready, ready)
                append(status)
            }
        }
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(stringResource(R.string.smart_import_result)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { result = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
