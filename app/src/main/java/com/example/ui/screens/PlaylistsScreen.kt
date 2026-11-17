package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.ui.components.HiResGoldAccent
import com.example.ui.components.PlaylistCoverArt
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable
import com.example.ui.theme.hapticCombinedClickable

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    onPlayPlaylist: (List<Track>) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddPlaylistToQueue: (List<Track>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var optionsPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var confirmingDeletePlaylist by remember { mutableStateOf<Playlist?>(null) }

    val totalTracksCount = remember(playlists) { playlists.sumOf { it.trackCount } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

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
                                tertiary.copy(alpha = 0.22f),
                                primary.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "HYBRID COLLECTIONS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = secondary
                    )
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${playlists.size} playlists • $totalTracksCount unified tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        MixaHaptics.confirm(view)
                        showCreateSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    modifier = Modifier.testTag("create_playlist_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_create_playlist))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (playlists.isEmpty()) {
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
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(tokens.heroCornerRadius))
                            .background(tertiary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = tertiary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No hybrid playlists yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Combine offline device FLAC/MP3 files, YouTube Music tracks, and live lossless radio stations in one unified queue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            showCreateSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Playlist")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onPlay = {
                            if (playlist.tracks.isNotEmpty()) {
                                onPlayPlaylist(playlist.tracks)
                            }
                        },
                        onOpenOptions = { optionsPlaylist = playlist }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        PlaylistEditorSheet(
            title = "New Hybrid Playlist",
            subtitle = "Mix local files, streams & radio links together",
            initialName = "",
            initialDescription = "",
            isEdit = false,
            onDismiss = { showCreateSheet = false },
            onSave = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreateSheet = false
            }
        )
    }

    editingPlaylist?.let { target ->
        PlaylistEditorSheet(
            title = "Edit Playlist",
            subtitle = "Update collection title & notes",
            initialName = target.name,
            initialDescription = target.description,
            isEdit = true,
            onDismiss = { editingPlaylist = null },
            onSave = { name, desc ->
                onRenamePlaylist(target.id, name, desc)
                editingPlaylist = null
            }
        )
    }

    optionsPlaylist?.let { target ->
        PlaylistOptionsSheet(
            playlist = target,
            onDismiss = { optionsPlaylist = null },
            onOpen = { onOpenPlaylist(target.id) },
            onPlayAll = {
                if (target.tracks.isNotEmpty()) onPlayPlaylist(target.tracks)
            },
            onShuffle = {
                if (target.tracks.isNotEmpty()) onPlayPlaylist(target.tracks.shuffled())
            },
            onAddToQueue = {
                if (target.tracks.isNotEmpty()) onAddPlaylistToQueue(target.tracks)
            },
            onEdit = { editingPlaylist = target },
            onRequestDelete = { confirmingDeletePlaylist = target }
        )
    }

    confirmingDeletePlaylist?.let { target ->
        PlaylistDeleteConfirmDialog(
            playlistName = target.name,
            onDismiss = { confirmingDeletePlaylist = null },
            onConfirmDelete = {
                onDeletePlaylist(target.id)
                confirmingDeletePlaylist = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onOpenOptions: () -> Unit
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardCornerRadius))
            .hapticCombinedClickable(
                clickKind = MixaHapticKind.CONFIRM,
                onLongClick = onOpenOptions,
                onClick = onClick
            )
            .testTag("playlist_card_${playlist.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCoverArt(
                playlist = playlist,
                modifier = Modifier.size(68.dp),
                cornerRadius = tokens.chipCornerRadius
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlaylistMixBadge(
                        text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                        color = primary
                    )
                    if (playlist.localCount > 0) {
                        PlaylistMixBadge(text = "${playlist.localCount} Local", color = tertiary)
                    }
                    if (playlist.youtubeCount > 0) {
                        PlaylistMixBadge(text = "${playlist.youtubeCount} YT", color = secondary)
                    }
                    if (playlist.extensionCount > 0) {
                        PlaylistMixBadge(text = "${playlist.extensionCount} Ext", color = tokens.emeraldAccent)
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
            }

            if (playlist.tracks.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primary.copy(alpha = 0.16f),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .hapticClickable(kind = MixaHapticKind.CONFIRM, onClick = onPlay)
                        .testTag("play_playlist_button_${playlist.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play playlist",
                            tint = primary
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    MixaHaptics.tap(view)
                    onOpenOptions()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("playlist_options_button_${playlist.id}")
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Playlist options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlaylistMixBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorSheet(
    title: String,
    subtitle: String,
    initialName: String,
    initialDescription: String,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
                            imageVector = if (isEdit) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = primary
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
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

            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                placeholder = { Text("e.g. Night Drive FLAC + YT") },
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
                    .testTag("playlist_editor_name_input")
            )

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Vibe, genre or bitrate mix notes") },
                maxLines = 2,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onDismiss()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            MixaHaptics.confirm(view)
                            onSave(name.trim(), description.trim())
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("playlist_editor_save_button")
                ) {
                    Text(if (isEdit) "Save Changes" else "Create Playlist")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

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
                .testTag("playlist_options_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PlaylistCoverArt(
                    playlist = playlist,
                    modifier = Modifier.size(54.dp),
                    cornerRadius = 12.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.trackCount} tracks • Hybrid Collection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onDismiss()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close options")
                }
            }

            if (onOpen != null) {
                PlaylistOptionActionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "Show Playlist",
                    subtitle = "Inspect track mix & reorder",
                    accent = primary,
                    onClick = {
                        onDismiss()
                        onOpen()
                    }
                )
            }

            if (playlist.tracks.isNotEmpty()) {
                PlaylistOptionActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = "Play Mix",
                    subtitle = "Start from track 1",
                    accent = primary,
                    onClick = {
                        onDismiss()
                        onPlayAll()
                    }
                )
                PlaylistOptionActionRow(
                    icon = Icons.Rounded.Shuffle,
                    title = "Shuffle Mix",
                    subtitle = "Play all tracks in random order",
                    accent = secondary,
                    onClick = {
                        onDismiss()
                        onShuffle()
                    }
                )
                PlaylistOptionActionRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Add Mix to Up Next",
                    subtitle = "Append all ${playlist.trackCount} tracks to active queue",
                    accent = tokens.emeraldAccent,
                    onClick = {
                        onDismiss()
                        onAddToQueue()
                    }
                )
            }

            PlaylistOptionActionRow(
                icon = Icons.Rounded.Edit,
                title = "Rename & Edit Notes",
                subtitle = "Change playlist name or description",
                accent = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onEdit()
                }
            )

            PlaylistOptionActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Delete Playlist",
                subtitle = "Remove collection (tracks stay in library)",
                accent = MaterialTheme.colorScheme.error,
                kind = MixaHapticKind.REJECT,
                onClick = {
                    onDismiss()
                    onRequestDelete()
                }
            )
        }
    }
}

@Composable
private fun PlaylistOptionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    kind: MixaHapticKind = MixaHapticKind.TAP,
    onClick: () -> Unit
) {
    val tokens = LocalSonoraTokens.current
    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(tokens.cardCornerRadius))
            .hapticClickable(kind = kind, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (accent == MaterialTheme.colorScheme.error) accent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlaylistDeleteConfirmDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Delete \"$playlistName\"?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "This removes the playlist collection. Your local audio files and liked tracks remain untouched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    MixaHaptics.reject(view)
                    onConfirmDelete()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    MixaHaptics.tap(view)
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
