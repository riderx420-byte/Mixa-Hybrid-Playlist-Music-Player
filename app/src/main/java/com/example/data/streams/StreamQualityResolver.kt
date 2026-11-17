package com.example.data.streams

import com.example.domain.model.AudioQualityTier
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.StreamVariant
import com.example.domain.model.Track
import com.example.domain.model.TrackSource

object StreamQualityResolver {

    fun tierRank(tier: AudioQualityTier): Int = when (tier) {
        AudioQualityTier.HI_RES_LOSSLESS -> 4
        AudioQualityTier.CD_LOSSLESS -> 3
        AudioQualityTier.HIGH_ADAPTIVE -> 2
        AudioQualityTier.DATA_SAVER -> 1
    }

    fun variantScore(variant: StreamVariant): Long {
        val tierWeight = tierRank(variant.tier) * 100_000_000L
        val depthWeight = (variant.bitDepth ?: 16) * 1_000_000L
        val srWeight = (variant.sampleRateHz ?: 44_100) * 10L
        val brWeight = (variant.bitrateKbps ?: 0).toLong()
        return tierWeight + depthWeight + srWeight + brWeight
    }

    fun selectBestVariant(
        variants: List<StreamVariant>,
        isUnmeteredWifi: Boolean,
        preference: PreferredStreamQuality
    ): StreamVariant? {
        if (variants.isEmpty()) return null
        val sortedDesc = variants.sortedByDescending { variantScore(it) }

        return when (preference) {
            PreferredStreamQuality.ALWAYS_HI_RES -> sortedDesc.first()
            PreferredStreamQuality.AUTO_BEST -> {
                if (isUnmeteredWifi) {
                    sortedDesc.first()
                } else {
                    sortedDesc.firstOrNull {
                        it.tier == AudioQualityTier.HIGH_ADAPTIVE || it.tier == AudioQualityTier.DATA_SAVER
                    } ?: sortedDesc.last()
                }
            }
            PreferredStreamQuality.BALANCED_HIGH -> {
                sortedDesc.firstOrNull {
                    it.tier == AudioQualityTier.HIGH_ADAPTIVE
                } ?: sortedDesc.firstOrNull {
                    it.tier == AudioQualityTier.CD_LOSSLESS
                } ?: sortedDesc.first()
            }
            PreferredStreamQuality.DATA_SAVER -> {
                sortedDesc.last()
            }
        }
    }

    fun applyPolicyToTrack(
        track: Track,
        isUnmeteredWifi: Boolean,
        preference: PreferredStreamQuality,
        explicitVariantId: String? = null
    ): Track {
        if ((track.source != TrackSource.DIRECT_STREAM && track.source != TrackSource.EXTENSION) ||
            track.variants.isEmpty()
        ) {
            return track
        }
        val chosen = explicitVariantId?.let { vid ->
            track.variants.firstOrNull { it.id == vid }
        } ?: selectBestVariant(track.variants, isUnmeteredWifi, preference)
            ?: return track

        return track.copy(
            contentUri = chosen.url,
            audioFormatLabel = chosen.codecLabel,
            bitrateKbps = chosen.bitrateKbps ?: track.bitrateKbps,
            sampleRateHz = chosen.sampleRateHz ?: track.sampleRateHz,
            bitDepth = chosen.bitDepth ?: track.bitDepth,
            activeVariantId = chosen.id
        )
    }

    fun downgradeVariant(track: Track): StreamVariant? {
        if (track.variants.size <= 1) return null
        val current = track.activeVariant ?: track.variants.first()
        val sortedDesc = track.variants.sortedByDescending { variantScore(it) }
        val idx = sortedDesc.indexOfFirst { it.id == current.id }
        return if (idx >= 0 && idx < sortedDesc.lastIndex) {
            sortedDesc[idx + 1]
        } else {
            null
        }
    }
}
