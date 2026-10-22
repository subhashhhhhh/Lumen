package com.subhashh.lumen.ui.components

import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.subhashh.lumen.ui.icons.LumenIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.ui.theme.LocalGlassColors

enum class LumenTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Library("Library", LumenIcons.Library, LumenIcons.LibraryFilled),
    Collections("Collections", LumenIcons.Collections, LumenIcons.CollectionsFilled),
}

/** iOS 26 Photos-style bar: a glass capsule of tabs, plus a separate glass search button. */
@Composable
fun GlassTabBar(
    backdrop: Backdrop,
    selected: LumenTab?,
    onSelect: (LumenTab) -> Unit,
    searchSelected: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while covered (e.g. by the viewer): stays composed so its glass shaders aren't rebuilt. */
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .glass(backdrop)
                .padding(4.dp),
        ) {
            SelectionPill(
                index = selected?.ordinal,
                modifier = Modifier.matchParentSize(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TabSpacing)) {
                LumenTab.entries.forEach { tab ->
                    TabItem(
                        enabled = enabled,
                        // Outline normally, filled when selected — like iOS.
                        icon = if (tab == selected) tab.selectedIcon else tab.icon,
                        label = tab.label,
                        selected = tab == selected,
                        onClick = {
                            if (tab != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(tab)
                        },
                    )
                }
            }
        }

        val colors = LocalGlassColors.current
        val searchTint by animateColorAsState(
            if (searchSelected) Color(0xFF0A84FF) else colors.content,
            label = "searchTint",
        )
        Column(
            modifier = Modifier
                .size(60.dp)
                .pressable(
                    onClick = {
                        if (!searchSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSearch()
                    },
                    enabled = enabled,
                )
                .glass(backdrop),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (searchSelected) LumenIcons.SearchFilled else LumenIcons.Search,
                contentDescription = "Search",
                tint = searchTint,
            )
        }
    }
}

@Composable
private fun TabItem(
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    val tint by animateColorAsState(
        if (selected) Color(0xFF0A84FF) else colors.content,
        label = "tabTint",
    )
    Column(
        modifier = Modifier
            .pressable(onClick, enabled, pressedScale = 0.9f)
            .width(TabWidth)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp)
    }
}

private val TabWidth = 88.dp
private val TabSpacing = 2.dp

/**
 * One highlight that slides between tabs, iOS 26-style, instead of each tab fading its own:
 * a spring carries it across and it stretches with its speed, like a droplet, settling back to
 * its shape as it arrives. Fades out when no tab is selected (search is open).
 */
@Composable
private fun SelectionPill(index: Int?, modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    val density = LocalDensity.current
    val step = with(density) { (TabWidth + TabSpacing).toPx() }
    val position = remember { Animatable((index ?: 0) * step) }
    val alpha by animateFloatAsState(if (index == null) 0f else 1f, label = "pillAlpha")
    LaunchedEffect(index, step) {
        if (index != null) position.animateTo(index * step, PillSpring)
    }
    Box(modifier) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = position.value
                    // Stretch along the direction of travel with speed, up to +30%.
                    val stretch = (abs(position.velocity) / 4000f).coerceAtMost(0.3f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.25f
                    this.alpha = alpha
                }
                .width(TabWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.selection),
        )
    }
}

private val PillSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)

