package com.subhashh.lumen.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

/**
 * Captions the user writes for photos and videos (Details → Add a Caption), searchable. Kept in
 * Lumen's own storage, not the files: writing EXIF would ask permission for every photo, and
 * videos have nowhere standard to keep one.
 */
class CaptionStore private constructor(context: Context) : SQLiteOpenHelper(context, "captions.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE captions (media_id INTEGER PRIMARY KEY, text TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun get(mediaId: Long): String? =
        readableDatabase.rawQuery("SELECT text FROM captions WHERE media_id = ?", arrayOf(mediaId.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Sets [mediaId]'s caption; blank removes it. */
    fun set(mediaId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            writableDatabase.delete("captions", "media_id = ?", arrayOf(mediaId.toString()))
        } else {
            writableDatabase.insertWithOnConflict(
                "captions",
                null,
                ContentValues().apply {
                    put("media_id", mediaId)
                    put("text", trimmed)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    /** Items whose caption has every word of [query] in it. A small table: a plain scan. */
    fun search(query: String): List<Long> {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return readableDatabase.rawQuery("SELECT media_id, text FROM captions", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val text = c.getString(1).lowercase(Locale.ROOT)
                    if (words.all { it in text }) add(c.getLong(0))
                }
            }
        }
    }

    companion object {
        @Volatile private var open: CaptionStore? = null

        fun of(context: Context): CaptionStore =
            open ?: synchronized(this) { open ?: CaptionStore(context.applicationContext).also { open = it } }
    }
}
