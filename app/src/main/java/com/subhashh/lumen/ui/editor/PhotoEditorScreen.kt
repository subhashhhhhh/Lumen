package com.subhashh.lumen.ui.editor

import android.graphics.Bitmap
import com.subhashh.lumen.ui.components.Spinner
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.edit.Adjustment
import com.subhashh.lumen.data.edit.CropHandle
import com.subhashh.lumen.data.edit.CropRect
import com.subhashh.lumen.data.edit.CropShape
import com.subhashh.lumen.data.edit.PhotoEdit
import com.subhashh.lumen.data.edit.PhotoEditRenderer
import com.subhashh.lumen.data.edit.PhotoEditSaver
import com.subhashh.lumen.data.edit.PhotoFilter
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private enum class PhotoTab(@StringRes val label: Int) {
    Crop(R.string.editor_crop), Adjust(R.string.editor_adjust), Filters(R.string.editor_filters),
}

/**
 * The photo editor: crop, turn, flip and straighten; exposure, colour and vignette sliders; and
 * one-tap filters. Nothing is written until Save, which makes a new photo beside the original or,
 * for JPEG, PNG and WebP, replaces it.
 */
@Composable
fun PhotoEditorScreen(
    item: MediaItem,
    /** Runs the action once Android has let Lumen change this file. */
    withWriteAccess: (List<MediaItem>, () -> Unit) -> Unit,
    onDone: (message: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(PhotoEdit()) }
    var tab by remember { mutableStateOf(PhotoTab.Crop) }
    var shape by remember { mutableStateOf(CropShape.Free) }
    var adjusting by remember { mutableStateOf(Adjustment.Exposure) }
    var choices by remember { mutableStateOf<List<EditorChoice>?>(null) }
    var saving by remember { mutableStateOf(false) }

    /** Null while loading; a failed load closes the editor. */
    val preview by produceState<Bitmap?>(null, item) {
        value = runCatching { PhotoEditSaver.decode(context, item, PreviewSide) }.getOrNull()
        if (value == null) onDone(context.getString(R.string.editor_open_failed))
    }

    val leave = {
        if (edit.isUnchanged) {
            onCancel()
        } else {
            choices = listOf(
                EditorChoice(context.getString(R.string.editor_discard), Color(0xFFFF453A)) { onCancel() },
                EditorChoice(context.getString(R.string.editor_keep_editing)) { choices = null },
            )
        }
    }
    BackHandler {
        when {
            saving -> Unit
            choices != null -> choices = null
            else -> leave()
        }
    }

    val save = { replace: Boolean ->
        choices = null
        saving = true
        scope.launch {
            val ok = if (replace) PhotoEditSaver.replace(context, item, edit) else PhotoEditSaver.saveCopy(context, item, edit)
            saving = false
            if (ok) {
                onDone(context.getString(if (replace) R.string.editor_saved else R.string.editor_saved_copy))
            } else {
                // Still open, with the edit intact, to try the other way or again.
                android.widget.Toast.makeText(context, R.string.editor_save_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        Unit
    }
    val askSave = {
        if (PhotoEditSaver.canReplace(item)) {
            choices = listOf(
                EditorChoice(context.getString(R.string.editor_save_copy)) { save(false) },
                EditorChoice(context.getString(R.string.editor_replace_original)) {
                    choices = null
                    withWriteAccess(listOf(item)) { save(true) }
                },
                EditorChoice(context.getString(R.string.common_cancel), Color.White.copy(alpha = 0.7f)) { choices = null },
            )
        } else {
            save(false)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bitmap = preview
        if (bitmap == null) {
            Spinner(Modifier.align(Alignment.Center), color = Color.White)
            return@Box
        }
        val frame = edit.frameSize(bitmap.width.toFloat(), bitmap.height.toFloat())
        val aspect = shape.aspect(frame.first, frame.second)

        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            EditorTopBar(
                changed = !edit.isUnchanged,
                onCancel = leave,
                onReset = {
                    edit = PhotoEdit()
                    shape = CropShape.Free
                },
                onSave = askSave,
            )
            EditPreview(
                bitmap = bitmap,
                edit = edit,
                cropping = tab == PhotoTab.Crop,
                aspect = aspect,
                onCrop = { edit = edit.copy(crop = it) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth().height(PanelHeight), contentAlignment = Alignment.Center) {
                when (tab) {
                    PhotoTab.Crop -> CropPanel(
                        edit = edit,
                        shape = shape,
                        tall = frame.second > frame.first,
                        onEdit = { edit = it },
                        onShape = { picked ->
                            shape = picked
                            picked.aspect(frame.first, frame.second)?.let { edit = edit.copy(crop = CropRect.centered(it)) }
                        },
                    )
                    PhotoTab.Adjust -> AdjustPanel(edit, adjusting, onPick = { adjusting = it }, onEdit = { edit = it })
                    PhotoTab.Filters -> FilterPanel(bitmap, edit, onEdit = { edit = it })
                }
            }
            EditorTabs(
                tabs = PhotoTab.entries,
                selected = tab,
                icon = {
                    when (it) {
                        PhotoTab.Crop -> LumenIcons.Crop
                        PhotoTab.Adjust -> LumenIcons.Adjust
                        PhotoTab.Filters -> LumenIcons.Filters
                    }
                },
                label = { stringResource(it.label) },
                onSelect = { tab = it },
            )
        }
        EditorChoices(choices, onDismiss = { choices = null })
        if (saving) EditorBusy(stringResource(R.string.editor_saving))
    }
}

/**
 * The photo as it will be saved. While [cropping] it shows the whole frame instead, with the crop
 * box over it: drag a corner or a side to resize the box, or its middle to move it.
 */
@Composable
private fun EditPreview(
    bitmap: Bitmap,
    edit: PhotoEdit,
    cropping: Boolean,
    aspect: Float?,
    onCrop: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paint = remember(edit.adjustments, edit.filter) { PhotoEditRenderer.paint(edit) }
    val currentEdit by rememberUpdatedState(edit)
    val currentAspect by rememberUpdatedState(aspect)
    val currentOnCrop by rememberUpdatedState(onCrop)
    var dragging by remember { mutableStateOf(false) }

    // A corner or side of the crop box often sits at the screen's edge, where a drag is Android's
    // Back gesture: the editor would close instead of cropping. Those spots are taken out of it.
    val view = LocalView.current
    var placed by remember { mutableStateOf(Rect.Zero) }
    val reach = with(LocalDensity.current) { HandleReach.toPx() }
    val padding = with(LocalDensity.current) { PreviewPadding.toPx() }
    DisposableEffect(cropping, edit.crop, edit.straighten, edit.quarterTurns, placed) {
        if (cropping && !placed.isEmpty) {
            val box = cropBox(frameBox(placed.size, bitmap, edit, CropRect.Full, padding), edit.crop).translate(placed.topLeft)
            view.systemGestureExclusionRects = listOf(box.left, box.right).flatMap { x ->
                listOf(box.top, box.center.y, box.bottom).map { y ->
                    android.graphics.Rect((x - reach).toInt(), (y - reach).toInt(), (x + reach).toInt(), (y + reach).toInt())
                }
            }
        }
        onDispose { view.systemGestureExclusionRects = emptyList() }
    }

    Canvas(
        modifier.onGloballyPositioned { placed = it.boundsInRoot() }.pointerInput(bitmap, cropping) {
            if (!cropping) return@pointerInput
            var handle: CropHandle? = null
            detectDragGestures(
                onDragStart = { at ->
                    val box = frameBox(Size(size.width.toFloat(), size.height.toFloat()), bitmap, currentEdit, CropRect.Full, PreviewPadding.toPx())
                    handle = handleAt(at, cropBox(box, currentEdit.crop), HandleReach.toPx())
                    dragging = handle != null
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, drag ->
                val grabbed = handle ?: return@detectDragGestures
                change.consume()
                val box = frameBox(Size(size.width.toFloat(), size.height.toFloat()), bitmap, currentEdit, CropRect.Full, PreviewPadding.toPx())
                currentOnCrop(currentEdit.crop.dragged(grabbed, drag.x / box.width, drag.y / box.height, currentAspect))
            }
        },
    ) {
        val region = if (cropping) CropRect.Full else edit.crop
        val box = frameBox(size, bitmap, edit, region, PreviewPadding.toPx())
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(box.left, box.top)
            PhotoEditRenderer.draw(native, bitmap, edit, region, box.width, box.height, paint, finished = !cropping)
            native.restore()
        }
        if (cropping) drawCropBox(box, cropBox(box, edit.crop), grid = dragging)
    }
}

/** Where the [region] of the frame sits in a canvas of [size]: as big as fits inside [padding]. */
private fun frameBox(size: Size, bitmap: Bitmap, edit: PhotoEdit, region: CropRect, padding: Float): Rect {
    val (frameWidth, frameHeight) = edit.frameSize(bitmap.width.toFloat(), bitmap.height.toFloat())
    val width = region.width * frameWidth
    val height = region.height * frameHeight
    val scale = min((size.width - 2 * padding) / width, (size.height - 2 * padding) / height)
    val left = (size.width - width * scale) / 2f
    val top = (size.height - height * scale) / 2f
    return Rect(left, top, left + width * scale, top + height * scale)
}

private fun cropBox(frame: Rect, crop: CropRect) = Rect(
    frame.left + crop.left * frame.width,
    frame.top + crop.top * frame.height,
    frame.left + crop.right * frame.width,
    frame.top + crop.bottom * frame.height,
)

/** The part of the crop box under a touch at [at]: a corner first, then a side, then its inside. */
private fun handleAt(at: Offset, box: Rect, reach: Float): CropHandle? {
    val nearLeft = abs(at.x - box.left) <= reach
    val nearRight = abs(at.x - box.right) <= reach
    val nearTop = abs(at.y - box.top) <= reach
    val nearBottom = abs(at.y - box.bottom) <= reach
    val withinX = at.x in (box.left - reach)..(box.right + reach)
    val withinY = at.y in (box.top - reach)..(box.bottom + reach)
    return when {
        nearLeft && nearTop -> CropHandle.TopLeft
        nearRight && nearTop -> CropHandle.TopRight
        nearLeft && nearBottom -> CropHandle.BottomLeft
        nearRight && nearBottom -> CropHandle.BottomRight
        nearLeft && withinY -> CropHandle.Left
        nearRight && withinY -> CropHandle.Right
        nearTop && withinX -> CropHandle.Top
        nearBottom && withinX -> CropHandle.Bottom
        box.contains(at) -> CropHandle.Move
        else -> null
    }
}

/** Everything outside the box dimmed, a thin border, thick corners, and thirds lines while it moves. */
private fun DrawScope.drawCropBox(frame: Rect, box: Rect, grid: Boolean) {
    val dim = Color.Black.copy(alpha = 0.6f)
    drawRect(dim, Offset(frame.left, frame.top), Size(frame.width, box.top - frame.top))
    drawRect(dim, Offset(frame.left, box.bottom), Size(frame.width, frame.bottom - box.bottom))
    drawRect(dim, Offset(frame.left, box.top), Size(box.left - frame.left, box.height))
    drawRect(dim, Offset(box.right, box.top), Size(frame.right - box.right, box.height))
    if (grid) {
        val line = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            val x = box.left + box.width * i / 3f
            val y = box.top + box.height * i / 3f
            drawLine(line, Offset(x, box.top), Offset(x, box.bottom), 1.dp.toPx())
            drawLine(line, Offset(box.left, y), Offset(box.right, y), 1.dp.toPx())
        }
    }
    drawRect(Color.White, box.topLeft, box.size, style = Stroke(1.5.dp.toPx()))
    val arm = min(22.dp.toPx(), min(box.width, box.height) / 3f)
    val thick = 4.dp.toPx()
    for ((corner, dx, dy) in listOf(
        Triple(box.topLeft, 1f, 1f), Triple(box.topRight, -1f, 1f),
        Triple(box.bottomLeft, 1f, -1f), Triple(box.bottomRight, -1f, -1f),
    )) {
        drawLine(Color.White, corner, corner + Offset(arm * dx, 0f), thick, StrokeCap.Square)
        drawLine(Color.White, corner, corner + Offset(0f, arm * dy), thick, StrokeCap.Square)
    }
}

/** Turn and flip, the shapes, and the straighten slider. */
@Composable
private fun CropPanel(
    edit: PhotoEdit,
    shape: CropShape,
    tall: Boolean,
    onEdit: (PhotoEdit) -> Unit,
    onShape: (CropShape) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorIconButton(LumenIcons.RotateLeft, stringResource(R.string.editor_rotate), onClick = { onEdit(edit.rotatedLeft()) })
            EditorIconButton(LumenIcons.Flip, stringResource(R.string.editor_flip), onClick = { onEdit(edit.flippedHorizontally()) })
            Spacer(Modifier.width(4.dp))
            for (option in CropShape.entries) {
                EditorChip(shapeLabel(option, tall), selected = option == shape, onClick = { onShape(option) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorSlider(
                value = edit.straighten,
                onValueChange = { onEdit(edit.copy(straighten = it)) },
                min = -PhotoEdit.MaxStraighten,
                max = PhotoEdit.MaxStraighten,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.editor_degrees, edit.straighten.roundToInt()),
                color = if (edit.straighten == 0f) Color.White.copy(alpha = 0.6f) else EditorAccent,
                fontSize = 14.sp,
                modifier = Modifier.width(48.dp).padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun shapeLabel(shape: CropShape, tall: Boolean): String = when (shape) {
    CropShape.Free -> stringResource(R.string.editor_shape_free)
    CropShape.Original -> stringResource(R.string.editor_shape_original)
    CropShape.Square -> stringResource(R.string.editor_shape_square)
    CropShape.FourThree -> if (tall) "3:4" else "4:3"
    CropShape.ThreeTwo -> if (tall) "2:3" else "3:2"
    CropShape.SixteenNine -> if (tall) "9:16" else "16:9"
}

@StringRes
private fun adjustmentLabel(adjustment: Adjustment): Int = when (adjustment) {
    Adjustment.Exposure -> R.string.editor_exposure
    Adjustment.Brightness -> R.string.editor_brightness
    Adjustment.Contrast -> R.string.editor_contrast
    Adjustment.Saturation -> R.string.editor_saturation
    Adjustment.Warmth -> R.string.editor_warmth
    Adjustment.Tint -> R.string.editor_tint
    Adjustment.Vignette -> R.string.editor_vignette
}

/** One slider, for whichever adjustment is picked in the row above it. Moved ones show their amount. */
@Composable
private fun AdjustPanel(edit: PhotoEdit, adjusting: Adjustment, onPick: (Adjustment) -> Unit, onEdit: (PhotoEdit) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (adjustment in Adjustment.entries) {
                val amount = (edit.level(adjustment) * 100).roundToInt()
                EditorChip(
                    stringResource(adjustmentLabel(adjustment)),
                    selected = adjustment == adjusting,
                    onClick = { onPick(adjustment) },
                    detail = if (amount != 0) (if (amount > 0) "+$amount" else "$amount") else null,
                )
            }
        }
        EditorSlider(
            value = edit.level(adjusting),
            onValueChange = { onEdit(edit.with(adjusting, it)) },
            min = adjusting.min,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@StringRes
private fun filterLabel(filter: PhotoFilter): Int = when (filter) {
    PhotoFilter.None -> R.string.editor_filter_none
    PhotoFilter.Vivid -> R.string.editor_filter_vivid
    PhotoFilter.Warm -> R.string.editor_filter_warm
    PhotoFilter.Cool -> R.string.editor_filter_cool
    PhotoFilter.Fade -> R.string.editor_filter_fade
    PhotoFilter.Mono -> R.string.editor_filter_mono
    PhotoFilter.Noir -> R.string.editor_filter_noir
    PhotoFilter.Sepia -> R.string.editor_filter_sepia
}

/** Every filter as a small copy of the photo itself, crop and adjustments included. */
@Composable
private fun FilterPanel(bitmap: Bitmap, edit: PhotoEdit, onEdit: (PhotoEdit) -> Unit) {
    // A small copy: eight of these are drawn at once.
    val small by produceState<Bitmap?>(null, bitmap) {
        value = withContext(Dispatchers.Default) {
            val scale = FilterSide.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (scale >= 1f) bitmap
            else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (filter in PhotoFilter.entries) {
            val selected = filter == edit.filter
            Column(
                Modifier.pressable({ onEdit(edit.copy(filter = filter)) }, pressedScale = 0.94f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val look = remember(edit, filter) { edit.copy(filter = filter) }
                val paint = remember(look.adjustments, filter) { PhotoEditRenderer.paint(look) }
                Canvas(
                    Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1C1C1E)),
                ) {
                    val source = small ?: return@Canvas
                    // The middle square of the cropped picture.
                    val (width, height) = look.outputSize(source.width, source.height)
                    val side = min(width, height).toFloat()
                    val crop = look.crop
                    val square = CropRect(
                        crop.left + crop.width * (1f - side / width) / 2f,
                        crop.top + crop.height * (1f - side / height) / 2f,
                        crop.right - crop.width * (1f - side / width) / 2f,
                        crop.bottom - crop.height * (1f - side / height) / 2f,
                    )
                    drawIntoCanvas { PhotoEditRenderer.draw(it.nativeCanvas, source, look, square, size.width, size.height, paint, finished = false) }
                    if (selected) drawRoundRect(EditorAccent, style = Stroke(3.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                }
                Text(
                    stringResource(filterLabel(filter)),
                    color = if (selected) EditorAccent else Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** The preview's longest side: sharp on any phone screen, small enough to redraw on every drag. */
private const val PreviewSide = 2048
private const val FilterSide = 256
private val PreviewPadding = 20.dp

/** How far from a corner or side of the crop box a touch still grabs it. */
private val HandleReach = 26.dp
private val PanelHeight = 132.dp
