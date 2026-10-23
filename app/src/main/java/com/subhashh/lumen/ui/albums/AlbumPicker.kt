package com.subhashh.lumen.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.ui.images.version
import com.subhashh.lumen.data.MediaThumbnail
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.images.ImageKeys

/** Where to put photos: an existing album (by folder) or a new one. */
data class AlbumTarget(val relativePath: String, val title: String)

private data class PickerColors(val sheet: Color, val row: Color, val text: Color, val secondary: Color)

@Composable
private fun pickerColors() = if (LocalDarkTheme.current) {
    PickerColors(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color.White, Color(0x99EBEBF5))
} else {
    PickerColors(Color(0xFFF2F2F7), Color.White, Color.Black, Color(0x993C3C43))
}

/**
 * Bottom sheet to choose an album for Copy / Move: "New Album…" first (named inline), then every
 * device folder with its cover and count.
 */
@Composable
fun AlbumPicker(
    open: Boolean,
    title: String,
    albums: List<MediaCollection>,
    onPick: (AlbumTarget) -> Unit,
    onDismiss: () -> Unit,
    newAlbumPath: (String) -> String,
    /** Offer "New Album…" (not when choosing a folder to exclude). */
    allowNewAlbum: Boolean = true,
) {
    BackHandler(enabled = open, onBack = onDismiss)
    AnimatedVisibility(visible = open, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = slideInVertically(spring(dampingRatio = 1f, stiffness = 400f)) { it },
        exit = slideOutVertically(tween(220)) { it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Sheet(title, albums, onPick, onDismiss, newAlbumPath, allowNewAlbum)
        }
    }
}

@Composable
private fun Sheet(
    title: String,
    albums: List<MediaCollection>,
    onPick: (AlbumTarget) -> Unit,
    onDismiss: () -> Unit,
    newAlbumPath: (String) -> String,
    allowNewAlbum: Boolean,
) {
    val colors = pickerColors()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(naming) { if (naming) focus.requestFocus() }
    val create = {
        if (name.isNotBlank()) onPick(AlbumTarget(newAlbumPath(name), name.trim()))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.sheet)
            // Taps inside the sheet must not reach the dismissing scrim behind it.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Cancel",
                color = Color(0xFF0A84FF),
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onDismiss),
            )
            Text(
                title,
                color = colors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (allowNewAlbum) item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.row)
                        .clickable { naming = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LumenIcons.NewAlbum, null, tint = Color(0xFF0A84FF))
                    }
                    Spacer(Modifier.width(14.dp))
                    if (!naming) {
                        Text("New Album…", color = colors.text, fontSize = 16.sp)
                    } else {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color(0xFF0A84FF)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { create() }),
                            decorationBox = { field ->
                                if (name.isEmpty()) Text("Album name", color = colors.secondary, fontSize = 16.sp)
                                field()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focus),
                        )
                        Text(
                            "Create",
                            color = if (name.isBlank()) colors.secondary else Color(0xFF0A84FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clickable(enabled = name.isNotBlank()) { create() },
                        )
                    }
                }
            }
            items(albums, key = { it.key }) { album ->
                val path = album.cover?.relativePath ?: return@items
                AlbumRow(album, colors, onClick = { onPick(AlbumTarget(path, album.title)) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AlbumRow(album: MediaCollection, colors: PickerColors, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.98f)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondary.copy(alpha = 0.15f)),
        ) {
            album.cover?.let { cover ->
                AsyncImage(
                    model = remember(cover.version) {
                        ImageRequest.Builder(context)
                            .data(MediaThumbnail(cover.uri))
                            .size(ImageKeys.ThumbBuckets.first(), ImageKeys.ThumbBuckets.first())
                            .memoryCacheKey(ImageKeys.thumb(cover, ImageKeys.ThumbBuckets.first()))
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(album.title, color = colors.text, fontSize = 16.sp)
            Text(album.count.toString(), color = colors.secondary, fontSize = 13.sp)
        }
    }
}
