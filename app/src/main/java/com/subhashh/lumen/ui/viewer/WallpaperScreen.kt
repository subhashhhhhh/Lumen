package com.subhashh.lumen.ui.viewer

import android.app.WallpaperManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * Set a photo as the wallpaper: it fills the screen as it will on the home or lock screen; drag
 * and pinch to frame it, then pick where it goes. What's on screen is exactly what's set.
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
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
        if (image == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            return@BoxWithConstraints
        }
        val imageBitmap = remember(image) { image.asImageBitmap() }
        // Cover scale, then the user's zoom (1× = just covering) and pan, always kept covering.
        val cover = maxOf(screenW.toFloat() / image.width, screenH.toFloat() / image.height)
        var zoom by remember(image) { mutableFloatStateOf(1f) }
        var panX by remember(image) { mutableFloatStateOf(0f) }
        var panY by remember(image) { mutableFloatStateOf(0f) }
        val shownW = image.width * cover * zoom
        val shownH = image.height * cover * zoom
        val left = (screenW - shownW) / 2f + panX
        val top = (screenH - shownH) / 2f + panY

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(image) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, MaxZoom)
                        val w = image.width * cover * zoom
                        val h = image.height * cover * zoom
                        panX = (panX + pan.x).coerceIn(-(w - screenW) / 2f, (w - screenW) / 2f)
                        panY = (panY + pan.y).coerceIn(-(h - screenH) / 2f, (h - screenH) / 2f)
                    }
                },
        ) {
            drawImage(
                imageBitmap,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(shownW.roundToInt(), shownH.roundToInt()),
            )
        }
        val apply = { which: Int, done: String ->
            if (!applying) {
                applying = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            // The visible part of the bitmap, at screen size.
                            val scale = cover * zoom
                            val x = (-left / scale).roundToInt().coerceIn(0, image.width - 1)
                            val y = (-top / scale).roundToInt().coerceIn(0, image.height - 1)
                            val w = (screenW / scale).roundToInt().coerceIn(1, image.width - x)
                            val h = (screenH / scale).roundToInt().coerceIn(1, image.height - y)
                            val crop = Bitmap.createBitmap(image, x, y, w, h)
                            val sized = Bitmap.createScaledBitmap(crop, screenW, screenH, true)
                            WallpaperManager.getInstance(context).setBitmap(sized, null, true, which)
                        }.isSuccess
                    }
                    applying = false
                    onDone(if (ok) done else context.getString(R.string.viewer_wallpaper_failed))
                }
            }
        }

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
        if (applying) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
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
            .pressable(onClick, pressedScale = 0.97f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE61C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (dim) Color.White.copy(alpha = 0.7f) else Color.White,
            fontSize = 17.sp,
            fontWeight = if (dim) FontWeight.Normal else FontWeight.SemiBold,
        )
    }
}

private const val MaxZoom = 5f
