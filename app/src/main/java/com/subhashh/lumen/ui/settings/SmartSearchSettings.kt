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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.ai.DownloadNotification.megabytes
import com.subhashh.lumen.data.ai.DownloadState
import com.subhashh.lumen.data.ai.HfTokenStore
import com.subhashh.lumen.data.ai.ModelDownloads
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
) {
    val context = LocalContext.current
    val model = ModelDownloads.model
    val state by ModelDownloads.state.collectAsState()
    var removed by remember { mutableIntStateOf(0) }
    val installed = remember(refresh, state, removed) { model.installed(context) }
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

    val size = megabytes(model.bytes)
    val (status, action) = when (val s = state) {
        is DownloadState.Running -> "Downloading · ${megabytes(s.done)} of ${megabytes(s.total)}" to "Cancel"
        DownloadState.Waiting -> (if (wifiOnly) "Waiting for Wi-Fi…" else "Waiting for a connection…") to "Cancel"
        is DownloadState.Failed -> "Couldn't download: ${s.message}" to "Retry"
        DownloadState.Idle -> if (installed) "Ready · $size on this phone" to "Remove" else "Not downloaded · $size" to "Download"
    }

    Group(
        "Smart Search",
        footer = "Find photos by what's in them, like “cat asleep on a sofa”, in many languages. " +
            "The search model (Google's SigLIP 2) downloads once from Hugging Face; your photos " +
            "and searches never leave this phone.",
    ) {
        SettingsRow(
            title = "${model.title} Model",
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
            icon = LumenIcons.Search,
            iconColor = Indigo,
        )
        Divider()
        ToggleRow(
            title = "Download on Wi-Fi Only",
            checked = wifiOnly,
            onCheckedChange = {
                wifiOnly = it
                prefs.modelWifiOnly = it
            },
            icon = LumenIcons.Import,
            iconColor = Blue,
        )
        Divider()
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
