package com.subhashh.lumen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.ui.components.ReminderDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Remind Me of This… for [items] (null: closed). Saves the reminder, then [onSaved] with the
 * confirmation to show; asks for notification access the first time, when it's clear why.
 */
@Composable
fun PhotoReminderSheet(items: List<MediaItem>?, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val notificationAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(context.getString(R.string.reminder_notifications_off))
    }
    ReminderDialog(
        items = items,
        onSet = { at, note ->
            val chosen = items.orEmpty()
            onClose()
            if (chosen.isNotEmpty()) scope.launch {
                withContext(Dispatchers.IO) { PhotoReminders.add(context.applicationContext, at, note, chosen) }
                val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
                onSaved(context.getString(R.string.reminder_saved, time))
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (canAsk) notificationAccess.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else toast(context.getString(R.string.reminder_notifications_off))
                }
            }
        },
        onDismiss = onClose,
    )
}

/**
 * A reminder's notification was tapped: once the library has loaded ([ready]), [open] its photo;
 * it answers false when the photo isn't in the library any more.
 */
@Composable
fun OpenFromReminder(ready: Boolean, open: (mediaId: Long) -> Boolean) {
    val context = LocalContext.current
    val request by PhotoReminders.openRequest.collectAsState()
    val currentOpen by rememberUpdatedState(open)
    LaunchedEffect(request, ready) {
        val id = request ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        PhotoReminders.openRequest.value = null
        if (!currentOpen(id)) Toast.makeText(context, context.getString(R.string.reminder_photo_gone), Toast.LENGTH_LONG).show()
    }
}
