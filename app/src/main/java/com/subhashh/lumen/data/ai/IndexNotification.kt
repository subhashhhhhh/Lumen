package com.subhashh.lumen.data.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.subhashh.lumen.MainActivity
import com.subhashh.lumen.R

/**
 * A quiet progress notification while Smart Search works through a backlog: vectors first, then
 * text, then faces. Gone when the pass finishes; one that stops early (unplugged, too warm, Android's
 * 10-minute job limit) clears itself a few minutes after its last update unless the next run
 * picks it up.
 */
object IndexNotification {
    private const val Id = 4202
    private const val Channel = "indexing"
    private const val ClearAfterMs = 3 * 60_000L
    private const val EveryMs = 1_000L
    private var lastShown = 0L

    enum class Pass(val title: String) {
        Vectors("Indexing photos and videos"),
        Text("Reading text in photos and videos"),
        Faces("Finding faces in photos"),
    }

    fun update(context: Context, pass: Pass, done: Int, total: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (done >= total) {
            manager.cancel(Id)
            return
        }
        // Once a second at most: a photo without text takes ~0.5 s.
        val now = SystemClock.elapsedRealtime()
        if (now - lastShown < EveryMs) return
        lastShown = now
        if (manager.getNotificationChannel(Channel) == null) {
            manager.createNotificationChannel(
                NotificationChannel(Channel, "Smart Search indexing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress while Smart Search reads your library"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            Id,
            NotificationCompat.Builder(context, Channel)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(pass.title)
                .setContentText("%,d of %,d".format(done, total))
                .setProgress(total, done, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setTimeoutAfter(ClearAfterMs)
                .setContentIntent(open)
                .build(),
        )
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(Id)
}
