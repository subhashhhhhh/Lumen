package com.subhashh.lumen.data.backup

import org.json.JSONObject
import java.io.IOException

/**
 * rclone, built with only the backends Lumen Backup offers (WebDAV, S3, SFTP, SMB and local), as
 * `librclone.so`. Its Go side (`lumen/main.go` in the rclone build on oracle) exports the two JNI
 * functions below under this exact class name: rename both together.
 *
 * Calls are rclone's remote-control commands (https://rclone.org/rc/), as JSON. No rclone config
 * file: every call names its remote as a connection string (`:webdav,url='…':path`).
 */
object Rclone {
    private val ready: Boolean by lazy {
        runCatching { System.loadLibrary("rclone"); nativeInit(); true }.getOrDefault(false)
    }

    /** Whether this build carries rclone (not on 32-bit x86, say). */
    val available: Boolean get() = ready

    /**
     * Runs rclone command [method] with [input]; its JSON answer, or an [IOException] with
     * rclone's error. Safe from several threads at once (rclone's RPC is).
     */
    fun call(method: String, input: JSONObject = JSONObject()): JSONObject {
        if (!ready) throw IOException("rclone isn't in this build")
        val raw = try {
            nativeRpc(method.toByteArray(), input.toString().toByteArray())
        } catch (e: UnsatisfiedLinkError) {
            throw IOException("rclone isn't in this build", e)
        }
        val text = String(raw)
        val status = text.take(3).toIntOrNull() ?: 500
        val output = runCatching { JSONObject(text.drop(3)) }.getOrDefault(JSONObject())
        if (status != 200) throw IOException(output.optString("error").ifEmpty { "rclone: $status" })
        return output
    }

    /** [password] in rclone's reversible "obscured" form, which its backends expect. */
    fun obscure(password: String): String = call("core/obscure", JSONObject().put("clear", password)).getString("obscured")

    private external fun nativeInit()
    private external fun nativeRpc(method: ByteArray, input: ByteArray): ByteArray
}
