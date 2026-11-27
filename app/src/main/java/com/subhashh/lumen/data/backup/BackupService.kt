package com.subhashh.lumen.data.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.subhashh.lumen.GalleryActivity
import com.subhashh.lumen.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A backup in progress, run by [BackupService] so it carries on when Lumen is closed. Settings
 * and the notification read [state] and pause, resume or stop it.
 *
 * Stopping is safe at any point: chunks already written stay in the backup folder and photos
 * already sent are remembered (the catalog), so the next backup carries on where this one ended.
 */
object BackupRunner {
    data class State(
        /** Null when no backup is running. */
        val progress: LumenBackup.Progress? = null,
        val paused: Boolean = false,
        /** What the last backup did, for Settings ("Backed up 20 GB; 15 kB of it new"). */
        val result: String? = null,
        /** A restore, not a backup. */
        val restoring: Boolean = false,
    ) {
        val running: Boolean get() = progress != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun start(context: Context) {
        if (_state.value.running) return
        _state.value = State(LumenBackup.Progress(R.string.backup_step_preparing, 0, 0))
        context.startForegroundService(Intent(context, BackupService::class.java).setAction(BackupService.Start))
    }

    private var pendingRestore: Pair<LumenBackup.Snapshot, Set<LumenBackup.Item>>? = null

    /**
     * Restores [snapshot] in [BackupService], so it carries on if Settings closes. When it's
     * staged, Lumen restarts to put it in place (or, if Lumen isn't on screen, at its next start).
     */
    fun restore(context: Context, snapshot: LumenBackup.Snapshot, items: Set<LumenBackup.Item>) {
        if (_state.value.running) return
        pendingRestore = snapshot to items
        _state.value = State(LumenBackup.Progress(R.string.backup_step_preparing, 0, 0), restoring = true)
        context.startForegroundService(Intent(context, BackupService::class.java).setAction(BackupService.Restore))
    }

    internal fun takeRestore() = pendingRestore.also { pendingRestore = null }

    fun pause() = _state.update { if (it.running) it.copy(paused = true) else it }
    fun resume() = _state.update { it.copy(paused = false) }

    /** The running backup or restore, whoever started it (Settings, or the schedule): Stop cancels it. */
    @Volatile internal var current: Job? = null

    fun stop(@Suppress("UNUSED_PARAMETER") context: Context? = null) {
        current?.cancel()
    }

    /** Marks a scheduled backup as running; false if one already is. */
    @Synchronized
    internal fun claim(): Boolean {
        if (_state.value.running) return false
        _state.value = State(LumenBackup.Progress(R.string.backup_step_preparing, 0, 0))
        return true
    }

    /**
     * Backs up what's chosen in Settings, then clears out old backups (Keep), and says what
     * happened ("Backed up 20 GB; 15 kB of it new", "Backup stopped…").
     */
    internal suspend fun runBackup(context: Context): String {
        val choices = context.getSharedPreferences("lumen-backup-choices", 0)
        val items = LumenBackup.Item.entries.filter { choices.getBoolean(it.key, it.defaultOn) }.toSet()
        return try {
            val done = LumenBackup.backUp(context, items, gate = ::gate, progress = ::progress)
            progress(LumenBackup.Progress(R.string.backup_step_cleaning, 0, 0))
            // A failed clean-up never fails the backup: it's tried again next time.
            runCatching { LumenBackup.prune(context, BackupSchedule.keep(context)) }
                .onFailure { android.util.Log.w("LumenBackup", "Couldn't clear out old backups", it) }
            context.getString(R.string.backup_done, Formatter.formatShortFileSize(context, done.total), Formatter.formatShortFileSize(context, done.uploaded))
        } catch (e: CancellationException) {
            context.getString(R.string.backup_stopped)
        } catch (e: Exception) {
            context.getString(R.string.backup_failed, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Waits here while the backup is paused; between chunks, so a pause takes effect within one. */
    internal suspend fun gate() {
        _state.first { !it.paused }
    }

    internal fun progress(p: LumenBackup.Progress) = _state.update { if (it.running) it.copy(progress = p) else it }

    internal fun finished(result: String?) {
        _state.value = State(result = result)
    }
}

class BackupService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Start -> begin()
            Restore -> beginRestore()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (!startWatching()) return
        job = scope.launch {
            val result = BackupRunner.runBackup(this@BackupService)
            watcher?.cancel()
            BackupRunner.finished(result)
            stopForeground(STOP_FOREGROUND_REMOVE)
            // A short "done" note when nobody is watching Settings; dismissed by a tap.
            getSystemService(NotificationManager::class.java).notify(BackupNotifications.DoneId, BackupNotifications.finished(this@BackupService, result))
            job = null
            stopSelf()
        }.also { BackupRunner.current = it }
    }

    /** Goes foreground with the notification, which then follows progress; false if a job is running already. */
    private fun startWatching(): Boolean {
        startForeground(BackupNotifications.Id, BackupNotifications.progress(this, BackupRunner.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (job != null) return false
        // The notification follows progress and pause, at most twice a second.
        watcher = scope.launch {
            var shown = 0L
            BackupRunner.state.collect { state ->
                if (!state.running) return@collect
                val now = SystemClock.elapsedRealtime()
                if (now - shown < 500 && !state.paused) return@collect
                shown = now
                getSystemService(NotificationManager::class.java).notify(BackupNotifications.Id, BackupNotifications.progress(this@BackupService, state))
            }
        }
        return true
    }

    private fun beginRestore() {
        val (snapshot, items) = BackupRunner.takeRestore() ?: run { if (job == null) stopSelf(); return }
        if (!startWatching()) return
        job = scope.launch {
            val failure = try {
                LumenBackup.stageRestore(this@BackupService, snapshot, items, gate = BackupRunner::gate, progress = BackupRunner::progress)
                null
            } catch (e: CancellationException) {
                getString(R.string.backup_restore_stopped)
            } catch (e: Exception) {
                getString(R.string.backup_restore_failed, e.message ?: e.javaClass.simpleName)
            }
            watcher?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (failure != null) {
                // Nothing was put in place; a staged part is cleared at the next start.
                BackupRunner.finished(failure)
                getSystemService(NotificationManager::class.java).notify(BackupNotifications.DoneId, BackupNotifications.finished(this@BackupService, failure))
                job = null
                stopSelf()
                return@launch
            }
            // Staged: Lumen restarts and puts it in place before anything opens a database.
            if (onScreen()) {
                startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(this@BackupService, GalleryActivity::class.java)))
            } else {
                getSystemService(NotificationManager::class.java).notify(BackupNotifications.DoneId, BackupNotifications.finished(this@BackupService, getString(R.string.backup_restore_ready)))
            }
            Runtime.getRuntime().exit(0)
        }.also { BackupRunner.current = it }
    }

    private fun onScreen(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ allows a data sync service 6 hours a day: stop cleanly, carry on next time.
        job?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        if (BackupRunner.state.value.running) BackupRunner.finished(getString(R.string.backup_stopped))
        super.onDestroy()
    }

    companion object {
        const val Start = "start"
        const val Restore = "restore"
    }
}

/** Backup's notifications, for [BackupService] and [BackupWorker] alike. */
internal object BackupNotifications {
    const val Id = 4301
    const val DoneId = 4302
    private const val Channel = "backup"

    private fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(Channel) == null) {
            manager.createNotificationChannel(
                NotificationChannel(Channel, context.getString(R.string.backup_notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun action(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, BackupActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun open(context: Context) =
        PendingIntent.getActivity(context, 0, Intent(context, GalleryActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun progress(context: Context, state: BackupRunner.State): Notification {
        channel(context)
        val p = state.progress
        val percent = if (p != null && p.total > 0) (p.done * 100 / p.total).toInt() else 0
        val text = when {
            p == null -> ""
            p.total > 0 -> context.getString(R.string.backup_notification_progress, Formatter.formatShortFileSize(context, p.done), Formatter.formatShortFileSize(context, p.total))
            else -> context.getString(p.step)
        }
        return NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(
                context.getString(
                    when {
                        state.restoring && state.paused -> R.string.backup_notification_restore_paused
                        state.restoring -> R.string.backup_notification_restoring
                        state.paused -> R.string.backup_notification_paused
                        else -> R.string.backup_notification_title
                    },
                ),
            )
            .setContentText(text)
            .setProgress(100, percent, p == null || p.total == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open(context))
            .apply {
                if (state.paused) addAction(0, context.getString(R.string.backup_resume), action(context, BackupActionReceiver.Resume, 2))
                else addAction(0, context.getString(R.string.backup_pause), action(context, BackupActionReceiver.Pause, 1))
            }
            .addAction(0, context.getString(R.string.backup_stop), action(context, BackupActionReceiver.Stop, 3))
            .build()
    }

    fun finished(context: Context, result: String): Notification {
        channel(context)
        return NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.backup_notification_done))
            .setContentText(result)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open(context))
            .build()
    }
}

/** The notification's Pause, Resume and Stop, for a backup or restore however it was started. */
class BackupActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Pause -> BackupRunner.pause()
            Resume -> BackupRunner.resume()
            Stop -> BackupRunner.stop()
        }
    }

    companion object {
        const val Pause = "com.subhashh.lumen.backup.PAUSE"
        const val Resume = "com.subhashh.lumen.backup.RESUME"
        const val Stop = "com.subhashh.lumen.backup.STOP"
    }
}
