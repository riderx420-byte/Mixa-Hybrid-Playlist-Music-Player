package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.UserAudioSettings
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.hapticClickable

@Composable
fun AppCustomizationSettingsCard(
    userSettings: UserAudioSettings,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectThemeAccent: (ThemeAccent) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectCornerStyle: (ThemeCornerStyle) -> Unit,
    onSelectBackdropStyle: (PlayerBackdropStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.chipCornerRadius))
                    .hapticClickable(kind = MixaHapticKind.TAP) { expanded = !expanded }
                    .testTag("settings_customization_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "App Customization",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${userSettings.themeMode.label} • ${if (userSettings.useDynamicColor) "Material You" else userSettings.themeAccent.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse customization" else "Expand customization",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ThemePreviewCard(settings = userSettings)

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                ThemeCustomizationControls(
                    settings = userSettings,
                    onSelectThemeMode = onSelectThemeMode,
                    onSelectAccent = onSelectThemeAccent,
                    onToggleDynamicColor = onToggleDynamicColor,
                    onSelectCornerStyle = onSelectCornerStyle,
                    onSelectBackdropStyle = onSelectBackdropStyle
                )
            }
        }
    }
}

@Composable
fun AboutAppSettingsCard(
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_about_app_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About Mixa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Mixa Brand Logo Banner
            Surface(
                shape = RoundedCornerShape(tokens.heroCornerRadius),
                color = Color(0xFF0A0A0C),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_mixa_logo_banner")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primary.copy(alpha = 0.22f),
                                    tertiary.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(tokens.cardCornerRadius),
                            color = Color.Black,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_mixa_splash_logo),
                                contentDescription = "Mixa App Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mixa",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Unified Hi-Res Streaming & Hybrid Audio Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.80f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PipelineTagPill(text = "32-bit Float PCM", accent = WaveformTealPlayed)
                                PipelineTagPill(text = "Gapless + Crossfade", accent = secondary)
                            }
                        }
                    }
                }
            }

            // Streaming Tech Pipeline
            Surface(
                shape = RoundedCornerShape(tokens.chipCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "MUSIC STREAMING PIPELINE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    PipelineDetailRow(
                        icon = Icons.Rounded.Hub,
                        tint = primary,
                        title = "Dual Discovery & ISRC Router",
                        description = "Native YouTube InnerTube clients (ANDROID_VR, WEB_REMIX, IOS + SAPISIDHASH account auth) run alongside QuickJS-sandboxed Mornye .sflx extensions with automatic ISRC cross-matching."
                    )
                    PipelineDetailRow(
                        icon = Icons.Rounded.Security,
                        tint = tokens.emeraldAccent,
                        title = "Resilient Resolution & Decryption",
                        description = "NewPipeExtractor signature recovery feeds Media3 ExoPlayer via a 512 KB chunked failover ResolvingDataSource, HLS fallback, and on-the-fly Blowfish stream decryption."
                    )
                    PipelineDetailRow(
                        icon = Icons.Rounded.GraphicEq,
                        tint = secondary,
                        title = "Audio Sink, Dual-Deck Transitions & DSP",
                        description = "DefaultAudioSink preserves 32-bit float PCM (with optional EQ bypass for lossless), paired with a 5-band hardware EQ and dual-deck setPauseAtEndOfMediaItems gapless/crossfade engine."
                    )
                    PipelineDetailRow(
                        icon = Icons.Rounded.Lyrics,
                        tint = tertiary,
                        title = "Time-Synced .LRC Engine",
                        description = "Multi-timestamp .lrc parser resolves sidecar files, LRCLIB synced catalogs, and manual SAF imports with millimeter-accurate position tracking."
                    )
                }
            }

            // Hybrid Playlists Tech Pipeline
            Surface(
                shape = RoundedCornerShape(tokens.chipCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HYBRID PLAYLISTS PIPELINE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.emeraldAccent
                    )
                    PipelineDetailRow(
                        icon = Icons.Rounded.Storage,
                        tint = tokens.emeraldAccent,
                        title = "Relational Room Graph",
                        description = "Playlists and tracks persist offline in Room SQLite using ordered PlaylistTrackCrossRef joins, preserving exact track order, source metadata, and stream quality variants."
                    )
                    PipelineDetailRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        tint = primary,
                        title = "Cross-Source Unified Queue",
                        description = "Mix local MediaStore/SAF FLAC & MP3 files, YouTube Music streams, .sflx catalog tracks, and live lossless FLAC radio URLs in a single playlist resolved lazily on playback."
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineTagPill(
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PipelineDetailRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

