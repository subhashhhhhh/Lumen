package com.subhashh.lumen.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
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
        "dng" to "image/x-adobe-dng", "cr2" to "image/x-canon-cr2", "cr3" to "image/x-canon-cr3",
        "nef" to "image/x-nikon-nef", "arw" to "image/x-sony-arw", "raf" to "image/x-fuji-raf",
        "orf" to "image/x-olympus-orf", "rw2" to "image/x-panasonic-rw2", "pef" to "image/x-pentax-pef",
        "srw" to "image/x-samsung-srw",
    )

    /** Caches and temp folders apps hide with .nomedia; not anyone's private photos. */
    private val JunkFolders = setOf(".thumbnails", ".trashed", ".trash", ".cache", "cache", "tmp", ".tmp", "temp", ".temp")

    fun available() = Environment.isExternalStorageManager()

    /**
     * Every hidden photo and video on shared storage, oldest first. Empty without All files access.
     * Dimensions are left at 0 for speed; [withSizes] fills them in afterwards. Exclusions are
     * the caller's job, so excluding or including a folder never needs a rescan.
     *
     * Walking 12k+ files with java.io through Android's storage layer took many seconds (every
     * entry is a slow stat), so the system's own `find` locates the dot-named entries and
     * `.nomedia` markers in one fast pass; only the hidden folders' contents are then listed.
     */
    suspend fun scan(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!available()) return@withContext emptyList()
        val root = Environment.getExternalStorageDirectory()
        val skip = File(root, "Android").absolutePath
        val hits = runCatching {
            val process = ProcessBuilder(
                "find", root.absolutePath, "-path", skip, "-prune", "-o",
                "(", "-name", ".*", "-o", "-name", NoMedia, ")", "-print",
            ).redirectErrorStream(false).start()
            process.inputStream.bufferedReader().readLines().also { process.waitFor() }
        }.getOrDefault(emptyList())

        val found = LinkedHashMap<String, MediaItem>()
        fun add(file: File) {
            val type = Types[file.extension.lowercase()] ?: return
            found.putIfAbsent(file.absolutePath, item(file, type, file.lastModified()))
        }
        fun isJunk(dir: File) = generateSequence(dir) { it.parentFile }
            .takeWhile { it != root }
            .any { it.name.lowercase() in JunkFolders }
        // Everything below a hidden folder is hidden too (Android treats .nomedia as recursive).
        fun addTree(dir: File) {
            if (isJunk(dir)) return
            dir.listFiles()?.forEach { if (it.isDirectory) addTree(it) else add(it) }
        }

        for (line in hits) {
            val file = File(line)
            when {
                file.name == NoMedia -> file.parentFile?.let(::addTree)
                file.isDirectory -> addTree(file)
                !isJunk(file.parentFile ?: root) -> add(file)
            }
        }
        found.values.sortedWith(compareBy<MediaItem> { it.takenAt }.thenBy { it.id })
    }

    /**
     * [items] with display dimensions (EXIF rotation applied, as MediaStore reports them) and,
     * for videos, durations. The open/close zoom needs the true shape: camera photos are often
     * stored sideways with a rotate tag. A separate pass so the Hidden grid can show the moment
     * [scan] returns.
     */
    suspend fun withSizes(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        items.map { item ->
            if (item.width > 0) return@map item
            val path = item.uri.path ?: return@map item
            runCatching { if (item.isVideo) videoInfo(item, path) else photoInfo(item, path) }.getOrDefault(item)
        }
    }

    private fun photoInfo(item: MediaItem, path: String): MediaItem {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val w = bounds.outWidth.coerceAtLeast(0)
        val h = bounds.outHeight.coerceAtLeast(0)
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        // 90° and 270°, plain or mirrored.
        val sideways = orientation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )
        return item.copy(width = if (sideways) h else w, height = if (sideways) w else h)
    }

    private fun videoInfo(item: MediaItem, path: String): MediaItem {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            fun int(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val w = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val sideways = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 == 90
            item.copy(
                width = if (sideways) h else w,
                height = if (sideways) w else h,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
            )
        } finally {
            retriever.release()
        }
    }

    /** True when the whole folder is hidden (.nomedia or a dot-name here or above), not just some files in it. */
    fun isHiddenFolder(folder: File): Boolean {
        val root = Environment.getExternalStorageDirectory()
        return generateSequence(folder) { it.parentFile }
            .takeWhile { it != root }
            .any { it.name.startsWith('.') || File(it, NoMedia).exists() }
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

    /** Type for a hidden file's extension, or null when it isn't a photo or video. */
    fun typeOf(file: File): String? = Types[file.extension.lowercase()]

    /** The item for a hidden [file]; [modified] in millis, sizes 0 when not read yet. */
    fun item(file: File, type: String, modified: Long, width: Int = 0, height: Int = 0, durationMs: Long = 0): MediaItem {
        val root = Environment.getExternalStorageDirectory()
        val folder = file.parentFile ?: root
        return MediaItem(
            // Negative, so it can never collide with a MediaStore id.
            id = -(file.absolutePath.hashCode().toLong() and 0x7fffffffL) - 1,
            uri = Uri.fromFile(file),
            mimeType = type,
            isVideo = type.startsWith("video/"),
            takenAt = modified,
            durationMs = durationMs,
            isFavorite = false,
            relativePath = folder.relativeTo(root).path.let { if (it.isEmpty()) "" else "$it/" },
            bucketId = folder.absolutePath.hashCode().toLong(),
            bucketName = folder.name,
            width = width,
            height = height,
            modified = modified / 1000,
            name = file.name,
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
