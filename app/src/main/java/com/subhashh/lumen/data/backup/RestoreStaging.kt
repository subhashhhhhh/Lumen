package com.subhashh.lumen.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.SettingsBackup
import com.subhashh.lumen.data.toRef
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The second half of a restore. [LumenBackup.stageRestore] downloads into [dir] and rewrites ids
 * there; [commit] notes what goes where and pauses indexing; Lumen restarts, and [apply] (first
 * thing in Application.onCreate, before anything opens a database) moves the files in.
 */
object RestoreStaging {
    private const val Tag = "LumenBackup"

    fun dir(context: Context) = File(context.noBackupFilesDir, "restore")

    private fun manifest(context: Context) = File(dir(context), "manifest.json")

    /**
     * Each photo in the backup → (its id here, the `modified` the indexes compare), found by
     * folder, name and size; then by name and size (moved); then by folder and name. A photo
     * that isn't on this phone has no entry, and its rows are left out of the restored data.
     */
    fun matchMedia(media: JSONArray, library: Map<Long, MediaItem>): Map<Long, Pair<Long, Long>> {
        val byPathSize = HashMap<String, MediaItem>()
        val byNameSize = HashMap<String, MediaItem>()
        val byPath = HashMap<String, MediaItem>()
        for (item in library.values) {
            byPathSize.putIfAbsent("${item.relativePath}${item.name}|${item.size}", item)
            byNameSize.putIfAbsent("${item.name}|${item.size}", item)
            byPath.putIfAbsent("${item.relativePath}${item.name}", item)
        }
        val out = HashMap<Long, Pair<Long, Long>>()
        val taken = HashSet<Long>()
        for (i in 0 until media.length()) {
            val row = media.getJSONArray(i)
            val old = row.getLong(0)
            val path = row.getString(1)
            val name = row.getString(2)
            val size = row.getLong(3)
            val found = byPathSize["$path$name|$size"] ?: byNameSize["$name|$size"] ?: byPath["$path$name"] ?: continue
            // One photo here for one in the backup: a second match would make two rows one id.
            if (!taken.add(found.id)) continue
            out[old] = found.id to found.toRef().modified
        }
        return out
    }

    /** A table whose rows belong to a photo: its id column and, if it has one, its `modified`. */
    private class Keyed(val table: String, val id: String, val modified: String? = null)

    private val Tables = mapOf(
        "captions.db" to listOf(Keyed("captions", "media_id")),
        "faces.db" to listOf(Keyed("items", "media_id", "modified"), Keyed("faces", "media_id")),
        "search.db" to listOf(Keyed("items", "media_id", "modified"), Keyed("vectors", "media_id")),
        "text.db" to listOf(Keyed("items", "media_id", "modified"), Keyed("lines", "media_id", "modified"), Keyed("texts", "docid")),
    )

    /** Rewrites every photo id in the staged databases to its id here; rows for photos not here go. */
    fun remapAll(staging: File, map: Map<Long, Pair<Long, Long>>) {
        for ((file, tables) in Tables) {
            val db = File(staging, file).takeIf { it.isFile } ?: continue
            remap(db, tables, map)
        }
    }

    private fun remap(file: File, tables: List<Keyed>, map: Map<Long, Pair<Long, Long>>) {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.beginTransaction()
            try {
                db.execSQL("CREATE TEMP TABLE idmap (old INTEGER PRIMARY KEY, new INTEGER NOT NULL, modified INTEGER NOT NULL)")
                val insert = db.compileStatement("INSERT INTO idmap VALUES (?, ?, ?)")
                for ((old, value) in map) {
                    insert.bindLong(1, old); insert.bindLong(2, value.first); insert.bindLong(3, value.second)
                    insert.executeInsert(); insert.clearBindings()
                }
                for (t in tables) {
                    if (!exists(db, t.table)) continue
                    // Old ids out of the way first (negative), so a new id never meets an old one.
                    db.execSQL("UPDATE ${t.table} SET ${t.id} = -${t.id} - 1")
                    val modified = t.modified?.let { ", $it = (SELECT modified FROM idmap WHERE old = -${t.table}.${t.id} - 1)" }.orEmpty()
                    db.execSQL(
                        "UPDATE ${t.table} SET ${t.id} = (SELECT new FROM idmap WHERE old = -${t.table}.${t.id} - 1)$modified " +
                            "WHERE ${t.id} < 0 AND (-${t.id} - 1) IN (SELECT old FROM idmap)",
                    )
                    db.execSQL("DELETE FROM ${t.table} WHERE ${t.id} < 0")
                }
                db.execSQL("DROP TABLE idmap")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        File(file.path + "-journal").delete()
    }

    private fun exists(db: SQLiteDatabase, table: String) =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE name = ?", arrayOf(table)).use { it.moveToFirst() }

    /** Notes what goes where, and pauses indexing so nothing writes until the restart. */
    fun commit(context: Context, staging: File, searchModel: String?) {
        val json = JSONObject().put("searchModel", searchModel ?: JSONObject.NULL)
        File(staging, "manifest.json").writeText(json.toString())
        com.subhashh.lumen.ai.SmartIndex.pause(context, Long.MAX_VALUE)
    }

    /**
     * Moves a staged restore into place. Call first thing when Lumen starts, before any database
     * is open. Returns the favorites to mark again (they need the person's OK, asked later).
     */
    fun apply(context: Context): Set<String> {
        val staging = dir(context)
        val manifestFile = manifest(context)
        if (!manifestFile.isFile) {
            if (staging.exists()) staging.deleteRecursively() // a restore cut short before commit
            return emptySet()
        }
        var favorites = emptySet<String>()
        runCatching {
            val manifest = JSONObject(manifestFile.readText())
            val noBackup = context.noBackupFilesDir
            fun place(name: String, target: File) {
                val from = File(staging, name).takeIf { it.isFile } ?: return
                for (suffix in listOf("", "-wal", "-shm", "-journal")) File(target.path + suffix).delete()
                target.parentFile?.mkdirs()
                if (!from.renameTo(target)) from.copyTo(target, overwrite = true)
            }
            place("captions.db", context.getDatabasePath("captions.db"))
            place("faces.db", File(noBackup, "search/faces.db"))
            place("text.db", File(noBackup, "search/text.db"))
            manifest.optString("searchModel").takeIf { it.isNotEmpty() && it != "null" }?.let { place("search.db", File(noBackup, "search/$it.db")) }
            File(staging, "faces.pack").takeIf { it.isFile }?.let { pack ->
                val crops = File(noBackup, "faces")
                crops.deleteRecursively()
                LumenBackup.unpack(pack, crops)
            }
            File(staging, "settings.json").takeIf { it.isFile }?.let { file ->
                favorites = kotlinx.coroutines.runBlocking { SettingsBackup.import(context, Uri.fromFile(file)) }?.favoritePaths.orEmpty()
            }
        }.onFailure { Log.w(Tag, "Couldn't finish a restore", it) }
        staging.deleteRecursively()
        com.subhashh.lumen.ai.SmartIndex.resume(context)
        return favorites
    }
}
