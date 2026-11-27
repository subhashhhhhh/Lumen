package com.subhashh.lumen.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaRepository
import com.subhashh.lumen.data.SettingsBackup
import com.subhashh.lumen.data.vault.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.coroutineContext

/**
 * Lumen Backup: Lumen's own data (stage 1; photos and videos come next) to a folder the person
 * picked, encrypted or not, and back.
 *
 * Like Time Machine, the first backup copies everything and later ones only what changed: each
 * file is cut into 4 MB chunks stored once under a fingerprint (`data/ab/<name>`), and each backup
 * is a small list of the chunks it needs (`snapshots/<date>.snap`). A database that changed a
 * little re-uploads only the chunks that changed. Every snapshot is still a complete backup.
 *
 * In the folder (all inside "Lumen Backup"):
 * - `lumen-backup.json`: format, whether it's encrypted, and the backup key locked twice
 *   ([BackupCrypto]); never anything secret in the clear.
 * - `data/`: the chunks, encrypted with AES-GCM if the backup is (named by a keyed hash then).
 * - `snapshots/`: one list per backup, encrypted the same way.
 *
 * On this phone the backup key is kept sealed by the Android Keystore, so backups run without
 * asking for the password; restoring on another phone asks for the password or recovery key.
 */
object LumenBackup {
    /** What can be backed up in stage 1. [key] is stored in snapshots: never rename it. */
    enum class Item(val key: String, @StringRes val label: Int, val defaultOn: Boolean = true) {
        Settings("settings", R.string.backup_item_settings),
        Captions("captions", R.string.backup_item_captions),
        People("people", R.string.backup_item_people),
        SearchIndex("search", R.string.backup_item_search),
        TextIndex("text", R.string.backup_item_text),
        /** The library's photos and videos (excluded folders left out). Big, so off until chosen. */
        Photos("photos", R.string.backup_item_photos, defaultOn = false),
        /** Encrypted Hidden: the vault's files as they are (already encrypted with its password). */
        Hidden("vault", R.string.backup_item_hidden, defaultOn = false),
    }

    private const val Format = 1
    private const val FolderName = "Lumen Backup"
    private const val ConfigName = "lumen-backup.json"
    private const val ChunkBytes = 4 * 1024 * 1024

    // ---- Where, and with which key ----

    class Config(
        val id: String,
        val encrypted: Boolean,
        val passwordLock: BackupCrypto.PasswordLock?,
        val recoveryLock: ByteArray?,
    ) {
        fun toJson(): String = JSONObject().apply {
            put("format", Format)
            put("id", id)
            put("encrypted", encrypted)
            passwordLock?.let {
                put("password", JSONObject().put("kdf", "Argon2id").put("salt", BackupCrypto.b64(it.salt)).put("memoryKiB", it.memoryKiB).put("passes", it.passes).put("key", BackupCrypto.b64(it.sealed)))
            }
            recoveryLock?.let { put("recovery", JSONObject().put("key", BackupCrypto.b64(it))) }
        }.toString(2)

        companion object {
            fun parse(text: String): Config {
                val json = JSONObject(text)
                require(json.getInt("format") <= Format) { "A newer Lumen made this backup" }
                val password = json.optJSONObject("password")?.let {
                    BackupCrypto.PasswordLock(BackupCrypto.unb64(it.getString("salt")), it.getInt("memoryKiB"), it.getInt("passes"), BackupCrypto.unb64(it.getString("key")))
                }
                val recovery = json.optJSONObject("recovery")?.let { BackupCrypto.unb64(it.getString("key")) }
                return Config(json.getString("id"), json.getBoolean("encrypted"), password, recovery)
            }
        }
    }

    /** The backup set up on this phone: its folder and config, and the key (null if unencrypted). */
    class Target(val folder: BackupStore, val dirId: String, val config: Config, val keys: BackupCrypto.Keys?)

    private fun prefs(context: Context) = context.getSharedPreferences("lumen-backup", Context.MODE_PRIVATE)

    /** The folder backups go to, once one is set up (a picked folder, or a server). */
    fun location(context: Context): BackupLocation? {
        val prefs = prefs(context)
        prefs.getString("folder", null)?.let { return BackupLocation.Folder(Uri.parse(it)) }
        val sealed = prefs.getString("server", null) ?: return null
        return runCatching { BackupLocation.Server(BackupServer.parse(JSONObject(String(unseal(sealed))))) }.getOrNull()
    }

