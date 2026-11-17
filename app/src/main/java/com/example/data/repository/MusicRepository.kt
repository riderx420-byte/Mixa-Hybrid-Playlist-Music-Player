package com.example.data.repository

import android.net.Uri
import com.example.data.extensions.ExtensionPackageManager
import com.example.data.extensions.ExtensionSecretsStore
import com.example.data.extensions.IsrcCrossMatchRouter
import com.example.data.innertube.InnerTubeClient
import com.example.data.innertube.StreamResolution
import com.example.data.local.LocalMediaScanner
import com.example.data.local.SettingsPreferences
import com.example.data.local.UserAudioSettings
import com.example.data.local.db.CachedTrackEntity
import com.example.data.lyrics.LrcDocument
import com.example.data.lyrics.LyricsRepository
import com.example.data.local.db.PlaylistEntity
import com.example.data.local.db.PlaylistTrackCrossRef
import com.example.data.local.db.SearchHistoryEntity
import com.example.data.local.db.SonoraDao
import com.example.data.streams.DirectStreamProber
import com.example.domain.model.ExtensionRegistryInfo
import com.example.domain.model.InstalledExtension
import com.example.domain.model.Playlist
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.RegistryExtensionEntry
import com.example.domain.model.SearchFilter
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import com.example.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val dao: SonoraDao,
    private val localScanner: LocalMediaScanner,
    private val innerTubeClient: InnerTubeClient,
    private val settingsPreferences: SettingsPreferences,
    private val extensionPackageManager: ExtensionPackageManager? = null,
    private val extensionSecretsStore: ExtensionSecretsStore? = null,
    private val isrcCrossMatchRouter: IsrcCrossMatchRouter? = null,
    private val lyricsRepository: LyricsRepository? = null
) {
    private val directStreamProber = DirectStreamProber(innerTubeClient.okHttpClient)

    val extensionRegistries: Flow<List<ExtensionRegistryInfo>> =
        extensionPackageManager?.registriesFlow ?: flowOf(emptyList())

    val installedExtensions: Flow<List<InstalledExtension>> =
        extensionPackageManager?.installedExtensionsFlow ?: flowOf(emptyList())

    val localTracks: Flow<List<Track>> = dao.observeLocalTracks().map { list ->
        list.map { it.toDomain() }
    }

    val cachedYoutubeTracks: Flow<List<Track>> = dao.observeCachedYoutubeTracks().map { list ->
        list.map { it.toDomain() }
    }

    val directStreamTracks: Flow<List<Track>> = dao.observeDirectStreamTracks().map { list ->
        list.map { it.toDomain() }
    }

    val favoriteTracks: Flow<List<Track>> = dao.observeFavoriteTracks().map { list ->
        list.map { it.toDomain() }
    }

    val recentlyPlayedTracks: Flow<List<Track>> = dao.observeRecentlyPlayed().map { list ->
        list.map { it.toDomain() }
    }

    val searchHistory: Flow<List<String>> = dao.observeSearchHistory().map { list ->
        list.map { it.query }
    }

    val userSettings: Flow<UserAudioSettings> = settingsPreferences.settingsFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlists: Flow<List<Playlist>> = dao.observePlaylistsWithTracks().flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            val orderedFlows = list.map { pwt ->
                dao.observeOrderedPlaylistTrackIds(pwt.playlist.id).map { orderedIds ->
                    pwt.toDomain(orderedIds)
                }
            }
            combine(orderedFlows) { array -> array.toList() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePlaylist(playlistId: Long): Flow<Playlist?> {
        return dao.observePlaylistWithTracks(playlistId).flatMapLatest { pwt ->
            if (pwt == null) {
                flowOf(null)
            } else {
                dao.observeOrderedPlaylistTrackIds(playlistId).map { orderedIds ->
                    pwt.toDomain(orderedIds)
                }
            }
        }
    }

    suspend fun scanAndSyncLocalLibrary(): List<Track> {
        val scanned = localScanner.scanDeviceAudio()
        val favoriteIds = dao.getFavoriteIds().toSet()
        if (scanned.isNotEmpty()) {
            val entities = scanned.map { track ->
                val existing = dao.getTrackById(track.id)
                CachedTrackEntity.fromDomain(
                    track = track,
                    preserveFavorite = track.id in favoriteIds,
                    lastPlayedAt = existing?.lastPlayedAt
                )
            }
            dao.upsertTracks(entities)
            val activeIds = scanned.map { it.id }
            // Keep SAF imported docs even if not in MediaStore
            val allStoredLocal = activeIds + entities.filter { it.id.startsWith("local_doc_") }.map { it.id }
            if (allStoredLocal.isNotEmpty()) {
                dao.deleteRemovedLocalTracks(allStoredLocal)
            }
        }
        return scanned
    }

    suspend fun importLocalAudioDocument(uri: Uri): Track? {
        val track = localScanner.importDocumentAudio(uri) ?: return null
        val existing = dao.getTrackById(track.id)
        dao.upsertTrack(
            CachedTrackEntity.fromDomain(
                track = track,
                preserveFavorite = existing?.isFavorite ?: false,
                lastPlayedAt = existing?.lastPlayedAt
            )
        )
        return track
    }

    suspend fun probeDirectStream(
        primaryUrl: String,
        customTitle: String? = null,
        fallbackUrl: String? = null
    ): Result<Track> {
        return directStreamProber.probeStreamTrack(
            primaryUrl = primaryUrl,
            customTitle = customTitle,
            fallbackUrl = fallbackUrl
        )
    }

    suspend fun saveDirectStreamTrack(track: Track) {
        val existing = dao.getTrackById(track.id)
        dao.upsertTrack(
            CachedTrackEntity.fromDomain(
                track = track,
                preserveFavorite = existing?.isFavorite ?: track.isFavorite,
                lastPlayedAt = existing?.lastPlayedAt
            )
        )
    }

    suspend fun deleteDirectStreamTrack(trackId: String) {
        dao.deleteTrackById(trackId)
    }

    suspend fun toggleFavorite(track: Track): Boolean {
        val existing = dao.getTrackById(track.id)
        val newFav = !(existing?.isFavorite ?: track.isFavorite)
        if (existing == null) {
            dao.upsertTrack(
                CachedTrackEntity.fromDomain(track, preserveFavorite = newFav)
            )
        } else {
            dao.setFavorite(track.id, newFav)
        }
        return newFav
    }

    suspend fun recordTrackPlayed(track: Track) {
        val existing = dao.getTrackById(track.id)
        val now = System.currentTimeMillis()
        if (existing == null) {
            dao.upsertTrack(
                CachedTrackEntity.fromDomain(
                    track = track,
                    preserveFavorite = track.isFavorite,
                    lastPlayedAt = now
                )
            )
        } else {
            dao.updateLastPlayed(track.id, now)
        }
    }

    suspend fun cacheTracksMetadata(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val favoriteIds = dao.getFavoriteIds().toSet()
        val entities = tracks.map { track ->
            val existing = dao.getTrackById(track.id)
            CachedTrackEntity.fromDomain(
                track = track,
                preserveFavorite = existing?.isFavorite ?: (track.id in favoriteIds),
                lastPlayedAt = existing?.lastPlayedAt
            )
        }
        dao.upsertTracks(entities)
    }

    suspend fun createPlaylist(name: String, description: String = "", initialTracks: List<Track> = emptyList()): Long {
        val cleanName = name.trim().ifBlank { "Untitled Mix" }
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(name = cleanName, description = description.trim())
        )
        if (initialTracks.isNotEmpty()) {
            addTracksToPlaylist(playlistId, initialTracks)
        }
        return playlistId
    }

    suspend fun renamePlaylist(playlistId: Long, name: String, description: String = "") {
        dao.updatePlaylist(playlistId, name.trim().ifBlank { "Untitled Mix" }, description.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) {
        addTracksToPlaylist(playlistId, listOf(track))
    }

    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        cacheTracksMetadata(tracks)
        var nextPos = dao.getNextPlaylistPosition(playlistId)
        for (track in tracks) {
            dao.insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = nextPos++
                )
            )
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun moveTrackInPlaylist(playlistId: Long, orderedTracks: List<Track>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in orderedTracks.indices || toIndex !in orderedTracks.indices) return
        val mutable = orderedTracks.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        mutable.forEachIndexed { index, track ->
            dao.updateTrackPosition(playlistId, track.id, index)
        }
    }

    suspend fun searchYouTubeMusic(query: String, filter: SearchFilter): Result<List<Track>> {
        if (query.isNotBlank()) {
            dao.insertSearchHistory(SearchHistoryEntity(query = query.trim()))
        }
        return innerTubeClient.search(query, filter)
    }

    suspend fun getSearchSuggestions(query: String): Result<List<String>> {
        return innerTubeClient.getSearchSuggestions(query)
    }

    suspend fun getExploreFeatured(): Result<List<Track>> {
        return innerTubeClient.getExploreFeatured()
    }

    suspend fun getAccountPersonalizedTracks(cookieOverride: String? = null): Result<List<Track>> {
        if (!cookieOverride.isNullOrBlank()) {
            innerTubeClient.updateSessionCookie(cookieOverride)
        }
        return innerTubeClient.getAccountPersonalizedTracks()
    }

    suspend fun getRadioQueue(videoId: String): Result<List<Track>> {
        return innerTubeClient.getRadioNext(videoId)
    }

    suspend fun resolveStream(videoId: String, preferOpus: Boolean): Result<StreamResolution> {
        return innerTubeClient.resolveStream(videoId, preferOpus)
    }

    suspend fun deleteSearchHistoryItem(query: String) {
        dao.deleteSearchHistory(query)
    }

    suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }

    suspend fun setAutoRadioEnabled(enabled: Boolean) {
        settingsPreferences.setAutoRadioEnabled(enabled)
    }

    suspend fun setPreferOpus(preferOpus: Boolean) {
        settingsPreferences.setPreferOpus(preferOpus)
    }

    suspend fun setAutoSkipBrokenTracks(enabled: Boolean) {
        settingsPreferences.setAutoSkipBrokenTracks(enabled)
    }

    suspend fun setPreferredStreamQuality(quality: PreferredStreamQuality) {
        settingsPreferences.setPreferredStreamQuality(quality)
    }

    suspend fun setDirectFloatPcmBypassEq(enabled: Boolean) {
        settingsPreferences.setDirectFloatPcmBypassEq(enabled)
    }

    suspend fun setCrossfadeDurationSeconds(seconds: Int) {
        settingsPreferences.setCrossfadeDurationSeconds(seconds)
    }

    fun clearStreamCache() {
        innerTubeClient.clearAllStreamCaches()
        isrcCrossMatchRouter?.clearCache()
    }

    suspend fun addAndSyncRegistry(url: String): Result<ExtensionRegistryInfo> {
        val mgr = extensionPackageManager
            ?: return Result.failure(IllegalStateException("Extension host unavailable"))
        return mgr.addAndSyncRegistry(url)
    }

    suspend fun syncAllRegistries(): Result<Int> {
        val mgr = extensionPackageManager
            ?: return Result.failure(IllegalStateException("Extension host unavailable"))
        return mgr.syncAllRegistries()
    }

    suspend fun removeRegistry(url: String) {
        extensionPackageManager?.removeRegistry(url)
    }

    suspend fun installExtensionFromRegistry(
        entry: RegistryExtensionEntry,
        registryUrl: String
    ): Result<InstalledExtension> {
        val mgr = extensionPackageManager
            ?: return Result.failure(IllegalStateException("Extension host unavailable"))
        return mgr.installFromRegistryEntry(entry, registryUrl)
    }

    suspend fun installExtensionFromLocalUri(uri: Uri): Result<InstalledExtension> {
        val mgr = extensionPackageManager
            ?: return Result.failure(IllegalStateException("Extension host unavailable"))
        return mgr.installFromLocalUri(uri)
    }

    suspend fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        extensionPackageManager?.setExtensionEnabled(extensionId, enabled)
        isrcCrossMatchRouter?.clearCache()
    }

    suspend fun uninstallExtension(extensionId: String) {
        extensionPackageManager?.uninstallExtension(extensionId)
        isrcCrossMatchRouter?.clearCache()
    }

    fun getExtensionSettings(extensionId: String): Map<String, String> {
        return extensionSecretsStore?.getAllSettings(extensionId).orEmpty()
    }

    fun saveExtensionSetting(extensionId: String, key: String, value: String, isSecret: Boolean) {
        extensionSecretsStore?.setSetting(extensionId, key, value, isSecret)
        isrcCrossMatchRouter?.clearCache()
    }

    suspend fun searchExtensionCatalog(
        extensionId: String,
        query: String,
        filter: SearchFilter
    ): Result<List<Track>> {
        if (query.isNotBlank()) {
            dao.insertSearchHistory(SearchHistoryEntity(query = query.trim()))
        }
        val router = isrcCrossMatchRouter
            ?: return Result.failure(IllegalStateException("Extension router unavailable"))
        return router.searchCatalog(extensionId, query, filter)
    }

    suspend fun getExtensionFeatured(extensionId: String): Result<List<Track>> {
        val router = isrcCrossMatchRouter
            ?: return Result.failure(IllegalStateException("Extension router unavailable"))
        return router.getFeaturedCatalog(extensionId)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsPreferences.setThemeMode(mode)
    }

    suspend fun setThemeAccent(accent: ThemeAccent) {
        settingsPreferences.setThemeAccent(accent)
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        settingsPreferences.setUseDynamicColor(enabled)
    }

    suspend fun setCornerStyle(style: ThemeCornerStyle) {
        settingsPreferences.setCornerStyle(style)
    }

    suspend fun setPlayerBackdropStyle(style: PlayerBackdropStyle) {
        settingsPreferences.setPlayerBackdropStyle(style)
    }

    suspend fun setSelectedGoogleEmail(email: String?) {
        settingsPreferences.setSelectedGoogleEmail(email)
    }

    suspend fun saveYouTubeAccount(cookie: String, customLabel: String? = null): Result<String> {
        val cleanCookie = com.example.data.innertube.InnerTubeAuthHelper.sanitizeCookie(cookie)
        if (!com.example.data.innertube.InnerTubeAuthHelper.hasValidSessionCookie(cleanCookie)) {
            return Result.failure(
                IllegalArgumentException("Session cookie is missing SAPISID / __Secure-3PAPISID. Please complete Google sign-in.")
            )
        }
        val fetchedName = innerTubeClient.fetchAccountDisplayName(cleanCookie)
        val resolvedLabel = when {
            !fetchedName.isNullOrBlank() && !customLabel.isNullOrBlank() &&
                !fetchedName.equals(customLabel, ignoreCase = true) -> "$fetchedName ($customLabel)"
            !fetchedName.isNullOrBlank() -> fetchedName
            !customLabel.isNullOrBlank() -> customLabel
            else -> "YouTube Music Account"
        }
        settingsPreferences.saveYouTubeAccount(cleanCookie, resolvedLabel)
        innerTubeClient.updateSessionCookie(cleanCookie)
        return Result.success(resolvedLabel)
    }

    suspend fun clearYouTubeAccount() {
        settingsPreferences.clearYouTubeAccount()
        innerTubeClient.updateSessionCookie(null)
    }

    suspend fun loadLyricsForTrack(track: Track, forceRefresh: Boolean = false): Result<LrcDocument> {
        val repo = lyricsRepository
            ?: return Result.failure(IllegalStateException("Lyrics repository unavailable"))
        return repo.loadLyricsForTrack(track, forceRefresh)
    }

    suspend fun importLrcForTrack(track: Track, uri: Uri): Result<LrcDocument> {
        val repo = lyricsRepository
            ?: return Result.failure(IllegalStateException("Lyrics repository unavailable"))
        return repo.importLrcFromUri(track, uri)
    }
}
