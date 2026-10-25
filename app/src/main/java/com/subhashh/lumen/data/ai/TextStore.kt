package com.subhashh.lumen.data.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * The words read from each photo, in a full-text index (SQLite FTS4, unicode61: case- and
 * accent-insensitive), so a search for a name or a phrase finds every screenshot containing it.
 * Same lifecycle as [EmbeddingStore]: app-private, not backed up, rebuilt if lost.
 */
class TextStore private constructor(context: Context) : SQLiteOpenHelper(context, file(context).path, null, 2) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // An item with no text keeps its row in items (so it isn't read again) and none in texts.
        db.execSQL("CREATE TABLE items (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL)")
        db.execSQL("CREATE VIRTUAL TABLE texts USING fts4(body, tokenize=unicode61 \"remove_diacritics=2\")")
        createLines(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createLines(db)
    }

    /**
     * Where each line of a photo's text is, for Live Text: [LiveText.encode]d lines in pixels of
     * the [width]×[height] image they were read from. Photos only, at the `modified` they were
     * read at; added in version 2, so photos read before it have words but no lines.
     */
    private fun createLines(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS lines (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, data BLOB NOT NULL)")
    }

    class StoredLines(val width: Int, val height: Int, val data: ByteArray)

    fun putLines(mediaId: Long, modified: Long, width: Int, height: Int, data: ByteArray) {
        writableDatabase.insertWithOnConflict("lines", null, ContentValues().apply {
            put("media_id", mediaId)
            put("modified", modified)
            put("width", width)
            put("height", height)
            put("data", data)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** [mediaId]'s lines as read at [modified]; null when there are none for that version. */
    fun lines(mediaId: Long, modified: Long): StoredLines? =
        readableDatabase.rawQuery("SELECT width, height, data FROM lines WHERE media_id = ? AND modified = ?", arrayOf(mediaId.toString(), modified.toString())).use { c ->
            if (c.moveToFirst()) StoredLines(c.getInt(0), c.getInt(1), c.getBlob(2)) else null
        }

    /**
     * Adds the [lines] [mediaId]'s words don't have yet, if it's already been read at [modified]
     * (a frame read in the viewer, from a video the indexer sampled); an item not read yet is
     * left for its full read. Returns how many lines were new.
     */
    fun addLines(mediaId: Long, modified: Long, lines: List<String>): Int {
        if (known(mediaId, modified) == Known.NotRead) return 0
        val old = text(mediaId)
        val have = old.orEmpty().lines().mapTo(HashSet()) { it.trim().lowercase() }
        val new = lines.map { it.trim() }.filter { it.isNotEmpty() && have.add(it.lowercase()) }
        if (new.isEmpty()) return 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("texts", "docid = ?", arrayOf(mediaId.toString()))
            db.insert("texts", null, ContentValues().apply {
                put("docid", mediaId)
                put("body", listOfNotNull(old?.takeIf { it.isNotBlank() }, new.joinToString("\n")).joinToString("\n"))
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return new.size
    }

    /** What the index knows about [mediaId] at [modified]: not read yet, read with no words, or read with words. */
    enum class Known { NotRead, NoText, HasText }

    fun known(mediaId: Long, modified: Long): Known {
        val read = readableDatabase.rawQuery("SELECT 1 FROM items WHERE media_id = ? AND modified = ?", arrayOf(mediaId.toString(), modified.toString())).use { it.moveToFirst() }
        if (!read) return Known.NotRead
        return if (text(mediaId) != null) Known.HasText else Known.NoText
    }

    /** Photos read before lines were kept that have words: read again once, for Live Text. */
    fun withTextButNoLines(): Set<Long> = HashSet<Long>().apply {
        readableDatabase.rawQuery("SELECT docid FROM texts WHERE docid NOT IN (SELECT media_id FROM lines)", null).use { c ->
            while (c.moveToNext()) add(c.getLong(0))
        }
    }

    /** Item id → the `modified` it was read at. */
    fun indexed(): Map<Long, Long> = HashMap<Long, Long>().apply {
        readableDatabase.rawQuery("SELECT media_id, modified FROM items", null).use { c ->
            while (c.moveToNext()) put(c.getLong(0), c.getLong(1))
        }
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM items", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    /** Replaces whatever [mediaId] had; [text] is empty when the photo has no readable words. */
    fun put(mediaId: Long, modified: Long, text: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("texts", "docid = ?", arrayOf(mediaId.toString()))
            if (text.isNotBlank()) db.insert("texts", null, ContentValues().apply {
                put("docid", mediaId)
                put("body", text)
            })
            db.insertWithOnConflict("items", null, ContentValues().apply {
                put("media_id", mediaId)
                put("modified", modified)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete("texts", "docid = ?", arrayOf(id.toString()))
                db.delete("items", "media_id = ?", arrayOf(id.toString()))
                db.delete("lines", "media_id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** The words read in one item, or null if none (or not read yet). */
    fun text(mediaId: Long): String? =
        readableDatabase.rawQuery("SELECT body FROM texts WHERE docid = ?", arrayOf(mediaId.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /**
     * Items whose text has every word of [query], the last one as a prefix (it may still be
     * being typed). Empty for a query with no letters or digits.
     */
    fun search(query: String): List<Long> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val match = words.dropLast(1).joinToString(" ") + " " + words.last() + "*"
        return readableDatabase.rawQuery("SELECT docid FROM texts WHERE texts MATCH ?", arrayOf(match.trim())).use { c ->
            List(c.count) { c.moveToNext(); c.getLong(0) }
        }
    }

    /**
     * Items whose text has every phrase word for word (full-text phrase queries skip
     * punctuation: "so baby, come" matches "so baby come"); the last word is a prefix while its
     * quote is still being typed.
     */
    fun searchPhrases(p: com.subhashh.lumen.data.QueryParts.Phrases): List<Long> {
        val match = p.phrases.withIndex().joinToString(" ") { (i, words) ->
            val last = if (p.open && i == p.phrases.lastIndex) "*" else ""
            "\"" + words.joinToString(" ") + last + "\""
        }
        return readableDatabase.rawQuery("SELECT docid FROM texts WHERE texts MATCH ?", arrayOf(match)).use { c ->
            List(c.count) { c.moveToNext(); c.getLong(0) }
        }
    }

    companion object {
        @Volatile
        private var open: TextStore? = null

        /** One open helper for the whole process (it's thread-safe). */
        @Synchronized
        fun of(context: Context): TextStore = open ?: TextStore(context.applicationContext).also { open = it }

        @Synchronized
        fun delete(context: Context) {
            open?.close()
            open = null
            SQLiteDatabase.deleteDatabase(file(context))
        }

        private fun file(context: Context) =
            File(context.noBackupFilesDir, "search/text.db").apply { parentFile?.mkdirs() }
    }
}
