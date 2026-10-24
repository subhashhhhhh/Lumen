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
 *   so a first index of thousands of photos and videos never costs battery; unless the user
 *   turns on Index on Battery (Settings → Smart Search), which drops the charger rule.
 * - **New items**: MediaStore wakes Lumen shortly after a photo or video is added; up to
 *   [NewLimit] get indexed right away, charging or not. More than that waits for the charger.
 * - **Scheduled Indexing** (Settings): the backlog runs only in the user's hours (overnight,
 *   say), plugged in or not; a small job wakes Lumen at the window's start and end.
 * Both resume where they stopped. Scheduled at app start and when a model finishes downloading.
 */
object SmartIndex {
    private val mutableProgress = MutableStateFlow<IndexProgress?>(null)
    /** Latest counts from a pass (null until one runs in this process). */
    val progress: StateFlow<IndexProgress?> = mutableProgress.asStateFlow()

    private const val Backlog = "index-backlog"
    private const val New = "index-new"
    private const val Window = "index-window"
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
     * Makes sure both jobs are queued, if a search, text or face model is installed. Safe to call often (app start). A
     * backlog job waiting out a back-off is replaced by a fresh one, so opening Lumen restarts a
     * stalled index; a running one is left alone.
     */
    fun schedule(context: Context, replace: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            if (!anythingToIndex(app)) return@launch
            val work = WorkManager.getInstance(app)
            val prefs = com.subhashh.lumen.data.LumenPrefs(app)
            var charging = !prefs.indexOnBattery
            if (prefs.indexScheduleOn) {
                val inside = inWindow(prefs.indexScheduleStart, prefs.indexScheduleEnd)
                // Wake at the window's next edge: its end while inside it, else its start.
                val edge = if (inside) prefs.indexScheduleEnd else prefs.indexScheduleStart
                work.enqueueUniqueWork(
                    Window,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WindowWorker>().setInitialDelay(untilMinute(edge)).build(),
                )
                if (!inside) {
                    // Outside the hours: no backlog; new items still come in.
                    work.cancelUniqueWork(Backlog)
                    watchForNew(app, ExistingWorkPolicy.KEEP)
                    return@launch
                }
                // Inside them: plugged in or not (still never on a low battery).
                charging = false
            } else {
                work.cancelUniqueWork(Window)
            }
            val waiting = work.getWorkInfosForUniqueWorkFlow(Backlog).first()
                .any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
            work.enqueueUniqueWork(
                Backlog,
                if (waiting || replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexWorker>()
                    .setInputData(workDataOf(KeyMode to Backlog))
                    .setConstraints(Constraints.Builder().setRequiresCharging(charging).setRequiresBatteryNotLow(true).build())
                    // Android stops a job after ~10 minutes; a long first index carries on a minute
                    // later each time, rather than WorkManager's default doubling wait.
                    .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(1))
                    .build(),
            )
            watchForNew(app, ExistingWorkPolicy.KEEP)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Each pack downloads on its own: faces or text can be on the phone without search. */
    private fun anythingToIndex(context: Context) =
        ModelDownloads.installed(context) != null || FaceModel.installed(context) || OcrModel.installed(context)

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
            if (!anythingToIndex(applicationContext)) return Result.success()
            val model = ModelDownloads.installed(applicationContext)
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

        /**
         * Faces first (fastest; while videos are decoded for faces, their frames are kept for
         * the next step), then Smart Search's vectors, then the words in photos. Steps whose
         * models aren't downloaded are skipped (each indexer checks its own).
         */
        private suspend fun pass(model: AiModel?, limit: Int): Indexer.Outcome {
            val faces = FaceIndexer(applicationContext, model).run(limit)
            if (faces != Indexer.Outcome.Done) return faces
            if (model != null) {
                val vectors = Indexer(applicationContext, model).run(limit)
                if (vectors != Indexer.Outcome.Done) return vectors
                // Suggested searches follow what's in the library; refreshed once it's all indexed.
                SearchSuggestions.refreshIfStale(applicationContext, model)
            }
            return TextIndexer(applicationContext).run(limit)
        }
    }

    private val passes = Mutex()

    /** Whether now is between [start] and [end], minutes after midnight (a window may cross it). */
    private fun inWindow(start: Int, end: Int): Boolean {
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        return if (start <= end) now in start until end else now >= start || now < end
    }

    /** From now until the next [minute] after midnight (today or tomorrow). */
    private fun untilMinute(minute: Int): Duration {
        val now = java.time.LocalDateTime.now()
        var at = now.toLocalDate().atStartOfDay().plusMinutes(minute.toLong())
        if (!at.isAfter(now)) at = at.plusDays(1)
        return Duration.between(now, at)
    }

    /** Wakes at a Scheduled Indexing edge: starts or stops the backlog, and arms the next edge. */
    class WindowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            schedule(applicationContext, replace = true)
            return Result.success()
        }
    }
}
