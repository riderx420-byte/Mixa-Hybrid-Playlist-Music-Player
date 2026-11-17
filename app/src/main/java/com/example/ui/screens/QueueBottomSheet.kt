package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlaybackState
import com.example.ui.components.AnimatedEqualizerBars
import com.example.ui.components.SourceFormatBadge
import com.example.ui.components.TrackArtwork
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onJumpToIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onToggleAutoRadio: (Boolean) -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("Mixa Mix") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Up Next Queue",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${playbackState.queue.size} unified tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (playbackState.queue.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            showSaveDialog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("save_queue_playlist_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Mix")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto-Radio Continuation",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (playbackState.isLoadingRadio) "Fetching RDAMVM tracks…" else "Auto-append related YT Music tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = playbackState.isRadioActive,
                        onCheckedChange = { checked ->
                            MixaHaptics.toggle(view, checked)
                            onToggleAutoRadio(checked)
                        },
                        modifier = Modifier.testTag("queue_auto_radio_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(playbackState.queue) { index, track ->
                    val isCurrent = index == playbackState.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .hapticClickable(kind = MixaHapticKind.CONFIRM) { onJumpToIndex(index) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(44.dp)) {
                            TrackArtwork(
                                artworkUri = track.artworkUri,
                                contentDescription = track.title,
                                modifier = Modifier.size(44.dp),
                                cornerRadius = 9.dp
                            )
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedEqualizerBars(isPlaying = playbackState.isPlaying)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isCurrent) primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                SourceFormatBadge(track = track)
                            }
                        }

                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    MixaHaptics.tick(view)
                                    onMoveItem(index, index - 1)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                if (index < playbackState.queue.lastIndex) {
                                    MixaHaptics.tick(view)
                                    onMoveItem(index, index + 1)
                                }
                            },
                            enabled = index < playbackState.queue.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                MixaHaptics.reject(view)
                                onRemoveItem(index)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove from queue", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    tint = primary
                )
            },
            title = {
                Text(
                    text = "Save Unified Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Persist these ${playbackState.queue.size} tracks as an editable hybrid playlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        shape = RoundedCornerShape(tokens.cardCornerRadius),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            MixaHaptics.confirm(view)
                            onSaveQueueAsPlaylist(playlistName.trim())
                            showSaveDialog = false
                        }
                    },
                    enabled = playlistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text("Save Mix")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    MixaHaptics.tap(view)
                    showSaveDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}
