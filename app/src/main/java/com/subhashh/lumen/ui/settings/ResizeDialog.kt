package com.subhashh.lumen.ui.settings

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.Lumen
import com.subhashh.lumen.ui.components.DialogButtons
import com.subhashh.lumen.ui.components.DialogTitle
import com.subhashh.lumen.ui.components.dialogAlignment
import com.subhashh.lumen.ui.components.dialogCard
import com.subhashh.lumen.ui.components.dialogTextAlign

/**
 * Resize / Convert: size, format and quality for new copies of [count] photos, each a glass
 * dropdown like Settings'. Null [count] = closed.
 */
@Composable
fun ResizeDialog(
    count: Int?,
    onSave: (ImageExport.Target, ImageExport.Format, ImageExport.Quality) -> Unit,
    onDismiss: () -> Unit,
) {
    val open = count != null
    // Added when it opens, not composed all along and switched on: Back goes to the newest
    // handler, and a viewer opened since (its own handlers) would otherwise take it (#24).
    if (open) BackHandler(onBack = onDismiss)
    com.subhashh.lumen.ui.components.DialogScrim(open, onDismiss, strong = true)
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.92f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val n = count ?: return@AnimatedVisibility
        var target by remember { mutableStateOf(ImageExport.Target.FullHD) }
        var format by remember { mutableStateOf(ImageExport.Format.Same) }
        var quality by remember { mutableStateOf(ImageExport.Quality.High) }
        val colors = groupColors()
        val material = Lumen.material
        // The dropdowns' glass refracts the card under them, so the card lives in the picker area.
        PickerArea {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .dialogCard(Lumen.colors.panel, if (material) Lumen.shapes.dialog else RoundedCornerShape(24.dp)),
                    horizontalAlignment = dialogAlignment,
                ) {
                    Spacer(Modifier.height(if (material) 24.dp else 20.dp))
                    DialogTitle(stringResource(R.string.settings_resize_title), oneLine = true)
                    Spacer(Modifier.height(if (material) 12.dp else 4.dp))
                    Text(
                        if (n == 1) {
                            stringResource(R.string.settings_resize_message_one)
                        } else {
                            pluralStringResource(R.plurals.settings_resize_message, n, n)
                        },
                        color = colors.secondary,
                        fontSize = if (material) 14.sp else 13.sp,
                        textAlign = dialogTextAlign,
                        modifier = Modifier.padding(horizontal = if (material) 24.dp else 20.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    PickerRow(
                        title = stringResource(R.string.settings_resize_size),
                        options = ImageExport.Target.entries.map { stringResource(it.label) },
                        selected = target.ordinal,
                        onSelect = { target = ImageExport.Target.entries[it] },
                        icon = LumenIcons.Image,
                        iconColor = Blue,
                    )
                    Divider()
                    PickerRow(
                        title = stringResource(R.string.settings_resize_format),
                        options = ImageExport.Format.entries.map { stringResource(it.label) },
                        selected = format.ordinal,
                        onSelect = { format = ImageExport.Format.entries[it] },
                        icon = LumenIcons.Document,
                        iconColor = Orange,
                    )
                    // PNG is lossless: quality doesn't apply.
                    if (format != ImageExport.Format.Png) {
                        Divider()
                        PickerRow(
                            title = stringResource(R.string.settings_resize_quality),
                            options = ImageExport.Quality.entries.map { stringResource(it.label) },
                            selected = quality.ordinal,
                            onSelect = { quality = ImageExport.Quality.entries[it] },
                            icon = LumenIcons.Hdr,
                            iconColor = Green,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    DialogButtons(
                        cancel = stringResource(R.string.common_cancel),
                        onCancel = onDismiss,
                        confirm = stringResource(if (n == 1) R.string.settings_resize_save_copy else R.string.settings_resize_save_copies),
                        onConfirm = { onSave(target, format, quality) },
                        height = 50.dp,
                        line = colors.divider,
                    )
                }
            }
        }
    }
}
