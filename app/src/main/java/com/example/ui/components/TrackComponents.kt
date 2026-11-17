package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable
import com.example.ui.theme.hapticCombinedClickable

fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
fun TrackArtwork(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val context = LocalContext.current
    val placeholder = painterResource(id = R.drawable.img_vinyl_placeholder)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artworkUri)
                .crossfade(true)
                .build(),
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun PlaylistCoverArt(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val collage = remember(playlist.tracks) { playlist.collageArtworkUris }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.24f),
                        tertiary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            collage.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        TrackArtwork(
                            artworkUri = collage[0],
                            contentDescription = null,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        TrackArtwork(
                            artworkUri = collage[1],
                            contentDescription = null,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        TrackArtwork(
                            artworkUri = collage[2],
                            contentDescription = null,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        TrackArtwork(
                            artworkUri = collage[3],
                            contentDescription = null,
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
            collage.isNotEmpty() -> {
                TrackArtwork(
                    artworkUri = collage.first(),
                    contentDescription = "${playlist.name} cover",
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "${playlist.name} cover",
                    tint = primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

val HiResGoldAccent = Color(0xFFF5C451)
private val HiResGoldLightText = Color(0xFF8A6500)

@Composable
fun SourceFormatBadge(
    track: Track,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val (bgColor, textColor, label) = when (track.source) {
        TrackSource.DIRECT_STREAM -> {
            val baseLabel = if (track.isHiRes) {
                "HI-RES • ${track.qualityBadgeLabel}"
            } else {
                track.qualityBadgeLabel
            }
            val fullLabel = if (track.isLiveStream) "$baseLabel • LIVE" else baseLabel
            when {
                track.isHiRes -> Triple(
                    HiResGoldAccent.copy(alpha = if (tokens.isDark) 0.20f else 0.24f),
                    if (tokens.isDark) HiResGoldAccent else HiResGoldLightText,
                    fullLabel
                )
                track.isLossless -> Triple(
                    WaveformTealPlayed.copy(alpha = if (tokens.isDark) 0.18f else 0.20f),
                    if (tokens.isDark) WaveformTealPlayed else Color(0xFF007A66),
                    fullLabel
                )
                else -> Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.secondary,
                    fullLabel
                )
            }
        }
        TrackSource.EXTENSION -> {
            val baseLabel = track.qualityBadgeLabel
            when {
                track.isHiRes -> Triple(
                    HiResGoldAccent.copy(alpha = if (tokens.isDark) 0.20f else 0.24f),
                    if (tokens.isDark) HiResGoldAccent else HiResGoldLightText,
                    baseLabel
                )
                track.isLossless -> Triple(
                    WaveformTealPlayed.copy(alpha = if (tokens.isDark) 0.18f else 0.20f),
                    if (tokens.isDark) WaveformTealPlayed else Color(0xFF007A66),
                    baseLabel
                )
                else -> Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.secondary,
                    baseLabel
                )
            }
        }
        TrackSource.YOUTUBE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            "YT • ${track.audioFormatLabel}"
        )
        TrackSource.LOCAL -> Triple(
            if (track.isHiRes) HiResGoldAccent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.tertiaryContainer,
            if (track.isHiRes) (if (tokens.isDark) HiResGoldAccent else HiResGoldLightText) else MaterialTheme.colorScheme.tertiary,
            track.qualityBadgeLabel
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (track.isHiRes || track.isLossless) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(510, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.4f, 0.7f, 0.5f)
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16 * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun TrackRowItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onStartRadio: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDeleteDirectStream: (() -> Unit)? = null,
    showRadioIconButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardCornerRadius))
            .background(containerColor)
            .hapticCombinedClickable(
                clickKind = MixaHapticKind.CONFIRM,
                onLongClick = { menuExpanded = true },
                onClick = onClick
            )
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .testTag("track_item_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            TrackArtwork(
                artworkUri = track.artworkUri,
                contentDescription = "${track.title} artwork",
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 11.dp
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(isPlaying = isPlaying)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isCurrent) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.album.isNotBlank()) {
                        append(" • ")
                        append(track.album)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceFormatBadge(track = track)
                if (track.durationMs > 0L) {
                    Text(
                        text = formatDurationMs(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showRadioIconButton &&
            onStartRadio != null &&
            track.source == TrackSource.YOUTUBE &&
            !track.videoId.isNullOrBlank()
        ) {
            IconButton(
                onClick = {
                    MixaHaptics.tap(view)
                    onStartRadio()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("start_radio_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = "Start Radio",
                    tint = secondary
                )
            }
        }

        IconButton(
            onClick = {
                MixaHaptics.toggle(view, !isFavorite)
                onToggleFavorite()
            },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("favorite_button_${track.id}")
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(
                onClick = {
                    MixaHaptics.tap(view)
                    menuExpanded = true
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("track_menu_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                    onClick = {
                        MixaHaptics.tap(view)
                        menuExpanded = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Up Next") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                    onClick = {
                        MixaHaptics.tap(view)
                        menuExpanded = false
                        onAddToQueue()
                    }
                )
                if (onStartRadio != null && track.source == TrackSource.YOUTUBE && !track.videoId.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Start Radio Mix") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Radio, contentDescription = null, tint = secondary)
                        },
                        onClick = {
                            MixaHaptics.tap(view)
                            menuExpanded = false
                            onStartRadio()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add to Hybrid Playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        MixaHaptics.tap(view)
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            MixaHaptics.reject(view)
                            menuExpanded = false
                            onRemoveFromPlaylist()
                        }
                    )
                }
                if (onDeleteDirectStream != null) {
                    DropdownMenuItem(
                        text = { Text("Remove Stream", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            MixaHaptics.reject(view)
                            menuExpanded = false
                            onDeleteDirectStream()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newPlaylistName by remember { mutableStateOf("") }
    var showCreateField by remember { mutableStateOf(playlists.isEmpty()) }
    val view = LocalView.current
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary

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
                .testTag("add_to_playlist_sheet"),
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                            .background(primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = primary
                        )
                    }
                    Column {
                        Text(
                            text = "Add to Hybrid Playlist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mix local, stream & radio tracks together",
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
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Target Track Preview
            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 10.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceFormatBadge(track = track)
                }
            }

            if (playlists.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Surface(
                            shape = RoundedCornerShape(tokens.cardCornerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.cardCornerRadius))
                                .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                                    onSelectPlaylist(playlist.id)
                                    onDismiss()
                                }
                                .testTag("add_to_playlist_option_${playlist.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistCoverArt(
                                    playlist = playlist,
                                    modifier = Modifier.size(46.dp),
                                    cornerRadius = 10.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val mixParts = buildList {
                                        add("${playlist.trackCount} tracks")
                                        if (playlist.localCount > 0) add("${playlist.localCount} Local")
                                        if (playlist.youtubeCount > 0) add("${playlist.youtubeCount} YT")
                                        if (playlist.extensionCount > 0) add("${playlist.extensionCount} Ext")
                                        if (playlist.radioCount > 0) add("${playlist.radioCount} Radio")
                                    }.joinToString(" • ")
                                    Text(
                                        text = mixParts,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = primary.copy(alpha = 0.16f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add to ${playlist.name}",
                                        tint = primary,
                                        modifier = Modifier.padding(6.dp).size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showCreateField) {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("New playlist name") },
                    placeholder = { Text("Late Night FLAC Mix") },
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
                        .testTag("new_playlist_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            if (playlists.isNotEmpty()) {
                                showCreateField = false
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (playlists.isNotEmpty()) "Back" else "Cancel")
                    }
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                MixaHaptics.confirm(view)
                                onCreateAndAdd(newPlaylistName.trim())
                                onDismiss()
                            }
                        },
                        enabled = newPlaylistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_create_and_add_button")
                    ) {
                        Text("Create & Add")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        showCreateField = true
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_create_playlist_field")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create New Playlist")
                }
            }
        }
    }
}

