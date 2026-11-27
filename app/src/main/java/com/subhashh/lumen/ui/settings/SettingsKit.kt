package com.subhashh.lumen.ui.settings

import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import com.subhashh.lumen.R
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
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
import com.subhashh.lumen.ui.components.dialogCard
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import com.subhashh.lumen.ui.theme.Lumen
import kotlin.math.roundToInt

internal data class GroupColors(val card: Color, val text: Color, val secondary: Color, val divider: Color)

/** The current style's colours for a grouped list (see [Lumen.colors]). */
@Composable
internal fun groupColors(): GroupColors = Lumen.colors.let { GroupColors(it.groupOnPage, it.text, it.secondary, it.divider) }

/** Whether rows on this page show their icon on a coloured badge (Material: the main page only). */
val LocalSettingsBadges = staticCompositionLocalOf { true }

/** This Settings page's scroll position, held outside the page so it outlives it; null to keep its own. */
val LocalSettingsScroll = staticCompositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

/** What the dropdown pickers' glass refracts: the whole page, cards and all. */
private val LocalPickerHost = staticCompositionLocalOf<PickerHost?> { null }

/** Settings' card colour: one step up from the page, like iOS grouped lists. */
@Composable
internal fun cardColor(): Color = Lumen.colors.card

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
    PickerArea(modifier.background(Lumen.colors.groupedPage)) {
        Column(
            Modifier
                .fillMaxSize()
                // Kept by the main screen when it's there to keep it: a page comes back where it was left.
                .verticalScroll(LocalSettingsScroll.current ?: rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A readable width in a wide window (tablet, desktop mode), centred.
            Column(Modifier.widthIn(max = com.subhashh.lumen.ui.components.ReadableWidth).fillMaxWidth(), content = content)
        }
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
        style = Lumen.type.screenTitle,
        color = groupColors().text,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * A section: small caps header, a card. [footer] explains the section: behind an info button at the
 * header's right, open on tap (always shown for an untitled group). [note] stays under the card:
 * for warnings that shouldn't be missed.
 */
@Composable
internal fun Group(title: String, footer: String? = null, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = groupColors()
    val material = Lumen.material
    var info by rememberSaveable(title) { mutableStateOf(false) }
    if (title.isBlank()) {
        // Untitled groups (Settings' main list) are just separated, like iOS: no header line.
        Spacer(Modifier.height(18.dp))
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Material: the section's name as a label in the accent colour, in its own case.
            Text(
                if (material) title else title.uppercase(),
                fontSize = if (material) 14.sp else 13.sp,
                fontWeight = if (material) FontWeight.Medium else FontWeight.SemiBold,
                color = if (material) Lumen.colors.accent else colors.secondary,
                modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp),
            )
            if (footer != null) {
                val label = stringResource(R.string.settings_group_info, title)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = label) { info = !info }
                        .semantics { contentDescription = label; stateDescription = if (info) "open" else "closed" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        LumenIcons.Info,
                        contentDescription = null,
                        tint = if (info) colors.text else colors.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        AnimatedVisibility(visible = info && footer != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                footer.orEmpty(),
                fontSize = 13.sp,
                color = colors.secondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Lumen.shapes.card)
            .background(cardColor()),
        content = content,
    )
    val under = listOfNotNull(footer.takeIf { title.isBlank() }, note).joinToString("\n\n").takeIf { it.isNotEmpty() }
    if (under != null) {
        Text(
            under,
            fontSize = 13.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

/** A coloured rounded square with a white glyph, as in iOS Settings and Collections' utilities. */
@Composable
internal fun IconBadge(icon: ImageVector, color: Color) {
    if (Lumen.material) {
        // A tonal circle: the badge's colour picks which of the scheme's containers it takes.
        val (container, content) = badgeTones(color)
        Box(
            Modifier
                .size(40.dp)
                .clip(Lumen.shapes.badge)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        return
    }
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

/**
 * Material's stand-in for an iOS badge colour: cool colours take the primary container, warm
 * ones the tertiary, red the error container and grey the neutral one, so rows still differ
 * while every colour comes from the scheme (the wallpaper's, with Wallpaper Colours on).
 */
@Composable
internal fun badgeTones(color: Color): Pair<Color, Color> {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return when {
        max - min < 0.12f -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        color.red == max && color.green < 0.35f && color.blue < 0.35f -> scheme.errorContainer to scheme.onErrorContainer
        color.blue == max -> scheme.primaryContainer to scheme.onPrimaryContainer
        color.green == max -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
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
    /** Drawn instead of [icon]: a picture (a folder's cover), say. */
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = groupColors()
    val material = Lumen.material
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (material) 64.dp else 54.dp)
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
            .padding(horizontal = if (material) 16.dp else 14.dp, vertical = if (material) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(if (material) 16.dp else 14.dp))
        } else if (icon != null && material && !LocalSettingsBadges.current) {
            // Inside a category: the icon alone, as Android's settings lists have it.
            Icon(icon, contentDescription = null, tint = colors.secondary, modifier = Modifier.padding(horizontal = 4.dp).size(24.dp))
            Spacer(Modifier.width(20.dp))
        } else if (icon != null) {
            IconBadge(icon, iconColor)
            Spacer(Modifier.width(if (material) 16.dp else 14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.text)
            if (subtitle != null) Text(subtitle, fontSize = if (material) 14.sp else 13.sp, lineHeight = if (material) 20.sp else androidx.compose.ui.unit.TextUnit.Unspecified, color = colors.secondary)
        }
        trailing()
    }
}

/**
 * A whole number typed in, from [min] to [max], with [unit] after it. A number above [max] is
 * taken as [max]; leaving the field empty puts the last number back.
 */
@Composable
internal fun NumberRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    unit: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Gray,
) {
    val colors = groupColors()
    var text by remember(value) { mutableStateOf(value.toString()) }
    RowShell(title, subtitle, icon, iconColor, onClick = null) {
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { typed ->
                val digits = typed.filter(Char::isDigit).take(max.toString().length)
                val number = digits.toIntOrNull()
                text = if (number != null && number > max) max.toString() else digits
                number?.coerceAtMost(max)?.takeIf { it >= min }?.let(onValueChange)
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Lumen.colors.accent),
            modifier = Modifier
                // Room for the largest number allowed (up to 60,000).
                .width((24 + 10 * max.toString().length).dp)
                .clip(if (Lumen.material) RoundedCornerShape(12.dp) else RoundedCornerShape(8.dp))
                .background(Lumen.colors.field)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .onFocusChanged { if (!it.isFocused && (text.toIntOrNull() ?: 0) < min) text = value.toString() },
        )
        Spacer(Modifier.width(6.dp))
        Text(unit, fontSize = 16.sp, color = colors.secondary)
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
    /** A small mark at the row's end (Backup's lock), before the chevron. */
    badge: (@Composable () -> Unit)? = null,
) {
    val colors = groupColors()
    if (Lumen.material && onClick != null) {
        // Material: what it's set to reads as the row's second line, not at its far end. A row
        // that only shows something (onClick null) keeps its value at the end, so it doesn't look
        // like one that opens a choice.
        RowShell(title, listOfNotNull(subtitle, value).joinToString("\n").ifEmpty { null }, icon, iconColor, onClick) { badge?.invoke() }
        return
    }
    RowShell(title, subtitle, icon, iconColor, onClick) {
        badge?.invoke()
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 16.sp, color = colors.secondary, maxLines = 1)
        }
        // Android's lists don't point onward: no chevron in the Material style.
        if (onClick != null && !Lumen.material) {
            Spacer(Modifier.width(6.dp))
            Icon(
                LumenIcons.Chevron,
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

/**
 * A row with one action as a round icon button at its end (remove, download, cancel), so it reads
 * as something to tap; the whole row does it too.
 */
@Composable
internal fun ActionRow(
    title: String,
    action: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    iconColor: Color = Gray,
) {
    val colors = groupColors()
    val material = Lumen.material
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    RowShell(title, subtitle, icon, iconColor, onClick = onAction) {
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (material) scheme.surfaceContainerHighest else colors.secondary.copy(alpha = 0.16f))
                .semantics { contentDescription = actionLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action,
                contentDescription = null,
                tint = when {
                    destructive && material -> scheme.error
                    destructive -> Color(0xFFFF453A)
                    else -> colors.text
                },
                modifier = Modifier.size(20.dp),
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
    /** Drawn instead of [icon]. */
    leading: (@Composable () -> Unit)? = null,
    /** Its own button just before the switch (Backup's photo folders, say). */
    accessory: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val toggle = {
        haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        onCheckedChange(!checked)
    }
    RowShell(title, subtitle, icon, iconColor, onClick = toggle, interactionSource = press, leading = leading) {
        accessory?.invoke()
        Spacer(Modifier.width(10.dp))
        if (Lumen.material) MaterialSwitch(checked, press)
        else LiquidSwitch(checked, pressed = press.collectIsPressedAsState().value)
    }
}

/** Material's switch, with a tick in the thumb while it's on. Display only: its row handles the touch. */
@Composable
private fun MaterialSwitch(checked: Boolean, interaction: MutableInteractionSource) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = null,
        interactionSource = interaction,
        thumbContent = if (checked) {
            { Icon(LumenIcons.Check, contentDescription = null, modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/**
 * iOS 26-style switch. At rest: a green (or grey) track and a white knob. While pressed, and as
 * it travels, the knob swells into a clear glass lens that bends the track under it, then turns
 * solid white again as it lands. Display only: its row handles the touch.
 */
@Composable
private fun LiquidSwitch(checked: Boolean, pressed: Boolean) {
    val dark = LocalDarkTheme.current
    // Settings → Appearance: the knob only bends the track behind it with Liquid Glass on.
    val liquid = com.subhashh.lumen.ui.theme.LocalGlass.current.lens
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
        // Liquid Glass off: a plain switch, its knob never turning to glass.
        if (!reduce && liquid && (pressed || travelling)) 1f else 0f,
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
    /** Options shown faded that can't be chosen here; tapping one calls [onUnavailable]. */
    unavailable: Set<Int> = emptySet(),
    onUnavailable: (Int) -> Unit = {},
) {
    val colors = groupColors()
    val host = LocalPickerHost.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    if (Lumen.material) {
        // Material: the choice is the row's second line, and tapping opens a list to choose from.
        RowShell(
            title, listOfNotNull(options.getOrNull(selected), subtitle).joinToString("\n").ifEmpty { null }, icon, iconColor,
            onClick = { host?.open(PickerRequest(bounds, options, selected, onSelect, unavailable, onUnavailable, title)) },
        ) {
            // A drop-down arrow: this row opens a choice (rows that only show something have none).
            Spacer(Modifier.width(8.dp))
            Icon(
                LumenIcons.Chevron, null, tint = colors.secondary,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -90f },
            )
        }
        return
    }
    RowShell(
        title, subtitle, icon, iconColor,
        onClick = { host?.open(PickerRequest(bounds, options, selected, onSelect, unavailable, onUnavailable)) },
        modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
    ) {
        Spacer(Modifier.width(8.dp))
        Text(options.getOrElse(selected) { "" }, fontSize = 16.sp, color = colors.secondary, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        // ⌃⌄, like iOS pop-up buttons: the two chevrons stacked.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                LumenIcons.Chevron, null, tint = colors.secondary,
                modifier = Modifier.size(11.dp).graphicsLayer { rotationZ = 90f },
            )
            Icon(
                LumenIcons.Chevron, null, tint = colors.secondary,
                modifier = Modifier.size(11.dp).graphicsLayer { rotationZ = -90f },
            )
        }
    }
}

/**
 * Between two rows of a [Group]. Lumen: a hairline starting after the icon. Material: a gap
 * showing the page through, so the group reads as separate segments stacked together.
 */
@Composable
internal fun Divider() {
    if (Lumen.material) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Lumen.colors.groupedPage))
        return
    }
    HorizontalDivider(color = groupColors().divider, modifier = Modifier.padding(start = 58.dp))
}

private data class PickerRequest(
    /** The row it opened from, in root coordinates. */
    val anchor: Rect,
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
    val unavailable: Set<Int>,
    val onUnavailable: (Int) -> Unit,
    /** What's being chosen, for Material's dialog. */
    val title: String = "",
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
    // Added when it opens, so it's the newest handler and Back closes it first (#24).
    if (request != null) BackHandler { host.close() }
    if (request != null && !Lumen.material) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null) { host.close() },
        )
    }
    if (Lumen.material) {
        MaterialPickerDialog(host, request)
        return
    }
    var origin by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val colors = groupColors()
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot() }) {
        val shown = request ?: return@Box
        val width = with(density) { PickerWidth.toPx() }
        val itemHeight = with(density) { PickerItemHeight.toPx() }
        val height = (if (Lumen.material) with(density) { 48.dp.toPx() } else itemHeight) * shown.options.size + with(density) { (if (Lumen.material) 16.dp else 12.dp).toPx() }
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
                        shape = if (Lumen.material) Lumen.shapes.menu else RoundedCornerShape(18.dp),
                        blurRadius = 10.dp,
                        refractionHeight = 18.dp,
                        refractionAmount = 36.dp,
                        tint = if (dark) Color(0x8C2C2C30) else Color(0xB3FFFFFF),
                    )
                    .padding(vertical = if (Lumen.material) 8.dp else 6.dp),
            ) {
                val material = Lumen.material
                val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                shown.options.forEachIndexed { index, option ->
                    val chosen = material && index == shown.selected
                    Row(
                        Modifier
                            .then(
                                if (material) {
                                    Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(if (chosen) Modifier.background(scheme.secondaryContainer) else Modifier)
                                } else {
                                    Modifier
                                },
                            )
                            .fillMaxWidth()
                            .height(if (material) 48.dp else PickerItemHeight)
                            .clickable {
                                host.close()
                                if (index in shown.unavailable) shown.onUnavailable(index) else shown.onSelect(index)
                            }
                            .padding(horizontal = if (material) 10.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val text = if (chosen) scheme.onSecondaryContainer else colors.text
                        Box(Modifier.width(26.dp)) {
                            if (index == shown.selected) {
                                Icon(LumenIcons.Check, null, tint = text, modifier = Modifier.size(17.dp))
                            }
                        }
                        Text(
                            option, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (index in shown.unavailable) text.copy(alpha = 0.38f) else text,
                        )
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


/**
 * Material's way of choosing one of a few: a dialog naming what's being chosen, the options as
 * radio buttons, and Cancel. Choosing one closes it.
 */
@Composable
private fun MaterialPickerDialog(host: PickerHost, request: PickerRequest?) {
    com.subhashh.lumen.ui.components.DialogScrim(request != null, onDismiss = { host.close() })
    AnimatedVisibility(
        visible = request != null,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.9f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        // Kept while it fades out, after the request has gone.
        val shown = remember { arrayOfNulls<PickerRequest>(1) }
        if (request != null) shown[0] = request
        val r = shown[0] ?: return@AnimatedVisibility
        val scheme = androidx.compose.material3.MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().dialogCard().padding(top = 24.dp, bottom = 12.dp),
            ) {
                com.subhashh.lumen.ui.components.DialogTitle(r.title)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    r.options.forEachIndexed { index, option ->
                        val off = index in r.unavailable
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    host.close()
                                    if (off) r.onUnavailable(index) else r.onSelect(index)
                                }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = index == r.selected, onClick = null, enabled = !off)
                            Spacer(Modifier.width(16.dp))
                            Text(option, fontSize = 16.sp, color = if (off) scheme.onSurface.copy(alpha = 0.38f) else scheme.onSurface)
                        }
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = { host.close() },
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp, top = 4.dp),
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}
