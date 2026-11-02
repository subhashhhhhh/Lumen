package com.subhashh.lumen.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaThumbnail
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.ui.images.ImageKeys
import com.subhashh.lumen.ui.images.version
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** "Remind Me of This…": a date, a time and a note for [items]; null keeps it closed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    items: List<MediaItem>?,
    onSet: (at: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val open = !items.isNullOrEmpty()
    BackHandler(enabled = open, onBack = onDismiss)
    AnimatedVisibility(visible = open, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.92f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val chosen = items?.takeIf { it.isNotEmpty() } ?: return@AnimatedVisibility
        val context = LocalContext.current
        val zone = ZoneId.systemDefault()
        // Starts an hour or so ahead, on the hour: 14:20 → 16:00.
        val start = remember(chosen) {
            LocalDateTime.now(zone).plusMinutes(90).truncatedTo(ChronoUnit.HOURS)
        }
        val today = remember(chosen) { LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        // The date picker works in UTC midnights; the time field in local hours and minutes.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
                    override fun isSelectableYear(year: Int) = year >= LocalDate.now(zone).year
                }
            },
        )
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        var note by remember(chosen) { mutableStateOf("") }
        // Re-checked every few seconds, so a reminder set for "now" can't slip into the past.
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
        // Coming back from the Alarms & Reminders page: check again.
        var exact by remember { mutableStateOf(PhotoReminders.canBeExact(context)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            exact = PhotoReminders.canBeExact(context)
            onPauseOrDispose { }
        }

        val at = run {
            val day = dateState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            } ?: start.toLocalDate()
            LocalDateTime.of(day, LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant().toEpochMilli()
        }
        val valid = at > now

        val dark = LocalDarkTheme.current
        val card = if (dark) Color(0xFF1C1C1E) else Color.White
        val text = if (dark) Color.White else Color.Black
        val secondary = if (dark) Color(0x99EBEBF5) else Color(0x993C3C43)
        val accent = Color(0xFF0A84FF)

        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(card)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = chosen.first()
                    AsyncImage(
                        model = remember(first.version) {
                            ImageRequest.Builder(context)
                                .data(MediaThumbnail(first.uri))
                                .size(ImageKeys.ThumbBuckets.first(), ImageKeys.ThumbBuckets.first())
                                .memoryCacheKey(ImageKeys.thumb(first, ImageKeys.ThumbBuckets.first()))
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(secondary.copy(alpha = 0.15f)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.reminder_dialog_title), color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            pluralStringResource(R.plurals.reminder_dialog_items, chosen.size, chosen.size),
                            color = secondary,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(secondary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (note.isEmpty()) Text(stringResource(R.string.reminder_note_hint), color = secondary, fontSize = 16.sp)
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        textStyle = TextStyle(color = text, fontSize = 16.sp),
                        cursorBrush = SolidColor(accent),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DatePicker(
                    state = dateState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = card,
                        weekdayContentColor = secondary,
                        navigationContentColor = text,
                        yearContentColor = text,
                        currentYearContentColor = accent,
                        selectedYearContainerColor = accent,
                        dayContentColor = text,
                        disabledDayContentColor = secondary.copy(alpha = 0.4f),
                        selectedDayContainerColor = accent,
                        selectedDayContentColor = Color.White,
                        todayContentColor = accent,
                        todayDateBorderColor = accent,
                    ),
                )
                TimeInput(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = accent.copy(alpha = 0.25f),
                        timeSelectorUnselectedContainerColor = secondary.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = text,
                        timeSelectorUnselectedContentColor = text,
                        periodSelectorSelectedContainerColor = accent.copy(alpha = 0.25f),
                        periodSelectorSelectedContentColor = text,
                        periodSelectorUnselectedContentColor = secondary,
                        periodSelectorBorderColor = secondary.copy(alpha = 0.4f),
                    ),
                )
                if (!valid) {
                    Text(
                        stringResource(R.string.reminder_in_past),
                        color = Color(0xFFFF453A),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // Android 13+ doesn't give exact alarms by default: without them it may ring late.
                if (!exact) {
                    Text(
                        stringResource(R.string.reminder_not_exact),
                        color = accent,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                                    )
                                }
                            },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(secondary.copy(alpha = 0.3f)))
                Row(Modifier.fillMaxWidth().height(50.dp)) {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = accent,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 14.dp),
                    )
                    Box(Modifier.width(0.5.dp).height(50.dp).background(secondary.copy(alpha = 0.3f)))
                    Text(
                        stringResource(R.string.reminder_set),
                        color = if (valid) accent else secondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = valid) { onSet(at, note) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}
