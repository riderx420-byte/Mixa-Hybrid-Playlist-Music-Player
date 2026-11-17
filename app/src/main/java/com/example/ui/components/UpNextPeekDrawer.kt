package com.example.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * Docked Up Next peek tab anchored at the bottom edge of Now Playing that expands cleanly
 * (via click or upward drag) into a dark translucent queue overlay sheet.
 */
@Composable
fun UpNextPeekOverlay(
    playbackState: PlaybackState,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onJumpToIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onToggleAutoRadio: (Boolean) -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val queue = playbackState.queue
    val nextTrack = queue.getOrNull(playbackState.currentIndex + 1)

    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("Mixa Mix") }

    BackHandler(enabled = isExpanded) {
        onExpandedChange(false)
    }

    val peekSurfaceColor = if (tokens.isDark) {
        Color(0xFF121417).copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
    }
    val sheetSurfaceColor = if (tokens.isDark) {
        Color(0xFF0B0D10).copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val glassRimColor = if (tokens.isDark) {
        Color.White.copy(alpha = 0.13f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    val handleColor = if (tokens.isDark) {
        Color.White.copy(alpha = 0.30f)
    } else {
        Color.Black.copy(alpha = 0.22f)
    }
    val labelMuted = if (tokens.isDark) Color(0xFFA2A6AE) else MaterialTheme.colorScheme.onSurfaceVariant
    val primaryText = if (tokens.isDark) Color.White else MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim when queue sheet is expanded
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(160, easing = FastOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .hapticClickable(kind = MixaHapticKind.GESTURE_END) {
                        onExpandedChange(false)
                    }
            )
        }

        // Docked Peek Tab at the very bottom edge (visible when collapsed)
        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn(tween(160)) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            var accumulatedDragY by remember { mutableFloatStateOf(0f) }
            val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(topShape)
                    .background(peekSurfaceColor)
                    .drawWithContent {
                        drawContent()
                        // Subtle 1px frosted top highlight arc
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, glassRimColor, Color.Transparent)
                            ),
                            start = Offset(0f, 0.5f),
                            end = Offset(size.width, 0.5f),
                            strokeWidth = 1.2f
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                accumulatedDragY = 0f
                                MixaHaptics.gestureStart(view)
                            },
                            onVerticalDrag = { _, dragAmount ->
                                accumulatedDragY += dragAmount
                                if (accumulatedDragY < -44f) {
                                    MixaHaptics.gestureEnd(view)
                                    onExpandedChange(true)
                                }
                            },
                            onDragEnd = { accumulatedDragY = 0f },
                            onDragCancel = { accumulatedDragY = 0f }
                        )
                    }
                    .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                        onExpandedChange(true)
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("now_playing_up_next_peek_tab")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(handleColor)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UP NEXT • ${queue.size} ${if (queue.size == 1) "TRACK" else "TRACKS"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = WaveformTealPlayed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = nextTrack?.let { "${it.title} — ${it.artist}" }
                                    ?: if (playbackState.isRadioActive) "Auto-Radio will queue next track" else "End of queue",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Expand Up Next queue",
                            tint = labelMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Expanded Dark Translucent Queue Overlay Sheet
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            var dragDownY by remember { mutableFloatStateOf(0f) }
            val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .clip(sheetShape)
                    .background(sheetSurfaceColor)
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = glassRimColor,
                            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                            style = Stroke(width = 1f)
                        )
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("now_playing_up_next_expanded_sheet")
            ) {
                // Drag-down header zone
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragDownY = 0f
                                    MixaHaptics.gestureStart(view)
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    dragDownY += dragAmount
                                    if (dragDownY > 72f) {
                                        MixaHaptics.gestureEnd(view)
                                        onExpandedChange(false)
                                    }
                                },
                                onDragEnd = { dragDownY = 0f },
                                onDragCancel = { dragDownY = 0f }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(handleColor)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UP NEXT • ${queue.size} TRACKS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = WaveformTealPlayed
                            )
                            Text(
                                text = playbackState.currentTrack?.title ?: "Active Queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (queue.isNotEmpty()) {
                                FrostedGlassPill(
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    label = "Save Mix",
                                    tint = primaryText,
                                    isDark = tokens.isDark,
                                    onClick = { showSaveDialog = true },
                                    tag = "up_next_save_mix_button"
                                )
                            }
                            IconButton(
                                onClick = {
                                    MixaHaptics.tap(view)
                                    onExpandedChange(false)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("up_next_collapse_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse queue",
                                    tint = primaryText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Compact Auto-Radio Strip
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (tokens.isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = null,
                                tint = WaveformTealPlayed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Auto-Radio Continuation",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = primaryText
                                )
                                Text(
                                    text = if (playbackState.isLoadingRadio) "Fetching related tracks…" else "Append endless similar tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = labelMuted
                                )
                            }
                        }
                        Switch(
                            checked = playbackState.isRadioActive,
                            onCheckedChange = { checked ->
                                MixaHaptics.toggle(view, checked)
                                onToggleAutoRadio(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = WaveformTealPlayed
                            ),
                            modifier = Modifier.testTag("up_next_auto_radio_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(queue) { index, track ->
                        val isCurrent = index == playbackState.currentIndex
                        val itemBg = when {
                            isCurrent && tokens.isDark -> WaveformTealPlayed.copy(alpha = 0.14f)
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                            tokens.isDark -> Color.White.copy(alpha = 0.045f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(itemBg)
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
                                            .background(Color.Black.copy(alpha = 0.56f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedEqualizerBars(
                                            isPlaying = playbackState.isPlaying,
                                            barColor = WaveformTealPlayed
                                        )
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
                                    color = if (isCurrent) WaveformTealPlayed else primaryText,
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
                                        color = labelMuted,
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
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    contentDescription = "Move up",
                                    tint = labelMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index < queue.lastIndex) {
                                        MixaHaptics.tick(view)
                                        onMoveItem(index, index + 1)
                                    }
                                },
                                enabled = index < queue.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    contentDescription = "Move down",
                                    tint = labelMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    MixaHaptics.reject(view)
                                    onRemoveItem(index)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove from queue",
                                    tint = labelMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Unified Queue") },
            text = {
                TextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            MixaHaptics.confirm(view)
                            onSaveQueueAsPlaylist(playlistName.trim())
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text("Save")
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

/**
 * Dark frosted glass pill button with blurred sheen backdrop and subtle 1px inner glass edge.
 */
@Composable
fun FrostedGlassPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val pillShape = RoundedCornerShape(tokens.chipCornerRadius.coerceAtLeast(16.dp))
    val baseFill = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    val rimStroke = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(baseFill)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = rimStroke,
                    cornerRadius = CornerRadius(
                        tokens.chipCornerRadius.coerceAtLeast(16.dp).toPx(),
                        tokens.chipCornerRadius.coerceAtLeast(16.dp).toPx()
                    ),
                    style = Stroke(width = 1f)
                )
            }
            .hapticClickable(kind = MixaHapticKind.TAP, onClick = onClick)
            .testTag(tag)
    ) {
        // Subtle inner frosted sheen highlight (softly blurred on Android 12+)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(10.dp)
                    } else {
                        Modifier
                    }
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.06f else 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}
