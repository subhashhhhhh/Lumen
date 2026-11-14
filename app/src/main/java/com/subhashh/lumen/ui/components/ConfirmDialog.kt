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
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.theme.Lumen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    /** Longer Markdown under the message (release notes): left-aligned and scrollable. */
    val details: String? = null,
    /** The dismiss button's label; Cancel when null. */
    val cancel: String? = null,
    /** Runs when that button is tapped (not when the dialog is just closed). */
    val onCancel: (() -> Unit)? = null,
    val onConfirm: () -> Unit,
)

/**
 * iOS-style alert: title, message, Cancel and one action. Shown while [confirmation] is non-null.
 * It stays inside the screen's safe area, and only its middle scrolls, so the buttons are always
 * on screen (on a small phone with three-button navigation too).
 */
@Composable
fun ConfirmDialog(confirmation: Confirmation?, onDismiss: () -> Unit) {
    val open = confirmation != null
    BackHandler(enabled = open, onBack = onDismiss)
    DialogScrim(open, onDismiss)
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.9f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val c = confirmation ?: return@AnimatedVisibility
        val text = Lumen.colors.text
        val secondary = Lumen.colors.secondary
        val material = Lumen.material
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(if (c.details != null || material) 320.dp else 290.dp)
                    .dialogCard()
                    .padding(top = if (material) 24.dp else 20.dp),
                horizontalAlignment = dialogAlignment,
            ) {
                DialogTitle(c.title)
                Spacer(Modifier.height(if (material) 16.dp else 6.dp))
                // The middle takes what's left of the screen and scrolls; title and buttons stay put.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    DialogMessage(c.message, Modifier.fillMaxWidth())
                    c.details?.let { details ->
                        Spacer(Modifier.height(14.dp))
                        Markdown(details, color = secondary, headingColor = text, modifier = Modifier.padding(horizontal = dialogInset))
                    }
                }
                Spacer(Modifier.height(18.dp))
                DialogButtons(
                    cancel = c.cancel ?: stringResource(R.string.common_cancel),
                    onCancel = { onDismiss(); c.onCancel?.invoke() },
                    confirm = c.confirm,
                    onConfirm = {
                        onDismiss()
                        c.onConfirm()
                    },
                    destructive = c.destructive,
                )
            }
        }
    }
}
