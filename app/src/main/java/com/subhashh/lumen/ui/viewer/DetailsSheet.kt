package com.subhashh.lumen.ui.viewer

import com.subhashh.lumen.R
import com.subhashh.lumen.data.toRef
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.media3.common.Player
import androidx.compose.foundation.layout.heightIn
import com.subhashh.lumen.ai.Subtitles
import com.subhashh.lumen.ai.SearchSuggestions
import com.subhashh.lumen.ai.ModelDownloads
import com.subhashh.lumen.ai.Cue
import com.subhashh.lumen.data.CaptionStore
import com.subhashh.lumen.data.AllMetadata
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import android.widget.Toast
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.subhashh.lumen.data.CameraInfo
import com.subhashh.lumen.data.GeoPoint
import com.subhashh.lumen.data.MediaDetails
import com.subhashh.lumen.data.MediaDetailsLoader
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaSource
import com.subhashh.lumen.data.PlaceLookup
import com.subhashh.lumen.data.RawExtensions
import com.subhashh.lumen.data.VideoInfo
import com.subhashh.lumen.ai.FaceStore
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.data.isRaw
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.map.MapPreview
import com.subhashh.lumen.ui.people.FaceAvatar
import com.subhashh.lumen.ui.transition.coverCell
import com.subhashh.lumen.ui.theme.LocalGlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Text and fill colours on the glass sheet. */
private data class SheetColors(val text: Color, val secondary: Color, val group: Color, val accent: Color)

@Composable
private fun sheetColors(): SheetColors {
    val glass = LocalGlassColors.current
    return SheetColors(glass.content, glass.contentSecondary, glass.selection, MaterialTheme.colorScheme.primary)
}

