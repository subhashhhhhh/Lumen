package com.subhashh.lumen.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings → Backup: Lumen's settings and the list of favorites, as a small JSON file the user
 * keeps wherever they like, for a reinstall or a new phone. The Hidden lock lives in its own
 * preferences file and is never exported: a backup mustn't carry anything that unlocks Hidden.
 */
object SettingsBackup {

    private const val PrefsName = "lumen"
    private const val Format = 1

    /** Writes the backup to [uri] (from the system's Save dialog). */
    suspend fun export(context: Context, favorites: List<MediaItem>, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val settings = JSONObject()
            for ((key, value) in context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).all) {
                val entry = when (value) {
                    is Boolean -> JSONObject().put("type", "boolean").put("value", value)
                    is Int -> JSONObject().put("type", "int").put("value", value)
                    is Long -> JSONObject().put("type", "long").put("value", value)
                    is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                    is String -> JSONObject().put("type", "string").put("value", value)
                    is Set<*> -> JSONObject().put("type", "stringSet").put("value", JSONArray(value.map { it.toString() }))
                    else -> null
                }
                if (entry != null) settings.put(key, entry)
            }
            val json = JSONObject()
                .put("app", "Lumen")
                .put("format", Format)
                .put("exportedAt", System.currentTimeMillis())
                .put("settings", settings)
                // Favorites live in MediaStore, not in Lumen; save them by path to find them again.
                .put("favorites", JSONArray(favorites.map { it.relativePath + it.name }))
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
                ?: error("Can't write")
            true
        }.getOrDefault(false)
    }

    /** What an import found: whether the settings were applied, and the favorites' paths. */
    data class Imported(val favoritePaths: Set<String>)

    /**
     * Replaces Lumen's settings with the backup at [uri]. Null if it isn't a Lumen backup.
     * Favorites come back as paths; the caller marks the matching files.
     */
    suspend fun import(context: Context, uri: Uri): Imported? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching null
            val json = JSONObject(text)
            if (json.optString("app") != "Lumen") return@runCatching null
            val settings = json.getJSONObject("settings")
            context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit(commit = true) {
                clear()
                for (key in settings.keys()) {
                    val entry = settings.getJSONObject(key)
                    when (entry.getString("type")) {
                        "boolean" -> putBoolean(key, entry.getBoolean("value"))
                        "int" -> putInt(key, entry.getInt("value"))
                        "long" -> putLong(key, entry.getLong("value"))
                        "float" -> putFloat(key, entry.getDouble("value").toFloat())
                        "string" -> putString(key, entry.getString("value"))
                        "stringSet" -> {
                            val array = entry.getJSONArray("value")
                            putStringSet(key, (0 until array.length()).mapTo(HashSet()) { array.getString(it) })
                        }
                    }
                }
            }
            val favorites = json.optJSONArray("favorites")
            Imported(favoritePaths = (0 until (favorites?.length() ?: 0)).mapTo(HashSet()) { favorites!!.getString(it) })
        }.getOrNull()
    }
}
