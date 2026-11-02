package com.subhashh.lumen.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.subhashh.lumen.MainActivity
import com.subhashh.lumen.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/** "Remind Me of This…": a note and a time for one or more photos. */
data class PhotoReminder(
    val id: Int,
    /** Epoch millis. */
    val at: Long,
    val note: String,
    val mediaIds: List<Long>,
    /** Content URIs, same order as [mediaIds]; the first one is the notification's picture. */
    val uris: List<Uri>,
)

/**
 * Photo reminders (Developer Options → Photo Reminders). Kept in their own prefs file and set
 * as alarms; [PhotoReminderReceiver] posts the notification and sets them again after a reboot,
 * since Android forgets alarms when the phone restarts.
 */
object PhotoReminders {
    private const val Prefs = "photo_reminders"
    private const val KeyAll = "all"
    private const val KeyNextId = "next_id"
    private const val Channel = "photo_reminders"
    const val ActionFire = "com.subhashh.lumen.REMINDER"
    const val ExtraId = "reminder_id"
    /** On MainActivity's intent: open this photo in the viewer. */
    const val ExtraOpenMedia = "open_media_id"

    /** The Developer Options switch, as state, so open menus follow it. */
    var enabled by mutableStateOf<Boolean?>(null)
        private set

    fun enabled(context: Context): Boolean =
        enabled ?: LumenPrefs(context).photoReminders.also { enabled = it }

    fun setEnabled(context: Context, on: Boolean) {
        LumenPrefs(context).photoReminders = on
        enabled = on
    }

    /** A notification was tapped: the photo to open, until the app shows it. */
    val openRequest = MutableStateFlow<Long?>(null)

    fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(ExtraOpenMedia, -1L) ?: -1L
        if (id >= 0) {
            openRequest.value = id
            intent?.removeExtra(ExtraOpenMedia)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    /** Waiting reminders, soonest first. */
    fun all(context: Context): List<PhotoReminder> {
        val json = prefs(context).getString(KeyAll, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val ids = o.getJSONArray("ids")
                val uris = o.getJSONArray("uris")
                PhotoReminder(
                    id = o.getInt("id"),
                    at = o.getLong("at"),
                    note = o.optString("note"),
                    mediaIds = (0 until ids.length()).map { ids.getLong(it) },
                    uris = (0 until uris.length()).map { Uri.parse(uris.getString(it)) },
                )
            }.sortedBy { it.at }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, reminders: List<PhotoReminder>) {
        val array = JSONArray()
        reminders.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("at", r.at)
                    .put("note", r.note)
                    .put("ids", JSONArray(r.mediaIds))
                    .put("uris", JSONArray(r.uris.map { it.toString() })),
            )
        }
        prefs(context).edit { putString(KeyAll, array.toString()) }
    }

    @Synchronized
    fun add(context: Context, at: Long, note: String, items: List<MediaItem>): PhotoReminder {
        val p = prefs(context)
        val id = p.getInt(KeyNextId, 1)
        p.edit { putInt(KeyNextId, if (id == Int.MAX_VALUE) 1 else id + 1) }
        val reminder = PhotoReminder(id, at, note.trim(), items.map { it.id }, items.map { it.uri })
        save(context, all(context) + reminder)
        schedule(context, reminder)
        return reminder
    }

    @Synchronized
    fun cancel(context: Context, id: Int) {
        save(context, all(context).filterNot { it.id == id })
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
    }

    /** Can the alarm ring at the exact minute? Without the permission Android may delay it. */
    fun canBeExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun alarmIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, PhotoReminderReceiver::class.java).setAction(ActionFire).putExtra(ExtraId, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(context: Context, reminder: PhotoReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context, reminder.id)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
        }
    }

    /** After a reboot, an update or a change of the exact-alarm permission: set every alarm again. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        all(context).forEach { r ->
            if (r.at <= now) fire(context, r.id) else schedule(context, r)
        }
    }

    /** Shows reminder [id]'s notification and forgets it. Runs off the main thread. */
    fun fire(context: Context, id: Int) {
        val reminder = synchronized(this) {
            val list = all(context)
            list.firstOrNull { it.id == id }?.also { r -> save(context, list - r) }
        } ?: return
        notify(context, reminder)
    }

    private fun notify(context: Context, reminder: PhotoReminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(Channel) == null) {
            manager.createNotificationChannel(
                NotificationChannel(Channel, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.reminder_channel_description)
                },
            )
        }
        // The photo may be gone by now: then the notification has no picture.
        val picture = reminder.uris.firstNotNullOfOrNull { thumbnail(context, it) }
        val count = reminder.mediaIds.size
        val title = context.resources.getQuantityString(R.plurals.reminder_title, count, count)
        val open = PendingIntent.getActivity(
            context,
            reminder.id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ExtraOpenMedia, reminder.mediaIds.firstOrNull() ?: -1L),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(reminder.note.ifEmpty { null })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.at)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (picture != null) {
            builder.setLargeIcon(picture).setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(reminder.note.ifEmpty { null }),
            )
        } else if (reminder.note.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(reminder.note))
        }
        // Without notification access (Android 13+) it's simply not shown.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify("reminder", reminder.id, builder.build())
        }
    }

    private fun thumbnail(context: Context, uri: Uri): Bitmap? =
        runCatching { context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null) }.getOrNull()
}

/** Rings a reminder, and sets them all again after a reboot or an app update. */
class PhotoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        // Loading the photo's thumbnail reads from disk: not on the main thread.
        thread(name = "photo-reminder") {
            try {
                when (intent.action) {
                    PhotoReminders.ActionFire -> PhotoReminders.fire(app, intent.getIntExtra(PhotoReminders.ExtraId, -1))
                    else -> PhotoReminders.rescheduleAll(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
