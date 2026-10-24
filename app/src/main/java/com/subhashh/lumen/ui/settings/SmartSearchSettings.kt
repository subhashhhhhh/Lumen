package com.subhashh.lumen.ui.settings

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.subhashh.lumen.data.ai.EmbeddingStore
import com.subhashh.lumen.data.ai.IndexProgress
import com.subhashh.lumen.data.ai.Indexer
import com.subhashh.lumen.data.ai.SmartIndex
import com.subhashh.lumen.data.ai.TextStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.VideoDetail
import com.subhashh.lumen.data.ai.DownloadNotification.megabytes
import com.subhashh.lumen.data.ai.DownloadState
import com.subhashh.lumen.data.ai.HfTokenStore
import com.subhashh.lumen.data.ai.ModelCatalog
import com.subhashh.lumen.data.ai.ModelDownloads
import com.subhashh.lumen.data.ai.ModelPack
import com.subhashh.lumen.data.ai.OcrModel
import com.subhashh.lumen.data.ai.SpeechModel
import com.subhashh.lumen.data.ai.FaceModel
import com.subhashh.lumen.data.ai.FaceStore
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.icons.LumenIcons

/**
 * Settings → Smart Features: Smart Search, Text Reading, Faces and Subtitles, each downloaded,
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
    var scheduleOn by remember { mutableStateOf(prefs.indexScheduleOn) }
    var scheduleStart by remember { mutableIntStateOf(prefs.indexScheduleStart) }
    var scheduleEnd by remember { mutableIntStateOf(prefs.indexScheduleEnd) }
    val waitingText = when {
        scheduleOn -> "Continues ${clock(context, scheduleStart)} – ${clock(context, scheduleEnd)}"
        onBattery -> "Continues in the background"
        else -> "Continues while your phone charges"
    }
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
    fun PackRow(pack: ModelPack, title: String, removeTitle: String, removeMessage: String, iconColor: Color) {
        val (status, action) = remember(state, refresh, changed, chosenId) { packStatus(context, pack, state, wifiOnly) }
        SettingsRow(
            title = title,
            subtitle = status,
            value = action,
            onClick = {
                when (action) {
                    "Cancel" -> {
                        ModelDownloads.cancel(context, pack, wifiOnly)
                        changed++
                    }
                    "Remove" -> onConfirm(
                        Confirmation(
                            title = removeTitle,
                            message = removeMessage + " (${megabytes(ModelDownloads.bytes(context, pack))})",
                            confirm = "Remove",
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
            icon = LumenIcons.Import,
            iconColor = iconColor,
        )
    }

    Group(
        "Smart Search",
        footer = "Find photos by what's in them, like “cat asleep on a sofa”. Accurate finds more " +
            "and understands many languages; Fast is smaller and quicker to set up, in English.",
    ) {
        Anchor("search-quality", highlight) {
            PickerRow(
                title = "Search Quality",
                options = ModelCatalog.all.map { "${it.label} · ${megabytes(it.bytes)}" },
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
                ModelPack.Search, "Search Model", "Remove Smart Search?",
                "Searching by what's in photos stops working until you download it again. Text, faces and subtitles stay",
                Indigo,
            )
        }
        if (progress != null) {
            Divider()
            val complete = progress.indexed >= progress.total
            Anchor("indexed", highlight) {
                SettingsRow(
                    title = "Indexed",
                    subtitle = if (complete) "New photos and videos are added as they arrive" else waitingText,
                    value = if (complete) "All ${"%,d".format(progress.total)}" else "%,d of %,d".format(progress.indexed, progress.total),
                    onClick = null,
                    icon = LumenIcons.Image,
                    iconColor = Green,
                )
            }
        }
    }

    Group(
        "Text in Photos",
        footer = "Search the words in photos, screenshots and videos, like a name in a chat. " +
            "Reads Latin scripts, Chinese and Japanese.",
    ) {
        Anchor("text-model", highlight) {
            PackRow(
                ModelPack.Text, "Text Reader", "Remove Text Reading?",
                "Searching the words in photos stops working, and the words read so far are deleted",
                Orange,
            )
        }
        if (textProgress != null) {
            Divider()
            val complete = textProgress.indexed >= textProgress.total
            Anchor("text-read", highlight) {
                SettingsRow(
                    title = "Text Read",
                    subtitle = if (complete) "Words in photos and videos are searchable" else waitingText,
                    value = if (complete) "All ${"%,d".format(textProgress.total)}" else "%,d of %,d".format(textProgress.indexed, textProgress.total),
                    onClick = null,
                    icon = LumenIcons.Document,
                    iconColor = Orange,
                )
            }
        }
    }

    Group(
        "Faces",
        footer = "Groups photos and videos by person, in Collections → People. Name people to find them in Search.",
    ) {
        Anchor("face-model", highlight) {
            PackRow(
                ModelPack.Faces, "Face Models", "Remove Faces?",
                "People, their names and merges are deleted; downloading again finds everyone from scratch",
                Purple,
            )
        }
        if (faceProgress != null) {
            Divider()
            val (progress, people) = faceProgress
            val complete = progress.indexed >= progress.total
            Anchor("faces", highlight) {
                SettingsRow(
                    title = "Faces",
                    subtitle = if (complete) "%,d people found".format(people) else "$waitingText · %,d people so far".format(people),
                    value = if (complete) "All ${"%,d".format(progress.total)}" else "%,d of %,d".format(progress.indexed, progress.total),
                    onClick = null,
                    icon = LumenIcons.Image,
                    iconColor = Purple,
                )
            }
        }
    }

    // Subtitles: made on the phone when CC is tapped on a video.
    Group(
        "Subtitles",
        footer = "Tap CC on a video to make subtitles on this phone. Works for English, German, " +
            "French, Spanish, Italian, Dutch and Polish speech; music without speech gets none.",
    ) {
        Anchor("subtitle-quality", highlight) {
            PickerRow(
                title = "Subtitle Quality",
                options = SpeechModel.tiers.map { "${it.label} · ${megabytes(SpeechModel.bytes)}" },
                selected = 0,
                onSelect = {},
                icon = LumenIcons.Video,
                iconColor = Purple,
            )
        }
        Divider()
        Anchor("subtitles-model", highlight) {
            PackRow(
                ModelPack.Subtitles, "Subtitles Model", "Remove Subtitles?",
                "CC can't make new subtitles until you download it again. Subtitles already made stay",
                Indigo,
            )
        }
    }

    Group(
        "Scheduled Indexing",
        footer = if (scheduleOn) {
            "Photos and videos are indexed only in these hours, plugged in or not (it stops if the battery " +
                "runs low or the phone gets warm). New photos are still added as they arrive. Unplugged, " +
                "Android lets background work run only now and then at night, so it's fastest on the charger."
        } else {
            "Index the backlog at a time you choose, like overnight, instead of whenever the phone charges."
        },
    ) {
        Anchor("scheduled-indexing", highlight) {
            ToggleRow(
                title = "Scheduled Indexing",
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
                title = "Starts",
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
                title = "Ends",
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
        "Indexing and Downloads",
        footer = "Models download once from Hugging Face; your photos and searches never leave this phone.",
    ) {
        // With a schedule, its hours decide when indexing runs; this applies without one.
        if (!scheduleOn) {
            Anchor("index-on-battery", highlight) {
                ToggleRow(
                    title = "Index on Battery",
                    subtitle = "Keep scanning off the charger; stops when the battery runs low",
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
        Anchor("video-detail", highlight) {
            PickerRow(
                title = "Video Detail",
                subtitle = "Frame size for faces and text in videos; lower is faster on older phones",
                options = VideoDetail.entries.map { "${it.label} · ${it.px} px" },
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
                title = "Download on Wi-Fi Only",
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
                title = "Hugging Face Token",
                subtitle = "Optional; can make downloads faster",
                value = if (hasToken) "Saved" else "None",
                onClick = {
                    if (hasToken) {
                        onConfirm(
                            Confirmation(
                                title = "Remove Your Token?",
                                message = "Downloads will go without it. It's stored encrypted on this phone only.",
                                confirm = "Remove",
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
        return "Downloading · ${megabytes(state.done)} of ${megabytes(state.total)}" to "Cancel"
    }
    if (queued && state is DownloadState.Running) return "Queued, after ${state.pack.label}" to "Cancel"
    if (queued && state == DownloadState.Waiting) {
        return (if (wifiOnly) "Waiting for Wi-Fi…" else "Waiting for a connection…") to "Cancel"
    }
    if (state is DownloadState.Failed && state.pack == pack) return "Couldn't download: ${state.message}" to "Retry"
    val remaining = ModelDownloads.remainingBytes(context, pack)
    val installed = ModelDownloads.installed(context)
    return when {
        ModelDownloads.complete(context, pack) -> "Ready · ${megabytes(size)} on this phone" to "Remove"
        // Switching tiers: the current one keeps working until the new one is in.
        pack == ModelPack.Search && installed != null && installed != ModelDownloads.model(context) ->
            "Using ${installed.label} · ${ModelDownloads.model(context).label} is ${megabytes(size)}" to "Download"
        // A newer version changed some files, or a download stopped part way: fetch the rest.
        remaining < size -> "Update available · ${megabytes(remaining)}" to "Update"
        else -> "Not downloaded · ${megabytes(size)}" to "Download"
    }
}
