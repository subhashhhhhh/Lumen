package com.subhashh.lumen.data.ai

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface DownloadState {
    data object Idle : DownloadState
    /** Scheduled; waiting for a network (Wi-Fi, if that's required). */
    data object Waiting : DownloadState
    data class Running(val done: Long, val total: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Starts, tracks and cancels model downloads. The transfer itself runs in a user-initiated data
 * transfer job on Android 14+ (exempt from job quotas, survives leaving the app), and in a
 * WorkManager foreground worker on Android 11–13. Both call [run].
 */
object ModelDownloads {
    private val mutableState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    /** The model being downloaded; one at a time. */
    val model = ModelCatalog.Siglip2

    /** Must be called while Lumen is on screen: Android only accepts user-initiated jobs then. */
    fun start(context: Context, wifiOnly: Boolean) {
        mutableState.value = DownloadState.Waiting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val network = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .apply { if (wifiOnly) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }
                .build()
            val job = JobInfo.Builder(JobId, ComponentName(context, ModelDownloadJob::class.java))
                .setUserInitiated(true)
                .setRequiredNetwork(network)
                .setEstimatedNetworkBytes(remainingBytes(context), 0)
                .build()
            val result = context.getSystemService(JobScheduler::class.java).schedule(job)
            if (result != JobScheduler.RESULT_SUCCESS) mutableState.value = DownloadState.Failed("Android didn't start the download")
        } else {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WorkName, ExistingWorkPolicy.KEEP, request)
        }
    }

    /** Android paused the job (network lost, heat); it reruns and resumes by itself. */
    fun paused() {
        mutableState.value = DownloadState.Waiting
    }

    fun cancel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(JobScheduler::class.java).cancel(JobId)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WorkName)
        }
        mutableState.value = DownloadState.Idle
    }

    /** Removes the model and any half-downloaded parts. */
    fun delete(context: Context) {
        cancel(context)
        model.dir(context).deleteRecursively()
    }

    /** Bytes still to fetch, counting partial files already on disk. */
    fun remainingBytes(context: Context): Long = model.files.sumOf { file ->
        val dir = model.dir(context)
        if (File(dir, file.name).length() == file.bytes) 0L
        else file.bytes - File(dir, file.name + ".part").length().coerceAtMost(file.bytes)
    }

    /**
     * Downloads what's missing. Returns null when done, else whether to retry later. Cancellation
     * propagates, leaving `.part` files to resume from.
     */
    suspend fun run(context: Context, onProgress: (done: Long, total: Long) -> Unit): Boolean? {
        val downloader = ModelDownloader(model, model.dir(context), HfTokenStore(context).token)
        var lastPercent = -1L
        return try {
            downloader.run { done, total ->
                mutableState.value = DownloadState.Running(done, total)
                // The notification only redraws when the percentage moves.
                val percent = done * 100 / total.coerceAtLeast(1)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(done, total)
                }
            }
            mutableState.value = DownloadState.Idle
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            mutableState.value = if (e.retry) DownloadState.Waiting else DownloadState.Failed(e.message.orEmpty())
            e.retry
        }
    }

    private const val JobId = 4201
    private const val WorkName = "model-download"
}
