package com.subhashh.lumen.ui.settings

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.subhashh.lumen.ai.EmbeddingStore
import com.subhashh.lumen.ai.IndexProgress
import com.subhashh.lumen.ai.Indexer
import com.subhashh.lumen.ai.SmartIndex
import com.subhashh.lumen.ai.TextStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.VideoDetail
import com.subhashh.lumen.ai.DownloadNotification.megabytes
import com.subhashh.lumen.ai.DownloadState
import com.subhashh.lumen.ai.HfTokenStore
import com.subhashh.lumen.ai.ModelCatalog
import com.subhashh.lumen.ai.ModelDownloads
import com.subhashh.lumen.ai.ModelPack
import com.subhashh.lumen.ai.OcrModel
import com.subhashh.lumen.ai.SpeechModel
import com.subhashh.lumen.ai.FaceModel
import com.subhashh.lumen.ai.FaceStore
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.icons.LumenIcons

/**
 * Settings → Smart Features: Smart Search, Text Reading, Faces, Subtitles and Cutout, each downloaded,
 * updated and removed on its own ([ModelPack]); then indexing and download options. The only
 * part of Lumen that touches the network.
 */
@Composable
internal fun SmartSearchGroup(
    prefs: LumenPrefs,
    /** Bumped by Settings on resume and after the token changes, to re-read files and token. */
    refresh: Int,
    onConfirm: (Confirmation) -> Unit,
    onEnterToken: () -> Unit,
    /** A setting searched for in Settings, to bring into view and flash. */
    highlight: String? = null,
) {
    val context = LocalContext.current
    val state by ModelDownloads.state.collectAsState()
    var chosenId by remember { mutableStateOf(prefs.searchModel) }
    val chosen = ModelCatalog.byId(chosenId)
    /** Bumped after every download, cancel and remove: re-read what's on the phone. */
    var changed by remember { mutableIntStateOf(0) }
    val installed = remember(refresh, state, changed, chosenId) { ModelDownloads.installed(context) }
    val readerReady = remember(refresh, state, changed) { OcrModel.installed(context) }
    val facesReady = remember(refresh, state, changed) { FaceModel.installed(context) }
    val live by SmartIndex.progress.collectAsState()
    val counted by produceState<IndexProgress?>(null, refresh, installed) {
        val model = installed
        value = if (model == null) null else withContext(Dispatchers.IO) {
            IndexProgress(EmbeddingStore.of(context, model).count(), Indexer.eligible(context).size)
        }
    }
    val progress = if (installed == null) null else live ?: counted
    val liveText by SmartIndex.textProgress.collectAsState()
    val countedText by produceState<IndexProgress?>(null, refresh, readerReady) {
        value = if (!readerReady) null else withContext(Dispatchers.IO) {
            IndexProgress(TextStore.of(context).count(), Indexer.eligible(context).size)
        }
    }
    val textProgress = if (!readerReady) null else liveText ?: countedText
    val liveFaces by SmartIndex.faceProgress.collectAsState()
    val countedFaces by produceState<Pair<IndexProgress, Int>?>(null, refresh, facesReady, liveFaces) {
        value = if (!facesReady) null else withContext(Dispatchers.IO) {
            val store = FaceStore.of(context)
            IndexProgress(store.count(), Indexer.eligible(context).size) to store.peopleCount()
        }
    }
    val faceProgress = countedFaces?.let { (counted, people) -> (liveFaces ?: counted) to people }
    var tokenRemoved by remember { mutableIntStateOf(0) }
    val hasToken = remember(refresh, tokenRemoved) { HfTokenStore(context).token != null }
    var wifiOnly by remember { mutableStateOf(prefs.modelWifiOnly) }
    var videoDetail by remember { mutableStateOf(prefs.videoDetail) }
    var onBattery by remember { mutableStateOf(prefs.indexOnBattery) }
    // Android's battery optimization holds background jobs back (standby quotas, Doze, and some
    // makers' own battery managers); only the system screen can change it, so re-read on resume.
    val unrestricted = remember(refresh) {
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    var scheduleOn by remember { mutableStateOf(prefs.indexScheduleOn) }
    var scheduleStart by remember { mutableIntStateOf(prefs.indexScheduleStart) }
    var scheduleEnd by remember { mutableIntStateOf(prefs.indexScheduleEnd) }
    val waitingText = when {
        scheduleOn -> stringResource(R.string.smart_continues_schedule, clock(context, scheduleStart), clock(context, scheduleEnd))
        onBattery -> stringResource(R.string.smart_continues_background)
        else -> stringResource(R.string.smart_continues_charging)
    }
    val cancelAction = stringResource(R.string.common_cancel)
    val removeAction = stringResource(R.string.common_remove)
    /** Android's own time picker, in the phone's 12- or 24-hour style. */
    fun pickTime(minute: Int, onPicked: (Int) -> Unit) {
        android.app.TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            minute / 60,
            minute % 60,
            android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }


    // The download's progress notification needs this on Android 13+; it downloads either way.
    var asked by remember { mutableStateOf<ModelPack?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked?.let { pack -> ModelDownloads.start(context, pack, wifiOnly) }
        changed++
    }
    fun download(pack: ModelPack) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            asked = pack
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ModelDownloads.start(context, pack, wifiOnly)
            changed++
        }
    }

    /** One pack's row: its state, and Download / Update / Cancel / Retry / Remove. */
    @Composable
    fun PackRow(pack: ModelPack, title: String, removeTitle: String, removeMessage: String, iconColor: Color, icon: ImageVector = LumenIcons.Import) {
        val (status, action) = remember(state, refresh, changed, chosenId) { packStatus(context, pack, state, wifiOnly) }
        SettingsRow(
            title = title,
            subtitle = status,
            value = action,
            onClick = {
                when (action) {
                    cancelAction -> {
                        ModelDownloads.cancel(context, pack, wifiOnly)
                        changed++
                    }
                    removeAction -> onConfirm(
                        Confirmation(
                            title = removeTitle,
                            message = context.getString(R.string.smart_remove_message_size, removeMessage, megabytes(ModelDownloads.bytes(context, pack))),
                            confirm = removeAction,
                            destructive = true,
                            onConfirm = {
                                ModelDownloads.delete(context, pack, wifiOnly)
                                changed++
                            },
                        ),
                    )
                    else -> download(pack)
                }
            },
            icon = icon,
            iconColor = iconColor,
        )
    }

    Group(
        stringResource(R.string.smart_search_title),
        footer = stringResource(R.string.smart_search_footer),
    ) {
        Anchor("search-quality", highlight) {
            PickerRow(
                title = stringResource(R.string.smart_search_quality),
                options = ModelCatalog.all.map { stringResource(R.string.smart_option_size, modelLabel(context, it.label), megabytes(it.bytes(context))) },
                selected = ModelCatalog.all.indexOf(chosen),
                onSelect = {
                    val next = ModelCatalog.all[it]
                    if (next != chosen) {
                        // A half-finished download was for the other tier; its parts stay to resume.
                        val s = state
                        if (s is DownloadState.Running && s.pack == ModelPack.Search) ModelDownloads.cancel(context, ModelPack.Search, wifiOnly)
                        chosenId = next.id
                        prefs.searchModel = next.id
                    }
                },
                icon = LumenIcons.Search,
                iconColor = Purple,
            )
        }
        Divider()
        Anchor("search-model", highlight) {
            PackRow(
                ModelPack.Search, stringResource(R.string.smart_search_model), stringResource(R.string.smart_search_remove_title),
                stringResource(R.string.smart_search_remove_message),
                Indigo,
            )
        }
        if (progress != null) {
            Divider()
            val complete = progress.indexed >= progress.total
            Anchor("indexed", highlight) {
                SettingsRow(
                    title = stringResource(R.string.smart_indexed),
                    subtitle = if (complete) stringResource(R.string.smart_indexed_complete) else waitingText,
                    value = if (complete) stringResource(R.string.smart_all_count, progress.total) else stringResource(R.string.smart_count_of, progress.indexed, progress.total),
                    onClick = null,
                    icon = LumenIcons.Image,
                    iconColor = Green,
                )
            }
        }
        ProcessorRow(ModelPack.Search, refresh)
    }

    Group(
        stringResource(R.string.smart_text_title),
        footer = stringResource(R.string.smart_text_footer),
    ) {
        Anchor("text-model", highlight) {
            PackRow(
                ModelPack.Text, stringResource(R.string.smart_text_model), stringResource(R.string.smart_text_remove_title),
                stringResource(R.string.smart_text_remove_message),
                Orange,
            )
        }
        if (textProgress != null) {
            Divider()
            val complete = textProgress.indexed >= textProgress.total
            Anchor("text-read", highlight) {
                SettingsRow(
                    title = stringResource(R.string.smart_text_read),
                    subtitle = if (complete) stringResource(R.string.smart_text_read_complete) else waitingText,
                    value = if (complete) stringResource(R.string.smart_all_count, textProgress.total) else stringResource(R.string.smart_count_of, textProgress.indexed, textProgress.total),
                    onClick = null,
                    icon = LumenIcons.Document,
                    iconColor = Orange,
                )
            }
        }
        ProcessorRow(ModelPack.Text, refresh)
    }

    Group(
        stringResource(R.string.smart_faces_title),
        footer = stringResource(R.string.smart_faces_footer),
    ) {
        // Accurate runs only on a supported NPU: elsewhere it shows, faded, and says why.
        val accurate = remember(refresh) { com.subhashh.lumen.ai.FaceModel.accurate(context) }
        val faceRecognizer = remember(refresh, changed) { com.subhashh.lumen.ai.FaceModel.recognizer(context) }
        Anchor("face-quality", highlight) {
            PickerRow(
                title = stringResource(R.string.smart_faces_quality),
                options = listOf(
                    accurate?.let { stringResource(R.string.smart_option_size, modelLabel(context, "Accurate"), megabytes(com.subhashh.lumen.ai.FaceModel.bytes(it))) }
                        ?: modelLabel(context, "Accurate"),
                    stringResource(R.string.smart_option_size, modelLabel(context, "Fast"), megabytes(com.subhashh.lumen.ai.FaceModel.bytes(com.subhashh.lumen.ai.FaceModel.Fast))),
                ),
                selected = if (faceRecognizer.id == com.subhashh.lumen.ai.FaceModel.AccurateId) 0 else 1,
                onSelect = { index ->
                    val id = if (index == 0) com.subhashh.lumen.ai.FaceModel.AccurateId else com.subhashh.lumen.ai.FaceModel.Fast.id
                    if (id != faceRecognizer.id) {
                        prefs.faceModel = id
                        changed++
                        // Faces already read move over to it once it's downloaded; names stay.
                        if (FaceModel.installed(context)) ModelDownloads.start(context, ModelPack.Faces, wifiOnly)
                    }
                },
                unavailable = if (accurate == null) setOf(0) else emptySet(),
                onUnavailable = {
                    android.widget.Toast.makeText(context, R.string.smart_faces_no_npu, android.widget.Toast.LENGTH_LONG).show()
                },
                icon = LumenIcons.Face,
                iconColor = Purple,
            )
        }
        Divider()
        Anchor("face-model", highlight) {
            PackRow(
                ModelPack.Faces, stringResource(R.string.smart_faces_model), stringResource(R.string.smart_faces_remove_title),
                stringResource(R.string.smart_faces_remove_message),
                Purple,
                LumenIcons.Face,
            )
        }
        if (faceProgress != null) {
            Divider()
            val (progress, people) = faceProgress
            val complete = progress.indexed >= progress.total
            Anchor("faces", highlight) {
                SettingsRow(
                    title = stringResource(R.string.smart_faces_title),
                    subtitle = if (complete) {
                        pluralStringResource(R.plurals.smart_faces_people_found, people, people)
                    } else {
                        pluralStringResource(R.plurals.smart_faces_people_so_far, people, waitingText, people)
                    },
                    value = if (complete) stringResource(R.string.smart_all_count, progress.total) else stringResource(R.string.smart_count_of, progress.indexed, progress.total),
                    onClick = null,
                    icon = LumenIcons.Face,
                    iconColor = Purple,
                )
            }
        }
        ProcessorRow(ModelPack.Faces, refresh)
    }

    // Subtitles: made on the phone when CC is tapped on a video.
    Group(
        stringResource(R.string.smart_subtitles_title),
        footer = stringResource(R.string.smart_subtitles_footer),
    ) {
        Anchor("subtitle-quality", highlight) {
            PickerRow(
                title = stringResource(R.string.smart_subtitles_quality),
                options = SpeechModel.tiers.map { stringResource(R.string.smart_option_size, modelLabel(context, it.label), megabytes(SpeechModel.bytes)) },
                selected = 0,
                onSelect = {},
                icon = LumenIcons.Subtitles,
                iconColor = Purple,
            )
        }
        Divider()
        Anchor("subtitles-model", highlight) {
            PackRow(
                ModelPack.Subtitles, stringResource(R.string.smart_subtitles_model), stringResource(R.string.smart_subtitles_remove_title),
                stringResource(R.string.smart_subtitles_remove_message),
                Indigo,
            )
        }
    }

    // Cutout: touch and hold a subject in the viewer.
    Group(
        stringResource(R.string.smart_cutout_title),
        footer = stringResource(R.string.smart_cutout_footer),
    ) {
        Anchor("cutout-model", highlight) {
            PackRow(
                ModelPack.Cutout, stringResource(R.string.smart_cutout_model), stringResource(R.string.smart_cutout_remove_title),
                stringResource(R.string.smart_cutout_remove_message),
                Indigo,
            )
        }
        ProcessorRow(ModelPack.Cutout, refresh)
    }

    Group(
        stringResource(R.string.smart_schedule_title),
        footer = if (scheduleOn) {
            stringResource(R.string.smart_schedule_footer_on)
        } else {
            stringResource(R.string.smart_schedule_footer_off)
        },
    ) {
        Anchor("scheduled-indexing", highlight) {
            ToggleRow(
                title = stringResource(R.string.smart_schedule_title),
                checked = scheduleOn,
                onCheckedChange = {
                    scheduleOn = it
                    prefs.indexScheduleOn = it
                    SmartIndex.schedule(context, replace = true)
                },
                icon = LumenIcons.Timer,
                iconColor = Indigo,
            )
        }
        if (scheduleOn) {
            Divider()
            SettingsRow(
                title = stringResource(R.string.smart_schedule_starts),
                value = clock(context, scheduleStart),
                onClick = {
                    pickTime(scheduleStart) {
                        scheduleStart = it
                        prefs.indexScheduleStart = it
                        SmartIndex.schedule(context, replace = true)
                    }
                },
                icon = LumenIcons.Timer,
                iconColor = Indigo,
            )
            Divider()
            SettingsRow(
                title = stringResource(R.string.smart_schedule_ends),
                value = clock(context, scheduleEnd),
                onClick = {
                    pickTime(scheduleEnd) {
                        scheduleEnd = it
                        prefs.indexScheduleEnd = it
                        SmartIndex.schedule(context, replace = true)
                    }
                },
                icon = LumenIcons.Timer,
                iconColor = Indigo,
            )
        }
    }

    Group(
        stringResource(R.string.smart_downloads_title),
        footer = stringResource(R.string.smart_downloads_footer),
    ) {
        // With a schedule, its hours decide when indexing runs; this applies without one.
        if (!scheduleOn) {
            Anchor("index-on-battery", highlight) {
                ToggleRow(
                    title = stringResource(R.string.smart_index_on_battery),
                    subtitle = stringResource(R.string.smart_index_on_battery_subtitle),
                    checked = onBattery,
                    onCheckedChange = {
                        onBattery = it
                        prefs.indexOnBattery = it
                        SmartIndex.schedule(context, replace = true)
                    },
                    icon = LumenIcons.Timer,
                    iconColor = Green,
                )
            }
            Divider()
        }
        Anchor("unrestricted-battery", highlight) {
            ToggleRow(
                title = stringResource(R.string.smart_unrestricted_battery),
                subtitle = stringResource(R.string.smart_unrestricted_battery_subtitle),
                checked = unrestricted,
                onCheckedChange = { on ->
                    // Asking needs Android's own dialog; taking it back, its battery settings list.
                    val intent = if (on) {
                        android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + context.packageName),
                        )
                    } else {
                        android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }
                    runCatching { context.startActivity(intent) }
                },
                icon = LumenIcons.Timer,
                iconColor = Green,
            )
        }
        Divider()
        Anchor("video-detail", highlight) {
            PickerRow(
                title = stringResource(R.string.smart_video_detail),
                subtitle = stringResource(R.string.smart_video_detail_subtitle),
                options = VideoDetail.entries.map { stringResource(R.string.smart_option_px, stringResource(videoDetailLabel(it)), it.px) },
                selected = VideoDetail.entries.indexOf(videoDetail),
                onSelect = {
                    videoDetail = VideoDetail.entries[it]
                    prefs.videoDetail = videoDetail
                },
                icon = LumenIcons.Video,
                iconColor = Orange,
            )
        }
        Divider()
        Anchor("wifi-only", highlight) {
            ToggleRow(
                title = stringResource(R.string.smart_wifi_only),
                checked = wifiOnly,
                onCheckedChange = {
                    wifiOnly = it
                    prefs.modelWifiOnly = it
                },
                icon = LumenIcons.Settings,
                iconColor = Blue,
            )
        }
        Divider()
        Anchor("hf-token", highlight) {
            SettingsRow(
                title = stringResource(R.string.smart_hf_token),
                subtitle = stringResource(R.string.smart_hf_token_subtitle),
                value = if (hasToken) stringResource(R.string.smart_hf_token_saved) else stringResource(R.string.smart_hf_token_none),
                onClick = {
                    if (hasToken) {
                        onConfirm(
                            Confirmation(
                                title = context.getString(R.string.smart_hf_token_remove_title),
                                message = context.getString(R.string.smart_hf_token_remove_message),
                                confirm = removeAction,
                                destructive = true,
                                onConfirm = {
                                    HfTokenStore(context).token = null
                                    tokenRemoved++
                                },
                            ),
                        )
                    } else {
                        onEnterToken()
                    }
                },
                icon = LumenIcons.Lock,
                iconColor = Color(0xFFFF9F0A),
            )
        }
    }

    // How many frames a second are read for text, by video length; more is slower.
    var rates by remember { mutableStateOf(prefs.videoTextRates) }
    val defaults = com.subhashh.lumen.ai.VideoTextRates()
    val slower = rates.short > defaults.short || rates.medium > defaults.medium || rates.long > defaults.long
    Group(
        stringResource(R.string.smart_video_text_title),
        footer = stringResource(R.string.smart_video_text_footer),
        note = if (slower) stringResource(R.string.smart_video_text_slow) else null,
    ) {
        val unit = stringResource(R.string.smart_video_text_unit)
        val max = com.subhashh.lumen.ai.VideoTextRates.Max
        fun save(new: com.subhashh.lumen.ai.VideoTextRates) {
            // Max Frames goes up to 1,000 times the long videos' rate: lowering the rate lowers it too.
            val capped = new.copy(longMaxFrames = new.longMaxFrames.coerceAtMost(new.long * com.subhashh.lumen.ai.VideoTextRates.MaxFramesPerRate))
            rates = capped
            prefs.videoTextRates = capped
        }
        Anchor("video-text-short", highlight) {
            NumberRow(stringResource(R.string.smart_video_text_short), rates.short, { save(rates.copy(short = it)) }, 1, max, unit, stringResource(R.string.smart_video_text_short_subtitle))
        }
        Divider()
        Anchor("video-text-medium", highlight) {
            NumberRow(stringResource(R.string.smart_video_text_medium), rates.medium, { save(rates.copy(medium = it)) }, 1, max, unit, stringResource(R.string.smart_video_text_medium_subtitle))
        }
        Divider()
        Anchor("video-text-long", highlight) {
            NumberRow(stringResource(R.string.smart_video_text_long), rates.long, { save(rates.copy(long = it)) }, 1, max, unit, stringResource(R.string.smart_video_text_long_subtitle))
        }
        Divider()
        Anchor("video-text-max-frames", highlight) {
            NumberRow(
                stringResource(R.string.smart_video_text_max_frames),
                rates.longMaxFrames,
                { save(rates.copy(longMaxFrames = it)) },
                0,
                rates.long * com.subhashh.lumen.ai.VideoTextRates.MaxFramesPerRate,
                stringResource(R.string.smart_video_text_frames_unit),
                stringResource(R.string.smart_video_text_max_frames_subtitle),
            )
        }
    }
}

