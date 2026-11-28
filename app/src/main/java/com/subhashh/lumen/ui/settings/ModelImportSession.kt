package com.subhashh.lumen.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import com.subhashh.lumen.ai.ModelImports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Owns one import for the app process. Document grants and jobs are not restored after process death. */
internal class ModelImportSession(
    private val scope: CoroutineScope,
    private val importFiles: suspend (Context, List<Uri>) -> ModelImports.Result?,
) {
    data class Task(val id: Long, val startedAtMillis: Long, val selectedCount: Int)

    sealed interface State {
        data object Idle : State
        data class Importing(val task: Task) : State
        data class Finished(val task: Task, val outcome: Outcome) : State
    }

    sealed interface Outcome {
        data class Imported(val result: ModelImports.Result) : Outcome
        data object Busy : Outcome
        data object AccessFailed : Outcome
        data object UnexpectedFailure : Outcome
        data object Cancelled : Outcome
    }

    private val nextId = AtomicLong()
    private val current = MutableStateFlow<State>(State.Idle)
    val state = current.asStateFlow()

    fun start(context: Context, documents: List<Uri>) {
        if (documents.isEmpty()) return
        val previous = current.value
        if (previous is State.Importing) return
        val selection = documents.distinct()
        val app = context.applicationContext
        val running = State.Importing(Task(nextId.incrementAndGet(), System.currentTimeMillis(), selection.size))
        if (!current.compareAndSet(previous, running)) return

        scope.launch {
            val outcome = try {
                importFiles(app, selection)?.let { Outcome.Imported(it) } ?: Outcome.Busy
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                when (failure) {
                    is IOException, is SecurityException, is IllegalArgumentException -> Outcome.AccessFailed
                    else -> {
                        // Provider exception messages and cause chains can contain document URIs.
                        Log.w(Tag, "Unexpected import failure: ${failure.javaClass.name}")
                        Outcome.UnexpectedFailure
                    }
                }
            }
            current.compareAndSet(running, State.Finished(running.task, outcome))
        }.invokeOnCompletion { failure ->
            if (failure != null) {
                val outcome = if (failure is CancellationException) Outcome.Cancelled else Outcome.UnexpectedFailure
                current.compareAndSet(running, State.Finished(running.task, outcome))
            }
        }
    }

    fun dismiss(result: State.Finished) {
        current.compareAndSet(result, State.Idle)
    }

    companion object {
        private const val Tag = "ModelImportSession"
        val process = ModelImportSession(
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ModelImports::importFiles,
        )
    }
}
