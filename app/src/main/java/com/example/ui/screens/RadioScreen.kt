package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.streams.HiResStreamCatalog
import com.example.domain.model.PlaybackState
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.HiResGoldAccent
import com.example.ui.components.TrackRowItem
import com.example.ui.components.WaveformTealPlayed
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHaptics

private enum class RadioFilter(val label: String) {
    ALL("All Stations"),
    HI_RES("24-Bit Hi-Res"),
    CD_FLAC("CD Lossless FLAC"),
    CUSTOM("My Added URLs")
}

@Composable
fun RadioScreen(
    stations: List<Track>,
    playbackState: PlaybackState,
    favoriteIds: Set<String>,
    playlists: List<Playlist>,
    onPlayTrackList: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddTrackToPlaylist: (Long, Track) -> Unit,
    onCreatePlaylistAndAdd: (String, Track) -> Unit,
    onOpenAddHiResStream: () -> Unit,
    onDeleteDirectStream: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var selectedFilter by remember { mutableStateOf(RadioFilter.ALL) }
    var playlistDialogTrack by remember { mutableStateOf<Track?>(null) }

    val customCount = remember(stations) {
        stations.count { !HiResStreamCatalog.isCuratedStation(it.id) }
    }
    val hiResCount = remember(stations) {
        stations.count { it.isHiRes }
    }

    val filteredStations = remember(stations, selectedFilter) {
        when (selectedFilter) {
            RadioFilter.ALL -> stations
            RadioFilter.HI_RES -> stations.filter { it.isHiRes }
            RadioFilter.CD_FLAC -> stations.filter { it.isLossless && !it.isHiRes }
            RadioFilter.CUSTOM -> stations.filter { !HiResStreamCatalog.isCuratedStation(it.id) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Lossless Radio Studio Hero Card
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
                                HiResGoldAccent.copy(alpha = 0.16f),
                                primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HighQuality,
                                contentDescription = null,
                                tint = HiResGoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "LOSSLESS RADIO",
                                style = MaterialTheme.typography.labelMedium,
                                color = HiResGoldAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Hi-Res & FLAC Radio",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${stations.size} live stations • $hiResCount 24-bit Hi-Res • $customCount custom",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(tokens.chipCornerRadius),
                        color = WaveformTealPlayed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "32-BIT FLOAT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WaveformTealPlayed,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onOpenAddHiResStream()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radio_insert_url_button")
                    ) {
                        Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Insert Radio URL")
                    }

                    FilledTonalButton(
                        onClick = {
                            MixaHaptics.confirm(view)
                            if (filteredStations.isNotEmpty()) {
                                val randomIndex = filteredStations.indices.random()
                                onPlayTrackList(filteredStations, randomIndex)
                            }
                        },
                        enabled = filteredStations.isNotEmpty(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radio_tune_random_button")
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = secondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Surprise Station")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(RadioFilter.entries, key = { it.name }) { filter ->
                val countSuffix = when (filter) {
                    RadioFilter.CUSTOM -> if (customCount > 0) " ($customCount)" else ""
                    else -> ""
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        MixaHaptics.tap(view)
                        selectedFilter = filter
                    },
                    label = { Text("${filter.label}$countSuffix") },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = primary.copy(alpha = 0.22f),
                        selectedLabelColor = primary
                    ),
                    modifier = Modifier.testTag("radio_filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredStations.isEmpty()) {
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
                            .size(68.dp)
                            .clip(RoundedCornerShape(tokens.heroCornerRadius))
                            .background(HiResGoldAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = HiResGoldAccent,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Text(
                        text = if (selectedFilter == RadioFilter.CUSTOM) {
                            "No custom radio links yet"
                        } else {
                            "No stations match this filter"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paste any public FLAC, WAV, ALAC, Ogg, AAC, .pls, or .m3u stream URL to probe bit-depth & tune live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            MixaHaptics.confirm(view)
                            onOpenAddHiResStream()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Insert Lossless URL")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                itemsIndexed(filteredStations, key = { _, station -> station.id }) { idx, station ->
                    val isCustom = !HiResStreamCatalog.isCuratedStation(station.id)
                    TrackRowItem(
                        track = station,
                        isCurrent = playbackState.currentTrack?.id == station.id,
                        isPlaying = playbackState.isPlaying,
                        isFavorite = station.id in favoriteIds || station.isFavorite,
                        onClick = { onPlayTrackList(filteredStations, idx) },
                        onToggleFavorite = { onToggleFavorite(station) },
                        onPlayNext = { onPlayNext(station) },
                        onAddToQueue = { onAddToQueue(station) },
                        onAddToPlaylist = { playlistDialogTrack = station },
                        onDeleteDirectStream = if (isCustom) {
                            { onDeleteDirectStream(station.id) }
                        } else {
                            null
                        }
                    )
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
