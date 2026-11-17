package com.subhashh.lumen.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.EmbeddingStore
import com.subhashh.lumen.ai.FaceModel
import com.subhashh.lumen.ai.FaceStore
import com.subhashh.lumen.ai.IndexProgress
import com.subhashh.lumen.ai.Indexer
import com.subhashh.lumen.ai.ModelDownloads
import com.subhashh.lumen.ai.OcrModel
import com.subhashh.lumen.ai.SmartIndex
import com.subhashh.lumen.ai.TextStore
import com.subhashh.lumen.ui.components.materialSurface
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How far one of Lumen's passes over the library has got. */
@Immutable
private class Pass(val icon: ImageVector, val name: Int, val progress: IndexProgress) {
    val left: Int get() = (progress.total - progress.indexed).coerceAtLeast(0)
}

/**
 * How far Lumen has got with what it works out from the library (search, text, faces), for the
 * features that are switched on: counted from what's saved, then live while a pass runs.
 * Empty when no Smart Feature is installed.
 */
@Composable
private fun indexPasses(): List<Pass> {
    val context = LocalContext.current
    val downloads by ModelDownloads.state.collectAsState()
    val liveSearch by SmartIndex.progress.collectAsState()
    val liveText by SmartIndex.textProgress.collectAsState()
    val liveFaces by SmartIndex.faceProgress.collectAsState()
    // Counted once off the main thread (and again when a download finishes): what's saved so
    // far. While a pass runs it reports as it goes, and that's shown instead, with no recount.
    val counted by produceState(emptyList<Pass>(), downloads) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val total = Indexer.eligible(context).size
                buildList {
                    ModelDownloads.installed(context)?.let { model ->
                        add(Pass(LumenIcons.Search, R.string.index_search, IndexProgress(EmbeddingStore.of(context, model).count(), total)))
                    }
                    if (OcrModel.installed(context)) add(Pass(LumenIcons.Document, R.string.index_text, IndexProgress(TextStore.of(context).count(), total)))
                    if (FaceModel.installed(context)) add(Pass(LumenIcons.Face, R.string.index_faces, IndexProgress(FaceStore.of(context).count(), total)))
                }
            }.getOrDefault(emptyList())
        }
    }
    return counted.map { pass ->
        val live = when (pass.name) {
            R.string.index_search -> liveSearch
            R.string.index_text -> liveText
            else -> liveFaces
        }
        if (live == null) pass else Pass(pass.icon, pass.name, live)
    }
}

/**
 * The Material style's indexing status, at the top left of Photos where Android galleries show
 * their backup's: a chip saying whether Lumen has caught up. Tapping it opens the status in the
 * page ([IndexShelf]) and leaves a round button here to fold it away again. Nothing at all when
 * no Smart Feature is installed.
 */
