package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlaybackState
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var dragTriggered by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(tokens.cardCornerRadius))
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragDeltaX = 0f
                        dragTriggered = false
                        MixaHaptics.gestureStart(view)
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragTriggered) return@detectHorizontalDragGestures
                        dragDeltaX += dragAmount
                        if (dragDeltaX < -64f) {
                            dragTriggered = true
                            MixaHaptics.confirm(view)
                            onSkipNext()
                        } else if (dragDeltaX > 64f) {
                            dragTriggered = true
                            MixaHaptics.confirm(view)
                            onSkipPrevious()
                        }
                    },
                    onDragEnd = {
                        dragDeltaX = 0f
                        dragTriggered = false
                    },
                    onDragCancel = {
                        dragDeltaX = 0f
                        dragTriggered = false
                    }
                )
            }
            .hapticClickable(kind = MixaHapticKind.CONFIRM, onClick = onExpand)
            .testTag("mini_player_bar"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            if (track.isLiveStream) {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (track.isHiRes) HiResGoldAccent else WaveformTealPlayed,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.16f),
                                tertiary.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    artworkUri = track.artworkUri,
                    contentDescription = "${track.title} mini player cover",
                    modifier = Modifier.size(46.dp),
                    cornerRadius = tokens.chipCornerRadius
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val hasError = playbackState.streamError != null || playbackState.errorMessage != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasError) {
                            Text(
                                text = playbackState.streamError?.title
                                    ?: playbackState.errorMessage
                                    ?: "Stream error — tap to retry",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (playbackState.activeStreamInfo.isNotBlank()) {
                                Text(
                                    text = "• ${playbackState.activeStreamInfo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (track.isHiRes) HiResGoldAccent else primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onOpenQueue()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Open Up Next queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val hasErrorAction = playbackState.streamError != null || playbackState.errorMessage != null
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasErrorAction) MaterialTheme.colorScheme.errorContainer else primary
                        )
                        .hapticClickable(kind = MixaHapticKind.CONFIRM, onClick = onTogglePlayPause)
                        .testTag("mini_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (hasErrorAction) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry stream",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                IconButton(
                    onClick = {
                        MixaHaptics.confirm(view)
                        onSkipNext()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
