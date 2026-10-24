package com.subhashh.lumen.ui.onboarding

import android.Manifest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import com.subhashh.lumen.data.MediaThumbnail
import com.subhashh.lumen.data.MediaItem
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.ai.DownloadNotification.megabytes
import com.subhashh.lumen.data.ai.FaceModel
import com.subhashh.lumen.data.ai.ModelCatalog
import com.subhashh.lumen.data.ai.ModelDownloads
import com.subhashh.lumen.data.ai.ModelPack
import com.subhashh.lumen.data.ai.OcrModel
import com.subhashh.lumen.data.ai.SmartIndex
import com.subhashh.lumen.data.ai.SpeechModel
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlin.math.cos
import kotlin.math.sin

/** The onboarding's steps, in order. */
private enum class Step { Welcome, Photos, Smart, Schedule, Done }

/**
 * First launch: what Lumen is, photo access, which Smart Features to download (they download in
 * the background, with a notification, so this stays quick), when to index, and in. [hasAccess]
 * and [onRequestAccess] are the app's media permission; [done] is the last screen's content
 * [photos] are the newest in the library: the last screen shows them, and [onFinish] hands
 * where each one is to the Library, which flies them into its grid ([Entrance]).
 */
@Composable
fun Onboarding(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    photos: List<MediaItem>,
    onFinish: (OnboardingExit) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    val backdrop = rememberLayerBackdrop()
    BackHandler(enabled = step != Step.Welcome) { step = Step.entries[step.ordinal - 1] }

    // Opaque, and every touch stays here: the Library is already built underneath.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        // Plain: the glass sits on black.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(380)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(380)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(220)))
            },
            label = "onboarding",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                when (s) {
                    Step.Welcome -> Welcome(backdrop) { step = Step.Photos }
                    Step.Photos -> Photos(backdrop, hasAccess, onRequestAccess) { step = Step.Smart }
                    Step.Smart -> Smart(backdrop) { step = Step.Schedule }
                    Step.Schedule -> Schedule(backdrop) { step = Step.Done }
                    Step.Done -> Finish(backdrop, photos, onFinish)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Title(title: String, subtitle: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp)
    Spacer(Modifier.height(24.dp))
}

/** The big glass button at the bottom of each step. */
@Composable
private fun PrimaryButton(backdrop: Backdrop, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glass(backdrop, CircleShape, tint = Color.White.copy(alpha = if (enabled) 0.28f else 0.1f))
            .pressable(onClick, enabled = enabled, pressedScale = 0.96f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().pressable(onClick, pressedScale = 0.96f).padding(14.dp),
    )
}

