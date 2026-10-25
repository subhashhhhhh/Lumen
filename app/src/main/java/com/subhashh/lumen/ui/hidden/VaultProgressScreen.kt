package com.subhashh.lumen.ui.hidden

import android.os.SystemClock
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.vault.VaultMigration
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.LocalMotion
import kotlin.random.Random

/**
 * Moving Hidden into or out of the vault. A ring fills around a lock, the file being worked on
 * dissolves into ciphertext (or resolves out of it when decrypting), and the numbers say how
 * far along it is and roughly how long is left. Covers the app: it's quick to wait out, and
 * the screen stays on so it isn't cut short.
 */
@Composable
fun VaultProgressScreen(encrypting: Boolean, progress: VaultMigration.Progress?) {
    BackHandler { }
    val context = LocalContext.current
    val reduce = LocalMotion.current.reduce
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val fraction = progress?.let {
        when {
            it.bytesTotal > 0 -> it.bytesDone.toFloat() / it.bytesTotal
            it.total > 0 -> it.done.toFloat() / it.total
            else -> 0f
        }
    } ?: 0f
    val shown by animateFloatAsState(fraction, if (reduce) tween(0) else spring(stiffness = 120f), label = "vaultRing")

    // Time left from the speed so far; needs a few seconds of history to mean anything.
    val startedAt = remember { SystemClock.elapsedRealtime() }
    val remaining = progress?.let {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (elapsed < 3000 || it.bytesDone <= 0 || it.bytesTotal <= 0) null
        else (elapsed * (it.bytesTotal - it.bytesDone) / it.bytesDone)
    }

    val accent = if (encrypting) Color(0xFF30D158) else Color(0xFF0A84FF)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color(0xFF050507)), radius = 1400f))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                val spin = rememberInfiniteTransition(label = "vaultGlow")
                val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "glowAngle")
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2 + 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(accent.copy(alpha = 0.6f), accent, Color(0xFF64D2FF), accent.copy(alpha = 0.6f))),
                        -90f, 360f * shown, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                // A soft highlight orbiting the ring: working, even between files.
                if (!reduce) {
                    Canvas(Modifier.fillMaxSize().rotate(angle)) {
                        val stroke = 12.dp.toPx()
                        val inset = stroke / 2 + 6.dp.toPx()
                        drawArc(
                            Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent)),
                            -90f, 50f, false, Offset(inset, inset),
                            Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (encrypting) LumenIcons.Lock else LumenIcons.Unhide,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.hidden_vault_percent, (shown * 100).toInt()),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            CipherText(name = progress?.current.orEmpty(), encrypting = encrypting, animate = !reduce, accent = accent)
            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    progress == null || progress.total == 0 ->
                        stringResource(if (encrypting) R.string.hidden_vault_getting_ready else R.string.hidden_vault_opening)
                    else -> stringResource(
                        if (encrypting) R.string.hidden_vault_encrypting else R.string.hidden_vault_decrypting,
                        minOf(progress.done + 1, progress.total),
                        progress.total,
                    )
                },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (progress != null && progress.bytesTotal > 0) {
                val sizes = stringResource(
                    R.string.hidden_vault_bytes,
                    Formatter.formatShortFileSize(context, progress.bytesDone),
                    Formatter.formatShortFileSize(context, progress.bytesTotal),
                )
                Text(
                    remaining?.let { stringResource(R.string.hidden_vault_bytes_time, sizes, timeLeft(context, it)) } ?: sizes,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(36.dp))
            Text(
                if (encrypting) {
                    stringResource(R.string.hidden_vault_encrypt_note)
                } else {
                    stringResource(R.string.hidden_vault_decrypt_note)
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The current file's name turning into ciphertext letter by letter (or back out of it when
 * decrypting), with the scrambled part flickering like a stream of bytes.
 */
@Composable
private fun CipherText(name: String, encrypting: Boolean, animate: Boolean, accent: Color) {
    if (name.isEmpty()) {
        Spacer(Modifier.height(22.dp))
        return
    }
    val shown = name.take(MaxChars)
    var revealStart by remember(name) { mutableLongStateOf(0L) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(name, animate) {
        if (!animate) return@LaunchedEffect
        revealStart = withFrameMillis { it }
        while (true) tick = withFrameMillis { it }
    }
    // 0 → 1 over the turn: how many characters have flipped.
    val phase = if (!animate) 1f else ((tick - revealStart) / TurnMillis.toFloat()).coerceIn(0f, 1f)
    val flipped = (phase * shown.length).toInt()
    val random = Random(tick / 60) // a new scramble a few times a second, not every frame
    val text = buildString {
        shown.forEachIndexed { i, c ->
            // Encrypting: the front turns to hex first. Decrypting: the front resolves first.
            val scrambled = if (encrypting) i < flipped else i >= flipped
            append(if (scrambled && c != '.') Hex[random.nextInt(Hex.length)] else c)
        }
    }
    Text(
        text,
        color = accent,
        fontSize = 16.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun timeLeft(context: android.content.Context, ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 10 -> context.getString(R.string.hidden_vault_almost_done)
        seconds < 60 -> context.getString(R.string.hidden_vault_seconds_left, seconds)
        seconds < 3600 -> context.getString(R.string.hidden_vault_minutes_left, (seconds + 30) / 60)
        else -> context.getString(R.string.hidden_vault_hours_left, seconds / 3600, (seconds % 3600) / 60)
    }
}

private const val Hex = "0123456789abcdef"
private const val MaxChars = 28
private const val TurnMillis = 500
