package com.subhashh.lumen.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.data.GridThumbnail
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import kotlin.math.roundToInt

internal data class GroupColors(val card: Color, val text: Color, val secondary: Color, val divider: Color)

@Composable
internal fun groupColors(): GroupColors = if (LocalDarkTheme.current) {
    GroupColors(Color(0xFF1C1C1E), Color.White, Color(0x99EBEBF5), Color(0x33EBEBF5))
} else {
    GroupColors(Color(0xFFF2F2F7), Color.Black, Color(0x993C3C43), Color(0x333C3C43))
}

/** What the dropdown pickers' glass refracts: the whole page, cards and all. */
private val LocalPickerHost = staticCompositionLocalOf<PickerHost?> { null }

/** Settings' card colour: one step up from the page, like iOS grouped lists. */
@Composable
private fun cardColor(): Color = if (LocalDarkTheme.current) Color(0xFF1C1C1E) else Color.White

/**
 * A Settings page: plain black (iOS light grey in light mode) with solid cards, like iOS
 * Settings. The liquid glass is saved for the controls: the toggles, and the dropdown pickers,
 * whose glass refracts this page, so the page is recorded as their backdrop.
 */
@Composable
internal fun SettingsPage(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    PickerArea(modifier.background(if (dark) Color.Black else Color(0xFFF2F2F7))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}

/**
 * Hosts [PickerRow]s' glass dropdowns: records [content] as the backdrop the dropdown glass
 * refracts, and draws the open dropdown above it. Settings pages and dialogs wrap themselves in one.
 */
@Composable
internal fun PickerArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val host = remember { PickerHost() }
    CompositionLocalProvider(LocalPickerHost provides host) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
            PickerOverlay(host, backdrop)
        }
    }
}

@Composable
internal fun LargeTitle(text: String) {
    Text(
        text,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = groupColors().text,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** A section: small caps header, a card, an optional footer. */
@Composable
internal fun Group(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = groupColors()
    Text(
        title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.secondary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor()),
        content = content,
    )
    if (footer != null) {
        Text(
            footer,
            fontSize = 13.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

/** A coloured rounded square with a white glyph, as in iOS Settings and Collections' utilities. */
@Composable
internal fun IconBadge(icon: ImageVector, color: Color) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** The shared row layout: badge, title (and subtitle), then whatever control goes on the right. */
@Composable
private fun RowShell(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    iconColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = groupColors()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.text)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = colors.secondary)
        }
        trailing()
    }
}

/** A row that opens something: a page, a system screen. Shows a value and a chevron. */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)?,
    icon: ImageVector? = null,
    iconColor: Color = Gray,
) {
    val colors = groupColors()
    RowShell(title, subtitle, icon, iconColor, onClick) {
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 16.sp, color = colors.secondary, maxLines = 1)
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                LumenIcons.Back,
                contentDescription = null,
                tint = colors.secondary,
                // The back chevron, turned around: there's no forward one in the icon set yet.
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f },
            )
        }
    }
}

/** An on/off setting with a liquid glass switch; the whole row toggles it. */
@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Gray,
) {
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val toggle = {
        haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        onCheckedChange(!checked)
    }
    RowShell(title, subtitle, icon, iconColor, onClick = toggle, interactionSource = press) {
        Spacer(Modifier.width(10.dp))
        LiquidSwitch(checked, pressed = press.collectIsPressedAsState().value)
    }
}

/**
 * iOS 26-style switch. At rest: a green (or grey) track and a white knob. While pressed, and as
 * it travels, the knob swells into a clear glass lens that bends the track under it, then turns
 * solid white again as it lands. Display only: its row handles the touch.
 */
@Composable
private fun LiquidSwitch(checked: Boolean, pressed: Boolean) {
    val dark = LocalDarkTheme.current
    val reduce = com.subhashh.lumen.ui.theme.LocalMotion.current.reduce
    val track by animateColorAsState(
        if (checked) Color(0xFF34C759) else if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA),
        tween(220),
        label = "switchTrack",
    )
    // 0 at rest, 1 fully glass. On while pressed and for a beat of the travel after release.
    var travelling by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(checked) {
        travelling = true
        kotlinx.coroutines.delay(260)
        travelling = false
    }
    val glass by androidx.compose.animation.core.animateFloatAsState(
        if (!reduce && (pressed || travelling)) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "switchGlass",
    )
    val knobX by animateDpAsState(
        if (checked) SwitchWidth - KnobSize - 2.dp else 2.dp,
        if (reduce) tween(0) else spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "switchKnob",
    )
    val trackBackdrop = rememberLayerBackdrop()
    val card = cardColor()
    val lensBackdrop = remember(trackBackdrop, card) { SwitchBackdrop(trackBackdrop, card) }
    Box(Modifier.size(SwitchWidth, SwitchHeight)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .background(track),
        )
        // The knob grows past the track while it's glass: wider and a little taller.
        val knobW = KnobSize + KnobStretch * glass
        val knobH = KnobSize + KnobGrow * glass
        Box(
            Modifier
                .offset(x = knobX - KnobStretch * glass / 2, y = (SwitchHeight - knobH) / 2)
                .size(knobW, knobH)
                .drawBackdrop(
                    backdrop = lensBackdrop,
                    shape = { CircleShape },
                    effects = {
                        if (glass > 0.01f) lens(10.dp.toPx() * glass, 16.dp.toPx() * glass, chromaticAberration = true)
                    },
                    highlight = { if (glass > 0.01f) com.kyant.backdrop.highlight.Highlight.Default.copy(alpha = glass) else null },
                    shadow = {
                        com.kyant.backdrop.shadow.Shadow(
                            radius = 4.dp, offset = androidx.compose.ui.unit.DpOffset(0.dp, 1.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                        )
                    },
                    // Solid white at rest, fading to clear as the glass comes on.
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - glass * 0.92f)) },
                ),
        )
    }
}

