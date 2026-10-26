package com.subhashh.lumen.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.subhashh.lumen.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from Lumen's GitHub releases: the latest release's APK, downloaded on request and
 * installed through Android's own installer (which asks first). Only an APK signed with the same
 * key as this install is accepted; Android would refuse anything else anyway, after the download.
 *
 * Play and F-Droid builds must not update themselves (both stores forbid it): leave this out of
 * those builds when distribution is set up.
 */
object Updater {
    const val RepoUrl = "https://github.com/subhashhhhhh/Lumen"
    private const val LatestApi = "https://api.github.com/repos/subhashhhhhh/Lumen/releases/latest"

    class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkBytes: Long,
        val pageUrl: String,
        /** The APK's SHA-256 (hex) as GitHub reports it; the download must match. Null if GitHub gave none. */
        val apkSha256: String? = null,
    )

    sealed interface Check {
        data object UpToDate : Check
        /** The repository has no releases yet (or isn't public yet). */
        data object NoReleases : Check
        class Available(val release: Release) : Check
        class Failed(val message: String) : Check
    }

    /** Asks GitHub for the latest release. Nothing about the phone or its photos is sent. */
    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LatestApi).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                val code = connection.responseCode
                if (code == 200 || code == 404) LumenPrefs(context).lastUpdateCheck = System.currentTimeMillis()
                when (code) {
                    404 -> return@runCatching Check.NoReleases
                    200 -> Unit
                    else -> return@runCatching Check.Failed("GitHub answered ${connection.responseCode}")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val version = json.getString("tag_name").removePrefix("v")
                val assets = json.optJSONArray("assets")
                val apks = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                    .filter { it.getString("name").endsWith(".apk", ignoreCase = true) }
                // The APK for this phone's processor ("Lumen-0.2.0-arm64-v8a.apk"), else the
                // universal one, else whatever APK there is.
                val apk = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.firstOrNull { "-$abi." in it.getString("name") } }
                    ?: apks.firstOrNull { "universal" in it.getString("name") }
                    ?: apks.firstOrNull()
                    ?: return@runCatching Check.Failed("The latest release has no APK")
                if (!isNewer(version, installedVersion(context))) return@runCatching Check.UpToDate
                Check.Available(
                    Release(
                        version = version,
                        notes = json.optString("body").trim(),
                        apkUrl = apk.getString("browser_download_url"),
                        apkBytes = apk.optLong("size"),
                        pageUrl = json.optString("html_url", RepoUrl),
                        apkSha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                    ),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrElse { Check.Failed("Couldn't reach GitHub") }
    }

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** "1.10.0" is newer than "1.9.2"; a pre-release suffix ("-beta") counts as older than none. */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return '-' in installed && '-' !in candidate
    }

    /** Downloads the release's APK into the cache, reporting 0..1. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "Lumen-${release.version}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkBytes
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        // A cut-off or altered download stops here, before the installer sees it.
        if (release.apkSha256 != null && sha256(file) != release.apkSha256) {
            file.delete()
            throw java.io.IOException("The download doesn't match the release's checksum")
        }
        file
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** True when [apk] is Lumen, signed with the same key as this install. */
    fun signedLikeUs(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val theirs = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (theirs.packageName != context.packageName) return false
        val ours = pm.getPackageInfo(context.packageName, flags)
        // Each signing certificate by its SHA-256, the same fingerprint the release notes publish.
        fun fingerprints(signers: Array<android.content.pm.Signature>?) =
            signers?.map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }?.toSet()
        val a = fingerprints(ours.signingInfo?.apkContentsSigners) ?: return false
        val b = fingerprints(theirs.signingInfo?.apkContentsSigners) ?: return false
        return a.isNotEmpty() && a == b
    }

    /** Whether Android lets Lumen install apps (Settings → Install unknown apps). */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Hands [apk] to Android's installer, which shows its own confirmation. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("lumen.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, UpdateReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}

/** The installer's answer: asks the user to confirm when it needs to. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: context.getString(R.string.data_update_install_failed)
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
