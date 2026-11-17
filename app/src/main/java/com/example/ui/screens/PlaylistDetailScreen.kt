package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlaybackState
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import com.example.ui.components.HiResGoldAccent
import com.example.ui.components.PlaylistCoverArt
import com.example.ui.components.SourceFormatBadge
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

private enum class TrackPickerSourceFilter(val label: String) {
    ALL("All"),
    LOCAL("Local"),
    ONLINE("YT & Ext"),
    RADIO("Radio")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    availableTracks: List<Track>,
    playbackState: PlaybackState,
    favoriteIds: Set<String>,
    allPlaylists: List<Playlist>,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onStartRadio: (Track) -> Unit,
    onAddTrackToThisPlaylist: (Track) -> Unit,
    onAddTrackToOtherPlaylist: (Long, Track) -> Unit,
    onCreatePlaylistAndAdd: (String, Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onMoveTrack: (List<Track>, Int, Int) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit = { _, _, _ -> },
    onDeletePlaylist: (Long) -> Unit = {},
    onAddTracksToQueue: (List<Track>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    var showTrackPicker by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    MixaHaptics.tap(view)
                    onBack()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("playlist_detail_back")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = playlist?.name ?: "Playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (playlist != null) {
                FilledTonalButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        showTrackPicker = true
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("add_tracks_to_playlist_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        showOptionsSheet = true
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_detail_options_button")
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                }
            }
        }

