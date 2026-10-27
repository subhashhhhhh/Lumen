package com.subhashh.lumen.ui.viewer

import com.subhashh.lumen.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ai.LiveText
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Live Text for the photo on screen: its text ([result]), what's selected, and whether all of
 * it is shown (the text button). The photo page draws it ([LiveTextLayer]); the viewer's chrome
 * shows Android's text toolbar ([LiveTextToolbar]). Characters are numbered through the whole photo, lines
 * joined by newlines, so a selection can run across lines.
 */
@Stable
class LiveTextState {
    var item by mutableStateOf<MediaItem?>(null)
        private set
    var result by mutableStateOf<LiveText.Result?>(null)
        private set
    /** The text button: every line lit up, the rest of the photo dimmed. */
    var showAll by mutableStateOf(false)
    /** Selected characters, start inclusive, end exclusive; null when nothing is. */
    var selection by mutableStateOf<IntRange?>(null)
    /** Where the selection is on screen (root coordinates), for the menu. */
    var selectionBounds by mutableStateOf<Rect?>(null)

    /** The whole photo's text, lines joined by newlines, and where each line starts in it. */
    val text: String get() = result?.lines?.joinToString("\n") { it.text }.orEmpty()

    fun lineStarts(): IntArray {
        val lines = result?.lines ?: return IntArray(0)
        val starts = IntArray(lines.size)
        var at = 0
        for (i in lines.indices) {
            starts[i] = at
            at += lines[i].text.length + 1
        }
        return starts
    }

    val hasText: Boolean get() = result?.lines?.isNotEmpty() == true

    fun show(item: MediaItem?, result: LiveText.Result?) {
        if (item?.id != this.item?.id) {
            showAll = false
            selection = null
            selectionBounds = null
        }
        this.item = item
        this.result = result
    }

    fun clear() {
        selection = null
        selectionBounds = null
    }

    val selectedText: String
        get() = selection?.let { r -> text.substring(r.first.coerceIn(0, text.length), (r.last + 1).coerceIn(0, text.length)) }.orEmpty()
}

/** A line's frame on screen: its top-left corner and its edges along and across the text. */
private class LineFrame(val origin: Offset, val along: Offset, val across: Offset) {
    fun at(s: Float, t: Float) = origin + along * s + across * t

    /** Where [p] is in the line: s along it, t across it (0–1 inside). */
    fun locate(p: Offset): Pair<Float, Float> {
        val d = p - origin
        val det = along.x * across.y - along.y * across.x
        if (det == 0f) return -1f to -1f
        val s = (d.x * across.y - d.y * across.x) / det
        val t = (along.x * d.y - along.y * d.x) / det
        return s to t
    }
}

private fun frames(result: LiveText.Result, bounds: Rect): List<LineFrame> {
    val sx = bounds.width / result.width
    val sy = bounds.height / result.height
    fun point(c: FloatArray, i: Int) = Offset(bounds.left + c[2 * i] * sx, bounds.top + c[2 * i + 1] * sy)
    return result.lines.map { line ->
        val tl = point(line.corners, 0)
        LineFrame(tl, point(line.corners, 1) - tl, point(line.corners, 3) - tl)
    }
}

/** The character nearest [p], as an index into [LiveTextState.text]; null if no line is near. */
private fun hit(state: LiveTextState, bounds: Rect, p: Offset, strict: Boolean): Int? {
    val result = state.result ?: return null
    val frames = frames(result, bounds)
    val starts = state.lineStarts()
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    for ((i, f) in frames.withIndex()) {
        val (s, t) = f.locate(p)
        val height = hypot(f.across.x, f.across.y)
        // Distance from the line's box, in pixels across it (lines are thin).
        val off = when {
            t < 0f -> -t * height
            t > 1f -> (t - 1f) * height
            else -> 0f
        } + when {
            s < 0f -> -s * hypot(f.along.x, f.along.y)
            s > 1f -> (s - 1f) * hypot(f.along.x, f.along.y)
            else -> 0f
        }
        if (strict && off > height * 0.4f) continue
        if (off < bestDistance) {
            val line = result.lines[i]
            val k = line.starts.indices.firstOrNull { s < line.ends[it] } ?: line.text.lastIndex
            bestDistance = off
            best = starts[i] + k.coerceAtLeast(0)
        }
    }
    return best
}

