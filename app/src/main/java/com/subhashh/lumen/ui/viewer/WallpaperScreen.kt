package com.subhashh.lumen.ui.viewer

import android.app.WallpaperManager
import androidx.compose.foundation.shape.CircleShape
import com.subhashh.lumen.ui.components.Spinner
import com.subhashh.lumen.ui.theme.Lumen
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import com.subhashh.lumen.ui.icons.LumenIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.components.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** How the photo is framed: zoom (1× just covers) and pan, in the px of whatever shows it. */
internal class Framing {
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    /** The size it's shown at: the whole screen, or the preview's card. */
    var viewW = 1f
    var viewH = 1f
    /** A finger has framed it. */
    var touched by mutableStateOf(false)

    /** The part of [image] in view, at [screenW] × [screenH]: what's set as the wallpaper. */
    fun crop(image: Bitmap, screenW: Int, screenH: Int): Bitmap {
        val scale = maxOf(viewW / image.width, viewH / image.height) * zoom
        val left = (viewW - image.width * scale) / 2f + panX
        val top = (viewH - image.height * scale) / 2f + panY
        val x = (-left / scale).roundToInt().coerceIn(0, image.width - 1)
        val y = (-top / scale).roundToInt().coerceIn(0, image.height - 1)
        val w = (viewW / scale).roundToInt().coerceIn(1, image.width - x)
        val h = (viewH / scale).roundToInt().coerceIn(1, image.height - y)
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(image, x, y, w, h), screenW, screenH, true)
    }
}

/** The photo, always covering what it's given; drag and pinch frame it. */
@Composable
private fun FramedPhoto(image: Bitmap, framing: Framing, modifier: Modifier = Modifier) {
    val imageBitmap = remember(image) { image.asImageBitmap() }
    Canvas(
        modifier.pointerInput(image) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                framing.touched = true
                val cover = maxOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
                framing.zoom = (framing.zoom * gestureZoom).coerceIn(1f, MaxZoom)
                val w = image.width * cover * framing.zoom
                val h = image.height * cover * framing.zoom
                framing.panX = (framing.panX + pan.x).coerceIn(-(w - size.width) / 2f, (w - size.width) / 2f)
                framing.panY = (framing.panY + pan.y).coerceIn(-(h - size.height) / 2f, (h - size.height) / 2f)
            }
        },
    ) {
        framing.viewW = size.width
        framing.viewH = size.height
        val scale = maxOf(size.width / image.width, size.height / image.height) * framing.zoom
        val shownW = image.width * scale
        val shownH = image.height * scale
        drawImage(
            imageBitmap,
            dstOffset = IntOffset(((size.width - shownW) / 2f + framing.panX).roundToInt(), ((size.height - shownH) / 2f + framing.panY).roundToInt()),
            dstSize = IntSize(shownW.roundToInt(), shownH.roundToInt()),
        )
    }
}

/**
 * Set a photo as the wallpaper; drag and pinch to frame it, then pick where it goes. What's
 * framed is exactly what's set. Lumen's style fills the screen with it, as it will be; the
 * Material style previews it on a card the shape of the screen, as Android's own picker does.
 */
@Composable
fun WallpaperScreen(
    item: MediaItem,
    onDone: (message: String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var applying by remember { mutableStateOf(false) }
    val material = Lumen.material

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (material) MaterialTheme.colorScheme.background else Color.Black)
            // Touches stop here: a tap beside the card isn't one on the viewer underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        // Decoded to just cover the screen: framing at 1× maps pixel for pixel.
        val bitmap by produceState<Bitmap?>(null, item, screenW, screenH) {
            value = withContext(Dispatchers.IO) {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(screenW, screenH)
                        .scale(Scale.FILL)
                        .allowHardware(false)
                        .build(),
                )
                (result.image as? BitmapImage)?.bitmap
            }
        }
        val image = bitmap
        val framing = remember(image) { Framing() }
        val apply = { which: Int, done: String ->
            if (!applying && image != null) {
                applying = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            WallpaperManager.getInstance(context).setBitmap(framing.crop(image, screenW, screenH), null, true, which)
                        }.isSuccess
                    }
                    applying = false
                    onDone(if (ok) done else context.getString(R.string.viewer_wallpaper_failed))
                }
            }
        }

        if (material) {
            MaterialWallpaper(image, framing, screenW.toFloat() / screenH, onCancel) { where ->
                when (where) {
                    0 -> apply(WallpaperManager.FLAG_LOCK, context.getString(R.string.viewer_wallpaper_set_lock))
                    1 -> apply(WallpaperManager.FLAG_SYSTEM, context.getString(R.string.viewer_wallpaper_set_home))
                    else -> apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, context.getString(R.string.viewer_wallpaper_set_both))
                }
            }
        } else if (image == null) {
            Spinner(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            FramedPhoto(image, framing, Modifier.fillMaxSize())
            Text(
                stringResource(R.string.viewer_wallpaper_hint),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperButton(stringResource(R.string.viewer_wallpaper_home)) { apply(WallpaperManager.FLAG_SYSTEM, context.getString(R.string.viewer_wallpaper_set_home)) }
                WallpaperButton(stringResource(R.string.viewer_wallpaper_lock)) { apply(WallpaperManager.FLAG_LOCK, context.getString(R.string.viewer_wallpaper_set_lock)) }
                WallpaperButton(stringResource(R.string.viewer_wallpaper_both)) { apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, context.getString(R.string.viewer_wallpaper_set_both)) }
                WallpaperButton(stringResource(R.string.common_cancel), dim = true, onClick = onCancel)
            }
        }
        if (applying) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Spinner(color = Color.White)
            }
        }
    }
}

