package com.subhashh.lumen.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    /** Epoch millis; falls back to date added when the file has no capture date. */
    val takenAt: Long,
    val durationMs: Long,
)
