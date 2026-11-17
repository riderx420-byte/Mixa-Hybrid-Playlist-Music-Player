package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserAudioSettings
import com.example.data.lyrics.LrcDocument
import com.example.data.repository.MusicRepository
import com.example.data.streams.HiResStreamCatalog
import com.example.domain.model.EqPreset
import com.example.domain.model.EqualizerState
import com.example.domain.model.ExploreSource
import com.example.domain.model.ExtensionRegistryInfo
import com.example.domain.model.InstalledExtension
import com.example.domain.model.PlaybackState
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.Playlist
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.RegistryExtensionEntry
import com.example.domain.model.SearchFilter
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import com.example.domain.model.Track
import com.example.player.AudioEffectsManager
import com.example.player.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    ALL_LOCAL("Local Audio"),
    DIRECT_STREAMS("Hi-Res Streams"),
    FAVORITES("Favorites"),
    FOLDERS("Folders"),
    CACHED_YT("YT History")
}

data class StreamProbeUiState(
    val isProbing: Boolean = false,
    val probedTrack: Track? = null,
    val errorMessage: String? = null
)

data class ExtensionManagerUiState(
    val isSyncing: Boolean = false,
    val installingId: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class LyricsUiState(
    val trackId: String? = null,
    val isLoading: Boolean = false,
    val document: LrcDocument? = null,
    val syncOffsetMs: Long = 0L,
    val errorMessage: String? = null
)

data class ExploreUiState(
    val query: String = "",
    val selectedFilter: SearchFilter = SearchFilter.SONGS,
    val availableSources: List<ExploreSource> = listOf(ExploreSource.YouTubeMusic),
    val selectedSource: ExploreSource = ExploreSource.YouTubeMusic,
    val suggestions: List<String> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val accountTracks: List<Track> = emptyList(),
    val featuredTracks: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingAccountTracks: Boolean = false,
    val isLoadingFeatured: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.ALL_LOCAL,
    val selectedFolder: String? = null,
    val isScanning: Boolean = false,
    val localTracks: List<Track> = emptyList(),
    val directStreamTracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val cachedYoutubeTracks: List<Track> = emptyList(),
    val folders: Map<String, List<Track>> = emptyMap(),
    val statusBanner: String? = null
)

class SonoraViewModel(
    private val repository: MusicRepository,
    val playbackController: PlaybackController,
    private val audioEffectsManager: AudioEffectsManager
) : ViewModel() {

    private var suggestionJob: Job? = null
    private var exploreSearchJob: Job? = null
    private var exploreFeaturedJob: Job? = null
    private var libraryBannerJob: Job? = null
    private var lyricsFetchJob: Job? = null

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val equalizerState: StateFlow<EqualizerState> = audioEffectsManager.state

    private val _lyricsUiState = MutableStateFlow(LyricsUiState())
    val lyricsUiState: StateFlow<LyricsUiState> = _lyricsUiState.asStateFlow()

    val userSettings: StateFlow<UserAudioSettings> = repository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserAudioSettings())

    val extensionRegistries: StateFlow<List<ExtensionRegistryInfo>> = repository.extensionRegistries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedExtensions: StateFlow<List<InstalledExtension>> = repository.installedExtensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _extensionUiState = MutableStateFlow(ExtensionManagerUiState())
    val extensionUiState: StateFlow<ExtensionManagerUiState> = _extensionUiState.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<String>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.ALL_LOCAL)
    private val _selectedFolder = MutableStateFlow<String?>(null)
    private val _isScanning = MutableStateFlow(false)
    private val _libraryBanner = MutableStateFlow<String?>(null)

    private val _streamProbeUiState = MutableStateFlow(StreamProbeUiState())
    val streamProbeUiState: StateFlow<StreamProbeUiState> = _streamProbeUiState.asStateFlow()

    val libraryUiState: StateFlow<LibraryUiState> = combine(
        _selectedLibraryTab,
        _selectedFolder,
        _isScanning,
        repository.localTracks,
        repository.directStreamTracks,
        repository.favoriteTracks,
        repository.cachedYoutubeTracks,
        _libraryBanner
    ) { values ->
        val tab = values[0] as LibraryTab
        val folder = values[1] as String?
        val scanning = values[2] as Boolean
        @Suppress("UNCHECKED_CAST")
        val local = values[3] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val directStreams = values[4] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val favs = values[5] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val cachedYt = values[6] as List<Track>
        val banner = values[7] as String?

        val groupedFolders = local
            .groupBy { it.folderName?.takeIf { f -> f.isNotBlank() } ?: "Device Audio" }
            .toSortedMap()

        // Include curated Hi-Res stations cleanly with user-saved direct streams
        val mergedDirect = (directStreams + HiResStreamCatalog.curatedHiResStations)
            .distinctBy { it.id }

        LibraryUiState(
            selectedTab = tab,
            selectedFolder = folder,
            isScanning = scanning,
            localTracks = local,
            directStreamTracks = mergedDirect,
            favoriteTracks = favs,
            cachedYoutubeTracks = cachedYt,
            folders = groupedFolders,
            statusBanner = banner
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    private val _exploreUiState = MutableStateFlow(ExploreUiState())
    val exploreUiState: StateFlow<ExploreUiState> = _exploreUiState.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = repository.favoriteTracks
        .combine(MutableStateFlow(Unit)) { list, _ -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        loadExploreFeatured()
        viewModelScope.launch {
            repository.installedExtensions.collect { extensions ->
                val enabledSources = listOf(ExploreSource.YouTubeMusic) + extensions
                    .filter { it.isEnabled }
                    .map { ext ->
                        ExploreSource.ExtensionProvider(
                            extensionId = ext.id,
                            name = ext.name,
                            isMetadataOnly = ext.manifest.isMetadataOnly
                        )
                    }
                val currentSelected = _exploreUiState.value.selectedSource
                val stillValid = enabledSources.any { it.id == currentSelected.id }
                val nextSelected = if (stillValid) currentSelected else ExploreSource.YouTubeMusic
                _exploreUiState.update {
                    it.copy(
                        availableSources = enabledSources,
                        selectedSource = nextSelected
                    )
                }
                if (!stillValid) {
                    loadExploreFeatured()
                }
            }
        }
        viewModelScope.launch {
            repository.userSettings
                .map { it.ytMusicCookie }
                .distinctUntilChanged()
                .collect { cookie ->
                    if (!cookie.isNullOrBlank()) {
                        loadAccountTracks(cookie)
                    } else {
                        _exploreUiState.update {
                            it.copy(accountTracks = emptyList(), isLoadingAccountTracks = false)
                        }
                    }
                }
        }
        viewModelScope.launch {
            playbackController.playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    if (track == null) {
                        lyricsFetchJob?.cancel()
                        _lyricsUiState.value = LyricsUiState()
                    } else {
                        fetchLyricsForTrack(track, forceRefresh = false)
                    }
                }
        }
    }

    fun observePlaylist(playlistId: Long): Flow<Playlist?> = repository.observePlaylist(playlistId)

    fun selectLibraryTab(tab: LibraryTab) {
        _selectedLibraryTab.value = tab
        if (tab != LibraryTab.FOLDERS) {
            _selectedFolder.value = null
        }
    }

    fun selectFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    private fun showLibraryBanner(message: String) {
        libraryBannerJob?.cancel()
        _libraryBanner.value = message
        libraryBannerJob = viewModelScope.launch {
            delay(20_000L)
            _libraryBanner.value = null
        }
    }

    fun clearLibraryBanner() {
        libraryBannerJob?.cancel()
        libraryBannerJob = null
        _libraryBanner.value = null
    }

    fun scanLocalMedia() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val scanned = repository.scanAndSyncLocalLibrary()
            _isScanning.value = false
            showLibraryBanner(
                if (scanned.isEmpty()) {
                    "No local audio files found in MediaStore. Use Import File to pick tracks manually."
                } else {
                    "Indexed ${scanned.size} local tracks"
                }
            )
        }
    }

    fun importLocalAudioUri(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importLocalAudioDocument(uri)
            showLibraryBanner(
                if (imported != null) {
                    "Imported \"${imported.title}\""
                } else {
                    "Could not read selected audio file"
                }
            )
        }
    }

    fun probeDirectStreamUrl(primaryUrl: String, customTitle: String?, fallbackUrl: String?) {
        viewModelScope.launch {
            _streamProbeUiState.value = StreamProbeUiState(isProbing = true)
            repository.probeDirectStream(primaryUrl, customTitle, fallbackUrl)
                .onSuccess { track ->
                    _streamProbeUiState.value = StreamProbeUiState(probedTrack = track)
                }
                .onFailure { err ->
                    _streamProbeUiState.value = StreamProbeUiState(
                        errorMessage = err.localizedMessage ?: "Failed to inspect stream URL"
                    )
                }
        }
    }

    fun saveAndPlayProbedStream(track: Track, playImmediately: Boolean = true) {
        viewModelScope.launch {
            repository.saveDirectStreamTrack(track)
            _streamProbeUiState.value = StreamProbeUiState()
            _selectedLibraryTab.value = LibraryTab.DIRECT_STREAMS
            showLibraryBanner("Added \"${track.title}\" (${track.qualityBadgeLabel})")
            if (playImmediately) {
                playTrackList(listOf(track), 0)
            }
        }
    }

    fun resetStreamProbe() {
        _streamProbeUiState.value = StreamProbeUiState()
    }

    fun deleteDirectStream(trackId: String) {
        viewModelScope.launch {
            repository.deleteDirectStreamTrack(trackId)
            showLibraryBanner("Removed stream from library")
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    // Explore / Multi-Source Search (YouTube Music + Enabled .sflx Providers)
    fun selectExploreSource(source: ExploreSource) {
        if (_exploreUiState.value.selectedSource.id == source.id) return
        suggestionJob?.cancel()
        exploreSearchJob?.cancel()
        exploreFeaturedJob?.cancel()
        _exploreUiState.update {
            it.copy(
                selectedSource = source,
                suggestions = emptyList(),
                errorMessage = null
            )
        }
        val activeQuery = _exploreUiState.value.query.trim()
        if (activeQuery.isNotBlank() && _exploreUiState.value.hasSearched) {
            submitSearch(activeQuery)
        } else {
            loadExploreFeatured()
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _exploreUiState.update { it.copy(query = newQuery, errorMessage = null) }
        suggestionJob?.cancel()
        if (newQuery.isBlank()) {
            _exploreUiState.update { it.copy(suggestions = emptyList(), hasSearched = false, searchResults = emptyList()) }
            return
        }
        if (_exploreUiState.value.selectedSource is ExploreSource.YouTubeMusic) {
            suggestionJob = viewModelScope.launch {
                delay(250L)
                repository.getSearchSuggestions(newQuery).onSuccess { suggestions ->
                    _exploreUiState.update { it.copy(suggestions = suggestions) }
                }
            }
        }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        _exploreUiState.update { it.copy(selectedFilter = filter) }
        val q = _exploreUiState.value.query
        if (q.isNotBlank() && _exploreUiState.value.hasSearched) {
            submitSearch(q)
        }
    }

    fun submitSearch(queryOverride: String? = null) {
        val query = (queryOverride ?: _exploreUiState.value.query).trim()
        if (query.isBlank()) return
        suggestionJob?.cancel()
        exploreSearchJob?.cancel()
        val activeSource = _exploreUiState.value.selectedSource
        _exploreUiState.update {
            it.copy(
                query = query,
                suggestions = emptyList(),
                isSearching = true,
                hasSearched = true,
                errorMessage = null
            )
        }
        exploreSearchJob = viewModelScope.launch {
            val filter = _exploreUiState.value.selectedFilter
            val result = when (activeSource) {
                ExploreSource.YouTubeMusic -> repository.searchYouTubeMusic(query, filter)
                is ExploreSource.ExtensionProvider ->
                    repository.searchExtensionCatalog(activeSource.extensionId, query, filter)
            }
            result
                .onSuccess { tracks ->
                    _exploreUiState.update {
                        it.copy(
                            searchResults = tracks,
                            isSearching = false,
                            errorMessage = if (tracks.isEmpty()) {
                                "No matching results on ${activeSource.displayName}"
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { err ->
                    _exploreUiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = err.localizedMessage ?: "${activeSource.displayName} search failed"
                        )
                    }
                }
        }
    }

    fun loadExploreFeatured() {
        exploreFeaturedJob?.cancel()
        val activeSource = _exploreUiState.value.selectedSource
        exploreFeaturedJob = viewModelScope.launch {
            _exploreUiState.update { it.copy(isLoadingFeatured = true, errorMessage = null) }
            val result = when (activeSource) {
                ExploreSource.YouTubeMusic -> repository.getExploreFeatured()
                is ExploreSource.ExtensionProvider -> repository.getExtensionFeatured(activeSource.extensionId)
            }
            result
                .onSuccess { tracks ->
                    _exploreUiState.update {
                        it.copy(featuredTracks = tracks, isLoadingFeatured = false)
                    }
                }
                .onFailure { err ->
                    _exploreUiState.update {
                        it.copy(
                            featuredTracks = if (activeSource is ExploreSource.ExtensionProvider) emptyList() else it.featuredTracks,
                            isLoadingFeatured = false,
                            errorMessage = if (activeSource is ExploreSource.ExtensionProvider) err.localizedMessage else it.errorMessage
                        )
                    }
                }
        }
        if (activeSource is ExploreSource.YouTubeMusic) {
            val activeCookie = userSettings.value.ytMusicCookie
            if (!activeCookie.isNullOrBlank()) {
                loadAccountTracks(activeCookie)
            }
        }
    }

    private fun loadAccountTracks(cookieOverride: String? = userSettings.value.ytMusicCookie) {
        viewModelScope.launch {
            _exploreUiState.update { it.copy(isLoadingAccountTracks = true) }
            repository.getAccountPersonalizedTracks(cookieOverride)
                .onSuccess { tracks ->
                    _exploreUiState.update {
                        it.copy(accountTracks = tracks, isLoadingAccountTracks = false)
                    }
                }
                .onFailure {
                    _exploreUiState.update { it.copy(isLoadingAccountTracks = false) }
                }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch { repository.deleteSearchHistoryItem(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    // Playback actions
    fun playTrackList(tracks: List<Track>, index: Int) {
        val autoRadio = userSettings.value.autoRadioEnabled
        playbackController.playTracks(tracks, index, enableAutoRadio = autoRadio)
    }

    fun switchActiveStreamVariant(variantId: String) {
        playbackController.switchStreamVariant(variantId)
    }

    fun startRadioFromTrack(track: Track) {
        playbackController.startRadio(track)
    }

    fun playNext(track: Track) = playbackController.playNext(track)
    fun addToQueue(track: Track) = playbackController.addToQueue(track)
    fun addTracksToQueue(tracks: List<Track>) {
        tracks.forEach { playbackController.addToQueue(it) }
    }

    // Playlist CRUD
    fun createPlaylist(name: String, description: String = "", initialTracks: List<Track> = emptyList()) {
        viewModelScope.launch {
            repository.createPlaylist(name, description, initialTracks)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String, description: String = "") {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, name, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, tracks: List<Track>, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.moveTrackInPlaylist(playlistId, tracks, fromIndex, toIndex)
        }
    }

    fun saveCurrentQueueAsPlaylist(name: String) {
        val queueTracks = playbackState.value.queue
        if (queueTracks.isEmpty()) return
        viewModelScope.launch {
            repository.createPlaylist(
                name = name,
                description = "Saved hybrid queue (${queueTracks.size} tracks)",
                initialTracks = queueTracks
            )
        }
    }

    fun retryStream(forceAlternateFormat: Boolean = false) {
        playbackController.retryCurrentTrack(forceAlternateFormat)
    }

    fun retryStreamWithHls() {
        playbackController.retryCurrentWithHls()
    }

    // Equalizer & Audio Settings
    fun setEqEnabled(enabled: Boolean) = audioEffectsManager.setEnabled(enabled)
    fun applyEqPreset(preset: EqPreset) = audioEffectsManager.applyPreset(preset)
    fun setEqBandLevel(index: Int, level: Float) = audioEffectsManager.setBandLevel(index, level)
    fun setBassBoost(strength: Int) = audioEffectsManager.setBassBoost(strength)
    fun setAutoRadioSetting(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoRadioEnabled(enabled) }
        playbackController.setAutoRadioEnabled(enabled)
    }
    fun setPreferOpusSetting(preferOpus: Boolean) {
        viewModelScope.launch { repository.setPreferOpus(preferOpus) }
    }
    fun setAutoSkipBrokenTracks(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoSkipBrokenTracks(enabled) }
    }
    fun setPreferredStreamQuality(quality: PreferredStreamQuality) {
        viewModelScope.launch { repository.setPreferredStreamQuality(quality) }
    }
    fun setDirectFloatPcmBypassEq(enabled: Boolean) {
        viewModelScope.launch { repository.setDirectFloatPcmBypassEq(enabled) }
    }
    fun setCrossfadeDurationSeconds(seconds: Int) {
        viewModelScope.launch { repository.setCrossfadeDurationSeconds(seconds) }
    }
    fun clearStreamCache() {
        playbackController.clearStreamCache()
    }

    // Synchronized LRC Lyrics
    private fun fetchLyricsForTrack(track: Track, forceRefresh: Boolean) {
        lyricsFetchJob?.cancel()
        _lyricsUiState.value = LyricsUiState(
            trackId = track.id,
            isLoading = true,
            document = null,
            syncOffsetMs = 0L,
            errorMessage = null
        )
        lyricsFetchJob = viewModelScope.launch {
            repository.loadLyricsForTrack(track, forceRefresh = forceRefresh)
                .onSuccess { doc ->
                    if (_lyricsUiState.value.trackId == track.id) {
                        _lyricsUiState.value = LyricsUiState(
                            trackId = track.id,
                            isLoading = false,
                            document = doc,
                            syncOffsetMs = 0L,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    if (_lyricsUiState.value.trackId == track.id) {
                        _lyricsUiState.value = LyricsUiState(
                            trackId = track.id,
                            isLoading = false,
                            document = null,
                            syncOffsetMs = 0L,
                            errorMessage = err.localizedMessage ?: "No synced lyrics found"
                        )
                    }
                }
        }
    }

    fun refreshCurrentTrackLyrics() {
        val current = playbackState.value.currentTrack ?: return
        fetchLyricsForTrack(current, forceRefresh = true)
    }

    fun importLrcForCurrentTrack(uri: Uri) {
        val current = playbackState.value.currentTrack ?: return
        lyricsFetchJob?.cancel()
        _lyricsUiState.update { it.copy(trackId = current.id, isLoading = true, errorMessage = null) }
        lyricsFetchJob = viewModelScope.launch {
            repository.importLrcForTrack(current, uri)
                .onSuccess { doc ->
                    _lyricsUiState.value = LyricsUiState(
                        trackId = current.id,
                        isLoading = false,
                        document = doc,
                        syncOffsetMs = 0L,
                        errorMessage = null
                    )
                }
                .onFailure { err ->
                    _lyricsUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to import .lrc file"
                        )
                    }
                }
        }
    }

    fun nudgeLyricsSyncOffset(deltaMs: Long) {
        _lyricsUiState.update { state ->
            state.copy(syncOffsetMs = (state.syncOffsetMs + deltaMs).coerceIn(-15_000L, 15_000L))
        }
    }

    // Theme & Appearance Customization
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setThemeAccent(accent: ThemeAccent) {
        viewModelScope.launch { repository.setThemeAccent(accent) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setUseDynamicColor(enabled) }
    }

    fun setCornerStyle(style: ThemeCornerStyle) {
        viewModelScope.launch { repository.setCornerStyle(style) }
    }

    fun setPlayerBackdropStyle(style: PlayerBackdropStyle) {
        viewModelScope.launch { repository.setPlayerBackdropStyle(style) }
    }

    private val _accountStatusMessage = MutableStateFlow<String?>(null)
    val accountStatusMessage: StateFlow<String?> = _accountStatusMessage.asStateFlow()

    fun clearAccountStatus() {
        _accountStatusMessage.value = null
    }

    fun selectDeviceGoogleAccount(email: String) {
        viewModelScope.launch {
            repository.setSelectedGoogleEmail(email)
            _accountStatusMessage.value = "Selected phone account: $email"
        }
    }

    fun saveYouTubeAccountCookie(
        cookie: String,
        preferredEmail: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _accountStatusMessage.value = "Verifying YouTube Music session…"
            val effectiveEmail = preferredEmail ?: userSettings.value.ytSelectedEmail
            repository.saveYouTubeAccount(cookie, effectiveEmail)
                .onSuccess { label ->
                    _accountStatusMessage.value = "Signed in as $label"
                    loadExploreFeatured()
                    onSuccess()
                }
                .onFailure { err ->
                    _accountStatusMessage.value = err.localizedMessage ?: "Invalid session cookie"
                }
        }
    }

    fun signOutYouTubeAccount() {
        viewModelScope.launch {
            repository.clearYouTubeAccount()
            _accountStatusMessage.value = "Signed out of YouTube Music"
            loadExploreFeatured()
        }
    }

    // Extension Hub Management
    fun addAndSyncRegistry(url: String) {
        viewModelScope.launch {
            _extensionUiState.update { it.copy(isSyncing = true, errorMessage = null, statusMessage = null) }
            repository.addAndSyncRegistry(url)
                .onSuccess { info ->
                    _extensionUiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "Synced ${info.entries.size} extensions from ${info.name}"
                        )
                    }
                }
                .onFailure { err ->
                    _extensionUiState.update {
                        it.copy(isSyncing = false, errorMessage = err.localizedMessage ?: "Failed to sync registry")
                    }
                }
        }
    }

    fun syncAllRegistries() {
        viewModelScope.launch {
            _extensionUiState.update { it.copy(isSyncing = true, errorMessage = null) }
            repository.syncAllRegistries()
                .onSuccess { count ->
                    _extensionUiState.update {
                        it.copy(isSyncing = false, statusMessage = "Refreshed $count registry feed(s)")
                    }
                }
                .onFailure { err ->
                    _extensionUiState.update {
                        it.copy(isSyncing = false, errorMessage = err.localizedMessage ?: "Refresh failed")
                    }
                }
        }
    }

    fun removeRegistry(url: String) {
        viewModelScope.launch { repository.removeRegistry(url) }
    }

    fun installRegistryExtension(entry: RegistryExtensionEntry, registryUrl: String) {
        viewModelScope.launch {
            _extensionUiState.update { it.copy(installingId = entry.id, errorMessage = null, statusMessage = null) }
            repository.installExtensionFromRegistry(entry, registryUrl)
                .onSuccess { installed ->
                    _extensionUiState.update {
                        it.copy(
                            installingId = null,
                            statusMessage = "Installed ${installed.name} v${installed.version}"
                        )
                    }
                }
                .onFailure { err ->
                    _extensionUiState.update {
                        it.copy(
                            installingId = null,
                            errorMessage = err.localizedMessage ?: "Failed to install ${entry.name}"
                        )
                    }
                }
        }
    }

    fun installLocalExtensionUri(uri: Uri) {
        viewModelScope.launch {
            _extensionUiState.update { it.copy(isSyncing = true, errorMessage = null, statusMessage = null) }
            repository.installExtensionFromLocalUri(uri)
                .onSuccess { installed ->
                    _extensionUiState.update {
                        it.copy(isSyncing = false, statusMessage = "Imported ${installed.name} v${installed.version}")
                    }
                }
                .onFailure { err ->
                    _extensionUiState.update {
                        it.copy(isSyncing = false, errorMessage = err.localizedMessage ?: "Invalid .sflx archive")
                    }
                }
        }
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        viewModelScope.launch { repository.setExtensionEnabled(extensionId, enabled) }
    }

    fun uninstallExtension(extensionId: String) {
        viewModelScope.launch {
            repository.uninstallExtension(extensionId)
            _extensionUiState.update { it.copy(statusMessage = "Removed extension") }
        }
    }

    fun getExtensionSettings(extensionId: String): Map<String, String> =
        repository.getExtensionSettings(extensionId)

    fun saveExtensionSetting(extensionId: String, key: String, value: String, isSecret: Boolean) {
        repository.saveExtensionSetting(extensionId, key, value, isSecret)
        _extensionUiState.update { it.copy(statusMessage = "Saved extension settings") }
    }

    fun clearExtensionUiMessages() {
        _extensionUiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    class Factory(
        private val repository: MusicRepository,
        private val playbackController: PlaybackController,
        private val audioEffectsManager: AudioEffectsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SonoraViewModel(repository, playbackController, audioEffectsManager) as T
        }
    }
}
