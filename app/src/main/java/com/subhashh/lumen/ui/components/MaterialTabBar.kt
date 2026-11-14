package com.subhashh.lumen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.subhashh.lumen.ui.theme.LocalMotion

/**
 * Material 3 Expressive's navigation bar, docked across the bottom: Library, Collections and
 * Search as three destinations (search is no separate button here). The current one's icon sits
 * on a tonal pill that stretches out from nothing, and the icon gives a little jump as it's chosen.
 */
@Composable
internal fun MaterialNavBar(
    selected: LumenTab?,
    onSelect: (LumenTab) -> Unit,
    searchSelected: Boolean,
    onSearch: () -> Unit,
    /** The gesture or button bar's height: the bar's surface runs under it. */
    navBottom: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // Swallows taps between the items: there are photos under the bar.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(bottom = navBottom)
            .height(MaterialNavBarHeight),
    ) {
        LumenTab.entries.forEach { tab ->
            val current = tab == selected && !searchSelected
            NavItem(
                icon = if (current) tab.selectedIcon else tab.icon,
                label = stringResource(tab.label),
                selected = current,
                enabled = enabled,
                onClick = {
                    if (!current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab)
                },
                modifier = Modifier.weight(1f),
            )
        }
        NavItem(
            icon = if (searchSelected) LumenIcons.SearchFilled else LumenIcons.Search,
            label = stringResource(R.string.common_search),
            selected = searchSelected,
            enabled = enabled,
            onClick = {
                if (!searchSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSearch()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalMotion.current.reduce
    val pillWidth by animateDpAsState(if (selected) 64.dp else 24.dp, expressiveSpatialDefault(), label = "navPill")
    val pill by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.secondaryContainer.copy(alpha = 0f), label = "navPillColor")
    val content by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "navContent")
    val label_ by animateColorAsState(if (selected) scheme.onSurface else scheme.onSurfaceVariant, label = "navLabel")
    // The jump: up and a touch bigger, then settling with a wobble.
    val jump = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected && !reduce) {
            jump.snapTo(1f)
            jump.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f))
        }
    }
    Column(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(64.dp).height(32.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(pillWidth).height(32.dp).clip(CircleShape).background(pill))
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp).graphicsLayer {
                    val j = jump.value
                    translationY = -5.dp.toPx() * j
                    scaleX = 1f + 0.22f * j
                    scaleY = 1f + 0.22f * j
                    rotationZ = -10f * j
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = label_, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Years / Months / All as a connected button group: square where the buttons meet, round at the
 * group's ends; the chosen one turns fully round and takes the primary colour.
 */
@Composable
internal fun MaterialZoomSwitch(
    level: ZoomLevel,
    onLevel: (ZoomLevel) -> Unit,
    modifier: Modifier = Modifier,
    /** On a bar (flat) or floating over photos (with a shadow). */
    floating: Boolean = false,
    segmentWidth: Dp = 88.dp,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ZoomLevel.entries.forEachIndexed { index, each ->
            ZoomSegment(
                label = stringResource(each.label),
                width = segmentWidth,
                selected = each == level,
                first = index == 0,
                last = index == ZoomLevel.entries.lastIndex,
                floating = floating,
                onClick = {
                    if (each != level) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onLevel(each)
                },
            )
        }
    }
}

@Composable
private fun ZoomSegment(
    label: String,
    width: Dp,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    floating: Boolean,
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
            .then(if (floating) Modifier.materialSurface(container, shape) else Modifier.background(container, shape))
            .clip(shape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

val MaterialNavBarHeight = 76.dp
private val SegmentHeight = 40.dp
