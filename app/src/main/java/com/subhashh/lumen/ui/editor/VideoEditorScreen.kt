package com.subhashh.lumen.ui.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.edit.VideoEdit
import com.subhashh.lumen.data.edit.VideoEditSaver
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The video editor: drag the ends of the strip to trim, turn the picture, or take the sound out.
 * Save writes a new video beside the original, which is left as it is.
 */
@Composable
fun VideoEditorScreen(
    item: MediaItem,
    onDone: (message: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(VideoEdit()) }
    var choices by remember { mutableStateOf<List<EditorChoice>?>(null) }
    var export by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    // MediaStore's length until the player knows better (it can be missing or wrong there).
    var durationMs by remember { mutableLongStateOf(item.durationMs) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 16f / 9f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) durationMs = player.duration
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    val ratio = size.width * size.pixelWidthHeightRatio / size.height
                    aspect = if (size.unappliedRotationDegrees % 180 == 90) 1f / ratio else ratio
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(edit.muted) { player.volume = if (edit.muted) 0f else 1f }
    // Playback stays inside the kept part: at its end it stops, back at its start.
    val currentEdit by rememberUpdatedState(edit)
    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition
            val end = currentEdit.end(durationMs)
            if (player.isPlaying && durationMs > 0 && positionMs >= end) {
                player.pause()
                player.seekTo(currentEdit.startMs)
            }
            delay(40)
        }
    }

    val leave = {
        if (edit.isUnchanged) {
            onCancel()
        } else {
            player.pause()
            choices = listOf(
                EditorChoice(context.getString(R.string.editor_discard), Color(0xFFFF453A)) { onCancel() },
                EditorChoice(context.getString(R.string.editor_keep_editing)) { choices = null },
            )
        }
    }
    BackHandler {
        when {
            export != null -> Unit
            choices != null -> choices = null
            else -> leave()
        }
    }
    val save = {
        player.pause()
        progress = 0f
        export = scope.launch {
            val ok = VideoEditSaver.saveCopy(context, item, edit) { progress = it }
            export = null
            if (ok) {
                onDone(context.getString(R.string.editor_saved_video))
            } else {
                android.widget.Toast.makeText(context, R.string.editor_save_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            EditorTopBar(changed = !edit.isUnchanged, onCancel = leave, onReset = { edit = VideoEdit() }, onSave = save)
            TurnedVideo(player, aspect, edit.quarterTurns, Modifier.weight(1f).fillMaxWidth().padding(16.dp))
            Text(
                stringResource(
                    R.string.editor_clip_range,
                    clock(edit.startMs, durationMs), clock(edit.end(durationMs), durationMs), clock(edit.end(durationMs) - edit.startMs, durationMs),
                ),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
            )
            TrimStrip(
                item = item,
                durationMs = durationMs,
                edit = edit,
                positionMs = positionMs,
                onStart = {
                    edit = edit.withStart(it, durationMs)
                    player.pause()
                    player.seekTo(edit.startMs)
                },
                onEnd = {
                    edit = edit.withEnd(it, durationMs)
                    player.pause()
                    player.seekTo(edit.end(durationMs))
                },
                onSeek = {
                    player.pause()
                    player.seekTo(it.coerceIn(edit.startMs, edit.end(durationMs)))
                },
                modifier = Modifier.padding(horizontal = StripInset),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            ) {
                EditorIconButton(
                    if (edit.muted) LumenIcons.Muted else LumenIcons.Sound,
                    stringResource(if (edit.muted) R.string.editor_sound_off else R.string.editor_sound_on),
                    onClick = { edit = edit.copy(muted = !edit.muted) },
                    active = edit.muted,
                )
                EditorIconButton(
                    if (playing) LumenIcons.Pause else LumenIcons.Play,
                    stringResource(if (playing) R.string.viewer_pause else R.string.viewer_play),
                    onClick = {
                        if (playing) {
                            player.pause()
                        } else {
                            // From the start of the kept part when the playhead is outside it or at its end.
                            val end = edit.end(durationMs)
                            if (player.currentPosition < edit.startMs || player.currentPosition >= end - 50) player.seekTo(edit.startMs)
                            player.play()
                        }
                    },
                )
                EditorIconButton(LumenIcons.RotateLeft, stringResource(R.string.editor_rotate), onClick = { edit = edit.rotatedLeft() })
            }
        }
        EditorChoices(choices, onDismiss = { choices = null })
        if (export != null) {
            EditorBusy(stringResource(R.string.editor_saving), progress, onCancel = {
                export?.cancel()
                export = null
            })
        }
    }
}

/** The player's picture, fitted into the space and turned by [quarterTurns] with a short spin. */
@Composable
private fun TurnedVideo(player: Player, aspect: Float, quarterTurns: Int, modifier: Modifier = Modifier) {
    // Counts turns without wrapping, so three lefts then a fourth keeps spinning left.
    var turns by remember { mutableStateOf(0) }
    LaunchedEffect(quarterTurns) {
        val now = ((turns % 4) + 4) % 4
        val step = (quarterTurns - now + 4) % 4
        turns += if (step == 3) -1 else step
    }
    val degrees by animateFloatAsState(turns * 90f, tween(220), label = "videoTurn")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sideways = quarterTurns % 2 == 1
        val shown = if (sideways) 1f / aspect else aspect
        val area = maxWidth.value / maxHeight.value
        val width = if (shown >= area) maxWidth else maxHeight * shown
        val height = if (shown >= area) maxWidth / shown else maxHeight
        // Laid out in the video's own shape, then turned: sideways, its width is the box's height.
        ContentFrame(
            player = player,
            // A TextureView turns with the layer; a SurfaceView wouldn't.
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .requiredSize(if (sideways) height else width, if (sideways) width else height)
                .rotate(degrees),
        )
    }
}

