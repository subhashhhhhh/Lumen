package com.subhashh.lumen.ui.library

import androidx.activity.compose.BackHandler
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
 * their backup's: a chip saying whether Lumen has caught up, which opens a panel with each
 * pass's count. Nothing at all when no Smart Feature is installed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IndexStatus(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val passes = indexPasses()
    if (passes.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val left = passes.maxOf { it.left }
    val done = left == 0
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    Box(modifier.fillMaxSize()) {
        // A tap anywhere else puts the panel away.
        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { open = false },
            )
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // The chip: a tick once caught up, a ring filling while there's more to do.
            Row(
                Modifier
                    .height(48.dp)
                    .pressable({ open = !open }, pressedScale = 0.96f, shape = CircleShape)
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
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .materialSurface(scheme.surfaceContainerLow, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .padding(16.dp),
                ) {
                    Text(
                        if (done) stringResource(R.string.index_title_done) else pluralStringResource(R.plurals.index_title_left, left, left),
                        color = scheme.onSurface,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp),
                    )
                    Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (pass in passes) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(pass.icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text(stringResource(pass.name), color = scheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        if (pass.left == 0) stringResource(R.string.smart_all_count, pass.progress.total)
                                        else stringResource(R.string.smart_count_of, pass.progress.indexed, pass.progress.total),
                                        color = scheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                    )
                                }
                                if (pass.left > 0) {
                                    val fraction = pass.progress.indexed.toFloat() / pass.progress.total.coerceAtLeast(1)
                                    LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 40.dp))
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(if (done) R.string.index_note_done else R.string.index_note_left),
                        color = scheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
                    )
                    TextButton(
                        onClick = {
                            open = false
                            onOpenSettings()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.settings_category_smart_features)) }
                }
            }
        }
    }
}
