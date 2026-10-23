package com.subhashh.lumen.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class Confirmation(
    val title: String,
    val message: String,
    val confirm: String,
    val destructive: Boolean = false,
    val onConfirm: () -> Unit,
)

/** iOS-style alert: title, message, Cancel and one action. Shown while [confirmation] is non-null. */
@Composable
fun ConfirmDialog(confirmation: Confirmation?, onDismiss: () -> Unit) {
    val open = confirmation != null
    BackHandler(enabled = open, onBack = onDismiss)
    AnimatedVisibility(visible = open, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.9f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val c = confirmation ?: return@AnimatedVisibility
        val dark = LocalDarkTheme.current
        val card = if (dark) Color(0xFF2C2C2E) else Color.White
        val text = if (dark) Color.White else Color.Black
        val secondary = if (dark) Color(0x99EBEBF5) else Color(0x993C3C43)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(290.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(card)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(c.title, color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp))
                Spacer(Modifier.height(6.dp))
                Text(c.message, color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp))
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(secondary.copy(alpha = 0.3f)))
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    Text(
                        "Cancel",
                        color = Color(0xFF0A84FF),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 13.dp),
                    )
                    Box(Modifier.width(0.5.dp).height(48.dp).background(secondary.copy(alpha = 0.3f)))
                    Text(
                        c.confirm,
                        color = if (c.destructive) Color(0xFFFF453A) else Color(0xFF0A84FF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onDismiss()
                                c.onConfirm()
                            }
                            .padding(vertical = 13.dp),
                    )
                }
            }
        }
    }
}
