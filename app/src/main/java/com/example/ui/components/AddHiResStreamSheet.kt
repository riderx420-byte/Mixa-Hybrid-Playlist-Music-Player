package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Track
import com.example.domain.model.formatSampleRateKhz
import com.example.ui.StreamProbeUiState
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHiResStreamSheet(
    probeState: StreamProbeUiState,
    onDismiss: () -> Unit,
    onProbeStream: (primaryUrl: String, customTitle: String?, fallbackUrl: String?) -> Unit,
    onSaveTrack: (track: Track, playImmediately: Boolean) -> Unit,
    onResetProbe: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary

    var primaryUrl by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var fallbackUrl by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = {
            onResetProbe()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("add_hires_stream_sheet"),
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
                        tint = HiResGoldAccent
                    )
                    Column {
                        Text(
                            text = "Add Lossless / Hi-Res Stream",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "FLAC, WAV, ALAC, Ogg, AAC, .pls or .m3u radio URL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onResetProbe()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_add_hires_sheet_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            TextField(
                value = primaryUrl,
                onValueChange = { primaryUrl = it },
                label = { Text("Primary Lossless Stream URL") },
                placeholder = { Text("https://.../stream.flac or station.pls") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_stream_url_input")
            )

            TextField(
                value = customTitle,
                onValueChange = { customTitle = it },
                label = { Text("Station or Track Title (Optional)") },
                placeholder = { Text("Auto-detected from ICY stream headers if empty") },
                singleLine = true,
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_stream_title_input")
            )

            TextField(
                value = fallbackUrl,
                onValueChange = { fallbackUrl = it },
                label = { Text("Cellular Fallback URL (Optional AAC/MP3)") },
                placeholder = { Text("https://.../aac-320") },
                singleLine = true,
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_stream_fallback_input")
            )

            Button(
                onClick = {
                    MixaHaptics.confirm(view)
                    onProbeStream(
                        primaryUrl.trim(),
                        customTitle.trim().ifBlank { null },
                        fallbackUrl.trim().ifBlank { null }
                    )
                },
                enabled = primaryUrl.isNotBlank() && !probeState.isProbing,
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inspect_stream_button")
            ) {
                if (probeState.isProbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspecting Audio Stream…")
                } else {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Stream")
                }
            }

            probeState.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(tokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            probeState.probedTrack?.let { probed ->
                Surface(
                    shape = RoundedCornerShape(tokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("probed_stream_preview_card")
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = probed.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = probed.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SourceFormatBadge(track = probed)
                        }

                        val specDetails = buildList {
                            probed.bitDepth?.let { add("${it}-bit Float PCM Ready") }
                            probed.sampleRateHz?.let { add(formatSampleRateKhz(it)) }
                            probed.bitrateKbps?.let { add("$it kbps") }
                            if (probed.variants.size > 1) add("${probed.variants.size} quality tiers")
                        }.joinToString(" • ")

                        if (specDetails.isNotBlank()) {
                            Text(
                                text = specDetails,
                                style = MaterialTheme.typography.labelMedium,
                                color = WaveformTealPlayed
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    MixaHaptics.confirm(view)
                                    onSaveTrack(probed, false)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_stream_library_button")
                            ) {
                                Text("Save Only")
                            }
                            Button(
                                onClick = {
                                    MixaHaptics.confirm(view)
                                    onSaveTrack(probed, true)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaveformTealPlayed),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_and_play_stream_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save & Play", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
