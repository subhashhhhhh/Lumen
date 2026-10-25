package com.subhashh.lumen.ui.viewer

import com.subhashh.lumen.data.toRef
import androidx.compose.ui.unit.IntOffset

import androidx.compose.foundation.layout.offset

import androidx.compose.ui.layout.onSizeChanged

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.subhashh.lumen.data.vault.VaultDataSource
import com.subhashh.lumen.ui.theme.LocalMotion
import android.view.WindowManager
import com.subhashh.lumen.data.HdrMode
import com.subhashh.lumen.data.FilmStripSize
import android.os.Build
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import com.subhashh.lumen.data.UltraHdr
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableFloatStateOf
import com.subhashh.lumen.data.LumenPrefs
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
import com.subhashh.lumen.data.MediaFiles
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
import androidx.compose.ui.draw.clip
import androidx.media3.common.MediaItem as PlayerMediaItem

/** Fraction of the screen height a downward drag must cover to dismiss on release. */
private const val DismissThreshold = 0.15f

private const val SlideshowFadeMs = 350
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
    /** Hidden section: Share, Info, Unhide and permanent Delete; no favorite or ⋯ menu. */
    inHidden: Boolean = false,
    onUnhide: (MediaItem) -> Unit = {},
    onDeleteHidden: (MediaItem) -> Unit = {},
    onCopyToAlbum: (MediaItem) -> Unit = {},
    onMoveToAlbum: (MediaItem) -> Unit = {},
    onRename: (MediaItem) -> Unit = {},
    onRotate: (MediaItem, degrees: Int) -> Unit = { _, _ -> },
    onExcludeFolder: (MediaItem) -> Unit = {},
    onHide: (MediaItem) -> Unit = {},
    onHideFolder: (MediaItem) -> Unit = {},
    /** Start a slideshow right away, limited to these ids (from a selection); null = don't. */
    slideshowOf: Set<Long>? = null,
    /** Opened by another app: Share and Info, and ⋯ with only what works on someone else's file. */
    external: Boolean = false,
    /** Viewing a folder album: ⋯ offers Make Album Cover (a person's photos: [coverLabel]). */
    onMakeCover: ((MediaItem) -> Unit)? = null,
    coverLabel: String = "Make Album Cover",
    /** Viewing a person's photos: ⋯ offers this ("Not Chiara") to take them out of this one. */
    notPerson: Pair<String, (MediaItem) -> Unit>? = null,
    onAdjustDate: (MediaItem) -> Unit = {},
    onSetWallpaper: (MediaItem) -> Unit = {},
    onResize: (MediaItem) -> Unit = {},
    /** Hidden's Share: encrypted items need a decrypted copy first. */
    onShareHidden: (MediaItem) -> Unit = {},
    /** Everyone Faces found, with cover photos: the details sheet shows who's in this one. */
    people: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>> = emptyList(),
    onOpenPerson: (com.subhashh.lumen.ai.Person) -> Unit = {},
    /** Live Text's Search: look for the selected words in Lumen. */
    onSearchText: (String) -> Unit = {},
    /** Faces in the details sheet grow into their person's page. */
    coverTransition: com.subhashh.lumen.ui.transition.CoverTransition? = null,
    /**
     * Kept, out of sight, under a person's page opened from the details sheet, to come back to
     * just as it was: video paused, HDR, brightness and system bars back to normal, Back left
     * to the page.
     */
    parked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = initialIndex) { items.size }
    val current = items.getOrNull(pager.currentPage)
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    /** The bottom controls' height (px, inside the navigation bar), so subtitles clear them. */
    var bottomChromeHeight by remember { mutableIntStateOf(0) }
    /** CC: subtitles made on the phone for videos, on until turned off again. */
    var captions by rememberSaveable { mutableStateOf(false) }
    val speechReady = remember { com.subhashh.lumen.ai.SpeechModel.installed(context) }
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
    // Settings → Photos & Videos → Maps, read each time the viewer opens.
    val showMaps = remember { com.subhashh.lumen.data.LumenPrefs(context).showMaps }
    // Settings → Photos & Videos → Film Strip.
    val filmStrip = remember { com.subhashh.lumen.data.LumenPrefs(context).filmStrip }
    // Live Text: the lines Text Reading kept for the photo, at once. A photo it hasn't read is
    // read here, once it settles (swiping past doesn't start reads); one it found no words in
    // isn't read at all.
    val liveText = remember { LiveTextState() }
    LaunchedEffect(current?.id, current?.modified) {
        val item = current?.takeIf { !it.isVideo }
        liveText.show(item, item?.let { com.subhashh.lumen.ai.LiveText.cached(it.toRef()) })
        if (item == null || liveText.result != null) return@LaunchedEffect
        val (stored, known) = com.subhashh.lumen.ai.LiveText.stored(context, item.toRef())
        if (stored != null || known == com.subhashh.lumen.ai.TextStore.Known.NoText) {
            liveText.show(item, stored)
            return@LaunchedEffect
        }
        if (known == com.subhashh.lumen.ai.TextStore.Known.NotRead) delay(300)
        liveText.show(item, com.subhashh.lumen.ai.LiveText.read(context, item.toRef()))
    }
    // The text models load as the viewer opens, not on its first photo, and stay a little after
    // it closes, so opening the next photo doesn't wait for them.
    DisposableEffect(Unit) {
        com.subhashh.lumen.ai.LiveText.warm(context)
        onDispose { com.subhashh.lumen.ai.LiveText.release() }
    }
    /** The caption being edited from the details sheet: the item and its caption so far. */
    var captionEdit by remember { mutableStateOf<Pair<MediaItem, String>?>(null) }
    var captionVersion by remember { mutableIntStateOf(0) }
    /** The spot shown on the full-screen map, from the details sheet; null when it's closed. */
    var mapOf by remember { mutableStateOf<com.subhashh.lumen.data.GeoPoint?>(null) }
    // Composition only cares whether the photo is displaced, not by how much.
    val atRest by remember { derivedStateOf { motion.dismissX == 0f && motion.dismissY == 0f } }
    var locationRefresh by remember { mutableIntStateOf(0) }
    var moreOpen by remember { mutableStateOf(false) }
    // Slideshow: hides the chrome and advances on a timer (Settings → Slideshow: interval, slide
    // or fade, shuffle, repeat); any tap stops it.
    var slideshow by remember { mutableStateOf(slideshowOf != null) }
    val reduceMotion = LocalMotion.current.reduce
    /** The pages' opacity, for the fade transition (always used with Reduce Motion). */
    val slideshowAlpha = remember { Animatable(1f) }
    LaunchedEffect(slideshow) {
        if (!slideshow) return@LaunchedEffect
        val prefs = LumenPrefs(context)
        chromeVisible = false
        val eligible = items.indices.filter { slideshowOf.isNullOrEmpty() || items[it].id in slideshowOf }
        // What's still to show: the rest in order, or the rest shuffled.
        fun queueAfter(page: Int): ArrayDeque<Int> = ArrayDeque(
            if (prefs.slideshowShuffle) eligible.filter { it != page }.shuffled() else eligible.filter { it > page },
        )
        var queue = queueAfter(pager.currentPage)
        try {
            while (true) {
                delay(prefs.slideshowSeconds * 1000L)
                if (queue.isEmpty()) {
                    if (!prefs.slideshowRepeat) break
                    queue = if (prefs.slideshowShuffle) queueAfter(pager.currentPage) else ArrayDeque(eligible)
                    if (queue.isEmpty()) break
                }
                val next = queue.removeFirst()
                if (prefs.slideshowFade || reduceMotion) {
                    slideshowAlpha.animateTo(0f, tween(SlideshowFadeMs))
                    pager.scrollToPage(next)
                    slideshowAlpha.animateTo(1f, tween(SlideshowFadeMs))
                } else {
                    pager.animateScrollToPage(next, animationSpec = tween(SlideshowTransitionMs))
                }
            }
        } finally {
            slideshowAlpha.snapTo(1f)
        }
        slideshow = false
        chromeVisible = true
    }

    // Ultra HDR photos get the window's HDR mode while they're on screen; nothing else does.
    val activity = LocalActivity.current
    /** The current photo's full-HDR ratio while it's shown in HDR; null for everything else. */
    var hdrRatio by remember { mutableStateOf<Float?>(null) }
    var hdrMode by remember { mutableStateOf(HdrMode.Off) }
    LaunchedEffect(current?.id) {
        hdrMode = LumenPrefs(context).hdrMode
        hdrRatio = if (current != null && hdrMode != HdrMode.Off) UltraHdr.fullHdrRatio(context, current) else null
    }
    // Full HDR turns the screen all the way up while an HDR photo or video is shown, like
    // Chrome: HDR needs brightness to work with. (desiredHdrHeadroom can only lower Android's
    // default, never raise the panel.) Your own brightness comes back as soon as it's gone.
    val fullBrightness = !parked && hdrMode == HdrMode.Full && (hdrRatio != null || current?.hdrVideo == true)
    val hdrShown = hdrRatio != null && !parked
    DisposableEffect(hdrShown, fullBrightness, activity) {
        val window = activity?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.colorMode = if (hdrShown) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        }
        window?.attributes = window?.attributes?.apply {
            screenBrightness = if (fullBrightness) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        onDispose {
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) locationRefresh++ }

    // Everything was deleted out from under us.

    SystemBarsVisibility(visible = chromeVisible || parked)

    val player = rememberViewerPlayer(current)
    LaunchedEffect(parked) {
        if (parked) {
            player?.pause()
            slideshow = false
        }
    }

    // The screen stays on while a video plays or a slideshow runs, then times out as usual.
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    // Live Text on a paused video: the frame on screen is read once the pause settles, and again
    // if a scrub lands somewhere else while paused; playing clears it.
    LaunchedEffect(current?.id, playing, player) {
        val item = current?.takeIf { it.isVideo } ?: return@LaunchedEffect
        val p = player ?: return@LaunchedEffect
        liveText.clear()
        liveText.show(item, null)
        if (playing) return@LaunchedEffect
        var readAt = -1L
        while (true) {
            delay(350)
            if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_BUFFERING) continue
            val at = p.currentPosition
            if (at == readAt) continue
            readAt = at
            liveText.clear()
            liveText.show(item, null)
            val result = com.subhashh.lumen.ai.LiveText.readVideoFrame(context, item.toRef(), at)
            if (p.currentPosition == at && !p.isPlaying) liveText.show(item, result)
        }
    }
    val view = LocalView.current
    DisposableEffect(playing, slideshow) {
        view.keepScreenOn = playing || slideshow
        onDispose { view.keepScreenOn = false }
    }

    // Favorite and delete go through Android's request screen, which pauses Lumen for a moment
    // (and so the video) even when it doesn't need to ask: a video that was playing carries on.
    var playingBeforeRequest by remember { mutableStateOf(false) }
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (playingBeforeRequest) player?.play()
        playingBeforeRequest = false
        if (result.resultCode == Activity.RESULT_OK) onMediaChanged()
    }

    val background by animateColorAsState(
        if (chromeVisible) MaterialTheme.colorScheme.background else Color.Black,
        label = "viewerBackground",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        // Lambdas, read only inside draw/layer blocks: a drag then redraws the photo without
        // recomposing the viewer (pager, image, chrome) on every frame.
        val progress = { (abs(motion.dismissY) / height).coerceIn(0f, 1f) }
        val sheetHeight = height * SheetFraction
        motion.sheetHeight = sheetHeight
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
                val key = ImageKeys.screen(item)
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
        // (the details sheet slides over it without moving it) — so the close transition can fly
        // from exactly there.
        val close: (velocity: Velocity) -> Unit = { velocity ->
            val index = pager.currentPage
            val item = items.getOrNull(index)
            val rect = item?.let {
                val fitted = fittedHeight(it, width, height)
                val scale = 1f - progress() * 0.4f
                // Same transform the page's graphicsLayer applies: scale about the pivot, then move.
                val origin = pageOrigin()
                val px = origin.pivotFractionX * width
                val py = origin.pivotFractionY * height
                val top = (height - fitted) / 2f
                val tx = motion.dismissX
                val ty = motion.dismissY
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
        BackHandler(enabled = !parked) { close(Velocity.Zero) }
        BackHandler(enabled = !parked && motion.infoOpen) { motion.closeInfo() }
        BackHandler(enabled = !parked && moreOpen) { moreOpen = false }
        BackHandler(enabled = !parked && (liveText.showAll || liveText.selection != null)) {
            if (liveText.selection != null) liveText.clear() else liveText.showAll = false
        }

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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = slideshowAlpha.value },
            ) { page ->
                val item = items[page]
                val isCurrent = page == pager.currentPage
                val pageModifier = if (isCurrent) {
                    Modifier.graphicsLayer {
                        // The details sheet slides up over the photo; the photo stays where it is.
                        transformOrigin = pageOrigin()
                        translationX = motion.dismissX
                        translationY = motion.dismissY
                        val scale = 1f - progress() * 0.4f
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
                        onZoomedChange = { zoomed = it },
                        liveText = if (isCurrent) liveText else null,
                        modifier = pageModifier,
                    )
                } else {
                    PhotoPage(
                        item = item,
                        isCurrent = isCurrent,
                        onZoomedChange = { zoomed = it },
                        onTap = toggleChrome,
                        liveText = if (isCurrent) liveText else null,
                        modifier = pageModifier,
                    )
                }
            }
        }

        if (captions && current?.isVideo == true && player != null && atRest) {
            SubtitleOverlay(
                item = current,
                player = player,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // Above the controls (their real height) when they're up, near the bottom when not.
                    .padding(
                        bottom = if (chromeVisible) with(LocalDensity.current) { bottomChromeHeight.toDp() } + 8.dp else 40.dp,
                        start = 24.dp,
                        end = 24.dp,
                    ),
            )
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
                    onMore = if (inTrash || inHidden) null else ({ moreOpen = !moreOpen }),
                    // Live Text's button: only when the photo has text.
                    liveTextOn = if (liveText.hasText && liveText.item?.id == current.id) liveText.showAll else null,
                    onLiveText = {
                        liveText.showAll = !liveText.showAll
                        liveText.clear()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = 1f - infoFraction() }
                        .navigationBarsPadding()
                        .onSizeChanged { bottomChromeHeight = it.height }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (current.isVideo) {
                        if (speechReady) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                CaptionsButton(backdrop, on = captions, onClick = { captions = !captions })
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (player != null) VideoControls(backdrop, player)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (items.size > 1 && !external && filmStrip != FilmStripSize.Off) {
                        FilmStrip(items, pager, filmStrip.heightDp.dp)
                        Spacer(Modifier.height(12.dp))
                    }
                    val launch = { sender: android.content.IntentSender ->
                        playingBeforeRequest = player?.isPlaying == true
                        requestLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (external) {
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Share,
                                contentDescription = "Share",
                                onClick = { MediaActions.share(context, current) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Info,
                                contentDescription = "Details",
                                onClick = motion::openInfo,
                            )
                        } else if (inHidden) {
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Share,
                                contentDescription = "Share",
                                onClick = { onShareHidden(current) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Info,
                                contentDescription = "Details",
                                onClick = motion::openInfo,
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.Unhide,
                                contentDescription = "Unhide",
                                onClick = { onUnhide(current) },
                            )
                            GlassIconButton(
                                backdrop = backdrop,
                                icon = LumenIcons.DeleteForever,
                                contentDescription = "Delete permanently",
                                tint = Color(0xFFFF453A),
                                onClick = { onDeleteHidden(current) },
                            )
                        } else if (inTrash) {
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
                                    if (MediaActions.setFavoriteDirectly(context, listOf(current), !current.isFavorite)) onMediaChanged()
                                    else launch(MediaActions.favoriteRequest(context, current))
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
                sections = if (external) externalMenu(context, current) else viewerMenu(
                    context = context,
                    item = current,
                    onCopyToAlbum = onCopyToAlbum,
                    onMoveToAlbum = onMoveToAlbum,
                    onRename = onRename,
                    onRotate = onRotate,
                    onExcludeFolder = onExcludeFolder,
                    onHide = onHide,
                    onHideFolder = onHideFolder,
                    onSlideshow = { slideshow = true },
                    onMakeCover = onMakeCover,
                    coverLabel = coverLabel,
                    notPerson = notPerson,
                    onAdjustDate = onAdjustDate,
                    onSetWallpaper = onSetWallpaper,
                    onResize = onResize,
                ),
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
                backdrop = backdrop,
                showMaps = showMaps,
                locationRefresh = locationRefresh,
                people = people,
                onOpenPerson = onOpenPerson,
                coverTransition = coverTransition,
                onRename = onRename,
                onAdjustDate = onAdjustDate,
                onEditCaption = { item, caption -> captionEdit = item to caption.orEmpty() },
                captionVersion = captionVersion,
                onSearch = { word ->
                    motion.closeInfo()
                    onSearchText(word)
                },
                player = player?.takeIf { current.isVideo },
                onOpenMap = { if (showMaps) mapOf = it else MediaActions.openInMaps(context, it) },
                onAllowLocation = { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(with(LocalDensity.current) { sheetHeight.toDp() })
                    .graphicsLayer { translationY = sheetHeight - motion.reveal }
                    .nestedScroll(motion.sheetScroll),
            )
        }
        // Live Text's menu: above the selection (below it when that's too close to the top), or
        // at the bottom with all text shown and nothing selected.
        if (current != null && liveText.item?.id == current.id && atRest && !sheetVisible) {
            val density = LocalDensity.current
            val bounds = liveText.selectionBounds
            val topLimit = with(density) { (WindowInsets.statusBars.getTop(this) + 72.dp.roundToPx()).toFloat() }
            var menuHeight by remember { mutableIntStateOf(0) }
            var menuWidth by remember { mutableIntStateOf(0) }
            LiveTextMenu(
                liveText,
                backdrop,
                onSearch = { query ->
                    liveText.clear()
                    onSearchText(query)
                },
                modifier = Modifier
                    .onSizeChanged {
                        menuHeight = it.height
                        menuWidth = it.width
                    }
                    .then(
                        if (bounds != null) {
                            Modifier.offset {
                                val gap = 12.dp.roundToPx()
                                val above = bounds.top - menuHeight - gap
                                val y = if (above > topLimit) above else bounds.bottom + gap + 24.dp.roundToPx()
                                val x = (bounds.center.x - menuWidth / 2f).coerceIn(8.dp.toPx(), (width - menuWidth - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
                                IntOffset(x.roundToInt(), y.roundToInt())
                            }
                        } else {
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
                        },
                    ),
            )
        }
        com.subhashh.lumen.ui.components.RenameDialog(
            currentName = captionEdit?.second,
            title = "Caption",
            confirm = "Save",
            keepExtension = false,
            freeText = true,
            onRename = { text ->
                val item = captionEdit?.first
                captionEdit = null
                if (item != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.subhashh.lumen.data.CaptionStore.of(context).set(item.id, text)
                        captionVersion++
                    }
                }
            },
            onDismiss = { captionEdit = null },
        )
        mapOf?.let { point ->
            com.subhashh.lumen.ui.map.PhotoMapScreen(
                point = point,
                title = null,
                onBack = { mapOf = null },
                onOpenInMaps = { MediaActions.openInMaps(context, point) },
            )
        }
    }
}

/** The viewer's ⋯ menu for [item]. Photo-only actions are left out for videos. */
private fun viewerMenu(
    context: Context,
    item: MediaItem,
    onCopyToAlbum: (MediaItem) -> Unit,
    onMoveToAlbum: (MediaItem) -> Unit,
    onRename: (MediaItem) -> Unit,
    onRotate: (MediaItem, degrees: Int) -> Unit,
    onExcludeFolder: (MediaItem) -> Unit,
    onHide: (MediaItem) -> Unit,
    onHideFolder: (MediaItem) -> Unit,
    onSlideshow: () -> Unit,
    onMakeCover: ((MediaItem) -> Unit)?,
    coverLabel: String,
    notPerson: Pair<String, (MediaItem) -> Unit>?,
    onAdjustDate: (MediaItem) -> Unit,
    onSetWallpaper: (MediaItem) -> Unit,
    onResize: (MediaItem) -> Unit,
): List<List<MenuItem>> {
    val photo = !item.isVideo
    return listOfNotNull(
        // A person's photos: their own options first.
        notPerson?.let { (label, action) ->
            listOfNotNull(
                onMakeCover?.let { MenuItem(coverLabel) { it(item) } },
                MenuItem(label) { action(item) },
            )
        },
        listOf(
            MenuItem("Move to Album…") { onMoveToAlbum(item) },
            MenuItem("Copy to Album…") { onCopyToAlbum(item) },
        ),
        listOfNotNull(
            MenuItem("Rename…") { onRename(item) },
            MenuItem("Adjust Date & Time…") { onAdjustDate(item) },
            if (photo) MenuItem("Resize or Convert…") { onResize(item) } else null,
            if (MediaFiles.canRotate(item)) rotateMenu { degrees -> onRotate(item, degrees) } else null,
        ),
        listOfNotNull(
            MenuItem("Slideshow") { onSlideshow() },
            if (notPerson == null) onMakeCover?.let { MenuItem(coverLabel) { it(item) } } else null,
        ),
        // Folder actions live on albums (Collections → long-press), not on a single photo.
        listOf(MenuItem("Hide") { onHide(item) }),
        listOfNotNull(
            MenuItem("Open with…") { MediaActions.openWith(context, item) },
            if (photo) MenuItem("Set as Wallpaper…") { onSetWallpaper(item) } else null,
            if (photo) MenuItem("Set as…") { MediaActions.setAs(context, item) } else null,
            MenuItem("Copy to clipboard") { MediaActions.copyToClipboard(context, item) },
        ),
        listOfNotNull(
            if (photo) MenuItem("Print") { MediaActions.print(context, item) } else null,
        ),
    )
}

/** ⋯ for a file another app opened in Lumen: hand-offs only, nothing that changes it. */
private fun externalMenu(context: Context, item: MediaItem): List<List<MenuItem>> = listOf(
    listOfNotNull(
        MenuItem("Open with…") { MediaActions.openWith(context, item) },
        if (!item.isVideo) MenuItem("Set as…") { MediaActions.setAs(context, item) } else null,
        MenuItem("Copy to clipboard") { MediaActions.copyToClipboard(context, item) },
    ),
    listOfNotNull(if (!item.isVideo) MenuItem("Print") { MediaActions.print(context, item) } else null),
)

/** "Rotate ›" with Left / Right / 180° inside, shared by the viewer and selection menus. */
fun rotateMenu(onRotate: (degrees: Int) -> Unit) = MenuItem(
    label = "Rotate",
    submenu = listOf(
        MenuItem("Rotate Left") { onRotate(270) },
        MenuItem("Rotate Right") { onRotate(90) },
        MenuItem("Rotate 180°") { onRotate(180) },
    ),
)

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
    /** Live Text's state when the photo has text (on: all text shown); null hides the button. */
    liveTextOn: Boolean? = null,
    onLiveText: () -> Unit = {},
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
        if (liveTextOn != null) {
            GlassIconButton(
                backdrop = backdrop,
                icon = if (liveTextOn) LumenIcons.LiveTextFilled else LumenIcons.LiveText,
                contentDescription = "Live Text",
                onClick = onLiveText,
                tint = if (liveTextOn) MaterialTheme.colorScheme.primary else colors.content,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (onMore != null) 56.dp else 0.dp),
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
    val context = LocalContext.current
    var muted by remember(player) { mutableStateOf(player.volume == 0f) }
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }

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
            .padding(start = 4.dp, end = 6.dp),
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
        // Speed cycles 1× → 1.25× → 1.5× → 2× → 0.5× and stays for the rest of the viewer.
        Text(
            formatSpeed(speed),
            color = colors.content,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 10.dp)
                .width(42.dp)
                .pressable({
                    val next = Speeds[(Speeds.indexOf(speed) + 1).mod(Speeds.size)]
                    player.setPlaybackSpeed(next)
                    speed = next
                }, pressedScale = 0.85f)
                .padding(vertical = 10.dp),
        )
        Icon(
            imageVector = if (muted) LumenIcons.Muted else LumenIcons.Sound,
            contentDescription = if (muted) "Unmute" else "Mute",
            tint = colors.content,
            modifier = Modifier
                .size(36.dp)
                .pressable({
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                    LumenPrefs(context).videoMuted = muted
                }, pressedScale = 0.8f)
                .padding(7.dp),
        )
    }
}

