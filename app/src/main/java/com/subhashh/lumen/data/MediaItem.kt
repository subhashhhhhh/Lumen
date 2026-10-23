package com.subhashh.lumen.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val mimeType: String,
    val isVideo: Boolean,
    /** Epoch millis: EXIF capture time, else file modified time. */
    val takenAt: Long,
    val durationMs: Long,
    val isFavorite: Boolean,
    /** Folder the file lives in, e.g. "DCIM/Camera/". */
    val relativePath: String,
    val bucketId: Long,
    val bucketName: String,
    /** Display dimensions (EXIF rotation applied); 0 when unknown. */
    val width: Int,
    val height: Int,
    /** DATE_MODIFIED (seconds): changes whenever the file does (rotate, edit), so it versions every cached image of it. */
    val modified: Long,
)
