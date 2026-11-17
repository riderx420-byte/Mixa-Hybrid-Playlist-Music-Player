package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VerifiedUser
import com.example.data.streams.HiResStreamCatalog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.PlaybackState
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.ui.LibraryTab
import com.example.ui.LibraryUiState
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    playbackState: PlaybackState,
    favoriteIds: Set<String>,
    playlists: List<Playlist>,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onScanLocalMedia: () -> Unit,
    onImportAudioUri: (android.net.Uri) -> Unit,
    onPlayTrackList: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onStartRadio: (Track) -> Unit,
    onAddTrackToPlaylist: (Long, Track) -> Unit,
    onCreatePlaylistAndAdd: (String, Track) -> Unit,
    onClearBanner: () -> Unit,
    isYoutubeSignedIn: Boolean = false,
    onOpenAccountSheet: () -> Unit = {},
    onOpenAddHiResStream: () -> Unit = {},
    onDeleteDirectStream: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            onScanLocalMedia()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportAudioUri)
    }

    var playlistDialogTrack by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && uiState.localTracks.isEmpty()) {
            onScanLocalMedia()
        }
    }

    val displayedTracks: List<Track> = when (uiState.selectedTab) {
        LibraryTab.ALL_LOCAL -> uiState.localTracks
        LibraryTab.DIRECT_STREAMS -> uiState.directStreamTracks
        LibraryTab.FAVORITES -> uiState.favoriteTracks
        LibraryTab.CACHED_YT -> uiState.cachedYoutubeTracks
        LibraryTab.FOLDERS -> {
            val selected = uiState.selectedFolder
            if (selected != null) uiState.folders[selected].orEmpty() else emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Studio Library Header
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
                                primary.copy(alpha = 0.16f),
                                tertiary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MIXA STUDIO",
                            style = MaterialTheme.typography.labelMedium,
                            color = primary
                        )
                        Text(
                            text = "Unified Library",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.localTracks.size} Local • ${uiState.directStreamTracks.size} Hi-Res • ${uiState.favoriteTracks.size} Liked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            onOpenAccountSheet()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_account_sheet_button")
                    ) {
                        Icon(
                            imageVector = if (isYoutubeSignedIn) Icons.Rounded.VerifiedUser else Icons.Rounded.AccountCircle,
                            contentDescription = "YouTube Music Account",
                            tint = if (isYoutubeSignedIn) tokens.emeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            if (hasAudioPermission) {
                                onScanLocalMedia()
                            } else {
                                permissionLauncher.launch(audioPermission)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_device_button")
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasAudioPermission) stringResource(R.string.action_scan_library) else "Grant & Scan",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            documentPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_import_audio),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.statusBanner != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(220))
        ) {
            uiState.statusBanner?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            MixaHaptics.tap(view)
                            onClearBanner()
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(LibraryTab.entries) { tab ->
                val selected = uiState.selectedTab == tab
                FilterChip(
                    selected = selected,
                    onClick = {
                        MixaHaptics.tap(view)
                        onSelectTab(tab)
                    },
                    label = { Text(tab.label) },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = primary.copy(alpha = 0.24f),
                        selectedLabelColor = primary
                    ),
                    modifier = Modifier.testTag("library_tab_${tab.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.selectedTab == LibraryTab.FOLDERS && uiState.selectedFolder == null) {
            // Folder grid/list
            if (uiState.folders.isEmpty()) {
                EmptyLibraryState(
                    hasPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(audioPermission) },
                    onImportFile = { documentPickerLauncher.launch(arrayOf("audio/*")) }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    items(uiState.folders.entries.toList(), key = { it.key }) { (folderName, tracks) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .hapticClickable(kind = MixaHapticKind.TAP) { onSelectFolder(folderName) }
                                .testTag("folder_item_$folderName")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(tokens.chipCornerRadius))
                                        .background(secondary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = secondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folderName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${tracks.size} audio tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (uiState.selectedTab == LibraryTab.FOLDERS && uiState.selectedFolder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    IconButton(onClick = {
                        MixaHaptics.tap(view)
                        onSelectFolder(null)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to folders"
                        )
                    }
                    Text(
                        text = uiState.selectedFolder,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.selectedTab == LibraryTab.DIRECT_STREAMS) {
                FilledTonalButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onOpenAddHiResStream()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = primary.copy(alpha = 0.16f),
                        contentColor = primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("library_add_hires_stream_button")
                ) {
                    Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Lossless FLAC / WAV / Radio Stream URL", fontWeight = FontWeight.SemiBold)
                }
            }

            if (displayedTracks.isEmpty()) {
                EmptyLibraryState(
                    hasPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(audioPermission) },
                    onImportFile = { documentPickerLauncher.launch(arrayOf("audio/*")) }
                )
            } else {
                // Play All / Shuffle bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onPlayTrackList(displayedTracks, 0)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_all_library_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_play_all))
                    }
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            val shuffled = displayedTracks.shuffled()
                            onPlayTrackList(shuffled, 0)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shuffle_library_button")
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_shuffle_all))
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    itemsIndexed(displayedTracks, key = { _, t -> t.id }) { index, track ->
                        val canDeleteStream = uiState.selectedTab == LibraryTab.DIRECT_STREAMS &&
                            !HiResStreamCatalog.isCuratedStation(track.id)
                        TrackRowItem(
                            track = track,
                            isCurrent = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            isFavorite = track.id in favoriteIds || track.isFavorite,
                            onClick = { onPlayTrackList(displayedTracks, index) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { playlistDialogTrack = track },
                            onStartRadio = { onStartRadio(track) },
                            onDeleteDirectStream = if (canDeleteStream) {
                                { onDeleteDirectStream(track.id) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }

    playlistDialogTrack?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = { playlistDialogTrack = null },
            onSelectPlaylist = { playlistId -> onAddTrackToPlaylist(playlistId, track) },
            onCreateAndAdd = { name -> onCreatePlaylistAndAdd(name, track) }
        )
    }
}

@Composable
private fun EmptyLibraryState(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onImportFile: () -> Unit
) {
    val view = LocalView.current
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = stringResource(R.string.label_empty_library_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.label_empty_library_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasPermission) {
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onRequestPermission()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Allow Audio Access")
                    }
                }
                FilledTonalButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onImportFile()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Pick Audio File")
                }
            }
        }
    }
}
