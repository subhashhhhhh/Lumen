package com.subhashh.lumen.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.theme.LocalGlassColors

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
                colors.content,
                label = "searchTint",
            )
            Column(
                modifier = Modifier
                    .size(Bar)
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
        colors.content,
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
    // Emerges as a circle centred under the tab circle, then slides out to its own spot.
    val zoomX = lerp((Bar - ZoomBar) / 2, Bar + Gap, p)
    val zoomCapsuleWidth = lerp(ZoomBar, zoomWidth, p)
    val width = if (zoomLevel != null && t > 0f) maxOf(capsuleWidth, zoomX + zoomCapsuleWidth) else capsuleWidth
    val shape = RoundedCornerShape(50)

    val density = LocalDensity.current
    val colors = LocalGlassColors.current
    val haptics = LocalHapticFeedback.current
    val inset = with(density) { Offset(PillInset.toPx(), PillInset.toPx()) }

    val tabPill = rememberLiquidPillState(selected?.ordinal ?: 0)
    LaunchedEffect(selected) { selected?.let { tabPill.moveTo(it.ordinal) } }
    // Fades out while nothing is selected (search is open).
    val tabPillVisible by animateFloatAsState(if (selected == null) 0f else 1f, label = "tabPillVisible")
    val tabGeometry = rememberPillGeometry(TabWidth, Bar)
    val tabBarGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()

    val zoomPill = rememberLiquidPillState(zoomLevel?.ordinal ?: ZoomLevel.All.ordinal)
    LaunchedEffect(zoomLevel) { zoomLevel?.let { zoomPill.moveTo(it.ordinal) } }
    val zoomGeometry = rememberPillGeometry(zoomItemWidth, ZoomBar)
    val zoomBarGlass = rememberLayerBackdrop()
    val zoomRow = rememberLayerBackdrop()

    Box(Modifier.width(width).height(Bar)) {
        // Behind: the zoom control, emerging from under the shrinking capsule.
        if (zoomLevel != null && t > 0.001f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = zoomX)
                    .width(zoomCapsuleWidth)
                    .height(ZoomBar)
                    .graphicsLayer { alpha = ((t - 0.05f) / 0.4f).coerceIn(0f, 1f) }
                    .glass(backdrop, shape = shape, exportedBackdrop = zoomBarGlass)
                    .clip(shape),
            ) {
                Box(
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(zoomWidth)
                        .fillMaxHeight()
                        .padding(PillInset)
                        .liquidPillDrag(
                            state = zoomPill,
                            count = ZoomLevel.entries.size,
                            stepPx = { zoomGeometry.stepPx },
                            enabled = { enabled && morph > 0.5f },
                            onCross = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                            onSelect = { onZoomLevel(ZoomLevel.entries[it]) },
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    LiquidPillFill(zoomPill, zoomGeometry, colors.selection, visible = { 1f }, Modifier.matchParentSize())
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TabSpacing),
                        modifier = Modifier.layerBackdrop(zoomRow),
                    ) {
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
                .glass(backdrop, shape = shape, exportedBackdrop = tabBarGlass)
                .clip(shape),
        ) {
            // Folded into the circle, the tabs are invisible; leaving them out of layout means
            // nothing in them can sit between a tap and the circle's "show the tabs" action.
            if (t < 0.5f) Box(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(tabsWidth)
                    .fillMaxHeight()
                    .padding(PillInset)
                    .graphicsLayer { alpha = (1f - t * 2f).coerceIn(0f, 1f) }
                    .liquidPillDrag(
                        state = tabPill,
                        count = LumenTab.entries.size,
                        stepPx = { tabGeometry.stepPx },
                        enabled = { enabled && selected != null && morph < 0.5f },
                        onCross = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                        onSelect = { onSelect(LumenTab.entries[it]) },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                LiquidPillFill(tabPill, tabGeometry, colors.selection, visible = { tabPillVisible }, Modifier.matchParentSize())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TabSpacing),
                    modifier = Modifier.layerBackdrop(tabRow),
                ) {
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
                tint = colors.content,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = ((t - 0.5f) * 2f).coerceIn(0f, 1f) },
            )
        }

        // Lifted lenses, above both capsules and outside their clips. Only mid-switch, and only
        // for the control that's fully out.
        if (t < 0.01f) {
            LiquidPillLens(
                tabPill, tabGeometry, page = backdrop, bar = tabBarGlass, items = tabRow, fillColor = colors.selection,
                offset = inset, modifier = Modifier.matchParentSize(),
            )
        } else if (zoomLevel != null && t > 0.99f) {
            LiquidPillLens(
                zoomPill, zoomGeometry, page = backdrop, bar = zoomBarGlass, items = zoomRow, fillColor = colors.selection,
                offset = inset + with(density) { Offset(zoomX.toPx(), ((Bar - ZoomBar) / 2).toPx()) },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A pill one [itemWidth] item wide resting inside a [barHeight] bar, lifting [PillLift] taller than it. */
@Composable
private fun rememberPillGeometry(itemWidth: Dp, barHeight: Dp): LiquidPillGeometry {
    val density = LocalDensity.current
    return remember(itemWidth, barHeight, density) {
        with(density) {
            val rest = Size(itemWidth.toPx(), (barHeight - PillInset * 2).toPx())
            val liftedHeight = (barHeight + PillLift).toPx()
            val step = (itemWidth + TabSpacing).toPx()
            LiquidPillGeometry(
                restSize = rest,
                liftedSize = Size(rest.width * liftedHeight / rest.height, liftedHeight),
                center = { position -> Offset(position * step + rest.width / 2f, rest.height / 2f) },
                stepPx = step,
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

private val TabWidth = 88.dp
private val ZoomWidth = 78.dp
/** Bar height, and the circle's diameter. */
private val Bar = 66.dp
/** Years / Months / All: a little shorter than the tabs, centred beside the circle. */
private val ZoomBar = 60.dp
private val Gap = 12.dp
private val TabSpacing = 2.dp
/** Gap between the capsule's edge and the selection pill. */
private val PillInset = 4.dp
/** How much taller than the bar the pill stands while lifted. */
private val PillLift = 12.dp
