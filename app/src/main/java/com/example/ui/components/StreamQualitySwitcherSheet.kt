package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioQualityTier
import com.example.domain.model.PlaybackState
import com.example.domain.model.TrackSource
import com.example.domain.model.formatSampleRateKhz
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamQualitySwitcherSheet(
    playbackState: PlaybackState,
    directFloatPcmBypassEq: Boolean,
    isEqBypassedNow: Boolean,
    onDismiss: () -> Unit,
    onSelectVariant: (String) -> Unit,
    onToggleDirectPcmBypass: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val track = playbackState.currentTrack ?: return
    val telemetry = playbackState.audioTelemetry

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("stream_quality_switcher_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = if (track.isHiRes) HiResGoldAccent else WaveformTealPlayed
                    )
                    Column {
                        Text(
                            text = "Stream Quality & Float PCM",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = {
                    MixaHaptics.tap(view)
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close switcher")
                }
            }

            // Live 32-Bit Float PCM Audio Engine Telemetry Card
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = WaveformTealPlayed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MEDIA3 AUDIO OUTPUT",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = WaveformTealPlayed
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WaveformTealPlayed.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "32-BIT FLOAT PCM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = WaveformTealPlayed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    val sr = (telemetry?.sampleRateHz ?: 0).takeIf { it > 0 }
                        ?: track.activeVariant?.sampleRateHz
                        ?: track.sampleRateHz
                        ?: 44_100
                    val bitDepth = track.activeVariant?.bitDepth ?: track.bitDepth ?: telemetry?.bitDepth ?: 16
                    val codec = telemetry?.codecLabel?.takeIf { it.isNotBlank() } ?: track.audioFormatLabel
                    val channels = telemetry?.channelCount ?: track.activeVariant?.channelCount ?: 2

                    Text(
                        text = "$codec • ${bitDepth}-bit Input → 32-bit Float • ${formatSampleRateKhz(sr)} • ${channels}ch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (!telemetry?.liveIcyTitle.isNullOrBlank()) {
                        Text(
                            text = "ICY Now On Air: ${telemetry?.liveIcyTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiResGoldAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Float PCM Direct (Bypass EQ on Lossless)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isEqBypassedNow) {
                                    "Bit-clean bypass active for lossless audio"
                                } else {
                                    "Avoid 16-bit hardware AudioEffect quantization"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = directFloatPcmBypassEq,
                            onCheckedChange = { checked ->
                                MixaHaptics.toggle(view, checked)
                                onToggleDirectPcmBypass(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = WaveformTealPlayed
                            ),
                            modifier = Modifier.testTag("switcher_direct_pcm_bypass_switch")
                        )
                    }
                }
            }

            // Switchable Bitrate / Quality Variants
            if ((track.source == TrackSource.DIRECT_STREAM || track.source == TrackSource.EXTENSION) &&
                track.variants.isNotEmpty()
            ) {
                Text(
                    text = "AVAILABLE QUALITY TIERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    track.variants.forEach { variant ->
                        val isActive = variant.id == (track.activeVariant?.id ?: track.activeVariantId)
                        val tierColor = when (variant.tier) {
                            AudioQualityTier.HI_RES_LOSSLESS -> HiResGoldAccent
                            AudioQualityTier.CD_LOSSLESS -> WaveformTealPlayed
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(tokens.cardCornerRadius),
                            color = if (isActive) {
                                tierColor.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.cardCornerRadius))
                                .clickable {
                                    MixaHaptics.tick(view)
                                    onSelectVariant(variant.id)
                                    onDismiss()
                                }
                                .testTag("variant_option_${variant.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = variant.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isActive) tierColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = variant.specSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Active quality",
                                        tint = tierColor
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (track.source == TrackSource.YOUTUBE) {
                Text(
                    text = "YOUTUBE STREAM FORMAT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val isOpusActive = !playbackState.activeStreamInfo.contains("AAC", ignoreCase = true) &&
                    !playbackState.activeStreamInfo.contains("HLS", ignoreCase = true)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    YouTubeVariantOption(
                        title = "Opus / WebM Adaptive",
                        subtitle = "128–160 kbps low-latency psychoacoustic codec",
                        isActive = isOpusActive,
                        onClick = {
                            MixaHaptics.tick(view)
                            onSelectVariant("yt_opus")
                            onDismiss()
                        }
                    )
                    YouTubeVariantOption(
                        title = "AAC / HLS High Compatibility",
                        subtitle = "M4A / HLS adaptive segments for maximum resilience",
                        isActive = !isOpusActive,
                        onClick = {
                            MixaHaptics.tick(view)
                            onSelectVariant("yt_aac_hls")
                            onDismiss()
                        }
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "Playing native device audio directly through Media3 Float PCM mixer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeVariantOption(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tokens = LocalSonoraTokens.current
    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        color = if (isActive) {
            WaveformTealPlayed.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardCornerRadius))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isActive) WaveformTealPlayed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Active format",
                    tint = WaveformTealPlayed
                )
            }
        }
    }
}
