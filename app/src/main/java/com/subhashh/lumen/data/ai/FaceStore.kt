package com.subhashh.lumen.data.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One face in one photo: where (fractions of the photo's width and height), its fingerprint, whose. */
class Face(
    val id: Long,
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val vector: FloatArray,
    val person: Long?,
)

/**
 * Faces found in photos and the people they're grouped into. Same lifecycle as [EmbeddingStore]:
 * app-private, not backed up. People carry what the user decides (names, merges, hidden), so
 * grouping only ever adds faces to people; it never moves a face that already has one.
 */
class FaceStore private constructor(context: Context) : SQLiteOpenHelper(context, file(context).path, null, 1) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    /** Bumped on every write, so screens know to reload. */
    @Volatile
    var version = 0
        private set

    override fun onCreate(db: SQLiteDatabase) {
        // A photo with no faces keeps its row in items, so it isn't looked at again.
        db.execSQL("CREATE TABLE items (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE faces (id INTEGER PRIMARY KEY, media_id INTEGER NOT NULL, " +
                "l REAL NOT NULL, t REAL NOT NULL, r REAL NOT NULL, b REAL NOT NULL, vec BLOB NOT NULL, person INTEGER)",
        )
        db.execSQL("CREATE INDEX faces_media ON faces (media_id)")
        db.execSQL("CREATE INDEX faces_person ON faces (person)")
        db.execSQL("CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT, hidden INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Item id → the `modified` it was looked at. */
    fun indexed(): Map<Long, Long> = HashMap<Long, Long>().apply {
        readableDatabase.rawQuery("SELECT media_id, modified FROM items", null).use { c ->
            while (c.moveToNext()) put(c.getLong(0), c.getLong(1))
        }
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM items", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    fun peopleCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM people", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    /** Replaces [mediaId]'s faces (an edited photo is looked at again). Boxes are fractions. */
    fun put(mediaId: Long, modified: Long, faces: List<Pair<FloatArray, FloatArray>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("faces", "media_id = ?", arrayOf(mediaId.toString()))
            for ((box, vector) in faces) {
                db.insert("faces", null, ContentValues().apply {
                    put("media_id", mediaId)
                    put("l", box[0]); put("t", box[1]); put("r", box[2]); put("b", box[3])
                    put("vec", toBytes(vector))
                })
            }
            db.insertWithOnConflict("items", null, ContentValues().apply {
                put("media_id", mediaId)
                put("modified", modified)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        version++
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete("faces", "media_id = ?", arrayOf(id.toString()))
                db.delete("items", "media_id = ?", arrayOf(id.toString()))
            }
            // People whose last face went with those photos.
            db.execSQL("DELETE FROM people WHERE id NOT IN (SELECT DISTINCT person FROM faces WHERE person IS NOT NULL)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        version++
    }

    /** Every face, newest row last. */
    fun faces(): List<Face> = ArrayList<Face>().apply {
        readableDatabase.rawQuery("SELECT id, media_id, l, t, r, b, vec, person FROM faces ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val blob = c.getBlob(6)
                val vector = FloatArray(blob.size / 4).also {
                    ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
                }
                add(
                    Face(
                        c.getLong(0), c.getLong(1), c.getFloat(2), c.getFloat(3), c.getFloat(4), c.getFloat(5),
                        vector, if (c.isNull(7)) null else c.getLong(7),
                    ),
                )
            }
        }
    }

    /** Saves grouping: faces joining existing people, and new people with their first faces. */
    fun assign(joins: Map<Long, Long>, newPeople: List<List<Long>>) {
        if (joins.isEmpty() && newPeople.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((face, person) in joins) db.execSQL("UPDATE faces SET person = ? WHERE id = ?", arrayOf(person, face))
            for (faces in newPeople) {
                val person = db.insert("people", null, ContentValues().apply { put("hidden", 0) })
                for (face in faces) db.execSQL("UPDATE faces SET person = ? WHERE id = ?", arrayOf(person, face))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        version++
    }

    companion object {
        @Volatile
        private var open: FaceStore? = null

        /** One open helper for the whole process (it's thread-safe). */
        @Synchronized
        fun of(context: Context): FaceStore = open ?: FaceStore(context.applicationContext).also { open = it }

        @Synchronized
        fun delete(context: Context) {
            open?.close()
            open = null
            SQLiteDatabase.deleteDatabase(file(context))
        }

        private fun file(context: Context) =
            File(context.noBackupFilesDir, "search/faces.db").apply { parentFile?.mkdirs() }

        private fun toBytes(vector: FloatArray): ByteArray =
            ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(vector) }.array()
    }
}