/**
 * Swipe-up details for one item, on a floating glass sheet that refracts the viewer behind it
 * ([backdrop]): when, who, the camera, the image or video inside, where, and the file.
 * [locationRefresh] bumps when location access is granted, so the details reload with
 * unredacted GPS. [people] is every person found, each with their cover photo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSheet(
    item: MediaItem,
    backdrop: Backdrop,
    locationRefresh: Int,
    people: List<Pair<Person, MediaItem>>,
    onOpenPerson: (Person) -> Unit,
    /** Lets a face grow into its person's page (and land back here on the way back). */
    coverTransition: com.subhashh.lumen.ui.transition.CoverTransition? = null,
    /** Settings → Photos & Videos → Maps: a map of the spot; off, its name and coordinates only. */
    showMaps: Boolean,
    /** Opens the full map of the spot (Maps on) or another app's (off). */
    onOpenMap: (GeoPoint) -> Unit,
    onAllowLocation: () -> Unit,
    onRename: (MediaItem) -> Unit,
    onAdjustDate: (MediaItem) -> Unit,
    /** Opens the caption editor with the current caption (null: none yet). */
    onEditCaption: (MediaItem, String?) -> Unit,
    /** Bumped after a caption is saved, to show it. */
    captionVersion: Int,
    /** A "Looks like" word was tapped: search the library for it. */
    onSearch: (String) -> Unit,
    /** The video's player, for live subtitles; null for photos. */
    player: Player? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = sheetColors()
    val glass = LocalGlassColors.current
    val details by produceState<MediaDetails?>(null, item.id, locationRefresh) {
        value = MediaDetailsLoader.load(context, item)
    }
    val inPhoto by produceState(emptyList<Pair<Long, Long?>>(), item.id) {
        value = withContext(Dispatchers.IO) { runCatching { FaceStore.of(context).peopleIn(item.id) }.getOrDefault(emptyList()) }
    }
    val shown = remember(inPhoto, people) {
        val byId = people.associateBy { it.first.id }
        inPhoto.mapNotNull { (id, _) -> byId[id] }.filterNot { it.first.hidden }
    }
    // Their pages' header photos, ready before a tap, so the face flies the moment it's touched.
    LaunchedEffect(shown) {
        if (coverTransition == null) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        for ((person, cover) in shown) {
            val crop = if (person.coverInVideo) FaceStore.cropFile(context, person.coverFaceId) else null
            loader.enqueue(com.subhashh.lumen.ui.transition.heroRequest(context, com.subhashh.lumen.ui.transition.HeroCover("", cover, crop = crop)))
        }
    }

    Column(
        modifier
            .padding(horizontal = 8.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .glass(
                backdrop,
                RoundedCornerShape(32.dp),
                blurRadius = 24.dp,
                refractionHeight = 20.dp,
                refractionAmount = 40.dp,
                tint = glass.fallbackTint.copy(alpha = 0.55f),
            )
            .clip(RoundedCornerShape(32.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
    ) {
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.4f)),
        )

        val d = details
        WhenHeader(item, d, colors, onAdjustDate)
        val caption by produceState<String?>(null, item.id, captionVersion) {
            value = withContext(Dispatchers.IO) { CaptionStore.of(context).get(item.id) }
        }
        CaptionRow(caption, colors) { onEditCaption(item, caption) }
        Spacer(Modifier.height(14.dp))

        if (shown.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            ) {
                for ((person, cover) in shown) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(98.dp)
                            .pressable({ onOpenPerson(person) }, pressedScale = 0.94f),
                    ) {
                        FaceAvatar(
                            person, cover,
                            Modifier
                                .size(90.dp)
                                .then(
                                    if (coverTransition == null) Modifier
                                    else Modifier.coverCell(coverTransition, com.subhashh.lumen.ui.transition.viewerFaceKey(com.subhashh.lumen.data.CollectionKeys.person(person.id)), circle = true),
                                ),
                        )
                        Text(
                            person.name ?: stringResource(R.string.viewer_unnamed),
                            color = if (person.name == null) colors.secondary else colors.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (d == null) return@Column
        d.camera?.let { camera ->
            Group(colors) { CameraGroup(item, camera, colors) }
            Spacer(Modifier.height(10.dp))
        }
        Group(colors) { if (item.isVideo) VideoGroup(item, d, colors) else ImageGroup(item, d, colors) }

        when {
            d.location != null -> {
                Spacer(Modifier.height(10.dp))
                Group(colors) { LocationGroup(d, d.location, showMaps, colors, onOpenMap) }
            }
            !d.locationReadable -> {
                Spacer(Modifier.height(10.dp))
                Group(colors) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LumenIcons.Location, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.viewer_allow_location),
                            color = colors.secondary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Action(stringResource(R.string.common_allow), colors, onAllowLocation)
                    }
                }
            }
        }

        WhatLumenSees(item, colors, onSearch, player)

        Spacer(Modifier.height(10.dp))
        Group(colors) { FileGroup(item, d, colors, onRename) }

        Spacer(Modifier.height(10.dp))
        Group(colors) { AllMetadataGroup(item, d.locationReadable, colors) }
    }
}

