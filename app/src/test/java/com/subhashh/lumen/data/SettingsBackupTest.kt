package com.subhashh.lumen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val lumen get() = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)
    private val lock get() = context.getSharedPreferences("hidden_lock", Context.MODE_PRIVATE)

    private fun fill() = lumen.edit()
        .putBoolean("liquid_glass", false)
        .putInt("grid_columns", 3)
        .putLong("last_update_check", Long.MAX_VALUE)
        .putLong("first_day_used", 7L) // a Long small enough to look like an Int in JSON
        .putFloat("some_float", 0.1f)
        .putString("appearance", "Dark")
        .putString("recent_searches", "Café\nline two\t\"quoted\"")
        .putStringSet("excluded_folders", setOf("Developer/A/", "Ünïcode/"))
        .commit()

    private fun export(favorites: List<MediaItem> = emptyList()): File {
        val file = temp.newFile("backup.json")
        assertTrue(runBlocking { SettingsBackup.export(context, favorites, Uri.fromFile(file)) })
        return file
    }

    private fun import(file: File) = runBlocking { SettingsBackup.import(context, Uri.fromFile(file)) }
    private fun importText(text: String) = import(temp.newFile().apply { writeText(text) })

    @Test
    fun everySettingComesBackWithItsExactType() {
        fill()
        val before = lumen.all.toMap()
        val file = export()
        lumen.edit().clear().putInt("grid_columns", 9).commit()
        assertNotNull(import(file))
        val after = lumen.all
        assertEquals(before, after)
        // Equal values could still hide a type change (an Int read back as a Long would crash getInt).
        for ((key, value) in before) assertEquals(key, value!!::class, after[key]!!::class)
    }

    @Test
    fun importReplacesSettingsNotMergesThem() {
        lumen.edit().putInt("grid_columns", 4).commit()
        val file = export()
        lumen.edit().putBoolean("added_after_backup", true).commit()
        import(file)
        assertFalse(lumen.contains("added_after_backup"))
        assertEquals(4, lumen.getInt("grid_columns", 0))
    }

    @Test
    fun favoritesTravelAsFolderAndName() {
        val favorites = listOf(media(1, name = "IMG_1.jpg", relativePath = "DCIM/Camera/"), media(2, name = "x.png", relativePath = "Pictures/"))
        val imported = import(export(favorites))
        assertEquals(setOf("DCIM/Camera/IMG_1.jpg", "Pictures/x.png"), imported?.favoritePaths)
    }

    @Test
    fun theHiddenLockIsNeverExportedOrTouched() {
        HiddenLockStore(context).set(LockType.Pin, "4821")
        val lockBefore = lock.all.toMap()
        fill()
        val file = export()
        val text = file.readText()
        // The salt and hash: long enough that a match can't be a coincidence.
        for (value in lockBefore.values) if (value is String && value.length >= 16) assertFalse("lock data in backup", text.contains(value))
        assertFalse(text.contains("4821"))

        // Importing a backup made elsewhere (even one claiming lock keys) can't change the lock.
        val json = JSONObject(text)
        json.getJSONObject("settings").put("hash", JSONObject().put("type", "string").put("value", "00"))
        importText(json.toString())
        assertEquals(lockBefore, lock.all)
        assertTrue(HiddenLockStore(context).verify("4821"))
    }

    @Test
    fun aFileFromAnotherAppIsRefusedAndChangesNothing() {
        fill()
        val before = lumen.all.toMap()
        assertNull(importText("""{"app":"Aves","settings":{}}"""))
        assertNull(importText("""{"settings":{"grid_columns":{"type":"int","value":2}}}"""))
        assertNull(importText("not json at all"))
        assertNull(importText(""))
        assertEquals(before, lumen.all)
    }

    @Test
    fun aDamagedBackupChangesNothing() {
        fill()
        val before = lumen.all.toMap()
        val json = JSONObject(export().readText())
        // The second entry is broken; the first must not be applied (nor the existing ones cleared).
        json.put("settings", JSONObject()
            .put("grid_columns", JSONObject().put("type", "int").put("value", 2))
            .put("liquid_glass", JSONObject().put("type", "int").put("value", "not a number")))
        assertNull(importText(json.toString()))
        assertEquals(before, lumen.all)
    }

    @Test
    fun unknownEntryTypesAreSkipped() {
        val json = JSONObject()
            .put("app", "Lumen")
            .put("settings", JSONObject()
                .put("grid_columns", JSONObject().put("type", "int").put("value", 2))
                .put("future", JSONObject().put("type", "bytes").put("value", "AAAA")))
        assertNotNull(importText(json.toString()))
        assertEquals(mapOf("grid_columns" to 2), lumen.all)
    }

    @Test
    fun aBackupWithoutFavoritesHasNone() {
        val json = JSONObject().put("app", "Lumen").put("settings", JSONObject())
        assertEquals(emptySet<String>(), importText(json.toString())?.favoritePaths)
    }

    @Test
    fun exportingSomewhereUnwritableFails() {
        val dir = temp.newFolder()
        assertFalse(runBlocking { SettingsBackup.export(context, emptyList(), Uri.fromFile(dir)) })
    }

    @Test
    fun exportOverwritesAnOlderFileCompletely() {
        val file = temp.newFile("backup.json")
        file.writeText("x".repeat(100_000)) // longer than any backup
        lumen.edit().putInt("grid_columns", 3).commit()
        assertTrue(runBlocking { SettingsBackup.export(context, emptyList(), Uri.fromFile(file)) })
        assertEquals("Lumen", JSONObject(file.readText()).getString("app"))
    }
}
