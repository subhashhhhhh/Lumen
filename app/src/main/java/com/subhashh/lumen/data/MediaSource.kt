package com.subhashh.lumen.data

import android.content.Context
import com.subhashh.lumen.R

/**
 * Where a photo or video came from, as far as its folder and name tell: the camera, an app that
 * saved it, or a screenshot of an app ("Screenshot of WhatsApp"). Nothing is read from the file.
 */
object MediaSource {

    /** Folder words (lowercase) and the app they mean, most specific first. */
    private val Folders = listOf(
        "whatsapp business" to "WhatsApp Business",
        "whatsapp" to "WhatsApp",
        "telegram" to "Telegram",
        "signal" to "Signal",
        "instagram" to "Instagram",
        "threads" to "Threads",
        "snapchat" to "Snapchat",
        "messenger" to "Messenger",
        "facebook" to "Facebook",
        "twitter" to "X",
        "reddit" to "Reddit",
        "tiktok" to "TikTok",
        "discord" to "Discord",
        "pinterest" to "Pinterest",
        "youtube" to "YouTube",
        "snapseed" to "Snapseed",
        "lightroom" to "Lightroom",
        "picsart" to "PicsArt",
        "capcut" to "CapCut",
        "bluetooth" to "Bluetooth",
        "quick share" to "Quick Share",
        "nearby share" to "Quick Share",
    )

    /** Name prefixes some apps give their files wherever they're saved. */
    private val Prefixes = listOf(
        "RDT_" to "Reddit",
        "Snapchat-" to "Snapchat",
        "PXL_" to "Camera",
    )

    fun describe(context: Context, relativePath: String, name: String): String? {
        val folder = relativePath.lowercase()
        // "Screenshot_20261021-133849_WhatsApp.png" (Samsung: "…_20261021_133849_YouTube.jpg"):
        // the app is what's left after the date and time.
        if ("screenshot" in folder || name.startsWith("Screenshot", ignoreCase = true)) {
            val app = name.substringBeforeLast('.').split('_').drop(1)
                .filterNot { part -> part.all { it.isDigit() || it == '-' } }
                .joinToString(" ").trim()
            return if (app.isEmpty()) context.getString(R.string.data_source_screenshot) else context.getString(R.string.data_source_screenshot_of, app)
        }
        if ("screen record" in folder || "screenrecord" in folder || name.startsWith("Screen_Recording", ignoreCase = true)) {
            return context.getString(R.string.data_source_screen_recording)
        }
        Folders.firstOrNull { (word, _) -> word in folder }?.let { return localized(context, it.second) }
        // WhatsApp's "IMG-20261005-WA0001" and "VID-…-WA0026", wherever they were saved.
        if ("-WA" in name) return "WhatsApp"
        Prefixes.firstOrNull { (prefix, _) -> name.startsWith(prefix) }?.let { return localized(context, it.second) }
        if (folder.startsWith("dcim/camera") || folder.startsWith("dcim/100") || folder == "dcim/") return context.getString(R.string.data_source_camera)
        if (folder.startsWith("download")) return context.getString(R.string.data_source_downloads)
        return null
    }

    /** App names stay as they are; the generic words in the lists above are translated. */
    private fun localized(context: Context, label: String): String = when (label) {
        "Camera" -> context.getString(R.string.data_source_camera)
        "Downloads" -> context.getString(R.string.data_source_downloads)
        else -> label
    }
}