/** The word around [index]: letters, digits and the joiners in addresses and links. */
private fun wordAt(text: String, index: Int): IntRange {
    fun inWord(c: Char) = c.isLetterOrDigit() || c in "@._-/:%+#&=?'"
    if (index !in text.indices || !inWord(text[index])) return index..index
    var a = index
    var b = index
    while (a > 0 && inWord(text[a - 1])) a--
    while (b < text.lastIndex && inWord(text[b + 1])) b++
    return a..b
}

/** Long-press: select the word there. Returns false when there's no text under [p]. */
fun LiveTextState.selectAt(bounds: Rect, p: Offset): Boolean {
    val i = hit(this, bounds, p, strict = true) ?: return false
    selection = wordAt(text, i)
    return true
}

/** A tap with all text shown: a link, phone number or email under it opens. */
fun LiveTextState.openAt(context: Context, bounds: Rect, p: Offset): Boolean {
    val i = hit(this, bounds, p, strict = true) ?: return false
    val span = smartSpans(text).firstOrNull { i in it.first } ?: return false
    open(context, span.second, text.substring(span.first.first, span.first.last + 1))
    return true
}

private enum class Smart { Link, Phone, Email }

private fun smartSpans(text: String): List<Pair<IntRange, Smart>> = buildList {
    for ((pattern, kind) in listOf(Patterns.EMAIL_ADDRESS to Smart.Email, Patterns.WEB_URL to Smart.Link, Patterns.PHONE to Smart.Phone)) {
        val m = pattern.matcher(text)
        while (m.find()) {
            val r = m.start() until m.end()
            // Phone numbers need enough digits to not be a time or a price.
            if (kind == Smart.Phone && text.substring(r.first, r.last + 1).count { it.isDigit() } < 7) continue
            if (none { it.first.first <= r.last && r.first <= it.first.last }) add(r to kind)
        }
    }
}

