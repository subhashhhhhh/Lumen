package com.subhashh.lumen.ui

import android.content.Intent
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem

/**
 * Another app asked Lumen for photos or videos (attach, upload, profile picture). Lumen opens
 * as a picker: only the asked-for types, no viewer, and taps choose instead of opening.
 */
@Immutable
data class PickRequest(val images: Boolean, val videos: Boolean, val multiple: Boolean) {

    fun accepts(item: MediaItem) = if (item.isVideo) videos else images

    /** "Choose a Photo", "Choose Videos"… */
    @get:StringRes
    val title: Int
        get() = when {
            images && !videos -> if (multiple) R.string.main_pick_choose_photos else R.string.main_pick_choose_a_photo
            videos && !images -> if (multiple) R.string.main_pick_choose_videos else R.string.main_pick_choose_a_video
            else -> if (multiple) R.string.main_pick_choose_items else R.string.main_pick_choose_an_item
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
