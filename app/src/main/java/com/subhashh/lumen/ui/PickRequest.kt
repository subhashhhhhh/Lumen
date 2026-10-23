package com.subhashh.lumen.ui

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import com.subhashh.lumen.data.MediaItem

/**
 * Another app asked Lumen for photos or videos (attach, upload, profile picture). Lumen opens
 * as a picker: only the asked-for types, no viewer, and taps choose instead of opening.
 */
@Immutable
data class PickRequest(val images: Boolean, val videos: Boolean, val multiple: Boolean) {

    fun accepts(item: MediaItem) = if (item.isVideo) videos else images

    /** "Choose a Photo", "Choose Videos"… */
    val title: String
        get() = when {
            images && !videos -> if (multiple) "Choose Photos" else "Choose a Photo"
            videos && !images -> if (multiple) "Choose Videos" else "Choose a Video"
            else -> if (multiple) "Choose Items" else "Choose an Item"
        }

    companion object {
        /** The request in [intent], or null when it's a normal launch. */
        fun from(intent: Intent?): PickRequest? {
            val action = intent?.action
            if (action != Intent.ACTION_GET_CONTENT && action != Intent.ACTION_PICK) return null
            // PICK can name the collection by Uri instead of by type.
            val type = intent.type ?: intent.data?.let { uri ->
                when {
                    uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) -> "image/*"
                    uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) -> "video/*"
                    else -> null
                }
            }
            val images = type == null || type.startsWith("image/") || type.endsWith("/image") || type.startsWith("*/")
            val videos = type == null || type.startsWith("video/") || type.endsWith("/video") || type.startsWith("*/")
            return PickRequest(
                images = images || !videos,
                videos = videos,
                multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
            )
        }
    }
}
