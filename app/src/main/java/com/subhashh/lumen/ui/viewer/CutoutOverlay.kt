package com.subhashh.lumen.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.Cutout
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Touch and hold a subject in a photo: it lifts off its background ([Cutout]), the photo dims, and
 * Android's floating toolbar offers Copy, Share and Save. A tap elsewhere or back puts it down.
 */
@Stable
class CutoutState {
    /** The photo being cut out of. */
    var item by mutableStateOf<MediaItem?>(null)
        private set
    /** Where the press was, while the cutout is still being made (for the spinner). */
    var pressedAt by mutableStateOf<Offset?>(null)
        private set
    /** Where the finger is: the subject comes forward from there. */
    var press by mutableStateOf(Offset.Zero)
        private set
    var result by mutableStateOf<Cutout.Result?>(null)
        private set
    /** The photo's place on screen when it was pressed: the cutout lifts from the same spot. */
    var photoBounds by mutableStateOf(Rect.Zero)
        private set

    val open get() = item != null

    fun start(item: MediaItem, at: Offset, bounds: Rect) {
        this.item = item
        pressedAt = at
        press = at
        photoBounds = bounds
        result = null
    }

    fun show(item: MediaItem, cutout: Cutout.Result) {
        if (this.item?.id != item.id) return
        pressedAt = null
        result = cutout
    }

    fun clear() {
        item = null
        pressedAt = null
        result = null
    }

    /** The cutout's rectangle on screen, from its place in the photo. */
    val bounds: Rect?
        get() = result?.bounds?.let { b ->
            val p = photoBounds
            Rect(p.left + b.left * p.width, p.top + b.top * p.height, p.left + b.right * p.width, p.top + b.bottom * p.height)
        }
}

/** The dimmed photo, the lifted subject, and the spinner while it's being made. */
@Composable
fun CutoutLayer(state: CutoutState, modifier: Modifier = Modifier) {
    if (!state.open) return
    val result = state.result
    val dim = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        launch { dim.animateTo(1f, spring(stiffness = 500f)) }
        lift.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
    }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(state) { detectTapGestures { state.clear() } },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim.value }.background(Color.Black.copy(alpha = 0.6f)))
        val bounds = state.bounds
        if (result != null && bounds != null) {
            val density = LocalDensity.current
            val image = remember(result) { result.bitmap.asImageBitmap() }
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        // Forward, towards the finger: it grows from under it, as if picked up there.
                        transformOrigin = TransformOrigin(
                            ((state.press.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
                            ((state.press.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
                        )
                        val s = 1f + 0.06f * lift.value
                        scaleX = s
                        scaleY = s
                    },
            )
        }
        // Only when it takes a while: the first cutout loads the model (and checks the GPU).
        val at = state.pressedAt
        if (at != null) {
            var slow by remember(at) { mutableStateOf(false) }
            LaunchedEffect(at) {
                delay(400)
                slow = true
            }
            if (slow) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .offset { IntOffset((at.x - 16.dp.toPx()).roundToInt(), (at.y - 16.dp.toPx()).roundToInt()) }
                        .size(32.dp),
                )
            }
        }
    }
}

