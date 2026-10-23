package com.subhashh.lumen.data.vault

import android.content.Context
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.MediaItem
import java.io.File

/** Turning Encrypt Hidden on (everything hidden goes into the vault) and off (it all comes back out). */
object VaultMigration {

    data class Result(val moved: Int, val failed: Int)

    /**
     * Encrypts every Fossify-style hidden file into the vault, which must be open. Each file is
     * verified before its original is deleted, so a failure leaves that file where it was.
     * Excluded folders are left alone: they're often other apps' files, which must stay put.
     */
    suspend fun encryptAll(context: Context, excluded: Set<String>, progress: (done: Int, total: Int) -> Unit): Result {
        val items = HiddenMedia.withSizes(HiddenMedia.scan().filterNot { ExcludedFolders.covers(excluded, it) })
        var moved = 0
        var failed = 0
        items.forEachIndexed { i, item ->
            progress(i, items.size)
            val file = HiddenMedia.file(item)
            val ok = file != null && Vault.add(context, file, Vault.Details.of(item, file, visible = false))
            if (ok) moved++ else failed++
        }
        progress(items.size, items.size)
        return Result(moved, failed)
    }

    /** Decrypts everything back to where it was, still hidden the Fossify way. */
    suspend fun decryptAll(context: Context, progress: (done: Int, total: Int) -> Unit): Result {
        val items = Vault.list()
        var moved = 0
        var failed = 0
        items.forEachIndexed { i, item ->
            progress(i, items.size)
            if (Vault.restore(context, item, hidden = true) != null) moved++ else failed++
        }
        progress(items.size, items.size)
        return Result(moved, failed)
    }

    /** Hides visible photos straight into the vault (Encrypt Hidden on). Returns how many went in. */
    suspend fun hideIntoVault(context: Context, items: List<MediaItem>, path: (MediaItem) -> String?): Int =
        items.count { item ->
            val file = path(item)?.let { File(it) } ?: return@count false
            Vault.add(context, file, Vault.Details.of(item, file, visible = true))
        }
}
