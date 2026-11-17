package com.example.domain.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class TrackSource {
    LOCAL,
    YOUTUBE,
    DIRECT_STREAM,
    EXTENSION
}

enum class SearchFilter(val label: String, val innerTubeParams: String) {
    SONGS("Songs", "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"),
    ALBUMS("Albums", "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D"),
    ARTISTS("Artists", "EgWKAQIgAWoKEAoQAxAEEAkQBQ%3D%3D")
}

@Serializable
enum class AudioQualityTier(val label: String) {
    HI_RES_LOSSLESS("Hi-Res Lossless"),
    CD_LOSSLESS("CD Lossless"),
    HIGH_ADAPTIVE("Studio High"),
    DATA_SAVER("Efficiency")
}

enum class PreferredStreamQuality(val label: String, val subtitle: String) {
    AUTO_BEST("Auto Best", "Hi-Res lossless on Wi-Fi, adaptive on cellular"),
    ALWAYS_HI_RES("Maximum Lossless", "Always pick highest FLAC / WAV / PCM tier"),
    BALANCED_HIGH("Studio High", "Cap at 320 kbps AAC / Opus"),
    DATA_SAVER("Efficiency", "Prefer lower bandwidth streams")
}

@Serializable
data class StreamVariant(
    val id: String,
    val label: String,
    val url: String,
    val codecLabel: String = "FLAC",
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int = 2,
    val tier: AudioQualityTier = AudioQualityTier.CD_LOSSLESS,
    val headers: Map<String, String> = emptyMap(),
    val decryptionSpec: BlowfishChunkSpec? = null
) {
    val specSummary: String
        get() = buildList {
            add(codecLabel)
            if (bitDepth != null && sampleRateHz != null) {
                add("${bitDepth}-BIT / ${formatSampleRateKhz(sampleRateHz)}")
            } else if (sampleRateHz != null) {
                add(formatSampleRateKhz(sampleRateHz))
            }
            if (bitrateKbps != null && bitrateKbps > 0) {
                add("$bitrateKbps KBPS")
            }
        }.joinToString(" • ")
}

fun formatSampleRateKhz(sampleRateHz: Int): String {
    if (sampleRateHz <= 0) return ""
    val khz = sampleRateHz / 1000.0
    return if (sampleRateHz % 1000 == 0) {
        "${sampleRateHz / 1000} kHz"
    } else {
        String.format("%.1f kHz", khz)
    }
}

private val variantJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