/** "1:00 AM" or "01:00", as the phone shows times. */
private fun clock(context: Context, minute: Int): String =
    android.text.format.DateFormat.getTimeFormat(context).format(
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, minute / 60)
            set(java.util.Calendar.MINUTE, minute % 60)
        }.time,
    )

/** A pack's status line and its action, from the download state and the files on the phone. */
private fun packStatus(context: Context, pack: ModelPack, state: DownloadState, wifiOnly: Boolean): Pair<String, String> {
    val size = ModelDownloads.bytes(context, pack)
    val queued = pack in ModelDownloads.queue(context)
    if (state is DownloadState.Running && state.pack == pack) {
        return context.getString(R.string.smart_status_downloading, megabytes(state.done), megabytes(state.total)) to
            context.getString(R.string.common_cancel)
    }
    if (queued && state is DownloadState.Running) {
        return context.getString(R.string.smart_status_queued, context.getString(packLabel(state.pack))) to
            context.getString(R.string.common_cancel)
    }
    if (queued && state == DownloadState.Waiting) {
        return context.getString(if (wifiOnly) R.string.smart_status_waiting_wifi else R.string.smart_status_waiting_connection) to
            context.getString(R.string.common_cancel)
    }
    if (state is DownloadState.Failed && state.pack == pack) {
        return context.getString(R.string.smart_status_failed, state.message) to context.getString(R.string.common_retry)
    }
    val remaining = ModelDownloads.remainingBytes(context, pack)
    val installed = ModelDownloads.installed(context)
    return when {
        ModelDownloads.complete(context, pack) ->
            context.getString(R.string.smart_status_ready, megabytes(size)) to context.getString(R.string.common_remove)
        // Switching tiers: the current one keeps working until the new one is in.
        pack == ModelPack.Search && installed != null && installed != ModelDownloads.model(context) ->
            context.getString(
                R.string.smart_status_switching,
                modelLabel(context, installed.label),
                modelLabel(context, ModelDownloads.model(context).label),
                megabytes(size),
            ) to context.getString(R.string.common_download)
        // A newer version changed some files, or a download stopped part way: fetch the rest.
        remaining < size ->
            context.getString(R.string.smart_status_update, megabytes(remaining)) to context.getString(R.string.common_update)
        else -> context.getString(R.string.smart_status_not_downloaded, megabytes(size)) to context.getString(R.string.common_download)
    }
}

