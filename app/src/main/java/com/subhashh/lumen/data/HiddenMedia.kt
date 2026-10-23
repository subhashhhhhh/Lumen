package com.subhashh.lumen.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Hidden photos and videos, which MediaStore deliberately doesn't index: files whose name starts
 * with a dot, and anything in a folder holding a `.nomedia` file (or in a dot-folder). These are
 * Android's own conventions — Fossify uses the same ones, so content hidden there shows up here
 * and the other way round. Reading them needs All files access.
 */
object HiddenMedia {

    private val Types = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
        "gif" to "image/gif", "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "3gp" to "video/3gpp", "mov" to "video/quicktime",
    )

    /** Caches and temp folders apps hide with .nomedia; not anyone's private photos. */
    private val JunkFolders = setOf(".thumbnails", ".trashed", ".trash", ".cache", "cache", "tmp", ".tmp", "temp", ".temp")

    fun available() = Environment.isExternalStorageManager()

    /** Every hidden photo and video on shared storage, oldest first. Empty without All files access. */
    suspend fun scan(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!available()) return@withContext emptyList()
        val root = Environment.getExternalStorageDirectory()
        val found = ArrayList<MediaItem>()

        fun walk(dir: File, hiddenAbove: Boolean) {
            val children = dir.listFiles() ?: return
            val hidden = hiddenAbove ||
                (dir != root && dir.name.startsWith('.')) ||
                children.any { it.name == NoMedia }
            for (child in children) {
                if (child.isDirectory) {
                    if (dir == root && child.name == "Android") continue
                    if (child.name.lowercase() in JunkFolders) continue
                    walk(child, hidden)
                } else {
                    val type = Types[child.extension.lowercase()] ?: continue
                    if (hidden || child.name.startsWith('.')) found += child.toItem(root, type)
                }
            }
        }

        walk(root, false)
        found.sortWith(compareBy<MediaItem> { it.takenAt }.thenBy { it.id })
        found
    }

    /** True for items from [scan] (file Uris rather than MediaStore ones). */
    fun isHidden(item: MediaItem) = item.uri.scheme == "file"

    fun file(item: MediaItem): File? = item.uri.path?.let(::File)

    /**
     * Hides each file by renaming it with a leading dot, then re-scans the old path so MediaStore
     * drops it. Returns how many were hidden.
     */
    suspend fun hide(context: Context, paths: List<String>): Int = withContext(Dispatchers.IO) {
        var done = 0
        for (path in paths) {
            val file = File(path)
            if (file.name.startsWith('.')) continue
            val target = File(file.parentFile, ".${file.name}")
            if (target.exists() || !file.renameTo(target)) continue
            scan(context, listOf(file))
            done++
        }
        done
    }

    /** Hides whole folders with a `.nomedia` file; their contents leave MediaStore on re-scan. */
    suspend fun hideFolders(context: Context, folders: List<File>): Int = withContext(Dispatchers.IO) {
        var done = 0
        for (folder in folders) {
            val marker = File(folder, NoMedia)
            if (!marker.exists() && !runCatching { marker.createNewFile() }.getOrDefault(false)) continue
            scan(context, folder.listFiles()?.filter { it.isFile }.orEmpty())
            done++
        }
        done
    }

    /** What unhiding an item will actually do, so the UI can say so before doing it. */
    sealed interface Unhide {
        /** The file's own name is hidden: rename it back. */
        data class File(val file: java.io.File) : Unhide

        /** A `.nomedia` marker hides it, along with everything else in that folder. */
        data class Folder(val folder: java.io.File) : Unhide

        /** It lives in a dot-folder; Lumen won't rename folders other apps may depend on. */
        data class DotFolder(val folder: java.io.File) : Unhide
    }

    fun unhidePlan(item: MediaItem): Unhide? {
        val file = file(item) ?: return null
        if (file.name.startsWith('.')) return Unhide.File(file)
        var dir = file.parentFile
        val root = Environment.getExternalStorageDirectory()
        while (dir != null && dir != root) {
            if (File(dir, NoMedia).exists()) return Unhide.Folder(dir)
            if (dir.name.startsWith('.')) return Unhide.DotFolder(dir)
            dir = dir.parentFile
        }
        return null
    }

    /** Carries out [plans]; returns how many succeeded. */
    suspend fun unhide(context: Context, plans: Collection<Unhide>): Int = withContext(Dispatchers.IO) {
        var done = 0
        for (plan in plans) {
            when (plan) {
                is Unhide.File -> {
                    val target = File(plan.file.parentFile, plan.file.name.trimStart('.'))
                    if (!target.exists() && plan.file.renameTo(target)) {
                        scan(context, listOf(target))
                        done++
                    }
                }
                is Unhide.Folder -> {
                    if (File(plan.folder, NoMedia).delete()) {
                        scan(context, plan.folder.listFiles()?.filter { it.isFile }.orEmpty())
                        done++
                    }
                }
                is Unhide.DotFolder -> Unit
            }
        }
        done
    }

    /** Permanent: hidden files have no system trash to go to. */
    suspend fun delete(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        items.count { item -> file(item)?.delete() == true }
    }

    private fun File.toItem(root: File, type: String): MediaItem {
        val isVideo = type.startsWith("video/")
        var width = 0
        var height = 0
        if (!isVideo) {
            // Header only: cheap, and the open/close zoom needs the shape.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(absolutePath, bounds)
            width = bounds.outWidth.coerceAtLeast(0)
            height = bounds.outHeight.coerceAtLeast(0)
        }
        val folder = parentFile ?: root
        return MediaItem(
            // Negative, so it can never collide with a MediaStore id.
            id = -(absolutePath.hashCode().toLong() and 0x7fffffffL) - 1,
            uri = Uri.fromFile(this),
            mimeType = type,
            isVideo = isVideo,
            takenAt = lastModified(),
            durationMs = 0,
            isFavorite = false,
            relativePath = folder.relativeTo(root).path.let { if (it.isEmpty()) "" else "$it/" },
            bucketId = folder.absolutePath.hashCode().toLong(),
            bucketName = folder.name,
            width = width,
            height = height,
            modified = lastModified() / 1000,
        )
    }

    private suspend fun scan(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            var remaining = files.size
            MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null) { _, _ ->
                if (--remaining == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    private const val NoMedia = ".nomedia"
}
