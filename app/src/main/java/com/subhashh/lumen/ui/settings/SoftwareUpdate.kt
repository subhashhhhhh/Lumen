package com.subhashh.lumen.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.Updater
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What Software Update is doing. Lives outside Settings, so a download carries on if you leave. */
object UpdateSession {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Result(val check: Updater.Check) : State
        data class Downloading(val fraction: Float) : State
        data object Installing : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun check(context: Context) {
        if (state is State.Checking || state is State.Downloading) return
        state = State.Checking
        scope.launch {
            val result = Updater.check(context)
            LumenPrefs(context).availableUpdate = (result as? Updater.Check.Available)?.release?.version
            state = State.Result(result)
        }
    }

    /** Once a day on launch, when Check Automatically is on. */
    fun checkIfDue(context: Context) {
        val prefs = LumenPrefs(context)
        if (!prefs.autoUpdateCheck || System.currentTimeMillis() - prefs.lastUpdateCheck < DayMs) return
        check(context)
    }

    fun install(context: Context, release: Updater.Release) {
        if (state is State.Downloading || state is State.Installing) return
        if (!Updater.canInstall(context)) {
            // Android's per-app "Install unknown apps" switch, for Lumen.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            toast(context, "Allow Lumen to install apps, then tap Install again")
            return
        }
        state = State.Downloading(0f)
        scope.launch {
            try {
                val apk = Updater.download(context, release) { f -> scope.launch { state = State.Downloading(f) } }
                if (!Updater.signedLikeUs(context, apk)) {
                    apk.delete()
                    toast(context, "This update isn't signed like your Lumen, so it wasn't installed")
                    state = State.Result(Updater.Check.Available(release))
                    return@launch
                }
                state = State.Installing
                Updater.install(context, apk)
            } catch (e: Exception) {
                toast(context, "The update couldn't be downloaded")
            }
            state = State.Result(Updater.Check.Available(release))
        }
    }

    private fun toast(context: Context, message: String) =
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()

    private const val DayMs = 24L * 60 * 60 * 1000
}

/** About → Software Update: the latest version on GitHub, and installing it. */
@Composable
internal fun ColumnScope.SoftwareUpdateGroup(highlight: String?, onConfirm: (Confirmation) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LumenPrefs(context) }
    var auto by remember { mutableStateOf(prefs.autoUpdateCheck) }
    val state = UpdateSession.state
    val available = ((state as? UpdateSession.State.Result)?.check as? Updater.Check.Available)?.release
    Group(
        "Software Update",
        footer = "Updates come from Lumen's releases on GitHub. Checking only asks GitHub for the latest " +
            "version; nothing about you or your photos is sent.",
    ) {
        Anchor("software-update", highlight) {
            SettingsRow(
                title = if (available != null) "Lumen ${available.version}" else "Software Update",
                value = when (state) {
                    UpdateSession.State.Idle -> prefs.availableUpdate?.let { "Version $it" } ?: "Check Now"
                    UpdateSession.State.Checking -> "Checking…"
                    is UpdateSession.State.Downloading -> "Downloading ${(state.fraction * 100).toInt()}%"
                    UpdateSession.State.Installing -> "Installing…"
                    is UpdateSession.State.Result -> when (val check = state.check) {
                        Updater.Check.UpToDate -> "Up to Date"
                        Updater.Check.NoReleases -> "No Releases Yet"
                        is Updater.Check.Failed -> "Couldn't Check"
                        is Updater.Check.Available -> "Install"
                    }
                },
                onClick = {
                    if (available != null) {
                        val size = if (available.apkBytes > 0) " (%.0f MB)".format(available.apkBytes / 1_000_000f) else ""
                        val notes = available.notes.take(600).let { if (available.notes.length > 600) "$it…" else it }
                        onConfirm(
                            Confirmation(
                                title = "Install Lumen ${available.version}?",
                                message = (notes.ifBlank { "A new version of Lumen is out." }) +
                                    "\n\nDownloads the update$size from GitHub, then Android asks to install it.",
                                confirm = "Install",
                                onConfirm = { UpdateSession.install(context, available) },
                            ),
                        )
                    } else {
                        UpdateSession.check(context)
                    }
                },
                icon = LumenIcons.Import,
                iconColor = Blue,
            )
        }
        Divider()
        Anchor("auto-update", highlight) {
            ToggleRow(
                title = "Check Automatically",
                checked = auto,
                onCheckedChange = {
                    auto = it
                    prefs.autoUpdateCheck = it
                },
                subtitle = "Once a day, when Lumen opens",
                icon = LumenIcons.Repeat,
                iconColor = Gray,
            )
        }
    }
}
