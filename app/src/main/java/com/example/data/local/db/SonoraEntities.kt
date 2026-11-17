package com.example.data.local.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.domain.model.TrackSource

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String?,
    val contentUri: String?,
    val videoId: String?,
    val audioFormatLabel: String,
    val bitrateKbps: Int?,
    val folderName: String?,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val isLiveStream: Boolean = false,
    val variantsJson: String? = null,
    val activeVariantId: String? = null,
    val extensionId: String? = null,
    val providerTrackId: String? = null,
    val isrc: String? = null,
    val isMetadataOnly: Boolean = false,
    val resolvedByExtensionId: String? = null,
    val sourceLabel: String? = null
) {
    fun toDomain(): Track {
        val domainSource = when (source) {
            TrackSource.YOUTUBE.name -> TrackSource.YOUTUBE
            TrackSource.DIRECT_STREAM.name -> TrackSource.DIRECT_STREAM
            TrackSource.EXTENSION.name -> TrackSource.EXTENSION
            else -> TrackSource.LOCAL
        }
        return Track(
            id = id,
            source = domainSource,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUri = artworkUri,
            contentUri = contentUri,
            videoId = videoId,
            audioFormatLabel = audioFormatLabel,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            isLiveStream = isLiveStream,
            variants = Track.decodeVariants(variantsJson),
            activeVariantId = activeVariantId,
            folderName = folderName,
            isFavorite = isFavorite,
            dateAdded = dateAdded,
            extensionId = extensionId,
            providerTrackId = providerTrackId,
            isrc = isrc,
            isMetadataOnly = isMetadataOnly,
            resolvedByExtensionId = resolvedByExtensionId,
            sourceLabel = sourceLabel
        )
    }

    companion object {
        fun fromDomain(track: Track, preserveFavorite: Boolean? = null, lastPlayedAt: Long? = null): CachedTrackEntity {
            return CachedTrackEntity(
                id = track.id,
                source = track.source.name,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkUri = track.artworkUri,
                contentUri = track.contentUri,
                videoId = track.videoId,
                audioFormatLabel = track.audioFormatLabel,
                bitrateKbps = track.bitrateKbps,
                folderName = track.folderName,
                isFavorite = preserveFavorite ?: track.isFavorite,
                dateAdded = track.dateAdded,
                lastPlayedAt = lastPlayedAt,
                sampleRateHz = track.sampleRateHz,
                bitDepth = track.bitDepth,
                isLiveStream = track.isLiveStream,
                variantsJson = Track.encodeVariants(track.variants),
                activeVariantId = track.activeVariantId,
                extensionId = track.extensionId,
                providerTrackId = track.providerTrackId,
                isrc = track.isrc,
                isMetadataOnly = track.isMetadataOnly,
                resolvedByExtensionId = track.resolvedByExtensionId,
                sourceLabel = track.sourceLabel
            )
        }
    }
}

@Entity(tableName = "extension_registries")
data class ExtensionRegistryEntity(
    @PrimaryKey val url: String,
    val name: String,
    val entriesJson: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "installed_extensions")
data class InstalledExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val registryUrl: String?,
    val manifestJson: String,
    val isEnabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val sha256: String? = null,
    val localDirPath: String,
    val lastError: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CachedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    val tracks: List<CachedTrackEntity>
) {
    fun toDomain(orderedTrackIds: List<String>? = null): Playlist {
        val mapped = if (orderedTrackIds != null) {
            val byId = tracks.associateBy { it.id }
            orderedTrackIds.mapNotNull { byId[it]?.toDomain() }
        } else {
            tracks.map { it.toDomain() }
        }
        return Playlist(
            id = playlist.id,
            name = playlist.name,
            description = playlist.description,
            tracks = mapped,
            createdAt = playlist.createdAt
        )
    }
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