        if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Playlist not found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hero Playlist Summary Card
        Surface(
            shape = RoundedCornerShape(tokens.heroCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tertiary.copy(alpha = 0.20f),
                                primary.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlaylistCoverArt(
                        playlist = playlist,
                        modifier = Modifier.size(84.dp),
                        cornerRadius = tokens.cardCornerRadius
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "Unified Track" else "Unified Tracks"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (playlist.localCount > 0) {
                                PlaylistMixBadge(text = "${playlist.localCount} Local", color = tertiary)
                            }
                            if (playlist.youtubeCount > 0) {
                                PlaylistMixBadge(text = "${playlist.youtubeCount} YouTube", color = secondary)
                            }
                            if (playlist.extensionCount > 0) {
                                PlaylistMixBadge(text = "${playlist.extensionCount} Extension", color = tokens.emeraldAccent)
                            }
                            if (playlist.radioCount > 0) {
                                PlaylistMixBadge(text = "${playlist.radioCount} Radio", color = HiResGoldAccent)
                            }
                            if (playlist.totalDurationMs > 0L) {
                                PlaylistMixBadge(
                                    text = formatDurationMs(playlist.totalDurationMs),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (playlist.description.isNotBlank()) {
                            Text(
                                text = playlist.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (playlist.tracks.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                MixaHaptics.confirm(view)
                                onPlayTracks(playlist.tracks, 0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("playlist_play_mix_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Mix")
                        }
                        FilledTonalButton(
                            onClick = {
                                MixaHaptics.confirm(view)
                                onPlayTracks(playlist.tracks.shuffled(), 0)
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("playlist_shuffle_mix_button")
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = secondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                        if (playlist.tracks.size > 1) {
                            FilledTonalButton(
                                onClick = {
                                    MixaHaptics.toggle(view, !reorderMode)
                                    reorderMode = !reorderMode
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (reorderMode) primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (reorderMode) primary else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("playlist_reorder_toggle_button")
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = "Toggle reorder controls", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (playlist.tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Your hybrid mix is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add local FLAC/MP3 tracks, YouTube Music songs, or lossless radio streams to build this playlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            showTrackPicker = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Tracks")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                itemsIndexed(playlist.tracks, key = { _, t -> t.id }) { index, track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AnimatedVisibility(
                            visible = reorderMode,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(tokens.chipCornerRadius),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                MixaHaptics.tick(view)
                                                onMoveTrack(playlist.tracks, index, index - 1)
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = "Move up",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < playlist.tracks.lastIndex) {
                                                MixaHaptics.tick(view)
                                                onMoveTrack(playlist.tracks, index, index + 1)
                                            }
                                        },
                                        enabled = index < playlist.tracks.lastIndex,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowDownward,
                                            contentDescription = "Move down",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        TrackRowItem(
                            track = track,
                            isCurrent = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            isFavorite = track.id in favoriteIds || track.isFavorite,
                            onClick = { onPlayTracks(playlist.tracks, index) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddTrackToOtherPlaylist(playlist.id, track) },
                            onStartRadio = { onStartRadio(track) },
                            onRemoveFromPlaylist = { onRemoveTrack(track.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    val activePlaylist = playlist
    if (showTrackPicker && activePlaylist != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var pickerQuery by remember { mutableStateOf("") }
        var sourceFilter by remember { mutableStateOf(TrackPickerSourceFilter.ALL) }
        val existingIds = remember(activePlaylist.tracks) { activePlaylist.tracks.map { it.id }.toSet() }

        val candidates = remember(availableTracks, pickerQuery, sourceFilter) {
            availableTracks.filter { track ->
                val matchesSource = when (sourceFilter) {
                    TrackPickerSourceFilter.ALL -> true
                    TrackPickerSourceFilter.LOCAL -> track.source == TrackSource.LOCAL
                    TrackPickerSourceFilter.ONLINE -> track.source == TrackSource.YOUTUBE || track.source == TrackSource.EXTENSION
                    TrackPickerSourceFilter.RADIO -> track.source == TrackSource.DIRECT_STREAM
                }
                val q = pickerQuery.trim()
                val matchesQuery = q.isEmpty() ||
                    track.title.contains(q, ignoreCase = true) ||
                    track.artist.contains(q, ignoreCase = true) ||
                    track.album.contains(q, ignoreCase = true)
                matchesSource && matchesQuery
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showTrackPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("playlist_add_tracks_sheet"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                text = "Add Tracks",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "To ${activePlaylist.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            showTrackPicker = false
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text("Done")
                    }
                }

                TextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it },
                    placeholder = { Text("Filter local, YouTube & radio tracks…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (pickerQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                MixaHaptics.tap(view)
                                pickerQuery = ""
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear filter")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(tokens.cardCornerRadius),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TrackPickerSourceFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            selected = sourceFilter == filter,
                            onClick = {
                                MixaHaptics.tap(view)
                                sourceFilter = filter
                            },
                            label = { Text(filter.label) },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = primary.copy(alpha = 0.22f),
                                selectedLabelColor = primary
                            )
                        )
                    }
                }

                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching tracks indexed yet. Scan device audio or search Explore to index more tracks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates, key = { it.id }) { track ->
                            val alreadyAdded = track.id in existingIds
                            Surface(
                                shape = RoundedCornerShape(tokens.cardCornerRadius),
                                color = if (alreadyAdded) {
                                    tokens.emeraldAccent.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(tokens.cardCornerRadius))
                                    .hapticClickable(
                                        kind = MixaHapticKind.CONFIRM,
                                        enabled = !alreadyAdded
                                    ) {
                                        onAddTrackToThisPlaylist(track)
                                    }
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
                                    Spacer(modifier = Modifier.width(6.dp))
                                    SourceFormatBadge(track = track)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (alreadyAdded) {
                                            tokens.emeraldAccent.copy(alpha = 0.20f)
                                        } else {
                                            primary.copy(alpha = 0.16f)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (alreadyAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = if (alreadyAdded) "Added" else "Add track",
                                            tint = if (alreadyAdded) tokens.emeraldAccent else primary,
                                            modifier = Modifier.padding(6.dp).size(18.dp)
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

    if (showOptionsSheet && activePlaylist != null) {
        PlaylistOptionsSheet(
            playlist = activePlaylist,
            onDismiss = { showOptionsSheet = false },
            onOpen = null,
            onPlayAll = {
                if (activePlaylist.tracks.isNotEmpty()) onPlayTracks(activePlaylist.tracks, 0)
            },
            onShuffle = {
                if (activePlaylist.tracks.isNotEmpty()) onPlayTracks(activePlaylist.tracks.shuffled(), 0)
            },
            onAddToQueue = {
                if (activePlaylist.tracks.isNotEmpty()) onAddTracksToQueue(activePlaylist.tracks)
            },
            onEdit = { showEditSheet = true },
            onRequestDelete = { showDeleteConfirm = true }
        )
    }

    if (showEditSheet && activePlaylist != null) {
        PlaylistEditorSheet(
            title = "Edit Playlist",
            subtitle = "Update collection title & notes",
            initialName = activePlaylist.name,
            initialDescription = activePlaylist.description,
            isEdit = true,
            onDismiss = { showEditSheet = false },
            onSave = { name, desc ->
                onRenamePlaylist(activePlaylist.id, name, desc)
                showEditSheet = false
            }
        )
    }

    if (showDeleteConfirm && activePlaylist != null) {
        PlaylistDeleteConfirmDialog(
            playlistName = activePlaylist.name,
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                showDeleteConfirm = false
                onDeletePlaylist(activePlaylist.id)
                onBack()
            }
        )
    }
}