/** What the switch knob refracts: the card colour (past the track's ends), then the track. */
private class SwitchBackdrop(private val track: Backdrop, private val card: Color) : Backdrop {
    override val isCoordinatesDependent = true

    override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackdrop(
        density: androidx.compose.ui.unit.Density,
        coordinates: androidx.compose.ui.layout.LayoutCoordinates?,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)?,
    ) {
        drawRect(card, topLeft = androidx.compose.ui.geometry.Offset(-size.width, -size.height), size = androidx.compose.ui.geometry.Size(size.width * 3, size.height * 3))
        with(track) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

/**
 * A choice among a few options: shows the current one; tap opens a glass dropdown beside it
 * with a checkmark on the current choice.
 */
@Composable
internal fun PickerRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Gray,
) {
    val colors = groupColors()
    val host = LocalPickerHost.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    RowShell(
        title, subtitle, icon, iconColor,
        onClick = { host?.open(PickerRequest(bounds, options, selected, onSelect)) },
        modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
    ) {
        Spacer(Modifier.width(8.dp))
        Text(options.getOrElse(selected) { "" }, fontSize = 16.sp, color = colors.secondary, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        // ⌃⌄, like iOS pop-up buttons: the two chevrons stacked.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                LumenIcons.Back, null, tint = colors.secondary,
                modifier = Modifier.size(11.dp).graphicsLayer { rotationZ = 90f },
            )
            Icon(
                LumenIcons.Back, null, tint = colors.secondary,
                modifier = Modifier.size(11.dp).graphicsLayer { rotationZ = -90f },
            )
        }
    }
}

@Composable
internal fun Divider() {
    HorizontalDivider(color = groupColors().divider, modifier = Modifier.padding(start = 58.dp))
}

private data class PickerRequest(
    /** The row it opened from, in root coordinates. */
    val anchor: Rect,
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

@Stable
private class PickerHost {
    var request by mutableStateOf<PickerRequest?>(null)
        private set

    fun open(request: PickerRequest) {
        this.request = request
    }

    fun close() {
        request = null
    }
}

/** The open dropdown: a glass list that grows from the row's right edge, below it or, near the bottom, above. */
@Composable
private fun PickerOverlay(host: PickerHost, backdrop: Backdrop) {
    val request = host.request
    BackHandler(enabled = request != null) { host.close() }
    if (request != null) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null) { host.close() },
        )
    }
    var origin by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val colors = groupColors()
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot() }) {
        val shown = request ?: return@Box
        val width = with(density) { PickerWidth.toPx() }
        val itemHeight = with(density) { PickerItemHeight.toPx() }
        val height = itemHeight * shown.options.size + with(density) { 12.dp.toPx() }
        val below = shown.anchor.bottom + height < origin.bottom - with(density) { 24.dp.toPx() }
        val x = (shown.anchor.right - with(density) { 14.dp.toPx() } - width - origin.left)
        val y = if (below) shown.anchor.bottom - origin.top - with(density) { 6.dp.toPx() } else shown.anchor.top - origin.top - height
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 520f), initialScale = 0.6f,
                transformOrigin = TransformOrigin(1f, if (below) 0f else 1f)) + fadeIn(tween(120)),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        ) {
            Column(
                Modifier
                    .width(PickerWidth)
                    .glass(
                        backdrop,
                        shape = RoundedCornerShape(18.dp),
                        blurRadius = 10.dp,
                        refractionHeight = 18.dp,
                        refractionAmount = 36.dp,
                        tint = if (dark) Color(0x8C2C2C30) else Color(0xB3FFFFFF),
                    )
                    .padding(vertical = 6.dp),
            ) {
                shown.options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(PickerItemHeight)
                            .clickable {
                                host.close()
                                shown.onSelect(index)
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(26.dp)) {
                            if (index == shown.selected) {
                                Icon(LumenIcons.Check, null, tint = colors.text, modifier = Modifier.size(17.dp))
                            }
                        }
                        Text(option, fontSize = 16.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

internal val Gray = Color(0xFF8E8E93)
private val SwitchWidth = 51.dp
private val SwitchHeight = 31.dp
private val KnobSize = 27.dp
private val KnobStretch = 12.dp
private val KnobGrow = 6.dp
private val PickerWidth = 230.dp
private val PickerItemHeight = 44.dp
