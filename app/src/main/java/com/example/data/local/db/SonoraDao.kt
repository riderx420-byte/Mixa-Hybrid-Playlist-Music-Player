package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SonoraDao {

    @Query("SELECT * FROM cached_tracks WHERE source = 'LOCAL' ORDER BY dateAdded DESC")
    fun observeLocalTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE source = 'YOUTUBE' ORDER BY COALESCE(lastPlayedAt, dateAdded) DESC")
    fun observeCachedYoutubeTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE source = 'DIRECT_STREAM' ORDER BY COALESCE(lastPlayedAt, dateAdded) DESC")
    fun observeDirectStreamTracks(): Flow<List<CachedTrackEntity>>

    @Query("DELETE FROM cached_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("SELECT * FROM cached_tracks WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun observeFavoriteTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 30")
    fun observeRecentlyPlayed(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): CachedTrackEntity?

    @Query("SELECT id FROM cached_tracks WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: CachedTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<CachedTrackEntity>)

    @Query("UPDATE cached_tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE cached_tracks SET lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun updateLastPlayed(trackId: String, timestamp: Long)

    @Query("DELETE FROM cached_tracks WHERE source = 'LOCAL' AND id NOT IN (:activeLocalIds)")
    suspend fun deleteRemovedLocalTracks(activeLocalIds: List<String>)

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun observeOrderedPlaylistTrackIds(playlistId: Long): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getNextPlaylistPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
    suspend fun updatePlaylist(playlistId: Long, name: String, description: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackPosition(playlistId: Long, trackId: String, position: Int)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM extension_registries ORDER BY addedAt DESC")
    fun observeExtensionRegistries(): Flow<List<ExtensionRegistryEntity>>

    @Query("SELECT * FROM extension_registries ORDER BY addedAt DESC")
    suspend fun getExtensionRegistriesOnce(): List<ExtensionRegistryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtensionRegistry(registry: ExtensionRegistryEntity)

    @Query("DELETE FROM extension_registries WHERE url = :url")
    suspend fun deleteExtensionRegistry(url: String)

    @Query("SELECT * FROM installed_extensions ORDER BY installedAt ASC")
    fun observeInstalledExtensions(): Flow<List<InstalledExtensionEntity>>

    @Query("SELECT * FROM installed_extensions WHERE isEnabled = 1 ORDER BY installedAt ASC")
    suspend fun getEnabledExtensionsOnce(): List<InstalledExtensionEntity>

    @Query("SELECT * FROM installed_extensions WHERE id = :id LIMIT 1")
    suspend fun getInstalledExtensionById(id: String): InstalledExtensionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstalledExtension(extension: InstalledExtensionEntity)

    @Query("UPDATE installed_extensions SET isEnabled = :enabled WHERE id = :id")
    suspend fun setExtensionEnabled(id: String, enabled: Boolean)

    @Query("UPDATE installed_extensions SET lastError = :error WHERE id = :id")
    suspend fun updateExtensionLastError(id: String, error: String?)

    @Query("DELETE FROM installed_extensions WHERE id = :id")
    suspend fun deleteInstalledExtension(id: String)
}
