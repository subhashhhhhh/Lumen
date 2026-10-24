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
    /** The person the user said this isn't (Not This Person): grouping never puts it back there. */
    val notPerson: Long?,
)

/**
 * A person as the People row and their album show them: name (null until named), the photos
 * they're in, and the face to show for them (their biggest, so the sharpest).
 */
@androidx.compose.runtime.Immutable
data class Person(
    val id: Long,
    val name: String?,
    val hidden: Boolean,
    val mediaIds: Set<Long>,
    val faceCount: Int,
    val coverMediaId: Long,
    /** The cover face's box, fractions of its photo: left, top, right, bottom. */
    val coverBox: List<Float>,
    val coverFaceId: Long,
    /** The cover is a video's face: shown from its saved crop ([FaceStore.crop]), not the video's thumbnail. */
    val coverInVideo: Boolean,
)

/** A face to save: box as fractions, fingerprint, and for a video the moment it's from. */
class NewFace(val box: FloatArray, val vector: FloatArray, val atMs: Long? = null)

/**
 * Faces found in photos and the people they're grouped into. Same lifecycle as [EmbeddingStore]:
 * app-private, not backed up. People carry what the user decides (names, merges, hidden), so
 * grouping only ever adds faces to people; it never moves a face that already has one.
 */
class FaceStore private constructor(context: Context) : SQLiteOpenHelper(context, file(context).path, null, 3) {
    private val crops = cropDir(context)

    init {
        setWriteAheadLoggingEnabled(true)
    }

    /** Bumped on every write, so screens know to reload. */
    var version: Int
        get() = changes.value
        private set(value) {
            changes.value = value
        }

