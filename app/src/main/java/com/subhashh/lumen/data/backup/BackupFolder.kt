package com.subhashh.lumen.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * A folder the person picked (Android's folder picker: phone storage, an SD card or USB drive, or
 * any app that offers folders there, such as Google Drive, OneDrive, Nextcloud or an SMB/S3 app),
 * reached through DocumentsContract. Lumen writes plain files into it and nothing else, which is
 * all any of those support. Lookups are cached per folder: some providers answer slowly.
 */
class BackupFolder(private val context: Context, private val tree: Uri) : BackupStore {
    private val resolver = context.contentResolver
    // Read and added to from several uploads at once.
    private val listings = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, String>>()

    override val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    /** Name → document id of everything directly in [dirId]. */
    override fun list(dirId: String): Map<String, String> = listings.getOrPut(dirId) {
        val out = java.util.concurrent.ConcurrentHashMap<String, String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getString(1)] = c.getString(0)
        }
        out
    }

    override fun child(dirId: String, name: String): String? = list(dirId)[name]

    /** The folder [name] in [dirId], made if it isn't there. */
    // Locked: two uploads making the same folder at once would leave "ab" and "ab (1)" (two "ab"
    // folders on Google Drive), and chunks in the second wouldn't be found again.
    @Synchronized
    override fun dir(dirId: String, name: String): String = child(dirId, name) ?: create(dirId, name, DocumentsContract.Document.MIME_TYPE_DIR)

    override fun read(docId: String): ByteArray =
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, docId))?.use { it.readBytes() }
            ?: throw IOException("Couldn't read the backup folder")

    /** Writes [name] in [dirId], replacing it if it's there. */
    override fun write(dirId: String, name: String, bytes: ByteArray) {
        val id = synchronized(this) { child(dirId, name) ?: create(dirId, name, "application/octet-stream") }
        resolver.openOutputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id), "wt")?.use { it.write(bytes) }
            ?: throw IOException("Couldn't write to the backup folder")
    }

    override fun delete(dirId: String, name: String) {
        val id = child(dirId, name) ?: return
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        listings[dirId]?.remove(name)
    }

    private fun create(dirId: String, name: String, mime: String): String {
        val uri = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, dirId), mime, name)
            ?: throw IOException("Couldn't create $name in the backup folder")
        val id = DocumentsContract.getDocumentId(uri)
        list(dirId) // make sure the listing is loaded before adding to it
        listings[dirId]?.put(name, id)
        return id
    }

    /** A short, readable name for the picked folder ("Lumen Backups", "Google Drive › Backups"). */
    override fun label(): String = runCatching {
        resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment.orEmpty()
}
