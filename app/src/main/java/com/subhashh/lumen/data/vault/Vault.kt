package com.subhashh.lumen.data.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Settings → Privacy → Encrypt Hidden: hidden photos and videos moved into an encrypted folder,
 * `.LumenVault` on shared storage, where other apps and file managers see only random names and
 * scrambled bytes. It's outside Lumen's sandbox on purpose, so uninstalling Lumen doesn't delete
 * it; the user's Lumen lock opens it again after a reinstall (see [VaultKeys]).
 *
 * Items in it are [MediaItem]s with a `lumenvault:<file>` Uri, shown inside Hidden alongside
 * Fossify-style hidden files, and decrypted only in memory or Lumen's private cache.
 */
object Vault {

    const val Scheme = "lumenvault"
    private const val Extension = ".lumenvault"

    fun dir(): File = File(Environment.getExternalStorageDirectory(), ".LumenVault")

    /** The master key while the vault is open (Hidden unlocked); null otherwise. */
    @Volatile
    private var key: SecretKey? = null

    /** Headers already read, by vault file name and size: listing doesn't re-decrypt them. */
    private val listed = HashMap<String, MediaItem>()

    val isOpen: Boolean get() = key != null

    private fun keys(context: Context) = VaultKeys(context, dir())

    /** A vault exists on this phone's storage (maybe from an earlier install). */
    fun exists(context: Context) = keys(context).exists

    /**
     * Opens the vault: from the Keystore copy on this install, else with [secret] (the Lumen
     * lock) from the recovery file. False if neither works.
     */
    fun open(context: Context, secret: String? = null): Boolean {
        if (key != null) return true
        val keys = keys(context)
        key = keys.unlockOnDevice() ?: secret?.let(keys::unlockWithSecret)
        return key != null
    }

    /** Hidden locked again: forget the key until the next unlock. */
    fun close() {
        key = null
        synchronized(listed) { listed.clear() }
    }

    /** A new, empty vault sealed with [secret]; open afterwards. */
    fun create(context: Context, secret: String) {
        key = keys(context).create(secret)
    }

    /** The Lumen lock changed: reseal the recovery copy with the new one. */
    fun changeSecret(context: Context, newSecret: String) {
        val keys = keys(context)
        if (!keys.exists) return
        val k = key ?: keys.unlockOnDevice() ?: return
        keys.changeSecret(k, newSecret)
    }

    fun isVault(item: MediaItem) = item.uri.scheme == Scheme

    fun file(item: MediaItem): File? =
        if (isVault(item)) File(dir(), item.uri.schemeSpecificPart) else null

    /** Random access to an item's decrypted bytes (video playback, frames). */
    fun reader(item: MediaItem): VaultCrypto.Reader? {
        val k = key ?: return null
        return file(item)?.let { VaultCrypto.Reader(k, it) }
    }

    /** Decrypts the whole item into [output] (photos, which are decoded whole anyway). */
    fun decrypt(item: MediaItem, output: OutputStream): Boolean {
        val k = key ?: return false
        val f = file(item) ?: return false
        return runCatching { VaultCrypto.decrypt(k, f, output) }.isSuccess
    }

    /** Everything in the vault, oldest first. Empty while it's closed. */
    suspend fun list(): List<MediaItem> = withContext(Dispatchers.IO) {
        val k = key ?: return@withContext emptyList()
        val files = dir().listFiles { f -> f.isFile && f.name.endsWith(Extension) }.orEmpty()
        files.mapNotNull { f ->
            val cacheKey = "${f.name}:${f.length()}"
            synchronized(listed) { listed[cacheKey] }?.let { return@mapNotNull it }
            runCatching { Details.parse(VaultCrypto.readHeader(k, f)).toItem(f) }.getOrNull()
                ?.also { synchronized(listed) { listed[cacheKey] = it } }
        }.sortedWith(compareBy<MediaItem> { it.takenAt }.thenBy { it.id })
            .also(VaultIndex::update)
    }

