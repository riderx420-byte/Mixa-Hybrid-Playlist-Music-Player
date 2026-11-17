package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.UserAudioSettings
import com.example.domain.model.EqPreset
import com.example.domain.model.EqualizerState
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import com.example.ui.components.AboutAppSettingsCard
import com.example.ui.components.AppCustomizationSettingsCard
import com.example.ui.components.CrossfadeSettingsSheet
import com.example.ui.components.EqSnapHelper
import com.example.ui.components.InteractiveEqResponseGraph
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@Composable
fun EqualizerScreen(
    eqState: EqualizerState,
    userSettings: UserAudioSettings,
    onToggleEq: (Boolean) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandLevelChanged: (Int, Float) -> Unit,
    onBassBoostChanged: (Int) -> Unit,
    onToggleAutoRadio: (Boolean) -> Unit,
    onTogglePreferOpus: (Boolean) -> Unit,
    onToggleAutoSkipBroken: (Boolean) -> Unit = {},
    onSelectPreferredStreamQuality: (PreferredStreamQuality) -> Unit = {},
    onToggleDirectFloatPcmBypass: (Boolean) -> Unit = {},
    onSelectCrossfadeDuration: (Int) -> Unit = {},
    onClearStreamCache: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onSelectThemeAccent: (ThemeAccent) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onSelectCornerStyle: (ThemeCornerStyle) -> Unit = {},
    onSelectBackdropStyle: (PlayerBackdropStyle) -> Unit = {},
    onOpenAccountSheet: () -> Unit = {},
    activeExtensionsSummary: String = "0 registries • Add Mornye .sflx providers",
    hasActiveExtensions: Boolean = false,
    onOpenExtensionManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var streamCacheClearedNote by remember { mutableStateOf(false) }
    var showCrossfadeSheet by remember { mutableStateOf(false) }
    var lastBassStrength by remember { mutableIntStateOf(eqState.bassBoostStrength) }
    LaunchedEffect(eqState.activePreset) {
        lastBassStrength = eqState.bassBoostStrength
    }
    val view = LocalView.current
    val context = LocalContext.current
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
    ) {
        // Unified Settings Header
        item(key = "settings_header") {
            Surface(
                shape = RoundedCornerShape(tokens.heroCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primary.copy(alpha = 0.16f),
                                    tertiary.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MIXA SETTINGS",
                                style = MaterialTheme.typography.labelMedium,
                                color = primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Settings & Audio Studio",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mastering EQ, theme customization & stream engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(tokens.chipCornerRadius),
                        color = if (eqState.isAvailable && eqState.isEnabled) {
                            tokens.emeraldAccent.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = if (eqState.isAvailable && eqState.isEnabled) "DSP LIVE" else "DSP READY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (eqState.isAvailable && eqState.isEnabled) {
                                tokens.emeraldAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Section 1: Mastering Audio Studio (EQ toggle + Interactive 5-Bar Graph + Presets + Sub-Bass)
        item(key = "section_audio_studio") {
            SettingsSectionLabel(title = "AUDIO STUDIO")
        }

        item(key = "eq_studio_card") {
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Master DSP Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(tokens.chipCornerRadius))
                                    .background(primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = primary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Studio Equalizer",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        eqState.isBypassedForDirectPcm -> "Bypassed cleanly for 32-bit Float PCM Hi-Res"
                                        eqState.isAvailable -> "Hardware DSP attached • ${eqState.activePreset.displayName}"
                                        else -> "Play audio to attach live ExoPlayer session"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        eqState.isBypassedForDirectPcm -> com.example.ui.components.HiResGoldAccent
                                        eqState.isAvailable -> tokens.emeraldAccent
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Switch(
                            checked = eqState.isEnabled,
                            onCheckedChange = { checked ->
                                MixaHaptics.toggle(view, checked)
                                onToggleEq(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = primary
                            ),
                            modifier = Modifier.testTag("eq_master_switch")
                        )
                    }

                    // Interactive 5-Bar Frequency Response Equalizer Graph
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Frequency Response Equalizer",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Double-tap bar to reset",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        InteractiveEqResponseGraph(
                            bands = eqState.bands,
                            enabled = eqState.isEnabled,
                            onBandLevelChanged = onBandLevelChanged
                        )
                    }

                    // Inline Preset Strip
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EqPreset.entries.filter { it != EqPreset.CUSTOM }, key = { it.name }) { preset ->
                            FilterChip(
                                selected = eqState.activePreset == preset,
                                onClick = {
                                    EqSnapHelper.performStepTickHaptic(view)
                                    onSelectPreset(preset)
                                },
                                label = { Text(preset.displayName) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = primary.copy(alpha = 0.24f),
                                    selectedLabelColor = primary
                                ),
                                modifier = Modifier.testTag("eq_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    // Sub-Bass Boost with Progressive Escalating Haptic Feedback
                    Surface(
                        shape = RoundedCornerShape(tokens.chipCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val bassPct = eqState.bassBoostStrength / 10
                            val isHarshBass = eqState.bassBoostStrength >= 700
                            val isMaxBass = eqState.bassBoostStrength >= 900
                            val bassAccent = when {
                                isMaxBass -> MaterialTheme.colorScheme.error
                                isHarshBass -> tertiary
                                else -> secondary
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Sub-Bass Boost",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            isMaxBass -> "Maximum punch • intense haptic"
                                            isHarshBass -> "Heavy low-end • strong haptic"
                                            bassPct > 0 -> "Warm sub amplification"
                                            else -> "Off"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = bassAccent.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "$bassPct%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = bassAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Slider(
                                value = eqState.bassBoostStrength.toFloat(),
                                onValueChange = { raw ->
                                    val nextStrength = raw.toInt().coerceIn(0, 1000)
                                    val bassSpec = EqSnapHelper.evaluateBassHaptic(
                                        previousStrength = lastBassStrength,
                                        newStrength = nextStrength
                                    )
                                    if (bassSpec.shouldTrigger) {
                                        lastBassStrength = nextStrength
                                        EqSnapHelper.dispatchBassHaptic(context, view, bassSpec)
                                    }
                                    onBassBoostChanged(nextStrength)
                                },
                                valueRange = 0f..1000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = bassAccent,
                                    activeTrackColor = bassAccent,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                modifier = Modifier.testTag("bass_boost_slider")
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Personalization & Account
        item(key = "section_personalization") {
            SettingsSectionLabel(title = "ACCOUNT & APPEARANCE")
        }

        item(key = "ytm_account_card") {
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(tokens.chipCornerRadius))
                                .background(
                                    if (userSettings.isYoutubeSignedIn) {
                                        tokens.emeraldAccent.copy(alpha = 0.16f)
                                    } else {
                                        secondary.copy(alpha = 0.16f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (userSettings.isYoutubeSignedIn) {
                                    Icons.Rounded.VerifiedUser
                                } else {
                                    Icons.Rounded.AccountCircle
                                },
                                contentDescription = null,
                                tint = if (userSettings.isYoutubeSignedIn) tokens.emeraldAccent else secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "YouTube Music Account",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (userSettings.isYoutubeSignedIn) {
                                    userSettings.ytAccountLabel ?: "Premium session active (SAPISIDHASH)"
                                } else {
                                    "Sign in to sync your songs, Premium audio & mixes"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (userSettings.isYoutubeSignedIn) {
                                    tokens.emeraldAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            onOpenAccountSheet()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("eq_open_account_button")
                    ) {
                        Text(if (userSettings.isYoutubeSignedIn) "Manage" else "Sign In")
                    }
                }
            }
        }

        item(key = "extensions_hub_card") {
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(tokens.chipCornerRadius))
                                .background(
                                    if (hasActiveExtensions) {
                                        tokens.emeraldAccent.copy(alpha = 0.16f)
                                    } else {
                                        primary.copy(alpha = 0.16f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = if (hasActiveExtensions) tokens.emeraldAccent else primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Extensions & Repositories",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeExtensionsSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasActiveExtensions) {
                                    tokens.emeraldAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            onOpenExtensionManager()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("open_extension_manager_button")
                    ) {
                        Text("Manage")
                    }
                }
            }
        }

        item(key = "app_customization_card") {
            AppCustomizationSettingsCard(
                userSettings = userSettings,
                onSelectThemeMode = onSelectThemeMode,
                onSelectThemeAccent = onSelectThemeAccent,
                onToggleDynamicColor = onToggleDynamicColor,
                onSelectCornerStyle = onSelectCornerStyle,
                onSelectBackdropStyle = onSelectBackdropStyle
            )
        }

        // Section 3: Playback Engine & About
        item(key = "section_engine") {
            SettingsSectionLabel(title = "STREAM ENGINE & ABOUT")
        }

        item(key = "stream_engine_prefs") {
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Playback & InnerTube Engine",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SettingsToggleRow(
                        title = "Auto-Radio Continuation",
                        subtitle = "Fetch related tracks automatically near queue end",
                        checked = userSettings.autoRadioEnabled,
                        onCheckedChange = onToggleAutoRadio,
                        tag = "auto_radio_setting_switch"
                    )

                    SettingsToggleRow(
                        title = "Prefer Opus/WebM Audio",
                        subtitle = "Prioritize 128–160 kbps Opus over AAC/M4A when resolving streams",
                        checked = userSettings.preferOpusStream,
                        onCheckedChange = onTogglePreferOpus,
                        tag = "prefer_opus_setting_switch"
                    )

                    SettingsToggleRow(
                        title = "Auto-Skip Unplayable Tracks",
                        subtitle = "Advance to next queued track if all stream recovery attempts fail",
                        checked = userSettings.autoSkipBrokenTracks,
                        onCheckedChange = onToggleAutoSkipBroken,
                        tag = "auto_skip_broken_switch"
                    )

                    SettingsToggleRow(
                        title = "Float PCM Direct (Bypass EQ on Hi-Res)",
                        subtitle = "Keep 32-bit float PCM path bit-transparent on lossless/FLAC/WAV tracks",
                        checked = userSettings.directFloatPcmBypassEq,
                        onCheckedChange = onToggleDirectFloatPcmBypass,
                        tag = "direct_float_pcm_bypass_switch"
                    )

                    val crossfadeSec = userSettings.crossfadeDurationSeconds
                    val isGaplessMode = crossfadeSec == 0
                    Surface(
                        shape = RoundedCornerShape(tokens.chipCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                            .hapticClickable { showCrossfadeSheet = true }
                            .testTag("crossfade_settings_menu_item")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isGaplessMode) {
                                                tokens.emeraldAccent.copy(alpha = 0.16f)
                                            } else {
                                                primary.copy(alpha = 0.16f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGaplessMode) Icons.Rounded.AllInclusive else Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = if (isGaplessMode) tokens.emeraldAccent else primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Audio Crossfade & Gapless",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isGaplessMode) {
                                            "Gapless continuous playback active"
                                        } else {
                                            "${crossfadeSec}s overlap • Boundary transition active"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isGaplessMode) {
                                        tokens.emeraldAccent.copy(alpha = 0.16f)
                                    } else {
                                        primary.copy(alpha = 0.16f)
                                    }
                                ) {
                                    Text(
                                        text = if (isGaplessMode) "Gapless" else "${crossfadeSec}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGaplessMode) tokens.emeraldAccent else primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Configure crossfade duration",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Hi-Res Stream Quality Policy",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = userSettings.preferredStreamQuality.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PreferredStreamQuality.entries, key = { it.name }) { quality ->
                                val selected = userSettings.preferredStreamQuality == quality
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        MixaHaptics.tick(view)
                                        onSelectPreferredStreamQuality(quality)
                                    },
                                    label = { Text(quality.label) },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = primary.copy(alpha = 0.24f),
                                        selectedLabelColor = primary
                                    ),
                                    modifier = Modifier.testTag("stream_quality_policy_${quality.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(tokens.chipCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stream Error & URL Cache",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (streamCacheClearedNote) {
                                        "Cleared cached CDN tokens & client penalties"
                                    } else {
                                        "Reset cached googlevideo URLs and client cooldowns"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (streamCacheClearedNote) {
                                        tokens.emeraldAccent
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    MixaHaptics.confirm(view)
                                    onClearStreamCache()
                                    streamCacheClearedNote = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("clear_stream_cache_button")
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        item(key = "about_app_card") {
            AboutAppSettingsCard()
        }
    }

    if (showCrossfadeSheet) {
        CrossfadeSettingsSheet(
            currentDurationSeconds = userSettings.crossfadeDurationSeconds,
            onSelectDurationSeconds = onSelectCrossfadeDuration,
            onDismiss = { showCrossfadeSheet = false }
        )
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { next ->
                MixaHaptics.toggle(view, next)
                onCheckedChange(next)
            },
            modifier = Modifier.testTag(tag)
        )
    }
}

