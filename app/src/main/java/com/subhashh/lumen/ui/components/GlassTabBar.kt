package com.subhashh.lumen.ui.components

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
                    enabled = enabled,
                    // Outline normally, filled when selected — like iOS.
                    icon = if (tab == selected) tab.selectedIcon else tab.icon,
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
                .pressable(onSearch, enabled)
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
    val pill by animateColorAsState(
        if (selected) colors.selection else Color.Transparent,
        label = "tabPill",
    )
    Column(
        modifier = Modifier
            .pressable(onClick, enabled, pressedScale = 0.9f)
            .clip(CircleShape)
            .background(pill)
            .width(88.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp)
    }
}
