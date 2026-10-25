package com.subhashh.lumen.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.ExclusionSuggestion

/**
 * Settings → Excluded folders: what Lumen ignores (with a way back), code projects it found, and
 * a picker for anything else. Nothing on disk changes.
 */
@Composable
fun ExcludedFoldersScreen(
    excluded: List<Pair<String, Int>>,
    suggestions: List<ExclusionSuggestion>,
    contentPadding: PaddingValues,
    onInclude: (String) -> Unit,
    onExclude: (String) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = groupColors()
    SettingsPage(contentPadding, modifier) {
        LargeTitle(stringResource(R.string.settings_excluded_folders))
        Text(
            stringResource(R.string.settings_excluded_intro),
            fontSize = 15.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )

        if (excluded.isNotEmpty()) {
            Group(stringResource(R.string.settings_group_excluded)) {
                excluded.forEachIndexed { i, (folder, count) ->
                    if (i > 0) Divider()
                    FolderRow(
                        folder,
                        pluralStringResource(R.plurals.settings_excluded_items, count, count),
                        stringResource(R.string.settings_include),
                    ) { onInclude(folder) }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Group(stringResource(R.string.settings_group_suggested), footer = stringResource(R.string.settings_suggested_footer)) {
                suggestions.forEachIndexed { i, suggestion ->
                    if (i > 0) Divider()
                    FolderRow(
                        suggestion.folder,
                        pluralStringResource(R.plurals.settings_code_images, suggestion.itemCount, suggestion.itemCount),
                        stringResource(R.string.settings_exclude),
                    ) {
                        onExclude(suggestion.folder)
                    }
                }
            }
        }

        Group(stringResource(R.string.common_add)) {
            SettingsRow(title = stringResource(R.string.settings_choose_folder), onClick = onChooseFolder)
        }
    }
}

@Composable
private fun FolderRow(folder: String, subtitle: String, action: String, onAction: () -> Unit) {
    val colors = groupColors()
    val name = folder.trimEnd('/').substringAfterLast('/')
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.settings_folder_subtitle, folder.trimEnd('/'), subtitle),
                fontSize = 13.sp,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            action,
            color = Color(0xFF0A84FF),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onAction),
        )
    }
}
