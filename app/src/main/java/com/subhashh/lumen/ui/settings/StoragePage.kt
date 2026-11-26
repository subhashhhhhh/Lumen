package com.subhashh.lumen.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.GridThumbnailFetcher
import com.subhashh.lumen.data.StorageUsage
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.Lumen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Storage: what Lumen keeps on this phone and what can go. Lumen's style is laid out
 * like macOS's Storage (one bar split by kind, recommendations, then the kinds); Material's like
 * Android's (a big total, free-up cards, a bar under each kind).
 */
@Composable
internal fun StoragePage(highlight: String?, onConfirm: (Confirmation) -> Unit, onOpenSmartFeatures: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val usage by produceState<StorageUsage?>(null, version) { value = StorageUsage.measure(context) }
    val u = usage
    if (u == null) {
        Text(stringResource(R.string.storage_measuring), color = groupColors().secondary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        return
    }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    val recommendations = buildList {
        if (u.oldSearch > 0) add(
            Recommendation(
                LumenIcons.Search, Indigo,
                context.getString(R.string.storage_old_search),
                context.getString(R.string.storage_old_search_detail, size(u.oldSearch)),
                context.getString(R.string.common_remove),
            ) {
                onConfirm(
                    Confirmation(
                        title = context.getString(R.string.storage_old_search),
                        message = context.getString(R.string.storage_old_search_detail, size(u.oldSearch)),
                        confirm = context.getString(R.string.common_remove),
                        destructive = true,
                    ) { scope.launch { StorageUsage.removeOldSearch(context); version++ } },
                )
            },
        )
        if (u.thumbnails > 50L * 1024 * 1024) add(
            Recommendation(
                LumenIcons.Image, Orange,
                context.getString(R.string.storage_clear_thumbnails),
                context.getString(R.string.storage_clear_thumbnails_detail, size(u.thumbnails)),
                context.getString(R.string.storage_clear),
            ) {
                scope.launch {
                    withContext(Dispatchers.IO) { GridThumbnailFetcher.Factory.clear(context) }
                    coil3.SingletonImageLoader.get(context).memoryCache?.clear()
                    version++
                }
            },
        )
    }

    if (Lumen.material) {
        MaterialHeader(u, ::size)
        for (r in recommendations) MaterialCard(r)
        MaterialKinds(u, ::size, onOpenSmartFeatures)
    } else {
        LumenHeader(u, ::size)
        if (recommendations.isNotEmpty()) {
            Group(stringResource(R.string.storage_recommendations)) {
                recommendations.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    LumenRecommendation(r)
                }
            }
        }
        Group("") {
            u.kinds.forEachIndexed { i, kind ->
                if (i > 0) Divider()
                SettingsRow(
                    title = stringResource(kind.type.label()),
                    subtitle = kind.parts.takeIf { it.size > 1 }?.joinToString("  ·  ") { context.getString(it.label) + " " + size(it.bytes) },
                    value = size(kind.bytes),
                    onClick = if (kind.type == StorageUsage.Type.Models) onOpenSmartFeatures else null,
                    icon = kind.type.icon(),
                    iconColor = kind.type.color(),
                )
            }
        }
        if (u.trash > 0) {
            Group("", footer = stringResource(R.string.storage_trash_footer)) {
                SettingsRow(title = stringResource(R.string.storage_trash), value = size(u.trash), onClick = null, icon = LumenIcons.Delete, iconColor = Gray)
            }
        }
    }
}

private class Recommendation(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val detail: String,
    val action: String,
    val onClick: () -> Unit,
)

/** macOS: the volume's name and total, one bar split by kind, and a legend under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LumenHeader(u: StorageUsage, size: (Long) -> String) {
    val colors = groupColors()
    Group("", footer = stringResource(R.string.storage_phone_free, size(u.phoneFree), size(u.phoneTotal))) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), color = colors.text, fontSize = 16.sp, fontWeight = Lumen.type.emphasis, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.storage_used, size(u.total)), color = colors.secondary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            SplitBar(u, Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)), gap = 1.dp) { it.color() }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (kind in u.kinds.filter { it.bytes > 0 }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(kind.type.color()))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(kind.type.label()), color = colors.secondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LumenRecommendation(r: Recommendation) {
    val colors = groupColors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(r.icon, r.color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = colors.text, fontSize = 16.sp)
            Text(r.detail, color = colors.secondary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        // A button, as macOS has it: "Remove…", "Clear…".
        Text(
            r.action,
            color = colors.text,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.secondary.copy(alpha = 0.18f))
                .clickable(onClick = r.onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Android: the total in big type, the phone's free space beside it, and the split under it. */
