package com.subhashh.lumen.ui.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        GlassTextButton(backdrop, if (allSelected) "Deselect All" else "Select All", onToggleAll)
        GlassIconButton(backdrop, LumenIcons.Close, "Cancel selection", onCancel)
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
    /** Hidden section: Share, Unhide, Delete (permanent). */
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
                backdrop, LumenIcons.Share, "Share", guard(onShare),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.Unhide, "Unhide", guard(onUnhide),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.DeleteForever, "Delete permanently", guard(onDeleteForever),
                tint = if (hasSelection) Color(0xFFFF453A) else idle,
            )
        } else if (inTrash) {
            GlassIconButton(
                backdrop, LumenIcons.Restore, "Restore", guard(onRestore),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.DeleteForever, "Delete forever", guard(onDeleteForever),
                tint = if (hasSelection) Color(0xFFFF453A) else idle,
            )
        } else {
            GlassIconButton(
                backdrop, LumenIcons.Share, "Share", guard(onShare),
                tint = if (hasSelection) colors.content else idle,
            )
            if (showFavorite) GlassIconButton(
                backdrop,
                if (allFavorite) LumenIcons.HeartFilled else LumenIcons.Heart,
                if (allFavorite) "Unfavorite" else "Favorite",
                guard(onFavorite),
                tint = when {
                    !hasSelection -> idle
                    allFavorite -> Color(0xFFFF375F)
                    else -> colors.content
                },
            )
            GlassIconButton(
                backdrop, LumenIcons.Delete, "Delete", guard(onDelete),
                tint = if (hasSelection) colors.content else idle,
            )
            GlassIconButton(
                backdrop, LumenIcons.More, "More", guard(onMore),
                tint = if (hasSelection) colors.content else idle,
            )
        }
    }
}
