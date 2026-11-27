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

    private const val RestoreRoot = "Pictures/Lumen Restore/"

    /** Folders MediaStore lets an app create files in without All files access. */
    private val StandardRoots = listOf("DCIM/", "Pictures/", "Movies/", "Download/", "Documents/")

    /**
     * Photo rows are [relativePath, name, size, takenAt, mimeType, chunks]. With All files access
     * each goes back to its own folder with its date; without it, through MediaStore, into its
     * folder if Android allows it there, else under Pictures/Lumen Restore.
     */
    suspend fun photos(context: Context, rows: JSONArray, read: (String) -> ByteArray, progress: (Long, Long) -> Unit): Int {
        val library = MediaRepository(context).loadLibrary()
        val here = library.mapTo(HashSet()) { "${it.relativePath}${it.name}|${it.size}" }
        // A photo restored before may sit under Pictures/Lumen Restore/ (no All files access), or
        // have been renamed "IMG (2).jpg" beside a different file: same size and moment taken.
        val sameMoment = library.filter { it.takenAt > 0 }.mapTo(HashSet()) { "${it.size}|${it.takenAt}" }
        val missing = (0 until rows.length()).map { rows.getJSONArray(it) }.filter { row ->
            val path = row.getString(0)
            val name = row.getString(1)
            val size = row.getLong(2)
            val takenAt = row.getLong(3)
            "$path$name|$size" !in here && "$RestoreRoot$path$name|$size" !in here && (takenAt <= 0 || "$size|$takenAt" !in sameMoment)
        }
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
                require(!path.contains("..") && name != ".." && name != "." && !name.contains('/')) { "Bad path in a backup" }
                if (allFiles) {
                    val target = uniqueFile(File(Environment.getExternalStorageDirectory(), path), name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use(::copyTo)
                    if (takenAt > 0) target.setLastModified(takenAt)
                    written += target.path
                } else {
                    val video = mime.startsWith("video/")
                    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val folder = if (StandardRoots.any { path.startsWith(it) }) path else "$RestoreRoot$path"
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

    /** What a vault restore did: files added, and files left out because they wouldn't open here. */
    data class VaultResult(val restored: Int, val wontOpen: Int, val locked: Boolean)

    /**
     * Vault rows are [name, size, chunks]. A file already in the vault is never replaced: it may
     * have changed since (re-encrypted, moved to a newer format), and the backup's copy might not
     * open any more. With no vault here, everything goes back, its key file too, so the old Hidden
     * password opens it. With a vault here, a missing file goes in only if it opens with that
     * vault's key (Hidden must be unlocked to check); one that doesn't is left out and counted.
     * Needs All files access.
     */
    suspend fun vault(context: Context, rows: JSONArray, read: (String) -> ByteArray, progress: (Long, Long) -> Unit): VaultResult {
        if (!Environment.isExternalStorageManager()) return VaultResult(0, 0, locked = false)
        val vault = com.subhashh.lumen.data.vault.Vault
        val fresh = !vault.exists()
        val dir = vault.dir().apply { mkdirs() }
        val missing = (0 until rows.length()).map { rows.getJSONArray(it) }.filter { row ->
            val name = row.getString(0)
            // Into an existing vault only hidden files, each checked below; its key and anything
            // else (a leftover .tmp or .part) belong to the vault that made the backup.
            !File(dir, name).exists() && (fresh || name.endsWith(".lumenvault"))
        }
        if (missing.isEmpty()) return VaultResult(0, 0, locked = false)
        // Into an existing vault only what opens with its key, which needs Hidden unlocked.
        if (!fresh && !vault.unlocked) {
            return VaultResult(0, missing.size, locked = true)
        }
        val total = missing.sumOf { it.getLong(1) }
        var done = 0L
        var restored = 0
        var wontOpen = 0
        for (row in missing) {
            coroutineContext.ensureActive()
            val name = row.getString(0)
            require(!name.contains('/') && name != ".." && name != ".") { "Bad name in a backup" }
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
            if (!fresh && !vault.opens(tmp)) {
                tmp.delete()
                wontOpen++
                continue
            }
            if (!tmp.renameTo(File(dir, name))) tmp.delete() else restored++
        }
        return VaultResult(restored, wontOpen, locked = false)
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