/** A pack's name; the AI library's own labels are English only. */
@StringRes
private fun packLabel(pack: ModelPack): Int = when (pack) {
    ModelPack.Search -> R.string.smart_pack_search
    ModelPack.Text -> R.string.smart_pack_text
    ModelPack.Faces -> R.string.smart_pack_faces
    ModelPack.Subtitles -> R.string.smart_pack_subtitles
    ModelPack.Cutout -> R.string.smart_pack_cutout
}

/** A model tier's name ("Accurate", "Fast") from its English library label; unknown ones as they are. */
private fun modelLabel(context: Context, label: String): String = when (label) {
    "Accurate" -> context.getString(R.string.smart_tier_accurate)
    "Fast" -> context.getString(R.string.smart_tier_fast)
    else -> label
}

@StringRes
private fun videoDetailLabel(detail: VideoDetail): Int = when (detail) {
    VideoDetail.High -> R.string.smart_video_high
    VideoDetail.Medium -> R.string.smart_video_medium
    VideoDetail.Low -> R.string.smart_video_low
}

/**
 * What ran [pack]'s main model last: the NPU (with the chip), the GPU or the CPU. Hidden until it
 * has run once. Tells a slow report from a phone that fell back.
 */
@Composable
private fun ProcessorRow(pack: ModelPack, refresh: Any?) {
    val context = LocalContext.current
    val processor = remember(refresh) { com.subhashh.lumen.ai.Models.processor(context, pack) } ?: return
    val chip = remember { com.subhashh.lumen.ai.Models.npuChip(context) }
    // Why a phone with an NPU isn't using it: for the user, and for bug reports.
    val why = remember(refresh) {
        // A recorded failure shows even while the row still names the NPU from the last run: the
        // larger standard model is then offered for download, and this says why.
        com.subhashh.lumen.ai.Models.npuFailure(context, pack)?.let { context.getString(R.string.smart_runs_on_npu_failed, it) }
            ?: context.getString(R.string.smart_runs_on_needs_accurate).takeIf { processor != "NPU" && com.subhashh.lumen.ai.Models.npuNeedsAccurate(context, pack) }
            // "Runs On" is what ran the last photos indexed: until new ones are, it can still say GPU.
            ?: when (com.subhashh.lumen.ai.Models.npuFile(context, pack).takeIf { processor != "NPU" }) {
                com.subhashh.lumen.ai.NpuFileState.Ready -> context.getString(R.string.smart_runs_on_npu_ready)
                com.subhashh.lumen.ai.NpuFileState.Waiting -> context.getString(R.string.smart_runs_on_npu_waiting)
                null -> null
            }
    }
    Divider()
    SettingsRow(
        title = stringResource(R.string.smart_runs_on),
        subtitle = why,
        value = when (processor) {
            "NPU" -> stringResource(R.string.smart_runs_on_npu, chip.orEmpty())
            "GPU" -> stringResource(R.string.smart_runs_on_gpu)
            else -> stringResource(R.string.smart_runs_on_cpu)
        },
        onClick = null,
        icon = LumenIcons.Code,
        iconColor = Gray,
    )
}
