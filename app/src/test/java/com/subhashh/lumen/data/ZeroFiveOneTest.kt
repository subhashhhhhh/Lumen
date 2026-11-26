package com.subhashh.lumen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** 0.5.1: squares behind see-through pictures (#27), metadata editing (#9), and the new settings. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZeroFiveOneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val raw = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    // --- Checkerboard (#27) ---

    @Test
    fun onlyPicturesThatCanBeSeeThroughGetSquares() {
        assertTrue(Checkerboard.mayBeTransparent(media(1, name = "sig.png", mimeType = "image/png", relativePath = "Download/")))
        assertTrue(Checkerboard.mayBeTransparent(media(2, name = "s.webp", mimeType = "image/webp")))
        assertFalse(Checkerboard.mayBeTransparent(media(3, name = "a.jpg", mimeType = "image/jpeg")))
        assertFalse(Checkerboard.mayBeTransparent(media(4, name = "v.mp4")))
        // Screenshots are PNGs that are never see-through.
        assertFalse(Checkerboard.mayBeTransparent(media(5, name = "Screenshot_1.png", mimeType = "image/png", relativePath = "Pictures/Screenshots/")))
    }

    @Test
    fun anOpaquePictureIsLeftAlone() {
        val opaque = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        assertTrue(opaque.hasAlpha())
        assertSame(opaque, Checkerboard.under(opaque))
    }

    @Test
    fun seeThroughPartsShowSquaresAndTheRestShowsThePicture() {
        // A black mark on a clear background: the signature of #27.
        val sig = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 50 until 70) for (y in 20 until 40) setPixel(x, y, Color.BLACK)
        }
        val out = Checkerboard.under(sig)
        assertNotSame(sig, out)
        assertEquals(120, out.width)
        assertEquals(60, out.height)
        assertEquals(Color.BLACK, out.getPixel(60, 30))
        // 12 squares across the shorter side: squares of 5 px, alternating.
        assertEquals(Checkerboard.Dark, out.getPixel(1, 1))
        assertEquals(Checkerboard.Light, out.getPixel(6, 1))
        assertEquals(Checkerboard.Light, out.getPixel(1, 6))
        assertEquals(Checkerboard.Dark, out.getPixel(6, 6))
        // Nothing see-through is left: a JPEG thumbnail of it can't turn black.
        for (x in 0 until out.width step 7) for (y in 0 until out.height step 7) assertEquals(0xFF, Color.alpha(out.getPixel(x, y)))
    }

    @Test
    fun aTinyPictureStillGetsSquaresItCanShow() {
        val small = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val out = Checkerboard.under(small)
        // Squares of at least 4 px, so both colours show.
        assertEquals(Checkerboard.Dark, out.getPixel(0, 0))
        assertEquals(Checkerboard.Light, out.getPixel(5, 0))
    }

    // --- All Metadata editing (#9) ---

    @Test
    fun onlyFilesExifCanBeSavedIntoAreEditable() {
        assertTrue(AllMetadata.editable(media(1, name = "a.jpg")))
        assertTrue(AllMetadata.editable(media(2, name = "a.png", mimeType = "image/png")))
        assertTrue(AllMetadata.editable(media(3, name = "a.webp", mimeType = "image/webp")))
        assertFalse(AllMetadata.editable(media(4, name = "a.heic", mimeType = "image/heic")))
        assertFalse(AllMetadata.editable(media(5, name = "a.gif", mimeType = "image/gif")))
        assertFalse(AllMetadata.editable(media(6, name = "a.dng", mimeType = "image/x-adobe-dng")))
        assertFalse(AllMetadata.editable(media(7, name = "a.jxl", mimeType = "image/jxl")))
        assertFalse(AllMetadata.editable(media(8, name = "v.mp4")))
    }

    // --- New settings ---

    @Test
    fun newSettingsStartAsBefore() {
        raw.edit().clear().commit()
        val prefs = LumenPrefs(context)
        // Updating to 0.5.1 must not stop any indexing step or move downloads elsewhere.
        assertTrue(prefs.indexSearch)
        assertTrue(prefs.indexText)
        assertTrue(prefs.indexVideoText)
        assertTrue(prefs.indexFaces)
        assertFalse(prefs.modelMirror)
        assertEquals(MapStyle.MatchApp, prefs.mapStyle)
        assertEquals(emptySet<String>(), prefs.shownFolders)
        assertFalse(prefs.discordAsked)
    }

    @Test
    fun mapStyleFollowsTheAppOnlyWhenAskedTo() {
        assertTrue(MapStyle.MatchApp.dark(appDark = true))
        assertFalse(MapStyle.MatchApp.dark(appDark = false))
        assertFalse(MapStyle.Light.dark(appDark = true))
        assertTrue(MapStyle.Dark.dark(appDark = false))
        raw.edit().clear().putString("map_style", "Sepia").commit()
        assertEquals(MapStyle.MatchApp, LumenPrefs(context).mapStyle)
    }

    @Test
    fun exclusionsAreBothListsTogether() {
        raw.edit().clear().commit()
        val prefs = LumenPrefs(context)
        prefs.excludedFolders = setOf("Download/")
        prefs.shownFolders = setOf("Download/Telegram/")
        val rules = LumenPrefs(context).exclusions
        assertTrue(rules.covers("Download/Other/"))
        assertFalse(rules.covers("Download/Telegram/"))
    }

    @Test
    fun shownFoldersAndTheNewSwitchesSurviveABackup() {
        raw.edit().clear().commit()
        val prefs = LumenPrefs(context)
        prefs.excludedFolders = setOf("Download/")
        prefs.shownFolders = setOf("Download/Telegram/", "Download/Ünïcode Folder/")
        prefs.indexFaces = false
        prefs.modelMirror = true
        prefs.mapStyle = MapStyle.Light
        val file = temp.newFile("backup.json")
        assertTrue(runBlocking { SettingsBackup.export(context, emptyList(), Uri.fromFile(file)) })

        raw.edit().clear().commit()
        runBlocking { SettingsBackup.import(context, Uri.fromFile(file)) }
        val back = LumenPrefs(context)
        assertEquals(setOf("Download/Telegram/", "Download/Ünïcode Folder/"), back.shownFolders)
        assertFalse(back.indexFaces)
        assertTrue(back.modelMirror)
        assertEquals(MapStyle.Light, back.mapStyle)
        assertFalse(back.exclusions.covers("Download/Telegram/"))
    }
}