data class Track(
    val id: String,
    val source: TrackSource,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val contentUri: String? = null,
    val videoId: String? = null,
    val audioFormatLabel: String = when (source) {
        TrackSource.YOUTUBE -> "OPUS"
        TrackSource.DIRECT_STREAM -> "FLAC"
        TrackSource.EXTENSION -> "EXT"
        TrackSource.LOCAL -> "LOCAL"
    },
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val isLiveStream: Boolean = false,
    val variants: List<StreamVariant> = emptyList(),
    val activeVariantId: String? = null,
    val folderName: String? = null,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val extensionId: String? = null,
    val providerTrackId: String? = null,
    val isrc: String? = null,
    val isMetadataOnly: Boolean = false,
    val resolvedByExtensionId: String? = null,
    val sourceLabel: String? = null
) {
    val activeVariant: StreamVariant?
        get() = variants.firstOrNull { it.id == activeVariantId } ?: variants.firstOrNull()

    val isHiRes: Boolean
        get() {
            val sr = activeVariant?.sampleRateHz ?: sampleRateHz ?: 0
            val bd = activeVariant?.bitDepth ?: bitDepth ?: 0
            val codec = (activeVariant?.codecLabel ?: audioFormatLabel).uppercase()
            return bd >= 24 || sr > 48_000 || codec.contains("HI-RES") || codec.contains("DSD")
        }

    val isLossless: Boolean
        get() {
            val codec = (activeVariant?.codecLabel ?: audioFormatLabel).uppercase()
            return isHiRes ||
                codec.contains("FLAC") ||
                codec.contains("ALAC") ||
                codec.contains("WAV") ||
                codec.contains("PCM")
        }

    val qualityBadgeLabel: String
        get() {
            val codec = activeVariant?.codecLabel ?: audioFormatLabel
            val sr = activeVariant?.sampleRateHz ?: sampleRateHz
            val bd = activeVariant?.bitDepth ?: bitDepth
            return when {
                source == TrackSource.YOUTUBE -> "YT • $codec"
                source == TrackSource.EXTENSION -> {
                    val prefix = (sourceLabel ?: extensionId ?: "EXT").uppercase()
                    when {
                        bd != null && sr != null -> "$prefix • $codec ${bd}B/${formatSampleRateKhz(sr)}"
                        sr != null && sr > 48_000 -> "$prefix • $codec ${formatSampleRateKhz(sr)}"
                        isMetadataOnly && !resolvedByExtensionId.isNullOrBlank() ->
                            "$prefix → ${resolvedByExtensionId.uppercase()}"
                        else -> "$prefix • $codec"
                    }
                }
                bd != null && sr != null -> "$codec • ${bd}B/${formatSampleRateKhz(sr)}"
                sr != null && sr > 48_000 -> "$codec • ${formatSampleRateKhz(sr)}"
                else -> codec
            }
        }

    val playbackUriString: String
        get() = when (source) {
            TrackSource.LOCAL -> contentUri.orEmpty()
            TrackSource.YOUTUBE -> "$YOUTUBE_VIRTUAL_SCHEME${videoId.orEmpty()}"
            TrackSource.DIRECT_STREAM -> activeVariant?.url ?: contentUri.orEmpty()
            TrackSource.EXTENSION -> {
                val active = activeVariant
                when {
                    active != null &&
                        active.decryptionSpec == null &&
                        (active.url.startsWith("http://") || active.url.startsWith("https://")) ->
                        active.url
                    !contentUri.isNullOrBlank() &&
                        contentUri.startsWith(EXTENSION_VIRTUAL_SCHEME) &&
                        active == null ->
                        contentUri
                    else -> buildExtensionVirtualUri(
                        extensionId = extensionId.orEmpty(),
                        providerTrackId = providerTrackId ?: id,
                        isrc = isrc,
                        title = title,
                        artist = artist,
                        variantId = active?.id
                    )
                }
            }
        }

    fun toMediaItem(overrideUrl: String? = null): MediaItem {
        val serializedVariants = if (variants.isNotEmpty()) {
            runCatching { variantJson.encodeToString(variants) }.getOrNull()
        } else {
            null
        }
        val effectiveUri = overrideUrl ?: playbackUriString

        val extras = Bundle().apply {
            putString(EXTRA_TRACK_ID, id)
            putString(EXTRA_SOURCE, source.name)
            putString(EXTRA_VIDEO_ID, videoId)
            putString(EXTRA_FORMAT_LABEL, audioFormatLabel)
            putLong(EXTRA_DURATION_MS, durationMs)
            putString(EXTRA_FOLDER_NAME, folderName)
            putString(EXTRA_ARTWORK_URI, artworkUri)
            putString(EXTRA_CONTENT_URI, overrideUrl ?: contentUri)
            putInt(EXTRA_BITRATE_KBPS, bitrateKbps ?: 0)
            putInt(EXTRA_SAMPLE_RATE_HZ, sampleRateHz ?: 0)
            putInt(EXTRA_BIT_DEPTH, bitDepth ?: 0)
            putBoolean(EXTRA_IS_LIVE_STREAM, isLiveStream)
            putString(EXTRA_ACTIVE_VARIANT_ID, activeVariantId)
            putString(EXTRA_EXTENSION_ID, extensionId)
            putString(EXTRA_PROVIDER_TRACK_ID, providerTrackId)
            putString(EXTRA_ISRC, isrc)
            putBoolean(EXTRA_IS_METADATA_ONLY, isMetadataOnly)
            putString(EXTRA_RESOLVED_BY_EXT_ID, resolvedByExtensionId)
            putString(EXTRA_SOURCE_LABEL, sourceLabel)
            if (serializedVariants != null) {
                putString(EXTRA_VARIANTS_JSON, serializedVariants)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album.ifBlank { null })
            .setArtworkUri(artworkUri?.let { Uri.parse(it) })
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(effectiveUri)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        const val YOUTUBE_VIRTUAL_SCHEME = "sonora://yt/"
        const val YOUTUBE_HLS_SCHEME = "sonora://yt-hls/"
        const val EXTENSION_VIRTUAL_SCHEME = "sonora://ext/"
        private const val EXTRA_TRACK_ID = "sonora_track_id"
        private const val EXTRA_SOURCE = "sonora_source"
        private const val EXTRA_VIDEO_ID = "sonora_video_id"
        private const val EXTRA_FORMAT_LABEL = "sonora_format_label"
        private const val EXTRA_DURATION_MS = "sonora_duration_ms"
        private const val EXTRA_FOLDER_NAME = "sonora_folder_name"
        private const val EXTRA_ARTWORK_URI = "sonora_artwork_uri"
        private const val EXTRA_CONTENT_URI = "sonora_content_uri"
        private const val EXTRA_BITRATE_KBPS = "sonora_bitrate_kbps"
        private const val EXTRA_SAMPLE_RATE_HZ = "sonora_sample_rate_hz"
        private const val EXTRA_BIT_DEPTH = "sonora_bit_depth"
        private const val EXTRA_IS_LIVE_STREAM = "sonora_is_live_stream"
        private const val EXTRA_VARIANTS_JSON = "sonora_variants_json"
        private const val EXTRA_ACTIVE_VARIANT_ID = "sonora_active_variant_id"
        private const val EXTRA_EXTENSION_ID = "sonora_extension_id"
        private const val EXTRA_PROVIDER_TRACK_ID = "sonora_provider_track_id"
        private const val EXTRA_ISRC = "sonora_isrc"
        private const val EXTRA_IS_METADATA_ONLY = "sonora_is_metadata_only"
        private const val EXTRA_RESOLVED_BY_EXT_ID = "sonora_resolved_by_ext_id"
        private const val EXTRA_SOURCE_LABEL = "sonora_source_label"

        fun buildExtensionVirtualUri(
            extensionId: String,
            providerTrackId: String,
            isrc: String? = null,
            title: String? = null,
            artist: String? = null,
            variantId: String? = null
        ): String {
            val builder = Uri.Builder()
                .scheme("sonora")
                .authority("ext")
                .appendPath(extensionId)
                .appendPath(providerTrackId)
            if (!isrc.isNullOrBlank()) builder.appendQueryParameter("isrc", isrc)
            if (!title.isNullOrBlank()) builder.appendQueryParameter("title", title)
            if (!artist.isNullOrBlank()) builder.appendQueryParameter("artist", artist)
            if (!variantId.isNullOrBlank()) builder.appendQueryParameter("variant", variantId)
            return builder.build().toString()
        }

        fun encodeVariants(variants: List<StreamVariant>): String? {
            if (variants.isEmpty()) return null
            return runCatching { variantJson.encodeToString(variants) }.getOrNull()
        }

        fun decodeVariants(rawJson: String?): List<StreamVariant> {
            if (rawJson.isNullOrBlank()) return emptyList()
            return runCatching { variantJson.decodeFromString<List<StreamVariant>>(rawJson) }
                .getOrDefault(emptyList())
        }

        fun fromMediaItem(item: MediaItem): Track? {
            val meta = item.mediaMetadata
            val extras = meta.extras
            val sourceName = extras?.getString(EXTRA_SOURCE)
            val source = when (sourceName) {
                TrackSource.YOUTUBE.name -> TrackSource.YOUTUBE
                TrackSource.DIRECT_STREAM.name -> TrackSource.DIRECT_STREAM
                TrackSource.EXTENSION.name -> TrackSource.EXTENSION
                TrackSource.LOCAL.name -> TrackSource.LOCAL
                else -> when {
                    item.mediaId.startsWith("yt_") -> TrackSource.YOUTUBE
                    item.mediaId.startsWith("stream_") -> TrackSource.DIRECT_STREAM
                    item.mediaId.startsWith("ext_") -> TrackSource.EXTENSION
                    else -> TrackSource.LOCAL
                }
            }
            val videoId = extras?.getString(EXTRA_VIDEO_ID)
                ?: item.localConfiguration?.uri?.toString()?.let { uriStr ->
                    when {
                        uriStr.startsWith(YOUTUBE_VIRTUAL_SCHEME) ->
                            uriStr.removePrefix(YOUTUBE_VIRTUAL_SCHEME).substringBefore("?")
                        uriStr.startsWith(YOUTUBE_HLS_SCHEME) ->
                            uriStr.removePrefix(YOUTUBE_HLS_SCHEME).substringBefore("?")
                        else -> null
                    }
                }
            val bitrate = extras?.getInt(EXTRA_BITRATE_KBPS)?.takeIf { it > 0 }
            val sampleRate = extras?.getInt(EXTRA_SAMPLE_RATE_HZ)?.takeIf { it > 0 }
            val bitDepth = extras?.getInt(EXTRA_BIT_DEPTH)?.takeIf { it > 0 }
            val isLive = extras?.getBoolean(EXTRA_IS_LIVE_STREAM, false) ?: false
            val variants = decodeVariants(extras?.getString(EXTRA_VARIANTS_JSON))
            val activeVariantId = extras?.getString(EXTRA_ACTIVE_VARIANT_ID)

            return Track(
                id = extras?.getString(EXTRA_TRACK_ID) ?: item.mediaId,
                source = source,
                title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Track",
                artist = meta.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                album = meta.albumTitle?.toString().orEmpty(),
                durationMs = extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
                artworkUri = extras?.getString(EXTRA_ARTWORK_URI) ?: meta.artworkUri?.toString(),
                contentUri = extras?.getString(EXTRA_CONTENT_URI)
                    ?: item.localConfiguration?.uri?.toString(),
                videoId = videoId,
                audioFormatLabel = extras?.getString(EXTRA_FORMAT_LABEL)
                    ?: when (source) {
                        TrackSource.YOUTUBE -> "OPUS"
                        TrackSource.DIRECT_STREAM -> "FLAC"
                        TrackSource.EXTENSION -> "EXT"
                        TrackSource.LOCAL -> "LOCAL"
                    },
                bitrateKbps = bitrate,
                sampleRateHz = sampleRate,
                bitDepth = bitDepth,
                isLiveStream = isLive,
                variants = variants,
                activeVariantId = activeVariantId,
                folderName = extras?.getString(EXTRA_FOLDER_NAME),
                extensionId = extras?.getString(EXTRA_EXTENSION_ID),
                providerTrackId = extras?.getString(EXTRA_PROVIDER_TRACK_ID),
                isrc = extras?.getString(EXTRA_ISRC),
                isMetadataOnly = extras?.getBoolean(EXTRA_IS_METADATA_ONLY, false) ?: false,
                resolvedByExtensionId = extras?.getString(EXTRA_RESOLVED_BY_EXT_ID),
                sourceLabel = extras?.getString(EXTRA_SOURCE_LABEL)
            )
        }
    }
}
