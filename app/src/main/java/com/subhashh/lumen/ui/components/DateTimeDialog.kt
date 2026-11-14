package com.subhashh.lumen.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimeInputDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.theme.Lumen
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

/**
 * "Adjust Date & Time": pick a new date and time for [original] (epoch millis), or dismiss.
 * With [count] > 1, the note explains that every item shifts by the same amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeDialog(
    original: Long?,
    count: Int,
    onAdjust: (newTime: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val open = original != null
    BackHandler(enabled = open, onBack = onDismiss)
    DialogScrim(open, onDismiss)
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.92f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val start = original ?: return@AnimatedVisibility
        val zone = ZoneId.systemDefault()
        val startLocal = remember(start) { LocalDateTime.ofInstant(Instant.ofEpochMilli(start), zone) }
        // The date picker works in UTC midnights; the time field in local hours and minutes.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = startLocal.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val context = LocalContext.current
        val timeState = rememberTimePickerState(
            initialHour = startLocal.hour,
            initialMinute = startLocal.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        val card = Lumen.colors.panel
        val text = Lumen.colors.text
        val secondary = Lumen.colors.secondary
        val accent = Lumen.colors.accent
        val material = Lumen.material

        val adjust = {
            val day = dateState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            } ?: startLocal.toLocalDate()
            // Keep the original seconds: only what the user can see changes.
            val time = LocalTime.of(timeState.hour, timeState.minute, startLocal.second)
            onAdjust(LocalDateTime.of(day, time).atZone(zone).toInstant().toEpochMilli())
        }

        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .dialogCard(card, if (material) Lumen.shapes.dialog else RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(top = if (material) 24.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DialogTitle(stringResource(R.string.components_adjust_date_title))
                Spacer(Modifier.height(4.dp))
                DialogMessage(
                    stringResource(R.string.components_adjust_date_original, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(start))),
                )
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
                        selectedDayContainerColor = accent,
                        selectedDayContentColor = Lumen.colors.onAccent,
                        todayContentColor = accent,
                        todayDateBorderColor = accent,
                    ),
                )
                TimeInput(
                    state = timeState,
                    colors = TimeInputDefaults.colors(
                        periodSelectorSelectedContainerColor = accent.copy(alpha = 0.25f),
                        periodSelectorSelectedContentColor = text,
                        periodSelectorContentColor = secondary,
                        periodSelectorBorderColor = secondary.copy(alpha = 0.4f),
                    ),
                )
                if (count > 1) {
                    Text(
                        pluralStringResource(R.plurals.components_adjust_date_many, count, count),
                        color = secondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                DialogButtons(
                    cancel = stringResource(R.string.common_cancel),
                    onCancel = onDismiss,
                    confirm = stringResource(R.string.components_adjust),
                    onConfirm = adjust,
                    height = 50.dp,
                )
            }
        }
    }
}