/** Android's floating toolbar above the lifted subject: Copy, Share, Save. */
@Composable
fun CutoutToolbar(state: CutoutState) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(state.result to state.item)
    val show = state.result != null
    var closing by remember { mutableStateOf(false) }

    val callback = remember(state, view) {
        object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                menu.add(Menu.NONE, ItemCopy, 0, R.string.cutout_copy)
                menu.add(Menu.NONE, ItemShare, 1, R.string.cutout_share)
                menu.add(Menu.NONE, ItemSave, 2, R.string.cutout_save)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val (result, photo) = latest.value
                if (result == null || photo == null) return true
                val bitmap = result.bitmap
                scope.launch {
                    when (item.itemId) {
                        ItemCopy -> png(context, bitmap, photo)?.let { uri ->
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newUri(context.contentResolver, context.getString(R.string.smart_pack_cutout), uri))
                            // Android 13+ shows its own confirmation.
                            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, R.string.cutout_copied, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ItemShare -> png(context, bitmap, photo)?.let { uri ->
                            val send = Intent(Intent.ACTION_SEND).setType("image/png")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .apply { clipData = ClipData.newRawUri(null, uri) }
                            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        ItemSave -> {
                            val saved = save(context, bitmap, photo)
                            Toast.makeText(context, if (saved) R.string.cutout_saved else R.string.cutout_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    state.clear()
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Closed by Android (back, a tap elsewhere): put the subject down.
                if (!closing) state.clear()
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                val b = state.bounds ?: return
                outRect.set(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
            }
        }
    }

    DisposableEffect(show) {
        var mode: ActionMode? = null
        if (show) {
            closing = false
            mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        }
        onDispose {
            closing = true
            mode?.finish()
        }
    }
}

private const val ItemCopy = 1
private const val ItemShare = 2
private const val ItemSave = 3

/** The cutout as a PNG in Lumen's cache, offered through its file provider (copy, share). */
private suspend fun png(context: Context, bitmap: Bitmap, photo: MediaItem): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // A new name each time: apps that already read the last one may hold it cached.
        dir.listFiles { f -> f.name.startsWith("cutout") }?.forEach { it.delete() }
        val file = File(dir, "cutout_${photo.name.substringBeforeLast('.')}_${System.currentTimeMillis() % 100_000}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()
}

/** Saves the cutout as a PNG in Pictures/Cutouts, where it shows up in the library. */
private suspend fun save(context: Context, bitmap: Bitmap, photo: MediaItem): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val uri = runCatching {
        resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${photo.name.substringBeforeLast('.')}_cutout.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Cutouts/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        )
    }.getOrNull() ?: return@withContext false
    val ok = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    }.getOrDefault(false)
    if (!ok) {
        resolver.delete(uri, null, null)
        return@withContext false
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    true
}

/**
 * The outline of the people in the photo ([Cutout.outline]) drawing itself, as the photo settles on
 * screen: from where a person meets the photo's edge, up and around, to the other side. Then it
 * fades. Says they can be touched and held to lift them out. [bounds] is the photo on screen.
 */
private const val PenDpPerSecond = 900f
private val PenEasing = CubicBezierEasing(0.25f, 0.1f, 0.75f, 0.9f)

@Composable
fun OutlineSweep(lines: List<FloatArray>, bounds: Rect, modifier: Modifier = Modifier) {
    val draw = remember(lines) { Animatable(0f) }
    val fade = remember(lines) { Animatable(1f) }
    val paths = remember(lines, bounds) {
        lines.map { line ->
            Path().apply {
                moveTo(bounds.left + line[0] * bounds.width, bounds.top + line[1] * bounds.height)
                for (i in 2 until line.size step 2) lineTo(bounds.left + line[i] * bounds.width, bounds.top + line[i + 1] * bounds.height)
            }.let { it to PathMeasure().apply { setPath(it, false) }.length }
        }
    }
    val total = remember(paths) { paths.sumOf { it.second.toDouble() }.toFloat() }
    val pxPerDp = LocalDensity.current.density
    LaunchedEffect(lines) {
        // One pen at a steady speed, so every photo feels the same: a small person isn't slow,
        // a big one isn't rushed. Within 0.8 to 2.6 s; eased only at the very start and end.
        val ms = (total / pxPerDp / PenDpPerSecond * 1000).toInt().coerceIn(800, 2600)
        draw.animateTo(1f, tween(ms, easing = PenEasing))
        delay(250)
        fade.animateTo(0f, tween(600))
    }
    if (fade.value <= 0f || bounds.width <= 0f) return
    val measure = remember { PathMeasure() }
    val part = remember { Path() }
    Canvas(modifier.fillMaxSize()) {
        if (total <= 0f) return@Canvas
        val alpha = fade.value
        val glow = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val core = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // How far the pen has gone along all the lines, one after another.
        var left = total * draw.value
        for ((path, length) in paths) {
            if (length <= 0f || left <= 0f) continue
            val end = minOf(length, left)
            left -= length
            part.reset()
            measure.setPath(path, false)
            measure.getSegment(0f, end, part, true)
            drawPath(part, Color.White.copy(alpha = 0.22f * alpha), style = glow)
            drawPath(part, Color.White.copy(alpha = 0.95f * alpha), style = core)
            // The pen: a brighter point where the line is being drawn.
            if (end < length) {
                val tip = measure.getPosition(end)
                drawCircle(Color.White.copy(alpha = 0.5f * alpha), radius = 7.dp.toPx(), center = tip)
                drawCircle(Color.White.copy(alpha = alpha), radius = 3.dp.toPx(), center = tip)
            }
        }
    }
}
