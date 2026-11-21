package com.subhashh.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.icons.LumenIcons

/**
 * The bar in Material 3 Expressive: a floating pill of tabs (the current one on a tonal pill with
 * its icon) and search as a round button beside it, together in the middle. It stays as it is
 * while the Library scrolls: this style has no Years or Months to switch to.
 */
@Composable
internal fun MaterialTabBar(
    selected: LumenTab?,
    onSelect: (LumenTab) -> Unit,
    searchSelected: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(BarHeight)
                .materialSurface(scheme.surfaceContainerHigh, CircleShape)
                .clip(CircleShape)
                .animateContentSize(expressiveSpatialDefault())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LumenTab.entries.forEach { tab ->
                val current = tab == selected
                TabPill(
                    icon = if (current) tab.selectedIcon else tab.icon,
                    // Material calls the first tab what Android galleries do.
                    label = stringResource(if (tab == LumenTab.Library) R.string.main_photos else tab.label),
                    selected = current,
                    enabled = enabled,
                    onClick = {
                        if (!current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(tab)
                    },
                )
            }
        }

        // Search: the screen's one floating action button, filled while search is open.
        val searchContainer by animateColorAsState(if (searchSelected) scheme.primary else scheme.primaryContainer, label = "searchContainer")
        val searchContent by animateColorAsState(if (searchSelected) scheme.onPrimary else scheme.onPrimaryContainer, label = "searchContent")
        MaterialMorphSurface(
            onClick = {
                if (!searchSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSearch()
            },
            color = searchContainer,
            size = BarHeight,
            pressedCorner = 20.dp,
            enabled = enabled,
        ) {
            Icon(
                if (searchSelected) LumenIcons.SearchFilled else LumenIcons.Search,
                contentDescription = stringResource(R.string.common_search),
                tint = searchContent,
            )
        }
    }
}

/** One tab: its name, on a tonal pill with its icon while it's the current one. */
@Composable
private fun TabPill(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.secondaryContainer.copy(alpha = 0f), label = "tabContainer")
    val content by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "tabContent")
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(expressiveSpatialDefault(), expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Row {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(label, color = content, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private val BarHeight = 64.dp
