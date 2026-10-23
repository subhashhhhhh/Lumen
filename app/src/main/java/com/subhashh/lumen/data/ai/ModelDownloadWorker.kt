package com.subhashh.lumen.data.ai

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/** Android 11–13: the download as a WorkManager worker in a data-sync foreground service. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val total = ModelDownloads.totalBytes(applicationContext)
        setForeground(info(total - ModelDownloads.remainingBytes(applicationContext), total))
        val retry = ModelDownloads.run(applicationContext) { done, total -> setForegroundAsync(info(done, total)) }
        return when (retry) {
            null -> Result.success()
            true -> Result.retry()
            false -> Result.failure()
        }
    }

    private fun info(done: Long, total: Long) = ForegroundInfo(
        DownloadNotification.Id,
        DownloadNotification.build(applicationContext, done, total),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
}