    override fun onCreate(db: SQLiteDatabase) {
        // A photo with no faces keeps its row in items, so it isn't looked at again.
        db.execSQL("CREATE TABLE items (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE faces (id INTEGER PRIMARY KEY, media_id INTEGER NOT NULL, " +
                "l REAL NOT NULL, t REAL NOT NULL, r REAL NOT NULL, b REAL NOT NULL, vec BLOB NOT NULL, person INTEGER, " +
                "not_person INTEGER, at_ms INTEGER)",
        )
        db.execSQL("CREATE INDEX faces_media ON faces (media_id)")
        db.execSQL("CREATE INDEX faces_person ON faces (person)")
        db.execSQL("CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT, hidden INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 2: Not This Person.
        if (oldVersion < 2) db.execSQL("ALTER TABLE faces ADD COLUMN not_person INTEGER")
        // 3: faces in videos, with the moment (null for photos).
        if (oldVersion < 3) db.execSQL("ALTER TABLE faces ADD COLUMN at_ms INTEGER")
    }

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

    /** Replaces [mediaId]'s faces (an edited photo is looked at again); returns the new faces' ids. */
    fun put(mediaId: Long, modified: Long, faces: List<NewFace>): List<Long> {
        val db = writableDatabase
        val ids = ArrayList<Long>()
        db.beginTransaction()
        try {
            dropCrops(db, "media_id = ?", arrayOf(mediaId.toString()))
            db.delete("faces", "media_id = ?", arrayOf(mediaId.toString()))
            for (face in faces) {
                val box = face.box
                ids += db.insert("faces", null, ContentValues().apply {
                    put("media_id", mediaId)
                    put("l", box[0]); put("t", box[1]); put("r", box[2]); put("b", box[3])
                    put("vec", toBytes(face.vector))
                    face.atMs?.let { put("at_ms", it) }
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
        return ids
    }

    /** Where a video face's crop is saved (photos' faces are cut from the photo when shown). */
    fun crop(faceId: Long) = File(crops, "$faceId.jpg")

    private fun dropCrops(db: SQLiteDatabase, where: String, args: Array<String>) {
        db.rawQuery("SELECT id FROM faces WHERE at_ms IS NOT NULL AND $where", args).use { c ->
            while (c.moveToNext()) crop(c.getLong(0)).delete()
        }
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                dropCrops(db, "media_id = ?", arrayOf(id.toString()))
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
        readableDatabase.rawQuery("SELECT id, media_id, l, t, r, b, vec, person, not_person FROM faces ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val blob = c.getBlob(6)
                val vector = FloatArray(blob.size / 4).also {
                    ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
                }
                add(
                    Face(
                        c.getLong(0), c.getLong(1), c.getFloat(2), c.getFloat(3), c.getFloat(4), c.getFloat(5),
                        vector, if (c.isNull(7)) null else c.getLong(7), if (c.isNull(8)) null else c.getLong(8),
                    ),
                )
            }
        }
    }

    /** Every person with at least one face, most photos first. */
    fun people(): List<Person> {
        val names = HashMap<Long, Pair<String?, Boolean>>()
        readableDatabase.rawQuery("SELECT id, name, hidden FROM people", null).use { c ->
            while (c.moveToNext()) names[c.getLong(0)] = (if (c.isNull(1)) null else c.getString(1)) to (c.getInt(2) != 0)
        }
        class Acc(val media: HashSet<Long> = HashSet(), var faces: Int = 0) {
            var cover = 0L
            var face = 0L
            var video = false
            var box: List<Float> = emptyList()
            var score = -1f
        }
        val acc = HashMap<Long, Acc>()
        readableDatabase.rawQuery("SELECT person, media_id, l, t, r, b, id, at_ms FROM faces WHERE person IS NOT NULL", null).use { c ->
            while (c.moveToNext()) {
                val a = acc.getOrPut(c.getLong(0)) { Acc() }
                a.media += c.getLong(1)
                a.faces++
                val box = listOf(c.getFloat(2), c.getFloat(3), c.getFloat(4), c.getFloat(5))
                val video = !c.isNull(7)
                // The biggest face, from a photo when there is one (sharper than a video frame).
                val score = (box[2] - box[0]) * (box[3] - box[1]) + if (video) 0f else 1f
                if (score > a.score) {
                    a.score = score
                    a.cover = c.getLong(1)
                    a.face = c.getLong(6)
                    a.video = video
                    a.box = box
                }
            }
        }
        return acc.mapNotNull { (id, a) ->
            val (name, hidden) = names[id] ?: return@mapNotNull null
            Person(id, name, hidden, a.media, a.faces, a.cover, a.box, a.face, a.video)
        }.sortedByDescending { it.mediaIds.size }
    }

    /** Names a person; blank clears the name. */
    fun rename(person: Long, name: String) {
        writableDatabase.execSQL("UPDATE people SET name = ? WHERE id = ?", arrayOf<Any?>(name.trim().ifEmpty { null }, person))
        version++
    }

    /**
     * Makes [gone] part of [keep]: its faces, and its name if [keep] has none. Faces told they
     * aren't [gone] aren't [keep] either.
     */
    fun merge(keep: Long, gone: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE faces SET person = ? WHERE person = ?", arrayOf(keep, gone))
            db.execSQL("UPDATE faces SET not_person = ? WHERE not_person = ?", arrayOf(keep, gone))
            db.execSQL(
                "UPDATE people SET name = (SELECT name FROM people WHERE id = ?) WHERE id = ? AND name IS NULL",
                arrayOf(gone, keep),
            )
            db.delete("people", "id = ?", arrayOf(gone.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        version++
    }

    /** Not This Person: [person]'s faces in these photos leave them for good; an emptied person goes. */
    fun notThisPerson(person: Long, mediaIds: Collection<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in mediaIds) {
                db.execSQL(
                    "UPDATE faces SET person = NULL, not_person = ? WHERE person = ? AND media_id = ?",
                    arrayOf(person, person, id),
                )
            }
            db.execSQL("DELETE FROM people WHERE id = ? AND NOT EXISTS (SELECT 1 FROM faces WHERE person = ?)", arrayOf(person, person))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        version++
    }

    fun setHidden(person: Long, hidden: Boolean) {
        writableDatabase.execSQL("UPDATE people SET hidden = ? WHERE id = ?", arrayOf(if (hidden) 1 else 0, person))
        version++
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
        /** [version] as a flow: People in Collections reloads when the face pass adds people. */
        val changes = kotlinx.coroutines.flow.MutableStateFlow(0)

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
            cropDir(context).deleteRecursively()
        }

        private fun cropDir(context: Context) = File(context.noBackupFilesDir, "faces").apply { mkdirs() }

        /** [crop] without opening the database, for showing a cover. */
        fun cropFile(context: Context, faceId: Long) = File(cropDir(context), "$faceId.jpg")

        private fun file(context: Context) =
            File(context.noBackupFilesDir, "search/faces.db").apply { parentFile?.mkdirs() }

        private fun toBytes(vector: FloatArray): ByteArray =
            ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(vector) }.array()
    }
}
