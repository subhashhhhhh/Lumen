package com.subhashh.lumen.data.vault

import android.content.Context
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.MediaItem
import java.io.File

/** Turning Encrypt Hidden on (everything hidden goes into the vault) and off (it all comes back out). */
object VaultMigration {

    data class Result(val moved: Int, val failed: Int)

    /** Where a move is: files and bytes done of the total, and the file being worked on now. */
    data class Progress(val done: Int, val total: Int, val bytesDone: Long, val bytesTotal: Long, val current: String)

    /**
     * Encrypts every Fossify-style hidden file into the vault, which must be open. Each file is
     * verified before its original is deleted, so a failure leaves that file where it was.
     * Excluded folders are left alone: they're often other apps' files, which must stay put.
     */
    suspend fun encryptAll(context: Context, excluded: Set<String>, progress: (Progress) -> Unit): Result {
        val items = HiddenMedia.withSizes(HiddenMedia.scan().filterNot { ExcludedFolders.covers(excluded, it) })
        val sizes = items.map { HiddenMedia.file(it)?.length() ?: 0L }
        val bytesTotal = sizes.sum()
        var bytesDone = 0L
        var moved = 0
        var failed = 0
        items.forEachIndexed { i, item ->
            val file = HiddenMedia.file(item)
            progress(Progress(i, items.size, bytesDone, bytesTotal, file?.name?.trimStart('.') ?: item.name))
            val ok = file != null && Vault.add(context, file, Vault.Details.of(item, file, visible = false))
            if (ok) moved++ else failed++
            bytesDone += sizes[i]
        }
        progress(Progress(items.size, items.size, bytesTotal, bytesTotal, ""))
        return Result(moved, failed)
    }

    /** Decrypts everything back to where it was, still hidden the Fossify way. */
    suspend fun decryptAll(context: Context, progress: (Progress) -> Unit): Result {
        val items = Vault.list()
        val bytesTotal = items.sumOf { it.size }
        var bytesDone = 0L
        var moved = 0
        var failed = 0
        items.forEachIndexed { i, item ->
            progress(Progress(i, items.size, bytesDone, bytesTotal, item.name))
            if (Vault.restore(context, item, hidden = true) != null) moved++ else failed++
            bytesDone += item.size
        }
        progress(Progress(items.size, items.size, bytesTotal, bytesTotal, ""))
        return Result(moved, failed)
    }

    /** Hides visible photos straight into the vault (Encrypt Hidden on). Returns how many went in. */
    suspend fun hideIntoVault(context: Context, items: List<MediaItem>, path: (MediaItem) -> String?): Int =
        items.count { item ->
            val file = path(item)?.let { File(it) } ?: return@count false
            Vault.add(context, file, Vault.Details.of(item, file, visible = true))
        }
}
