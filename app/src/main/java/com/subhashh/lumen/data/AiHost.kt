package com.subhashh.lumen.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaDataSource
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.PreferredColorConfig
import com.awxkee.jxlcoder.ScaleMode
import com.subhashh.lumen.GalleryActivity
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.AiNotifications
import com.subhashh.lumen.ai.AiSettings
import com.subhashh.lumen.ai.IndexSchedule
import com.subhashh.lumen.ai.LumenAiHost
import com.subhashh.lumen.ai.MediaRef
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.data.vault.VaultMediaDataSource

/** What Lumen AI needs from Lumen: the library, the user's settings, and the notification look. */
class AiHost(context: Context) : LumenAiHost {
    private val prefs = LumenPrefs(context.applicationContext)

    /** The library as the user sees it: no excluded folders, no Hidden. */
    override suspend fun library(context: Context): List<MediaRef> {
        val excluded = prefs.excludedFolders
        return MediaRepository(context).loadLibrary().filterNot { ExcludedFolders.covers(excluded, it) }.map { it.toRef() }
    }

    /** Encrypted Hidden videos, decrypted as they're read (a paused frame's Live Text). */
    override fun videoSource(context: Context, item: MediaRef): MediaDataSource? {
        val media = item.toMediaItem()
        if (!Vault.isVault(media)) return null
        val reader = Vault.reader(media) ?: return null
        val source = VaultMediaDataSource(reader)
        return object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int) = source.readAt(position, buffer, offset, size)
            override fun getSize() = source.size
            override fun close() = reader.close()
        }
    }

    /** JPEG XL photos, which Android can't decode: read with the bundled decoder, so they're searchable too. */
    override fun photo(context: Context, item: MediaRef, maxSide: Int): Bitmap? {
        if (item.mimeType != JxlType || item.uri.scheme != "content") return null
        val bytes = context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() } ?: return null
        val size = JxlCoder.getSize(bytes) ?: return null
        val long = maxOf(size.width, size.height)
        if (long <= maxSide) return JxlCoder.decode(bytes, PreferredColorConfig.RGBA_8888)
        val scale = maxSide.toFloat() / long
        return JxlCoder.decodeSampled(
            bytes,
            maxOf(1, (size.width * scale).toInt()),
            maxOf(1, (size.height * scale).toInt()),
            PreferredColorConfig.RGBA_8888,
            ScaleMode.FIT,
        )
    }

    override val settings = object : AiSettings {
        override val searchModelId get() = prefs.searchModel
        override val faceModelId get() = prefs.faceModel
        override val videoDetailPx get() = prefs.videoDetail.px
        override val indexOnBattery get() = prefs.indexOnBattery
        override val indexSchedule
            get() = if (prefs.indexScheduleOn) IndexSchedule(prefs.indexScheduleStart, prefs.indexScheduleEnd) else null
        override val videoTextRates get() = prefs.videoTextRates
        override val indexSearch get() = prefs.indexSearch
        override val indexText get() = prefs.indexText
        override val indexVideoText get() = prefs.indexVideoText
        override val indexFaces get() = prefs.indexFaces
    }

    override val notifications = object : AiNotifications {
        override val smallIcon = R.drawable.ic_launcher_monochrome
        override fun contentIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 0, Intent(context, GalleryActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    }
}

private const val JxlType = "image/jxl"

fun MediaItem.toRef() = MediaRef(
    id = id,
    uri = uri,
    mimeType = mimeType,
    isVideo = isVideo,
    takenAt = takenAt,
    durationMs = durationMs,
    // JPEG XL files indexed before Lumen could decode them were recorded as unreadable. A version
    // of its own (the indexes are keyed by it) has them read once more.
    modified = if (mimeType == JxlType) modified + 1 else modified,
    name = name,
)

/** Only for code that takes a MediaItem but reads just these fields (the vault's file lookup). */
private fun MediaRef.toMediaItem() = MediaItem(
    id = id, uri = uri, mimeType = mimeType, isVideo = isVideo, takenAt = takenAt, durationMs = durationMs,
    isFavorite = false, relativePath = "", bucketId = 0, bucketName = "", width = 0, height = 0,
    modified = modified, name = name,
)
