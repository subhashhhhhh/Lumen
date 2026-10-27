package com.subhashh.lumen.ui.onboarding

import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
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
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
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
import com.subhashh.lumen.ai.FaceModel
import com.subhashh.lumen.ai.ModelCatalog
import com.subhashh.lumen.ai.ModelDownloads
import com.subhashh.lumen.ai.ModelPack
import com.subhashh.lumen.ai.OcrModel
import com.subhashh.lumen.ai.SmartIndex
import com.subhashh.lumen.ai.SpeechModel
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlin.math.cos
import kotlin.math.sin

/** The onboarding's steps, in order. */
private enum class Step { Welcome, Look, Photos, Smart, Schedule, Done }

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
    /** Smart Features are downloading: without them there's nothing to index, so no When to Index. */
    var downloading by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()
    BackHandler(enabled = step != Step.Welcome) {
        step = if (step == Step.Done && !downloading) Step.Smart else Step.entries[step.ordinal - 1]
    }

    // Opaque, and every touch stays here: the Library is already built underneath. Being hit is
    // enough (a touch goes to the topmost sibling only); consuming the events too cancelled the
    // buttons' taps on screens that report tiny finger movements (Samsung, #1).
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
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
                    Step.Welcome -> Welcome(backdrop) { step = Step.Look }
                    Step.Look -> Look(backdrop) { step = Step.Photos }
                    Step.Photos -> Photos(backdrop, hasAccess, onRequestAccess) { step = Step.Smart }
                    Step.Smart -> Smart(backdrop) { started ->
                        downloading = started
                        step = if (started) Step.Schedule else Step.Done
                    }
                    Step.Schedule -> Schedule(backdrop) { step = Step.Done }
                    Step.Done -> Finish(backdrop, photos, downloading, onFinish)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Title(title: String, subtitle: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = ink.copy(alpha = 0.75f), fontSize = 17.sp)
    Spacer(Modifier.height(24.dp))
}