@Composable
private fun MaterialHeader(u: StorageUsage, size: (Long) -> String) {
    val scheme = MaterialTheme.colorScheme
    val total = size(u.total)
    // "2.4 GB" → "2.4" big, "GB used" beside it.
    val number = total.substringBeforeLast(' ', total)
    val unit = total.substringAfterLast(' ', "")
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(number, color = scheme.onSurface, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.storage_used, unit), color = scheme.onSurface, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp).weight(1f))
            Text(stringResource(R.string.storage_free, size(u.phoneFree)), color = scheme.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        Spacer(Modifier.height(14.dp))
        SplitBar(u, Modifier.fillMaxWidth().height(14.dp).clip(CircleShape), gap = 2.dp) { it.materialColor() }
    }
}

@Composable
private fun MaterialCard(r: Recommendation) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.secondaryContainer)
            .clickable(onClick = r.onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(scheme.surface), contentAlignment = Alignment.Center) {
            Icon(r.icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = scheme.onSecondaryContainer, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(r.detail, color = scheme.onSecondaryContainer, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(r.action, color = scheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Android: each kind with its icon, size, and a thin bar of its share. */
@Composable
private fun MaterialKinds(u: StorageUsage, size: (Long) -> String, onOpenSmartFeatures: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val rows = u.kinds.map { Triple(it.type.label(), it.type.icon(), it) }
    for ((label, icon, kind) in rows) {
        MaterialKindRow(
            stringResource(label), icon, size(kind.bytes), if (u.total > 0) kind.bytes.toFloat() / u.total else 0f,
            kind.parts.takeIf { it.size > 1 }?.joinToString("  ·  ") { context.getString(it.label) + " " + size(it.bytes) },
            onClick = if (kind.type == StorageUsage.Type.Models) onOpenSmartFeatures else null,
        )
    }
    if (u.trash > 0) {
        MaterialKindRow(stringResource(R.string.storage_trash), LumenIcons.Delete, size(u.trash), null, stringResource(R.string.storage_trash_footer), null)
    }
}

@Composable
private fun MaterialKindRow(title: String, icon: ImageVector, size: String, share: Float?, detail: String?, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(26.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = scheme.onSurface, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text(size, color = scheme.onSurface, fontSize = 20.sp)
            }
            if (detail != null) Text(detail, color = scheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            if (share != null) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(scheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(scheme.primary))
                }
            }
        }
    }
}

/** One bar, a segment per kind in proportion to its size. */
@Composable
private fun SplitBar(u: StorageUsage, modifier: Modifier, gap: androidx.compose.ui.unit.Dp, color: @Composable (StorageUsage.Type) -> Color) {
    val kinds = u.kinds.filter { it.bytes > 0 }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (kind in kinds) {
            Box(Modifier.weight(kind.bytes.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(color(kind.type)))
        }
    }
}

private fun StorageUsage.Type.label() = when (this) {
    StorageUsage.Type.App -> R.string.storage_app
    StorageUsage.Type.Models -> R.string.storage_models
    StorageUsage.Type.Indexes -> R.string.storage_indexes
    StorageUsage.Type.Caches -> R.string.storage_caches
    StorageUsage.Type.Other -> R.string.storage_other
}

private fun StorageUsage.Type.icon() = when (this) {
    StorageUsage.Type.App -> LumenIcons.Code
    StorageUsage.Type.Models -> LumenIcons.Folder
    StorageUsage.Type.Indexes -> LumenIcons.Search
    StorageUsage.Type.Caches -> LumenIcons.Image
    StorageUsage.Type.Other -> LumenIcons.More
}

/** macOS's legend colours: a distinct hue per kind. */
private fun StorageUsage.Type.color() = when (this) {
    StorageUsage.Type.App -> Blue
    StorageUsage.Type.Models -> Purple
    StorageUsage.Type.Indexes -> Orange
    StorageUsage.Type.Caches -> Green
    StorageUsage.Type.Other -> Gray
}

/** Material: the scheme's own tones, so the bar follows the wallpaper. */
@Composable
private fun StorageUsage.Type.materialColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        StorageUsage.Type.App -> scheme.primary
        StorageUsage.Type.Models -> scheme.tertiary
        StorageUsage.Type.Indexes -> scheme.secondary
        StorageUsage.Type.Caches -> scheme.inversePrimary
        StorageUsage.Type.Other -> scheme.outline
    }
}