private fun open(context: Context, kind: Smart, value: String) {
    val uri = when (kind) {
        Smart.Link -> (if ("://" in value) value else "https://$value").toUri()
        Smart.Phone -> "tel:${value.filter { it.isDigit() || it == '+' }}".toUri()
        Smart.Email -> "mailto:$value".toUri()
    }
    val action = if (kind == Smart.Phone) Intent.ACTION_DIAL else Intent.ACTION_VIEW
    runCatching { context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Drawn over the photo, inside its page, so it follows zoom, pan and swipe-to-dismiss.
 * [bounds] is where the photo is drawn right now. The selection's handles can be dragged.
 */
@Composable
fun LiveTextLayer(state: LiveTextState, bounds: Rect?, modifier: Modifier = Modifier) {
    val result = state.result
    if (result == null || bounds == null || bounds.isEmpty) return
    val context = LocalContext.current
    val view = LocalView.current
    // The system's own selection look: its highlight colour and handle drawables (the theme is DeviceDefault).
    val look = remember(context) { SelectionLook.of(context) }
    val selection = state.selection
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val frames = frames(result, bounds)
    val starts = state.lineStarts()
    val ends = if (selection != null) selectionEnds(state, frames, starts) else null
    // Tell the viewer where the selection is on screen, for its menu.
    SideEffect {
        val c = coords
        state.selectionBounds = if (ends != null && c != null && c.isAttached) {
            Rect(c.localToRoot(ends.second.topLeft), c.localToRoot(ends.second.bottomRight))
        } else {
            null
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { coords = it }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            fun quad(f: LineFrame, s0: Float, s1: Float, grow: Float = 0f) = Path().apply {
                val a = f.at(s0, -grow); val b = f.at(s1, -grow); val c = f.at(s1, 1f + grow); val d = f.at(s0, 1f + grow)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            if (state.showAll) {
                // The photo dims; each line stays clear, with a soft light edge.
                drawRect(Color.Black.copy(alpha = 0.45f), topLeft = bounds.topLeft, size = bounds.size)
                for (f in frames) {
                    drawPath(quad(f, 0f, 1f, 0.12f), Color.Black, blendMode = BlendMode.Clear)
                    drawPath(quad(f, 0f, 1f, 0.12f), Color.White.copy(alpha = 0.12f))
                }
            }
            if (selection != null) {
                for ((i, f) in frames.withIndex()) {
                    val line = result.lines[i]
                    val from = maxOf(selection.first, starts[i]) - starts[i]
                    val to = minOf(selection.last, starts[i] + line.text.length - 1) - starts[i]
                    if (from > to || from !in line.starts.indices || to !in line.ends.indices) continue
                    drawPath(quad(f, line.starts[from], line.ends[to], 0.08f), look.highlight)
                }
            }
        }
        if (selection != null && ends != null) {
            val magnifier = remember(view) { android.widget.Magnifier.Builder(view).build() }
            val toView = { p: Offset -> coords?.takeIf { it.isAttached }?.localToRoot(p) ?: p }
            // A point just inside the text from a handle's tip (tips sit on the line's bottom edge).
            val lift = with(LocalDensity.current) { 8.dp.toPx() }
            Handle(ends.first.first, start = true, look, magnifier, toView) { p ->
                hit(state, bounds, p - Offset(0f, lift), strict = false)?.let { i ->
                    if (i <= selection.last) state.selection = i..selection.last
                }
            }
            Handle(ends.first.second, start = false, look, magnifier, toView) { p ->
                hit(state, bounds, p - Offset(0f, lift), strict = false)?.let { i ->
                    if (i >= selection.first) state.selection = selection.first..i
                }
            }
        }
    }
}

/** Where the selection's handles point (the bottom of its first and last character), and the box around it. */
private fun selectionEnds(state: LiveTextState, frames: List<LineFrame>, starts: IntArray): Pair<Pair<Offset, Offset>, Rect>? {
    val result = state.result ?: return null
    val sel = state.selection ?: return null
    var start: Offset? = null
    var end: Offset? = null
    var box: Rect? = null
    for ((i, f) in frames.withIndex()) {
        val line = result.lines[i]
        val from = maxOf(sel.first, starts[i]) - starts[i]
        val to = minOf(sel.last, starts[i] + line.text.length - 1) - starts[i]
        if (from > to || from !in line.starts.indices || to !in line.ends.indices) continue
        if (start == null) start = f.at(line.starts[from], 1f)
        end = f.at(line.ends[to], 1f)
        val pts = listOf(f.at(line.starts[from], 0f), f.at(line.ends[to], 0f), f.at(line.ends[to], 1f), f.at(line.starts[from], 1f))
        val r = Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
        box = box?.let { Rect(minOf(it.left, r.left), minOf(it.top, r.top), maxOf(it.right, r.right), maxOf(it.bottom, r.bottom)) } ?: r
    }
    return if (start != null && end != null && box != null) (start to end) to box else null
}

/** The theme's text highlight colour and selection handles, as a TextView draws them. */
private class SelectionLook(val highlight: Color, val left: ImageBitmap, val right: ImageBitmap) {
    companion object {
        fun of(context: Context): SelectionLook {
            val a = context.obtainStyledAttributes(
                intArrayOf(android.R.attr.textColorHighlight, android.R.attr.textSelectHandleLeft, android.R.attr.textSelectHandleRight),
            )
            try {
                val highlight = Color(a.getColor(0, 0x6633B5E5))
                val left = checkNotNull(a.getDrawable(1)).toBitmap().asImageBitmap()
                val right = checkNotNull(a.getDrawable(2)).toBitmap().asImageBitmap()
                return SelectionLook(highlight, left, right)
            } finally {
                a.recycle()
            }
        }
    }
}

/**
 * A selection handle, the system's teardrop hanging below the text at [at]; dragged to move that
 * end, with the system magnifier over the text meanwhile (as in any text field).
 */
@Composable
private fun Handle(
    at: Offset,
    start: Boolean,
    look: SelectionLook,
    magnifier: android.widget.Magnifier,
    toView: (Offset) -> Offset,
    onDrag: (Offset) -> Unit,
) {
    val image = if (start) look.left else look.right
    // Where the drawable's tip is, as TextView places them: ¾ across for the start one, ¼ for the end.
    val hotspotX = if (start) image.width * 3 / 4f else image.width / 4f
    val current by rememberUpdatedState(at)
    val drag by rememberUpdatedState(onDrag)
    var dragAt = at
    val density = LocalDensity.current
    Image(
        image, null,
        Modifier
            .offset { IntOffset((at.x - hotspotX).roundToInt(), at.y.roundToInt()) }
            .size(with(density) { image.width.toDp() }, with(density) { image.height.toDp() })
            .pointerInput(start) {
                detectDragGestures(
                    onDragStart = { dragAt = current },
                    onDragEnd = { magnifier.dismiss() },
                    onDragCancel = { magnifier.dismiss() },
                ) { change, amount ->
                    change.consume()
                    dragAt += amount
                    drag(dragAt)
                    val p = toView(current - Offset(0f, 8.dp.toPx()))
                    magnifier.show(p.x, p.y)
                }
            },
    )
}

/**
 * Android's own floating text toolbar for the selection (the ActionMode every text field uses):
 * the text classifier's action for it (Open, Call, Email, Map… with the app's icon), Copy, Share,
 * Select All, Lumen's Search, and the apps that process text (Translate…). Cut and Paste don't
 * apply: text in a photo can't be edited. With all text shown and nothing selected: Copy All and
 * Select All, at the bottom. Dismissing it (back, tapping elsewhere) clears the selection.
 */
@Composable
fun LiveTextToolbar(state: LiveTextState, visible: Boolean, onSearch: (String) -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val selection = state.selection
    val show = visible && (selection != null || state.showAll)
    val selected = state.selectedText.trim()
    val classification by produceState<TextClassification?>(null, selected) {
        value = if (selected.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching {
                context.getSystemService(TextClassificationManager::class.java).textClassifier
                    .classifyText(TextClassification.Request.Builder(selected, 0, selected.length).build())
            }.getOrNull()
        }
    }
    val processors = remember(context) {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
            .filter { it.activityInfo.packageName != context.packageName }
    }
    val latest = rememberUpdatedState(Triple(selected, classification, onSearch))
    val bottomGap = with(LocalDensity.current) { 160.dp.roundToPx() }
    var mode by remember { mutableStateOf<ActionMode?>(null) }
    var closing by remember { mutableStateOf(false) }

    val callback = remember(state, view) {
        object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                val (text, smart, _) = latest.value
                val all = state.text
                if (state.selection == null) {
                    menu.add(Menu.NONE, ItemCopyAll, 0, R.string.viewer_copy_all)
                    menu.add(Menu.NONE, ItemSelectAll, 1, android.R.string.selectAll)
                    return true
                }
                smart?.actions?.firstOrNull()?.let { action ->
                    menu.add(Menu.NONE, ItemSmart, 0, action.title).apply {
                        if (action.shouldShowIcon()) icon = action.icon.loadDrawable(context)
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                }
                menu.add(Menu.NONE, ItemCopy, 1, android.R.string.copy)
                menu.add(Menu.NONE, ItemShare, 2, R.string.common_share)
                if (state.selection != 0..(all.length - 1)) menu.add(Menu.NONE, ItemSelectAll, 3, android.R.string.selectAll)
                menu.add(Menu.NONE, ItemSearch, 4, R.string.common_search)
                processors.forEachIndexed { i, info ->
                    menu.add(Menu.NONE, ItemProcess + i, 10 + i, info.loadLabel(context.packageManager)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                }
                return text.isNotEmpty()
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val (text, smart, search) = latest.value
                when (val id = item.itemId) {
                    ItemSmart -> runCatching { smart?.actions?.firstOrNull()?.actionIntent?.send() }
                    ItemCopy -> { copy(context, text); finish() }
                    ItemCopyAll -> { copy(context, state.text); finish() }
                    ItemShare -> {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        finish()
                    }
                    ItemSelectAll -> state.selection = 0..(state.text.length - 1).coerceAtLeast(0)
                    ItemSearch -> { finish(); search(text.take(80)) }
                    else -> processors.getOrNull(id - ItemProcess)?.let { info ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                                    .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                                    .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Closed by Android (back, a tap elsewhere): the selection goes too, as in any app.
                if (!closing) state.clear()
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                val b = state.selectionBounds
                if (b != null) {
                    outRect.set(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                } else {
                    val y = view.height - bottomGap
                    outRect.set(view.width / 2 - 1, y, view.width / 2 + 1, y + 1)
                }
            }

            private fun finish() {
                state.clear()
            }
        }
    }

    DisposableEffect(show) {
        if (show) {
            closing = false
            mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        }
        onDispose {
            closing = true
            mode?.finish()
            mode = null
        }
    }
    // The selection moved or grew, or the classifier answered: re-place and rebuild the toolbar.
    SideEffect {
        mode?.invalidateContentRect()
        mode?.invalidate()
    }
}

private const val ItemSmart = 1
private const val ItemCopy = 2
private const val ItemCopyAll = 3
private const val ItemShare = 4
private const val ItemSelectAll = 5
private const val ItemSearch = 6
private const val ItemProcess = 100

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Text", text))
    // Android 13+ shows its own confirmation.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
    }
}
