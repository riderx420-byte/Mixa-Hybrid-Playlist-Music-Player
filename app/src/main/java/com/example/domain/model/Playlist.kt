package com.example.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val description: String = "",
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val trackCount: Int get() = tracks.size
    val localCount: Int get() = tracks.count { it.source == TrackSource.LOCAL }
    val youtubeCount: Int get() = tracks.count { it.source == TrackSource.YOUTUBE }
    val extensionCount: Int get() = tracks.count { it.source == TrackSource.EXTENSION }
    val radioCount: Int get() = tracks.count { it.source == TrackSource.DIRECT_STREAM }
    val coverArtworkUri: String? get() = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
    val collageArtworkUris: List<String>
        get() = tracks.mapNotNull { it.artworkUri?.takeIf(String::isNotBlank) }.distinct().take(4)
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
}
