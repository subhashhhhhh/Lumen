package com.subhashh.lumen.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Random

/**
 * What someone with access to the vault folder (but not the key) can do to its files. Each test
 * edits the raw bytes of encrypted files; every edit must be refused, never silently accepted.
 * Complements VaultCryptoTest, which covers round trips and simple tampering.
 *
 * File layout: magic (8) | file id (8) | header length (4) | sealed header | chunks (each + 16-byte tag).
 */
class VaultCryptoAttackTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val key = VaultCrypto.newKey()
    private val c = VaultCrypto.ChunkSize

    private fun bytes(n: Int, seed: Long = n.toLong()) = ByteArray(n).also { Random(seed).nextBytes(it) }

    private fun encrypt(plain: ByteArray, header: String = "{\"name\":\"a.jpg\"}"): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCrypto.encrypt(key, header.toByteArray(), plain.inputStream(), out)
        return out.toByteArray()
    }

    private fun file(raw: ByteArray): File = temp.newFile().apply { writeBytes(raw) }

    private fun headerLength(raw: ByteArray) = ByteBuffer.wrap(raw, 16, 4).int
    private fun bodyOffset(raw: ByteArray) = 20 + headerLength(raw)

    /** Asserts that every way of reading [raw] refuses it. */
    private fun assertRejected(raw: ByteArray, message: String) {
        val f = file(raw)
        assertThrows("$message: decrypt") { VaultCrypto.decrypt(key, f, ByteArrayOutputStream()) }
        assertFalse("$message: the Reader read it all without complaint", readerAccepts(f))
    }

    /** Reads the whole file through the random-access Reader, as video playback would. */
    private fun readerAccepts(f: File): Boolean = try {
        VaultCrypto.Reader(key, f).use { r ->
            val buffer = ByteArray(64 * 1024)
            var position = 0L
            while (position < r.length) {
                val n = r.read(position, buffer, 0, buffer.size)
                if (n <= 0) break
                position += n
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    @Test
    fun swappingChunksIsCaught() {
        val raw = encrypt(bytes(3 * c))
        val body = bodyOffset(raw)
        val step = c + 16
        val swapped = raw.copyOf()
        System.arraycopy(raw, body, swapped, body + step, step)
        System.arraycopy(raw, body + step, swapped, body, step)
        assertRejected(swapped, "chunks 0 and 1 swapped")
    }

    @Test
    fun chunkFromAnotherFileIsCaught() {
        val a = encrypt(bytes(2 * c, seed = 1))
        val b = encrypt(bytes(2 * c, seed = 2))
        val mixed = a.copyOf()
        // Same key, same position, but B's chunk 0 was sealed under B's file id.
        System.arraycopy(b, bodyOffset(b), mixed, bodyOffset(a), c + 16)
        assertRejected(mixed, "chunk 0 taken from another file")
    }

    @Test
    fun droppingAMiddleChunkIsCaught() {
        val raw = encrypt(bytes(3 * c + 10))
        val body = bodyOffset(raw)
        val step = c + 16
        val cut = raw.copyOfRange(0, body + step) + raw.copyOfRange(body + 2 * step, raw.size)
        assertRejected(cut, "chunk 1 removed")
    }

    @Test
    fun appendingBytesIsCaught() {
        assertRejected(encrypt(bytes(1000)) + ByteArray(1), "one byte appended")
        assertRejected(encrypt(bytes(c)) + encrypt(bytes(c)).copyOfRange(0, c + 16), "a whole chunk appended")
    }

    @Test
    fun flippingTheFileIdIsCaught() {
        val raw = encrypt(bytes(100))
        raw[8] = (raw[8].toInt() xor 1).toByte()
        assertRejected(raw, "file id changed")
    }

    @Test
    fun tamperedHeaderIsCaught() {
        val raw = encrypt(bytes(100))
        raw[20 + 3] = (raw[20 + 3].toInt() xor 1).toByte()
        assertThrows("header bit flipped") { VaultCrypto.readHeader(key, file(raw)) }
    }

    @Test
    fun notAVaultFileIsRefused() {
        assertThrows("random bytes") { VaultCrypto.decrypt(key, file(bytes(500)), ByteArrayOutputStream()) }
        assertThrows("shorter than the magic") { VaultCrypto.readHeader(key, file(byteArrayOf(1, 2, 3))) }
        assertThrows("empty file") { VaultCrypto.Reader(key, file(ByteArray(0))).close() }
    }

    @Test
    fun negativeHeaderLengthIsRefused() {
        val raw = encrypt(bytes(100))
        ByteBuffer.wrap(raw, 16, 4).putInt(-1)
        assertThrows("negative header length") { VaultCrypto.readHeader(key, file(raw)) }
    }

    /**
     * Cutting a file back to just its header must not turn it into a valid empty file. decrypt()
     * catches it; the random-access Reader (used for video playback and frames) must too, rather
     * than reporting a negative length and an immediate end of file.
     */
    @Test
    fun strippingTheWholeBodyIsCaughtByTheReader() {
        val raw = encrypt(bytes(5000))
        val headerOnly = raw.copyOfRange(0, bodyOffset(raw))
        assertRejected(headerOnly, "body removed")
    }

    @Test
    fun cuttingIntoTheLastTagIsCaught() {
        val raw = encrypt(bytes(1000))
        assertRejected(raw.copyOf(raw.size - 5), "tag cut short")
    }

    /** Same attack, leaving less than one tag: the Reader would report a length of -12. */
    @Test
    fun leavingLessThanATagIsCaughtByTheReader() {
        val raw = encrypt(bytes(5000))
        assertRejected(raw.copyOfRange(0, bodyOffset(raw) + 4), "body cut to 4 bytes")
    }

    /**
     * The header (name, folder to restore to) must belong to its body. Swapping two files'
     * headers would make unhiding write one photo's content under the other's name and folder.
     */
    @Test
    fun headerFromAnotherFileIsCaught() {
        val a = encrypt(bytes(1000, seed = 1), "{\"name\":\"a.jpg\",\"path\":\"DCIM/Camera/\"}")
        val b = encrypt(bytes(1000, seed = 2), "{\"name\":\"b.jpg\",\"path\":\"Pictures/Private/\"}")
        // A's magic and id, B's header, A's body.
        val swapped = a.copyOfRange(0, 16) + b.copyOfRange(16, bodyOffset(b)) + a.copyOfRange(bodyOffset(a), a.size)
        val f = file(swapped)
        val headerOk = runCatching { VaultCrypto.readHeader(key, f) }.isSuccess
        val bodyOk = runCatching { VaultCrypto.decrypt(key, f, ByteArrayOutputStream()) }.isSuccess
        assertFalse("B's header was accepted on A's body", headerOk && bodyOk)
    }

    @Test
    fun sameContentEncryptsDifferentlyEachTime() {
        val plain = bytes(1000)
        assertFalse(encrypt(plain).contentEquals(encrypt(plain)))
    }

    @Test
    fun readerClampsReadsToTheEnd() {
        val plain = bytes(c + 100)
        VaultCrypto.Reader(key, file(encrypt(plain))).use { r ->
            val buffer = ByteArray(1000)
            val n = r.read(c + 50L, buffer, 0, 1000)
            assertEquals(50, n)
            assertArrayEquals(plain.copyOfRange(c + 50, c + 100), buffer.copyOf(n))
            assertEquals(-1, r.read(c + 100L, buffer, 0, 1))
            // Exactly at a chunk boundary, reading starts the next chunk.
            assertEquals(plain[c], ByteArray(1).also { r.read(c.toLong(), it, 0, 1) }[0])
        }
    }

    @Test
    fun exactlyOneChunkIsOneChunk() {
        val plain = bytes(c)
        val raw = encrypt(plain)
        assertEquals(bodyOffset(raw) + c + 16, raw.size)
        VaultCrypto.Reader(key, file(raw)).use { assertEquals(c.toLong(), it.length) }
    }

    private fun assertThrows(message: String, block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        fail("$message: was accepted")
    }

    /**
     * Files hidden by Lumen up to 0.4.2 are version 1: the same layout, the header bound to
     * nothing. They must keep opening after the format moved to version 2.
     */
    @Test
    fun versionOneFilesStillOpen() {
        val plain = bytes(1000)
        val header = "{\"name\":\"old.jpg\"}".toByteArray()
        val fileId = bytes(8, seed = 7)
        val sealedHeader = VaultCrypto.seal(key, header)
        val chunk = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
            val nonce = fileId + ByteBuffer.allocate(4).putInt(0).array()
            init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, nonce))
            updateAAD(byteArrayOf(1))
            doFinal(plain)
        }
        val raw = "LUMENVT1".toByteArray() + fileId + ByteBuffer.allocate(4).putInt(sealedHeader.size).array() + sealedHeader + chunk
        val f = file(raw)
        assertArrayEquals(header, VaultCrypto.readHeader(key, f))
        val out = ByteArrayOutputStream()
        VaultCrypto.decrypt(key, f, out)
        assertArrayEquals(plain, out.toByteArray())
        assertTrue(readerAccepts(f))
    }

    /** A version 2 file relabelled as version 1 must not shed its header's binding. */
    @Test
    fun relabellingAsVersionOneIsCaught() {
        val raw = encrypt(bytes(1000))
        "LUMENVT1".toByteArray().copyInto(raw)
        assertThrows("relabelled header") { VaultCrypto.readHeader(key, file(raw)) }
    }
}
