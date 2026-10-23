package com.subhashh.lumen.data.ai

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android 14+: the download as a user-initiated data transfer job. Android shows its
 * notification, keeps it running when Lumen leaves the screen, and reruns it (resuming from the
 * `.part` files) if it's stopped for lost network, heat or memory.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ModelDownloadJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transfer: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
                val model = ModelDownloads.model(this)
        notify(params, DownloadNotification.build(this, done = model.bytes - ModelDownloads.remainingBytes(this), total = model.bytes))
        transfer = scope.launch {
            val retry = ModelDownloads.run(this@ModelDownloadJob) { done, total ->
                notify(params, DownloadNotification.build(this@ModelDownloadJob, done, total))
                updateTransferredNetworkBytes(params, done, 0)
            }
            jobFinished(params, retry == true)
        }
        return true
    }

    /** Stopped by Android (network lost, heat) or cancelled: reschedule; parts stay to resume. */
    override fun onStopJob(params: JobParameters): Boolean {
        transfer?.cancel()
        if (params.stopReason != JobParameters.STOP_REASON_CANCELLED_BY_APP) ModelDownloads.paused()
        return true
    }

    // The download needs no reaction to network changes; Android stops the job if the network
    // it requires goes away. Overridden so JobService doesn't log a warning for each change.
    override fun onNetworkChanged(params: JobParameters) = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(params: JobParameters, notification: android.app.Notification) =
        setNotification(params, DownloadNotification.Id, notification, JOB_END_NOTIFICATION_POLICY_REMOVE)
}
