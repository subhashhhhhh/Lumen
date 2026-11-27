package com.subhashh.lumen.data.backup

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import com.subhashh.lumen.data.MediaRepository
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Puts a backup's photos, videos and vault files back: only what isn't on the phone already (same
 * folder, name and size), so restoring onto the phone that made the backup writes next to nothing.
 * Runs before the ids are matched, so restored photos get their faces, captions and indexes back.
 */
internal object MediaRestore {

    /** Folders MediaStore lets an app create files in without All files access. */
    private val StandardRoots = listOf("DCIM/", "Pictures/", "Movies/", "Download/", "Documents/")

    /**
     * Photo rows are [relativePath, name, size, takenAt, mimeType, chunks]. With All files access
     * each goes back to its own folder with its date; without it, through MediaStore, into its
     * folder if Android allows it there, else under Pictures/Lumen Restore.
     */
    suspend fun photos(context: Context, rows: JSONArray, read: (String) -> ByteArray, progress: (Long, Long) -> Unit): Int {
        val here = MediaRepository(context).loadLibrary().mapTo(HashSet()) { "${it.relativePath}${it.name}|${it.size}" }
        val missing = (0 until rows.length()).map { rows.getJSONArray(it) }.filter { "${it.getString(0)}${it.getString(1)}|${it.getLong(2)}" !in here }
        val total = missing.sumOf { it.getLong(2) }
        var done = 0L
        val written = ArrayList<String>()
        val allFiles = Environment.isExternalStorageManager()
        for (row in missing) {
            coroutineContext.ensureActive()
            val path = row.getString(0)
            val name = row.getString(1)
            val takenAt = row.getLong(3)
            val mime = row.getString(4)
            val chunks = row.getJSONArray(5)
            fun copyTo(out: OutputStream) {
                for (i in 0 until chunks.length()) {
                    val chunk = read(chunks.getString(i))
                    out.write(chunk)
                    done += chunk.size
                    progress(done, total)
                }
            }
            runCatching {
                if (allFiles) {
                    require(!path.contains("..") && !name.contains('/')) { "Bad path in a backup" }
                    val target = uniqueFile(File(Environment.getExternalStorageDirectory(), path), name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use(::copyTo)
                    if (takenAt > 0) target.setLastModified(takenAt)
                    written += target.path
                } else {
                    val video = mime.startsWith("video/")
                    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val folder = if (StandardRoots.any { path.startsWith(it) }) path else "Pictures/Lumen Restore/$path"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        if (takenAt > 0) put(MediaStore.MediaColumns.DATE_TAKEN, takenAt)
                    }
                    val uri = context.contentResolver.insert(collection, values) ?: error("Couldn't add $name")
                    try {
                        context.contentResolver.openOutputStream(uri)?.use(::copyTo) ?: error("Couldn't write $name")
                        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    } catch (e: Exception) {
                        context.contentResolver.delete(uri, null, null)
                        throw e
                    }
                }
            }.onFailure { android.util.Log.w("LumenBackup", "Couldn't restore $path$name", it) }
        }
        if (written.isNotEmpty()) scan(context, written)
        return missing.size
    }

    /** Vault rows are [name, size, chunks]; files already there are left alone. Needs All files access. */
    suspend fun vault(context: Context, rows: JSONArray, read: (String) -> ByteArray, progress: (Long, Long) -> Unit): Int {
        if (!Environment.isExternalStorageManager()) return 0
        val dir = com.subhashh.lumen.data.vault.Vault.dir().apply { mkdirs() }
        val missing = (0 until rows.length()).map { rows.getJSONArray(it) }.filter { row ->
            val f = File(dir, row.getString(0))
            !f.isFile || f.length() != row.getLong(1)
        }
        val total = missing.sumOf { it.getLong(1) }
        var done = 0L
        for (row in missing) {
            coroutineContext.ensureActive()
            val name = row.getString(0)
            require(!name.contains('/') && !name.contains("..")) { "Bad name in a backup" }
            val chunks = row.getJSONArray(2)
            val tmp = File(dir, "$name.restoring")
            tmp.outputStream().use { out ->
                for (i in 0 until chunks.length()) {
                    val chunk = read(chunks.getString(i))
                    out.write(chunk)
                    done += chunk.size
                    progress(done, total)
                }
            }
            File(dir, name).delete()
            tmp.renameTo(File(dir, name))
        }
        return missing.size
    }

    /** "IMG_1.jpg", or "IMG_1 (2).jpg" if a different file of that name is there. */
    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var n = 2
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (file.exists()) file = File(dir, "$base ($n)$ext").also { n++ }
        return file
    }

    private suspend fun scan(context: Context, paths: List<String>) = suspendCancellableCoroutine { cont ->
        var left = paths.size
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
            if (--left == 0 && cont.isActive) cont.resume(Unit)
        }
    }
}
