package com.subhashh.lumen.data.backup

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Automatic backups (Settings → Backup): daily or weekly, by default only while charging and on
 * Wi-Fi, through WorkManager, so Android runs them when it suits the phone. [apply] keeps the
 * queued work in line with the settings; call it when they or the backup place change.
 */
object BackupSchedule {
    enum class Every(val key: String, val days: Long) { Off("off", 0), Daily("daily", 1), Weekly("weekly", 7) }

    private const val Work = "lumen-backup"

    private fun choices(context: Context) = context.getSharedPreferences("lumen-backup-choices", Context.MODE_PRIVATE)

    fun every(context: Context): Every =
        choices(context).getString("auto", null)?.let { key -> Every.entries.firstOrNull { it.key == key } } ?: Every.Daily

    fun charging(context: Context) = choices(context).getBoolean("auto-charging", true)
    fun wifi(context: Context) = choices(context).getBoolean("auto-wifi", true)

    /** How many backups to keep; 0 keeps them all. */
    fun keep(context: Context) = choices(context).getInt("keep", 30)

    fun set(context: Context, every: Every? = null, charging: Boolean? = null, wifi: Boolean? = null, keep: Int? = null) {
        choices(context).edit {
            every?.let { putString("auto", it.key) }
            charging?.let { putBoolean("auto-charging", it) }
            wifi?.let { putBoolean("auto-wifi", it) }
            keep?.let { putInt("keep", it) }
        }
        apply(context)
    }

    /** Queues, updates or cancels the automatic backup to match the settings. */
    fun apply(context: Context) {
        val work = WorkManager.getInstance(context)
        val every = every(context)
        if (every == Every.Off || LumenBackup.location(context) == null) {
            work.cancelUniqueWork(Work)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresCharging(charging(context))
            .setRequiredNetworkType(if (wifi(context)) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(every.days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        // UPDATE keeps the timing when only the settings change.
        work.enqueueUniquePeriodicWork(Work, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Whether a scheduled run should back up now: the last backup is older than most of the interval. */
    internal fun due(context: Context): Boolean {
        val days = every(context).days.takeIf { it > 0 } ?: return false
        val last = LumenBackup.lastBackup(context)
        return System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(days) * 9 / 10
    }
}

/** A scheduled backup: the same run as Back Up Now, with its notification, in WorkManager's time. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (LumenBackup.location(context) == null || !BackupSchedule.due(context)) return Result.success()
        if (!BackupRunner.claim()) return Result.success() // one is running already (Back Up Now)
        return try {
            setForeground(foreground(BackupRunner.state.value))
            val result = coroutineScope {
                // The notification follows progress and pause, at most twice a second.
                val watcher = launch {
                    var shown = 0L
                    BackupRunner.state.collect { state ->
                        if (!state.running) return@collect
                        val now = SystemClock.elapsedRealtime()
                        if (now - shown < 500 && !state.paused) return@collect
                        shown = now
                        setForeground(foreground(state))
                    }
                }
                val run = async { BackupRunner.runBackup(context) }
                BackupRunner.current = run
                run.await().also { watcher.cancel() }
            }
            BackupRunner.finished(result)
            context.getSystemService(android.app.NotificationManager::class.java)
                .notify(BackupNotifications.DoneId, BackupNotifications.finished(context, result))
            Result.success()
        } catch (e: Exception) {
            BackupRunner.finished(context.getString(com.subhashh.lumen.R.string.backup_failed, e.message ?: e.javaClass.simpleName))
            Result.success() // tried again at the next interval, not in a retry loop
        }
    }

    private fun foreground(state: BackupRunner.State) =
        ForegroundInfo(BackupNotifications.Id, BackupNotifications.progress(applicationContext, state), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
