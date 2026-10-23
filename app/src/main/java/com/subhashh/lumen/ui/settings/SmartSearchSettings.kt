package com.subhashh.lumen.ui.settings

import android.Manifest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.ai.DownloadNotification.megabytes
import com.subhashh.lumen.data.ai.DownloadState
import com.subhashh.lumen.data.ai.HfTokenStore
import com.subhashh.lumen.data.ai.ModelCatalog
import com.subhashh.lumen.data.ai.ModelDownloads
import com.subhashh.lumen.data.ai.OcrModel
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.icons.LumenIcons

/**
 * Settings → Smart Search: download (or remove) the search model, Wi-Fi only, and an optional
 * Hugging Face token. The only part of Lumen that touches the network.
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
    var removed by remember { mutableIntStateOf(0) }
    val installed = remember(refresh, state, removed, chosenId) { ModelDownloads.installed(context) }
    val live by SmartIndex.progress.collectAsState()
    val counted by produceState<IndexProgress?>(null, refresh, installed) {
        val model = installed
        value = if (model == null) null else withContext(Dispatchers.IO) {
            IndexProgress(EmbeddingStore.of(context, model).count(), Indexer.eligible(context).size)
        }
    }
    val progress = if (installed == null) null else live ?: counted
    var tokenRemoved by remember { mutableIntStateOf(0) }
    val hasToken = remember(refresh, tokenRemoved) { HfTokenStore(context).token != null }
    var wifiOnly by remember { mutableStateOf(prefs.modelWifiOnly) }

    // The download's progress notification needs this on Android 13+; it downloads either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ModelDownloads.start(context, wifiOnly)
    }
    fun download() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ModelDownloads.start(context, wifiOnly)
        }
    }

    val size = megabytes(chosen.bytes + OcrModel.bytes)
    val readerMissing = remember(refresh, state, removed) { !OcrModel.installed(context) }
    val remaining = remember(state, refresh, removed, chosenId) { ModelDownloads.remainingBytes(context) }
    val update = if (remaining < chosen.bytes + OcrModel.bytes) "Update available · ${megabytes(remaining)}" to "Update"
        else "Not downloaded · $size" to "Download"
    val (status, action) = when (val s = state) {
        is DownloadState.Running -> "Downloading · ${megabytes(s.done)} of ${megabytes(s.total)}" to "Cancel"
        DownloadState.Waiting -> (if (wifiOnly) "Waiting for Wi-Fi…" else "Waiting for a connection…") to "Cancel"
        is DownloadState.Failed -> "Couldn't download: ${s.message}" to "Retry"
        DownloadState.Idle -> when (installed) {
            // A newer version added the text reader: search keeps working until it's fetched.
            chosen -> if (readerMissing) update else "Ready · $size on this phone" to "Remove"
            // Part of it is here already: a newer version changed some files (Accurate's image model).
            null -> update
            // Switching tiers: the current one keeps working until the new one is in.
            else -> "Using ${installed.label} · ${chosen.label} is $size" to "Download"
        }
    }
    val busy = state is DownloadState.Running || state is DownloadState.Waiting

    Group(
        "Search Model",
        footer = "Find photos by what's in them, like “cat asleep on a sofa”, or by the words in " +
            "them, like a name in a chat screenshot. Accurate finds more " +
            "and understands many languages; Fast is smaller and quicker to set up, in English. " +
            "The search model downloads once from Hugging Face; your photos and searches never " +
            "leave this phone.",
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
                        if (busy) ModelDownloads.cancel(context)
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
            SettingsRow(
                title = "Search Model",
                subtitle = status,
                value = action,
                onClick = {
                    when (action) {
                        "Cancel" -> ModelDownloads.cancel(context)
                        "Remove" -> onConfirm(
                            Confirmation(
                                title = "Remove the Search Model?",
                                message = "Smart Search stops working until you download it again ($size).",
                                confirm = "Remove",
                                destructive = true,
                                onConfirm = {
                                    ModelDownloads.delete(context)
                                    removed++
                                },
                            ),
                        )
                        else -> download()
                    }
                },
                icon = LumenIcons.Import,
                iconColor = Indigo,
            )
        }
        if (progress != null) {
            Divider()
            val complete = progress.indexed >= progress.total
            Anchor("indexed", highlight) {
                SettingsRow(
                    title = "Indexed",
                    subtitle = if (complete) "New photos and videos are added as they arrive" else "Continues while your phone charges",
                    value = if (complete) "All ${"%,d".format(progress.total)}" else "%,d of %,d".format(progress.indexed, progress.total),
                    onClick = null,
                    icon = LumenIcons.Image,
                    iconColor = Green,
                )
            }
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
