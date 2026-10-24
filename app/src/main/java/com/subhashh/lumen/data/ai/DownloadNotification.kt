package com.subhashh.lumen.data.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.subhashh.lumen.MainActivity
import com.subhashh.lumen.R

/** The model download's progress notification, with a Cancel button. */
object DownloadNotification {
    const val Id = 4201
    private const val Channel = "downloads"

    fun build(context: Context, pack: ModelPack, done: Long, total: Long): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(Channel) == null) {
            manager.createNotificationChannel(
                NotificationChannel(Channel, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Downloading Smart Features models"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getBroadcast(
            context, 0, Intent(context, CancelReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val percent = if (total > 0) (done * 100 / total).toInt() else 0
        return NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Downloading ${pack.label}")
            .setContentText("${megabytes(done)} of ${megabytes(total)}")
            .setProgress(100, percent, total == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    fun megabytes(bytes: Long) = "%,d MB".format(bytes / 1_000_000)

    class CancelReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = ModelDownloads.cancel(context)
    }
}
