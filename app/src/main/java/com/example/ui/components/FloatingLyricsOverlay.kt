package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.lyrics.LrcLine
import com.example.data.lyrics.LrcParser
import com.example.domain.model.Track
import com.example.ui.LyricsUiState
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable
import kotlinx.coroutines.delay

/**
 * Floating time-synchronized `.lrc` lyrics composable on the Now Playing screen.
 * Automatically scrolls the highlighted lyric line smoothly with Media3's live [positionMs],
 * supports tap-to-seek on any timed lyric line, manual scroll auto-resume, sync nudge, and `.lrc` import.
 */
@Composable
fun FloatingLyricsOverlay(
    visible: Boolean,
    track: Track?,
    lyricsState: LyricsUiState,
    positionMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    onRefreshLyrics: () -> Unit,
    onImportLrcUri: (Uri) -> Unit,
    onNudgeOffsetMs: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    var isExpanded by remember { mutableStateOf(false) }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            MixaHaptics.confirm(view)
            onImportLrcUri(uri)
        }
    }

    val containerBg = if (tokens.isDark) {
        Color(0xE80D1118)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
    val onCard = if (tokens.isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val onCardMuted = if (tokens.isDark) Color(0xFFA7AFBC) else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (tokens.isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    AnimatedVisibility(
        visible = visible && track != null,
        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    top = 60.dp,
                    bottom = if (isExpanded) 86.dp else 238.dp,
                    start = 18.dp,
                    end = 18.dp
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            val targetMaxHeight = if (isExpanded) maxHeight else (maxHeight * 0.96f).coerceAtLeast(250.dp)

            Surface(
                shape = RoundedCornerShape(tokens.heroCornerRadius),
                color = containerBg,
                border = BorderStroke(1.dp, borderColor),
                shadowElevation = 22.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = targetMaxHeight)
                    .testTag("floating_lyrics_overlay")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    accentColor.copy(alpha = if (tokens.isDark) 0.16f else 0.10f),
                                    Color.Transparent,
                                    accentColor.copy(alpha = if (tokens.isDark) 0.08f else 0.05f)
                                )
                            )
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Floating Header Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val doc = lyricsState.document
                                val statusDotColor = when {
                                    lyricsState.isLoading -> MaterialTheme.colorScheme.primary
                                    doc?.isSynced == true -> WaveformTealPlayed
                                    else -> onCardMuted
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when {
                                            lyricsState.isLoading -> "SYNCING LYRICS…"
                                            doc?.isSynced == true -> "TIME-SYNCHRONIZED LRC"
                                            doc != null -> "LYRICS (UNSYNCED)"
                                            else -> "LYRICS"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (doc?.isSynced == true) WaveformTealPlayed else onCardMuted
                                    )
                                    Text(
                                        text = buildString {
                                            append(track?.title.orEmpty())
                                            doc?.sourceLabel?.let { src ->
                                                append(" • ")
                                                append(src)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onCard,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        MixaHaptics.tap(view)
                                        lrcPickerLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_import_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = "Import .lrc file",
                                        tint = onCardMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        MixaHaptics.tap(view)
                                        onRefreshLyrics()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_refresh_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh lyrics",
                                        tint = onCardMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        MixaHaptics.tap(view)
                                        isExpanded = !isExpanded
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_expand_button")
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) {
                                            Icons.Rounded.CloseFullscreen
                                        } else {
                                            Icons.Rounded.OpenInFull
                                        },
                                        contentDescription = if (isExpanded) "Shrink floating lyrics" else "Expand floating lyrics",
                                        tint = onCardMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        MixaHaptics.tap(view)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close lyrics",
                                        tint = onCard,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Lyrics Content Body
                        val document = lyricsState.document
                        when {
                            lyricsState.isLoading && document == null -> {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = WaveformTealPlayed,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Text(
                                            text = "Matching synced .lrc for ${track?.title.orEmpty()}…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = onCardMuted,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            }

                            document == null || document.lines.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lyrics,
                                            contentDescription = null,
                                            tint = onCardMuted.copy(alpha = 0.7f),
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Text(
                                            text = lyricsState.errorMessage
                                                ?: "No synchronized lyrics found for this track.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = onCard,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Retry automatic lookup or load a local .lrc lyric file.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = onCardMuted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    MixaHaptics.tap(view)
                                                    onRefreshLyrics()
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = onCard
                                                )
                                            ) {
                                                Text("Retry")
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    MixaHaptics.confirm(view)
                                                    lrcPickerLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = WaveformTealPlayed.copy(alpha = 0.18f),
                                                    contentColor = WaveformTealPlayed
                                                )
                                            ) {
                                                Text("Import .lrc")
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                val effectivePosMs = (positionMs + lyricsState.syncOffsetMs).coerceAtLeast(0L)
                                val activeIndex = remember(document, effectivePosMs) {
                                    if (document.isSynced) {
                                        LrcParser.findActiveLineIndex(document.lines, effectivePosMs)
                                    } else {
                                        -1
                                    }
                                }

                                val listState = rememberLazyListState()
                                var viewportHeightPx by remember { mutableIntStateOf(0) }
                                var manualScrollActive by remember { mutableStateOf(false) }

                                LaunchedEffect(listState.isScrollInProgress) {
                                    if (listState.isScrollInProgress) {
                                        manualScrollActive = true
                                    } else if (manualScrollActive) {
                                        delay(3200L)
                                        manualScrollActive = false
                                    }
                                }

                                LaunchedEffect(activeIndex, manualScrollActive, document.rawLrc, viewportHeightPx) {
                                    if (!manualScrollActive && document.isSynced && activeIndex >= 0 && viewportHeightPx > 0) {
                                        val centerOffset = -(viewportHeightPx * 0.34f).toInt()
                                        runCatching {
                                            listState.animateScrollToItem(
                                                index = activeIndex.coerceIn(0, document.lines.lastIndex),
                                                scrollOffset = centerOffset
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(
                                            horizontal = 18.dp,
                                            vertical = 38.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .onSizeChanged { viewportHeightPx = it.height }
                                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        0.0f to Color.Transparent,
                                                        0.10f to Color.Black,
                                                        0.88f to Color.Black,
                                                        1.0f to Color.Transparent
                                                    ),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                            .testTag("floating_lyrics_list")
                                    ) {
                                        itemsIndexed(
                                            items = document.lines,
                                            key = { idx, line -> "${idx}_${line.timeMs}" }
                                        ) { index, line ->
                                            val isActive = index == activeIndex
                                            val isPast = activeIndex >= 0 && index < activeIndex
                                            LrcLineRow(
                                                line = line,
                                                isSynced = document.isSynced,
                                                isActive = isActive,
                                                isPast = isPast,
                                                onCardColor = onCard,
                                                onCardMuted = onCardMuted,
                                                onClick = {
                                                    MixaHaptics.tick(view)
                                                    val targetSeek = (line.timeMs - lyricsState.syncOffsetMs)
                                                        .coerceAtLeast(0L)
                                                    onSeekTo(targetSeek)
                                                    manualScrollActive = false
                                                }
                                            )
                                        }
                                    }

                                    if (manualScrollActive && document.isSynced) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = WaveformTealPlayed,
                                            shadowElevation = 6.dp,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(12.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                                                    manualScrollActive = false
                                                }
                                                .testTag("lyrics_resync_pill")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MyLocation,
                                                    contentDescription = "Auto-scroll to active lyric",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Sync",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                }

                                // Compact LRC Timing Nudge & Tap-to-Seek Footer
                                if (document.isSynced) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Tap any line to seek",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = onCardMuted
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OffsetChip(
                                                label = "-0.5s",
                                                onCard = onCard,
                                                onClick = { onNudgeOffsetMs(-500L) }
                                            )
                                            val offsetSec = lyricsState.syncOffsetMs / 1000.0
                                            Text(
                                                text = if (lyricsState.syncOffsetMs == 0L) "0.0s" else String.format("%+.1fs", offsetSec),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (lyricsState.syncOffsetMs != 0L) WaveformTealPlayed else onCardMuted
                                            )
                                            OffsetChip(
                                                label = "+0.5s",
                                                onCard = onCard,
                                                onClick = { onNudgeOffsetMs(500L) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LrcLineRow(
    line: LrcLine,
    isSynced: Boolean,
    isActive: Boolean,
    isPast: Boolean,
    onCardColor: Color,
    onCardMuted: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lrc_line_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !isSynced -> 0.90f
            isActive -> 1f
            isPast -> 0.40f
            else -> 0.68f
        },
        animationSpec = tween(220),
        label = "lrc_line_alpha"
    )
    val displayText = line.text.ifBlank { "•  •  •" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) WaveformTealPlayed.copy(alpha = 0.14f) else Color.Transparent
            )
            .hapticClickable(enabled = isSynced, kind = MixaHapticKind.TICK, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(alpha)
    ) {
        Text(
            text = displayText,
            style = if (isActive) {
                MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp)
            } else {
                MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp)
            },
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isActive) WaveformTealPlayed else if (isPast) onCardMuted else onCardColor
        )
    }
}

@Composable
private fun OffsetChip(
    label: String,
    onCard: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = onCard.copy(alpha = 0.10f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .hapticClickable(kind = MixaHapticKind.TICK, onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = onCard,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
