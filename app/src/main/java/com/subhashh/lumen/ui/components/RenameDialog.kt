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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rename a file: edits the name only, the extension stays fixed beside it. Opens with the name
 * selected so typing replaces it. [currentName] includes the extension; null while loading.
 */
@Composable
fun RenameDialog(
    currentName: String?,
    onRename: (newName: String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.common_rename),
    confirm: String = stringResource(R.string.common_rename),
    /** Files keep their extension fixed; album names have none. */
    keepExtension: Boolean = true,
    /** Any text, "/" included, and blank to clear (captions); names must be non-blank, no "/". */
    freeText: Boolean = false,
    /** A suggested name (a new person's, from search) can be accepted as it stands. */
    acceptUnchanged: Boolean = false,
) {
    val open = currentName != null
    // Added when it opens, not composed all along and switched on: Back goes to the newest
    // handler, and a viewer opened since (its own handlers) would otherwise take it (#24).
    if (open) BackHandler(onBack = onDismiss)
    DialogScrim(open, onDismiss)
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.9f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val name = currentName ?: return@AnimatedVisibility
        val base = if (keepExtension) name.substringBeforeLast('.', name) else name
        val ext = if (keepExtension) name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" } else ""
        var field by remember(name) { mutableStateOf(TextFieldValue(base, TextRange(0, base.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(name) { focus.requestFocus() }
        val text = Lumen.colors.text
        val secondary = Lumen.colors.secondary
        val material = Lumen.material
        val valid = (acceptUnchanged || field.text != base) && (freeText || (field.text.isNotBlank() && '/' !in field.text))
        val submit = { if (valid) onRename(field.text.trim() + ext) }

        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(if (material) 320.dp else 300.dp)
                    .dialogCard()
                    .padding(top = if (material) 24.dp else 20.dp),
                horizontalAlignment = dialogAlignment,
            ) {
                DialogTitle(title, oneLine = true)
                Spacer(Modifier.height(if (material) 18.dp else 14.dp))
                Row(
                    Modifier
                        .padding(horizontal = if (material) 24.dp else 16.dp)
                        .fillMaxWidth()
                        .clip(Lumen.shapes.field)
                        .background(Lumen.colors.field)
                        .padding(horizontal = if (material) 16.dp else 12.dp, vertical = if (material) 14.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = text, fontSize = 16.sp),
                        cursorBrush = SolidColor(Lumen.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                    )
                    Text(ext, color = secondary, fontSize = 16.sp)
                }
                Spacer(Modifier.height(18.dp))
                DialogButtons(
                    cancel = stringResource(R.string.common_cancel),
                    onCancel = onDismiss,
                    confirm = confirm,
                    onConfirm = { submit() },
                    enabled = valid,
                )
            }
        }
    }
}
