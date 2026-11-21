package com.subhashh.lumen.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.ui.components.pressable
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
import com.subhashh.lumen.ui.components.BareIconButton
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.materialSurface
import com.subhashh.lumen.ui.theme.Lumen
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
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
    /** How many are selected, for Material's bar ("2 Selected"). */
    title: String = "",
) {
    if (Lumen.material) {
        // Material: a bar across the top while selecting: close, how many, and Select All.
        val scheme = androidx.compose.material3.MaterialTheme.colorScheme
        Row(
            modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer)
                // Taps on the bar stay on the bar: there are photos under it.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(LumenIcons.Close, stringResource(R.string.main_cancel_selection), onCancel)
            Text(
                title,
                color = scheme.onSurface,
                fontSize = 22.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Text(
                if (allSelected) stringResource(R.string.main_deselect_all) else stringResource(R.string.common_select_all),
                color = scheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.pressable(onToggleAll, shape = CircleShape).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        return
    }
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
    val destructive = Lumen.colors.destructive
    // Ignore taps with nothing selected rather than hiding the bar, so it doesn't jump around.
    val guard = { action: () -> Unit -> { if (hasSelection) action() } }
    // Material 3 Expressive: one floating toolbar holding plain icon buttons, not a row of circles.
    val material = Lumen.material
    @Composable
    fun GlassIconButton(backdrop: Backdrop, icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color) {
        // Material: each action is its icon with its name under it.
        if (material) {
            Column(
                // Up to five across, on a narrow phone too.
                Modifier.width(minOf(84.dp, (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 16.dp) / 5)).pressable(onClick, shape = RoundedCornerShape(16.dp)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(Modifier.height(6.dp))
                Text(contentDescription, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            com.subhashh.lumen.ui.components.GlassIconButton(backdrop, icon, contentDescription, onClick, tint = tint)
        }
    }
    Row(
        // Material: a sheet docked to the bottom edge, its top corners rounded.
        modifier = if (material) {
            val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            modifier
                .fillMaxWidth()
                .materialSurface(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer, sheet)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        },
        horizontalArrangement = if (material) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
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
                tint = if (hasSelection) destructive else idle,
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
                tint = if (hasSelection) destructive else idle,
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
                    allFavorite -> Lumen.colors.favorite
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
