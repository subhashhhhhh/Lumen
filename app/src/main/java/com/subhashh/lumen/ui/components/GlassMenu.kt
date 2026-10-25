package com.subhashh.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.subhashh.lumen.ui.theme.GlassColors
import com.subhashh.lumen.ui.icons.LumenIcons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.theme.LocalGlassColors

@Immutable
data class MenuItem(
    val label: String,
    val destructive: Boolean = false,
    /** Choosing this item shows these instead (with a back row), like an iOS nested menu. */
    val submenu: List<MenuItem>? = null,
    /** Shows a checkmark: the current choice in a pick-one section. */
    val checked: Boolean = false,
    val onClick: () -> Unit = {},
)

/**
 * A glass action menu that grows out of the button that opened it ([origin] is that corner).
 * Sections are separated by dividers; choosing an item closes the menu first, unless it has a
 * [MenuItem.submenu], which replaces the menu's contents in place. Drawn in-window,
 * not as a popup, so its glass can refract what's behind it; a tap outside dismisses.
 */
@Composable
fun GlassMenu(
    backdrop: Backdrop,
    open: Boolean,
    onDismiss: () -> Unit,
    sections: List<List<MenuItem>>,
    origin: TransformOrigin,
    modifier: Modifier = Modifier,
) {
    if (open) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.5f, transformOrigin = origin) +
            fadeIn(tween(120)),
        exit = scaleOut(tween(160), targetScale = 0.7f, transformOrigin = origin) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        val colors = LocalGlassColors.current
        var submenu by remember { mutableStateOf<MenuItem?>(null) }
        LaunchedEffect(open) { if (!open) submenu = null }
        Column(
            Modifier
                .width(240.dp)
                .glass(
                    backdrop,
                    shape = RoundedCornerShape(22.dp),
                    blurRadius = 16.dp,
                    tint = colors.fallbackTint.copy(alpha = 0.82f),
                )
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 600f))
                .padding(vertical = 6.dp),
        ) {
            val parent = submenu
            if (parent != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clickable { submenu = null }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LumenIcons.Back, null, tint = colors.content, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(parent.label, color = colors.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = colors.contentSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                parent.submenu.orEmpty().forEach { item -> MenuRow(item, colors) { onDismiss(); item.onClick() } }
            } else {
                sections.filter { it.isNotEmpty() }.forEachIndexed { index, section ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = colors.contentSecondary.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    section.forEach { item ->
                        MenuRow(item, colors) {
                            if (item.submenu != null) {
                                submenu = item
                            } else {
                                onDismiss()
                                item.onClick()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, colors: GlassColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            color = if (item.destructive) Color(0xFFFF453A) else colors.content,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (item.checked) {
            Icon(LumenIcons.Check, contentDescription = stringResource(R.string.components_selected), tint = colors.content, modifier = Modifier.size(18.dp))
        }
        if (item.submenu != null) {
            Icon(
                LumenIcons.Back,
                null,
                tint = colors.contentSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = 180f },
            )
        }
    }
}
