package com.subhashh.lumen.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import com.subhashh.lumen.R
import androidx.core.content.FileProvider
import androidx.print.PrintHelper

/**
 * Write operations go through MediaStore requests, which show a system confirmation dialog
 * unless the user has granted Lumen "Media management" (MANAGE_MEDIA, Android 12+). Batch
 * versions confirm once for the whole selection.
 */
object MediaActions {

    fun share(context: Context, item: MediaItem) = share(context, listOf(item))

    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.map { shareableUri(context, it) }
        val send = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items[0].mimeType
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType(items)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Lets the user pick another app to open the file in (an editor, another viewer...). */
    fun openWith(context: Context, item: MediaItem) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(view, null))
    }

    /** "Set as" wallpaper, contact photo and so on: whatever the installed apps offer. */
    fun setAs(context: Context, item: MediaItem) {
        val attach = Intent(Intent.ACTION_ATTACH_DATA)
            .setDataAndType(item.uri, item.mimeType)
            .putExtra("mimeType", item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(attach, null))
    }

    /** Copies the photo itself (not a path) so it can be pasted into a chat or a document. */
    fun copyToClipboard(context: Context, item: MediaItem) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, context.getString(R.string.data_photo), item.uri))
    }

    /** Android's own print flow (printer, PDF, copies); scaled to fit the page. */
    fun print(context: Context, item: MediaItem) {
        runCatching {
            PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }
                .printBitmap(item.uri.lastPathSegment ?: context.getString(R.string.data_photo), item.uri)
        }
    }

    /** Hands the location to whatever maps app the user has; nothing is sent anywhere by Lumen. */
    fun openInMaps(context: Context, point: GeoPoint) {
        val geo = "geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(geo))) }
    }

    /** Moves to the system trash, which Android purges after 30 days. */
    fun trashRequest(context: Context, item: MediaItem): IntentSender = trashRequest(context, listOf(item))

    fun trashRequest(context: Context, items: List<MediaItem>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, true).intentSender

    fun restoreRequest(context: Context, item: MediaItem): IntentSender = restoreRequest(context, listOf(item))

    fun restoreRequest(context: Context, items: List<MediaItem>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, false).intentSender

    /** Permanent — only offered from Recently Deleted. */
    fun deleteForeverRequest(context: Context, item: MediaItem): IntentSender =
        deleteForeverRequest(context, listOf(item))

    fun deleteForeverRequest(context: Context, items: List<MediaItem>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri }).intentSender

    /**
     * Sets Android's shared favorite (seen by every gallery, kept across reinstalls) straight in
     * MediaStore, instant like an app's own list, when Lumen may write it: with All files access.
     * False when it may not; ask with [favoriteRequest] then, which opens a system screen (with
     * Media management it doesn't ask, but the screen still pauses the app for a moment).
     */
    fun setFavoriteDirectly(context: Context, items: List<MediaItem>, favorite: Boolean): Boolean {
        if (!android.os.Environment.isExternalStorageManager()) return false
        if (items.any { it.uri.scheme != "content" }) return false
        val values = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0) }
        return try {
            for (item in items) context.contentResolver.update(item.uri, values, null, null)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun favoriteRequest(context: Context, item: MediaItem): IntentSender =
        favoriteRequest(context, listOf(item), favorite = !item.isFavorite)

    fun favoriteRequest(context: Context, items: List<MediaItem>, favorite: Boolean): IntentSender =
        MediaStore.createFavoriteRequest(context.contentResolver, items.map { it.uri }, favorite).intentSender

    /** Hidden files are file:// Uris, which other apps can't open; hand them a provider Uri instead. */
    private fun shareableUri(context: Context, item: MediaItem): Uri {
        if (item.uri.scheme == "file" && item.uri.path?.contains("/vault-share/") == true) {
            return FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(item.uri.path!!))
        }
        val file = HiddenMedia.file(item)
        return if (item.uri.scheme == "file" && file != null) {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else {
            item.uri
        }
    }

    // Any image type when all are photos, any video type when all are videos, else anything.
    private fun commonMimeType(items: List<MediaItem>): String = when {
        items.all { !it.isVideo } -> "image/*"
        items.all { it.isVideo } -> "video/*"
        else -> "*/*"
    }
}
