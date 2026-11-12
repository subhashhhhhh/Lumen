package com.subhashh.lumen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoritesImportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    private val library = listOf(
        media(1, name = "IMG_1.jpg", relativePath = "DCIM/Camera/"),
        media(2, name = "x.png", relativePath = "Pictures/"),
        media(3, name = "y.gif", relativePath = "Download/"),
        media(4, name = "Sunset.JPG", relativePath = "DCIM/Camera/"),
    )

    private fun read(text: String) = runBlocking {
        FavoritesImport.read(context, Uri.fromFile(temp.newFile().apply { writeText(text) }), library)
    }

    @Test
    fun fossifyListOfFullPaths() {
        val found = read(
            """
            /storage/emulated/0/DCIM/Camera/IMG_1.jpg
            /storage/1A2B-3C4D/Pictures/x.png
            /sdcard/Download/y.gif
            /storage/emulated/0/DCIM/Camera/deleted.jpg
            """.trimIndent(),
        )
        assertEquals(4, found?.listed)
        assertEquals(listOf(1L, 2L, 3L), found?.matched?.ids)
    }

    @Test
    fun fossifyWithWindowsLineEndingsAndJunkLines() {
        val found = read("\r\n# my favorites\r\n/storage/emulated/0/DCIM/Camera/IMG_1.jpg\r\n\r\nnot a path\r\n")
        assertEquals(1, found?.listed)
        assertEquals(listOf(1L), found?.matched?.ids)
    }

    @Test
    fun secondaryUsersAndOldSdcardPaths() {
        val found = read("/storage/emulated/10/Pictures/x.png\n/mnt/sdcard/Download/y.gif")
        assertEquals(listOf(2L, 3L), found?.matched?.ids)
    }

    @Test
    fun avesBackupByVolume() {
        val found = read(
            """
            {"version":1,"favourites":{
              "/storage/emulated/0":["DCIM/Camera/IMG_1.jpg","/Pictures/x.png"],
              "/storage/1A2B-3C4D":["Download/y.gif"]
            }}
            """.trimIndent(),
        )
        assertEquals(3, found?.listed)
        assertEquals(setOf(1L, 2L, 3L), found?.matched?.ids?.toSet())
    }

    @Test
    fun lumensOwnBackup() {
        val found = read("""{"app":"Lumen","favorites":["DCIM/Camera/IMG_1.jpg","Download/y.gif"]}""")
        assertEquals(listOf(1L, 3L), found?.matched?.ids)
    }

    @Test
    fun matchingIgnoresCase() {
        val found = read("/storage/emulated/0/dcim/camera/sunset.jpg")
        assertEquals(listOf(4L), found?.matched?.ids)
    }

    @Test
    fun listedTwiceIsMatchedOnce() {
        val found = read("/sdcard/Pictures/x.png\n/storage/emulated/0/Pictures/x.png")
        assertEquals(2, found?.listed)
        assertEquals(listOf(2L), found?.matched?.ids)
    }

    @Test
    fun aFolderAloneMatchesNothing() {
        // "DCIM/Camera/" + "" must not match a file whose name happens to be empty, nor the folder.
        val found = read("/storage/emulated/0/DCIM/Camera/")
        assertEquals(emptyList<Long>(), found?.matched?.ids)
    }

    @Test
    fun unknownFormatsAreRefused() {
        assertNull(read(""))
        assertNull(read("   \n  "))
        assertNull(read("IMG_1.jpg\nx.png")) // names without paths
        assertNull(read("""{"app":"Lumen","settings":{}}""")) // a backup without favorites
        assertNull(read("""{"favourites": broken"""))
    }

    @Test
    fun aMissingFileIsRefused() {
        val gone = temp.newFile().apply { delete() }
        assertNull(runBlocking { FavoritesImport.read(context, Uri.fromFile(gone), library) })
    }

    @Test
    fun lumenBackupRoundTrip() {
        val favorites = listOf(library[0], library[3])
        val file = temp.newFile("backup.json")
        runBlocking { SettingsBackup.export(context, favorites, Uri.fromFile(file)) }
        val found = runBlocking { FavoritesImport.read(context, Uri.fromFile(file), library) }
        assertEquals(listOf(1L, 4L), found?.matched?.ids)
    }
}
