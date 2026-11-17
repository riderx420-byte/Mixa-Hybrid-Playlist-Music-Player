package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Resolves and caches time-synchronized `.lrc` lyrics for any Mixa track:
 * 1. Disk `.lrc` cache (including user-imported `.lrc` files via SAF OpenDocument)
 * 2. Local `.lrc` sidecar files alongside device audio tracks
 * 3. Live synced & plain lyrics via LRCLIB public API (`https://lrclib.net`)
 */
class LyricsRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val cacheDir: File by lazy {
        File(context.filesDir, "lyrics_cache").apply { mkdirs() }
    }

    suspend fun loadLyricsForTrack(
        track: Track,
        forceRefresh: Boolean = false
    ): Result<LrcDocument> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            readCachedLyrics(track.id)?.let { return@withContext Result.success(it) }
        }

        if (track.source == TrackSource.LOCAL) {
            readLocalSidecarLrc(track)?.let { doc ->
                writeCachedLyrics(track.id, doc)
                return@withContext Result.success(doc)
            }
        }

        fetchFromLrcLib(track).onSuccess { doc ->
            writeCachedLyrics(track.id, doc)
        }
    }

    suspend fun importLrcFromUri(track: Track, uri: Uri): Result<LrcDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("Could not open selected .lrc file")

            val parsed = LrcParser.parse(raw, sourceLabel = "Imported .lrc")
            if (parsed.lines.isEmpty()) {
                throw IllegalArgumentException("Selected file contains no readable lyric lines")
            }
            writeCachedLyrics(track.id, parsed)
            parsed
        }
    }

    private fun fetchFromLrcLib(track: Track): Result<LrcDocument> {
        val cleanTitle = cleanTrackTitle(track.title)
        val cleanArtist = cleanArtistName(track.artist)
        val durationSec = (track.durationMs / 1000L).takeIf { it > 0L }

        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Track title unavailable for lyrics search"))
        }

        // Attempt 1: Exact /api/get lookup
        if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
            val getUrlBuilder = StringBuilder("https://lrclib.net/api/get?")
                .append("track_name=").append(Uri.encode(cleanTitle))
                .append("&artist_name=").append(Uri.encode(cleanArtist))
            track.album.takeIf { it.isNotBlank() }?.let { album ->
                getUrlBuilder.append("&album_name=").append(Uri.encode(cleanTrackTitle(album)))
            }
            if (durationSec != null) {
                getUrlBuilder.append("&duration=").append(durationSec)
            }

            runCatching { executeJsonObjectGet(getUrlBuilder.toString()) }.getOrNull()?.let { obj ->
                parseLrcLibJsonEntry(obj)?.let { return Result.success(it) }
            }
        }

        // Attempt 2: Fuzzy /api/search fallback choosing closest duration synced match
        val query = buildString {
            append(cleanTitle)
            if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
                append(' ')
                append(cleanArtist)
            }
        }
        val searchUrl = "https://lrclib.net/api/search?q=${Uri.encode(query)}"
        val searchRes = runCatching { executeJsonArrayGet(searchUrl) }
        val array = searchRes.getOrElse { err ->
            return Result.failure(err)
        }

        var bestSynced: Pair<LrcDocument, Long>? = null
        var bestPlain: LrcDocument? = null

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val parsed = parseLrcLibJsonEntry(item) ?: continue
            val itemDuration = item.optDouble("duration", -1.0)
            val durationDiff = if (durationSec != null && itemDuration > 0.0) {
                abs(itemDuration.toLong() - durationSec)
            } else {
                0L
            }

            if (parsed.isSynced) {
                if (bestSynced == null || durationDiff < bestSynced.second) {
                    bestSynced = parsed to durationDiff
                }
            } else if (bestPlain == null) {
                bestPlain = parsed
            }
        }

        val chosen = bestSynced?.first ?: bestPlain
        return if (chosen != null) {
            Result.success(chosen)
        } else {
            Result.failure(NoSuchElementException("No synchronized lyrics found for \"$cleanTitle\""))
        }
    }

    private fun parseLrcLibJsonEntry(obj: JSONObject): LrcDocument? {
        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val trackName = obj.optString("trackName").takeIf { it.isNotBlank() }
        val artistName = obj.optString("artistName").takeIf { it.isNotBlank() }
        val albumName = obj.optString("albumName").takeIf { it.isNotBlank() }

        if (synced != null) {
            val doc = LrcParser.parse(synced, sourceLabel = "LRCLIB Synced")
            if (doc.lines.isNotEmpty()) {
                return doc.copy(
                    title = doc.title ?: trackName,
                    artist = doc.artist ?: artistName,
                    album = doc.album ?: albumName
                )
            }
        }
        if (plain != null) {
            val doc = LrcParser.parse(plain, sourceLabel = "LRCLIB Plain")
            if (doc.lines.isNotEmpty()) {
                return doc.copy(
                    title = doc.title ?: trackName,
                    artist = doc.artist ?: artistName,
                    album = doc.album ?: albumName
                )
            }
        }
        return null
    }

    private fun executeJsonObjectGet(url: String): JSONObject? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mixa/2.2 (https://github.com/mixa-audio)")
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun executeJsonArrayGet(url: String): JSONArray {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mixa/2.2 (https://github.com/mixa-audio)")
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Lyrics lookup HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            return if (body.isBlank()) JSONArray() else JSONArray(body)
        }
    }

    private fun readLocalSidecarLrc(track: Track): LrcDocument? {
        val rawUri = track.contentUri ?: return null
        return runCatching {
            val uri = Uri.parse(rawUri)
            val filePath: String? = when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    var resolvedPath: String? = null
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.Audio.Media.DATA),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                            if (idx >= 0) resolvedPath = c.getString(idx)
                        }
                    }
                    resolvedPath
                }
                else -> null
            }
            val audioFile = filePath?.let(::File) ?: return@runCatching null
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                val parsed = LrcParser.parse(lrcFile.readText(Charsets.UTF_8), sourceLabel = "Local .lrc")
                parsed.takeIf { it.lines.isNotEmpty() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun readCachedLyrics(trackId: String): LrcDocument? {
        val file = cacheFileFor(trackId)
        if (!file.exists()) return null
        return runCatching {
            val content = file.readText(Charsets.UTF_8)
            val firstNewline = content.indexOf('\n')
            val sourceLabel: String
            val rawLrc: String
            if (content.startsWith(CACHE_SOURCE_PREFIX) && firstNewline > 0) {
                sourceLabel = content.substring(CACHE_SOURCE_PREFIX.length, firstNewline).trim()
                rawLrc = content.substring(firstNewline + 1)
            } else {
                sourceLabel = "Cached LRC"
                rawLrc = content
            }
            LrcParser.parse(rawLrc, sourceLabel = sourceLabel).takeIf { it.lines.isNotEmpty() }
        }.getOrNull()
    }

    private fun writeCachedLyrics(trackId: String, doc: LrcDocument) {
        val raw = doc.rawLrc.ifBlank {
            doc.lines.joinToString("\n") { line ->
                if (doc.isSynced) {
                    val mins = line.timeMs / 60_000L
                    val secs = (line.timeMs % 60_000L) / 1_000L
                    val centis = (line.timeMs % 1_000L) / 10L
                    "[%02d:%02d.%02d]%s".format(mins, secs, centis, line.text)
                } else {
                    line.text
                }
            }
        }
        runCatching {
            cacheFileFor(trackId).writeText(
                "$CACHE_SOURCE_PREFIX${doc.sourceLabel}\n$raw",
                Charsets.UTF_8
            )
        }
    }

    private fun cacheFileFor(trackId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(cacheDir, "$digest.lrc")
    }

    companion object {
        private const val CACHE_SOURCE_PREFIX = "//MIXA_LYRICS_SOURCE:"

        fun cleanTrackTitle(raw: String): String {
            return raw
                .replace(Regex("""\((?:official|lyrics?|audio|music video|video|hd|4k|remaster(?:ed)?|live)[^)]*\)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\[(?:official|lyrics?|audio|music video|video|hd|4k|remaster(?:ed)?|live)[^\]]*\]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\((?:feat\.|ft\.)[^)]*\)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(?:feat\.|ft\.)\s+.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        }

        fun cleanArtistName(raw: String): String {
            return raw
                .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
                .substringBefore(",")
                .substringBefore("&")
                .trim()
        }
    }
}