/** A glass card holding rows. */
@Composable
private fun Card(backdrop: Backdrop, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(backdrop, RoundedCornerShape(24.dp), blurRadius = 16.dp, tint = Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
private fun ColumnScope.Welcome(backdrop: Backdrop, onNext: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Image(
        painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally),
    )
    Text(
        "Lumen",
        color = Color.White,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Your photos, smarter.\nAnd they never leave your phone.",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(32.dp))
    Card(backdrop) {
        Item(LumenIcons.Lock, "No account", "Nothing to sign up for, nothing to sign in to")
        Item(LumenIcons.Shield, "No cloud", "Search, faces and text are worked out on this phone")
        Item(LumenIcons.Hide, "No Google", "No ads, no tracking, no Play Services")
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(backdrop, "Get Started", onClick = onNext)
}

@Composable
private fun ColumnScope.Photos(backdrop: Backdrop, hasAccess: Boolean, onRequestAccess: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // Special access comes back from system settings: look again.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val location = remember(refresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val allFiles = remember(refresh) { Environment.isExternalStorageManager() }
    val manage = remember(refresh) { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Title("Your Photos", "Lumen shows the photos and videos already on your phone. Nothing is uploaded.")
    Card(backdrop) {
        Item(LumenIcons.Library, "Photos and Videos", "Needed to show your library") {
            Toggle(hasAccess) { if (!hasAccess) onRequestAccess() }
        }
    }
    if (hasAccess) {
        Spacer(Modifier.height(16.dp))
        Text("Optional", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(backdrop) {
            Item(LumenIcons.Location, "Where Photos Were Taken", "For maps and place names") {
                Toggle(location) { if (!location) askLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }
            }
            Item(LumenIcons.Folder, "All Files", "Manage folders and hidden files, like Fossify") {
                Toggle(allFiles) { context.openSpecial(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Item(LumenIcons.Delete, "Media Management", "Delete and favourite without asking each time") {
                    Toggle(manage) { context.openSpecial(Settings.ACTION_REQUEST_MANAGE_MEDIA) }
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(backdrop, if (hasAccess) "Continue" else "Allow Access") { if (hasAccess) onNext() else onRequestAccess() }
}

@Composable
private fun Toggle(on: Boolean, onTurnOn: () -> Unit) {
    Switch(
        checked = on,
        onCheckedChange = { if (it) onTurnOn() },
        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759)),
    )
}

/** Opens a special-access page for Lumen in system settings. */
private fun Context.openSpecial(action: String) {
    val intent = Intent(action, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** Accurate on phones with plenty of memory (8 GB or more); Fast below that. */
private fun recommendedTier(context: Context) =
    if (ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem >= 7_000_000_000L) {
        ModelCatalog.Accurate
    } else {
        ModelCatalog.Fast
    }

@Composable
private fun ColumnScope.Smart(backdrop: Backdrop, onNext: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    val recommended = remember { recommendedTier(context) }
    var tier by rememberSaveable { mutableStateOf(recommended.id) }
    var search by rememberSaveable { mutableStateOf(true) }
    var text by rememberSaveable { mutableStateOf(true) }
    var faces by rememberSaveable { mutableStateOf(true) }
    var subtitles by rememberSaveable { mutableStateOf(true) }
    var wifiOnly by rememberSaveable { mutableStateOf(prefs.modelWifiOnly) }
    val model = ModelCatalog.byId(tier)
    val total = (if (search) model.bytes else 0) + (if (text) OcrModel.bytes else 0) +
        (if (faces) FaceModel.bytes else 0) + (if (subtitles) SpeechModel.bytes else 0)

    fun start() {
        prefs.searchModel = tier
        prefs.modelWifiOnly = wifiOnly
        listOfNotNull(
            ModelPack.Search.takeIf { search },
            ModelPack.Faces.takeIf { faces },
            ModelPack.Text.takeIf { text },
            ModelPack.Subtitles.takeIf { subtitles },
        ).forEach { ModelDownloads.start(context, it, wifiOnly) }
        onNext()
    }
    // The download's notification needs this on Android 13+; it downloads either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Title("Smart Features", "Find photos by what's in them, the words in them, and who's in them. The models download once, in the background.")
        Card(backdrop) {
            Item(LumenIcons.Search, "Smart Search", "“cat on a sofa”, “sunset at the beach” · ${megabytes(model.bytes)}") { Switch(search, { search = it }) }
            if (search) {
                Row(Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in ModelCatalog.all) {
                        val selected = m.id == tier
                        Text(
                            m.label + if (m == recommended) " · Recommended" else "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (selected) 0.25f else 0.08f))
                                .border(1.dp, Color.White.copy(alpha = if (selected) 0.5f else 0f), CircleShape)
                                .pressable({ tier = m.id }, pressedScale = 0.95f)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Item(LumenIcons.Document, "Text in Photos", "Search the words in screenshots and videos · ${megabytes(OcrModel.bytes)}") { Switch(text, { text = it }) }
            Item(LumenIcons.Image, "Faces", "Group photos by person · ${megabytes(FaceModel.bytes)}") { Switch(faces, { faces = it }) }
            Item(LumenIcons.Video, "Subtitles", "Make subtitles for videos with CC · ${megabytes(SpeechModel.bytes)}") { Switch(subtitles, { subtitles = it }) }
        }
        Spacer(Modifier.height(12.dp))
        Card(backdrop) {
            Item(LumenIcons.Settings, "Download on Wi-Fi Only", "Waits for Wi-Fi before downloading") { Switch(wifiOnly, { wifiOnly = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Downloaded from Hugging Face. Your photos are never uploaded; everything runs on this phone.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(backdrop, if (total > 0) "Download in Background · ${megabytes(total)}" else "Continue", enabled = true) {
        if (total == 0L) onNext()
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else start()
    }
    SecondaryButton("Not Now", onNext)
}

@Composable
private fun ColumnScope.Schedule(backdrop: Backdrop, onNext: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    var overnight by rememberSaveable { mutableStateOf(prefs.indexScheduleOn) }

    Title("When to Index", "Lumen looks through your photos once, in the background. Pick when.")
    for ((night, title, subtitle) in listOf(
        Triple(false, "While Charging", "Whenever your phone is plugged in"),
        Triple(true, "Overnight", "1:00 – 7:00 AM, plugged in or not. Change the hours in Settings"),
    )) {
        val selected = overnight == night
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .glass(backdrop, RoundedCornerShape(24.dp), blurRadius = 16.dp, tint = Color.White.copy(alpha = if (selected) 0.26f else 0.1f))
                .border(1.5.dp, Color.White.copy(alpha = if (selected) 0.7f else 0f), RoundedCornerShape(24.dp))
                .pressable({ overnight = night }, pressedScale = 0.97f)
                .padding(18.dp),
        ) {
            Column {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(backdrop, "Continue") {
        prefs.indexScheduleOn = overnight
        SmartIndex.schedule(context, replace = true)
        onNext()
    }
}

/**
 * A last-screen photo's image: exactly the Library grid's (same loader, size and cache key; a
 * video's first frame, not Android's pick), so the photo that lands in the grid is the one that
 * flew, and the grid's cell already has it.
 */
internal fun mosaicRequest(context: Context, item: MediaItem): coil3.request.ImageRequest {
    val metrics = context.resources.displayMetrics
    val bucket = com.subhashh.lumen.ui.images.ImageKeys.thumbBucket(metrics.widthPixels / LumenPrefs(context).gridColumns)
    val key = com.subhashh.lumen.ui.images.ImageKeys.thumb(item, bucket)
    return coil3.request.ImageRequest.Builder(context)
        .data(com.subhashh.lumen.data.GridThumbnail(item, bucket))
        .size(bucket, bucket)
        .memoryCacheKey(key)
        .placeholderMemoryCacheKey(key)
        .build()
}

/** A photo on the last screen: where it's laid out, and its tilt and drift right now. */
private class MosaicTile(val item: MediaItem) {
    var coordinates: LayoutCoordinates? = null
    var rotation = 0f
    var drift = 0f

    fun exit(): ExitTile? {
        val c = coordinates?.takeIf { it.isAttached } ?: return null
        val at = c.positionInRoot()
        return ExitTile(item, Rect(Offset(at.x, at.y + drift), Size(c.size.width.toFloat(), c.size.height.toFloat())), rotation)
    }
}

/**
 * The user's newest photos, loosely tilted and gently drifting, filling the space they get:
 * the last screen's welcome, and where the move into the Library starts from.
 */
@Composable
private fun Mosaic(tiles: List<MosaicTile>, modifier: Modifier = Modifier) {
    val wave by rememberInfiniteTransition(label = "mosaic").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val columns = 4
        val cell = maxWidth / columns
        val rows = (maxHeight / cell).toInt().coerceAtLeast(1)
        val shown = tiles.take(columns * rows)
        val top = (maxHeight - cell * ((shown.size + columns - 1) / columns)) / 2
        val random = remember(tiles) { java.util.Random(11) }
        val jitter = remember(tiles) { List(tiles.size) { Triple(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f) } }
        for ((i, tile) in shown.withIndex()) {
            val (jx, jy, jr) = jitter[i]
            coil3.compose.AsyncImage(
                model = remember(tile.item.id) { mosaicRequest(context, tile.item) },
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .offset(cell * (i % columns) + cell * 0.08f + cell * 0.12f * jx, top + cell * (i / columns) + cell * 0.08f + cell * 0.12f * jy)
                    .size(cell * 0.84f)
                    .onGloballyPositioned { tile.coordinates = it }
                    .graphicsLayer {
                        tile.rotation = 9f * jr + 2f * sin(wave + i)
                        tile.drift = 6.dp.toPx() * cos(wave + i * 0.7f)
                        rotationZ = tile.rotation
                        translationY = tile.drift
                    }
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun ColumnScope.Finish(backdrop: Backdrop, photos: List<MediaItem>, onFinish: (OnboardingExit) -> Unit) {
    val tiles = remember(photos) { photos.map { MosaicTile(it) } }
    val fade = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    var button by remember { mutableStateOf(Offset.Zero) }
    var leaving by remember { mutableStateOf(false) }
    Mosaic(tiles, Modifier.fillMaxWidth().weight(1f))
    Column(Modifier.graphicsLayer { alpha = fade.value }) {
        Spacer(Modifier.height(16.dp))
        Text("You're All Set", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Smart Features download in the background.\nYou'll see their progress in your notifications.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.onGloballyPositioned { button = it.boundsInRoot().center }) {
            PrimaryButton(backdrop, "Let's Go") {
                if (leaving) return@PrimaryButton
                leaving = true
                scope.launch {
                    // The words step aside first; the photos fly from where they are then.
                    fade.animateTo(0f, tween(120))
                    onFinish(OnboardingExit(tiles.mapNotNull { it.exit() }, button, Color.Black))
                }
            }
        }
    }
}
