package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.domain.model.BlowfishChunkSpec
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * CustomData metadata attached to a Media3 DataSpec when an extension stream requires
 * Blowfish CBC 2048-byte chunk decryption (e.g., encrypted Deezer CDN blocks).
 */
data class BlowfishStreamSpecMeta(
    val spec: BlowfishChunkSpec
)

/**
 * Media3 DataSource wrapper decrypting Blowfish/CBC/NoPadding 2048-byte chunks on the fly
 * (decrypting every chunk where `chunkIndex % encryptedChunkModulo == 0` when a full chunk
 * is read, passing intermediate chunks and trailing partial EOF bytes plaintext).
 * Supports byte-accurate seeking by aligning upstream reads to chunk boundaries.
 */
@OptIn(UnstableApi::class)
class BlowfishChunkDataSource(
    private val upstream: DataSource,
    private val chunkSpec: BlowfishChunkSpec
) : BaseDataSource(/* isNetwork = */ true) {

    private val keyBytes = hexToBytes(chunkSpec.keyHex)
    private val chunkSize = chunkSpec.chunkSizeBytes.coerceAtLeast(8)
    private val modulo = chunkSpec.encryptedChunkModulo.coerceAtLeast(1)

    private var rootSpec: DataSpec? = null
    private var logicalPos: Long = 0L
    private var remainingLength: Long = C.LENGTH_UNSET.toLong()
    private var opened: Boolean = false

    private val rawChunkBuffer = ByteArray(chunkSize)
    private var decodedChunkBuffer = ByteArray(chunkSize)
    private var decodedChunkOffset = 0
    private var decodedChunkValidLen = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        rootSpec = dataSpec
        logicalPos = dataSpec.position.coerceAtLeast(0L)
        remainingLength = dataSpec.length

        val alignedStart = (logicalPos / chunkSize) * chunkSize
        val skipWithinFirstChunk = (logicalPos - alignedStart).toInt()
        val adjustedLength = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length + skipWithinFirstChunk
        } else {
            C.LENGTH_UNSET.toLong()
        }

        val alignedSpec = dataSpec.buildUpon()
            .setPosition(alignedStart)
            .setLength(adjustedLength)
            .build()

        val upstreamLen = upstream.open(alignedSpec)
        opened = true
        decodedChunkOffset = 0
        decodedChunkValidLen = 0

        if (skipWithinFirstChunk > 0) {
            if (!fillNextDecryptedChunk(alignedStart)) {
                transferStarted(dataSpec)
                return 0L
            }
            decodedChunkOffset = min(skipWithinFirstChunk, decodedChunkValidLen)
        }

        transferStarted(dataSpec)
        return when {
            remainingLength != C.LENGTH_UNSET.toLong() -> remainingLength
            upstreamLen != C.LENGTH_UNSET.toLong() -> (upstreamLen - skipWithinFirstChunk).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remainingLength == 0L) return C.RESULT_END_OF_INPUT

        if (decodedChunkOffset >= decodedChunkValidLen) {
            val chunkStartByte = (logicalPos / chunkSize) * chunkSize
            if (!fillNextDecryptedChunk(chunkStartByte)) {
                return C.RESULT_END_OF_INPUT
            }
        }

        val available = decodedChunkValidLen - decodedChunkOffset
        if (available <= 0) return C.RESULT_END_OF_INPUT

        var toCopy = min(length, available)
        if (remainingLength != C.LENGTH_UNSET.toLong()) {
            toCopy = min(toCopy.toLong(), remainingLength).toInt()
        }
        if (toCopy <= 0) return C.RESULT_END_OF_INPUT

        System.arraycopy(decodedChunkBuffer, decodedChunkOffset, buffer, offset, toCopy)
        decodedChunkOffset += toCopy
        logicalPos += toCopy
        if (remainingLength != C.LENGTH_UNSET.toLong()) {
            remainingLength -= toCopy
        }
        bytesTransferred(toCopy)
        return toCopy
    }

    private fun fillNextDecryptedChunk(chunkStartByte: Long): Boolean {
        var totalRead = 0
        while (totalRead < chunkSize) {
            val n = upstream.read(rawChunkBuffer, totalRead, chunkSize - totalRead)
            if (n == C.RESULT_END_OF_INPUT) break
            if (n > 0) totalRead += n
        }
        if (totalRead == 0) {
            decodedChunkOffset = 0
            decodedChunkValidLen = 0
            return false
        }

        val chunkIndex = chunkStartByte / chunkSize
        val shouldDecrypt = (chunkIndex % modulo == 0L) && (totalRead == chunkSize)
        if (shouldDecrypt) {
            decodedChunkBuffer = decryptChunk(rawChunkBuffer, keyBytes)
        } else {
            System.arraycopy(rawChunkBuffer, 0, decodedChunkBuffer, 0, totalRead)
        }
        decodedChunkOffset = 0
        decodedChunkValidLen = totalRead
        return true
    }

    override fun getUri(): Uri? = upstream.uri ?: rootSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        rootSpec = null
        decodedChunkOffset = 0
        decodedChunkValidLen = 0
        if (opened) {
            opened = false
            runCatching { upstream.close() }
            transferEnded()
        }
    }

    companion object {
        private val DEEZER_IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

        fun decryptChunk(encryptedChunk: ByteArray, keyBytes: ByteArray): ByteArray {
            if (keyBytes.size != 16) {
                throw IOException("Invalid Blowfish chunk key length: ${keyBytes.size} bytes")
            }
            val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "Blowfish"), IvParameterSpec(DEEZER_IV))
            return cipher.doFinal(encryptedChunk)
        }

        fun encryptChunkForTest(plainChunk: ByteArray, keyBytes: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "Blowfish"), IvParameterSpec(DEEZER_IV))
            return cipher.doFinal(plainChunk)
        }

        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            if (clean.length % 2 != 0) throw IllegalArgumentException("Malformed hex key")
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        /**
         * Derives standard 16-byte Blowfish track decryption key from trackId + 16-char secret salt.
         */
        fun deriveTrackKeyBytes(trackId: String, secretSalt: String): ByteArray {
            val md5Hex = MessageDigest.getInstance("MD5")
                .digest(trackId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val keyAscii = buildString(16) {
                for (i in 0 until 16) {
                    val code = md5Hex[i].code xor md5Hex[i + 16].code xor secretSalt[i % secretSalt.length].code
                    append(code.toChar())
                }
            }
            return keyAscii.toByteArray(Charsets.ISO_8859_1)
        }
    }
}
