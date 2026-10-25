package com.subhashh.lumen.ui.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.subhashh.lumen.R
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.GlassTextButton
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.LocalGlassColors

/** Top-right while selecting: Select All / Deselect All, and ✕ to leave selection. */
@Composable
fun SelectionTopControls(
    backdrop: Backdrop,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassTextButton(backdrop, if (allSelected) stringResource(R.string.main_deselect_all) else stringResource(R.string.common_select_all), onToggleAll)
        GlassIconButton(backdrop, LumenIcons.Close, stringResource(R.string.main_cancel_selection), onCancel)
    }
}

/**
 * Bottom bar while selecting, replacing the tab bar. Disabled-looking (no actions) while nothing
 * is selected. Recently Deleted gets Restore / Delete forever instead.
 */
@Composable
fun SelectionActionBar(
    backdrop: Backdrop,
    hasSelection: Boolean,
    inTrash: Boolean,
    /** Hidden section: Share, Unhide, Delete (permanent), More. */
    inHidden: Boolean,
    allFavorite: Boolean,
    /** False for whole albums. */
    showFavorite: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onUnhide: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val idle = colors.content.copy(alpha = 0.35f)
    // Ignore taps with nothing selected rather than hiding the bar, so it doesn't jump around.
    val guard = { action: () -> Unit -> { if (hasSelection) action() } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (inHidden) {
            GlassIconButton(
                backdrop, LumenIcons.Share, stringResource(R.string.common_share), guard(onShare),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.Unhide, stringResource(R.string.common_unhide), guard(onUnhide),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.DeleteForever, stringResource(R.string.main_delete_permanently), guard(onDeleteForever),
                tint = if (hasSelection) Color(0xFFFF453A) else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.More, stringResource(R.string.main_more), guard(onMore),
                tint = if (hasSelection) colors.content else idle,
            )
        } else if (inTrash) {
            GlassIconButton(
                backdrop, LumenIcons.Restore, stringResource(R.string.common_restore), guard(onRestore),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.DeleteForever, stringResource(R.string.main_delete_forever), guard(onDeleteForever),
                tint = if (hasSelection) Color(0xFFFF453A) else idle,
            )
        } else {
            GlassIconButton(
                backdrop, LumenIcons.Share, stringResource(R.string.common_share), guard(onShare),
                tint = if (hasSelection) colors.content else idle,
            )
            if (showFavorite) GlassIconButton(
                backdrop,
                if (allFavorite) LumenIcons.HeartFilled else LumenIcons.Heart,
                if (allFavorite) stringResource(R.string.common_unfavorite) else stringResource(R.string.common_favorite),
                guard(onFavorite),
                tint = when {
                    !hasSelection -> idle
                    allFavorite -> Color(0xFFFF375F)
                    else -> colors.content
                },
            )
            GlassIconButton(
                backdrop, LumenIcons.Delete, stringResource(R.string.common_delete), guard(onDelete),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.More, stringResource(R.string.main_more), guard(onMore),
                tint = if (hasSelection) colors.content else idle,
            )
        }
    }
}
