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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.ui.theme.LocalGlassColors

@Immutable
data class MenuItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A glass action menu that grows out of the button that opened it ([origin] is that corner).
 * Sections are separated by dividers; choosing an item closes the menu first. Drawn in-window,
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
        Column(
            Modifier
                .width(240.dp)
                .glass(
                    backdrop,
                    shape = RoundedCornerShape(22.dp),
                    blurRadius = 16.dp,
                    tint = colors.fallbackTint.copy(alpha = 0.82f),
                )
                .padding(vertical = 6.dp),
        ) {
            sections.filter { it.isNotEmpty() }.forEachIndexed { index, section ->
                if (index > 0) {
                    HorizontalDivider(
                        color = colors.contentSecondary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                section.forEach { item ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            item.label,
                            color = if (item.destructive) Color(0xFFFF453A) else colors.content,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}
