package com.subhashh.lumen.data.ai

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/** Android 11–13: the download as a WorkManager worker in a data-sync foreground service. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val (first, done, total) = ModelDownloads.first(applicationContext) ?: return Result.success()
        setForeground(info(first, done, total))
        val retry = ModelDownloads.run(applicationContext) { pack, got, all -> setForegroundAsync(info(pack, got, all)) }
        return when (retry) {
            null -> Result.success()
            true -> Result.retry()
            false -> Result.failure()
        }
    }

    private fun info(pack: ModelPack, done: Long, total: Long) = ForegroundInfo(
        DownloadNotification.Id,
        DownloadNotification.build(applicationContext, pack, done, total),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
}
