package com.subhashh.lumen.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.theme.LocalGlassColors
import kotlin.math.abs

enum class LumenTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Library("Library", LumenIcons.Library, LumenIcons.LibraryFilled),
    Collections("Collections", LumenIcons.Collections, LumenIcons.CollectionsFilled),
}

/**
 * iOS 26 Photos-style bar: a glass capsule of tabs, plus a separate glass search button.
 *
 * In the Library ([zoomLevel] non-null) the tabs collapse into one circle and a Years / Months /
 * All control takes the middle, like iOS; tapping the circle expands the tabs again so Collections
 * stays one tap away.
 */
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
    zoomLevel: ZoomLevel? = null,
    onZoomLevel: (ZoomLevel) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember(selected) { mutableStateOf(false) }
    val showZoom = zoomLevel != null && !expanded

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = showZoom,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.9f))
                    .togetherWith(fadeOut(tween(120)))
                    .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.8f, stiffness = 500f) })
            },
            label = "tabBarMode",
        ) { zoomMode ->
            if (zoomMode && zoomLevel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The Library tab, collapsed to its icon.
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .pressable(onClick = { expanded = true }, enabled = enabled)
                            .glass(backdrop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LumenIcons.LibraryFilled, contentDescription = "Tabs", tint = Accent)
                    }
                    Box(
                        modifier = Modifier
                            .glass(backdrop)
                            .padding(4.dp),
                    ) {
                        SlidingPill(
                            index = zoomLevel.ordinal,
                            itemWidth = ZoomWidth,
                            modifier = Modifier.matchParentSize(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(TabSpacing)) {
                            ZoomLevel.entries.forEach { level ->
                                ZoomItem(
                                    label = level.label,
                                    selected = level == zoomLevel,
                                    enabled = enabled,
                                    onClick = {
                                        if (level != zoomLevel) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onZoomLevel(level)
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .glass(backdrop)
                        .padding(4.dp),
                ) {
                    SlidingPill(
                        index = selected?.ordinal,
                        itemWidth = TabWidth,
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
                                    // Re-tapping the Library tab while expanded folds it back.
                                    if (tab == selected && zoomLevel != null) expanded = false
                                    onSelect(tab)
                                },
                            )
                        }
                    }
                }
            }
        }

        val colors = LocalGlassColors.current
        val searchTint by animateColorAsState(
            if (searchSelected) Accent else colors.content,
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
        if (selected) Accent else colors.content,
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

@Composable
private fun ZoomItem(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    val tint by animateColorAsState(if (selected) colors.content else colors.contentSecondary, label = "zoomTint")
    Box(
        modifier = Modifier
            .pressable(onClick, enabled, pressedScale = 0.9f)
            .width(ZoomWidth)
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

private val Accent = Color(0xFF0A84FF)
private val TabWidth = 88.dp
private val ZoomWidth = 78.dp
private val TabSpacing = 2.dp

/**
 * One highlight that slides between items, iOS 26-style, instead of each item fading its own:
 * a spring carries it across and it stretches with its speed, like a droplet, settling back to
 * its shape as it arrives. Fades out when nothing is selected (search is open).
 */
@Composable
private fun SlidingPill(index: Int?, itemWidth: Dp, modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    val step = with(LocalDensity.current) { (itemWidth + TabSpacing).toPx() }
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
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.selection),
        )
    }
}

private val PillSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