    /**
     * A Lumen backup already in [location] (to restore from, or to keep backing up into), or null
     * if there's none. Throws if the place can't be read, or holds a config that won't parse:
     * "none" would lead to setting up a new backup over it.
     */
    suspend fun existing(context: Context, location: BackupLocation): Config? = withContext(Dispatchers.IO) {
        val folder = location.open(context)
        val dir = folder.child(folder.rootId, FolderName) ?: return@withContext null
        if (folder.child(dir, ConfigName) == null && folder.child(dir, ConfigNew) == null) return@withContext null
        readConfig(folder, dir) ?: throw java.io.IOException(context.getString(R.string.backup_config_unreadable))
    }

    /**
     * Starts a new backup in [location]: encrypted with [password] if given. Returns the recovery key
     * to show the person once (null for an unencrypted backup).
     */
    suspend fun setUp(context: Context, location: BackupLocation, password: String?): String? = withContext(Dispatchers.IO) {
        val folder = location.open(context)
        val dir = folder.dir(folder.rootId, FolderName)
        // Never over another backup's config: its key locks are the only way into it.
        if (folder.child(dir, ConfigName) != null || folder.child(dir, ConfigNew) != null) {
            throw java.io.IOException(context.getString(R.string.backup_already_here))
        }
        var recovery: String? = null
        val config = if (password != null) {
            val key = BackupCrypto.newBackupKey()
            recovery = BackupCrypto.newRecoveryKey()
            Config(UUID.randomUUID().toString(), true, BackupCrypto.lockWithPassword(key, password), BackupCrypto.lockWithRecoveryKey(key, recovery))
                .also { keepKey(context, it.id, key) }
        } else {
            Config(UUID.randomUUID().toString(), false, null, null)
        }
        writeConfig(folder, dir, config)
        use(context, location, config)
        recovery
    }

    private const val ConfigNew = "$ConfigName.new"

    /** Chunks uploading at once (and files read at once while backing up). */
    private const val Parallel = 4

    /**
     * Writes the config without a moment where neither copy is whole: both key locks live in it,
     * so a write cut short in place would leave a backup nobody can open. The new config goes to
     * a side file first, then over the original; [readConfig] falls back to the side file.
     */
    private fun writeConfig(folder: BackupStore, dir: String, config: Config) {
        val bytes = config.toJson().toByteArray()
        folder.write(dir, ConfigNew, bytes)
        folder.write(dir, ConfigName, bytes)
        folder.delete(dir, ConfigNew)
    }

    /** The config, or the side file if the config is missing or cut short (an interrupted [writeConfig]). */
    private fun readConfig(folder: BackupStore, dir: String): Config? {
        for (name in listOf(ConfigName, ConfigNew)) {
            val id = folder.child(dir, name) ?: continue
            runCatching { return Config.parse(String(folder.read(id))) }
        }
        return null
    }

    /** Opens an existing backup in [location] with its password or recovery key; false if neither fits. */
    suspend fun connect(context: Context, location: BackupLocation, password: String?, recoveryKey: String?): Boolean = withContext(Dispatchers.IO) {
        val config = runCatching { existing(context, location) }.getOrNull() ?: return@withContext false
        if (config.encrypted) {
            val key = password?.let { p -> config.passwordLock?.let { BackupCrypto.unlockWithPassword(it, p) } }
                ?: recoveryKey?.let { r -> config.recoveryLock?.let { BackupCrypto.unlockWithRecoveryKey(it, r) } }
                ?: return@withContext false
            keepKey(context, config.id, key)
        }
        use(context, location, config)
        // "Last backup" from the folder's newest one, not from this phone's memory.
        snapshots(context).firstOrNull()?.let { prefs(context).edit { putLong("last", it.created) } }
        true
    }

    private fun use(context: Context, location: BackupLocation, config: Config) {
        releaseFolder(context)
        prefs(context).edit {
            remove("folder").remove("server")
            when (location) {
                is BackupLocation.Folder -> {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            location.tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    putString("folder", location.tree.toString())
                }
                is BackupLocation.Server -> putString("server", seal(location.server.toJson().toString().toByteArray()))
            }
            putString("id", config.id).putBoolean("encrypted", config.encrypted)
        }
        BackupSchedule.apply(context)
    }

