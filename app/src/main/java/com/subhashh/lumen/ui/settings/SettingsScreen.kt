package com.subhashh.lumen.ui.settings

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

internal data class GroupColors(val card: Color, val text: Color, val secondary: Color, val divider: Color)

@Composable
internal fun groupColors(): GroupColors = if (LocalDarkTheme.current) {
    GroupColors(Color(0xFF1C1C1E), Color.White, Color(0x99EBEBF5), Color(0x33EBEBF5))
} else {
    GroupColors(Color(0xFFF2F2F7), Color.Black, Color(0x993C3C43), Color(0x333C3C43))
}

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

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        LargeTitle("Settings")

        val appearance = LocalAppearance.current
        Group("Appearance") {
            Appearance.entries.forEachIndexed { i, option ->
                if (i > 0) Divider()
                CheckRow(option.label, checked = option == appearance.current) { appearance.onChange(option) }
            }
        }

        Group("Permissions", footer = "Lumen works without these; they unlock the features named.") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsRow(
                    title = "Media management",
                    subtitle = "Delete and favorite without a confirmation each time",
                    value = if (status.manageMedia) "On" else "Off",
                    onClick = { context.openSettings(Settings.ACTION_REQUEST_MANAGE_MEDIA) },
                )
                Divider()
            }
            SettingsRow(
                title = "All files access",
                subtitle = "Needed for hidden folders (.nomedia)",
                value = if (status.allFiles) "On" else "Off",
                onClick = { context.openSettings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) },
            )
            Divider()
            SettingsRow(
                title = "Location in photos",
                subtitle = "Show where photos were taken",
                value = if (status.mediaLocation) "On" else "Off",
                onClick = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
            )
        }

        Group("Folders") {
            SettingsRow(
                title = "Excluded folders",
                subtitle = "Folders Lumen ignores",
                value = if (excludedCount == 0) "None" else excludedCount.toString(),
                onClick = onOpenExcluded,
            )
            Divider()
            SettingsRow(title = "Hidden", subtitle = "Hidden from every app, locked in Lumen", onClick = onOpenHidden)
        }

        val prefs = remember { LumenPrefs(context) }
        var autoplay by remember { mutableStateOf(prefs.videoAutoplay) }
        var loop by remember { mutableStateOf(prefs.videoLoop) }
        Group("Videos") {
            SettingsRow(
                title = "Autoplay",
                subtitle = "Start playing as soon as a video opens",
                value = if (autoplay) "On" else "Off",
                onClick = {
                    autoplay = !autoplay
                    prefs.videoAutoplay = autoplay
                },
            )
            Divider()
            SettingsRow(
                title = "Loop",
                subtitle = "Start over at the end instead of stopping",
                value = if (loop) "On" else "Off",
                onClick = {
                    loop = !loop
                    prefs.videoLoop = loop
                },
            )
        }

        Group("Hidden lock", footer = "Lumen's own lock for Hidden, separate from your phone's screen lock.") {
            SettingsRow(title = if (hiddenLockSet) "Change Lock" else "Set Up Lock", onClick = onChangeLock)
            if (hiddenLockSet) {
                Divider()
                SettingsRow(
                    title = "Fingerprint or Face",
                    subtitle = "Unlock Hidden faster",
                    value = if (biometricsOn) "On" else "Off",
                    onClick = onToggleBiometrics,
                )
            }
        }

        Group("About") {
            SettingsRow(title = "Open-source licenses", onClick = onOpenLicenses)
            Divider()
            SettingsRow(title = "Version", value = context.versionName(), onClick = null)
        }
    }
}

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

@Composable
internal fun LargeTitle(text: String) {
    Text(
        text,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** iOS-style grouped section: small caps header, rounded card, optional footer. */
@Composable
internal fun Group(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = groupColors()
    Text(
        title.uppercase(),
        fontSize = 13.sp,
        color = colors.secondary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card),
        content = content,
    )
    if (footer != null) {
        Text(
            footer,
            fontSize = 13.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)?,
) {
    val colors = groupColors()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.text)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = colors.secondary)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 16.sp, color = colors.secondary)
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                LumenIcons.Back,
                contentDescription = null,
                tint = colors.secondary,
                // The back chevron, turned around: there's no forward one in the icon set yet.
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f },
            )
        }
    }
}

/** A row in a pick-one group: the chosen one gets a checkmark, like iOS. */
@Composable
private fun CheckRow(title: String, checked: Boolean, onClick: () -> Unit) {
    val colors = groupColors()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = colors.text, modifier = Modifier.weight(1f))
        if (checked) {
            Icon(LumenIcons.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun Divider() {
    HorizontalDivider(color = groupColors().divider, modifier = Modifier.padding(start = 16.dp))
}
