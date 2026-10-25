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
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.Updater
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.releaseChanges
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
            toast(context, context.getString(R.string.settings_update_allow_install))
            return
        }
        state = State.Downloading(0f)
        scope.launch {
            try {
                val apk = Updater.download(context, release) { f -> scope.launch { state = State.Downloading(f) } }
                if (!Updater.signedLikeUs(context, apk)) {
                    apk.delete()
                    toast(context, context.getString(R.string.settings_update_not_signed))
                    state = State.Result(Updater.Check.Available(release))
                    return@launch
                }
                state = State.Installing
                Updater.install(context, apk)
            } catch (e: Exception) {
                toast(context, context.getString(R.string.settings_update_download_failed))
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
        stringResource(R.string.settings_software_update),
        footer = stringResource(R.string.settings_software_update_footer),
    ) {
        Anchor("software-update", highlight) {
            SettingsRow(
                title = if (available != null) {
                    stringResource(R.string.settings_update_lumen_version, available.version)
                } else {
                    stringResource(R.string.settings_software_update)
                },
                value = when (state) {
                    UpdateSession.State.Idle -> prefs.availableUpdate?.let { stringResource(R.string.settings_version_value, it) }
                        ?: stringResource(R.string.settings_update_check_now)
                    UpdateSession.State.Checking -> stringResource(R.string.settings_update_checking)
                    is UpdateSession.State.Downloading -> stringResource(R.string.settings_update_downloading, (state.fraction * 100).toInt())
                    UpdateSession.State.Installing -> stringResource(R.string.settings_update_installing)
                    is UpdateSession.State.Result -> when (val check = state.check) {
                        Updater.Check.UpToDate -> stringResource(R.string.settings_update_up_to_date)
                        Updater.Check.NoReleases -> stringResource(R.string.settings_update_no_releases)
                        is Updater.Check.Failed -> stringResource(R.string.settings_update_check_failed)
                        is Updater.Check.Available -> stringResource(R.string.common_install)
                    }
                },
                onClick = {
                    if (available != null) {
                        val message = if (available.apkBytes > 0) {
                            context.getString(R.string.settings_update_confirm_message_size, available.apkBytes / 1_000_000f)
                        } else {
                            context.getString(R.string.settings_update_confirm_message)
                        }
                        onConfirm(
                            Confirmation(
                                title = context.getString(R.string.settings_update_confirm_title, available.version),
                                message = message,
                                details = releaseChanges(available.notes).ifBlank { null },
                                confirm = context.getString(R.string.common_install),
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
                title = stringResource(R.string.settings_update_check_automatically),
                checked = auto,
                onCheckedChange = {
                    auto = it
                    prefs.autoUpdateCheck = it
                },
                subtitle = stringResource(R.string.settings_update_check_automatically_subtitle),
                icon = LumenIcons.Repeat,
                iconColor = Gray,
            )
        }
    }
}
