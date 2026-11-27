package com.subhashh.lumen.data.backup

import android.content.Context
import android.os.Environment
import com.subhashh.lumen.data.vault.Vault
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.app.AppOpsManager
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Lumen Backup, stage 2: putting photos and Encrypted Hidden files back. A restore adds what's
 * missing; it must never replace or damage what's on the phone now.
 */
@RunWith(RobolectricTestRunner::class)
@Ignore(
    "Robolectric 4.17 can't grant All files access: Environment.isExternalStorageManager() throws " +
        "ArrayIndexOutOfBoundsException inside the framework whatever the app op and uid are set to " +
        "(tried 27 Nov). The cases stand as the spec; check them on the phone until then.",
)
class MediaRestoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val chunks = HashMap<String, ByteArray>()
    private val read: (String) -> ByteArray = { chunks.getValue(it) }
    private lateinit var vault: File

    /** All files access, as Settings grants it: the MANAGE_EXTERNAL_STORAGE app op. */
    private fun allFilesAccess(on: Boolean) {
        // Android finds the app by its uid first; Robolectric knows no packages for it until told.
        shadowOf(context.packageManager).setPackagesForUid(context.applicationInfo.uid, context.packageName)
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            "android:manage_external_storage", context.applicationInfo.uid, context.packageName,
            if (on) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED,
        )
    }

    @Before
    fun setUp() {
        allFilesAccess(true)
        assertTrue("All files access didn't take", Environment.isExternalStorageManager())
        vault = temp.newFolder(".LumenVault")
        Vault.testDir = vault
    }

    @After
    fun tearDown() {
        Vault.testDir = null
    }

    /** Stores [bytes] as one chunk; its name. */
    private fun chunk(bytes: ByteArray): String = "c${chunks.size}".also { chunks[it] = bytes }

    private fun vaultRows(vararg files: Pair<String, ByteArray>) = JSONArray().apply {
        for ((name, bytes) in files) put(JSONArray().put(name).put(bytes.size.toLong()).put(JSONArray().put(chunk(bytes))))
    }

    // ---- Encrypted Hidden ----

    @Test
    fun aVaultFileOnThePhoneIsNeverReplacedByTheBackupsCopy() = runBlocking {
        // Same name, different size: the file changed after the backup (re-encrypted under a new
        // Hidden password, or moved to the vault's newer format). The backup's older copy may
        // not even open with today's password; the file here is the one to keep.
        val now = "encrypted, current version".toByteArray()
        File(vault, "a1b2c3").writeBytes(now)
        MediaRestore.vault(context, vaultRows("a1b2c3" to "encrypted, older".toByteArray()), read) { _, _ -> }
        assertArrayEquals("the vault file on the phone was overwritten by the backup's", now, File(vault, "a1b2c3").readBytes())
    }

    @Test
    fun missingVaultFilesComeBackWhole() = runBlocking {
        val a = ByteArray(10_000) { it.toByte() }
        File(vault, "kept").writeBytes("same".toByteArray())
        val restored = MediaRestore.vault(context, vaultRows("kept" to "same".toByteArray(), "lost" to a), read) { _, _ -> }
        assertEquals(MediaRestore.VaultResult(restored = 1, wontOpen = 0, locked = false), restored)
        assertArrayEquals(a, File(vault, "lost").readBytes())
        assertFalse(File(vault, "lost.restoring").exists())
    }

    @Test
    fun aVaultNameCantReachOutsideTheVault() = runBlocking {
        val outside = File(vault.parentFile, "evil")
        runCatching { MediaRestore.vault(context, vaultRows("../evil" to "x".toByteArray()), read) { _, _ -> } }
        assertFalse(outside.exists())
        assertFalse(File(vault.parentFile, "evil.restoring").exists())
    }

    @Test
    fun withoutAllFilesAccessTheVaultIsntTouched() = runBlocking {
        allFilesAccess(false)
        assertFalse(Environment.isExternalStorageManager())
        assertEquals(0, MediaRestore.vault(context, vaultRows("lost" to "x".toByteArray()), read) { _, _ -> }.restored)
        assertFalse(File(vault, "lost").exists())
    }

    // ---- Photos, with All files access ----

    private fun photoRows(vararg rows: List<Any>) = JSONArray().apply { for (r in rows) put(JSONArray(r)) }

    @Test
    fun aDifferentPhotoOfTheSameNameIsKeptAndTheBackupsGoesBeside() = runBlocking {
        val camera = File(Environment.getExternalStorageDirectory(), "DCIM/Camera").apply { mkdirs() }
        val edited = "edited since the backup".toByteArray()
        File(camera, "IMG_1.jpg").writeBytes(edited)
        val original = "the original".toByteArray()
        withTimeout(20_000) {
            MediaRestore.photos(context, photoRows(listOf("DCIM/Camera/", "IMG_1.jpg", original.size.toLong(), 0L, "image/jpeg", JSONArray().put(chunk(original)))), read) { _, _ -> }
        }
        assertArrayEquals(edited, File(camera, "IMG_1.jpg").readBytes())
        assertArrayEquals(original, File(camera, "IMG_1 (2).jpg").readBytes())
    }

    @Test
    fun aPhotoPathCantReachOutsideSharedStorage() = runBlocking {
        val root = Environment.getExternalStorageDirectory()
        val bytes = "x".toByteArray()
        withTimeout(20_000) {
            MediaRestore.photos(
                context,
                photoRows(
                    listOf("../../", "evil.jpg", 1L, 0L, "image/jpeg", JSONArray().put(chunk(bytes))),
                    listOf("DCIM/", "../evil2.jpg", 1L, 0L, "image/jpeg", JSONArray().put(chunk(bytes))),
                ),
                read,
            ) { _, _ -> }
        }
        assertFalse(File(root.parentFile?.parentFile, "evil.jpg").exists())
        assertFalse(File(root, "evil2.jpg").exists())
        assertTrue(root.walkTopDown().none { it.name.startsWith("evil") })
    }
}
