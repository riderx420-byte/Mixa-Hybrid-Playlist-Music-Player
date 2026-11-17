package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.player.CrossfadeTransitionPolicy
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHaptics
import kotlin.math.roundToInt

private val CrossfadeQuickPresets = listOf(0, 3, 5, 8, 12)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CrossfadeSettingsSheet(
    currentDurationSeconds: Int,
    onSelectDurationSeconds: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val safeSeconds = CrossfadeTransitionPolicy.clampCrossfadeSeconds(currentDurationSeconds)
    var lastHapticStep by remember(safeSeconds) { mutableIntStateOf(safeSeconds) }
    val isGapless = safeSeconds == 0

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
                .testTag("crossfade_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                            .background(
                                if (isGapless) {
                                    tokens.emeraldAccent.copy(alpha = 0.16f)
                                } else {
                                    primary.copy(alpha = 0.16f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGapless) Icons.Rounded.AllInclusive else Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (isGapless) tokens.emeraldAccent else primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Crossfade & Gapless",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ExoPlayer track transition engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("crossfade_sheet_close_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close crossfade settings")
                }
            }

            // Live Mode Readout Card
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isGapless) "Gapless Continuous" else "${safeSeconds}s Equal-Power Crossfade",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isGapless) tokens.emeraldAccent else primary
                            )
                            Text(
                                text = if (isGapless) {
                                    "setPauseAtEndOfMediaItems(false) • Zero-gap sequential timeline"
                                } else {
                                    "setPauseAtEndOfMediaItems(true) • Boundary hold + dual-deck blend"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGapless) {
                                tokens.emeraldAccent.copy(alpha = 0.18f)
                            } else {
                                primary.copy(alpha = 0.18f)
                            }
                        ) {
                            Text(
                                text = if (isGapless) "GAPLESS" else "${safeSeconds}S",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isGapless) tokens.emeraldAccent else primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Slider(
                        value = safeSeconds.toFloat(),
                        onValueChange = { raw ->
                            val step = raw.roundToInt().coerceIn(
                                CrossfadeTransitionPolicy.MIN_CROSSFADE_SECONDS,
                                CrossfadeTransitionPolicy.MAX_CROSSFADE_SECONDS
                            )
                            if (step != lastHapticStep) {
                                lastHapticStep = step
                                MixaHaptics.tick(view)
                            }
                            onSelectDurationSeconds(step)
                        },
                        valueRange = CrossfadeTransitionPolicy.MIN_CROSSFADE_SECONDS.toFloat()..CrossfadeTransitionPolicy.MAX_CROSSFADE_SECONDS.toFloat(),
                        steps = CrossfadeTransitionPolicy.MAX_CROSSFADE_SECONDS - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isGapless) tokens.emeraldAccent else primary,
                            activeTrackColor = if (isGapless) tokens.emeraldAccent else primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier.testTag("crossfade_duration_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0s (Gapless)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "6s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "12s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "QUICK PRESETS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CrossfadeQuickPresets.forEach { presetSec ->
                        val selected = safeSeconds == presetSec
                        val chipColor = if (presetSec == 0) tokens.emeraldAccent else primary
                        FilterChip(
                            selected = selected,
                            onClick = {
                                MixaHaptics.confirm(view)
                                lastHapticStep = presetSec
                                onSelectDurationSeconds(presetSec)
                            },
                            label = {
                                Text(if (presetSec == 0) "Gapless (Off)" else "${presetSec}s")
                            },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = chipColor.copy(alpha = 0.22f),
                                selectedLabelColor = chipColor
                            ),
                            modifier = Modifier.testTag("crossfade_preset_${presetSec}s")
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(tokens.chipCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isGapless) {
                        "Continuous albums, DJ sets, and classical suites transition sample-accurately on ExoPlayer's audio pipeline with no silence."
                    } else {
                        "Mixa pre-cues the next queued track silently, holds the outgoing track cleanly at its end boundary via setPauseAtEndOfMediaItems, and applies a cosine/sine equal-power blend."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }

            FilledTonalButton(
                onClick = {
                    MixaHaptics.confirm(view)
                    onDismiss()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = primary.copy(alpha = 0.18f),
                    contentColor = primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crossfade_sheet_done_button")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
