package com.subhashh.lumen.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Random

class VaultCryptoTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val key = VaultCrypto.newKey()

    private fun encrypted(plain: ByteArray, header: ByteArray = "{\"name\":\"a.jpg\"}".toByteArray()): File {
        val file = temp.newFile()
        file.outputStream().use { VaultCrypto.encrypt(key, header, plain.inputStream(), it) }
        return file
    }

    private fun bytes(n: Int) = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }

    private fun roundTrip(n: Int) {
        val plain = bytes(n)
        val out = ByteArrayOutputStream()
        VaultCrypto.decrypt(key, encrypted(plain), out)
        assertArrayEquals("size $n", plain, out.toByteArray())
    }

    @Test
    fun roundTripsAnySize() {
        val c = VaultCrypto.ChunkSize
        for (n in listOf(0, 1, 1000, c - 1, c, c + 1, 3 * c, 3 * c + 17)) roundTrip(n)
    }

    @Test
    fun headerComesBack() {
        val header = "{\"name\":\"IMG_1.jpg\",\"path\":\"DCIM/Camera/\"}".toByteArray()
        assertArrayEquals(header, VaultCrypto.readHeader(key, encrypted(bytes(10), header)))
    }

    @Test
    fun readerReadsAnywhere() {
        val c = VaultCrypto.ChunkSize
        val plain = bytes(2 * c + 500)
        VaultCrypto.Reader(key, encrypted(plain)).use { reader ->
            assertEquals(plain.size.toLong(), reader.length)
            // Straddling the first chunk boundary: two reads.
            val buffer = ByteArray(100)
            var got = 0
            var position = c - 40L
            while (got < 100) {
                val n = reader.read(position, buffer, got, 100 - got)
                assertTrue(n > 0)
                got += n
                position += n
            }
            assertArrayEquals(plain.copyOfRange(c - 40, c + 60), buffer)
            assertEquals(-1, reader.read(plain.size.toLong(), buffer, 0, 10))
        }
    }

    @Test
    fun tamperingIsCaught() {
        val file = encrypted(bytes(VaultCrypto.ChunkSize + 10))
        RandomAccessFile(file, "rw").use { raf ->
            val at = raf.length() - 5
            raf.seek(at)
            val b = raf.read()
            raf.seek(at)
            raf.write(b xor 0x01)
        }
        assertFails { VaultCrypto.decrypt(key, file, ByteArrayOutputStream()) }
    }

    @Test
    fun cuttingOffTheEndIsCaught() {
        // Two full chunks plus a bit; drop the final chunk so the second looks like the last.
        val c = VaultCrypto.ChunkSize
        val file = encrypted(bytes(2 * c + 10))
        RandomAccessFile(file, "rw").use { raf -> raf.setLength(raf.length() - (10 + 16)) }
        assertFails { VaultCrypto.decrypt(key, file, ByteArrayOutputStream()) }
    }

    @Test
    fun wrongKeyFails() {
        val file = encrypted(bytes(100))
        assertFails { VaultCrypto.decrypt(VaultCrypto.newKey(), file, ByteArrayOutputStream()) }
        assertFails { VaultCrypto.readHeader(VaultCrypto.newKey(), file) }
    }

    @Test
    fun recoveryFileOpensOnlyWithTheRightLock() {
        val text = VaultKeyFile.write(key, "1234", memoryKiB = 1024, passes = 1)
        assertArrayEquals(key.encoded, VaultKeyFile.read(text, "1234")!!.encoded)
        assertNull(VaultKeyFile.read(text, "1235"))
        assertNull(VaultKeyFile.read("not json", "1234"))
        assertTrue(VaultKeyFile.isCurrent(text))
    }

    @Test
    fun oldRecoveryFileStillOpensAndAsksForUpgrade() {
        val text = VaultKeyFile.writePbkdf2(key, "1234", iterations = 1_000)
        assertArrayEquals(key.encoded, VaultKeyFile.read(text, "1234")!!.encoded)
        assertNull(VaultKeyFile.read(text, "1235"))
        assertFalse(VaultKeyFile.isCurrent(text))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected a failure")
        } catch (_: Exception) {
        }
    }
}
