package com.subhashh.lumen.data.backup

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Settings → Backup → Folders: which folders' photos and videos go into a backup. */
@RunWith(RobolectricTestRunner::class)
class BackupFoldersTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun fresh() {
        context.getSharedPreferences("lumen-backup-choices", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun everyFolderIsInUntilTurnedOff() {
        // Nothing chosen yet, so nothing is left out, and a folder made tomorrow is in too.
        assertTrue(LumenBackup.foldersOff(context).isEmpty())
    }

    @Test
    fun turningAFolderOffAndOnAgainLeavesNoTrace() {
        LumenBackup.setFolderOn(context, "Download/", false)
        assertEquals(setOf("Download/"), LumenBackup.foldersOff(context))
        LumenBackup.setFolderOn(context, "Download/", true)
        assertTrue(LumenBackup.foldersOff(context).isEmpty())
    }

    @Test
    fun eachFolderIsItsOwnChoice() {
        LumenBackup.setFolderOn(context, "Pictures/Instagram/", false)
        LumenBackup.setFolderOn(context, "Download/", false)
        LumenBackup.setFolderOn(context, "Download/", false) // twice: still once
        LumenBackup.setFolderOn(context, "DCIM/Camera/", true) // on already: nothing changes
        assertEquals(setOf("Pictures/Instagram/", "Download/"), LumenBackup.foldersOff(context))
        // A folder inside one that's off is a folder of its own, as the list shows it.
        assertTrue("Pictures/Instagram/wildpaoli/" !in LumenBackup.foldersOff(context))
        LumenBackup.setFolderOn(context, "Pictures/Instagram/", true)
        assertEquals(setOf("Download/"), LumenBackup.foldersOff(context))
    }

    @Test
    fun choicesLastBeyondTheScreenThatMadeThem() {
        LumenBackup.setFolderOn(context, "Movies/Screen recordings/", false)
        // Read back through a fresh preferences lookup, as the next backup (in its service) does.
        val stored = context.getSharedPreferences("lumen-backup-choices", Context.MODE_PRIVATE)
            .getStringSet("photo-folders-off", null)
        assertEquals(setOf("Movies/Screen recordings/"), stored)
    }

    @Test
    fun theStoredSetIsntChangedInPlace() {
        // SharedPreferences hands out its own set: changing that copy in place corrupts what's stored.
        LumenBackup.setFolderOn(context, "A/", false)
        val first = LumenBackup.foldersOff(context)
        LumenBackup.setFolderOn(context, "B/", false)
        assertEquals(setOf("A/"), first)
        assertEquals(setOf("A/", "B/"), LumenBackup.foldersOff(context))
    }
}
