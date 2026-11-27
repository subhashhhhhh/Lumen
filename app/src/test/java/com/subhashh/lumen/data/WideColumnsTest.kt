package com.subhashh.lumen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Wide windows keep their own column choices (0496191): pinching in desktop mode mustn't change
 * the phone's grid, nor the other way round.
 */
@RunWith(RobolectricTestRunner::class)
class WideColumnsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun fresh() {
        context.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun wideWindowsStartWithTheirOwnDefaults() {
        val prefs = LumenPrefs(context)
        assertEquals(3, prefs.gridColumnsWide)
        assertEquals(3, prefs.searchColumnsWide)
        assertEquals(2, prefs.albumColumnsWide)
    }

    @Test
    fun aWideChoiceLeavesThePhonesAlone() {
        val prefs = LumenPrefs(context)
        val phone = Triple(prefs.gridColumns, prefs.searchColumns, prefs.albumColumns)
        prefs.gridColumnsWide = 6
        prefs.searchColumnsWide = 5
        prefs.albumColumnsWide = 4
        val again = LumenPrefs(context)
        assertEquals(phone, Triple(again.gridColumns, again.searchColumns, again.albumColumns))
        assertEquals(6, again.gridColumnsWide)
        assertEquals(5, again.searchColumnsWide)
        assertEquals(4, again.albumColumnsWide)
    }

    @Test
    fun aPhoneChoiceLeavesTheWideOnesAlone() {
        val prefs = LumenPrefs(context)
        prefs.gridColumns = 2
        prefs.searchColumns = 2
        prefs.albumColumns = 1
        val again = LumenPrefs(context)
        assertEquals(3, again.gridColumnsWide)
        assertEquals(3, again.searchColumnsWide)
        assertEquals(2, again.albumColumnsWide)
    }

    @Test
    fun wideChoicesComeBackFromASettingsBackup() {
        val prefs = LumenPrefs(context)
        prefs.gridColumnsWide = 7
        prefs.albumColumnsWide = 3
        val file = temp.newFile("settings.json")
        assertTrue(runBlocking { SettingsBackup.export(context, emptyList(), Uri.fromFile(file)) })
        context.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { SettingsBackup.import(context, Uri.fromFile(file)) }
        val back = LumenPrefs(context)
        assertEquals(7, back.gridColumnsWide)
        assertEquals(3, back.albumColumnsWide)
    }
}
