package com.example.data.streams

import com.example.domain.model.AudioQualityTier
import com.example.domain.model.StreamVariant
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProbedAudioHeader(
    val codecLabel: String,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val bitrateKbps: Int?,
    val channelCount: Int = 2,
    val icyName: String? = null,
    val icyGenre: String? = null,
    val isLiveStream: Boolean = false
)

/**
 * Probes direct audio URLs (.flac, .wav, .alac, .ogg, .aac, .m3u, .pls, or Icecast/Shoutcast)
 * to extract container/codec format, sample rate (up to 192kHz+), bit depth, and ICY station metadata.
 */
class DirectStreamProber(
    private val okHttpClient: OkHttpClient
) {
    suspend fun probeStreamTrack(
        primaryUrl: String,
        customTitle: String? = null,
        fallbackUrl: String? = null,
        artistLabel: String? = null
    ): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPrimary = primaryUrl.trim()
            require(cleanPrimary.startsWith("http://") || cleanPrimary.startsWith("https://")) {
                "Enter a valid http:// or https:// stream URL"
            }

            val resolvedPrimary = expandPlaylistIfNeeded(cleanPrimary)
            val probedPrimary = probeSingleUrl(resolvedPrimary)

            val tier = classifyTier(
                codecLabel = probedPrimary.codecLabel,
                sampleRateHz = probedPrimary.sampleRateHz,
                bitDepth = probedPrimary.bitDepth,
                bitrateKbps = probedPrimary.bitrateKbps
            )

            val variants = mutableListOf(
                StreamVariant(
                    id = "primary_${resolvedPrimary.hashCode()}",
                    label = buildVariantLabel(probedPrimary, tier),
                    url = resolvedPrimary,
                    codecLabel = probedPrimary.codecLabel,
                    bitrateKbps = probedPrimary.bitrateKbps,
                    sampleRateHz = probedPrimary.sampleRateHz,
                    bitDepth = probedPrimary.bitDepth,
                    channelCount = probedPrimary.channelCount,
                    tier = tier
                )
            )

            val cleanFallback = fallbackUrl?.trim()?.takeIf {
                it.startsWith("http://") || it.startsWith("https://")
            }
            if (cleanFallback != null) {
                val resolvedFallback = expandPlaylistIfNeeded(cleanFallback)
                val probedFallback = probeSingleUrl(resolvedFallback)
                val fbTier = classifyTier(
                    codecLabel = probedFallback.codecLabel,
                    sampleRateHz = probedFallback.sampleRateHz,
                    bitDepth = probedFallback.bitDepth,
                    bitrateKbps = probedFallback.bitrateKbps
                )
                variants.add(
                    StreamVariant(
                        id = "fallback_${resolvedFallback.hashCode()}",
                        label = buildVariantLabel(probedFallback, fbTier),
                        url = resolvedFallback,
                        codecLabel = probedFallback.codecLabel,
                        bitrateKbps = probedFallback.bitrateKbps,
                        sampleRateHz = probedFallback.sampleRateHz,
                        bitDepth = probedFallback.bitDepth,
                        channelCount = probedFallback.channelCount,
                        tier = fbTier
                    )
                )
            }

            val inferredTitle = customTitle?.takeIf { it.isNotBlank() }
                ?: probedPrimary.icyName?.takeIf { it.isNotBlank() }
                ?: resolvedPrimary.substringAfterLast('/').substringBefore('?').ifBlank { "Hi-Res Stream" }

            val inferredArtist = artistLabel?.takeIf { it.isNotBlank() }
                ?: probedPrimary.icyGenre?.takeIf { it.isNotBlank() }
                ?: if (probedPrimary.isLiveStream) "Live Lossless Radio" else "Direct Lossless URL"

            val primaryVariant = variants.first()
            Track(
                id = "stream_${resolvedPrimary.hashCode().toUInt()}",
                source = TrackSource.DIRECT_STREAM,
                title = inferredTitle,
                artist = inferredArtist,
                album = if (probedPrimary.isLiveStream) "Hi-Res Radio" else "Direct Stream",
                durationMs = 0L,
                contentUri = resolvedPrimary,
                audioFormatLabel = primaryVariant.codecLabel,
                bitrateKbps = primaryVariant.bitrateKbps,
                sampleRateHz = primaryVariant.sampleRateHz,
                bitDepth = primaryVariant.bitDepth,
                isLiveStream = probedPrimary.isLiveStream,
                variants = variants,
                activeVariantId = primaryVariant.id,
                folderName = "Direct Hi-Res"
            )
        }
    }

    private fun expandPlaylistIfNeeded(url: String): String {
        val lower = url.substringBefore('?').lowercase(Locale.US)
        if (!lower.endsWith(".pls") && !lower.endsWith(".m3u")) {
            return url
        }
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use url
                val body = resp.body?.string().orEmpty()
                if (lower.endsWith(".pls")) {
                    parsePlsEntries(body).firstOrNull() ?: url
                } else {
                    parseM3uEntries(body).firstOrNull() ?: url
                }
            }
        }.getOrDefault(url)
    }

    private fun probeSingleUrl(url: String): ProbedAudioHeader {
        val req = Request.Builder()
            .url(url)
            .header("Icy-MetaData", "1")
            .header("Range", "bytes=0-4095")
            .get()
            .build()

        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type")?.lowercase(Locale.US).orEmpty()
                val icyName = resp.header("icy-name")?.trim()
                val icyGenre = resp.header("icy-genre")?.trim()
                val icyBr = resp.header("icy-br")?.toIntOrNull()
                val icySr = resp.header("icy-sr")?.toIntOrNull()
                val contentLength = resp.header("Content-Length")?.toLongOrNull()
                val isLive = icyName != null || resp.header("icy-metaint") != null ||
                    (contentLength == null && (contentType.contains("ogg") || contentType.contains("flac") || contentType.contains("aac")))

                val bytes = resp.body?.byteStream()?.let { stream ->
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read <= 0) break
                        total += read
                        // Stop early once we have enough header bytes for FLAC/WAV inspection
                        if (total >= 64) break
                    }
                    buffer.copyOf(total)
                } ?: ByteArray(0)

                val flacInfo = parseFlacStreamInfo(bytes)
                val wavInfo = if (flacInfo == null) parseWavHeader(bytes) else null
                val headerDetected = flacInfo ?: wavInfo

                val codec = when {
                    flacInfo != null || contentType.contains("flac") || url.contains("flac", ignoreCase = true) -> "FLAC"
                    wavInfo != null || contentType.contains("wav") || url.contains(".wav", ignoreCase = true) -> "WAV"
                    url.contains("alac", ignoreCase = true) -> "ALAC"
                    contentType.contains("opus") || url.contains("opus", ignoreCase = true) -> "OPUS"
                    contentType.contains("ogg") || url.contains(".ogg", ignoreCase = true) -> "OGG"
                    contentType.contains("aac") || contentType.contains("mp4") || url.contains("aac", ignoreCase = true) -> "AAC"
                    contentType.contains("mpeg") || url.contains(".mp3", ignoreCase = true) -> "MP3"
                    else -> "AUDIO"
                }

                val sampleRate = headerDetected?.sampleRateHz ?: icySr ?: if (codec == "FLAC") 44_100 else null
                val bitDepth = headerDetected?.bitDepth ?: if (codec == "FLAC" || codec == "WAV" || codec == "ALAC") 16 else null
                val estBitrate = headerDetected?.bitrateKbps ?: icyBr ?: when (codec) {
                    "FLAC" -> if ((sampleRate ?: 44_100) > 48_000) 2800 else 1411
                    "WAV" -> 1411
                    else -> null
                }

                ProbedAudioHeader(
                    codecLabel = codec,
                    sampleRateHz = sampleRate,
                    bitDepth = bitDepth,
                    bitrateKbps = estBitrate,
                    channelCount = headerDetected?.channelCount ?: 2,
                    icyName = icyName,
                    icyGenre = icyGenre,
                    isLiveStream = isLive
                )
            }
        }.getOrElse {
            inferFromUrl(url)
        }
    }

    private fun inferFromUrl(url: String): ProbedAudioHeader {
        val lower = url.lowercase(Locale.US)
        val codec = when {
            lower.contains("flac") -> "FLAC"
            lower.contains(".wav") -> "WAV"
            lower.contains("alac") -> "ALAC"
            lower.contains("opus") -> "OPUS"
            lower.contains("aac") -> "AAC"
            else -> "STREAM"
        }
        return ProbedAudioHeader(
            codecLabel = codec,
            sampleRateHz = if (codec == "FLAC" || codec == "WAV") 44_100 else null,
            bitDepth = if (codec == "FLAC" || codec == "WAV") 16 else null,
            bitrateKbps = null,
            isLiveStream = true
        )
    }

    companion object {
        fun parsePlsEntries(body: String): List<String> {
            return body.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("File", ignoreCase = true) && it.contains('=') }
                .map { it.substringAfter('=').trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .toList()
        }

        fun parseM3uEntries(body: String): List<String> {
            return body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .toList()
        }

        /**
         * Parses standard FLAC signature `fLaC` + STREAMINFO metadata block.
         */
        fun parseFlacStreamInfo(bytes: ByteArray): ProbedAudioHeader? {
            if (bytes.size < 26) return null
            if (bytes[0] != 'f'.code.toByte() ||
                bytes[1] != 'L'.code.toByte() ||
                bytes[2] != 'a'.code.toByte() ||
                bytes[3] != 'C'.code.toByte()
            ) {
                return null
            }
            val blockType = bytes[4].toInt() and 0x7F
            if (blockType != 0) return null // First metadata block must be STREAMINFO (0)

            // STREAMINFO payload begins at byte 8; sample rate + channels + bps start at offset 18
            val b0 = bytes[18].toInt() and 0xFF
            val b1 = bytes[19].toInt() and 0xFF
            val b2 = bytes[20].toInt() and 0xFF
            val b3 = bytes[21].toInt() and 0xFF

            val sampleRate = (b0 shl 12) or (b1 shl 4) or (b2 ushr 4)
            val channels = ((b2 ushr 1) and 0x07) + 1
            val bitDepth = (((b2 and 0x01) shl 4) or (b3 ushr 4)) + 1

            if (sampleRate < 8_000 || sampleRate > 384_000) return null
            val estBitrateKbps = ((sampleRate.toLong() * channels * bitDepth * 65L) / 100_000L).toInt()
                .coerceAtLeast(320)

            return ProbedAudioHeader(
                codecLabel = "FLAC",
                sampleRateHz = sampleRate,
                bitDepth = bitDepth,
                bitrateKbps = estBitrateKbps,
                channelCount = channels
            )
        }

        /**
         * Parses RIFF WAVE `fmt ` header for PCM/Float WAV streams.
         */
        fun parseWavHeader(bytes: ByteArray): ProbedAudioHeader? {
            if (bytes.size < 44) return null
            if (bytes[0] != 'R'.code.toByte() ||
                bytes[1] != 'I'.code.toByte() ||
                bytes[2] != 'F'.code.toByte() ||
                bytes[3] != 'F'.code.toByte() ||
                bytes[8] != 'W'.code.toByte() ||
                bytes[9] != 'A'.code.toByte() ||
                bytes[10] != 'V'.code.toByte() ||
                bytes[11] != 'E'.code.toByte()
            ) {
                return null
            }

            var pos = 12
            while (pos + 8 <= bytes.size) {
                val idMatchesFmt = bytes[pos] == 'f'.code.toByte() &&
                    bytes[pos + 1] == 'm'.code.toByte() &&
                    bytes[pos + 2] == 't'.code.toByte() &&
                    bytes[pos + 3] == ' '.code.toByte()
                val chunkSize = readLeInt(bytes, pos + 4)
                val dataOffset = pos + 8
                if (idMatchesFmt && dataOffset + 16 <= bytes.size) {
                    val audioFormat = readLeShort(bytes, dataOffset)
                    val channels = readLeShort(bytes, dataOffset + 2)
                    val sampleRate = readLeInt(bytes, dataOffset + 4)
                    val bitsPerSample = readLeShort(bytes, dataOffset + 14)
                    if (sampleRate in 8_000..384_000) {
                        val codec = if (audioFormat == 3) "PCM FLOAT" else "WAV"
                        val kbps = ((sampleRate.toLong() * channels * bitsPerSample) / 1000L).toInt()
                        return ProbedAudioHeader(
                            codecLabel = codec,
                            sampleRateHz = sampleRate,
                            bitDepth = bitsPerSample,
                            bitrateKbps = kbps,
                            channelCount = channels.coerceAtLeast(1)
                        )
                    }
                    return null
                }
                if (chunkSize <= 0) break
                pos += 8 + chunkSize + (chunkSize and 1)
            }
            return null
        }

        private fun readLeShort(b: ByteArray, offset: Int): Int {
            return (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
        }

        private fun readLeInt(b: ByteArray, offset: Int): Int {
            return (b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8) or
                ((b[offset + 2].toInt() and 0xFF) shl 16) or
                ((b[offset + 3].toInt() and 0xFF) shl 24)
        }

        fun classifyTier(
            codecLabel: String,
            sampleRateHz: Int?,
            bitDepth: Int?,
            bitrateKbps: Int?
        ): AudioQualityTier {
            val sr = sampleRateHz ?: 44_100
            val bd = bitDepth ?: 16
            val upper = codecLabel.uppercase(Locale.US)
            return when {
                (upper.contains("FLAC") || upper.contains("WAV") || upper.contains("ALAC") || upper.contains("PCM")) &&
                    (bd >= 24 || sr > 48_000) -> AudioQualityTier.HI_RES_LOSSLESS
                upper.contains("FLAC") || upper.contains("WAV") || upper.contains("ALAC") || upper.contains("PCM") ->
                    AudioQualityTier.CD_LOSSLESS
                (bitrateKbps ?: 192) >= 220 -> AudioQualityTier.HIGH_ADAPTIVE
                else -> AudioQualityTier.DATA_SAVER
            }
        }

        private fun buildVariantLabel(header: ProbedAudioHeader, tier: AudioQualityTier): String {
            val prefix = tier.label
            val ratePart = header.sampleRateHz?.let { " • ${com.example.domain.model.formatSampleRateKhz(it)}" }.orEmpty()
            val bitPart = header.bitDepth?.let { " ${it}-bit" }.orEmpty()
            return "$prefix (${header.codecLabel}$bitPart$ratePart)"
        }
    }
}
