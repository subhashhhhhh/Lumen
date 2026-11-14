package com.subhashh.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.ZoomLevel

/**
 * The bar in Material 3 Expressive: a floating toolbar of tabs (the current one a tonal pill with
 * its name, the others just icons) and search as a floating action button. As the Library scrolls
 * back in time the tabs fold into one button and Years / Months / All appears beside it as a
 * connected button group. Same behaviour as [GlassTabBar], which hands over to it.
 */
@Composable
internal fun MaterialTabBar(
    selected: LumenTab?,
    onSelect: (LumenTab) -> Unit,
    searchSelected: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    zoomLevel: ZoomLevel?,
    onZoomLevel: (ZoomLevel) -> Unit,
    showZoom: Boolean,
    onShowTabs: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val zoom = showZoom && zoomLevel != null
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Narrow phones: the folded tabs, the three segments and search must all fit.
        val segmentWidth = minOf(76.dp, (maxWidth - BarHeight - Gap - BarHeight - Gap - SegmentGap * 2) / 3)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .height(BarHeight)
                        .materialSurface(scheme.surfaceContainerHigh, CircleShape)
                        .clip(CircleShape)
                        .animateContentSize(expressiveSpatialDefault())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (zoom) {
                        // Folded: one button that brings the tabs (and the whole library) back.
                        TabPill(
                            icon = LumenIcons.LibraryFilled,
                            label = null,
                            description = stringResource(R.string.main_tabs),
                            selected = true,
                            enabled = enabled,
                            onClick = onShowTabs,
                        )
                    } else {
                        LumenTab.entries.forEach { tab ->
                            val current = tab == selected
                            TabPill(
                                icon = if (current) tab.selectedIcon else tab.icon,
                                label = stringResource(tab.label),
                                description = null,
                                selected = current,
                                enabled = enabled,
                                onClick = {
                                    if (!current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(tab)
                                },
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = zoom,
                    enter = fadeIn(expressiveSpatialDefault()) + expandHorizontally(expressiveSpatialDefault(), expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Row(
                        Modifier.padding(start = Gap),
                        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
                    ) {
                        ZoomLevel.entries.forEachIndexed { index, level ->
                            ZoomSegment(
                                label = stringResource(level.label),
                                width = segmentWidth,
                                selected = level == zoomLevel,
                                first = index == 0,
                                last = index == ZoomLevel.entries.lastIndex,
                                enabled = enabled && zoom,
                                onClick = {
                                    if (level != zoomLevel) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onZoomLevel(level)
                                },
                            )
                        }
                    }
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
                restCorner = 20.dp,
                pressedCorner = 28.dp,
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
}

/** One tab: an icon, and its name while it's the current one, on a tonal pill. */
@Composable
private fun TabPill(
    icon: ImageVector,
    label: String?,
    description: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
        Icon(icon, contentDescription = description ?: label.takeUnless { selected }, tint = content, modifier = Modifier.size(24.dp))
        if (label != null) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(expressiveSpatialDefault(), expandFrom = Alignment.Start),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

/**
 * One of Years / Months / All. Connected: square where it meets its neighbours, round at the
 * group's ends; the chosen one turns fully round and takes the primary colour.
 */
@Composable
private fun ZoomSegment(
    label: String,
    width: Dp,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val round = SegmentHeight / 2
    val inner = if (pressed) 4.dp else 8.dp
    val start by animateDpAsState(if (selected || first) round else inner, expressiveSpatial(), label = "segmentStart")
    val end by animateDpAsState(if (selected || last) round else inner, expressiveSpatial(), label = "segmentEnd")
    val container by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHigh, label = "segmentContainer")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "segmentContent")
    val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    Box(
        Modifier
            .width(width)
            .height(SegmentHeight)
            .materialSurface(container, shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private val BarHeight = 64.dp
private val SegmentHeight = 56.dp
private val Gap = 8.dp
private val SegmentGap = 2.dp
