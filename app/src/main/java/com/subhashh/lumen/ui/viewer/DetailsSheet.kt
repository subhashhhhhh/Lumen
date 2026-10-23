package com.subhashh.lumen.ui.viewer

import com.subhashh.lumen.ui.icons.LumenIcons
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.CameraInfo
import com.subhashh.lumen.data.GeoPoint
import com.subhashh.lumen.data.MediaDetails
import com.subhashh.lumen.data.MediaDetailsLoader
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.PlaceLookup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SheetColors(val sheet: Color, val card: Color, val text: Color, val secondary: Color)

@Composable
private fun sheetColors() = if (LocalDarkTheme.current) {
    SheetColors(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color.White, Color(0x99EBEBF5))
} else {
    SheetColors(Color(0xFFF2F2F7), Color.White, Color.Black, Color(0x993C3C43))
}

/**
 * Swipe-up details for one item. [locationRefresh] bumps when location access is granted, so the
 * details reload with unredacted GPS.
 */
@Composable
fun DetailsSheet(
    item: MediaItem,
    locationRefresh: Int,
    onOpenMap: (GeoPoint) -> Unit,
    onAllowLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = sheetColors()
    val details by produceState<MediaDetails?>(null, item.id, locationRefresh) {
        value = MediaDetailsLoader.load(context, item)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.sheet)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.4f)),
        )

        val dateTime = Instant.ofEpochMilli(item.takenAt).atZone(ZoneId.systemDefault())
        Text(
            dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            color = colors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            color = colors.secondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))

        val d = details ?: return@Column
        Card(colors) { MediaCard(item, d, colors) }

        when {
            d.location != null -> {
                Spacer(Modifier.height(12.dp))
                Card(colors) { LocationCard(d.location, colors, onOpenMap) }
            }
            !d.locationReadable -> {
                Spacer(Modifier.height(12.dp))
                Card(colors) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LumenIcons.Location, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Allow location access to see where photos were taken",
                            color = colors.secondary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Allow",
                            color = Color(0xFF0A84FF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onAllowLocation),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(colors) {
            Text(d.fileName, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(d.folder.trimEnd('/').ifEmpty { "Internal storage" }, color = colors.secondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Card(colors: SheetColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun MediaCard(item: MediaItem, d: MediaDetails, colors: SheetColors) {
    val context = LocalContext.current
    val camera = d.camera
    val icon: ImageVector = when {
        item.isVideo -> LumenIcons.Video
        camera?.device != null -> LumenIcons.Camera
        else -> LumenIcons.Image
    }
    val title = camera?.device ?: formatName(d.mimeType, item.isVideo)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = colors.secondary.copy(alpha = 0.2f))
    Spacer(Modifier.height(10.dp))

    val facts = buildList {
        if (!item.isVideo && d.width > 0) {
            val mp = d.width.toLong() * d.height / 1_000_000.0
            if (mp >= 1) add("${mp.roundToInt()} MP")
        }
        if (d.width > 0) add("${d.width} × ${d.height}")
        d.frameRate?.let { add("${it.roundToInt()} fps") }
        if (d.sizeBytes > 0) add(Formatter.formatShortFileSize(context, d.sizeBytes))
        if (item.isVideo && item.durationMs > 0) add(formatDuration(item.durationMs))
    }
    Text(facts.joinToString("  ·  "), color = colors.text, fontSize = 14.sp)

    if (camera != null) {
        camera.lens?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = colors.secondary, fontSize = 13.sp)
        }
        val exposure = exposureFacts(camera)
        if (exposure.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exposure.forEach { fact ->
                    Text(
                        fact,
                        color = colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.secondary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(location: GeoPoint, colors: SheetColors, onOpenMap: (GeoPoint) -> Unit) {
    val context = LocalContext.current
    val place by produceState<String?>(null, location) { value = PlaceLookup.describe(context, location) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(LumenIcons.Location, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(place ?: "Location", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(formatCoordinates(location), color = colors.secondary, fontSize = 13.sp)
        }
        Text(
            "Open in Maps",
            color = Color(0xFF0A84FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onOpenMap(location) },
        )
    }
}

private fun exposureFacts(c: CameraInfo) = buildList {
    c.iso?.let { add("ISO $it") }
    c.focalLengthMm?.let { add("${formatNumber(it)} mm") }
    c.aperture?.let { add("ƒ${formatNumber(it)}") }
    c.exposure?.let { add(if (it < 1) "1/${(1 / it).roundToInt()} s" else "${formatNumber(it)} s") }
}

private fun formatNumber(v: Double): String =
    if (abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else "%.1f".format(Locale.US, v)

private fun formatName(mime: String, isVideo: Boolean): String {
    val subtype = mime.substringAfter('/').uppercase()
    return when {
        subtype == "*" || subtype.isEmpty() -> if (isVideo) "Video" else "Photo"
        isVideo -> "$subtype video"
        else -> subtype
    }
}

private fun formatCoordinates(p: GeoPoint): String {
    val lat = "%.4f° %s".format(Locale.US, abs(p.latitude), if (p.latitude >= 0) "N" else "S")
    val lon = "%.4f° %s".format(Locale.US, abs(p.longitude), if (p.longitude >= 0) "E" else "W")
    return "$lat, $lon"
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
