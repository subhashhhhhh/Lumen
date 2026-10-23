package com.subhashh.lumen.data.ai

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/** Android 11–13: the download as a WorkManager worker in a data-sync foreground service. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val model = ModelDownloads.model
        setForeground(info(model.bytes - ModelDownloads.remainingBytes(applicationContext), model.bytes))
        val retry = ModelDownloads.run(applicationContext) { done, total -> setForegroundAsync(info(done, total)) }
        return when (retry) {
            null -> Result.success()
            true -> Result.retry()
            false -> Result.failure()
        }
    }

    private fun info(done: Long, total: Long) = ForegroundInfo(
        DownloadNotification.Id,
        DownloadNotification.build(applicationContext, ModelDownloads.model, done, total),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
}
