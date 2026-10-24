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
import com.subhashh.lumen.data.LumenPrefs
import java.io.File

/** A Smart Features part that downloads, updates and is removed on its own from Settings. */
enum class ModelPack(val id: String, val label: String) {
    Search("search", "Smart Search"),
    Text("text", "Text Reading"),
    Faces("faces", "Faces"),
    Subtitles("subtitles", "Subtitles"),
}

sealed interface DownloadState {
    data object Idle : DownloadState
    /** Scheduled; waiting for a network (Wi-Fi, if that's required). */
    data object Waiting : DownloadState
    /** [pack]'s files, [done] of [total] bytes; the rest of the queue follows. */
    data class Running(val pack: ModelPack, val done: Long, val total: Long) : DownloadState
    data class Failed(val pack: ModelPack, val message: String) : DownloadState
}

/**
 * Starts, tracks and cancels model downloads. Each [ModelPack] is asked for on its own; a single
 * transfer works through the queue ([LumenPrefs.modelQueue]) one pack after another. The transfer
 * runs in a user-initiated data transfer job on Android 14+ (exempt from job quotas, survives
 * leaving the app), and in a WorkManager foreground worker on Android 11–13. Both call [run].
 */
object ModelDownloads {
    private val mutableState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    /** The tier chosen in Settings: the one downloads fetch. */
    fun model(context: Context) = ModelCatalog.byId(LumenPrefs(context).searchModel)

    /** The search tier whose files are on the phone, if any (normally at most one). */
    fun installed(context: Context): AiModel? =
        model(context).takeIf { it.installed(context) } ?: ModelCatalog.all.firstOrNull { it.installed(context) }

    /** A pack's files: (files, their URLs, the folder they go in). */
    private class Part(val files: List<ModelFile>, val url: (ModelFile) -> String, val dir: File)

    private fun part(context: Context, pack: ModelPack): Part = when (pack) {
        ModelPack.Search -> model(context).let { Part(it.files, it::url, it.dir(context)) }
        ModelPack.Text -> Part(OcrModel.files, OcrModel::url, OcrModel.dir(context))
        ModelPack.Faces -> Part(FaceModel.files, FaceModel::url, FaceModel.dir(context))
        ModelPack.Subtitles -> Part(SpeechModel.files, SpeechModel::url, SpeechModel.dir(context))
    }

    /** Download size of [pack] (Search: the chosen tier). */
    fun bytes(context: Context, pack: ModelPack): Long = part(context, pack).files.sumOf { it.bytes }

    /** Bytes still to fetch for [pack], counting partial files already on disk. */
    fun remainingBytes(context: Context, pack: ModelPack): Long = part(context, pack).let { remaining(it.files, it.dir) }

    /** [pack] works: its feature can be used, maybe with an older version of its files. */
    fun usable(context: Context, pack: ModelPack): Boolean = when (pack) {
        ModelPack.Search -> installed(context) != null
        ModelPack.Text -> OcrModel.installed(context)
        ModelPack.Faces -> FaceModel.installed(context)
        ModelPack.Subtitles -> SpeechModel.installed(context)
    }

    /** Every file of this version of [pack] is in place (Search: the chosen tier). */
    fun complete(context: Context, pack: ModelPack): Boolean = when (pack) {
        ModelPack.Search -> model(context).complete(context)
        ModelPack.Text -> OcrModel.complete(context)
        ModelPack.Faces -> FaceModel.installed(context)
        ModelPack.Subtitles -> SpeechModel.installed(context)
    }

    /** Packs asked for and not finished, in order. */
    fun queue(context: Context): List<ModelPack> =
        LumenPrefs(context).modelQueue.mapNotNull { id -> ModelPack.entries.firstOrNull { it.id == id } }

    private fun setQueue(context: Context, packs: List<ModelPack>) {
        LumenPrefs(context).modelQueue = packs.distinct().map { it.id }
    }

    /**
     * Adds [pack] to the queue and starts the transfer if it isn't going already. Must be called
     * while Lumen is on screen: Android only accepts user-initiated jobs then.
     */
    fun start(context: Context, pack: ModelPack, wifiOnly: Boolean) {
        setQueue(context, queue(context) + pack)
        val s = state.value
        if (s is DownloadState.Running || s is DownloadState.Waiting) return
        schedule(context, wifiOnly)
    }

