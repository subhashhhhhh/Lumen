package com.subhashh.lumen.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaDataSource
import com.subhashh.lumen.MainActivity
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

    override val settings = object : AiSettings {
        override val searchModelId get() = prefs.searchModel
        override val videoDetailPx get() = prefs.videoDetail.px
        override val indexOnBattery get() = prefs.indexOnBattery
        override val indexSchedule
            get() = if (prefs.indexScheduleOn) IndexSchedule(prefs.indexScheduleStart, prefs.indexScheduleEnd) else null
        override val videoTextRates get() = prefs.videoTextRates
    }

    override val notifications = object : AiNotifications {
        override val smallIcon = R.drawable.ic_launcher_monochrome
        override fun contentIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    }
}

fun MediaItem.toRef() = MediaRef(
    id = id,
    uri = uri,
    mimeType = mimeType,
    isVideo = isVideo,
    takenAt = takenAt,
    durationMs = durationMs,
    modified = modified,
    name = name,
)

/** Only for code that takes a MediaItem but reads just these fields (the vault's file lookup). */
private fun MediaRef.toMediaItem() = MediaItem(
    id = id, uri = uri, mimeType = mimeType, isVideo = isVideo, takenAt = takenAt, durationMs = durationMs,
    isFavorite = false, relativePath = "", bucketId = 0, bucketName = "", width = 0, height = 0,
    modified = modified, name = name,
)
