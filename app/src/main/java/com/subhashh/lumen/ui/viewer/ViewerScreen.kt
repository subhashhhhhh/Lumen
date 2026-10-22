package com.subhashh.lumen.ui.viewer

import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    onClose: (index: Int) -> Unit,
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
    val dismissOffset = remember { Animatable(0f) }

    // Everything was deleted out from under us.
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onClose(0) }

    BackHandler { onClose(pager.currentPage) }
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
        val progress = (abs(dismissOffset.value) / height).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(background.copy(alpha = 1f - (progress * 3f).coerceAtMost(1f)))
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    state = rememberDraggableState { delta ->
                        scope.launch { dismissOffset.snapTo(dismissOffset.value + delta) }
                    },
                    onDragStopped = { velocity ->
                        if (abs(dismissOffset.value) > height * DismissThreshold || abs(velocity) > 2500f) {
                            onClose(pager.currentPage)
                        } else {
                            dismissOffset.animateTo(0f)
                        }
                    },
                ),
        ) {
            HorizontalPager(
                state = pager,
                key = { items[it].id },
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed && dismissOffset.value == 0f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                val isCurrent = page == pager.currentPage
                val pageModifier = if (isCurrent) {
                    Modifier.graphicsLayer {
                        translationY = dismissOffset.value
                        val scale = 1f - progress * 0.4f
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    Modifier
                }
                val toggleChrome = { chromeVisible = !chromeVisible }
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
                    onBack = { onClose(pager.currentPage) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
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
                                icon = Icons.Rounded.RestoreFromTrash,
                                contentDescription = "Restore",
                                onClick = { launch(MediaActions.restoreRequest(context, current)) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = Icons.Rounded.DeleteForever,
                                contentDescription = "Delete forever",
                                tint = Color(0xFFFF453A),
                                onClick = { launch(MediaActions.deleteForeverRequest(context, current)) },
                            )
                        } else {
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = Icons.Rounded.Share,
                                contentDescription = "Share",
                                onClick = { MediaActions.share(context, current) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = if (current.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (current.isFavorite) Color(0xFFFF375F) else LocalGlassColors.current.content,
                                onClick = { launch(MediaActions.favoriteRequest(context, current)) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                onClick = { launch(MediaActions.trashRequest(context, current)) },
                            )
                        }
                    }
                }
            }
        }
    }
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
            icon = Icons.AutoMirrored.Rounded.ArrowBackIos,
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
            imageVector = if (playPause.showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
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