    private fun schedule(context: Context, wifiOnly: Boolean) {
        mutableState.value = DownloadState.Waiting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val network = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .apply { if (wifiOnly) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }
                .build()
            val job = JobInfo.Builder(JobId, ComponentName(context, ModelDownloadJob::class.java))
                .setUserInitiated(true)
                .setRequiredNetwork(network)
                .setEstimatedNetworkBytes(queue(context).sumOf { remainingBytes(context, it) }, 0)
                .build()
            val result = context.getSystemService(JobScheduler::class.java).schedule(job)
            if (result != JobScheduler.RESULT_SUCCESS) {
                mutableState.value = DownloadState.Failed(queue(context).firstOrNull() ?: ModelPack.Search, "Android didn't start the download")
            }
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

    /** Stops every download; parts stay on disk to resume from. */
    fun cancel(context: Context) {
        setQueue(context, emptyList())
        stopTransfer(context)
        mutableState.value = DownloadState.Idle
    }

    /**
     * Takes [pack] out of the queue. If it was downloading, the transfer restarts for the rest
     * (Lumen is on screen: the only time this is called).
     */
    fun cancel(context: Context, pack: ModelPack, wifiOnly: Boolean) {
        val rest = queue(context) - pack
        setQueue(context, rest)
        val s = state.value
        val current = s is DownloadState.Running && s.pack == pack
        if (rest.isEmpty() || current) {
            stopTransfer(context)
            mutableState.value = DownloadState.Idle
            if (rest.isNotEmpty()) schedule(context, wifiOnly)
        } else if (s is DownloadState.Failed && s.pack == pack) {
            mutableState.value = DownloadState.Idle
        }
    }

    private fun stopTransfer(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(JobScheduler::class.java).cancel(JobId)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WorkName)
        }
    }

    /** Removes [pack]'s files, half-downloaded parts and what was found with them. */
    fun delete(context: Context, pack: ModelPack, wifiOnly: Boolean) {
        cancel(context, pack, wifiOnly)
        // The background pass may be using these files; it restarts without them.
        SmartIndex.cancel(context)
        when (pack) {
            ModelPack.Search -> {
                ModelCatalog.all.forEach {
                    it.dir(context).deleteRecursively()
                    EmbeddingStore.delete(context, it)
                }
                FrameCache.clear(context)
            }
            ModelPack.Text -> {
                OcrModel.dir(context).deleteRecursively()
                TextStore.delete(context)
            }
            ModelPack.Faces -> {
                FaceModel.dir(context).deleteRecursively()
                FaceStore.delete(context)
            }
            ModelPack.Subtitles -> SpeechModel.dir(context).deleteRecursively()
        }
        SmartIndex.schedule(context)
    }

    private fun remaining(files: List<ModelFile>, dir: File): Long = files.sumOf { file ->
        if (File(dir, file.name).length() == file.bytes) 0L
        else file.bytes - File(dir, file.name + ".part").length().coerceAtMost(file.bytes)
    }

    /** What the transfer's notification starts with: the first queued pack, and its progress. */
    fun first(context: Context): Triple<ModelPack, Long, Long>? = queue(context).firstOrNull()?.let { pack ->
        val total = bytes(context, pack)
        Triple(pack, total - remainingBytes(context, pack), total)
    }

    /**
     * Downloads the queue, one pack at a time, each taken off once complete. Returns null when
     * done, else whether to retry later (false: a pack failed for good, the rest were fetched). Cancellation propagates, leaving `.part` files to
     * resume from.
     */
    suspend fun run(context: Context, onProgress: (pack: ModelPack, done: Long, total: Long) -> Unit): Boolean? {
        val token = HfTokenStore(context).token
        var lastPercent = -1L
        var failed: DownloadState.Failed? = null
        while (true) {
            // Re-read each time: packs can be added or cancelled while one downloads.
            val pack = queue(context).firstOrNull() ?: break
            val part = part(context, pack)
            val total = part.files.sumOf { it.bytes }
            val report = { done: Long, all: Long ->
                mutableState.value = DownloadState.Running(pack, done, all)
                // The notification only redraws when the percentage moves.
                val percent = done * 100 / all.coerceAtLeast(1)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(pack, done, all)
                }
            }
            try {
                ModelDownloader(part.files, part.url, part.dir, token).run(0, total, report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                if (e.retry) {
                    mutableState.value = DownloadState.Waiting
                    return true
                }
                // This pack can't be fetched (a checksum or disk problem): the rest still can.
                failed = DownloadState.Failed(pack, e.message.orEmpty())
                setQueue(context, queue(context) - pack)
                continue
            }
            if (pack == ModelPack.Search) {
                val model = model(context)
                // Files a newer version no longer uses (Accurate's ONNX image model, before LiteRT).
                val wanted = model.files.mapTo(HashSet()) { it.name }
                model.dir(context).listFiles()?.filter { it.name !in wanted }?.forEach { it.deleteRecursively() }
                // Switching tiers: the old one (and its index) goes once the new one is complete.
                ModelCatalog.all.filter { it != model }.forEach {
                    it.dir(context).deleteRecursively()
                    EmbeddingStore.delete(context, it)
                }
            }
            setQueue(context, queue(context) - pack)
            if (pack != ModelPack.Subtitles) SmartIndex.startNow(context)
        }
        mutableState.value = failed ?: DownloadState.Idle
        return if (failed != null) false else null
    }

    private const val JobId = 4201
    private const val WorkName = "model-download"
}
