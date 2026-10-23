package com.subhashh.lumen.ui.settings

import com.subhashh.lumen.ui.theme.LocalMotion
import com.subhashh.lumen.data.HdrMode
import androidx.compose.runtime.mutableStateOf
import com.subhashh.lumen.data.LumenPrefs
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import com.subhashh.lumen.ui.theme.Appearance
import com.subhashh.lumen.ui.theme.LocalAppearance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.subhashh.lumen.ui.icons.LumenIcons

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenLicenses: () -> Unit,
    onOpenExcluded: () -> Unit,
    onOpenHidden: () -> Unit,
    hiddenLockSet: Boolean,
    onChangeLock: () -> Unit,
    biometricsOn: Boolean,
    onToggleBiometrics: () -> Unit,
    excludedCount: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Permissions change in system settings; re-read them whenever we come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val status = remember(refresh) { PermissionStatus.read(context) }
    val prefs = remember { LumenPrefs(context) }

    SettingsPage(contentPadding, modifier) {
        LargeTitle("Settings")

        val appearance = LocalAppearance.current
        Group("Appearance") {
            PickerRow(
                title = "Theme",
                options = Appearance.entries.map { it.label },
                selected = appearance.current.ordinal,
                onSelect = { appearance.onChange(Appearance.entries[it]) },
                icon = LumenIcons.DarkTheme,
                iconColor = Indigo,
            )
            Divider()
            val motion = LocalMotion.current
            ToggleRow(
                title = "Reduce Motion",
                subtitle = "No flying photos, springs or bounces",
                checked = motion.reduce,
                onCheckedChange = motion.onChange,
                icon = LumenIcons.Accessibility,
                iconColor = Blue,
            )
        }

        var hdrMode by remember { mutableStateOf(prefs.hdrMode) }
        var autoplay by remember { mutableStateOf(prefs.videoAutoplay) }
        var loop by remember { mutableStateOf(prefs.videoLoop) }
        val hdrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        Group(
            "Photos & Videos",
            footer = if (hdrSupported) {
                "Ultra HDR photos can glow brighter than white. Match Brightness keeps to your brightness; " +
                    "Full HDR turns the screen all the way up while an HDR photo or video is shown, like Chrome."
            } else null,
        ) {
            if (hdrSupported) {
                PickerRow(
                    title = "Ultra HDR",
                    options = HdrMode.entries.map { it.label },
                    selected = hdrMode.ordinal,
                    onSelect = {
                        hdrMode = HdrMode.entries[it]
                        prefs.hdrMode = hdrMode
                    },
                    icon = LumenIcons.Hdr,
                    iconColor = Orange,
                )
                Divider()
            }
            ToggleRow(
                title = "Autoplay Videos",
                checked = autoplay,
                onCheckedChange = {
                    autoplay = it
                    prefs.videoAutoplay = it
                },
                icon = LumenIcons.Play,
                iconColor = Blue,
            )
            Divider()
            ToggleRow(
                title = "Loop Videos",
                subtitle = "Start over at the end",
                checked = loop,
                onCheckedChange = {
                    loop = it
                    prefs.videoLoop = it
                },
                icon = LumenIcons.Repeat,
                iconColor = Teal,
            )
        }

        var slideSeconds by remember { mutableStateOf(prefs.slideshowSeconds) }
        var slideFade by remember { mutableStateOf(prefs.slideshowFade) }
        var slideShuffle by remember { mutableStateOf(prefs.slideshowShuffle) }
        var slideRepeat by remember { mutableStateOf(prefs.slideshowRepeat) }
        Group("Slideshow") {
            PickerRow(
                title = "Each Photo For",
                options = SlideSeconds.map { "$it seconds" },
                selected = SlideSeconds.indexOf(slideSeconds).coerceAtLeast(0),
                onSelect = {
                    slideSeconds = SlideSeconds[it]
                    prefs.slideshowSeconds = slideSeconds
                },
                icon = LumenIcons.Timer,
                iconColor = Purple,
            )
            Divider()
            PickerRow(
                title = "Transition",
                options = listOf("Slide", "Fade"),
                selected = if (slideFade) 1 else 0,
                onSelect = {
                    slideFade = it == 1
                    prefs.slideshowFade = slideFade
                },
                icon = LumenIcons.SlideTransition,
                iconColor = Pink,
            )
            Divider()
            ToggleRow(
                title = "Shuffle",
                checked = slideShuffle,
                onCheckedChange = {
                    slideShuffle = it
                    prefs.slideshowShuffle = it
                },
                icon = LumenIcons.Shuffle,
                iconColor = Orange,
            )
            Divider()
            ToggleRow(
                title = "Repeat",
                subtitle = "Start over at the end",
                checked = slideRepeat,
                onCheckedChange = {
                    slideRepeat = it
                    prefs.slideshowRepeat = it
                },
                icon = LumenIcons.Repeat,
                iconColor = Teal,
            )
        }

        Group("Privacy", footer = "Hidden has its own lock, separate from your phone's screen lock.") {
            SettingsRow(
                title = "Hidden",
                subtitle = "Hidden from every app, locked in Lumen",
                onClick = onOpenHidden,
                icon = LumenIcons.Hide,
                iconColor = Indigo,
            )
            Divider()
            SettingsRow(
                title = "Excluded Folders",
                subtitle = "Folders Lumen ignores",
                value = if (excludedCount == 0) "None" else excludedCount.toString(),
                onClick = onOpenExcluded,
                icon = LumenIcons.FolderProhibited,
                iconColor = Gray,
            )
            Divider()
            SettingsRow(
                title = if (hiddenLockSet) "Change Hidden Lock" else "Set Up Hidden Lock",
                onClick = onChangeLock,
                icon = LumenIcons.Lock,
                iconColor = Blue,
            )
            if (hiddenLockSet) {
                Divider()
                ToggleRow(
                    title = "Fingerprint or Face",
                    subtitle = "Unlock Hidden faster",
                    checked = biometricsOn,
                    onCheckedChange = { onToggleBiometrics() },
                    icon = LumenIcons.Fingerprint,
                    iconColor = Red,
                )
            }
        }

        Group("Permissions", footer = "Lumen works without these; they unlock the features named. Each opens Android's settings.") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsRow(
                    title = "Media Management",
                    subtitle = "Delete and favorite without asking each time",
                    value = if (status.manageMedia) "On" else "Off",
                    onClick = { context.openSettings(Settings.ACTION_REQUEST_MANAGE_MEDIA) },
                    icon = LumenIcons.Shield,
                    iconColor = Green,
                )
                Divider()
            }
            SettingsRow(
                title = "All Files Access",
                subtitle = "Hidden folders, renaming albums, dates on videos",
                value = if (status.allFiles) "On" else "Off",
                onClick = { context.openSettings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) },
                icon = LumenIcons.FolderOpen,
                iconColor = Blue,
            )
            Divider()
            SettingsRow(
                title = "Location in Photos",
                subtitle = "Show where photos were taken",
                value = if (status.mediaLocation) "On" else "Off",
                onClick = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                icon = LumenIcons.Location,
                iconColor = Green,
            )
        }

        Group("Backup", footer = "The Hidden lock is never included, so a backup can't unlock Hidden.") {
            SettingsRow(
                title = "Export Settings…",
                subtitle = "Settings, album arrangement and favorites",
                onClick = onExport,
                icon = LumenIcons.Export,
                iconColor = Green,
            )
            Divider()
            SettingsRow(
                title = "Import Settings…",
                subtitle = "From a Lumen backup file",
                onClick = onImport,
                icon = LumenIcons.Import,
                iconColor = Blue,
            )
        }

        Group("About") {
            SettingsRow(
                title = "Open-Source Licenses",
                onClick = onOpenLicenses,
                icon = LumenIcons.Document,
                iconColor = Gray,
            )
            Divider()
            SettingsRow(
                title = "Version",
                value = context.versionName(),
                onClick = null,
                icon = LumenIcons.Info,
                iconColor = Gray,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Slideshow intervals the row cycles through, in seconds. */
private val SlideSeconds = listOf(2, 3, 5, 10)

private data class PermissionStatus(val manageMedia: Boolean, val allFiles: Boolean, val mediaLocation: Boolean) {
    companion object {
        fun read(context: Context) = PermissionStatus(
            manageMedia = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context),
            allFiles = Environment.isExternalStorageManager(),
            mediaLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
}

/** Opens a per-app system settings page for Lumen. */
private fun Context.openSettings(action: String) {
    val intent = Intent(action, "package:$packageName".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

private val Indigo = Color(0xFF5E5CE6)
private val Blue = Color(0xFF0A84FF)
private val Teal = Color(0xFF30B0C7)
private val Purple = Color(0xFFBF5AF2)
private val Pink = Color(0xFFFF375F)
private val Orange = Color(0xFFFF9F0A)
private val Green = Color(0xFF30D158)
private val Red = Color(0xFFFF453A)
