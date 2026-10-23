package com.subhashh.lumen.data.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Every vector in an index, flattened for a fast scan: row i is `vectors[i*dim until (i+1)*dim]`. */
class Embeddings(val mediaIds: LongArray, val atMs: LongArray, val vectors: FloatArray, val dim: Int) {
    val size get() = mediaIds.size
}

/**
 * Smart Search's index for one model: which library items are done (at which version) and their
 * vectors. A photo has one vector; a video one per kept frame, with the frame's time. One file
 * per model, in app-private storage that isn't backed up, since vectors from different models
 * can't be mixed and are cheap to rebuild.
 */
class EmbeddingStore private constructor(context: Context, private val model: AiModel) :
    SQLiteOpenHelper(context, file(context, model).path, null, 1) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    /** Bumped on every write, so a search cache knows its copy is stale. */
    @Volatile
    var version = 0
        private set

    override fun onCreate(db: SQLiteDatabase) {
        // frames = 0: tried and unreadable (corrupt, DRM); kept so it isn't retried every run.
        db.execSQL("CREATE TABLE items (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL, frames INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE vectors (media_id INTEGER NOT NULL, at_ms INTEGER NOT NULL, vec BLOB NOT NULL)")
        db.execSQL("CREATE INDEX vectors_media ON vectors (media_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Item id → the `modified` it was indexed at. */
    fun indexed(): Map<Long, Long> = HashMap<Long, Long>().apply {
        readableDatabase.rawQuery("SELECT media_id, modified FROM items", null).use { c ->
            while (c.moveToNext()) put(c.getLong(0), c.getLong(1))
        }
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM items", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    /** Replaces whatever [mediaId] had. */
    fun put(mediaId: Long, modified: Long, frames: List<Pair<Long, FloatArray>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("vectors", "media_id = ?", arrayOf(mediaId.toString()))
            for ((at, vector) in frames) {
                db.insert("vectors", null, ContentValues().apply {
                    put("media_id", mediaId)
                    put("at_ms", at)
                    put("vec", toBytes(vector))
                })
            }
            db.insertWithOnConflict("items", null, ContentValues().apply {
                put("media_id", mediaId)
                put("modified", modified)
                put("frames", frames.size)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            version++
        } finally {
            db.endTransaction()
        }
    }

    fun remove(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in mediaIds) {
                val args = arrayOf(id.toString())
                db.delete("vectors", "media_id = ?", args)
                db.delete("items", "media_id = ?", args)
            }
            db.setTransactionSuccessful()
            version++
        } finally {
            db.endTransaction()
        }
    }

    /** Everything, for searching. */
    fun loadAll(): Embeddings {
        val dim = model.spec.dim
        readableDatabase.rawQuery("SELECT media_id, at_ms, vec FROM vectors", null).use { c ->
            val n = c.count
            val ids = LongArray(n)
            val at = LongArray(n)
            val vectors = FloatArray(n * dim)
            var i = 0
            while (c.moveToNext()) {
                ids[i] = c.getLong(0)
                at[i] = c.getLong(1)
                ByteBuffer.wrap(c.getBlob(2)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vectors, i * dim, dim)
                i++
            }
            return Embeddings(ids, at, vectors, dim)
        }
    }

    companion object {
        private val open = HashMap<String, EmbeddingStore>()

        /** One open helper per model for the whole process (it's thread-safe). */
        @Synchronized
        fun of(context: Context, model: AiModel): EmbeddingStore =
            open.getOrPut(model.id) { EmbeddingStore(context.applicationContext, model) }

        /** Drops a model's index: when it's removed or replaced by another tier. */
        @Synchronized
        fun delete(context: Context, model: AiModel) {
            open.remove(model.id)?.close()
            SQLiteDatabase.deleteDatabase(file(context, model))
        }

        private fun file(context: Context, model: AiModel) =
            File(context.noBackupFilesDir, "search/${model.id}.db").apply { parentFile?.mkdirs() }

        private fun toBytes(vector: FloatArray): ByteArray =
            ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(vector) }.array()
    }
}