/** The big glass button at the bottom of each step. */
@Composable
private fun PrimaryButton(backdrop: Backdrop, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glass(backdrop, CircleShape, tint = glassTint(if (enabled) 0.28f else 0.1f))
            .pressable(onClick, enabled = enabled, pressedScale = 0.96f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = ink.copy(alpha = 0.8f),
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
            .glass(backdrop, RoundedCornerShape(24.dp), blurRadius = 16.dp, tint = glassTint(0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ink.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
private fun ColumnScope.Welcome(backdrop: Backdrop, onNext: () -> Unit) {
    // Centered, and scrolls when it's taller than the screen (small screens, large text).
    BoxWithConstraints(Modifier.weight(1f)) {
        val height = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = height), verticalArrangement = Arrangement.Center) {
                // The app icon as the home screen shows it: background, flare and the phone's icon shape.
                com.subhashh.lumen.ui.components.AppIcon(132.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.app_name),
                    color = ink,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_tagline),
                    color = ink.copy(alpha = 0.8f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
                Card(backdrop) {
                    Item(LumenIcons.Lock, stringResource(R.string.onboarding_no_account), stringResource(R.string.onboarding_no_account_detail))
                    Item(LumenIcons.Shield, stringResource(R.string.onboarding_no_cloud), stringResource(R.string.onboarding_no_cloud_detail))
                    Item(LumenIcons.Hide, stringResource(R.string.onboarding_no_google), stringResource(R.string.onboarding_no_google_detail))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(backdrop, stringResource(R.string.onboarding_get_started), onClick = onNext)
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
    val unrestricted = remember(refresh) {
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val activity = LocalActivity.current
    // Denied twice, Android answers at once without asking, and App Info has no switch for it
    // (it's the locations saved in photos, not the phone's): say so instead of doing nothing.
    var locationBlocked by rememberSaveable { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        locationBlocked = !granted && activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_MEDIA_LOCATION) == false
    }

    // Scrolls, with Continue fixed below: on small screens or with large text the list is taller
    // than the screen.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Title(stringResource(R.string.onboarding_photos_title), stringResource(R.string.onboarding_photos_subtitle))
        Card(backdrop) {
            Item(LumenIcons.Library, stringResource(R.string.onboarding_photos_videos), stringResource(R.string.onboarding_photos_videos_detail)) {
                Toggle(hasAccess) { if (!hasAccess) onRequestAccess() }
            }
        }
        if (hasAccess) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onboarding_optional), color = ink.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            Card(backdrop) {
                Item(
                    LumenIcons.Location,
                    stringResource(R.string.onboarding_location),
                    stringResource(if (locationBlocked && !location) R.string.onboarding_location_blocked else R.string.onboarding_location_detail),
                ) {
                    Toggle(location) { if (!location) askLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }
                }
                Item(LumenIcons.Folder, stringResource(R.string.onboarding_all_files), stringResource(R.string.onboarding_all_files_detail)) {
                    // On or off, it's the same system page (an app can't turn these off itself).
                    Toggle(allFiles, onTurnOff = { context.openSpecial(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) }) {
                        context.openSpecial(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Item(LumenIcons.Delete, stringResource(R.string.onboarding_media_management), stringResource(R.string.onboarding_media_management_detail)) {
                        Toggle(manage, onTurnOff = { context.openSpecial(Settings.ACTION_REQUEST_MANAGE_MEDIA) }) {
                            context.openSpecial(Settings.ACTION_REQUEST_MANAGE_MEDIA)
                        }
                    }
                }
                Item(LumenIcons.Timer, stringResource(R.string.onboarding_unrestricted_battery), stringResource(R.string.onboarding_unrestricted_battery_detail)) {
                    // An app can't remove its own exemption: turning it off opens Android's battery list.
                    Toggle(
                        unrestricted,
                        onTurnOff = { context.openSpecial(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                    ) { context.openSpecial(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(backdrop, if (hasAccess) stringResource(R.string.common_continue) else stringResource(R.string.onboarding_allow_access)) { if (hasAccess) onNext() else onRequestAccess() }
}

/** Appearance: theme and glass, taking effect as they're tapped (it's the same look Settings sets). */
@Composable
private fun ColumnScope.Look(backdrop: Backdrop, onNext: () -> Unit) {
    val appearance = com.subhashh.lumen.ui.theme.LocalAppearance.current
    val glass = com.subhashh.lumen.ui.theme.LocalGlass.current
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Title(stringResource(R.string.onboarding_look_title), stringResource(R.string.onboarding_look_subtitle))
        Card(backdrop) {
            Item(LumenIcons.DarkTheme, stringResource(R.string.onboarding_theme), stringResource(R.string.onboarding_theme_detail))
            Row(Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in com.subhashh.lumen.ui.theme.Appearance.entries) {
                    val selected = a == appearance.current
                    Text(
                        stringResource(a.labelRes),
                        color = ink,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ink.copy(alpha = if (selected) 0.25f else 0.08f))
                            .border(1.dp, ink.copy(alpha = if (selected) 0.5f else 0f), CircleShape)
                            .pressable({ appearance.onChange(a) }, pressedScale = 0.95f)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(backdrop) {
            Item(LumenIcons.Image, stringResource(R.string.onboarding_liquid_glass), stringResource(R.string.onboarding_liquid_glass_detail)) {
                Switch(glass.liquid, glass.onLiquid)
            }
            Item(LumenIcons.Hide, stringResource(R.string.onboarding_reduce_blur), stringResource(R.string.onboarding_reduce_blur_detail)) {
                Switch(glass.reduceBlur && glass.liquid, glass.onReduceBlur, enabled = glass.liquid)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(backdrop, stringResource(R.string.common_continue), onClick = onNext)
}

@Composable
private fun Toggle(on: Boolean, onTurnOff: () -> Unit = {}, onTurnOn: () -> Unit) {
    Switch(
        checked = on,
        onCheckedChange = { if (it) onTurnOn() else onTurnOff() },
        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759)),
    )
}

/** Opens a special-access page for Lumen in system settings. */
internal fun Context.openSpecial(action: String) {
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

/** A download size, like the library's "%,d MB" but from resources. */
@Composable
private fun megabytes(bytes: Long): String = stringResource(R.string.onboarding_megabytes, bytes / 1_000_000)

/** A search model's tier name; the library's [com.subhashh.lumen.ai.AiModel.label] is English only. */
@Composable
private fun tierLabel(model: com.subhashh.lumen.ai.AiModel): String = when (model) {
    ModelCatalog.Accurate -> stringResource(R.string.onboarding_tier_accurate)
    ModelCatalog.Fast -> stringResource(R.string.onboarding_tier_fast)
    else -> model.label
}

@Composable
private fun ColumnScope.Smart(backdrop: Backdrop, onNext: (downloading: Boolean) -> Unit) {
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
        onNext(true)
    }
    // The download's notification needs this on Android 13+; it downloads either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Title(stringResource(R.string.onboarding_smart_title), stringResource(R.string.onboarding_smart_subtitle))
        Card(backdrop) {
            Item(LumenIcons.Search, stringResource(R.string.onboarding_smart_search), stringResource(R.string.onboarding_smart_search_detail, megabytes(model.bytes(context)))) { Switch(search, { search = it }) }
            if (search) {
                Row(Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in ModelCatalog.all) {
                        val selected = m.id == tier
                        Text(
                            if (m == recommended) stringResource(R.string.onboarding_tier_recommended, tierLabel(m)) else tierLabel(m),
                            color = ink,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ink.copy(alpha = if (selected) 0.25f else 0.08f))
                                .border(1.dp, ink.copy(alpha = if (selected) 0.5f else 0f), CircleShape)
                                .pressable({ tier = m.id }, pressedScale = 0.95f)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Item(LumenIcons.Document, stringResource(R.string.onboarding_text), stringResource(R.string.onboarding_text_detail, megabytes(com.subhashh.lumen.ai.ModelDownloads.bytes(context, com.subhashh.lumen.ai.ModelPack.Text)))) { Switch(text, { text = it }) }
            Item(LumenIcons.Face, stringResource(R.string.onboarding_faces), stringResource(R.string.onboarding_faces_detail, megabytes(FaceModel.bytes))) { Switch(faces, { faces = it }) }
            Item(LumenIcons.Subtitles, stringResource(R.string.onboarding_subtitles), stringResource(R.string.onboarding_subtitles_detail, megabytes(SpeechModel.bytes))) { Switch(subtitles, { subtitles = it }) }
        }
        Spacer(Modifier.height(12.dp))
        Card(backdrop) {
            Item(LumenIcons.Settings, stringResource(R.string.onboarding_wifi_only), stringResource(R.string.onboarding_wifi_only_detail)) { Switch(wifiOnly, { wifiOnly = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_download_source),
            color = ink.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(backdrop, if (total > 0) stringResource(R.string.onboarding_download_button, megabytes(total)) else stringResource(R.string.common_continue), enabled = true) {
        if (total == 0L) onNext(false)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else start()
    }
    SecondaryButton(stringResource(R.string.common_not_now)) { onNext(false) }
}

@Composable
private fun ColumnScope.Schedule(backdrop: Backdrop, onNext: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    var overnight by rememberSaveable { mutableStateOf(prefs.indexScheduleOn) }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Title(stringResource(R.string.onboarding_schedule_title), stringResource(R.string.onboarding_schedule_subtitle))
        for ((night, title, subtitle) in listOf(
            Triple(false, stringResource(R.string.onboarding_while_charging), stringResource(R.string.onboarding_while_charging_detail)),
            Triple(true, stringResource(R.string.onboarding_overnight), stringResource(R.string.onboarding_overnight_detail)),
        )) {
            val selected = overnight == night
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .glass(backdrop, RoundedCornerShape(24.dp), blurRadius = 16.dp, tint = glassTint(if (selected) 0.26f else 0.1f))
                    .border(1.5.dp, ink.copy(alpha = if (selected) 0.7f else 0f), RoundedCornerShape(24.dp))
                    .pressable({ overnight = night }, pressedScale = 0.97f)
                    .padding(18.dp),
            ) {
                Column {
                    Text(title, color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, color = ink.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(backdrop, stringResource(R.string.common_continue)) {
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
    // Laid out like the Library's grid (its columns, oldest to newest, the newest bottom right),
    // so each photo flies the short way into its cell.
    val columns = remember { LumenPrefs(context).gridColumns.coerceIn(3, 6) }
    BoxWithConstraints(modifier) {
        val cell = maxWidth / columns
        val rows = (maxHeight / cell).toInt().coerceAtLeast(1)
        val shown = tiles.takeLast(columns * rows)
        // A short first row, like the grid's last one filled from the left: here the gap goes first
        // so the newest still ends the bottom row.
        val gap = (columns - shown.size % columns) % columns
        val top = (maxHeight - cell * ((shown.size + gap + columns - 1) / columns)) / 2
        val random = remember(tiles) { java.util.Random(11) }
        val jitter = remember(tiles) { List(tiles.size) { Triple(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f) } }
        for ((i, tile) in shown.withIndex()) {
            val (jx, jy, jr) = jitter[i]
            val slot = i + gap
            coil3.compose.AsyncImage(
                model = remember(tile.item.id) { mosaicRequest(context, tile.item) },
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .offset(cell * (slot % columns) + cell * 0.08f + cell * 0.12f * jx, top + cell * (slot / columns) + cell * 0.08f + cell * 0.12f * jy)
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
private fun ColumnScope.Finish(backdrop: Backdrop, photos: List<MediaItem>, downloading: Boolean, onFinish: (OnboardingExit) -> Unit) {
    val tiles = remember(photos) { photos.map { MosaicTile(it) } }
    val fade = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    var button by remember { mutableStateOf(Offset.Zero) }
    var leaving by remember { mutableStateOf(false) }
    Mosaic(tiles, Modifier.fillMaxWidth().weight(1f))
    Column(Modifier.graphicsLayer { alpha = fade.value }) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_done_title), color = ink, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (downloading) stringResource(R.string.onboarding_done_downloading)
            else stringResource(R.string.onboarding_done_later),
            color = ink.copy(alpha = 0.85f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        val background = MaterialTheme.colorScheme.background
        Box(Modifier.onGloballyPositioned { button = it.boundsInRoot().center }) {
            PrimaryButton(backdrop, stringResource(R.string.onboarding_lets_go)) {
                if (leaving) return@PrimaryButton
                leaving = true
                scope.launch {
                    // The words step aside first; the photos fly from where they are then.
                    fade.animateTo(0f, tween(120))
                    onFinish(OnboardingExit(tiles.mapNotNull { it.exit() }, button, background))
                }
            }
        }
    }
}

/** Onboarding's words and icons: white on the dark theme, black on the light one. */
private val ink: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

/** The glass cards' and buttons' tint: white on dark; on light, half as much black. */
@Composable
private fun glassTint(alpha: Float): Color =
    if (com.subhashh.lumen.ui.theme.LocalDarkTheme.current) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha * 0.5f)
