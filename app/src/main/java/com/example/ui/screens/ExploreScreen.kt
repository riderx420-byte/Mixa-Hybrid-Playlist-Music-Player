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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ExploreSource
import com.example.domain.model.PlaybackState
import com.example.domain.model.Playlist
import com.example.domain.model.SearchFilter
import com.example.domain.model.Track
import com.example.ui.ExploreUiState
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@Composable
fun ExploreScreen(
    uiState: ExploreUiState,
    searchHistory: List<String>,
    playbackState: PlaybackState,
    favoriteIds: Set<String>,
    playlists: List<Playlist>,
    isYoutubeSignedIn: Boolean = false,
    onQueryChanged: (String) -> Unit,
    onSubmitSearch: (String?) -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onSelectSource: (ExploreSource) -> Unit = {},
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRefreshFeatured: () -> Unit,
    onPlayTrackList: (List<Track>, Int) -> Unit,
    onStartRadio: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddTrackToPlaylist: (Long, Track) -> Unit,
    onCreatePlaylistAndAdd: (String, Track) -> Unit,
    onOpenExtensionManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary
    var playlistDialogTrack by remember { mutableStateOf<Track?>(null) }
    var platformMenuExpanded by remember { mutableStateOf(false) }

    val isYoutubeSource = uiState.selectedSource is ExploreSource.YouTubeMusic

    val combinedTrendingTracks = remember(
        isYoutubeSource,
        isYoutubeSignedIn,
        uiState.accountTracks,
        uiState.featuredTracks
    ) {
        if (isYoutubeSource && isYoutubeSignedIn && uiState.accountTracks.isNotEmpty()) {
            (uiState.accountTracks + uiState.featuredTracks).distinctBy { it.id }
        } else {
            uiState.featuredTracks
        }
    }

    val accountOnlyList = remember(isYoutubeSource, isYoutubeSignedIn, uiState.accountTracks) {
        if (isYoutubeSource && isYoutubeSignedIn) uiState.accountTracks else emptyList()
    }
    val accountIds = remember(accountOnlyList) { accountOnlyList.map { it.id }.toSet() }
    val chartTracks = remember(isYoutubeSource, uiState.featuredTracks, accountIds) {
        if (isYoutubeSource) {
            uiState.featuredTracks.filterNot { it.id in accountIds }
        } else {
            uiState.featuredTracks
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Platform Dropdown Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            val activeSource = uiState.selectedSource
            val isExtensionActive = activeSource is ExploreSource.ExtensionProvider
            val sourceAccent = if (isExtensionActive) tokens.emeraldAccent else primary
            val sourceModeLabel = when (activeSource) {
                ExploreSource.YouTubeMusic -> "Native InnerTube"
                is ExploreSource.ExtensionProvider -> if (activeSource.isMetadataOnly) {
                    "ISRC Cross-Match"
                } else {
                    "Direct Stream"
                }
            }

            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(tokens.cardCornerRadius))
                    .hapticClickable(kind = MixaHapticKind.TAP) {
                        platformMenuExpanded = true
                    }
                    .testTag("explore_platform_dropdown")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(sourceAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExtensionActive) Icons.Rounded.Extension else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = sourceAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PLATFORM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = activeSource.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = sourceAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = sourceModeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = sourceAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = if (platformMenuExpanded) {
                                Icons.Rounded.KeyboardArrowUp
                            } else {
                                Icons.Rounded.KeyboardArrowDown
                            },
                            contentDescription = "Select streaming platform",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = platformMenuExpanded,
                onDismissRequest = { platformMenuExpanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(0.90f)
            ) {
                uiState.availableSources.forEach { source ->
                    val isSelected = source.id == activeSource.id
                    val isExt = source is ExploreSource.ExtensionProvider
                    val badge = when (source) {
                        ExploreSource.YouTubeMusic -> "Native"
                        is ExploreSource.ExtensionProvider -> if (source.isMetadataOnly) "ISRC" else "Direct"
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = source.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (if (isExt) tokens.emeraldAccent else primary).copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isExt) tokens.emeraldAccent else primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isExt) Icons.Rounded.Extension else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Active platform",
                                    tint = primary
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            MixaHaptics.confirm(view)
                            platformMenuExpanded = false
                            onSelectSource(source)
                        },
                        modifier = Modifier.testTag("explore_source_${source.id}")
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Manage .sflx Extensions",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    onClick = {
                        MixaHaptics.tap(view)
                        platformMenuExpanded = false
                        onOpenExtensionManager()
                    },
                    modifier = Modifier.testTag("explore_dropdown_manage_extensions")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Box
        TextField(
            value = uiState.query,
            onValueChange = onQueryChanged,
            placeholder = {
                Text(
                    text = stringResource(R.string.label_search_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            MixaHaptics.tap(view)
                            onQueryChanged("")
                        },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    MixaHaptics.confirm(view)
                    focusManager.clearFocus()
                    onSubmitSearch(null)
                }
            ),
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
                .testTag("explore_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips (Songs / Albums / Artists)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.selectedFilter == filter,
                    onClick = {
                        MixaHaptics.tap(view)
                        onSelectFilter(filter)
                    },
                    label = { Text(filter.label) },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = primary.copy(alpha = 0.24f),
                        selectedLabelColor = primary
                    ),
                    modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                )
            }
        }

        // Live InnerTube Search Suggestions
        if (uiState.suggestions.isNotEmpty() && !uiState.hasSearched) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    uiState.suggestions.take(6).forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                                    focusManager.clearFocus()
                                    onSubmitSearch(suggestion)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!uiState.hasSearched) {
                // Recent Search History
                if (searchHistory.isNotEmpty()) {
                    item(key = "recent_searches_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = {
                                MixaHaptics.reject(view)
                                onClearHistory()
                            }) {
                                Text("Clear")
                            }
                        }
                    }
                    item(key = "recent_searches_row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(searchHistory, key = { it }) { query ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                                            focusManager.clearFocus()
                                            onSubmitSearch(query)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = query,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        IconButton(
                                            onClick = {
                                                MixaHaptics.reject(view)
                                                onDeleteHistoryItem(query)
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove search history",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Trending Songs + Account Library Header
                item(key = "featured_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (val src = uiState.selectedSource) {
                                    is ExploreSource.ExtensionProvider -> "Discover on ${src.displayName}"
                                    ExploreSource.YouTubeMusic -> if (isYoutubeSignedIn) {
                                        "Your Account & Trending Songs"
                                    } else {
                                        "Trending on YouTube Music"
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.selectedSource is ExploreSource.ExtensionProvider) {
                                Text(
                                    text = if (uiState.selectedSource.isMetadataOnly) {
                                        "Metadata catalog • ISRC auto-cross-matches Qobuz, Deezer or YT Music"
                                    } else {
                                        "Direct lossless / adaptive streams from ${uiState.selectedSource.displayName}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.emeraldAccent
                                )
                            } else if (isYoutubeSignedIn) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = tokens.emeraldAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (accountOnlyList.isNotEmpty()) {
                                            "${accountOnlyList.size} songs synced from your account"
                                        } else if (uiState.isLoadingAccountTracks) {
                                            "Loading songs from your YouTube account…"
                                        } else {
                                            "Signed in — Personalized mixes & charts"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.emeraldAccent
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                MixaHaptics.tap(view)
                                onRefreshFeatured()
                            },
                            modifier = Modifier.testTag("refresh_featured_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh charts")
                        }
                    }
                }

                // Account songs integrated right inside Trending section when signed in
                if (isYoutubeSource && isYoutubeSignedIn && accountOnlyList.isNotEmpty()) {
                    item(key = "account_tracks_subheader") {
                        Text(
                            text = "From Your Account",
                            style = MaterialTheme.typography.labelMedium,
                            color = primary
                        )
                    }
                    itemsIndexed(accountOnlyList, key = { _, t -> "acct_${t.id}" }) { idx, track ->
                        ExploreTrackCard(
                            track = track,
                            isCurrent = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            isFavorite = track.id in favoriteIds || track.isFavorite,
                            onPlay = { onPlayTrackList(combinedTrendingTracks, idx) },
                            onStartRadio = { onStartRadio(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { playlistDialogTrack = track }
                        )
                    }
                    if (chartTracks.isNotEmpty()) {
                        item(key = "trending_charts_subheader") {
                            Text(
                                text = "Trending Charts",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                if ((uiState.isLoadingFeatured || uiState.isLoadingAccountTracks) && combinedTrendingTracks.isEmpty()) {
                    item(key = "featured_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primary)
                        }
                    }
                } else if (combinedTrendingTracks.isEmpty()) {
                    item(key = "featured_empty") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Search any song, album, or artist above",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "InnerTube queries music.youtube.com directly and resolves adaptive audio streams on demand.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(chartTracks, key = { _, t -> "feat_${t.id}" }) { idx, track ->
                        val combinedIdx = (accountOnlyList.size + idx).coerceIn(0, combinedTrendingTracks.lastIndex)
                        ExploreTrackCard(
                            track = track,
                            isCurrent = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            isFavorite = track.id in favoriteIds || track.isFavorite,
                            onPlay = { onPlayTrackList(combinedTrendingTracks, combinedIdx) },
                            onStartRadio = { onStartRadio(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { playlistDialogTrack = track }
                        )
                    }
                }
            } else {
                // Active Search Results
                if (uiState.isSearching) {
                    item(key = "search_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primary)
                        }
                    }
                } else if (uiState.errorMessage != null && uiState.searchResults.isEmpty()) {
                    item(key = "search_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(uiState.searchResults, key = { _, t -> "res_${t.id}" }) { idx, track ->
                        ExploreTrackCard(
                            track = track,
                            isCurrent = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            isFavorite = track.id in favoriteIds || track.isFavorite,
                            onPlay = { onPlayTrackList(uiState.searchResults, idx) },
                            onStartRadio = { onStartRadio(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { playlistDialogTrack = track }
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
private fun ExploreTrackCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onStartRadio: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    TrackRowItem(
        track = track,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isFavorite = isFavorite,
        onClick = onPlay,
        onToggleFavorite = onToggleFavorite,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onAddToPlaylist = onAddToPlaylist,
        onStartRadio = onStartRadio,
        showRadioIconButton = true
    )
}
