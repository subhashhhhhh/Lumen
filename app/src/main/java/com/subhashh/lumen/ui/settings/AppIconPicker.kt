package com.subhashh.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.LauncherIcon
import com.subhashh.lumen.ui.components.AppIcon
import com.subhashh.lumen.ui.icons.LumenIcons

/**
 * Every icon Lumen can wear, side by side, each drawn in the phone's own icon shape. The chosen
 * one carries a tick and goes on the Home screen when the user leaves Lumen.
 */
@Composable
internal fun AppIconPicker() {
    val context = LocalContext.current
    val colors = groupColors()
    val accent = MaterialTheme.colorScheme.primary
    val card = cardColor()
    var chosen by remember { mutableStateOf(LauncherIcon.chosen(context)) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (icon in LauncherIcon.entries) {
            val selected = icon == chosen
            Column(
                Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(selected, role = Role.RadioButton) {
                        LauncherIcon.choose(icon)
                        chosen = icon
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(62.dp)) {
                    AppIcon(58.dp, Modifier.align(Alignment.Center), icon)
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .border(2.dp, card, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(LumenIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Text(
                    stringResource(icon.label),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) accent else colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
