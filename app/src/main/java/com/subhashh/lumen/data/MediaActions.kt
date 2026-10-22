package com.subhashh.lumen.data

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.MediaStore

/**
 * Write operations go through MediaStore requests, which show a system confirmation dialog
 * unless the user has granted Lumen "Media management" (MANAGE_MEDIA, Android 12+).
 */
object MediaActions {

    fun share(context: Context, item: MediaItem) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Hands the location to whatever maps app the user has; nothing is sent anywhere by Lumen. */
    fun openInMaps(context: Context, point: GeoPoint) {
        val geo = "geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(geo))) }
    }

    /** Moves to the system trash, which Android purges after 30 days. */
    fun trashRequest(context: Context, item: MediaItem): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), true).intentSender

    fun restoreRequest(context: Context, item: MediaItem): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), false).intentSender

    /** Permanent — only offered from Recently Deleted. */
    fun deleteForeverRequest(context: Context, item: MediaItem): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri)).intentSender

    fun favoriteRequest(context: Context, item: MediaItem): IntentSender =
        MediaStore.createFavoriteRequest(context.contentResolver, listOf(item.uri), !item.isFavorite)
            .intentSender
}
