package com.subhashh.lumen.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.subhashh.lumen.ai.LumenAi
import com.subhashh.lumen.data.AiHost
import com.subhashh.lumen.data.media
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.DataOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Lumen Backup, stage 1: the keys (a backup must open with its password or recovery key and
 * nothing else, and never carry a secret in the clear), matching backed-up photos to this phone's,
 * rewriting photo ids in the restored databases (real SQLite, the stores' own table layouts), and
 * putting a restore in place at start-up.
 */
@RunWith(RobolectricTestRunner::class)
class LumenBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    // Argon2id at a test's cost: the parameters are stored with the lock, so any cost opens.
    private fun lock(key: ByteArray, password: String) = BackupCrypto.lockWithPassword(key, password, memoryKiB = 1024, passes = 1)

    @Before
    fun host() {
        LumenAi.install(AiHost(context))
    }

    // ---- Keys ----

    @Test
    fun thePasswordOpensTheBackupKeyAndNothingElseDoes() {
        val key = BackupCrypto.newBackupKey()
        val locked = lock(key, "correct horse battery staple")
        assertArrayEquals(key, BackupCrypto.unlockWithPassword(locked, "correct horse battery staple"))
        assertNull(BackupCrypto.unlockWithPassword(locked, "correct horse battery stapl"))
        assertNull(BackupCrypto.unlockWithPassword(locked, "Correct horse battery staple"))
        assertNull(BackupCrypto.unlockWithPassword(locked, ""))
        // Non-ASCII passwords work, and are exact.
        val umlaut = lock(key, "Grüße 🌅")
        assertArrayEquals(key, BackupCrypto.unlockWithPassword(umlaut, "Grüße 🌅"))
        assertNull(BackupCrypto.unlockWithPassword(umlaut, "Grusse 🌅"))
    }

    @Test
    fun theSamePasswordLocksDifferentlyEachTime() {
        val key = BackupCrypto.newBackupKey()
        val a = lock(key, "pw")
        val b = lock(key, "pw")
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.sealed.contentEquals(b.sealed))
    }

    @Test
    fun aRecoveryKeyLooksAsShownAndOpensHoweverItsTyped() {
        val key = BackupCrypto.newBackupKey()
        val recovery = BackupCrypto.newRecoveryKey()
        assertTrue(recovery, Regex("^([A-HJ-NP-Z2-9]{4}-){7}[A-HJ-NP-Z2-9]{4}$").matches(recovery))
        val sealed = BackupCrypto.lockWithRecoveryKey(key, recovery)
        for (typed in listOf(recovery, recovery.lowercase(), recovery.replace('-', ' '), recovery.replace("-", ""), " $recovery\n")) {
            assertArrayEquals("typed as \"$typed\"", key, BackupCrypto.unlockWithRecoveryKey(sealed, typed))
        }
    }

    @Test
    fun aMistypedRecoveryKeyFailsQuietly() {
        val key = BackupCrypto.newBackupKey()
        val recovery = BackupCrypto.newRecoveryKey()
        val sealed = BackupCrypto.lockWithRecoveryKey(key, recovery)
        val first = recovery[0]
        val other = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".first { it != first }
        for (typed in listOf(other + recovery.drop(1), recovery.dropLast(5), "", "----", recovery.replace(recovery[5], 'O'), BackupCrypto.newRecoveryKey())) {
            assertNull("\"$typed\" opened the backup", BackupCrypto.unlockWithRecoveryKey(sealed, typed))
        }
    }

    @Test
    fun recoveryKeysAreNeverRepeated() {
        val keys = List(200) { BackupCrypto.newRecoveryKey() }
        assertEquals(200, keys.toSet().size)
    }

    @Test
    fun base32ComesBackAsItWent() {
        val random = Random(7)
        for (size in 0..40) {
            val bytes = random.nextBytes(size)
            val text = BackupCrypto.base32(bytes)
            assertTrue(text.none { it in "01IO" })
            val back = BackupCrypto.unbase32(text)!!
            assertArrayEquals("size $size", bytes, back.copyOf(size))
        }
    }

    @Test
    fun theBackupFolderNeverHoldsASecretInTheClear() {
        val key = BackupCrypto.newBackupKey()
        val password = "hunter2-but-longer"
        val recovery = BackupCrypto.newRecoveryKey()
        val config = LumenBackup.Config("id-1", true, lock(key, password), BackupCrypto.lockWithRecoveryKey(key, recovery))
        val json = config.toJson()
        for (secret in listOf(password, recovery, recovery.replace("-", ""), BackupCrypto.b64(key), BackupCrypto.hex(key))) {
            assertFalse("config contains a secret: $secret", json.contains(secret, ignoreCase = true))
        }
        // And it opens again from the file alone, with either.
        val back = LumenBackup.Config.parse(json)
        assertTrue(back.encrypted)
        assertArrayEquals(key, BackupCrypto.unlockWithPassword(back.passwordLock!!, password))
        assertArrayEquals(key, BackupCrypto.unlockWithRecoveryKey(back.recoveryLock!!, recovery))
    }

    @Test
    fun aBackupFromANewerLumenIsRefused() {
        val json = JSONObject(LumenBackup.Config("id", false, null, null).toJson()).put("format", 99).toString()
        try {
            LumenBackup.Config.parse(json)
            fail("a newer format was read as if understood")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun chunkNamesSayNothingAboutWhatsInThem() {
        val plain = "the same 4 MB of a database".toByteArray()
        val one = BackupCrypto.Keys(BackupCrypto.newBackupKey())
        val two = BackupCrypto.Keys(BackupCrypto.newBackupKey())
        // Encrypted: not the content's plain fingerprint (anyone could check which known file is
        // in the folder), and different in another backup; the same within one (so it's stored once).
        assertNotEquals(BackupCrypto.plainName(plain), one.name(plain))
        assertNotEquals(one.name(plain), two.name(plain))
        assertEquals(one.name(plain), one.name(plain.copyOf()))
        assertNotEquals(one.name(plain), one.name(plain + 0))
    }

    // ---- Matching photos on another phone ----

    private fun backedUp(vararg rows: List<Any>) = JSONArray().apply { for (r in rows) put(JSONArray(r)) }

    @Test
    fun aPhotoIsFoundByFolderNameAndSizeThenMovedThenEdited() {
        val here = listOf(
            media(501, name = "IMG_1.jpg", relativePath = "DCIM/Camera/", size = 1000),
            media(502, name = "IMG_2.jpg", relativePath = "Pictures/Moved/", size = 2000),
            media(503, name = "IMG_3.jpg", relativePath = "DCIM/Camera/", size = 3333),
        ).associateBy { it.id }
        val map = RestoreStaging.matchMedia(
            backedUp(
                listOf(1, "DCIM/Camera/", "IMG_1.jpg", 1000, 0),
                listOf(2, "DCIM/Camera/", "IMG_2.jpg", 2000, 0), // moved to another folder since
                listOf(3, "DCIM/Camera/", "IMG_3.jpg", 3000, 0), // edited since: same place, new size
                listOf(4, "DCIM/Camera/", "IMG_4.jpg", 4000, 0), // not on this phone
            ),
            here,
        )
        assertEquals(501L, map[1]?.first)
        assertEquals(502L, map[2]?.first)
        assertEquals(503L, map[3]?.first)
        assertFalse(4L in map)
    }

    @Test
    fun anExactMatchIsNeverTakenByALooserOne() {
        // Two photos in the backup with the same name and size (a copy in Download). On this phone
        // only the camera one is there. It must go to the camera photo's own entry (exact: folder,
        // name and size), whichever comes first in the backup's list; the copy gets nothing.
        val camera = media(900, name = "IMG_7.jpg", relativePath = "DCIM/Camera/", size = 5000)
        val here = mapOf(camera.id to camera)
        val copyFirst = backedUp(listOf(10, "Download/", "IMG_7.jpg", 5000, 0), listOf(20, "DCIM/Camera/", "IMG_7.jpg", 5000, 0))
        val cameraFirst = backedUp(listOf(20, "DCIM/Camera/", "IMG_7.jpg", 5000, 0), listOf(10, "Download/", "IMG_7.jpg", 5000, 0))
        for ((order, rows) in listOf("copy first" to copyFirst, "camera first" to cameraFirst)) {
            val map = RestoreStaging.matchMedia(rows, here)
            assertEquals("$order: the camera photo's captions and faces went to the wrong entry", 900L, map[20]?.first)
            assertFalse("$order: one photo here matched twice", 10L in map)
        }
    }

    // ---- Rewriting ids in restored databases ----

    private fun db(file: File, vararg sql: String) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db -> sql.forEach(db::execSQL) }
    }

    private fun <T> query(file: File, sql: String, read: (android.database.Cursor) -> T): List<T> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }
        }

    @Test
    fun idsThatSwapPlacesEndUpRightAndPhotosNotHereGo() {
        val staging = temp.newFolder("staging")
        val captions = File(staging, "captions.db")
        db(
            captions,
            "CREATE TABLE captions (media_id INTEGER PRIMARY KEY, text TEXT NOT NULL)",
            "INSERT INTO captions VALUES (1, 'beach'), (2, 'snow'), (3, 'gone')",
        )
        // On this phone photo 1 is 2 and 2 is 1; 3 isn't here.
        RestoreStaging.remapAll(staging, mapOf(1L to (2L to 0L), 2L to (1L to 0L)))
        val rows = query(captions, "SELECT media_id, text FROM captions ORDER BY media_id") { it.getLong(0) to it.getString(1) }
        assertEquals(listOf(1L to "snow", 2L to "beach"), rows)
    }

    @Test
    fun theTextIndexStillFindsEachPhotoUnderItsNewId() {
        val staging = temp.newFolder("staging")
        val text = File(staging, "text.db")
        db(
            text,
            "CREATE TABLE items (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL)",
            "CREATE VIRTUAL TABLE texts USING fts4(body, tokenize=unicode61 \"remove_diacritics=2\")",
            "CREATE TABLE lines (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, data BLOB)",
            "INSERT INTO items VALUES (10, 111), (20, 222), (30, 333)",
            "INSERT INTO texts (docid, body) VALUES (10, 'boarding pass'), (20, 'menu café'), (30, 'receipt')",
            "INSERT INTO lines VALUES (10, 111, 1, 1, NULL), (20, 222, 1, 1, NULL), (30, 333, 1, 1, NULL)",
        )
        RestoreStaging.remapAll(staging, mapOf(10L to (7L to 9007L), 20L to (10L to 9010L)))
        assertEquals(listOf(7L), query(text, "SELECT docid FROM texts WHERE texts MATCH 'boarding'") { it.getLong(0) })
        assertEquals(listOf(10L), query(text, "SELECT docid FROM texts WHERE texts MATCH 'cafe'") { it.getLong(0) })
        assertEquals(emptyList<Long>(), query(text, "SELECT docid FROM texts WHERE texts MATCH 'receipt'") { it.getLong(0) })
        // The modified times are this phone's, or the indexes would read every photo again.
        assertEquals(listOf(7L to 9007L, 10L to 9010L), query(text, "SELECT media_id, modified FROM items ORDER BY media_id") { it.getLong(0) to it.getLong(1) })
        assertEquals(listOf(7L to 9007L, 10L to 9010L), query(text, "SELECT media_id, modified FROM lines ORDER BY media_id") { it.getLong(0) to it.getLong(1) })
    }

    @Test
    fun facesMoveWithTheirPhotosAndPeopleStayWhole() {
        val staging = temp.newFolder("staging")
        val faces = File(staging, "faces.db")
        db(
            faces,
            "CREATE TABLE items (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL)",
            "CREATE TABLE faces (id INTEGER PRIMARY KEY, media_id INTEGER NOT NULL, person INTEGER)",
            "CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT, hidden INTEGER NOT NULL DEFAULT 0, cover_face INTEGER)",
            "INSERT INTO items VALUES (1, 5), (2, 6)",
            "INSERT INTO faces VALUES (100, 1, 7), (101, 1, 8), (102, 2, 7)",
            "INSERT INTO people VALUES (7, 'Mai', 0, 100), (8, 'Daniel', 0, 101)",
        )
        RestoreStaging.remapAll(staging, mapOf(1L to (41L to 55L), 2L to (42L to 66L)))
        assertEquals(listOf(Triple(100L, 41L, 7L), Triple(101L, 41L, 8L), Triple(102L, 42L, 7L)),
            query(faces, "SELECT id, media_id, person FROM faces ORDER BY id") { Triple(it.getLong(0), it.getLong(1), it.getLong(2)) })
        assertEquals(listOf("Mai" to 100L, "Daniel" to 101L), query(faces, "SELECT name, cover_face FROM people ORDER BY id") { it.getString(0) to it.getLong(1) })
        assertEquals(listOf(41L to 55L, 42L to 66L), query(faces, "SELECT media_id, modified FROM items ORDER BY media_id") { it.getLong(0) to it.getLong(1) })
    }

    // ---- Face crops pack ----

    private fun pack(file: File, vararg entries: Pair<String, ByteArray>, cutLast: Int = 0) {
        DataOutputStream(file.outputStream()).use { out ->
            entries.forEachIndexed { i, (name, bytes) ->
                out.writeUTF(name)
                out.writeLong(bytes.size.toLong())
                out.write(if (i == entries.lastIndex) bytes.copyOf(bytes.size - cutLast) else bytes)
            }
        }
    }

    @Test
    fun cropsComeBackByteForByte() {
        val file = temp.newFile("faces.pack")
        val a = Random(1).nextBytes(70_000)
        val b = ByteArray(0)
        pack(file, "100.jpg" to a, "101.jpg" to b)
        val dir = File(temp.root, "faces")
        LumenBackup.unpack(file, dir)
        assertArrayEquals(a, File(dir, "100.jpg").readBytes())
        assertArrayEquals(b, File(dir, "101.jpg").readBytes())
    }

    @Test
    fun aPackCantWriteOutsideTheCropsFolder() {
        val outside = File(temp.root, "evil")
        for (name in listOf("../evil", "sub/evil", "..")) {
            val file = temp.newFile()
            pack(file, name to "x".toByteArray())
            val dir = File(temp.root, "faces-${name.hashCode()}")
            try {
                LumenBackup.unpack(file, dir)
                fail("unpacked \"$name\"")
            } catch (expected: Exception) {
            }
            assertFalse(outside.exists())
        }
    }

    @Test
    fun aCutShortPackIsAnError() {
        val file = temp.newFile("faces.pack")
        pack(file, "100.jpg" to ByteArray(1000), cutLast = 10)
        try {
            LumenBackup.unpack(file, File(temp.root, "faces"))
            fail("a pack cut short unpacked without complaint")
        } catch (expected: Exception) {
        }
    }

    // ---- Putting a restore in place ----

    @Test
    fun aRestoreCutShortBeforeItWasReadyChangesNothing() {
        val live = context.getDatabasePath("captions.db").apply { parentFile!!.mkdirs(); writeText("live") }
        val staging = RestoreStaging.dir(context).apply { mkdirs() }
        File(staging, "captions.db").writeText("half downloaded")
        // No manifest: the download never finished.
        assertTrue(RestoreStaging.apply(context).isEmpty())
        assertEquals("live", live.readText())
        assertFalse(staging.exists())
    }

    @Test
    fun aReadyRestoreReplacesTheDatabaseAndItsOldLog() {
        val live = context.getDatabasePath("captions.db").apply { parentFile!!.mkdirs(); writeText("live") }
        val wal = File(live.path + "-wal").apply { writeText("old log that would be replayed over the restore") }
        val staging = RestoreStaging.dir(context).apply { deleteRecursively(); mkdirs() }
        File(staging, "captions.db").writeText("restored")
        File(staging, "manifest.json").writeText(JSONObject().put("searchModel", JSONObject.NULL).toString())
        RestoreStaging.apply(context)
        assertEquals("restored", live.readText())
        assertFalse("the old log would be applied to the restored database", wal.exists())
        assertFalse(staging.exists())
    }
}
