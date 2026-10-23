package com.subhashh.lumen.data.vault

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The vault's file format. Each file is encrypted on its own with AES-256-GCM under the vault's
 * master key, in fixed-size chunks so any part can be read without decrypting the rest (videos
 * seek, thumbnails read just what they need). A file's own details (name, type, dates, size)
 * travel inside it, encrypted, so the vault folder reveals nothing but random names and sizes.
 *
 * Layout:
 *   "LUMENVT1"                      8 bytes, magic + format version
 *   file id                         8 random bytes; with the chunk number it makes each nonce
 *   header length                   4 bytes, big-endian
 *   header                          12-byte nonce + AES-GCM(JSON details) + 16-byte tag
 *   chunk 0, 1, …                   AES-GCM(up to ChunkSize bytes) + 16-byte tag each
 *
 * A chunk's nonce is the file id plus its number, and its associated data says whether it's the
 * last one, so chunks can't be swapped, reordered or cut off without the tag catching it.
 */
object VaultCrypto {

    const val ChunkSize = 256 * 1024
    private const val TagBytes = 16
    private const val NonceBytes = 12
    private val Magic = "LUMENVT1".toByteArray()
    private val random = SecureRandom()

    /** A new random 256-bit key. */
    fun newKey(): SecretKey = SecretKeySpec(ByteArray(32).also(random::nextBytes), "AES")

    /** Encrypts [input] with its [header] details into [output]. */
    fun encrypt(key: SecretKey, header: ByteArray, input: InputStream, output: OutputStream) {
        val fileId = ByteArray(8).also(random::nextBytes)
        output.write(Magic)
        output.write(fileId)
        val sealedHeader = seal(key, header)
        output.write(ByteBuffer.allocate(4).putInt(sealedHeader.size).array())
        output.write(sealedHeader)

        // Read one chunk ahead, so the last chunk is known when it's written.
        var index = 0
        var current = readChunk(input)
        while (true) {
            val next = if (current.size == ChunkSize) readChunk(input) else ByteArray(0)
            val last = next.isEmpty()
            output.write(chunkCipher(key, Cipher.ENCRYPT_MODE, fileId, index, last).doFinal(current))
            if (last) break
            current = next
            index++
        }
    }

    /** Reads just the encrypted details at the start of [file]. */
    fun readHeader(key: SecretKey, file: File): ByteArray = RandomAccessFile(file, "r").use { raf ->
        val layout = Layout.read(raf)
        val sealed = ByteArray(layout.headerLength)
        raf.seek(layout.headerOffset)
        raf.readFully(sealed)
        open(key, sealed)
    }

    /** Decrypts the whole of [file] into [output], checking every chunk. */
    fun decrypt(key: SecretKey, file: File, output: OutputStream) {
        RandomAccessFile(file, "r").use { raf ->
            val layout = Layout.read(raf)
            for (index in 0 until layout.chunkCount) output.write(layout.decryptChunk(raf, key, index))
        }
    }

    /** Random access to the plaintext of an encrypted file, chunk by chunk. */
    class Reader(private val key: SecretKey, file: File) : AutoCloseable {
        private val raf = RandomAccessFile(file, "r")
        private val layout = Layout.read(raf)
        private var cachedIndex = -1
        private var cached = ByteArray(0)

        /** Size of the decrypted content, bytes. */
        val length: Long = layout.plainLength

        /** Reads up to [count] bytes at [position] into [into]; -1 at the end. */
        fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int {
            if (position >= length) return -1
            val index = (position / ChunkSize).toInt()
            if (index != cachedIndex) {
                cached = layout.decryptChunk(raf, key, index)
                cachedIndex = index
            }
            val within = (position - index.toLong() * ChunkSize).toInt()
            val n = minOf(count, cached.size - within)
            System.arraycopy(cached, within, into, offset, n)
            return n
        }

        override fun close() = raf.close()
    }

    /** Where things are in an encrypted file, from its first bytes and its length. */
    private class Layout(
        val fileId: ByteArray,
        val headerOffset: Long,
        val headerLength: Int,
        val bodyOffset: Long,
        val bodyLength: Long,
    ) {
        val chunkCount: Int = ((bodyLength + ChunkSize + TagBytes - 1) / (ChunkSize + TagBytes)).toInt().coerceAtLeast(1)
        val plainLength: Long = bodyLength - chunkCount.toLong() * TagBytes

        fun decryptChunk(raf: RandomAccessFile, key: SecretKey, index: Int): ByteArray {
            require(index in 0 until chunkCount) { "No chunk $index" }
            val start = bodyOffset + index.toLong() * (ChunkSize + TagBytes)
            val size = minOf(ChunkSize + TagBytes.toLong(), bodyOffset + bodyLength - start).toInt()
            val sealed = ByteArray(size)
            raf.seek(start)
            raf.readFully(sealed)
            return chunkCipher(key, Cipher.DECRYPT_MODE, fileId, index, index == chunkCount - 1).doFinal(sealed)
        }

        companion object {
            fun read(raf: RandomAccessFile): Layout {
                val magic = ByteArray(Magic.size)
                raf.seek(0)
                raf.readFully(magic)
                require(magic.contentEquals(Magic)) { "Not a Lumen vault file" }
                val fileId = ByteArray(8).also(raf::readFully)
                val headerLength = raf.readInt()
                val headerOffset = raf.filePointer
                val bodyOffset = headerOffset + headerLength
                return Layout(fileId, headerOffset, headerLength, bodyOffset, raf.length() - bodyOffset)
            }
        }
    }

    /** AES-GCM with a random nonce stored in front: for small things (headers, wrapped keys). */
    fun seal(key: SecretKey, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NonceBytes).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TagBytes * 8, nonce))
        }
        return nonce + cipher.doFinal(plain)
    }

    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBytes * 8, sealed, 0, NonceBytes))
        }
        return cipher.doFinal(sealed, NonceBytes, sealed.size - NonceBytes)
    }

    private fun chunkCipher(key: SecretKey, mode: Int, fileId: ByteArray, index: Int, last: Boolean): Cipher {
        val nonce = fileId + ByteBuffer.allocate(4).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TagBytes * 8, nonce))
            // The last chunk says so: cutting a file short makes its new last chunk fail.
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }
    }

    private fun readChunk(input: InputStream): ByteArray {
        val buffer = ByteArray(ChunkSize)
        var filled = 0
        while (filled < ChunkSize) {
            val n = input.read(buffer, filled, ChunkSize - filled)
            if (n < 0) break
            filled += n
        }
        return if (filled == ChunkSize) buffer else buffer.copyOf(filled)
    }
}