    private fun releaseFolder(context: Context) {
        val uri = (location(context) as? BackupLocation.Folder)?.tree ?: return
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    /** Forgets the backup on this phone (the folder and its backups stay). */
    fun disconnect(context: Context) {
        releaseFolder(context)
        prefs(context).edit { clear() }
        BackupSchedule.apply(context)
    }

    suspend fun target(context: Context): Target? = withContext(Dispatchers.IO) {
        val folder = location(context)?.open(context) ?: return@withContext null
        val dir = folder.child(folder.rootId, FolderName) ?: return@withContext null
        val config = readConfig(folder, dir) ?: return@withContext null
        val keys = if (config.encrypted) BackupCrypto.Keys(keptKey(context, config.id) ?: return@withContext null) else null
        Target(folder, dir, config, keys)
    }

    /** Makes a new recovery key while the backup is open on this phone; the old one stops working. */
    suspend fun newRecoveryKey(context: Context): String? = withContext(Dispatchers.IO) {
        val target = target(context) ?: return@withContext null
        val key = keptKey(context, target.config.id) ?: return@withContext null
        val recovery = BackupCrypto.newRecoveryKey()
        val config = Config(target.config.id, true, target.config.passwordLock, BackupCrypto.lockWithRecoveryKey(key, recovery))
        writeConfig(target.folder, target.dirId, config)
        recovery
    }

    /** Sets a new password (after a forgotten one was replaced through the recovery key, say). */
    suspend fun changePassword(context: Context, password: String): Boolean = withContext(Dispatchers.IO) {
        val target = target(context) ?: return@withContext false
        val key = keptKey(context, target.config.id) ?: return@withContext false
        val config = Config(target.config.id, true, BackupCrypto.lockWithPassword(key, password), target.config.recoveryLock)
        writeConfig(target.folder, target.dirId, config)
        true
    }

    // The backup key on this phone, sealed by a Keystore key that never leaves the hardware.
    private const val KeystoreAlias = "lumen-backup"

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KeystoreAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KeystoreAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    /** [bytes] sealed by the Keystore key, as text for prefs: the backup key, a server's login. */
    private fun seal(bytes: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        return BackupCrypto.b64(cipher.iv + cipher.doFinal(bytes))
    }

    private fun unseal(text: String): ByteArray {
        val sealed = BackupCrypto.unb64(text)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, sealed, 0, 12)) }
            .doFinal(sealed, 12, sealed.size - 12)
    }

    private fun keepKey(context: Context, id: String, key: ByteArray) {
        prefs(context).edit { putString("key-$id", seal(key)) }
    }

    private fun keptKey(context: Context, id: String): ByteArray? = runCatching {
        unseal(prefs(context).getString("key-$id", null) ?: return null)
    }.getOrNull()

    // ---- Backing up ----

    data class Progress(@StringRes val step: Int, val done: Long, val total: Long)

    data class Done(val snapshot: String, val uploaded: Long, val total: Long)

    /** Backs up [items] to the folder set up on this phone. Only chunks the folder lacks are written. */
    suspend fun backUp(
        context: Context,
        items: Set<Item>,
        /** Called between chunks; suspends while the backup is paused. */
        gate: suspend () -> Unit = {},
        progress: (Progress) -> Unit,
    ): Done = withContext(Dispatchers.IO) {
        val target = target(context) ?: error("No backup folder set up")
        val work = File(context.cacheDir, "backup-work").apply { deleteRecursively(); mkdirs() }
        try {
            val library = MediaRepository(context).loadLibrary()
            val sources = sources(context, items, library, work)
            val exclusions = com.subhashh.lumen.data.LumenPrefs(context).exclusions
            val foldersOff = foldersOff(context)
            val photos = if (Item.Photos in items) library.filterNot { exclusions.covers(it) || it.relativePath in foldersOff } else emptyList()
            val vaultFiles = if (Item.Hidden in items) vaultFiles() else emptyList()
            val total = sources.sumOf { it.second.length() } + photos.sumOf { it.size } + vaultFiles.sumOf { it.length() }
            val done = java.util.concurrent.atomic.AtomicLong()
            val uploaded = java.util.concurrent.atomic.AtomicLong()
            val store = ChunkStore(target)
            val catalog = Catalog.load(context, target.config.id)
            val step = Progress(R.string.backup_step_uploading, 0, total)
            // Up to [Parallel] chunks on their way at once, from one file or several: a server's
            // round trip per chunk, not the network, was the limit (3 MB/s over Wi-Fi one by one).
            val inFlight = kotlinx.coroutines.sync.Semaphore(Parallel)
            suspend fun upload(open: () -> java.io.InputStream?): JSONArray? = kotlinx.coroutines.coroutineScope {
                val parts = ArrayList<kotlinx.coroutines.Deferred<String>>()
                (open() ?: return@coroutineScope null).use { input ->
                    val buffer = ByteArray(ChunkBytes)
                    while (true) {
                        gate()
                        coroutineContext.ensureActive()
                        val n = readFully(input, buffer)
                        if (n <= 0) break
                        inFlight.acquire()
                        val plain = if (n == ChunkBytes) buffer.copyOf() else buffer.copyOf(n)
                        parts += async {
                            try {
                                val (id, written) = store.put(plain)
                                uploaded.addAndGet(written)
                                progress(step.copy(done = done.addAndGet(n.toLong())))
                                id
                            } finally {
                                inFlight.release()
                            }
                        }
                    }
                }
                JSONArray(parts.awaitAll())
            }
            /** Like [upload], but a file unchanged since the last backup reuses its chunks unread (Time Machine). */
            suspend fun uploadKnown(key: String, size: Long, open: () -> java.io.InputStream?): JSONArray? {
                catalog[key]?.takeIf { known -> known.all(store::has) }?.let { known ->
                    progress(step.copy(done = done.addAndGet(size)))
                    return JSONArray(known)
                }
                return upload(open)?.also { chunks -> catalog[key] = (0 until chunks.length()).map(chunks::getString) }
            }
            /** [work] on each of [list] by [Parallel] workers; results in [list]'s order, null where skipped. */
            suspend fun <T, R : Any> eachInParallel(list: List<T>, work: suspend (T) -> R?): List<R?> = kotlinx.coroutines.coroutineScope {
                val results = arrayOfNulls<Any>(list.size)
                val next = java.util.concurrent.atomic.AtomicInteger()
                List(Parallel) {
                    launch {
                        while (true) {
                            val i = next.getAndIncrement()
                            if (i >= list.size) break
                            coroutineContext.ensureActive()
                            results[i] = work(list[i])
                        }
                    }
                }.joinAll()
                @Suppress("UNCHECKED_CAST")
                results.toList() as List<R?>
            }
            val files = JSONObject()
            for ((name, file) in sources) {
                coroutineContext.ensureActive()
                files.put(name, JSONObject().put("size", file.length()).put("chunks", upload { FileInputStream(file) }))
            }
            // Photos and videos, read as originals (with their location, where Lumen may read it).
            val readOriginals = context.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val photoRows = JSONArray()
            val vaultRows = JSONArray()
            // Saved even when the backup is stopped or fails: files already sent aren't sent again.
            try {
                val photoChunks = eachInParallel(photos) { item ->
                    // Null: gone or unreadable since the library was listed; next time.
                    unlessUnreadable {
                        uploadKnown("m|${item.id}|${item.size}|${item.modified}", item.size) {
                            val uri = if (readOriginals && com.subhashh.lumen.data.ExternalMedia.isMediaStore(item.uri)) android.provider.MediaStore.setRequireOriginal(item.uri) else item.uri
                            runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: context.contentResolver.openInputStream(item.uri)
                        }
                    }
                }
                photos.forEachIndexed { i, item ->
                    val chunks = photoChunks[i] ?: return@forEachIndexed
                    photoRows.put(JSONArray().put(item.relativePath).put(item.name).put(item.size).put(item.takenAt).put(item.mimeType).put(chunks).put(item.modified))
                }
                val vaultChunks = eachInParallel(vaultFiles) { file ->
                    unlessUnreadable { uploadKnown("v|${file.name}|${file.length()}|${file.lastModified()}", file.length()) { file.inputStream() } }
                }
                vaultFiles.forEachIndexed { i, file ->
                    val chunks = vaultChunks[i] ?: return@forEachIndexed
                    vaultRows.put(JSONArray().put(file.name).put(file.length()).put(chunks))
                }
            } finally {
                catalog.save()
            }
            // Which photo each id was here, so a restore can find them again on another phone.
            val media = JSONArray()
            for (item in library) media.put(JSONArray().put(item.id).put(item.relativePath).put(item.name).put(item.size).put(item.takenAt))
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val snapshot = JSONObject()
                .put("format", Format)
                .put("created", System.currentTimeMillis())
                // "motorola edge 50", not "motorola motorola edge 50": many models already start with the maker.
                .put("device", if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("items", JSONArray(items.map { it.key }))
                .put("searchModel", runCatching { com.subhashh.lumen.ai.ModelDownloads.installed(context)?.id }.getOrNull())
                .put("files", files)
                .put("media", media)
            if (Item.Photos in items) snapshot.put("photos", photoRows)
            if (Item.Hidden in items) snapshot.put("vault", vaultRows)
            // Compressed: with tens of thousands of photos the list runs to megabytes.
            val bytes = gzip(snapshot.toString().toByteArray())
            val dir = target.folder.dir(target.dirId, "snapshots")
            target.folder.write(dir, "$stamp.snap", target.keys?.let { VaultCrypto.seal(it.data, bytes) } ?: bytes)
            prefs(context).edit { putLong("last", System.currentTimeMillis()).putLong("last-size", total) }
            Done(stamp, uploaded.get(), total)
        } finally {
            work.deleteRecursively()
        }
    }

    // ---- Which folders' photos go in (Settings → Backup → Folders) ----

    private fun choices(context: Context) = context.getSharedPreferences("lumen-backup-choices", Context.MODE_PRIVATE)

    /**
     * Folders (relative paths, "DCIM/Camera/") whose photos and videos aren't backed up. Every
     * folder is in unless turned off, new ones too, so nothing is missed by default.
     */
    fun foldersOff(context: Context): Set<String> = choices(context).getStringSet("photo-folders-off", emptySet()).orEmpty()

    fun setFolderOn(context: Context, folder: String, on: Boolean) {
        val off = foldersOff(context).toMutableSet()
        if (on) off -= folder else off += folder
        choices(context).edit { putStringSet("photo-folders-off", off) }
    }

    /** A folder with photos or videos, as the folder list shows it. */
    data class PhotoFolder(val path: String, val name: String, val count: Int, val bytes: Long, val cover: com.subhashh.lumen.data.MediaItem)

    /** The library's folders, biggest first, without folders Lumen is told to leave out (Settings → Library). */
    suspend fun photoFolders(context: Context): List<PhotoFolder> = withContext(Dispatchers.IO) {
        val exclusions = com.subhashh.lumen.data.LumenPrefs(context).exclusions
        MediaRepository(context).loadLibrary()
            .filterNot { exclusions.covers(it) }
            .groupBy { it.relativePath }
            .map { (path, items) -> PhotoFolder(path, items.first().bucketName.ifEmpty { path.trimEnd('/') }, items.size, items.sumOf { it.size }, items.maxBy { it.takenAt }) }
            .sortedByDescending { it.bytes }
    }

    fun lastBackup(context: Context): Long = prefs(context).getLong("last", 0L)

    /** A message from the last restore for Settings → Backup to show once, then forget. */
    fun takeNote(context: Context): String? = prefs(context).getString("note", null)?.also { prefs(context).edit { remove("note") } }

    private fun vaultFiles(): List<File> =
        runCatching { com.subhashh.lumen.data.vault.Vault.dir().listFiles { f -> f.isFile }.orEmpty().toList() }.getOrDefault(emptyList())

    private fun gzip(bytes: ByteArray): ByteArray =
        java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    /** A snapshot's text: compressed since stage 2, plain before. */
    private fun ungzip(bytes: ByteArray): ByteArray =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes

    /**
     * What each photo, video and vault file became in the last backups to this folder: its id,
     * size and modified date → its chunks. A file that hasn't changed reuses them without being
     * read again, so a backup of 40,000 photos with 10 new ones reads 10.
     */
    private class Catalog(private val file: File, private val entries: HashMap<String, List<String>>) {
        @Synchronized operator fun get(key: String) = entries[key]
        @Synchronized operator fun set(key: String, chunks: List<String>) { entries[key] = chunks }

        @Synchronized fun save() {
            val json = JSONObject()
            for ((k, v) in entries) json.put(k, JSONArray(v))
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(gzip(json.toString().toByteArray()))
            tmp.renameTo(file)
        }

        companion object {
            fun load(context: Context, backupId: String): Catalog {
                val file = File(context.noBackupFilesDir, "backup-catalog-$backupId.json.gz")
                val entries = HashMap<String, List<String>>()
                runCatching {
                    val json = JSONObject(String(ungzip(file.readBytes())))
                    for (k in json.keys()) json.getJSONArray(k).let { a -> entries[k] = (0 until a.length()).map(a::getString) }
                }
                return Catalog(file, entries)
            }
        }
    }

    /** Fills [buffer] as far as the stream goes (readNBytes is Android 13+); the count read. */
    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Int {
        var n = 0
        while (n < buffer.size) {
            val got = input.read(buffer, n, buffer.size - n)
            if (got < 0) break
            n += got
        }
        return n
    }

    /** The chunk folders, listed once each and remembered, so a chunk already there isn't written again. */
    /** The backup's folder or server failed: the whole backup stops, unlike one unreadable photo. */
    class StoreFailed(cause: Throwable) : java.io.IOException(cause.message, cause)

    /**
     * [block]'s result, or null if a photo or vault file couldn't be read (deleted since the
     * library was listed, say): it's skipped this time. Stopping and store failures still end
     * the backup.
     */
    private inline fun <T> unlessUnreadable(block: () -> T): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: StoreFailed) {
        throw e
    } catch (e: Exception) {
        null
    }

    private class ChunkStore(private val target: Target) {
        private val data = target.folder.dir(target.dirId, "data")
        private val dirs = HashMap<String, String>()
        /** Chunks being written now: the same chunk twice at once is written once. */
        private val writing = HashMap<String, Any>()

        /** The prefix folder for [name], made once even with several uploads at once. */
        private fun dirFor(name: String): String = synchronized(dirs) {
            dirs.getOrPut(name.take(2)) { target.folder.dir(data, name.take(2)) }
        }

        fun has(name: String): Boolean = storing {
            val dir = dirFor(name)
            target.folder.child(dir, name) != null
        }

        /** Stores [plain] unless it's there; its name and the bytes written (0 if it was there). */
        fun put(plain: ByteArray): Pair<String, Long> {
            val name = target.keys?.name(plain) ?: BackupCrypto.plainName(plain)
            val written = storing { writeOnce(name, plain) }
            return name to written
        }

        // No early returns inside the locks: a return from a synchronized block inside an inline
        // lambda made bytecode ART's verifier rejects (VerifyError on the whole class).
        private fun writeOnce(name: String, plain: ByteArray): Long {
            val dir = dirFor(name)
            // One writer per chunk name; a second upload of the same chunk waits, then finds it.
            val lock = synchronized(writing) { writing.getOrPut(name) { Any() } }
            try {
                synchronized(lock) {
                    var written = 0L
                    if (target.folder.child(dir, name) == null) {
                        val bytes = target.keys?.let { VaultCrypto.seal(it.data, plain) } ?: plain
                        target.folder.write(dir, name, bytes)
                        written = bytes.size.toLong()
                    }
                    return written
                }
            } finally {
                synchronized(writing) { writing.remove(name) }
            }
        }

        /** A failure writing to the backup (server offline, folder gone) as [StoreFailed]. */
        private inline fun <T> storing(block: () -> T): T =
            try { block() } catch (e: StoreFailed) { throw e } catch (e: Exception) { throw StoreFailed(e) }

        fun get(name: String): ByteArray {
            val dir = dirFor(name)
            val bytes = target.folder.read(target.folder.child(dir, name) ?: error("A backup chunk is missing"))
            val plain = target.keys?.let { VaultCrypto.open(it.data, bytes) } ?: bytes
            // A chunk is named by its content: a damaged file (a cloud provider, an SD card) or two
            // swapped ones don't match, and the restore stops rather than put wrong data in place.
            check((target.keys?.name(plain) ?: BackupCrypto.plainName(plain)) == name) { "A backup chunk is damaged" }
            return plain
        }
    }

    /** Each item's files, as consistent copies in [work]: name in the snapshot → file. */
    private suspend fun sources(context: Context, items: Set<Item>, library: List<com.subhashh.lumen.data.MediaItem>, work: File): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val noBackup = context.noBackupFilesDir
        if (Item.Settings in items) {
            val file = File(work, "settings.json")
            SettingsBackup.export(context, library.filter { it.isFavorite }, Uri.fromFile(file))
            out += "settings.json" to file
        }
        if (Item.Captions in items) databaseCopy(context.getDatabasePath("captions.db"), File(work, "captions.db"))?.let { out += "captions.db" to it }
        if (Item.People in items) {
            databaseCopy(File(noBackup, "search/faces.db"), File(work, "faces.db"))?.let { out += "faces.db" to it }
            out += "faces.pack" to pack(File(noBackup, "faces"), File(work, "faces.pack"))
        }
        if (Item.SearchIndex in items) {
            val id = runCatching { com.subhashh.lumen.ai.ModelDownloads.installed(context)?.id }.getOrNull()
            if (id != null) databaseCopy(File(noBackup, "search/$id.db"), File(work, "search.db"))?.let { out += "search.db" to it }
        }
        if (Item.TextIndex in items) databaseCopy(File(noBackup, "search/text.db"), File(work, "text.db"))?.let { out += "text.db" to it }
        return out
    }

    /**
     * A consistent copy of a database Lumen may be writing to: its log is folded in, then writes
     * are held back while the file is copied (a moment; indexing waits for it).
     */
    private fun databaseCopy(source: File, copy: File): File? {
        if (!source.isFile) return null
        val db = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.beginTransactionNonExclusive()
            try {
                source.copyTo(copy, overwrite = true)
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        return copy
    }

    /** A folder's files in one stream, sorted by name, so new files only add to the end (few new chunks). */
    private fun pack(dir: File, out: File): File {
        DataOutputStream(out.outputStream().buffered()).use { stream ->
            for (file in dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }) {
                stream.writeUTF(file.name)
                stream.writeLong(file.length())
                file.inputStream().use { it.copyTo(stream) }
            }
        }
        return out
    }

    internal fun unpack(pack: File, dir: File) {
        dir.mkdirs()
        DataInputStream(pack.inputStream().buffered()).use { stream ->
            while (true) {
                val name = try { stream.readUTF() } catch (end: java.io.EOFException) { break }
                require(!name.contains('/') && !name.contains("..")) { "Bad name in a backup" }
                val size = stream.readLong()
                File(dir, name).outputStream().use { out ->
                    var left = size
                    val buffer = ByteArray(64 * 1024)
                    while (left > 0) {
                        val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (n < 0) error("A backup file is cut short")
                        out.write(buffer, 0, n)
                        left -= n
                    }
                }
            }
        }
    }

    // ---- Snapshots and restoring ----

    class Snapshot(val name: String, val created: Long, val device: String, val items: List<Item>, internal val json: JSONObject)

    private fun readSnapshot(target: Target, name: String, id: String): Snapshot {
        val raw = target.folder.read(id)
        val json = JSONObject(String(ungzip(target.keys?.let { VaultCrypto.open(it.data, raw) } ?: raw)))
        val items = json.getJSONArray("items").let { a -> (0 until a.length()).mapNotNull { i -> Item.entries.firstOrNull { it.key == a.getString(i) } } }
        return Snapshot(name.removeSuffix(".snap"), json.getLong("created"), json.optString("device"), items, json)
    }

    /** The backups in the folder, newest first. */
    suspend fun snapshots(context: Context): List<Snapshot> = withContext(Dispatchers.IO) {
        val target = target(context) ?: return@withContext emptyList()
        val dir = target.folder.child(target.dirId, "snapshots") ?: return@withContext emptyList()
        target.folder.list(dir).filterKeys { it.endsWith(".snap") }.mapNotNull { (name, id) ->
            runCatching { readSnapshot(target, name, id) }.getOrNull()
        }.sortedByDescending { it.created }
    }

    /** Every chunk [json] (a snapshot) needs: its files', photos' and vault files'. */
    private fun chunksOf(json: JSONObject): Set<String> {
        val out = HashSet<String>()
        fun add(chunks: JSONArray?) { if (chunks != null) for (i in 0 until chunks.length()) out += chunks.getString(i) }
        json.optJSONObject("files")?.let { files -> for (k in files.keys()) add(files.getJSONObject(k).optJSONArray("chunks")) }
        json.optJSONArray("photos")?.let { rows -> for (i in 0 until rows.length()) add(rows.getJSONArray(i).optJSONArray(5)) }
        json.optJSONArray("vault")?.let { rows -> for (i in 0 until rows.length()) add(rows.getJSONArray(i).optJSONArray(2)) }
        return out
    }

    /**
     * Clears out old backups: all but the newest [keep] snapshots (0 keeps them all), then chunks
     * no snapshot left needs (old backups', and a stopped run's). Two rules keep it safe:
     * - if any snapshot in the folder can't be read, nothing is deleted (it might need them);
     * - a chunk goes only when it was unneeded at the last clean-up too, so a backup another
     *   phone is still writing into the same folder (its chunks not in a snapshot yet) keeps them.
     */
    suspend fun prune(context: Context, keep: Int) = withContext(Dispatchers.IO) {
        val target = target(context) ?: return@withContext
        val dir = target.folder.child(target.dirId, "snapshots") ?: return@withContext
        val snapshots = target.folder.list(dir).filterKeys { it.endsWith(".snap") }.map { (name, id) ->
            runCatching { readSnapshot(target, name, id) }.getOrNull() ?: return@withContext
        }.sortedByDescending { it.created }
        val (kept, dropped) = if (keep > 0) snapshots.take(keep) to snapshots.drop(keep) else snapshots to emptyList()
        for (old in dropped) target.folder.delete(dir, "${old.name}.snap")
        val needed = HashSet<String>().apply { for (s in kept) addAll(chunksOf(s.json)) }
        val data = target.folder.child(target.dirId, "data") ?: return@withContext
        val unneeded = HashSet<String>()
        val earlier = prefs(context).getStringSet("unneeded-${target.config.id}", emptySet()).orEmpty()
        for ((_, prefixId) in target.folder.list(data).toList()) {
            for (name in target.folder.list(prefixId).keys.toList()) {
                if (name in needed) continue
                if (name in earlier) target.folder.delete(prefixId, name) else unneeded += name
            }
        }
        prefs(context).edit { putStringSet("unneeded-${target.config.id}", unneeded) }
    }

    /**
     * Gets [items] from [snapshot] ready to put in place: downloaded, and each photo's id in the
     * databases changed to its id on this phone (found by folder, name and size; ids differ on
     * another phone). Nothing in use is touched: [RestoreStaging] swaps the files in when Lumen
     * next starts, before anything opens them.
     */
    /**
     * Fetches [chunks] in order into [sink], up to [Parallel] ahead: one at a time, a server's
     * round trip per chunk set the pace. [gate] waits while paused.
     */
    private suspend fun fetchInOrder(store: ChunkStore, chunks: JSONArray, gate: suspend () -> Unit, sink: (ByteArray) -> Unit) =
        kotlinx.coroutines.coroutineScope {
            val ahead = ArrayDeque<kotlinx.coroutines.Deferred<ByteArray>>()
            for (i in 0 until chunks.length()) {
                gate()
                coroutineContext.ensureActive()
                val id = chunks.getString(i)
                ahead.addLast(async { store.get(id) })
                if (ahead.size >= Parallel) sink(ahead.removeFirst().await())
            }
            while (ahead.isNotEmpty()) sink(ahead.removeFirst().await())
        }

    suspend fun stageRestore(
        context: Context,
        snapshot: Snapshot,
        items: Set<Item>,
        gate: suspend () -> Unit = {},
        progress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val target = target(context) ?: error("No backup folder set up")
        val store = ChunkStore(target)
        val fetch: suspend (JSONArray, (ByteArray) -> Unit) -> Unit = { chunks, sink -> fetchInOrder(store, chunks, gate, sink) }
        val staging = RestoreStaging.dir(context).apply { deleteRecursively(); mkdirs() }
        val files = snapshot.json.getJSONObject("files")
        val wanted = buildList {
            if (Item.Settings in items) add("settings.json")
            if (Item.Captions in items) add("captions.db")
            if (Item.People in items) { add("faces.db"); add("faces.pack") }
            if (Item.SearchIndex in items) add("search.db")
            if (Item.TextIndex in items) add("text.db")
        }.filter { files.has(it) }
        val total = wanted.sumOf { files.getJSONObject(it).getLong("size") }
        var done = 0L
        for (name in wanted) {
            val chunks = files.getJSONObject(name).getJSONArray("chunks")
            File(staging, name).outputStream().use { out ->
                fetch(chunks) { chunk ->
                    out.write(chunk)
                    done += chunk.size
                    progress(Progress(R.string.backup_step_downloading, done, total))
                }
            }
        }
        // Photos, videos and vault files first, so restored photos are found when ids are matched.
        if (Item.Photos in items) snapshot.json.optJSONArray("photos")?.let { rows ->
            MediaRestore.photos(context, rows, fetch) { d, t -> progress(Progress(R.string.backup_step_photos, d, t)) }
        }
        if (Item.Hidden in items) snapshot.json.optJSONArray("vault")?.let { rows ->
            val result = MediaRestore.vault(context, rows, fetch) { d, t -> progress(Progress(R.string.backup_step_hidden, d, t)) }
            // Said after the restart (Settings → Backup), where the person looks next.
            val note = when {
                result.locked -> context.getString(R.string.backup_restore_hidden_locked, result.wontOpen)
                result.wontOpen > 0 -> context.resources.getQuantityString(R.plurals.backup_restore_hidden_other_key, result.wontOpen, result.wontOpen)
                else -> null
            }
            prefs(context).edit { if (note != null) putString("note", note) else remove("note") }
        }
        progress(Progress(R.string.backup_step_matching, 0, 0))
        val map = RestoreStaging.matchMedia(snapshot.json.getJSONArray("media"), MediaRepository(context).loadLibrary().associateBy({ it.id }) { it })
        RestoreStaging.remapAll(staging, map)
        RestoreStaging.commit(context, staging, snapshot.json.optString("searchModel").takeIf { it.isNotEmpty() && it != "null" })
    }
}