/** The caption, or a prompt to add one; tap to edit. */
@Composable
private fun CaptionRow(caption: String?, colors: SheetColors, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pressable(onEdit, pressedScale = 0.98f).padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            caption ?: stringResource(R.string.viewer_add_caption),
            color = if (caption == null) colors.secondary else colors.text,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(LumenIcons.Edit, contentDescription = stringResource(R.string.viewer_edit_caption), tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

/**
 * What Lumen's models found: the everyday words the item stands out for (tap one to search it),
 * and a video's subtitles once they've been made with CC. Hidden when there's neither.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatLumenSees(item: MediaItem, colors: SheetColors, onSearch: (String) -> Unit, player: Player?) {
    val context = LocalContext.current
    val looks by produceState(emptyList<String>(), item.id) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                ModelDownloads.installed(context)?.let { SearchSuggestions.looksLike(context, it, item.id) }
            }.getOrNull().orEmpty()
        }
    }
    val cues by produceState(emptyList<Cue>(), item.id) {
        if (item.isVideo) value = withContext(Dispatchers.IO) { Subtitles.saved(context, item.toRef())?.first.orEmpty() }
    }
    if (looks.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Group(colors) {
            Line(stringResource(R.string.viewer_looks_like), colors, secondary = true)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (word in looks) {
                    Text(
                        word.replaceFirstChar { it.uppercase() },
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.secondary.copy(alpha = 0.15f))
                            .pressable({ onSearch(word) }, pressedScale = 0.94f)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
    if (cues.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Group(colors) { SubtitlesPart(item, cues, colors, player) }
    }
}

/**
 * A video's subtitles, made with CC, following the video: the line being spoken is lit and kept
 * in view (unless the list is being scrolled), and tapping a line plays from there. Copy or
 * export below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitlesPart(item: MediaItem, cues: List<Cue>, colors: SheetColors, player: Player?) {
    val context = LocalContext.current
    var position by remember(item.id) { mutableLongStateOf(-1L) }
    LaunchedEffect(player, item.id) {
        while (player != null) {
            position = player.currentPosition
            delay(200)
        }
    }
    // The line being spoken, or in a pause the last one spoken, until the next starts: the
    // highlight never drops out between lines.
    val active = cues.indexOfLast { it.startMs <= position }.takeIf { it >= 0 }
    val list = rememberLazyListState()
    LaunchedEffect(active) {
        if (active != null && !list.isScrollInProgress) list.animateScrollToItem((active - 1).coerceAtLeast(0))
    }
    Title(LumenIcons.Video, stringResource(R.string.viewer_subtitles), colors)
    Spacer(Modifier.height(8.dp))
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        itemsIndexed(cues) { i, cue ->
            val on = i == active
            // Fades between lines; the same weight throughout, so a long line never re-wraps.
            val lit by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "line")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.secondary.copy(alpha = 0.15f * lit))
                    .pressable({
                        player?.seekTo(cue.startMs)
                        player?.play()
                    }, pressedScale = 0.98f)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                val s = cue.startMs / 1000
                Text(
                    "%d:%02d".format(s / 60, s % 60),
                    color = colors.secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.width(44.dp),
                )
                Text(
                    cue.text,
                    color = colors.text.copy(alpha = if (active == null) 1f else 0.55f + 0.45f * lit),
                    fontSize = 14.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, action) in listOf<Pair<String, () -> Unit>>(
            stringResource(R.string.common_copy) to { copyText(context, cues.joinToString("\n") { it.text }) },
            stringResource(R.string.viewer_copy_with_times) to { copyText(context, Subtitles.timestamped(cues)) },
            stringResource(R.string.viewer_export_srt) to { exportSubtitles(context, item, Subtitles.srt(cues), "srt", "application/x-subrip") },
            stringResource(R.string.viewer_export_vtt) to { exportSubtitles(context, item, Subtitles.vtt(cues), "vtt", "text/vtt") },
        )) {
            Text(
                label,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.secondary.copy(alpha = 0.15f))
                    .pressable(action, pressedScale = 0.94f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Text", text))
    // Android 13+ confirms copies itself.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
    }
}

/** Writes the subtitles next to nothing else (Lumen's cache) and offers them to the share sheet. */
private fun exportSubtitles(context: Context, item: MediaItem, body: String, extension: String, mime: String) {
    val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(dir, item.name.substringBeforeLast('.').ifEmpty { "subtitles" } + ".$extension")
    file.writeText(body)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Every tag in the file, folded away until asked for. */
@Composable
private fun AllMetadataGroup(item: MediaItem, canReadLocation: Boolean, colors: SheetColors) {
    val context = LocalContext.current
    var open by remember(item.id) { mutableStateOf(false) }
    val tags by produceState(emptyList<Pair<String, String>>(), item.id, open) {
        if (open) value = AllMetadata.read(context, item, canReadLocation)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable({ open = !open }, pressedScale = 0.98f),
    ) {
        Text(stringResource(R.string.viewer_all_metadata), color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (open && tags.isNotEmpty()) {
            Action(stringResource(R.string.common_copy), colors) { copyText(context, tags.joinToString("\n") { (k, v) -> "$k: $v" }) }
        } else {
            Text(if (open) "" else stringResource(R.string.viewer_show), color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (open) {
        Spacer(Modifier.height(8.dp))
        if (tags.isEmpty()) Line(stringResource(R.string.viewer_nothing_readable), colors, secondary = true)
        for ((key, value) in tags) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(key, color = colors.secondary, fontSize = 12.sp, modifier = Modifier.weight(0.42f))
                Text(value, color = colors.text, fontSize = 12.sp, modifier = Modifier.weight(0.58f))
            }
        }
    }
}

/** Date, time, and the time zone it was taken in when EXIF says so. */
@Composable
private fun WhenHeader(item: MediaItem, d: MediaDetails?, colors: SheetColors, onAdjustDate: (MediaItem) -> Unit) {
    val here = Instant.ofEpochMilli(item.takenAt).atZone(ZoneId.systemDefault())
    // EXIF's offset: show the clock as it was where the photo was taken, and ours if it differs.
    val there = d?.offset?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        ?.let { Instant.ofEpochMilli(item.takenAt).atOffset(it) }
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val zone = DateTimeFormatter.ofPattern("OOOO", Locale.getDefault())
    val yourTime = stringResource(R.string.viewer_your_time)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(there ?: here),
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val line = buildString {
                append(time.format(there ?: here))
                if (there != null) {
                    append("  ·  ").append(zone.format(there))
                    if (there.offset != here.offset) append("  ·  ").append(yourTime.format(here.format(time)))
                }
            }
            Text(line, color = colors.secondary, fontSize = 15.sp)
        }
        Action(stringResource(R.string.viewer_adjust), colors) { onAdjustDate(item) }
    }
}

/** A rounded block on the sheet. */
@Composable
private fun Group(colors: SheetColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.group)
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun Title(icon: ImageVector?, text: String, colors: SheetColors, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Action(text: String, colors: SheetColors, onClick: () -> Unit) {
    Text(
        text,
        color = colors.accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressable(onClick, pressedScale = 0.94f).padding(4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(facts: List<String>, colors: SheetColors) {
    if (facts.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (fact in facts) {
            Text(
                fact,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.secondary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Line(text: String, colors: SheetColors, secondary: Boolean = false) {
    Text(text, color = if (secondary) colors.secondary else colors.text, fontSize = if (secondary) 13.sp else 14.sp)
}

@Composable
private fun CameraGroup(item: MediaItem, camera: CameraInfo, colors: SheetColors) {
    val title = camera.device?.let { if (item.isRaw) "$it · RAW" else it } ?: stringResource(R.string.viewer_camera)
    Title(LumenIcons.Camera, title, colors)
    val kind = cameraKind(camera)?.let { stringResource(it) }
    val lens = listOfNotNull(kind, camera.lens).joinToString("  ·  ")
    if (lens.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        Text(lens, color = colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 30.dp))
    }
    val chips = exposureFacts(LocalContext.current, camera)
    if (chips.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Chips(chips, colors)
    }
}

/** Which of the phone's cameras, from its lens name or 35 mm-equivalent focal length. */
private fun cameraKind(c: CameraInfo): Int? = when {
    c.lens?.contains("front", ignoreCase = true) == true -> R.string.viewer_camera_front
    c.focal35mm == null -> null
    c.focal35mm < 20 -> R.string.viewer_camera_ultra_wide
    c.focal35mm <= 35 -> R.string.viewer_camera_wide
    else -> R.string.viewer_camera_telephoto
}

@Composable
private fun ImageGroup(item: MediaItem, d: MediaDetails, colors: SheetColors) {
    val context = LocalContext.current
    Title(LumenIcons.Image, formatName(context, d.mimeType, false), colors)
    Spacer(Modifier.height(8.dp))
    val size = buildList {
        if (d.width > 0) {
            val mp = d.width.toLong() * d.height / 1_000_000.0
            if (mp >= 1) add(context.getString(R.string.viewer_megapixels, mp.roundToInt()))
            add("${d.width} × ${d.height}")
        }
        if (d.sizeBytes > 0) add(Formatter.formatShortFileSize(context, d.sizeBytes))
    }
    if (size.isNotEmpty()) Line(size.joinToString("  ·  "), colors)
    val chips = listOfNotNull(
        d.hdr,
        d.colorSpace,
        d.bitDepth?.let { stringResource(R.string.viewer_bit_depth, it) },
        stringResource(R.string.viewer_motion_photo).takeIf { d.motionPhoto },
        "RAW".takeIf { item.isRaw },
    )
    if (chips.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Chips(chips, colors)
    }
}

@Composable
private fun VideoGroup(item: MediaItem, d: MediaDetails, colors: SheetColors) {
    val context = LocalContext.current
    val v = d.video
    Title(LumenIcons.Video, listOfNotNull(v?.codec, formatName(context, d.mimeType, true)).joinToString(" · "), colors)
    Spacer(Modifier.height(8.dp))
    val main = buildList {
        if (item.durationMs > 0) add(formatDuration(item.durationMs))
        if (d.width > 0) add("${d.width} × ${d.height}")
        d.frameRate?.let { add("${it.roundToInt()} fps") }
        if (d.sizeBytes > 0) add(Formatter.formatShortFileSize(context, d.sizeBytes))
    }
    if (main.isNotEmpty()) Line(main.joinToString("  ·  "), colors)
    if (v != null) {
        audioLine(context, v)?.let {
            Spacer(Modifier.height(2.dp))
            Line(it, colors, secondary = true)
        }
        val chips = listOfNotNull(
            v.hdr,
            v.bitrate?.let { "%.1f Mbps".format(Locale.US, it / 1_000_000.0) },
            v.captureFps?.let { context.getString(R.string.viewer_slow_motion, it.roundToInt()) },
        )
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Chips(chips, colors)
        }
    }
}

private fun audioLine(context: Context, v: VideoInfo): String? {
    val codec = v.audioCodec ?: return null
    val channels = when (val count = v.audioChannels) {
        null -> null
        1 -> context.getString(R.string.viewer_audio_mono)
        2 -> context.getString(R.string.viewer_audio_stereo)
        6 -> "5.1"
        8 -> "7.1"
        else -> context.resources.getQuantityString(R.plurals.viewer_audio_channels, count, count)
    }
    val rate = v.sampleRate?.let { if (it % 1000 == 0) "${it / 1000} kHz" else "%.1f kHz".format(Locale.US, it / 1000.0) }
    return listOfNotNull(context.getString(R.string.viewer_audio, codec), channels, rate).joinToString("  ·  ")
}

@Composable
private fun LocationGroup(d: MediaDetails, location: GeoPoint, showMaps: Boolean, colors: SheetColors, onOpenMap: (GeoPoint) -> Unit) {
    val context = LocalContext.current
    val place by produceState<String?>(null, location) { value = PlaceLookup.describe(context, location) }
    if (showMaps) {
        MapPreview(
            location,
            onOpen = { onOpenMap(location) },
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Title(LumenIcons.Location, place ?: stringResource(R.string.viewer_location), colors)
    } else {
        Title(LumenIcons.Location, place ?: stringResource(R.string.viewer_location), colors) { Action(stringResource(R.string.viewer_open_in_maps), colors) { onOpenMap(location) } }
    }
    Spacer(Modifier.height(2.dp))
    val extra = listOfNotNull(
        formatCoordinates(context, location),
        d.altitude?.let { "${it.roundToInt()} m" },
        d.direction?.let { context.getString(R.string.viewer_facing, compass(context, it)) },
    )
    Text(extra.joinToString("  ·  "), color = colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 30.dp))
}

private fun compass(context: Context, degrees: Double): String {
    val points = listOf(
        R.string.viewer_compass_n, R.string.viewer_compass_ne, R.string.viewer_compass_e, R.string.viewer_compass_se,
        R.string.viewer_compass_s, R.string.viewer_compass_sw, R.string.viewer_compass_w, R.string.viewer_compass_nw,
    )
    return context.getString(points[((degrees % 360 + 360) % 360 / 45).roundToInt() % 8])
}

@Composable
private fun FileGroup(item: MediaItem, d: MediaDetails, colors: SheetColors, onRename: (MediaItem) -> Unit) {
    Title(null, d.fileName, colors) {
        Icon(
            LumenIcons.Edit,
            contentDescription = stringResource(R.string.common_rename),
            tint = colors.accent,
            modifier = Modifier.pressable({ onRename(item) }, pressedScale = 0.9f).padding(4.dp).size(20.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
    val where = listOfNotNull(d.volume, d.folder.trimEnd('/').ifEmpty { null }).joinToString("  ·  ").ifEmpty { stringResource(R.string.viewer_internal_storage) }
    Text(where, color = colors.secondary, fontSize = 13.sp)
    val from = stringResource(R.string.viewer_from)
    val added = stringResource(R.string.viewer_added)
    val modified = stringResource(R.string.viewer_modified)
    val chips = listOfNotNull(MediaSource.describe(item.relativePath, d.fileName)?.let { from.format(it) })
    val dates = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    val lines = listOfNotNull(
        d.dateAdded?.let { added.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dates)) },
        d.dateModified?.let { modified.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dates)) },
    )
    if (chips.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Chips(chips, colors)
    }
    if (lines.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        lines.forEach { Line(it, colors, secondary = true) }
    }
}

private fun exposureFacts(context: Context, c: CameraInfo) = buildList {
    c.iso?.let { add("ISO $it") }
    // The 35 mm equivalent says how wide the shot is; the real focal length means little on phones.
    (c.focal35mm?.let { "$it mm" } ?: c.focalLengthMm?.let { "${formatNumber(it)} mm" })?.let(::add)
    c.aperture?.let { add("ƒ${formatNumber(it)}") }
    c.exposure?.let { add(if (it < 1) "1/${(1 / it).roundToInt()} s" else "${formatNumber(it)} s") }
    c.exposureBias?.takeIf { abs(it) >= 0.05 }?.let { add("%+.1f EV".format(Locale.US, it)) }
    if (c.flashFired == true) add(context.getString(R.string.viewer_flash))
    if (c.whiteBalanceManual == true) add(context.getString(R.string.viewer_manual_wb))
}

private fun formatNumber(v: Double): String =
    if (abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else "%.1f".format(Locale.US, v)

private fun formatName(context: Context, mime: String, isVideo: Boolean): String {
    // RAW types look like image/x-adobe-dng or image/x-canon-cr2: show "DNG RAW", "CR2 RAW".
    if (!isVideo && mime.startsWith("image/x-") && mime.substringAfterLast('-') in RawExtensions) {
        return mime.substringAfterLast('-').uppercase() + " RAW"
    }
    val subtype = mime.substringAfter('/').uppercase()
    return when {
        subtype == "*" || subtype.isEmpty() -> context.getString(if (isVideo) R.string.viewer_video else R.string.viewer_photo)
        isVideo -> context.getString(R.string.viewer_format_video, subtype)
        else -> subtype
    }
}

private fun formatCoordinates(context: Context, p: GeoPoint): String {
    val lat = "%.4f° %s".format(Locale.US, abs(p.latitude), context.getString(if (p.latitude >= 0) R.string.viewer_compass_n else R.string.viewer_compass_s))
    val lon = "%.4f° %s".format(Locale.US, abs(p.longitude), context.getString(if (p.longitude >= 0) R.string.viewer_compass_e else R.string.viewer_compass_w))
    return "$lat, $lon"
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