@Composable
fun IndexStatus(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val passes = indexPasses()
    if (passes.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val left = passes.maxOf { it.left }
    val done = left == 0
    Box(modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (open) {
            Box(
                Modifier
                    .size(48.dp)
                    .pressable(onToggle, pressedScale = 0.92f, shape = CircleShape)
                    .materialSurface(scheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // The chevron points left as drawn: turned to point up.
                Icon(LumenIcons.Chevron, stringResource(R.string.index_collapse), tint = scheme.onSurface, modifier = Modifier.size(24.dp).rotate(90f))
            }
        } else {
            // A tick once caught up, a ring filling while there's more to do.
            Row(
                Modifier
                    .height(48.dp)
                    .pressable(onToggle, pressedScale = 0.96f, shape = CircleShape)
                    .materialSurface(scheme.surfaceContainerHigh, CircleShape)
                    .padding(start = 14.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Icon(LumenIcons.Selected, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                } else {
                    val all = passes.sumOf { it.progress.total }.coerceAtLeast(1)
                    val fraction = passes.sumOf { minOf(it.progress.indexed, it.progress.total) }.toFloat() / all
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, trackColor = scheme.surfaceContainerHighest)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (done) stringResource(R.string.index_chip_done) else pluralStringResource(R.plurals.index_chip_left, left, left),
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The grid as a sheet while the status is open: cut off [top] px down, its top corners rounded. */
private data class SheetShape(val top: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = CornerRadius(radius)
        return Outline.Rounded(RoundRect(0f, top, size.width, size.height.coerceAtLeast(top), topLeftCornerRadius = r, topRightCornerRadius = r))
    }
}

/**
 * Photos with the indexing status above it. While [open], the status (a title, each pass's count,
 * a way to Smart Features) sits at the top of the page under the buttons and the grid ([content])
 * moves down beneath it as a sheet with rounded corners, as Android galleries show their backup's.
 * Back, or pulling the sheet up, folds it away. [top] is where the grid's first row starts.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IndexShelf(open: Boolean, top: Dp, onClose: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val amount = animateFloatAsState(if (open) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "indexShelf")
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // The sheet's edge sits a little above the grid's first row.
    val cut = with(density) { (top - 8.dp).toPx() }
    val gap = with(density) { 20.dp.toPx() }
    val radius = with(density) { 28.dp.toPx() }
    val page = scheme.background
    val behind = scheme.surfaceContainer
    BackHandler(enabled = open) { onClose() }
    val isOpen = androidx.compose.runtime.rememberUpdatedState(open)
    val close = androidx.compose.runtime.rememberUpdatedState(onClose)
    val pull = remember {
        object : NestedScrollConnection {
            // The drag that folded the status away: the rest of it, and its fling, don't scroll the grid.
            var folding = false

            // While it's open the grid doesn't scroll: a pull upwards folds the status away.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return if (folding || isOpen.value) available else Offset.Zero
                if (isOpen.value && available.y < 0f) {
                    folding = true
                    close.value()
                }
                return if (folding || isOpen.value) available else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val held = folding || isOpen.value
                folding = false
                return if (held) available else Velocity.Zero
            }
        }
    }
    Box(modifier.fillMaxSize().drawBehind { if (amount.value > 0f) drawRect(behind, alpha = amount.value.coerceIn(0f, 1f)) }) {
        if (open || amount.value > 0f) {
            val passes = indexPasses()
            val left = passes.maxOfOrNull { it.left } ?: 0
            val done = left == 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height }
                    .graphicsLayer { alpha = amount.value.coerceIn(0f, 1f) }
                    .padding(top = top, start = 16.dp, end = 16.dp),
            ) {
                Text(
                    if (done) stringResource(R.string.index_title_done) else pluralStringResource(R.plurals.index_title_left, left, left),
                    color = scheme.onSurface,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (pass in passes) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(page).padding(horizontal = 20.dp, vertical = 18.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(pass.icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(stringResource(pass.name), color = scheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(
                                    if (pass.left == 0) stringResource(R.string.smart_all_count, pass.progress.total)
                                    else stringResource(R.string.smart_count_of, pass.progress.indexed, pass.progress.total),
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                )
                            }
                            if (pass.left > 0) {
                                val fraction = pass.progress.indexed.toFloat() / pass.progress.total.coerceAtLeast(1)
                                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 40.dp))
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(page).padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text(
                            stringResource(if (done) R.string.index_note_done else R.string.index_note_left),
                            color = scheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        )
                        OutlinedButton(
                            onClick = {
                                onClose()
                                onOpenSettings()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                        ) { Text(stringResource(R.string.settings_category_smart_features), fontSize = 16.sp) }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                // Placed lower, not drawn lower, so a photo still opens from where its thumbnail is.
                .offset { IntOffset(0, (amount.value * (height + gap - cut)).roundToInt().coerceAtLeast(0)) }
                .graphicsLayer {
                    val a = amount.value.coerceIn(0f, 1f)
                    clip = a > 0f
                    shape = SheetShape(cut * a, radius * a)
                }
                .drawBehind { if (amount.value > 0f) drawRect(page) }
                .nestedScroll(pull),
        ) { content() }
    }
}
