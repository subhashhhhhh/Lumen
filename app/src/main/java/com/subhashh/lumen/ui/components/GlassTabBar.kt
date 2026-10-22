package com.subhashh.lumen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
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

enum class LumenTab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Rounded.PhotoLibrary),
    Collections("Collections", Icons.Rounded.Collections),
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
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .glass(backdrop)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LumenTab.entries.forEach { tab ->
                TabItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
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
                .glass(backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSearch,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = searchTint)
        }
    }
}

@Composable
private fun TabItem(
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
    val pill by animateColorAsState(
        if (selected) colors.selection else Color.Transparent,
        label = "tabPill",
    )
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .background(pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .width(88.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp)
    }
}
