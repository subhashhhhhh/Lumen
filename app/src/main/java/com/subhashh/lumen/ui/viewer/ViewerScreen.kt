package com.subhashh.lumen.ui.viewer

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.components.pressable
import android.app.Activity
import android.content.Context
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.components.MenuItem
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.subhashh.lumen.ui.images.ImageKeys
import kotlin.math.roundToInt
import android.os.Handler
import android.os.Looper
import android.Manifest
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.animation.core.tween
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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

private const val SlideshowIntervalMs = 3_000L
private const val SlideshowTransitionMs = 700

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
    onCopyToAlbum: (MediaItem) -> Unit = {},
    /** Start a slideshow right away, limited to these ids (from a selection); null = don't. */
    slideshowOf: Set<Long>? = null,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = initialIndex) { items.size }
    val current = items.getOrNull(pager.currentPage)
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val flingVelocity = with(LocalDensity.current) { FlingVelocity.toPx() }
    val haptics = LocalHapticFeedback.current
    val motion = remember(scope, flingVelocity) {
        ViewerMotion(scope, flingVelocity, onSheetSnap = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        })
    }
    var preloadNeighbors by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        preloadNeighbors = true
    }
    val sheetVisible by remember { derivedStateOf { motion.reveal > 0f } }
    // Composition only cares whether the photo is displaced, not by how much.
    val atRest by remember { derivedStateOf { motion.dismissX == 0f && motion.dismissY == 0f } }
    var locationRefresh by remember { mutableIntStateOf(0) }
    var moreOpen by remember { mutableStateOf(false) }
    // Slideshow: hides the chrome and advances every few seconds; any tap stops it.
    var slideshow by remember { mutableStateOf(slideshowOf != null) }
    LaunchedEffect(slideshow) {
        if (!slideshow) return@LaunchedEffect
        chromeVisible = false
        while (true) {
            delay(SlideshowIntervalMs)
            val next = (pager.currentPage + 1 until items.size)
                .firstOrNull { slideshowOf == null || slideshowOf.isEmpty() || items[it].id in slideshowOf }
            if (next == null) break
            pager.animateScrollToPage(next, animationSpec = tween(SlideshowTransitionMs))
        }
        slideshow = false
        chromeVisible = true
    }
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
        // Lambdas, read only inside draw/layer blocks: a drag then redraws the photo without
        // recomposing the viewer (pager, image, chrome) on every frame.
        val progress = { (abs(motion.dismissY) / height).coerceIn(0f, 1f) }
        val sheetHeight = height * SheetFraction
        motion.sheetHeight = sheetHeight
        // Status bar plus the top bar's back button row.
        val topInset = with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this) + 64.dp.toPx()
        }
        // Read inside graphicsLayer blocks so dragging redraws without recomposing.
        val infoFraction = { (motion.reveal / sheetHeight).coerceIn(0f, 1f) }
        val width = constraints.maxWidth.toFloat()

        // Keep screen-size decodes of the settled photo and its neighbors in memory: the close
        // flight then starts sharp even after swiping away from the photo that was tapped, and
        // swipes land on an already-sharp placeholder.
        LaunchedEffect(pager.settledPage, items) {
            val loader = SingletonImageLoader.get(context)
            for (i in pager.settledPage - 1..pager.settledPage + 1) {
                val item = items.getOrNull(i) ?: continue
                val key = ImageKeys.screen(item.id)
                if (loader.memoryCache?.get(MemoryCache.Key(key)) != null) continue
                val target = fullScreenRect(item, width, height)
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(target.width.roundToInt().coerceAtLeast(1), target.height.roundToInt().coerceAtLeast(1))
                        .memoryCacheKey(key)
                        .build(),
                )
            }
        }
        // Scale about the touch point while dismissing (photo stays under the thumb), else the center.
        val pageOrigin = {
            if (motion.dismissX != 0f || motion.dismissY != 0f) motion.pivot else TransformOrigin.Center
        }

        // Where the current photo is on screen right now — fitted, then moved by any dismiss drag
        // or open details sheet — so the close transition can fly from exactly there.
        val close: (velocity: Velocity) -> Unit = { velocity ->
            val index = pager.currentPage
            val item = items.getOrNull(index)
            val rect = item?.let {
                val fitted = fittedHeight(it, width, height)
                val fraction = infoFraction()
                val available = (height - sheetHeight - topInset).coerceAtLeast(1f)
                val infoScale = 1f - (1f - (available / fitted).coerceAtMost(1f)) * fraction
                val infoShift = (topInset + available / 2f - height / 2f) * fraction
                val scale = (1f - progress() * 0.4f) * infoScale
                // Same transform the page's graphicsLayer applies: scale about the pivot, then move.
                val origin = pageOrigin()
                val px = origin.pivotFractionX * width
                val py = origin.pivotFractionY * height
                val top = (height - fitted) / 2f
                val tx = motion.dismissX
                val ty = motion.dismissY + infoShift
                Rect(
                    left = px + (0f - px) * scale + tx,
                    top = py + (top - py) * scale + ty,
                    right = px + (width - px) * scale + tx,
                    bottom = py + (top + fitted - py) * scale + ty,
                )
            }
            onClose(
                ViewerExit(
                    index = index,
                    photoRect = rect,
                    velocity = velocity,
                    background = background,
                    backgroundAlpha = 1f - (progress() * 3f).coerceAtMost(1f),
                ),
            )
        }

        // Everything was deleted out from under us.
        LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) close(Velocity.Zero) }
        BackHandler { close(Velocity.Zero) }
        BackHandler(enabled = motion.infoOpen) { motion.closeInfo() }
        BackHandler(enabled = moreOpen) { moreOpen = false }

        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .drawBehind { drawRect(background, alpha = 1f - (progress() * 3f).coerceAtMost(1f)) }
                .viewerDrag(
                    motion = motion,
                    enabled = { !zoomed },
                    dismissDistance = { height * DismissThreshold },
                    onDismiss = close,
                ),
        ) {
            HorizontalPager(
                state = pager,
                key = { items[it].id },
                // Neighbors come a couple of frames after the first page, so the viewer's first
                // frame builds one page instead of three.
                beyondViewportPageCount = if (preloadNeighbors) 1 else 0,
                userScrollEnabled = !zoomed && !motion.dragging && atRest,
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
                        transformOrigin = pageOrigin()
                        translationX = motion.dismissX
                        translationY = motion.dismissY + infoShift
                        val scale = (1f - progress() * 0.4f) * infoScale
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    Modifier
                }
                val toggleChrome = {
                    when {
                        slideshow -> slideshow = false
                        motion.infoOpen -> motion.closeInfo()
                        else -> chromeVisible = !chromeVisible
                    }
                }
                if (item.isVideo) {
                    VideoPage(
                        item = item,
                        player = player?.takeIf { isCurrent },
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
            visible = chromeVisible && atRest && current != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (current == null) return@AnimatedVisibility
            Box(Modifier.fillMaxSize()) {
                TopBar(
                    backdrop = backdrop,
                    item = current,
                    onBack = { close(Velocity.Zero) },
                    // Recently Deleted items only get restore / delete forever.
                    onMore = if (inTrash) null else ({ moreOpen = !moreOpen }),
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
                        if (player != null) VideoControls(backdrop, player)
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
                                onClick = {
                                    haptics.performHapticFeedback(
                                        if (current.isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                    )
                                    launch(MediaActions.favoriteRequest(context, current))
                                },
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

        if (current != null) {
            GlassMenu(
                backdrop = backdrop,
                open = moreOpen && chromeVisible,
                onDismiss = { moreOpen = false },
                sections = viewerMenu(context, current, onCopyToAlbum, onSlideshow = { slideshow = true }),
                origin = TransformOrigin(1f, 0f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp),
            )
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

/** The viewer's ⋯ menu for [item]. Photo-only actions are left out for videos. */
private fun viewerMenu(
    context: Context,
    item: MediaItem,
    onCopyToAlbum: (MediaItem) -> Unit,
    onSlideshow: () -> Unit,
): List<List<MenuItem>> {
    val photo = !item.isVideo
    return listOf(
        listOf(MenuItem("Copy to Album…") { onCopyToAlbum(item) }),
        listOf(MenuItem("Slideshow") { onSlideshow() }),
        listOfNotNull(
            MenuItem("Open with…") { MediaActions.openWith(context, item) },
            if (photo) MenuItem("Set as…") { MediaActions.setAs(context, item) } else null,
            MenuItem("Copy to clipboard") { MediaActions.copyToClipboard(context, item) },
        ),
        listOfNotNull(
            if (photo) MenuItem("Print") { MediaActions.print(context, item) } else null,
        ),
    )
}

/** How the viewer was left, so the grid can fly the photo back into place. */
data class ViewerExit(
    val index: Int,
    /** The photo's on-screen rect at the moment of closing; null if there's no item. */
    val photoRect: Rect?,
    /** Finger speed at release (px/s); zero for buttons and back. */
    val velocity: Velocity,
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
    onMore: (() -> Unit)?,
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
        if (onMore != null) {
            GlassIconButton(
                backdrop = backdrop,
                icon = LumenIcons.More,
                contentDescription = "More",
                onClick = onMore,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        // Plain text like iOS, not a glass capsule. A soft glow in the background color keeps it
        // readable where a tall photo runs up behind it.
        val glow = Shadow(color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f), blurRadius = 10f)
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (day, time) = remember(item.takenAt) { formatTakenAt(item.takenAt) }
            Text(
                day,
                color = colors.content,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = glow),
            )
            Text(time, color = colors.contentSecondary, style = TextStyle(fontSize = 12.sp, shadow = glow))
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
                .pressable(playPause::onClick, pressedScale = 0.8f)
                .padding(10.dp),
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

/**
 * One player for the whole viewer, re-pointed at whichever page is current. Created only once a
 * video is actually shown (most opens are photos), and released a beat after the viewer closes:
 * ExoPlayer.release() blocks the main thread while its playback thread shuts down, which would
 * otherwise land on the first frames of the close animation.
 */
@Composable
private fun rememberViewerPlayer(current: MediaItem?): Player? {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            val p = player ?: return@onDispose
            p.pause()
            Handler(Looper.getMainLooper()).postDelayed({ p.release() }, PlayerReleaseDelayMs)
        }
    }
    LaunchedEffect(current?.id) {
        val p = player
        if (current?.isVideo == true) {
            val active = p ?: ExoPlayer.Builder(context).build()
                .apply { repeatMode = Player.REPEAT_MODE_ONE }
                .also { player = it }
            active.setMediaItem(PlayerMediaItem.fromUri(current.uri))
            active.prepare()
            active.playWhenReady = true
        } else if (p != null) {
            p.stop()
            p.clearMediaItems()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player?.pause() }
    return player
}

/** Long enough for the close animation to finish before the player's blocking teardown. */
private const val PlayerReleaseDelayMs = 700L

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
