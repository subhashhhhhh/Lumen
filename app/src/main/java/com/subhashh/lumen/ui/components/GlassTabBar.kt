package com.subhashh.lumen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * When [zoomLevel] is non-null and [showZoom] is set (the caller collapses it as the Library
 * scrolls back in time), the tabs fold into one circle and a Years / Months / All control takes
 * their place, like iOS; tapping the circle calls [onShowTabs]. Tabs sit at the far left and
 * search at the far right.
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
    showZoom: Boolean = false,
    onShowTabs: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current

    // One value drives the whole morph: 0 = tabs, 1 = circle + Years / Months / All.
    val morph by animateFloatAsState(
        targetValue = if (showZoom && zoomLevel != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 220f),
        label = "tabMorph",
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Narrow phones: shrink the zoom segments so circle + control + search always fit.
        val zoomItemWidth = minOf(ZoomWidth, (maxWidth - Bar - Gap - Bar - Gap - 12.dp) / 3)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphingTabs(
                backdrop = backdrop,
                morph = morph,
                zoomLevel = zoomLevel,
                selected = selected,
                enabled = enabled,
                zoomItemWidth = zoomItemWidth,
                onSelect = { tab ->
                    if (tab != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab)
                },
                onShowTabs = onShowTabs,
                onZoomLevel = { level ->
                    if (level != zoomLevel) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onZoomLevel(level)
                },
            )

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
    // Centered in the capsule's fixed height (not padded), so the label never clips, even with
    // a large system font.
    Column(
        modifier = Modifier
            .pressable(onClick, enabled, pressedScale = 0.9f)
            .width(TabWidth)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp)
    }
}

/**
 * The left side of the bar, morphing with [morph] (0..1): the two-tab capsule shrinks into a
 * circle holding the Library icon while the Years / Months / All capsule slides out from under
 * it, growing and fading in. Both are glass, so mid-morph they read as one droplet splitting.
 */
@Composable
private fun MorphingTabs(
    backdrop: Backdrop,
    morph: Float,
    zoomLevel: ZoomLevel?,
    selected: LumenTab?,
    enabled: Boolean,
    zoomItemWidth: Dp,
    onSelect: (LumenTab) -> Unit,
    onShowTabs: () -> Unit,
    onZoomLevel: (ZoomLevel) -> Unit,
) {
    val p = morph.coerceIn(0f, 1.05f)
    val t = morph.coerceIn(0f, 1f)
    val tabsWidth = TabWidth * LumenTab.entries.size + TabSpacing * (LumenTab.entries.size - 1) + 8.dp
    val zoomWidth = zoomItemWidth * ZoomLevel.entries.size + TabSpacing * (ZoomLevel.entries.size - 1) + 8.dp
    val capsuleWidth = lerp(tabsWidth, Bar, t)
    val zoomX = lerp(0.dp, Bar + Gap, p)
    val zoomCapsuleWidth = lerp(Bar, zoomWidth, p)
    val width = if (zoomLevel != null && t > 0f) maxOf(capsuleWidth, zoomX + zoomCapsuleWidth) else capsuleWidth
    val shape = RoundedCornerShape(50)

    Box(Modifier.width(width).height(Bar)) {
        // Behind: the zoom control, emerging from under the shrinking capsule.
        if (zoomLevel != null && t > 0.001f) {
            Box(
                Modifier
                    .offset(x = zoomX)
                    .width(zoomCapsuleWidth)
                    .height(Bar)
                    .graphicsLayer { alpha = ((t - 0.05f) / 0.4f).coerceIn(0f, 1f) }
                    .glass(backdrop, shape = shape)
                    .clip(shape),
            ) {
                Box(
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(zoomWidth)
                        .fillMaxHeight()
                        .padding(4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SlidingPill(index = zoomLevel.ordinal, itemWidth = zoomItemWidth, modifier = Modifier.matchParentSize())
                    Row(horizontalArrangement = Arrangement.spacedBy(TabSpacing)) {
                        ZoomLevel.entries.forEach { level ->
                            ZoomItem(
                                label = level.label,
                                width = zoomItemWidth,
                                selected = level == zoomLevel,
                                enabled = enabled && t > 0.5f,
                                onClick = { onZoomLevel(level) },
                            )
                        }
                    }
                }
            }
        }

        // In front: the tab capsule, becoming the circle.
        Box(
            Modifier
                .width(capsuleWidth)
                .height(Bar)
                .pressable(onClick = onShowTabs, enabled = enabled && t > 0.5f, pressedScale = 0.92f)
                .glass(backdrop, shape = shape)
                .clip(shape),
        ) {
            Box(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(tabsWidth)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .graphicsLayer { alpha = (1f - t * 2f).coerceIn(0f, 1f) },
                contentAlignment = Alignment.CenterStart,
            ) {
                SlidingPill(index = selected?.ordinal, itemWidth = TabWidth, modifier = Modifier.matchParentSize())
                Row(horizontalArrangement = Arrangement.spacedBy(TabSpacing)) {
                    LumenTab.entries.forEach { tab ->
                        TabItem(
                            enabled = enabled && t < 0.5f,
                            // Outline normally, filled when selected — like iOS.
                            icon = if (tab == selected) tab.selectedIcon else tab.icon,
                            label = tab.label,
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                        )
                    }
                }
            }
            Icon(
                LumenIcons.LibraryFilled,
                contentDescription = "Tabs",
                tint = Accent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = ((t - 0.5f) * 2f).coerceIn(0f, 1f) },
            )
        }
    }
}

@Composable
private fun ZoomItem(label: String, width: Dp, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    val tint by animateColorAsState(if (selected) colors.content else colors.contentSecondary, label = "zoomTint")
    Box(
        modifier = Modifier
            .pressable(onClick, enabled, pressedScale = 0.9f)
            .width(width)
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

private val Accent = Color(0xFF0A84FF)
private val TabWidth = 88.dp
private val ZoomWidth = 78.dp
/** Bar height, and the circle's diameter. */
private val Bar = 60.dp
private val Gap = 12.dp
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
