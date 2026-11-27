package com.subhashh.lumen.data.backup

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * What Lumen Backup needs from wherever backups live: folders by id, and whole small files
 * (chunks are 4 MB at most). [BackupFolder] (Android's folder picker) and [ServerFolder] (rclone)
 * provide it.
 */
interface BackupStore {
    val rootId: String

    /** Name → id of everything directly in [dirId]. */
    fun list(dirId: String): Map<String, String>

    fun child(dirId: String, name: String): String? = list(dirId)[name]

    /** The folder [name] in [dirId], made if it isn't there. */
    fun dir(dirId: String, name: String): String

    fun read(id: String): ByteArray

    /** Writes [name] in [dirId], replacing it if it's there. */
    fun write(dirId: String, name: String, bytes: ByteArray)

    fun delete(dirId: String, name: String)

    /** A short, readable name for the place ("Lumen Backups", "nas.local › backups"). */
    fun label(): String
}

/** Where the backup set up on this phone lives. */
sealed interface BackupLocation {
    fun open(context: Context): BackupStore

    /** A folder from Android's picker: phone storage, SD card, USB drive, or an app that offers folders. */
    data class Folder(val tree: Uri) : BackupLocation {
        override fun open(context: Context): BackupStore = BackupFolder(context, tree)
    }

    /** A server reached through rclone. */
    data class Server(val server: BackupServer) : BackupLocation {
        override fun open(context: Context): BackupStore = ServerFolder(context, server)
    }
}

/**
 * A server Lumen Backup reaches through rclone. Kept on this phone sealed by the Keystore (see
 * LumenBackup.seal); never in an rclone config file.
 *
 * [path] is the folder on the server: a path for WebDAV and SFTP, "bucket/folder" for S3,
 * "share/folder" for SMB.
 */
data class BackupServer(
    val kind: Kind,
    /** WebDAV: the full URL. S3: the endpoint (empty for Amazon). SFTP, SMB: the host name. */
    val address: String,
    val user: String,
    /** WebDAV, SFTP, SMB: the password. S3: the secret access key. */
    val password: String,
    val path: String,
    /** SFTP and SMB only; 0 = the usual port. */
    val port: Int = 0,
    /** S3 only; empty = rclone's default. */
    val region: String = "",
    /**
     * SFTP only: the server's key as a known_hosts line, kept on first connect. rclone then
     * refuses a server whose key changed (someone else answering at that address).
     */
    val hostKey: String = "",
) {
    enum class Kind(val key: String) { WebDav("webdav"), S3("s3"), Sftp("sftp"), Smb("smb") }

    fun toJson(): JSONObject = JSONObject().put("kind", kind.key).put("address", address).put("user", user)
        .put("password", password).put("path", path).put("port", port).put("region", region).put("hostKey", hostKey)

    /**
     * rclone's on-the-fly remote for it (`:webdav,url='…',…:path`), with the password obscured.
     * [knownHosts]: a file holding [hostKey], for SFTP.
     */
    fun remote(knownHosts: String? = null): String {
        val opts = LinkedHashMap<String, String>()
        when (kind) {
            Kind.WebDav -> {
                opts["url"] = address
                // Nextcloud and ownCloud take uploads faster (and set modified times) as themselves.
                opts["vendor"] = when {
                    address.contains("/remote.php/dav") || address.contains("/remote.php/webdav") -> "nextcloud"
                    else -> "other"
                }
                if (user.isNotEmpty()) opts["user"] = user
                if (password.isNotEmpty()) opts["pass"] = Rclone.obscure(password)
            }
            Kind.S3 -> {
                opts["provider"] = if (address.isBlank()) "AWS" else "Other"
                if (address.isNotBlank()) opts["endpoint"] = address
                if (region.isNotBlank()) opts["region"] = region
                opts["access_key_id"] = user
                opts["secret_access_key"] = password
                // Buckets are made in the provider's console; checking or creating one needs
                // rights a backup-only key often doesn't have.
                opts["no_check_bucket"] = "true"
            }
            Kind.Sftp -> {
                opts["host"] = address
                if (port > 0) opts["port"] = port.toString()
                opts["user"] = user
                if (password.isNotEmpty()) opts["pass"] = Rclone.obscure(password)
                if (knownHosts != null && hostKey.isNotEmpty()) opts["known_hosts_file"] = knownHosts
            }
            Kind.Smb -> {
                opts["host"] = address
                if (port > 0) opts["port"] = port.toString()
                opts["user"] = user.ifEmpty { "guest" }
                if (password.isNotEmpty()) opts["pass"] = Rclone.obscure(password)
            }
        }
        val params = opts.entries.joinToString("") { (k, v) -> ",$k='${v.replace("'", "''")}'" }
        return ":${kind.key}$params:${path.trim('/')}"
    }

    /** "nas.local › backups", "s3.eu-central-003.backblazeb2.com › photos/lumen". */
    fun label(): String {
        val host = when (kind) {
            Kind.WebDav -> runCatching { URI(address).host }.getOrNull() ?: address
            Kind.S3 -> address.ifBlank { "Amazon S3" }.removePrefix("https://").removePrefix("http://")
            Kind.Sftp, Kind.Smb -> address
        }
        return listOf(host, path.trim('/')).filter { it.isNotEmpty() }.joinToString(" › ")
    }

    companion object {
        fun parse(json: JSONObject) = BackupServer(
            Kind.entries.first { it.key == json.getString("kind") },
            json.getString("address"), json.getString("user"), json.getString("password"),
            json.getString("path"), json.optInt("port"), json.optString("region"), json.optString("hostKey"),
        )
    }
}

/**
 * A server folder through rclone. Ids are paths from the server folder ("Lumen Backup/data/ab").
 * Files go up and come down through a scratch folder in Lumen's cache (rclone copies files, not
 * bytes). Listings are cached, like [BackupFolder]'s.
 */
class ServerFolder(private val context: Context, private val server: BackupServer) : BackupStore {
    private val fs = server.remote(
        server.hostKey.takeIf { it.isNotEmpty() }?.let { line ->
            File(context.noBackupFilesDir, "backup-known-hosts").apply { writeText(line + "\n") }.path
        },
    )

    /** [Rclone.call], with a changed SFTP server key said plainly. */
    private fun call(method: String, input: JSONObject): JSONObject = try {
        Rclone.call(method, input)
    } catch (e: IOException) {
        if (e.message.orEmpty().contains("key mismatch")) throw IOException(context.getString(com.subhashh.lumen.R.string.backup_server_key_changed), e)
        throw e
    }
    private val scratch = File(context.cacheDir, "rclone-io").apply { mkdirs() }
    // Read and added to from several uploads at once.
    private val listings = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, String>>()

    override val rootId = ""

    private fun path(dirId: String, name: String) = if (dirId.isEmpty()) name else "$dirId/$name"

    override fun list(dirId: String): Map<String, String> = listings.getOrPut(dirId) {
        val out = java.util.concurrent.ConcurrentHashMap<String, String>()
        val answer = try {
            call("operations/list", JSONObject().put("fs", fs).put("remote", dirId))
        } catch (e: IOException) {
            // A folder not made yet (S3 has no empty folders at all) lists as empty.
            if (e.message.orEmpty().contains("not found", ignoreCase = true)) return@getOrPut out
            throw e
        }
        val items = answer.optJSONArray("list")
        for (i in 0 until (items?.length() ?: 0)) {
            val item = items!!.getJSONObject(i)
            out[item.getString("Name")] = item.getString("Path")
        }
        out
    }

    override fun dir(dirId: String, name: String): String = child(dirId, name) ?: path(dirId, name).also { p ->
        call("operations/mkdir", JSONObject().put("fs", fs).put("remote", p))
        list(dirId)
        listings[dirId]?.put(name, p)
        listings[p] = java.util.concurrent.ConcurrentHashMap()
    }

    override fun read(id: String): ByteArray {
        val local = File.createTempFile("download", null, scratch)
        try {
            call(
                "operations/copyfile",
                JSONObject().put("srcFs", fs).put("srcRemote", id).put("dstFs", scratch.path).put("dstRemote", local.name),
            )
            return local.readBytes()
        } finally {
            local.delete()
        }
    }

    override fun write(dirId: String, name: String, bytes: ByteArray) {
        val local = File.createTempFile("upload", null, scratch)
        try {
            local.writeBytes(bytes)
            val p = path(dirId, name)
            call(
                "operations/copyfile",
                JSONObject().put("srcFs", scratch.path).put("srcRemote", local.name).put("dstFs", fs).put("dstRemote", p),
            )
            list(dirId)
            listings[dirId]?.put(name, p)
        } finally {
            local.delete()
        }
    }

    override fun delete(dirId: String, name: String) {
        val p = child(dirId, name) ?: return
        call("operations/deletefile", JSONObject().put("fs", fs).put("remote", p))
        listings[dirId]?.remove(name)
    }

    override fun label() = server.label()

    companion object {
        /** An SFTP server's key as (known_hosts line, SHA-256 fingerprint), read without logging in. */
        fun hostKey(host: String, port: Int): Pair<String, String> {
            val answer = Rclone.call("lumen/hostkey", JSONObject().put("host", host).put("port", if (port > 0) port else 22))
            return answer.getString("line") to answer.getString("fingerprint")
        }
    }

    /** Reaches the server and its folder (made if missing); rclone's error otherwise. */
    fun check() {
        if (server.path.isNotBlank()) call("operations/mkdir", JSONObject().put("fs", fs).put("remote", ""))
        call("operations/list", JSONObject().put("fs", fs).put("remote", ""))
    }
}
