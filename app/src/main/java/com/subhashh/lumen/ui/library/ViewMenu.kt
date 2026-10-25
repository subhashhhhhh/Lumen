package com.subhashh.lumen.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.data.LibraryFilter
import com.subhashh.lumen.data.LibrarySort
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.LocalGlassColors

/**
 * The Library's ≡ menu: filter, sort and grid style. Grows out of the top-right button with a spring;
 * a tap anywhere outside, or on a choice, closes it. Drawn in-window (not a popup) so its glass
 * can refract the grid behind it.
 */
@Composable
fun ViewMenu(
    backdrop: Backdrop,
    open: Boolean,
    onDismiss: () -> Unit,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    aspectRatioGrid: Boolean,
    onAspectRatioGrid: (Boolean) -> Unit,
    sort: LibrarySort,
    descending: Boolean,
    onSort: (LibrarySort, descending: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (open) {
        // Catches the outside tap; invisible.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.5f, transformOrigin = TopEnd) +
            fadeIn(tween(120)),
        exit = scaleOut(tween(160), targetScale = 0.7f, transformOrigin = TopEnd) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        val colors = LocalGlassColors.current
        Column(
            Modifier
                .width(250.dp)
                .glass(
                    backdrop,
                    shape = RoundedCornerShape(22.dp),
                    blurRadius = 16.dp,
                    tint = colors.fallbackTint.copy(alpha = 0.82f),
                )
                .padding(vertical = 8.dp),
        ) {
            SectionLabel(stringResource(R.string.main_view_filter))
            LibraryFilter.entries.forEach { option ->
                MenuRow(stringResource(option.label), checked = option == filter) {
                    onFilter(option)
                    onDismiss()
                }
            }
            HorizontalDivider(
                color = colors.contentSecondary.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 6.dp),
            )
            SectionLabel(stringResource(R.string.main_view_sort))
            LibrarySort.entries.forEach { option ->
                // Picking a new sort starts in its natural direction; picking the current one keeps it.
                MenuRow(stringResource(option.label), checked = option == sort) {
                    onSort(option, if (option == sort) descending else option.defaultDescending)
                    onDismiss()
                }
            }
            MenuRow(stringResource(sort.ascendingLabel), checked = !descending) {
                onSort(sort, false)
                onDismiss()
            }
            MenuRow(stringResource(sort.descendingLabel), checked = descending) {
                onSort(sort, true)
                onDismiss()
            }
            HorizontalDivider(
                color = colors.contentSecondary.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 6.dp),
            )
            SectionLabel(stringResource(R.string.main_view_grid))
            MenuRow(stringResource(R.string.main_view_square), checked = !aspectRatioGrid) {
                onAspectRatioGrid(false)
                onDismiss()
            }
            MenuRow(stringResource(R.string.main_view_aspect_ratio), checked = aspectRatioGrid) {
                onAspectRatioGrid(true)
                onDismiss()
            }
        }
    }
}

private val TopEnd = TransformOrigin(1f, 0f)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalGlassColors.current.contentSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 44.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuRow(label: String, checked: Boolean, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp)) {
            if (checked) Icon(LumenIcons.Check, null, tint = colors.content, modifier = Modifier.size(18.dp))
        }
        Text(label, color = colors.content, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
    }
}
