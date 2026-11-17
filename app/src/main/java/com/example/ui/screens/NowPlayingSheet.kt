package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.EqualizerState
import com.example.domain.model.PlaybackState
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.Playlist
import com.example.domain.model.RepeatModeState
import com.example.domain.model.StreamErrorCategory
import com.example.domain.model.StreamErrorInfo
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import com.example.ui.LyricsUiState
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.FloatingLyricsOverlay
import com.example.ui.components.FrostedGlassPill
import com.example.ui.components.HiResGoldAccent
import com.example.ui.components.StreamQualitySwitcherSheet
import com.example.ui.components.TrackArtwork
import com.example.ui.components.UpNextPeekOverlay
import com.example.ui.components.WaveformScrubber
import com.example.ui.components.WaveformTealPlayed
import com.example.ui.components.rememberArtworkMeshColors
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    favoriteIds: Set<String>,
    playlists: List<Playlist>,
    equalizerState: EqualizerState = EqualizerState(),
    directFloatPcmBypassEq: Boolean = false,
    isYoutubeSignedIn: Boolean = false,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onStartRadio: (Track) -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenEqualizer: () -> Unit,
    onAddTrackToPlaylist: (Long, Track) -> Unit,
    onCreatePlaylistAndAdd: (String, Track) -> Unit,
    onRetryStream: () -> Unit = {},
    onSwitchStreamFormat: () -> Unit = {},
    onSwitchStreamVariant: (String) -> Unit = {},
    onToggleDirectPcmBypass: (Boolean) -> Unit = {},
    onOpenAccountSheet: () -> Unit = {},
    onJumpToQueueIndex: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = {},
    onToggleAutoRadio: (Boolean) -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    lyricsState: LyricsUiState = LyricsUiState(),
    onRefreshLyrics: () -> Unit = {},
    onImportLrcUri: (Uri) -> Unit = {},
    onNudgeLyricsOffsetMs: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showQualitySwitcher by remember { mutableStateOf(false) }
    var showFloatingLyrics by remember { mutableStateOf(false) }
    var isQueueExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = !isQueueExpanded && !showQualitySwitcher && !showFloatingLyrics, onBack = onDismiss)

    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val baseBg = if (tokens.isDark) Color.Black else MaterialTheme.colorScheme.background
    val onBg = if (tokens.isDark) Color.White else MaterialTheme.colorScheme.onBackground
    val onBgMuted = if (tokens.isDark) Color(0xFFA8ACB4) else MaterialTheme.colorScheme.onSurfaceVariant

    val track = playbackState.currentTrack

    val meshColors = rememberArtworkMeshColors(
        artworkUri = track?.artworkUri,
        fallbackPrimary = Color(0xFFB52B2B),
        fallbackSecondary = Color(0xFFD49234)
    )

    val artworkScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "artwork_scale"
    )

    var sheetDragDownY by remember { mutableFloatStateOf(0f) }
    var sheetDismissedByDrag by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .pointerInput(isQueueExpanded) {
                if (!isQueueExpanded) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            sheetDragDownY = 0f
                            sheetDismissedByDrag = false
                            MixaHaptics.gestureStart(view)
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (sheetDismissedByDrag) return@detectVerticalDragGestures
                            sheetDragDownY += dragAmount
                            if (sheetDragDownY > 110f) {
                                sheetDismissedByDrag = true
                                MixaHaptics.gestureEnd(view)
                                onDismiss()
                            }
                        },
                        onDragEnd = {
                            sheetDragDownY = 0f
                            sheetDismissedByDrag = false
                        },
                        onDragCancel = {
                            sheetDragDownY = 0f
                            sheetDismissedByDrag = false
                        }
                    )
                }
            }
    ) {
        if (tokens.backdropStyle == PlayerBackdropStyle.ARTWORK_BLUR) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track?.artworkUri)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(id = R.drawable.img_vinyl_placeholder),
                error = painterResource(id = R.drawable.img_vinyl_placeholder),
                fallback = painterResource(id = R.drawable.img_vinyl_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(46.dp)
                    .alpha(if (tokens.isDark) 0.24f else 0.16f)
            )
        }

        // Dynamic radial mesh glow extracted from the album artwork fading to pure #000000 black
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val meshAlphaPrimary = when {
                tokens.backdropStyle == PlayerBackdropStyle.MINIMAL -> if (tokens.isDark) 0.16f else 0.08f
                tokens.isDark -> 0.34f
                else -> 0.18f
            }
            val meshAlphaSecondary = when {
                tokens.backdropStyle == PlayerBackdropStyle.MINIMAL -> if (tokens.isDark) 0.10f else 0.06f
                tokens.isDark -> 0.24f
                else -> 0.13f
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        meshColors.primaryGlow.copy(alpha = meshAlphaPrimary),
                        meshColors.primaryGlow.copy(alpha = meshAlphaPrimary * 0.38f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.34f, h * 0.30f),
                    radius = w * 0.95f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        meshColors.secondaryGlow.copy(alpha = meshAlphaSecondary),
                        meshColors.secondaryGlow.copy(alpha = meshAlphaSecondary * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.74f, h * 0.37f),
                    radius = w * 0.85f
                )
            )
            // Smooth fade to pure black (#000000) toward the bottom controls & drawer dock
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.46f to Color.Transparent,
                    0.78f to baseBg.copy(alpha = 0.86f),
                    1.0f to baseBg
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onDismiss()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = onBg,
                        modifier = Modifier.size(30.dp)
                    )
                }

                val tierAccent = when {
                    track?.isHiRes == true -> HiResGoldAccent
                    else -> WaveformTealPlayed
                }
                val headerLabel = when {
                    playbackState.isRadioActive -> "AUTO-RADIO MIX"
                    track?.isHiRes == true -> "HI-RES LOSSLESS"
                    track?.isLossless == true -> "CD LOSSLESS"
                    else -> "NOW PLAYING"
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .hapticClickable(enabled = track != null, kind = MixaHapticKind.TAP) {
                            showQualitySwitcher = true
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("now_playing_quality_pill")
                ) {
                    Text(
                        text = headerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tierAccent
                    )
                    if (playbackState.activeStreamInfo.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = playbackState.activeStreamInfo,
                                style = MaterialTheme.typography.labelSmall,
                                color = onBgMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Switch stream quality",
                                tint = onBgMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onOpenEqualizer()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_eq_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Studio Equalizer",
                        tint = onBg
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Showcase Artwork with ambient colored drop shadow + 1px inner glass edge + swipe-to-skip
            val heroRadius = tokens.heroCornerRadius
            val heroShape = RoundedCornerShape(heroRadius)
            var artworkDragX by remember { mutableFloatStateOf(0f) }
            var artworkSkipTriggered by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .aspectRatio(1f)
                    .scale(artworkScale)
                    .pointerInput(track?.id) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                artworkDragX = 0f
                                artworkSkipTriggered = false
                                MixaHaptics.gestureStart(view)
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (artworkSkipTriggered) return@detectHorizontalDragGestures
                                artworkDragX += dragAmount
                                if (artworkDragX < -72f) {
                                    artworkSkipTriggered = true
                                    MixaHaptics.confirm(view)
                                    onSkipNext()
                                } else if (artworkDragX > 72f) {
                                    artworkSkipTriggered = true
                                    MixaHaptics.confirm(view)
                                    onSkipPrevious()
                                }
                            },
                            onDragEnd = {
                                artworkDragX = 0f
                                artworkSkipTriggered = false
                            },
                            onDragCancel = {
                                artworkDragX = 0f
                                artworkSkipTriggered = false
                            }
                        )
                    }
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    meshColors.primaryGlow.copy(alpha = if (tokens.isDark) 0.44f else 0.28f),
                                    meshColors.secondaryGlow.copy(alpha = if (tokens.isDark) 0.18f else 0.10f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height * 0.56f),
                                radius = size.width * 0.72f
                            ),
                            radius = size.width * 0.72f,
                            center = Offset(size.width / 2f, size.height * 0.56f)
                        )
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = heroShape,
                        ambientColor = meshColors.primaryGlow,
                        spotColor = meshColors.primaryGlow
                    )
                    .clip(heroShape)
            ) {
                TrackArtwork(
                    artworkUri = track?.artworkUri,
                    contentDescription = track?.title ?: "Album artwork",
                    cornerRadius = heroRadius,
                    modifier = Modifier.fillMaxSize()
                )
                // 1px inner glass border highlight
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cornerPx = heroRadius.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (tokens.isDark) 0.24f else 0.34f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = 1.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            if (track != null) {
                // Title, Artist, Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = onBg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = onBgMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val icyNowPlaying = playbackState.audioTelemetry?.liveIcyTitle
                            ?.takeIf { it.isNotBlank() && !it.equals(track.title, ignoreCase = true) }
                        if (track.source == TrackSource.DIRECT_STREAM && icyNowPlaying != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "On Air: $icyNowPlaying",
                                style = MaterialTheme.typography.labelMedium,
                                color = WaveformTealPlayed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val isFav = track.id in favoriteIds || track.isFavorite
                    IconButton(
                        onClick = {
                            MixaHaptics.toggle(view, !isFav)
                            onToggleFavorite(track)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (isFav) WaveformTealPlayed else onBg,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (playbackState.streamError != null || playbackState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val info = playbackState.streamError ?: StreamErrorInfo(
                        category = StreamErrorCategory.UNKNOWN,
                        title = playbackState.errorMessage ?: "Stream playback interrupted",
                        detail = "Tap Retry or switch audio format to recover playback.",
                        canSwitchFormat = track.source == TrackSource.YOUTUBE
                    )
                    StreamErrorRecoveryCard(
                        errorInfo = info,
                        isYoutubeSignedIn = isYoutubeSignedIn,
                        hasNextTrack = playbackState.currentIndex in 0 until playbackState.queue.lastIndex,
                        onRetry = onRetryStream,
                        onSwitchFormat = onSwitchStreamFormat,
                        onSkipNext = onSkipNext,
                        onSignIn = onOpenAccountSheet
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive 45-Bar Audio Waveform Scrubber
                WaveformScrubber(
                    trackId = track.id,
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                    isPlaying = playbackState.isPlaying,
                    isLiveStream = track.isLiveStream,
                    onSeekTo = onSeekTo
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            MixaHaptics.toggle(view, !playbackState.shuffleEnabled)
                            onToggleShuffle()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleEnabled) WaveformTealPlayed else onBgMuted
                        )
                    }

                    IconButton(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onSkipPrevious()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("now_playing_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = onBg,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(WaveformTealPlayed, tertiary.copy(alpha = 0.85f).let {
                                        if (tokens.accent.name == "MONOCHROME") Color(0xFF42C5DC) else Color(0xFF3FB8E6)
                                    })
                                )
                            )
                            .hapticClickable(kind = MixaHapticKind.CONFIRM, onClick = onTogglePlayPause)
                            .testTag("now_playing_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onSkipNext()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("now_playing_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            tint = onBg,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onCycleRepeat()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_repeat_button")
                    ) {
                        val icon = if (playbackState.repeatMode == RepeatModeState.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        }
                        val tint = if (playbackState.repeatMode != RepeatModeState.OFF) {
                            WaveformTealPlayed
                        } else {
                            onBgMuted
                        }
                        Icon(imageVector = icon, contentDescription = "Repeat mode", tint = tint)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Frosted Glass Utility Pills (Radio & Playlist; Queue moved to docked swipe-up Up Next drawer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                ) {
                    if (track.source == TrackSource.YOUTUBE && !track.videoId.isNullOrBlank()) {
                        FrostedGlassPill(
                            icon = Icons.Rounded.Radio,
                            label = if (playbackState.isLoadingRadio) "Loading…" else "Radio",
                            tint = WaveformTealPlayed,
                            isDark = tokens.isDark,
                            onClick = { onStartRadio(track) },
                            tag = "now_playing_radio_button"
                        )
                    }
                    FrostedGlassPill(
                        icon = Icons.Rounded.Lyrics,
                        label = if (showFloatingLyrics) "Hide Lyrics" else "Lyrics",
                        tint = if (showFloatingLyrics || lyricsState.document?.isSynced == true) WaveformTealPlayed else onBg,
                        isDark = tokens.isDark,
                        onClick = { showFloatingLyrics = !showFloatingLyrics },
                        tag = "now_playing_lyrics_button"
                    )
                    FrostedGlassPill(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = "Playlist",
                        tint = onBg,
                        isDark = tokens.isDark,
                        onClick = { showAddToPlaylist = true },
                        tag = "now_playing_add_playlist_button"
                    )
                }

                // Clear space above the docked Up Next peek tab & navigation bar
                Spacer(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(92.dp)
                )
            }
        }

        FloatingLyricsOverlay(
            visible = showFloatingLyrics && !isQueueExpanded,
            track = track,
            lyricsState = lyricsState,
            positionMs = playbackState.positionMs,
            accentColor = meshColors.primaryGlow,
            onSeekTo = onSeekTo,
            onRefreshLyrics = onRefreshLyrics,
            onImportLrcUri = onImportLrcUri,
            onNudgeOffsetMs = onNudgeLyricsOffsetMs,
            onDismiss = { showFloatingLyrics = false }
        )

        // Docked Swipe-Up / Clickable "Up Next" Queue Sheet Overlay
        UpNextPeekOverlay(
            playbackState = playbackState,
            isExpanded = isQueueExpanded,
            onExpandedChange = { isQueueExpanded = it },
            onJumpToIndex = onJumpToQueueIndex,
            onMoveItem = onMoveQueueItem,
            onRemoveItem = onRemoveQueueItem,
            onToggleAutoRadio = onToggleAutoRadio,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist
        )
    }

    if (showAddToPlaylist && track != null) {
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onSelectPlaylist = { playlistId -> onAddTrackToPlaylist(playlistId, track) },
            onCreateAndAdd = { name -> onCreatePlaylistAndAdd(name, track) }
        )
    }

    if (showQualitySwitcher && track != null) {
        StreamQualitySwitcherSheet(
            playbackState = playbackState,
            directFloatPcmBypassEq = directFloatPcmBypassEq,
            isEqBypassedNow = equalizerState.isBypassedForDirectPcm,
            onDismiss = { showQualitySwitcher = false },
            onSelectVariant = onSwitchStreamVariant,
            onToggleDirectPcmBypass = onToggleDirectPcmBypass
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamErrorRecoveryCard(
    errorInfo: StreamErrorInfo,
    isYoutubeSignedIn: Boolean,
    hasNextTrack: Boolean,
    onRetry: () -> Unit,
    onSwitchFormat: () -> Unit,
    onSkipNext: () -> Unit,
    onSignIn: () -> Unit
) {
    val tokens = LocalSonoraTokens.current
    val errorBg = MaterialTheme.colorScheme.errorContainer
    val onErrorBg = MaterialTheme.colorScheme.onErrorContainer

    val leadIcon = when (errorInfo.category) {
        StreamErrorCategory.NETWORK_UNAVAILABLE -> Icons.Rounded.WifiOff
        StreamErrorCategory.RESTRICTED_OR_LOGIN -> Icons.Rounded.VerifiedUser
        else -> Icons.Rounded.ErrorOutline
    }

    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        color = errorBg,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stream_error_recovery_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = leadIcon,
                    contentDescription = null,
                    tint = onErrorBg,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = errorInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onErrorBg
                    )
                    Text(
                        text = errorInfo.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = onErrorBg.copy(alpha = 0.88f)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecoveryActionPill(
                    icon = Icons.Rounded.Refresh,
                    label = "Retry",
                    containerColor = onErrorBg.copy(alpha = 0.16f),
                    contentColor = onErrorBg,
                    onClick = onRetry,
                    tag = "error_retry_button"
                )
                if (errorInfo.canSwitchFormat) {
                    RecoveryActionPill(
                        icon = Icons.Rounded.SwapHoriz,
                        label = "Try AAC / HLS",
                        containerColor = onErrorBg.copy(alpha = 0.12f),
                        contentColor = onErrorBg,
                        onClick = onSwitchFormat,
                        tag = "error_switch_format_button"
                    )
                }
                if (hasNextTrack) {
                    RecoveryActionPill(
                        icon = Icons.Rounded.SkipNext,
                        label = "Skip Track",
                        containerColor = onErrorBg.copy(alpha = 0.12f),
                        contentColor = onErrorBg,
                        onClick = onSkipNext,
                        tag = "error_skip_next_button"
                    )
                }
                if (errorInfo.suggestsSignIn && !isYoutubeSignedIn) {
                    RecoveryActionPill(
                        icon = Icons.Rounded.AccountCircle,
                        label = "Sign In",
                        containerColor = onErrorBg.copy(alpha = 0.16f),
                        contentColor = onErrorBg,
                        onClick = onSignIn,
                        tag = "error_sign_in_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryActionPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    val tokens = LocalSonoraTokens.current
    Surface(
        shape = RoundedCornerShape(tokens.chipCornerRadius),
        color = containerColor,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.chipCornerRadius))
            .hapticClickable(kind = MixaHapticKind.CONFIRM, onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
