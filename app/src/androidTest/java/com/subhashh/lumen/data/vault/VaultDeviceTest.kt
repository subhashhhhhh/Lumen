package com.subhashh.lumen.data.vault

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * The vault end to end on a real phone: Keystore, encrypting with verification, listing,
 * random reads, recovery with the lock, restoring. Uses a throwaway vault folder, Keystore alias
 * and key storage, so it never touches the real vault. Run with `adb shell am instrument`, not
 * connectedAndroidTest, which uninstalls the app (and its data) afterwards.
 */
@RunWith(AndroidJUnit4::class)
class VaultDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDir = File(context.cacheDir, "vault-test")
    private val restoreDir = File(Environment.getExternalStorageDirectory(), "Download/.lumen-vault-test")

    @Before
    fun setUp() {
        testDir.deleteRecursively()
        restoreDir.deleteRecursively()
        Vault.close()
        Vault.testDir = testDir
        Vault.keysFactory = { c, d -> VaultKeys(c, d, prefsName = "vault_keys_test", alias = "lumen_vault_test") }
        context.getSharedPreferences("vault_keys_test", 0).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Vault.close()
        Vault.testDir = null
        Vault.keysFactory = { c, d -> VaultKeys(c, d) }
        testDir.deleteRecursively()
        restoreDir.deleteRecursively()
        context.getSharedPreferences("vault_keys_test", 0).edit().clear().commit()
    }

    @Test
    fun encryptListReadRecoverRestore() = runBlocking {
        val bytes = Random(7).nextBytes(VaultCrypto.ChunkSize * 2 + 1234)
        val source = File(context.cacheDir, "vault-src.jpg").apply { writeBytes(bytes) }
        Vault.create(context, "1234")
        val details = Vault.Details(
            hiddenName = ".photo.jpg", visibleName = "photo.jpg", path = "Download/.lumen-vault-test/",
            mime = "image/jpeg", takenAt = 1_700_000_000_000, modified = 1_700_000_000_000,
            width = 4000, height = 3000, durationMs = 0, size = bytes.size.toLong(),
        )

        assertTrue("add", Vault.add(context, source, details))
        assertFalse("original removed after verifying", source.exists())

        val items = Vault.list()
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("photo.jpg", item.name)

        val out = ByteArrayOutputStream()
        assertTrue(Vault.decrypt(item, out))
        assertArrayEquals(bytes, out.toByteArray())

        Vault.reader(item)!!.use { reader ->
            val buffer = ByteArray(500)
            val n = reader.read(VaultCrypto.ChunkSize - 100L, buffer, 0, 500)
            assertTrue(n > 0)
            assertArrayEquals(bytes.copyOfRange(VaultCrypto.ChunkSize - 100, VaultCrypto.ChunkSize - 100 + n), buffer.copyOf(n))
        }

        // Day to day: the Keystore copy opens it.
        Vault.close()
        assertTrue("keystore", Vault.open(context))

        // After a "reinstall" (no Keystore copy): only the right lock opens it.
        Vault.close()
        context.getSharedPreferences("vault_keys_test", 0).edit().clear().commit()
        assertFalse("no key, no lock", Vault.open(context))
        assertFalse("wrong lock", Vault.open(context, "9999"))
        assertTrue("right lock", Vault.open(context, "1234"))

        // Back out, hidden, exactly as it was.
        val restored = Vault.restore(context, Vault.list().single(), hidden = true)
        assertNotNull(restored)
        assertEquals(".photo.jpg", restored!!.name)
        assertArrayEquals(bytes, restored.readBytes())
        assertTrue("vault empty again", Vault.list().isEmpty())
    }
}