/**
 * The Material style's wallpaper screen: Back, "Preview" and Set wallpaper across the top; the
 * photo on a card the shape of the screen, framed there by drag and pinch, with the lock
 * screen's clock or the home screen's dock sketched over it; underneath, where it goes.
 * [onSet] gets 0 for the lock screen, 1 for the home screen, 2 for both.
 */
@Composable
internal fun MaterialWallpaper(image: Bitmap?, framing: Framing, screenAspect: Float, onCancel: () -> Unit, onSet: (where: Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var where by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).pressable(onCancel, pressedScale = 0.9f, shape = CircleShape), contentAlignment = Alignment.Center) {
                Icon(LumenIcons.Back, stringResource(R.string.common_back), tint = scheme.onSurface, modifier = Modifier.size(24.dp))
            }
            Text(stringResource(R.string.wallpaper_preview), color = scheme.onSurface, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Button(onClick = { onSet(where) }, enabled = image != null, modifier = Modifier.height(52.dp)) {
                Text(stringResource(R.string.wallpaper_set), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .aspectRatio(screenAspect)
                    .clip(RoundedCornerShape(36.dp))
                    .background(scheme.surfaceContainerHigh),
            ) {
                if (image == null) {
                    Spinner(Modifier.align(Alignment.Center), color = scheme.onSurfaceVariant)
                } else {
                    FramedPhoto(image, framing, Modifier.fillMaxSize())
                    val over = TextStyle(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 8f))
                    if (where == 1) {
                        // The home screen, roughly: a row of apps over a search bar.
                        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                repeat(4) { Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.32f), CircleShape)) }
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.width(190.dp).height(34.dp).background(Color.White.copy(alpha = 0.26f), CircleShape))
                        }
                    } else {
                        // The lock screen's clock.
                        val now = remember { java.time.LocalDateTime.now() }
                        val appContext = LocalContext.current.applicationContext
                        Column(Modifier.align(Alignment.TopCenter).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                remember(now) { java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).format(now) },
                                style = over,
                                fontSize = 13.sp,
                            )
                            Text(
                                // Just the digits, as a lock screen's clock has them.
                                remember(now) { java.time.format.DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(appContext)) "HH:mm" else "h:mm").format(now) },
                                style = over,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Light,
                            )
                        }
                    }
                    // Until it's been framed: how to.
                    if (!framing.touched) {
                        Text(
                            stringResource(R.string.viewer_wallpaper_hint),
                            color = scheme.onSecondaryContainer,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(scheme.secondaryContainer, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        // Where it goes; the card shows that screen.
        Row(Modifier.padding(top = 8.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(R.string.wallpaper_lock, R.string.wallpaper_home, R.string.viewer_wallpaper_both).forEachIndexed { i, label ->
                val on = where == i
                Box(
                    Modifier
                        .height(48.dp)
                        .pressable({ where = i }, pressedScale = 0.95f, shape = RoundedCornerShape(16.dp))
                        .background(if (on) scheme.secondaryContainer else Color.Transparent, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), color = if (on) scheme.onSecondaryContainer else scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun WallpaperButton(label: String, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressable(onClick, pressedScale = 0.97f, shape = if (Lumen.material) CircleShape else RoundedCornerShape(14.dp))
            .clip(if (Lumen.material) CircleShape else RoundedCornerShape(14.dp))
            .background(if (Lumen.material) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xE61C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (dim) Color.White.copy(alpha = 0.7f) else Color.White,
            fontSize = 17.sp,
            fontWeight = if (dim) FontWeight.Normal else Lumen.type.emphasis,
        )
    }
}

private const val MaxZoom = 5f
