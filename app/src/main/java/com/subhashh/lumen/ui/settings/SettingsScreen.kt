package com.subhashh.lumen.ui.settings

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
import androidx.compose.foundation.isSystemInDarkTheme
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
internal fun groupColors(): GroupColors = if (isSystemInDarkTheme()) {
    GroupColors(Color(0xFF1C1C1E), Color.White, Color(0x99EBEBF5), Color(0x33EBEBF5))
} else {
    GroupColors(Color(0xFFF2F2F7), Color.Black, Color(0x993C3C43), Color(0x333C3C43))
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenLicenses: () -> Unit,
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
            SettingsRow(title = "Excluded folders", subtitle = "Folders Lumen ignores", value = "Soon", onClick = null)
            Divider()
            SettingsRow(title = "Hidden folders", subtitle = "Hidden from every app, locked in Lumen", value = "Soon", onClick = null)
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

@Composable
internal fun Divider() {
    HorizontalDivider(color = groupColors().divider, modifier = Modifier.padding(start = 16.dp))
}