    /**
     * Encrypts [source] into the vault and deletes it, but only once the encrypted copy has been
     * read back and checked against the original byte for byte (by SHA-256).
     */
    suspend fun add(context: Context, source: File, details: Details): Boolean = withContext(Dispatchers.IO) {
        val k = key ?: return@withContext false
        val dir = dir().apply { mkdirs() }
        val name = UUID.randomUUID().toString() + Extension
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        val ok = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(source.inputStream().buffered(), digest).use { input ->
                part.outputStream().buffered().use { out -> VaultCrypto.encrypt(k, details.toJson(), input, out) }
            }
            val written = digest.digest()
            val check = MessageDigest.getInstance("SHA-256")
            VaultCrypto.decrypt(k, part, DigestOutputStream(check))
            check.digest().contentEquals(written) && part.renameTo(target)
        }.getOrDefault(false)
        if (!ok) {
            part.delete()
            return@withContext false
        }
        source.delete()
        // MediaStore forgets the original if it knew it (a visible photo hidden straight into the vault).
        scan(context, listOf(source.absolutePath))
        true
    }

    /**
     * Decrypts [item] back to storage and removes it from the vault: as it was hidden ([hidden],
     * when encryption is turned off) or visible (unhide). An unhidden file whose folder is itself
     * hidden goes to Pictures/Restored, so it really shows up. Returns the restored file.
     */
    suspend fun restore(context: Context, item: MediaItem, hidden: Boolean): File? = withContext(Dispatchers.IO) {
        val k = key ?: return@withContext null
        val f = file(item) ?: return@withContext null
        val details = runCatching { Details.parse(VaultCrypto.readHeader(k, f)) }.getOrNull() ?: return@withContext null
        val root = Environment.getExternalStorageDirectory()
        var folder = File(root, details.path)
        if (!hidden && isHiddenFolder(folder, root)) folder = File(root, "Pictures/Restored")
        folder.mkdirs()
        val target = unique(File(folder, if (hidden) details.hiddenName else details.visibleName))
        val ok = runCatching {
            target.outputStream().buffered().use { VaultCrypto.decrypt(k, f, it) }
            target.setLastModified(details.modified)
        }.isSuccess
        if (!ok) {
            target.delete()
            return@withContext null
        }
        f.delete()
        synchronized(listed) { listed.keys.removeAll { it.startsWith(f.name) } }
        if (!hidden) scan(context, listOf(target.absolutePath))
        target
    }

    fun delete(item: MediaItem): Boolean = file(item)?.delete() == true

    /** A decrypted copy in Lumen's private cache, for sharing or opening in another app. */
    suspend fun cachedCopy(context: Context, item: MediaItem): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "vault-share").apply { mkdirs() }
        val out = File(dir, item.name.ifEmpty { "photo" })
        val ok = runCatching { out.outputStream().buffered().use { decrypt(item, it) } }.getOrDefault(false)
        if (ok) out else null.also { out.delete() }
    }

    /** Clears decrypted share copies; called when Hidden locks. */
    fun clearCache(context: Context) {
        File(context.cacheDir, "vault-share").deleteRecursively()
    }

    private fun isHiddenFolder(folder: File, root: File): Boolean =
        generateSequence(folder) { it.parentFile }.takeWhile { it != root }
            .any { it.name.startsWith('.') || File(it, ".nomedia").exists() }

    private fun unique(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension.let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(1) { it + 1 }.map { File(file.parentFile, "$base ($it)$ext") }.first { !it.exists() }
    }

    private suspend fun scan(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            var remaining = paths.size
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (--remaining == 0 && cont.isActive) cont.resume(Unit) {}
            }
        }
    }

    /** An OutputStream that only feeds a digest: verifying without writing anything. */
    private class DigestOutputStream(private val digest: MessageDigest) : OutputStream() {
        override fun write(b: Int) = digest.update(b.toByte())
        override fun write(b: ByteArray, off: Int, len: Int) = digest.update(b, off, len)
    }

    /**
     * What a vault file was: carried encrypted in its header.
     *
     * @param hiddenName the name to restore it under while hidden (".IMG_1.jpg", or "IMG_1.jpg"
     *   in a .nomedia folder); [visibleName] the name when unhidden
     * @param path its folder, relative to storage ("Pictures/hot/")
     */
    data class Details(
        val hiddenName: String,
        val visibleName: String,
        val path: String,
        val mime: String,
        val takenAt: Long,
        val modified: Long,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val size: Long,
    ) {
        fun toJson(): ByteArray = JSONObject()
            .put("hiddenName", hiddenName).put("visibleName", visibleName).put("path", path).put("mime", mime)
            .put("takenAt", takenAt).put("modified", modified).put("width", width).put("height", height)
            .put("durationMs", durationMs).put("size", size)
            .toString().toByteArray()

        fun toItem(file: File): MediaItem {
            val root = Environment.getExternalStorageDirectory()
            val folder = File(root, path)
            return MediaItem(
                id = -(file.name.hashCode().toLong() and 0x7fffffffL) - 1,
                uri = Uri.fromParts(Scheme, file.name, null),
                mimeType = mime,
                isVideo = mime.startsWith("video/"),
                takenAt = takenAt,
                durationMs = durationMs,
                isFavorite = false,
                relativePath = path,
                // Same folder id as Fossify-style hidden files there, so both group together.
                bucketId = folder.absolutePath.hashCode().toLong(),
                bucketName = folder.name,
                width = width,
                height = height,
                modified = modified / 1000,
                name = visibleName,
                size = size,
            )
        }

        companion object {
            fun parse(bytes: ByteArray): Details {
                val json = JSONObject(bytes.decodeToString())
                return Details(
                    hiddenName = json.getString("hiddenName"),
                    visibleName = json.getString("visibleName"),
                    path = json.getString("path"),
                    mime = json.getString("mime"),
                    takenAt = json.getLong("takenAt"),
                    modified = json.getLong("modified"),
                    width = json.optInt("width"),
                    height = json.optInt("height"),
                    durationMs = json.optLong("durationMs"),
                    size = json.optLong("size"),
                )
            }

            /** Details for a file that's hidden today (Fossify-style) or about to be hidden ([visible] = true). */
            fun of(item: MediaItem, file: File, visible: Boolean): Details {
                val root = Environment.getExternalStorageDirectory()
                val parent = file.parentFile ?: root
                val path = parent.relativeTo(root).path.let { if (it.isEmpty()) "" else "$it/" }
                return Details(
                    // Hidden by its own dot, or by its folder; a visible file gets a dot.
                    hiddenName = if (visible) ".${file.name}" else file.name,
                    visibleName = file.name.trimStart('.'),
                    path = path,
                    mime = item.mimeType,
                    takenAt = item.takenAt,
                    modified = file.lastModified(),
                    width = item.width,
                    height = item.height,
                    durationMs = item.durationMs,
                    size = file.length(),
                )
            }
        }
    }
}
