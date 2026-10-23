package com.subhashh.lumen.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Why a download stopped; [retry] means trying again later may work (network, server). */
class DownloadException(message: String, val retry: Boolean) : IOException(message)

/**
 * Fetches a model's files into its folder. Each file downloads to `<name>.part`, resuming with an
 * HTTP Range request after any interruption, then must match its SHA-256 before it's renamed into
 * place. Files already in place are skipped, so a rerun only fetches what's missing.
 */
class ModelDownloader(
    private val files: List<ModelFile>,
    private val url: (ModelFile) -> String,
    private val dir: File,
    private val token: String?,
) {

    /** [base] and [total] place these files' progress within a larger download. */
    suspend fun run(base: Long = 0, total: Long = files.sumOf { it.bytes }, onProgress: (done: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        var finished = base
        for (file in files) {
            val dest = File(dir, file.name)
            if (dest.length() != file.bytes) {
                val part = File(dir, file.name + ".part")
                part.parentFile?.mkdirs()
                val base = finished
                fetch(url(file), part, file.bytes) { got -> onProgress(base + got, total) }
                if (sha256(part) != file.sha256) {
                    part.delete()
                    throw DownloadException("${file.name} didn't match its checksum", retry = true)
                }
                if (!part.renameTo(dest)) throw DownloadException("Couldn't save ${file.name}", retry = false)
            }
            finished += file.bytes
            onProgress(finished, total)
        }
    }

    private suspend fun fetch(url: String, part: File, expected: Long, onBytes: (Long) -> Unit) {
        var have = part.length().takeIf { it <= expected } ?: run { part.delete(); 0L }
        if (have == expected) return
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Lumen")
            // Android's HttpURLConnection drops this on the redirect to Hugging Face's CDN host.
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw DownloadException("No connection to Hugging Face", retry = true)
            }
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> Unit
                code == HttpURLConnection.HTTP_OK -> have = 0 // Server ignored Range: start over.
                code == 401 || code == 403 -> throw DownloadException("Hugging Face refused the token", retry = false)
                code == 404 -> throw DownloadException("Model files not found", retry = false)
                code == 416 -> {
                    part.delete()
                    throw DownloadException("Download got out of step; starting over", retry = true)
                }
                else -> throw DownloadException("Hugging Face answered $code", retry = code >= 500 || code == 429)
            }
            connection.inputStream.use { input ->
                FileOutputStream(part, have > 0).use { output ->
                    val buffer = ByteArray(1 shl 18)
                    var lastReport = 0L
                    try {
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            have += n
                            if (have - lastReport >= ReportEvery) {
                                lastReport = have
                                onBytes(have)
                            }
                        }
                    } catch (e: IOException) {
                        throw DownloadException("Connection lost", retry = true)
                    }
                }
            }
            onBytes(have)
            if (have != expected) throw DownloadException("Download ended early", retry = true)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ReportEvery = 1L shl 20

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 18)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