/**
 * Frames of the video in a row. The kept part is framed in the accent colour, with a thick bar at
 * each end to drag; the rest is dimmed. Dragging anywhere else moves the playhead.
 */
@Composable
private fun TrimStrip(
    item: MediaItem,
    durationMs: Long,
    edit: VideoEdit,
    positionMs: Long,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frames = remember(item) { mutableStateListOf<Bitmap?>().apply { repeat(FrameCount) { add(null) } } }
    LaunchedEffect(item, durationMs) {
        if (durationMs <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, item.uri)
                    for (i in 0 until FrameCount) {
                        if (!isActive) break
                        val at = durationMs * 1000 * i / FrameCount
                        val frame = retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FrameSide, FrameSide)
                        withContext(Dispatchers.Main) { frames[i] = frame }
                    }
                }
            }
        }
    }
    val currentEdit by rememberUpdatedState(edit)
    val currentDuration by rememberUpdatedState(durationMs)
    val reach = with(LocalDensity.current) { 28.dp.toPx() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            // The strip's ends are near the screen's edges, where a drag would be Android's Back gesture.
            .systemGestureExclusion()
            .pointerInput(item) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val duration = currentDuration
                    if (duration <= 0) return@awaitEachGesture
                    fun time(x: Float) = (x / size.width * duration).toLong().coerceIn(0L, duration)
                    val startX = currentEdit.startMs.toFloat() / duration * size.width
                    val endX = currentEdit.end(duration).toFloat() / duration * size.width
                    // Whichever end is nearer wins when both are in reach (a very short clip).
                    val nearStart = abs(down.position.x - startX) <= reach
                    val nearEnd = abs(down.position.x - endX) <= reach
                    val move: (Float) -> Unit = when {
                        nearStart && (!nearEnd || abs(down.position.x - startX) <= abs(down.position.x - endX)) -> { x -> onStart(time(x)) }
                        nearEnd -> { x -> onEnd(time(x)) }
                        else -> { x -> onSeek(time(x)) }
                    }
                    move(down.position.x)
                    drag(down.id) { change ->
                        move(change.position.x)
                        change.consume()
                    }
                }
            }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(Color(0xFF1C1C1E)),
    ) {
        val cell = size.width / FrameCount
        frames.forEachIndexed { index, frame ->
            if (frame == null) return@forEachIndexed
            // Each frame fills its cell, cropped from the middle.
            val scale = maxOf(cell / frame.width, size.height / frame.height)
            val width = (frame.width * scale).roundToInt()
            val height = (frame.height * scale).roundToInt()
            clipRect(cell * index, 0f, cell * (index + 1), size.height) {
                drawImage(
                    frame.asImageBitmap(),
                    dstOffset = IntOffset((cell * index + (cell - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
                    dstSize = IntSize(width, height),
                )
            }
        }
        if (durationMs <= 0) return@Canvas
        val startX = edit.startMs.toFloat() / durationMs * size.width
        val endX = edit.end(durationMs).toFloat() / durationMs * size.width
        val dim = Color.Black.copy(alpha = 0.65f)
        drawRect(dim, Offset.Zero, Size(startX, size.height))
        drawRect(dim, Offset(endX, 0f), Size(size.width - endX, size.height))
        val bar = 7.dp.toPx()
        val edge = 3.dp.toPx()
        val color = if (edit.startMs > 0 || edit.endMs != null) EditorAccent else Color.White
        drawRoundRect(color, Offset(startX + edge / 2, edge / 2), Size(endX - startX - edge, size.height - edge), CornerRadius(8.dp.toPx()), Stroke(edge))
        drawRoundRect(color, Offset(startX, 0f), Size(bar, size.height), CornerRadius(4.dp.toPx()))
        drawRoundRect(color, Offset(endX - bar, 0f), Size(bar, size.height), CornerRadius(4.dp.toPx()))
        val playX = (positionMs.toFloat() / durationMs * size.width).coerceIn(startX + bar, (endX - bar).coerceAtLeast(startX + bar))
        drawRoundRect(Color.White, Offset(playX - 1.5.dp.toPx(), 2.dp.toPx()), Size(3.dp.toPx(), size.height - 4.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

/** 0:07.4, 12:34, 1:02:03. Videos under a minute ([durationMs]) show tenths: there, they matter. */
private fun clock(ms: Long, durationMs: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        durationMs < 60_000 -> "%d:%02d.%d".format(minutes, seconds, ms.coerceAtLeast(0) % 1000 / 100)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

private const val FrameCount = 10
private const val FrameSide = 160
private val StripInset = 24.dp
