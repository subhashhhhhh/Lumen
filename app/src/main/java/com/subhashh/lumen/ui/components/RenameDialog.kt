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
    title: String = "Rename",
    confirm: String = "Rename",
    /** Files keep their extension fixed; album names have none. */
    keepExtension: Boolean = true,
    /** Any text, "/" included, and blank to clear (captions); names must be non-blank, no "/". */
    freeText: Boolean = false,
) {
    val open = currentName != null
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
        val name = currentName ?: return@AnimatedVisibility
        val base = if (keepExtension) name.substringBeforeLast('.', name) else name
        val ext = if (keepExtension) name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" } else ""
        var field by remember(name) { mutableStateOf(TextFieldValue(base, TextRange(0, base.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(name) { focus.requestFocus() }
        val dark = LocalDarkTheme.current
        val card = if (dark) Color(0xFF2C2C2E) else Color.White
        val text = if (dark) Color.White else Color.Black
        val secondary = if (dark) Color(0x99EBEBF5) else Color(0x993C3C43)
        val valid = field.text != base && (freeText || (field.text.isNotBlank() && '/' !in field.text))
        val submit = { if (valid) onRename(field.text.trim() + ext) }

        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(card)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(secondary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = text, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF0A84FF)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                    )
                    Text(ext, color = secondary, fontSize = 16.sp)
                }
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
                        confirm,
                        color = if (valid) Color(0xFF0A84FF) else secondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = valid) { submit() }
                            .padding(vertical = 13.dp),
                    )
                }
            }
        }
    }
}
