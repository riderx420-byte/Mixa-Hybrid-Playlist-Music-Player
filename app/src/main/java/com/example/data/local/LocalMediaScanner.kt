package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaScanner(private val context: Context) {

    suspend fun scanDeviceAudio(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" }.orEmpty()
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val filePath = cursor.getString(dataCol).orEmpty()
                    val dateAdded = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    ).toString()

                    val artworkUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    } else {
                        null
                    }

                    val folderName = File(filePath).parentFile?.name ?: "Device Audio"
                    val formatLabel = formatLabelFromMimeOrPath(mime, filePath)

                    tracks.add(
                        Track(
                            id = "local_$mediaId",
                            source = TrackSource.LOCAL,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            artworkUri = artworkUri,
                            contentUri = contentUri,
                            videoId = null,
                            audioFormatLabel = formatLabel,
                            folderName = folderName,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        }
        tracks
    }

    suspend fun importDocumentAudio(uri: Uri): Track? = withContext(Dispatchers.IO) {
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }

            val retriever = MediaMetadataRetriever()
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var durationMs = 0L

            try {
                retriever.setDataSource(context, uri)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }

            var displayName: String? = null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) displayName = c.getString(idx)
                    }
                }

            val cleanFileTitle = displayName?.substringBeforeLast('.') ?: "Imported Audio"
            val mime = context.contentResolver.getType(uri).orEmpty()
            val formatLabel = formatLabelFromMimeOrPath(mime, displayName.orEmpty())

            Track(
                id = "local_doc_${uri.toString().hashCode().toUInt()}",
                source = TrackSource.LOCAL,
                title = title?.takeIf { it.isNotBlank() } ?: cleanFileTitle,
                artist = artist?.takeIf { it.isNotBlank() } ?: "Local File",
                album = album.orEmpty(),
                durationMs = durationMs,
                artworkUri = null,
                contentUri = uri.toString(),
                videoId = null,
                audioFormatLabel = formatLabel,
                folderName = "Imported",
                dateAdded = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun formatLabelFromMimeOrPath(mime: String, path: String): String {
        val lower = "$mime $path".lowercase()
        return when {
            "flac" in lower -> "FLAC"
            "alac" in lower -> "ALAC"
            "wav" in lower -> "WAV"
            "ogg" in lower || "vorbis" in lower -> "OGG"
            "opus" in lower -> "OPUS"
            "m4a" in lower || "mp4" in lower || "aac" in lower -> "AAC"
            "mpeg" in lower || "mp3" in lower -> "MP3"
            else -> "LOCAL"
        }
    }
}
