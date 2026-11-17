package com.example.data.extensions

import android.net.Uri
import com.example.data.innertube.InnerTubeClient
import com.example.domain.model.InstalledExtension
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.ResolvedExtensionAudio
import com.example.domain.model.SearchFilter
import com.example.domain.model.Track
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Coordinates extension catalog search, featured browsing, and lazy playback resolution
 * with automatic ISRC cross-matching (e.g., Apple Music -> Qobuz / Deezer) and
 * YouTube Music fallback so every track stays playable without breaking existing paths.
 */
class IsrcCrossMatchRouter(
    private val packageManager: ExtensionPackageManager,
    private val runtime: QuickJsExtensionRuntime,
    private val innerTubeClient: InnerTubeClient,
    private val preferredQualityProvider: () -> PreferredStreamQuality,
    private val preferOpusProvider: () -> Boolean
) {
    private data class CachedResolution(
        val audio: ResolvedExtensionAudio,
        val expiresAtMs: Long
    )

    private val resolutionCache = ConcurrentHashMap<String, CachedResolution>()

    fun clearCache() {
        resolutionCache.clear()
    }

    suspend fun searchCatalog(
        extensionId: String,
        query: String,
        filter: SearchFilter
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val ext = packageManager.getInstalledExtension(extensionId)
                ?: throw IllegalStateException("Extension '$extensionId' is not installed")
            require(ext.isEnabled) { "${ext.name} is currently disabled" }
            val script = packageManager.readEntryScript(ext)
            val hookCandidates = when (filter) {
                SearchFilter.ALBUMS -> listOf("searchAlbums", "customSearch", "searchAll", "searchTracks", "search", "queryTracks")
                SearchFilter.ARTISTS -> listOf("searchArtists", "customSearch", "searchAll", "searchTracks", "search", "queryTracks")
                else -> listOf("searchTracks", "customSearch", "searchAll", "search", "queryTracks")
            }
            val args = JSONArray().apply {
                put(query)
                put(25)
                put(0)
            }.toString()
            val raw = runtime.invokeHook(
                extension = ext,
                scriptSource = script,
                hookNames = hookCandidates,
                argsJsonArray = args
            )
            if (raw == "__SONORA_HOOK_MISSING__") {
                packageManager.recordExtensionError(ext.id, "Extension does not implement searchTracks/customSearch()")
                emptyList()
            } else {
                packageManager.recordExtensionError(ext.id, null)
                ExtensionManifestParser.parseTracksFromJsResult(raw, ext)
            }
        }.onFailure { err ->
            runCatching { packageManager.recordExtensionError(extensionId, err.message) }
        }
    }

    suspend fun getFeaturedCatalog(extensionId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val ext = packageManager.getInstalledExtension(extensionId)
                ?: throw IllegalStateException("Extension '$extensionId' is not installed")
            if (!ext.isEnabled) return@runCatching emptyList()
            val script = packageManager.readEntryScript(ext)
            val raw = runtime.invokeHook(
                extension = ext,
                scriptSource = script,
                hookNames = listOf("homeFeed", "getHomeFeed", "getFeatured", "getCharts", "featured", "home"),
                argsJsonArray = "[]"
            )
            if (raw == "__SONORA_HOOK_MISSING__") {
                emptyList()
            } else {
                ExtensionManifestParser.parseTracksFromJsResult(raw, ext)
            }
        }
    }

    /**
     * Blocking entrypoint called by SonoraDataSourceFactory on ExoPlayer's background loader thread
     * whenever a `sonora://ext/...` virtual URI is opened.
     */
    fun resolveExtensionUriSync(rawUri: String): ResolvedExtensionAudio {
        val parsed = Uri.parse(rawUri)
        val segments = parsed.pathSegments
        val extensionId = segments.getOrNull(0).orEmpty()
        val providerTrackId = segments.getOrNull(1).orEmpty()
        val isrc = parsed.getQueryParameter("isrc")
        val title = parsed.getQueryParameter("title")
        val artist = parsed.getQueryParameter("artist")
        val requestedVariantId = parsed.getQueryParameter("variant")

        val cacheKey = "$extensionId::$providerTrackId::${isrc.orEmpty()}"
        val cached = resolutionCache[cacheKey]
        val baseResolved = if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
            cached.audio
        } else {
            val fresh = runBlocking(Dispatchers.IO) {
                resolvePlaybackInternal(
                    originExtensionId = extensionId,
                    providerTrackId = providerTrackId,
                    initialIsrc = isrc,
                    title = title,
                    artist = artist
                )
            }
            resolutionCache[cacheKey] = CachedResolution(
                audio = fresh,
                expiresAtMs = System.currentTimeMillis() + CACHE_TTL_MS
            )
            fresh
        }

        val overrideVariant = requestedVariantId?.let { vid ->
            baseResolved.variants.firstOrNull { it.id == vid }
        }
        return if (overrideVariant != null) {
            baseResolved.copy(
                streamUrl = overrideVariant.url,
                headers = overrideVariant.headers.ifEmpty { baseResolved.headers },
                decryptionSpec = overrideVariant.decryptionSpec ?: baseResolved.decryptionSpec
            )
        } else {
            baseResolved
        }
    }

    suspend fun resolvePlaybackForTrack(track: Track): Result<ResolvedExtensionAudio> = withContext(Dispatchers.IO) {
        runCatching {
            val extId = track.extensionId.orEmpty()
            val pTrackId = track.providerTrackId ?: track.id
            resolvePlaybackInternal(
                originExtensionId = extId,
                providerTrackId = pTrackId,
                initialIsrc = track.isrc,
                title = track.title,
                artist = track.artist
            )
        }
    }

    private suspend fun resolvePlaybackInternal(
        originExtensionId: String,
        providerTrackId: String,
        initialIsrc: String?,
        title: String?,
        artist: String?
    ): ResolvedExtensionAudio {
        val originExt = packageManager.getInstalledExtension(originExtensionId)
        val preferredQuality = preferredQualityProvider().name
        var effectiveIsrc = initialIsrc?.trim()?.takeIf { it.isNotBlank() }
        var effectiveTitle = title?.trim()?.takeIf { it.isNotBlank() }
        var effectiveArtist = artist?.trim()?.takeIf { it.isNotBlank() }

        // 1. Direct playback from origin extension when not metadata-only
        if (originExt != null && originExt.isEnabled && originExt.manifest.canStreamAudio) {
            val direct = tryResolveStreamOnExtension(
                ext = originExt,
                providerTrackId = providerTrackId,
                preferredQuality = preferredQuality,
                isrc = effectiveIsrc,
                title = effectiveTitle,
                artist = effectiveArtist
            )
            if (direct != null) {
                return direct
            }
        }

        // 2. Enrich ISRC / track metadata from origin if missing (Apple Music / metadata providers)
        if (originExt != null && originExt.isEnabled && effectiveIsrc.isNullOrBlank()) {
            val detailed = tryGetTrackDetails(originExt, providerTrackId)
            if (detailed != null) {
                effectiveIsrc = detailed.isrc?.takeIf { it.isNotBlank() } ?: effectiveIsrc
                effectiveTitle = detailed.title.takeIf { it.isNotBlank() } ?: effectiveTitle
                effectiveArtist = detailed.artist.takeIf { it.isNotBlank() } ?: effectiveArtist
            }
        }

        // 3. Cross-match across installed streaming extensions ordered by lossless fidelity priority (Qobuz > Deezer > others)
        val streamingProviders = packageManager.getEnabledExtensions()
            .filter { it.id != originExtensionId && it.manifest.canStreamAudio }
            .sortedByDescending { it.manifest.priority }

        for (candidateExt in streamingProviders) {
            // 3a. Exact ISRC match
            if (!effectiveIsrc.isNullOrBlank()) {
                val matched = tryMatchByIsrc(candidateExt, effectiveIsrc, effectiveTitle, effectiveArtist)
                if (matched != null) {
                    val directFromMatched = matched.variants.firstOrNull()?.url?.takeIf {
                        it.startsWith("http://") || it.startsWith("https://")
                    }
                    if (directFromMatched != null) {
                        return ResolvedExtensionAudio(
                            streamUrl = directFromMatched,
                            variants = matched.variants,
                            headers = matched.variants.first().headers,
                            decryptionSpec = matched.variants.first().decryptionSpec,
                            resolvedByExtensionId = candidateExt.id,
                            resolvedByLabel = candidateExt.name
                        )
                    }
                    val targetId = matched.providerTrackId ?: matched.id
                    val audio = tryResolveStreamOnExtension(
                        ext = candidateExt,
                        providerTrackId = targetId,
                        preferredQuality = preferredQuality,
                        isrc = effectiveIsrc,
                        title = matched.title.ifBlank { effectiveTitle },
                        artist = matched.artist.ifBlank { effectiveArtist }
                    )
                    if (audio != null) return audio
                }
            }

            // 3b. Title + Artist catalog fallback inside lossless extension
            if (!effectiveTitle.isNullOrBlank()) {
                val query = listOfNotNull(effectiveTitle, effectiveArtist).joinToString(" ")
                val searchHits = searchCatalog(candidateExt.id, query, SearchFilter.SONGS).getOrDefault(emptyList())
                val bestHit = pickBestMetadataMatch(searchHits, effectiveTitle, effectiveArtist)
                if (bestHit != null) {
                    val targetId = bestHit.providerTrackId ?: bestHit.id
                    val audio = tryResolveStreamOnExtension(
                        ext = candidateExt,
                        providerTrackId = targetId,
                        preferredQuality = preferredQuality,
                        isrc = bestHit.isrc ?: effectiveIsrc,
                        title = bestHit.title,
                        artist = bestHit.artist
                    )
                    if (audio != null) return audio
                }
            }
        }

        // 4. Final resilient fallback -> Sonora's existing YouTube Music InnerTube engine
        if (!effectiveTitle.isNullOrBlank()) {
            val ytQuery = listOfNotNull(effectiveTitle, effectiveArtist).joinToString(" - ")
            val ytResults = innerTubeClient.search(ytQuery, SearchFilter.SONGS).getOrDefault(emptyList())
            val ytMatch = ytResults.firstOrNull { !it.videoId.isNullOrBlank() }
            val videoId = ytMatch?.videoId
            if (!videoId.isNullOrBlank()) {
                val ytStream = innerTubeClient.resolveStreamSync(
                    videoId = videoId,
                    preferOpus = preferOpusProvider(),
                    allowHlsFallback = true
                )
                return ResolvedExtensionAudio(
                    streamUrl = ytStream.streamUrl,
                    headers = mapOf("User-Agent" to ytStream.userAgent),
                    resolvedByExtensionId = "youtube_music",
                    resolvedByLabel = "YouTube Music",
                    isYouTubeFallback = true,
                    fallbackVideoId = videoId
                )
            }
        }

        throw IOException("Unable to resolve playable audio stream for ${effectiveTitle ?: providerTrackId}")
    }

    private suspend fun tryResolveStreamOnExtension(
        ext: InstalledExtension,
        providerTrackId: String,
        preferredQuality: String,
        isrc: String? = null,
        title: String? = null,
        artist: String? = null
    ): ResolvedExtensionAudio? {
        return runCatching {
            val script = packageManager.readEntryScript(ext)
            val idArgs = JSONArray().apply {
                put(providerTrackId)
                put(preferredQuality)
            }.toString()
            val rawDirect = runtime.invokeHook(
                extension = ext,
                scriptSource = script,
                hookNames = listOf("resolveStream", "getStreamUrl", "getAudioStream"),
                argsJsonArray = idArgs
            )
            val parsedDirect = if (rawDirect != "__SONORA_HOOK_MISSING__") {
                ExtensionManifestParser.parseResolvedAudioFromJsResult(rawDirect, ext)
            } else {
                null
            }
            if (parsedDirect != null) return@runCatching parsedDirect

            // Fallback: Mornye download_provider extensions expect track object in getDownloadUrl(track, quality)
            val trackObj = JSONObject().apply {
                put("id", providerTrackId)
                put("track_id", providerTrackId)
                if (!isrc.isNullOrBlank()) put("isrc", isrc)
                if (!title.isNullOrBlank()) {
                    put("title", title)
                    put("name", title)
                    put("track_name", title)
                }
                if (!artist.isNullOrBlank()) {
                    put("artist", artist)
                    put("artist_name", artist)
                    put("artists", artist)
                }
            }
            val objArgs = JSONArray().apply {
                put(trackObj)
                put(preferredQuality)
            }.toString()
            val rawObj = runtime.invokeHook(
                extension = ext,
                scriptSource = script,
                hookNames = listOf("getDownloadUrl", "resolveStream", "getStreamUrl"),
                argsJsonArray = objArgs
            )
            if (rawObj == "__SONORA_HOOK_MISSING__") {
                null
            } else {
                ExtensionManifestParser.parseResolvedAudioFromJsResult(rawObj, ext)
            }
        }.getOrNull()
    }

    private suspend fun tryGetTrackDetails(
        ext: InstalledExtension,
        providerTrackId: String
    ): Track? {
        return runCatching {
            val script = packageManager.readEntryScript(ext)
            val args = JSONArray().apply { put(providerTrackId) }.toString()
            val raw = runtime.invokeHook(
                extension = ext,
                scriptSource = script,
                hookNames = listOf("getTrack", "enrichTrack", "getTrackDetails", "trackInfo"),
                argsJsonArray = args
            )
            if (raw == "__SONORA_HOOK_MISSING__" || raw == "null") {
                null
            } else {
                ExtensionManifestParser.parseTracksFromJsResult(raw, ext).firstOrNull()
            }
        }.getOrNull()
    }

    private suspend fun tryMatchByIsrc(
        ext: InstalledExtension,
        isrc: String,
        title: String?,
        artist: String?
    ): Track? {
        return runCatching {
            val script = packageManager.readEntryScript(ext)
            val metaObj = JSONObject().apply {
                put("isrc", isrc)
                if (!title.isNullOrBlank()) put("title", title)
                if (!artist.isNullOrBlank()) put("artist", artist)
            }
            val args = JSONArray().apply {
                put(isrc)
                put(metaObj)
            }.toString()
            val raw = runtime.invokeHook(
                extension = ext,
                scriptSource = script,
                hookNames = listOf("getTrackByIsrc", "matchByIsrc", "findByIsrc"),
                argsJsonArray = args
            )
            if (raw == "__SONORA_HOOK_MISSING__" || raw == "null") {
                null
            } else {
                ExtensionManifestParser.parseTracksFromJsResult(raw, ext).firstOrNull()
            }
        }.getOrNull()
    }

    companion object {
        private const val CACHE_TTL_MS = 4 * 60_000L

        fun pickBestMetadataMatch(
            candidates: List<Track>,
            targetTitle: String,
            targetArtist: String?
        ): Track? {
            if (candidates.isEmpty()) return null
            val normTitle = normalize(targetTitle)
            val normArtist = targetArtist?.let(::normalize).orEmpty()
            return candidates.maxByOrNull { track ->
                var score = 0
                val t = normalize(track.title)
                val a = normalize(track.artist)
                if (t == normTitle) score += 10 else if (t.contains(normTitle) || normTitle.contains(t)) score += 5
                if (normArtist.isNotBlank()) {
                    if (a == normArtist) score += 8 else if (a.contains(normArtist) || normArtist.contains(a)) score += 4
                }
                score
            }?.takeIf { track ->
                val t = normalize(track.title)
                t == normTitle || t.contains(normTitle) || normTitle.contains(t)
            }
        }

        private fun normalize(text: String): String {
            return text.lowercase()
                .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
