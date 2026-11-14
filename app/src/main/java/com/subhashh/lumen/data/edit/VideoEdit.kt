package com.subhashh.lumen.data.edit

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.container.Mp4TimestampData
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** What the video editor changes: the part that's kept, whether it has sound, and which way is up. */
data class VideoEdit(
    val startMs: Long = 0,
    /** Null keeps everything to the end. */
    val endMs: Long? = null,
    val muted: Boolean = false,
    /** Clockwise quarter turns, 0 to 3. */
    val quarterTurns: Int = 0,
) {
    val isUnchanged: Boolean get() = this == VideoEdit()

    /** The end of the kept part, for a video [durationMs] long. */
    fun end(durationMs: Long): Long = endMs ?: durationMs

    /**
     * The kept part with its start moved to [ms]: never before the video's start, and never closer
     * than [MinLengthMs] to the end.
     */
    fun withStart(ms: Long, durationMs: Long): VideoEdit =
        copy(startMs = ms.coerceIn(0L, (end(durationMs) - MinLengthMs).coerceAtLeast(0L)))

    /** The same for the end; an end back at the video's own end is forgotten (null). */
    fun withEnd(ms: Long, durationMs: Long): VideoEdit {
        val end = ms.coerceIn((startMs + MinLengthMs).coerceAtMost(durationMs), durationMs)
        return copy(endMs = end.takeIf { it < durationMs })
    }

    fun rotatedLeft(): VideoEdit = copy(quarterTurns = (quarterTurns + 3) % 4)

    companion object {
        /** The shortest clip the trim handles allow. */
        const val MinLengthMs = 500L
    }
}

/** Saves an edited video as a new file. The original is never changed. */
object VideoEditSaver {

    /** Videos the editor opens: everything outside Hidden's vault. */
    fun canEdit(item: MediaItem): Boolean = item.isVideo && !Vault.isVault(item)

    /**
     * Writes [edit] of [item] as a new MP4 beside it (or in Movies/Edited), reporting how far it is
     * (0 to 1) as it goes. A trim or mute alone copies the video as it is, which is quick and loses
     * nothing; only what can't be copied is encoded again. Cancelling the coroutine stops the
     * export and leaves nothing behind. Returns whether the video was saved.
     */
    suspend fun saveCopy(
        context: Context,
        item: MediaItem,
        edit: VideoEdit,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.Main) {
        // Transformer writes to a path: made in Lumen's cache, then handed to MediaStore.
        val file = File.createTempFile("edit", ".mp4", context.cacheDir)
        try {
            export(context, item, edit, file, onProgress) && withContext(Dispatchers.IO) { publish(context, item, file) }
        } finally {
            file.delete()
        }
    }

    /** Runs Transformer on the main thread, as it requires, until it finishes or fails. */
    private suspend fun export(context: Context, item: MediaItem, edit: VideoEdit, file: File, onProgress: (Float) -> Unit): Boolean =
        coroutineScope {
            var transformer: Transformer? = null
            val watcher = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    val running = transformer
                    if (running != null && running.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    delay(150)
                }
            }
            val ok = suspendCancellableCoroutine { continuation ->
                val clip = PlayerMediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.startMs)
                    .apply { edit.endMs?.let(::setEndPositionMs) }
                    .build()
                val source = PlayerMediaItem.Builder().setUri(item.uri).setClippingConfiguration(clip).build()
                val effects = if (edit.quarterTurns == 0) Effects.EMPTY else Effects(
                    emptyList(),
                    // Counter-clockwise here, clockwise in VideoEdit.
                    listOf(ScaleAndRotateTransformation.Builder().setRotationDegrees(((4 - edit.quarterTurns) % 4) * 90f).build()),
                )
                val edited = EditedMediaItem.Builder(source).setRemoveAudio(edit.muted).setEffects(effects).build()
                // The new file carries the original's date, so it sits beside it in the library
                // instead of at today (MediaStore dates a video by what its file says).
                val stamp = Mp4TimestampData.unixTimeToMp4TimeSeconds(item.takenAt)
                val muxer = InAppMp4Muxer.Factory { entries ->
                    entries.removeAll { it is Mp4TimestampData }
                    entries.add(Mp4TimestampData(stamp, stamp))
                }
                val running = Transformer.Builder(context)
                    .setMuxerFactory(muxer)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            android.util.Log.w("VideoEdit", "Export failed", exportException)
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    .build()
                transformer = running
                running.start(edited, file.path)
                // Cancellation can come from any thread; Transformer only takes calls on its own.
                continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { running.cancel() } }
            }
            watcher.cancel()
            ok
        }

    /** Copies the finished [file] into the library, named after [item]. */
    private fun publish(context: Context, item: MediaItem, file: File): Boolean {
        val resolver = context.contentResolver
        val name = "${item.name.substringBeforeLast('.', item.name)}_edited.mp4"
        // Next to the original if MediaStore lets Lumen add videos there, else Movies/Edited.
        for (folder in listOf(item.relativePath, "Movies/Edited/")) {
            val uri = runCatching {
                resolver.insert(
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                        put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                )
            }.getOrNull() ?: continue
            val ok = runCatching {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            if (!ok) {
                resolver.delete(uri, null, null)
                return false
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return true
        }
        return false
    }
}