/** CC above the video controls, at the right: lit while subtitles are on. */
@Composable
private fun CaptionsButton(backdrop: Backdrop, on: Boolean, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    Box(
        Modifier
            .size(44.dp)
            .pressable(onClick, pressedScale = 0.85f)
            .glass(backdrop, shape = RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "CC",
            color = if (on) MaterialTheme.colorScheme.primary else colors.content,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The line being spoken now, made on the phone ([com.subhashh.lumen.ai.Subtitles]): the
 * part being watched first. "Making subtitles…" until the first lines arrive; "No speech" if
 * the video turns out to have none.
 */
@Composable
private fun SubtitleOverlay(item: MediaItem, player: Player, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cues by remember(item.id) { mutableStateOf<List<com.subhashh.lumen.ai.Cue>>(emptyList()) }
    var finished by remember(item.id) { mutableStateOf(false) }
    var failed by remember(item.id) { mutableStateOf(false) }
    var position by remember(item.id) { mutableLongStateOf(0L) }
    LaunchedEffect(item.id) {
        try {
            com.subhashh.lumen.ai.Subtitles.generate(context, item.toRef(), player.currentPosition).collect { cues = it }
            finished = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("LumenSubtitles", "Couldn't make subtitles for ${item.name}", e)
            failed = true
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            delay(100)
        }
    }
    val line = cues.lastOrNull { position >= it.startMs && position <= it.endMs }?.text
    val status = when {
        line != null -> null
        failed -> "Subtitles unavailable"
        finished && cues.isEmpty() -> "No speech"
        !finished && cues.none { it.startMs >= position - 30_000 && it.startMs <= position + 30_000 } -> "Making subtitles…"
        else -> null
    }
    val text = line ?: status ?: return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (line != null) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = if (line != null) 17.sp else 13.sp,
            fontWeight = if (line != null) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.5f)

private fun formatSpeed(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"

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
            // Read per video, so a change in Settings applies to the next one.
            val prefs = LumenPrefs(context)
            val active = p ?: ExoPlayer.Builder(context)
                // Vault videos decrypt as they play; everything else reads as usual.
                .setMediaSourceFactory(DefaultMediaSourceFactory(VaultDataSource.Factory(context)))
                .build()
                .also { player = it }
            active.repeatMode = if (prefs.videoLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            active.volume = if (prefs.videoMuted) 0f else 1f
            active.setMediaItem(PlayerMediaItem.fromUri(current.uri))
            active.prepare()
            active.playWhenReady = prefs.videoAutoplay
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
