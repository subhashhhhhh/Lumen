package com.subhashh.lumen.ui.viewer

import com.subhashh.lumen.ui.icons.LumenIcons
import android.app.Activity
import android.Manifest
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.data.MediaActions
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.theme.LocalGlassColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import androidx.media3.common.MediaItem as PlayerMediaItem

/** Fraction of the screen height a downward drag must cover to dismiss on release. */
private const val DismissThreshold = 0.15f

/** Release speed that counts as a flick. */
private val FlingVelocity = 400.dp // per second

/** Height of the details sheet as a fraction of the screen. */
private const val SheetFraction = 0.55f

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    onClose: (ViewerExit) -> Unit,
    onMediaChanged: () -> Unit,
    /** Viewing Recently Deleted: offer restore and delete forever instead of share/favorite/trash. */
    inTrash: Boolean = false,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = initialIndex) { items.size }
    val current = items.getOrNull(pager.currentPage)
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val flingVelocity = with(LocalDensity.current) { FlingVelocity.toPx() }
    val motion = remember(scope, flingVelocity) { ViewerMotion(scope, flingVelocity) }
    val sheetVisible by remember { derivedStateOf { motion.reveal > 0f } }
    var locationRefresh by remember { mutableIntStateOf(0) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) locationRefresh++ }

    // Everything was deleted out from under us.

    SystemBarsVisibility(visible = chromeVisible)

    val player = rememberViewerPlayer(current)

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onMediaChanged()
    }

    val background by animateColorAsState(
        if (chromeVisible) MaterialTheme.colorScheme.background else Color.Black,
        label = "viewerBackground",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val progress = (abs(motion.dismiss) / height).coerceIn(0f, 1f)
        val sheetHeight = height * SheetFraction
        motion.sheetHeight = sheetHeight
        // Status bar plus the top bar's back button row.
        val topInset = with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this) + 64.dp.toPx()
        }
        // Read inside graphicsLayer blocks so dragging redraws without recomposing.
        val infoFraction = { (motion.reveal / sheetHeight).coerceIn(0f, 1f) }
        val width = constraints.maxWidth.toFloat()

        // Where the current photo is on screen right now — fitted, then moved by any dismiss drag
        // or open details sheet — so the close transition can fly from exactly there.
        val close: (velocityY: Float) -> Unit = { velocityY ->
            val index = pager.currentPage
            val item = items.getOrNull(index)
            val rect = item?.let {
                val fitted = fittedHeight(it, width, height)
                val fraction = infoFraction()
                val available = (height - sheetHeight - topInset).coerceAtLeast(1f)
                val infoScale = 1f - (1f - (available / fitted).coerceAtMost(1f)) * fraction
                val infoShift = (topInset + available / 2f - height / 2f) * fraction
                val scale = (1f - progress * 0.4f) * infoScale
                val centerY = height / 2f + motion.dismiss + infoShift
                Rect(
                    left = width / 2f - width * scale / 2f,
                    top = centerY - fitted * scale / 2f,
                    right = width / 2f + width * scale / 2f,
                    bottom = centerY + fitted * scale / 2f,
                )
            }
            onClose(
                ViewerExit(
                    index = index,
                    photoRect = rect,
                    velocityY = velocityY,
                    background = background,
                    backgroundAlpha = 1f - (progress * 3f).coerceAtMost(1f),
                ),
            )
        }

        // Everything was deleted out from under us.
        LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) close(0f) }
        BackHandler { close(0f) }
        BackHandler(enabled = motion.infoOpen) { motion.closeInfo() }

        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(background.copy(alpha = 1f - (progress * 3f).coerceAtMost(1f)))
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    // Starting the drag from the touch-down point keeps the photo level with the
                    // finger instead of jumping by the touch slop.
                    startDragImmediately = sheetVisible,
                    state = rememberDraggableState(motion::onDrag),
                    onDragStarted = { motion.onDragStart() },
                    onDragStopped = { velocity ->
                        if (motion.onDragStop(velocity, height * DismissThreshold)) close(velocity)
                    },
                ),
        ) {
            HorizontalPager(
                state = pager,
                key = { items[it].id },
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed && motion.dismiss == 0f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                val isCurrent = page == pager.currentPage
                val pageModifier = if (isCurrent) {
                    Modifier.graphicsLayer {
                        // With details open, shrink the photo to fit the space between the top
                        // bar and the sheet, centered in it — like iOS.
                        val fitted = fittedHeight(item, size.width, size.height)
                        val available = (size.height - sheetHeight - topInset).coerceAtLeast(1f)
                        val fraction = infoFraction()
                        val infoScale = 1f - (1f - (available / fitted).coerceAtMost(1f)) * fraction
                        val infoShift = (topInset + available / 2f - size.height / 2f) * fraction
                        translationY = motion.dismiss + infoShift
                        val scale = (1f - progress * 0.4f) * infoScale
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    Modifier
                }
                val toggleChrome = {
                    if (motion.infoOpen) motion.closeInfo() else chromeVisible = !chromeVisible
                }
                if (item.isVideo) {
                    VideoPage(
                        item = item,
                        player = player.takeIf { isCurrent },
                        onTap = toggleChrome,
                        modifier = pageModifier,
                    )
                } else {
                    PhotoPage(
                        item = item,
                        isCurrent = isCurrent,
                        onZoomedChange = { zoomed = it },
                        onTap = toggleChrome,
                        modifier = pageModifier,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && progress == 0f && current != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (current == null) return@AnimatedVisibility
            Box(Modifier.fillMaxSize()) {
                TopBar(
                    backdrop = backdrop,
                    item = current,
                    onBack = { close(0f) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = 1f - infoFraction() }
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (current.isVideo) {
                        VideoControls(backdrop, player)
                        Spacer(Modifier.height(12.dp))
                    }
                    val launch = { sender: android.content.IntentSender ->
                        requestLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (inTrash) {
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Restore,
                                contentDescription = "Restore",
                                onClick = { launch(MediaActions.restoreRequest(context, current)) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.DeleteForever,
                                contentDescription = "Delete forever",
                                tint = Color(0xFFFF453A),
                                onClick = { launch(MediaActions.deleteForeverRequest(context, current)) },
                            )
                        } else {
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Share,
                                contentDescription = "Share",
                                onClick = { MediaActions.share(context, current) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = if (current.isFavorite) LumenIcons.HeartFilled else LumenIcons.Heart,
                                contentDescription = "Favorite",
                                tint = if (current.isFavorite) Color(0xFFFF375F) else LocalGlassColors.current.content,
                                onClick = { launch(MediaActions.favoriteRequest(context, current)) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Info,
                                contentDescription = "Details",
                                onClick = motion::openInfo,
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Delete,
                                contentDescription = "Delete",
                                onClick = { launch(MediaActions.trashRequest(context, current)) },
                            )
                        }
                    }
                }
            }
        }

        if (current != null && sheetVisible) {
            DetailsSheet(
                item = current,
                locationRefresh = locationRefresh,
                onOpenMap = { MediaActions.openInMaps(context, it) },
                onAllowLocation = { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(with(LocalDensity.current) { sheetHeight.toDp() })
                    .graphicsLayer { translationY = sheetHeight - motion.reveal }
                    .nestedScroll(motion.sheetScroll),
            )
        }
    }
}

/** How the viewer was left, so the grid can fly the photo back into place. */
data class ViewerExit(
    val index: Int,
    /** The photo's on-screen rect at the moment of closing; null if there's no item. */
    val photoRect: Rect?,
    /** Vertical finger speed at release (px/s); 0 for buttons and back. */
    val velocityY: Float,
    val background: Color,
    val backgroundAlpha: Float,
)

/** Where [item] sits when shown full screen in a width × height box (fitted, centered). */
fun fullScreenRect(item: MediaItem, width: Float, height: Float): Rect {
    val h = fittedHeight(item, width, height)
    val top = (height - h) / 2f
    return Rect(0f, top, width, top + h)
}

/** On-screen height of [item] when fit inside a width × height box; the box height if unknown. */
private fun fittedHeight(item: MediaItem, boxWidth: Float, boxHeight: Float): Float {
    if (item.width <= 0 || item.height <= 0) return boxHeight
    return (boxWidth * item.height / item.width).coerceAtMost(boxHeight)
}


@Composable
private fun TopBar(
    backdrop: Backdrop,
    item: MediaItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        GlassIconButton(
            backdrop = backdrop,
            icon = LumenIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .glass(backdrop, shape = RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (day, time) = remember(item.takenAt) { formatTakenAt(item.takenAt) }
            Text(day, color = colors.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(time, color = colors.contentSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VideoControls(backdrop: Backdrop, player: Player) {
    val colors = LocalGlassColors.current
    val playPause = rememberPlayPauseButtonState(player)
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(100)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .glass(backdrop, shape = RoundedCornerShape(50))
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (playPause.showPlay) LumenIcons.Play else LumenIcons.Pause,
            contentDescription = if (playPause.showPlay) "Play" else "Pause",
            tint = colors.content,
            modifier = Modifier
                .size(44.dp)
                .padding(10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = playPause::onClick,
                ),
        )
        Text(formatClock(position), color = colors.content, fontSize = 12.sp)
        Scrubber(
            progress = if (duration > 0) position.toFloat() / duration else 0f,
            onScrub = { fraction ->
                if (!scrubbing) {
                    scrubbing = true
                    // Snap to keyframes while dragging so the preview keeps up.
                    (player as? ExoPlayer)?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                }
                position = (fraction * duration).toLong()
                player.seekTo(position)
            },
            onScrubEnd = {
                (player as? ExoPlayer)?.setSeekParameters(SeekParameters.EXACT)
                player.seekTo(position)
                scrubbing = false
            },
            color = colors.content,
            trackColor = colors.contentSecondary.copy(alpha = 0.3f),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text("-" + formatClock(duration - position), color = colors.content, fontSize = 12.sp)
    }
}

/** One player for the whole viewer, re-pointed at whichever page is current. */
@Composable
private fun rememberViewerPlayer(current: MediaItem?): Player {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(current?.id) {
        if (current?.isVideo == true) {
            player.setMediaItem(PlayerMediaItem.fromUri(current.uri))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
            player.clearMediaItems()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    return player
}

/** Immersive mode while the chrome is hidden, like iOS Photos. */
@Composable
private fun SystemBarsVisibility(visible: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private fun formatTakenAt(millis: Long): Pair<String, String> {
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (dateTime.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
    val time = dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return day to time
}

private fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
