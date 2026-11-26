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
import com.subhashh.lumen.data.Exclusions
import com.subhashh.lumen.data.FolderTree
import com.subhashh.lumen.ui.icons.LumenIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Settings → Excluded folders: what Lumen ignores (with a way back), subfolders shown anyway, code
 * projects it found, and every folder with photos to browse (#44: parents too, not just albums).
 * Nothing on disk changes.
 */
@Composable
fun ExcludedFoldersScreen(
    exclusions: Exclusions,
    tree: FolderTree,
    suggestions: List<ExclusionSuggestion>,
    contentPadding: PaddingValues,
    onExclude: (String) -> Unit,
    onInclude: (String) -> Unit,
    onShowAnyway: (String) -> Unit,
    onStopShowing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = groupColors()
    fun count(folder: String) = tree.counts[folder] ?: 0
    SettingsPage(contentPadding, modifier) {
        LargeTitle(stringResource(R.string.settings_excluded_folders))
        Text(
            stringResource(R.string.settings_excluded_intro),
            fontSize = 15.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )

        if (exclusions.excluded.isNotEmpty()) {
            Group(stringResource(R.string.settings_group_excluded)) {
                exclusions.excluded.sorted().forEachIndexed { i, folder ->
                    if (i > 0) Divider()
                    FolderRow(
                        folder,
                        pluralStringResource(R.plurals.settings_excluded_items, count(folder), count(folder)),
                        stringResource(R.string.settings_include),
                    ) { onInclude(folder) }
                }
            }
        }

        if (exclusions.shown.isNotEmpty()) {
            Group(stringResource(R.string.settings_group_shown)) {
                exclusions.shown.sorted().forEachIndexed { i, folder ->
                    if (i > 0) Divider()
                    FolderRow(
                        folder,
                        pluralStringResource(R.plurals.settings_excluded_items, count(folder), count(folder)),
                        stringResource(R.string.settings_hide),
                    ) { onStopShowing(folder) }
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

        // Browsing: "" is the top. Back goes up a folder before it leaves the page.
        var at by rememberSaveable { mutableStateOf("") }
        val parent = at.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        if (at.isNotEmpty()) BackHandler { at = parent }
        val allFolders = stringResource(R.string.settings_all_folders)
        Group(if (at.isEmpty()) allFolders else at.trimEnd('/')) {
            if (at.isNotEmpty()) {
                SettingsRow(
                    title = stringResource(R.string.settings_folder_up, if (parent.isEmpty()) allFolders else parent.trimEnd('/').substringAfterLast('/')),
                    onClick = { at = parent },
                    icon = LumenIcons.Back,
                )
            }
            tree.children(at).forEachIndexed { i, folder ->
                if (i > 0 || at.isNotEmpty()) Divider()
                val items = pluralStringResource(R.plurals.settings_excluded_items, count(folder), count(folder))
                val rule = exclusions.ruleOver(folder)
                when {
                    folder in exclusions.excluded -> BrowseRow(
                        folder, stringResource(R.string.settings_folder_excluded, items), stringResource(R.string.settings_include),
                        { onInclude(folder) }, tree.children(folder).isNotEmpty(),
                    ) { at = folder }
                    folder in exclusions.shown -> BrowseRow(
                        folder, stringResource(R.string.settings_folder_shown, items), stringResource(R.string.settings_hide),
                        { onStopShowing(folder) }, tree.children(folder).isNotEmpty(),
                    ) { at = folder }
                    rule != null -> BrowseRow(
                        folder, stringResource(R.string.settings_folder_excluded_with, items, rule.trimEnd('/').substringAfterLast('/')),
                        stringResource(R.string.settings_show_anyway), { onShowAnyway(folder) }, tree.children(folder).isNotEmpty(),
                    ) { at = folder }
                    else -> BrowseRow(
                        folder, items, stringResource(R.string.settings_exclude),
                        { onExclude(folder) }, tree.children(folder).isNotEmpty(),
                    ) { at = folder }
                }
            }
        }
    }
}

/** A folder to browse: tapping it opens it (when it has folders inside); the action at its end applies the rule. */
@Composable
private fun BrowseRow(folder: String, subtitle: String, action: String, onAction: () -> Unit, hasFolders: Boolean, onOpen: () -> Unit) {
    val colors = groupColors()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (hasFolders) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            if (hasFolders) LumenIcons.FolderOpen else LumenIcons.Folder,
            contentDescription = null,
            tint = colors.secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.trimEnd('/').substringAfterLast('/'), fontSize = 16.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            action,
            color = com.subhashh.lumen.ui.theme.Lumen.colors.accent,
            fontSize = 16.sp,
            fontWeight = com.subhashh.lumen.ui.theme.Lumen.type.emphasis,
            modifier = Modifier.clickable(onClick = onAction).padding(vertical = 6.dp),
        )
        if (hasFolders) {
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(
                LumenIcons.Chevron, null, tint = colors.secondary,
                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 180f },
            )
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
            color = com.subhashh.lumen.ui.theme.Lumen.colors.accent,
            fontSize = 16.sp,
            fontWeight = com.subhashh.lumen.ui.theme.Lumen.type.emphasis,
            modifier = Modifier.clickable(onClick = onAction),
        )
    }
}
