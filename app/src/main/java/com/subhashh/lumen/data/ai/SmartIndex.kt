package com.subhashh.lumen.data.ai

import android.content.Context
import android.provider.MediaStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.WorkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

data class IndexProgress(val indexed: Int, val total: Int)

/**
 * Keeps Smart Search's index current, in the background, with nothing for the user to do:
 * - **Backlog** (everything not indexed yet) runs only while charging and the battery isn't low,
 *   so a first index of thousands of photos and videos never costs battery.
 * - **New items**: MediaStore wakes Lumen shortly after a photo or video is added; up to
 *   [NewLimit] get indexed right away, charging or not. More than that waits for the charger.
 * Both resume where they stopped. Scheduled at app start and when a model finishes downloading.
 */
object SmartIndex {
    private val mutableProgress = MutableStateFlow<IndexProgress?>(null)
    /** Latest counts from a pass (null until one runs in this process). */
    val progress: StateFlow<IndexProgress?> = mutableProgress.asStateFlow()

    private const val Backlog = "index-backlog"
    private const val New = "index-new"
    private const val NewLimit = 30
    private const val KeyMode = "mode"

    private val mutableTextProgress = MutableStateFlow<IndexProgress?>(null)
    /** The same for reading text in photos ([TextIndexer]). */
    val textProgress: StateFlow<IndexProgress?> = mutableTextProgress.asStateFlow()

    fun report(context: Context, indexed: Int, total: Int) {
        mutableProgress.value = IndexProgress(indexed, total)
        IndexNotification.update(context, IndexNotification.Pass.Vectors, indexed, total)
    }

    private val mutableFaceProgress = MutableStateFlow<IndexProgress?>(null)
    /** The same for finding faces ([FaceIndexer]). */
    val faceProgress: StateFlow<IndexProgress?> = mutableFaceProgress.asStateFlow()

    fun reportFaces(context: Context, done: Int, total: Int) {
        mutableFaceProgress.value = IndexProgress(done, total)
        IndexNotification.update(context, IndexNotification.Pass.Faces, done, total)
    }

    fun reportText(context: Context, read: Int, total: Int) {
        mutableTextProgress.value = IndexProgress(read, total)
        IndexNotification.update(context, IndexNotification.Pass.Text, read, total)
    }

    /**
     * Makes sure both jobs are queued, if a model is installed. Safe to call often (app start). A
     * backlog job waiting out a back-off is replaced by a fresh one, so opening Lumen restarts a
     * stalled index; a running one is left alone.
     */
    fun schedule(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (ModelDownloads.installed(app) == null) return@launch
            val work = WorkManager.getInstance(app)
            val waiting = work.getWorkInfosForUniqueWorkFlow(Backlog).first()
                .any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
            work.enqueueUniqueWork(
                Backlog,
                if (waiting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexWorker>()
                    .setInputData(workDataOf(KeyMode to Backlog))
                    .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build())
                    // Android stops a job after ~10 minutes; a long first index carries on a minute
                    // later each time, rather than WorkManager's default doubling wait.
                    .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(1))
                    .build(),
            )
            watchForNew(app, ExistingWorkPolicy.KEEP)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** After a model download: start indexing the newest items right away, charging or not. */
    fun startNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            New,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<IndexWorker>().setInputData(workDataOf(KeyMode to New)).build(),
        )
        schedule(context)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(Backlog)
            cancelUniqueWork(New)
        }
        mutableProgress.value = null
        mutableTextProgress.value = null
        mutableFaceProgress.value = null
        IndexNotification.cancel(context)
    }

    /** A content-triggered job fires once; each run queues the next one behind itself. */
    private fun watchForNew(context: Context, policy: ExistingWorkPolicy) {
        val triggers = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            // Camera bursts and downloads arrive in clumps: wait for them to settle.
            .setTriggerContentUpdateDelay(Duration.ofSeconds(15))
            .setTriggerContentMaxDelay(Duration.ofMinutes(2))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            New,
            policy,
            OneTimeWorkRequestBuilder<IndexWorker>().setInputData(workDataOf(KeyMode to New)).setConstraints(triggers).build(),
        )
    }

    class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val model = ModelDownloads.installed(applicationContext) ?: return Result.success()
            val backlog = inputData.getString(KeyMode) == Backlog
            val limit = if (backlog) Int.MAX_VALUE else NewLimit
            // One pass at a time: both jobs would otherwise work through the same items side by
            // side, each at half speed. The backlog waits its turn; a new-items job finding the
            // backlog running leaves new items to it.
            val outcome = when {
                backlog -> passes.withLock { pass(model, limit) }
                passes.tryLock() -> try { pass(model, limit) } finally { passes.unlock() }
                else -> Indexer.Outcome.Partial
            }
            if (backlog) {
                // Too warm: try again later (WorkManager backs off); otherwise finished.
                return if (outcome == Indexer.Outcome.TooHot) Result.retry() else Result.success()
            }
            // New-items job: queue the next watch, and leave anything beyond the limit to the charger.
            watchForNew(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            if (outcome != Indexer.Outcome.Done) schedule(applicationContext)
            return Result.success()
        }

        private suspend fun pass(model: AiModel, limit: Int): Indexer.Outcome {
            val outcome = Indexer(applicationContext, model).run(limit)
            if (outcome != Indexer.Outcome.Done) return outcome
            // Suggested searches follow what's in the library; refreshed once it's all indexed.
            SearchSuggestions.refreshIfStale(applicationContext, model)
            // Then the words in photos, once everything has its vectors; then faces.
            val text = TextIndexer(applicationContext).run(limit)
            if (text != Indexer.Outcome.Done) return text
            return FaceIndexer(applicationContext).run(limit)
        }
    }

    private val passes = Mutex()
}
