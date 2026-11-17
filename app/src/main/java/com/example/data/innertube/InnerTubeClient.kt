package com.example.data.innertube

import com.example.domain.model.SearchFilter
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StreamExtractionException(
    val videoId: String,
    val httpStatus: Int,
    val playability: PlayabilityResult?,
    val triedClients: List<String>,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class InnerTubeClient(
    private val streamCache: StreamUrlCache = StreamUrlCache()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var webRemixVisitorData: String? = null

    @Volatile
    private var sharedVisitorData: String? = null

    private val failedStreamUrls = ConcurrentHashMap<String, Long>()
    private val clientFailureTimestamps = ConcurrentHashMap<String, Long>()

    @Volatile
    var sessionCookie: String? = null
        private set

    fun updateSessionCookie(cookie: String?) {
        val clean = cookie?.let(InnerTubeAuthHelper::sanitizeCookie)
            ?.takeIf(InnerTubeAuthHelper::hasValidSessionCookie)
        if (clean != sessionCookie) {
            sessionCookie = clean
            webRemixVisitorData = null
            sharedVisitorData = null
            streamCache.clear()
            failedStreamUrls.clear()
            clientFailureTimestamps.clear()
        }
    }

    fun markStreamUrlFailed(videoId: String, streamUrl: String) {
        failedStreamUrls[streamUrl] = System.currentTimeMillis()
        streamCache.invalidate(videoId)
    }

    fun clearAllStreamCaches() {
        streamCache.clear()
        failedStreamUrls.clear()
        clientFailureTimestamps.clear()
        sharedVisitorData = null
    }

    fun clearFailedUrlsForVideo(videoId: String) {
        streamCache.invalidate(videoId)
        failedStreamUrls.clear()
        clientFailureTimestamps.clear()
    }

    private fun isUrlRecentlyFailed(url: String): Boolean {
        val failedAt = failedStreamUrls[url] ?: return false
        val stillFresh = (System.currentTimeMillis() - failedAt) < 10 * 60_000L
        if (!stillFresh) {
            failedStreamUrls.remove(url)
        }
        return stillFresh
    }

    private fun isClientPenalized(clientKey: String): Boolean {
        val failedAt = clientFailureTimestamps[clientKey] ?: return false
        val penalized = (System.currentTimeMillis() - failedAt) < 90_000L
        if (!penalized) clientFailureTimestamps.remove(clientKey)
        return penalized
    }

    private fun recordClientFailure(clientKey: String) {
        clientFailureTimestamps[clientKey] = System.currentTimeMillis()
    }

    private fun recordClientSuccess(clientKey: String) {
        clientFailureTimestamps.remove(clientKey)
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun search(query: String, filter: SearchFilter): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, webRemixVisitorData))
                    put("query", query.trim())
                    put("params", filter.innerTubeParams)
                }
                val raw = executePost(
                    url = "${InnerTubeClients.BASE_URL}search?prettyPrint=false",
                    spec = InnerTubeClients.WEB_REMIX,
                    body = payload
                )
                InnerTubeJsonParser.parseSearchResults(raw, filter)
            }
        }

    suspend fun getSearchSuggestions(query: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (query.isBlank()) return@runCatching emptyList()
                val payload = JSONObject().apply {
                    put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, webRemixVisitorData))
                    put("input", query.trim())
                }
                val raw = executePost(
                    url = "${InnerTubeClients.BASE_URL}music/get_search_suggestions?prettyPrint=false",
                    spec = InnerTubeClients.WEB_REMIX,
                    body = payload
                )
                InnerTubeJsonParser.parseSearchSuggestions(raw)
            }
        }

    suspend fun getRadioNext(videoId: String): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, webRemixVisitorData))
                    put("videoId", videoId)
                    put("playlistId", "RDAMVM$videoId")
                    put("isAudioOnly", true)
                    put("enablePersistentPlaylistPanel", true)
                }
                val raw = executePost(
                    url = "${InnerTubeClients.BASE_URL}next?prettyPrint=false",
                    spec = InnerTubeClients.WEB_REMIX,
                    body = payload
                )
                InnerTubeJsonParser.parseRadioQueue(raw)
            }
        }

    suspend fun getExploreFeatured(): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, webRemixVisitorData))
                    put("browseId", "FEmusic_charts")
                }
                val raw = executePost(
                    url = "${InnerTubeClients.BASE_URL}browse?prettyPrint=false",
                    spec = InnerTubeClients.WEB_REMIX,
                    body = payload
                )
                val parsed = InnerTubeJsonParser.parseExploreTracks(raw)
                if (parsed.isNotEmpty()) {
                    parsed
                } else {
                    val fallbackPayload = JSONObject().apply {
                        put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, webRemixVisitorData))
                        put("browseId", "FEmusic_explore")
                    }
                    val fallbackRaw = executePost(
                        url = "${InnerTubeClients.BASE_URL}browse?prettyPrint=false",
                        spec = InnerTubeClients.WEB_REMIX,
                        body = fallbackPayload
                    )
                    InnerTubeJsonParser.parseExploreTracks(fallbackRaw)
                }
            }
        }

    suspend fun getAccountPersonalizedTracks(): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val activeCookie = sessionCookie
                    ?.takeIf(InnerTubeAuthHelper::hasValidSessionCookie)
                    ?: return@runCatching emptyList()
                val collected = mutableListOf<Track>()
                val seenIds = hashSetOf<String>()
                val browseIds = listOf("FEmusic_liked_videos", "FEmusic_history", "FEmusic_home")

                for (browseId in browseIds) {
                    if (collected.size >= 36) break
                    runCatching {
                        val payload = JSONObject().apply {
                            put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, visitorData = null))
                            put("browseId", browseId)
                        }
                        val raw = executePost(
                            url = "${InnerTubeClients.BASE_URL}browse?prettyPrint=false",
                            spec = InnerTubeClients.WEB_REMIX,
                            body = payload,
                            cookieOverride = activeCookie
                        )
                        val tracks = InnerTubeJsonParser.parseExploreTracks(raw)
                        for (t in tracks) {
                            if (seenIds.add(t.id)) {
                                collected.add(t)
                            }
                        }
                    }
                }
                collected
            }
        }

    suspend fun fetchAccountDisplayName(cookieOverride: String? = null): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val activeCookie = (cookieOverride ?: sessionCookie)
                    ?.let(InnerTubeAuthHelper::sanitizeCookie)
                    ?.takeIf(InnerTubeAuthHelper::hasValidSessionCookie)
                    ?: return@runCatching null
                val payload = JSONObject().apply {
                    put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, visitorData = null))
                }
                val raw = executePost(
                    url = "${InnerTubeClients.BASE_URL}account/account_menu?prettyPrint=false",
                    spec = InnerTubeClients.WEB_REMIX,
                    body = payload,
                    cookieOverride = activeCookie
                )
                InnerTubeJsonParser.parseAccountDisplayName(raw)
            }.getOrNull()
        }

    suspend fun resolveStream(
        videoId: String,
        preferOpus: Boolean = true,
        allowHlsFallback: Boolean = false
    ): Result<StreamResolution> =
        withContext(Dispatchers.IO) {
            runCatching { resolveStreamSync(videoId, preferOpus, allowHlsFallback) }
        }

    fun getCachedStream(videoId: String): StreamResolution? = streamCache.getValid(videoId)

    fun getCachedStreamByUrl(url: String): StreamResolution? = streamCache.getValidByUrl(url)

    fun invalidateCachedStream(videoId: String) = streamCache.invalidate(videoId)

    @Throws(IOException::class)
    fun resolveStreamSync(
        videoId: String,
        preferOpus: Boolean = true,
        allowHlsFallback: Boolean = false
    ): StreamResolution {
        streamCache.getValid(videoId)?.let { cached ->
            if (!isUrlRecentlyFailed(cached.streamUrl) && (allowHlsFallback || !cached.isHls)) {
                return cached
            }
        }

        var lastStatus = 0
        var lastError: Throwable? = null
        var lastPlayability: PlayabilityResult? = null
        var hlsCandidate: StreamResolution? = null
        val triedClients = mutableListOf<String>()
        val hasAccountSession = InnerTubeAuthHelper.hasValidSessionCookie(sessionCookie)
        val visitorData = ensureVisitorData()

        // 1. Prioritize InnerTube clients without mid-stream PoToken throttling; demote recently failing ones
        val rawClientOrder = buildList {
            if (hasAccountSession) {
                add(Triple(InnerTubeClients.ANDROID_MUSIC, InnerTubeClients.BASE_URL, "ANDROID_MUSIC"))
            }
            add(Triple(InnerTubeClients.ANDROID_VR_161, InnerTubeClients.YOUTUBE_API_URL, "ANDROID_VR_161"))
            add(Triple(InnerTubeClients.ANDROID_VR, InnerTubeClients.YOUTUBE_API_URL, "ANDROID_VR"))
            add(Triple(InnerTubeClients.TVHTML5_EMBED, InnerTubeClients.YOUTUBE_API_URL, "TVHTML5_EMBED"))
            add(Triple(InnerTubeClients.TVHTML5_STANDARD, InnerTubeClients.YOUTUBE_API_URL, "TVHTML5"))
            add(Triple(InnerTubeClients.WEB_EMBEDDED, InnerTubeClients.YOUTUBE_API_URL, "WEB_EMBEDDED"))
            add(Triple(InnerTubeClients.ANDROID_MOBILE, InnerTubeClients.YOUTUBE_API_URL, "ANDROID_MOBILE"))
            add(Triple(InnerTubeClients.IOS, InnerTubeClients.YOUTUBE_API_URL, "IOS"))
            add(Triple(InnerTubeClients.ANDROID_VR_LEGACY, InnerTubeClients.YOUTUBE_API_URL, "ANDROID_VR_LEGACY"))
            add(Triple(InnerTubeClients.ANDROID_TESTSUITE, InnerTubeClients.YOUTUBE_API_URL, "ANDROID_TESTSUITE"))
            if (!hasAccountSession) {
                add(Triple(InnerTubeClients.ANDROID_MUSIC, InnerTubeClients.BASE_URL, "ANDROID_MUSIC"))
            }
        }
        val (healthyClients, penalizedClients) = rawClientOrder.partition { !isClientPenalized(it.third) }
        val orderedClients = healthyClients + penalizedClients

        for ((spec, baseUrl, clientTag) in orderedClients) {
            triedClients.add(clientTag)
            try {
                val payload = JSONObject().apply {
                    put("context", InnerTubeClients.buildContextJson(spec, visitorData = visitorData))
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }
                val raw = executePost(
                    url = "${baseUrl}player?prettyPrint=false",
                    spec = spec,
                    body = payload,
                    allowCookieAuth = (spec == InnerTubeClients.ANDROID_MUSIC || spec == InnerTubeClients.WEB_REMIX),
                    visitorIdOverride = visitorData
                )
                val playability = InnerTubeJsonParser.parsePlayabilityStatus(raw)
                if (playability != null && !playability.isPlayable) {
                    lastPlayability = playability
                }
                val candidates = InnerTubeJsonParser.parsePlayerCandidates(
                    rawJson = raw,
                    videoId = videoId,
                    preferOpus = preferOpus,
                    userAgent = spec.userAgent,
                    clientLabel = clientTag
                )

                var allCandidates403 = candidates.isNotEmpty()
                for (candidate in candidates) {
                    if (isUrlRecentlyFailed(candidate.streamUrl)) continue
                    if (candidate.isHls) {
                        if (hlsCandidate == null && probeHlsPlaylistPlayable(candidate.streamUrl, candidate.userAgent)) {
                            hlsCandidate = candidate
                            allCandidates403 = false
                        }
                        continue
                    }
                    val probeCode = probeStreamDualOffset(candidate.streamUrl, candidate.userAgent)
                    if (probeCode in 200..299) {
                        recordClientSuccess(clientTag)
                        streamCache.put(candidate)
                        return candidate
                    } else {
                        lastStatus = probeCode
                        if (probeCode != 403 && probeCode != 410) {
                            allCandidates403 = false
                        }
                    }
                }
                if (allCandidates403 || candidates.isEmpty()) {
                    recordClientFailure(clientTag)
                }
            } catch (t: Throwable) {
                lastError = t
                recordClientFailure(clientTag)
            }
        }

        // 2. Fallback: NewPipeExtractor (solves signatureCipher & JS n-parameter challenges)
        triedClients.add("NEWPIPE")
        val newPipeCandidates = NewPipeStreamExtractor.extractCandidates(
            okHttpClient = okHttpClient,
            videoId = videoId,
            preferOpus = preferOpus,
            sessionCookieProvider = { sessionCookie }
        )
        for (candidate in newPipeCandidates) {
            if (isUrlRecentlyFailed(candidate.streamUrl)) continue
            if (candidate.isHls) {
                if (hlsCandidate == null && probeHlsPlaylistPlayable(candidate.streamUrl, candidate.userAgent)) {
                    hlsCandidate = candidate
                }
                continue
            }
            val probeCode = probeStreamDualOffset(candidate.streamUrl, candidate.userAgent)
            if (probeCode in 200..299) {
                streamCache.put(candidate)
                return candidate
            } else {
                lastStatus = probeCode
            }
        }

        if (allowHlsFallback && hlsCandidate != null) {
            streamCache.put(hlsCandidate)
            return hlsCandidate
        }

        val reasonText = lastPlayability?.reason
        val message = when {
            lastStatus == 403 -> "Stream blocked by server (HTTP 403) across ${triedClients.size} clients"
            lastStatus > 0 -> "Stream probe failed (HTTP $lastStatus)"
            !reasonText.isNullOrBlank() -> reasonText
            else -> "Unable to extract playable audio stream for $videoId"
        }
        throw StreamExtractionException(
            videoId = videoId,
            httpStatus = lastStatus,
            playability = lastPlayability,
            triedClients = triedClients.toList(),
            message = message,
            cause = lastError
        )
    }

    private fun ensureVisitorData(): String? {
        sharedVisitorData?.let { return it }
        webRemixVisitorData?.let {
            sharedVisitorData = it
            return it
        }
        return runCatching {
            val payload = JSONObject().apply {
                put("context", InnerTubeClients.buildContextJson(InnerTubeClients.WEB_REMIX, visitorData = null))
            }
            val req = Request.Builder()
                .url("${InnerTubeClients.YOUTUBE_API_URL}visitor_id?prettyPrint=false")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", InnerTubeClients.WEB_REMIX.userAgent)
                .header("X-YouTube-Client-Name", InnerTubeClients.WEB_REMIX.clientIdHeader)
                .header("X-YouTube-Client-Version", InnerTubeClients.WEB_REMIX.clientVersion)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val extracted = InnerTubeJsonParser.extractVisitorData(body)
                if (!extracted.isNullOrBlank()) {
                    sharedVisitorData = extracted
                }
                extracted
            }
        }.getOrNull()
    }

    /**
     * Probes both the start of the stream AND an interior byte offset (>512 KB) to catch
     * PoToken-throttled URLs that return HTTP 206 for the first chunk then HTTP 403 mid-song.
     */
    private fun probeStreamDualOffset(url: String, userAgent: String): Int {
        val headCode = probeStreamStatusCode(url, userAgent, "bytes=0-1023")
        if (headCode !in 200..299) return headCode

        val clen = Regex("""[?&]clen=(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        if (clen > 650_000L) {
            val midStart = (clen / 2L).coerceIn(524_288L, clen - 2048L)
            val midCode = probeStreamStatusCode(url, userAgent, "bytes=$midStart-${midStart + 1023}")
            if (midCode !in 200..299) return midCode
        }
        return headCode
    }

    private fun probeStreamStatusCode(
        url: String,
        userAgent: String,
        rangeHeader: String = "bytes=0-1023"
    ): Int {
        return runCatching {
            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Range", rangeHeader)
            if (InnerTubeClients.isWebOriginStreamUrl(url)) {
                reqBuilder
                    .header("Origin", "https://www.youtube.com")
                    .header("Referer", "https://www.youtube.com/")
            }
            val activeCookie = sessionCookie
            if (!activeCookie.isNullOrBlank() && shouldAttachCookieForStream(url)) {
                reqBuilder.header("Cookie", activeCookie)
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                response.code
            }
        }.getOrDefault(0)
    }

    fun shouldAttachCookieForStream(url: String): Boolean {
        if (!url.contains("googlevideo.com")) return false
        val tag = InnerTubeClients.extractClientTag(url)?.uppercase() ?: return false
        return tag == "WEB" || tag == "WEB_REMIX" || tag == "MWEB" || tag == "ANDROID_MUSIC"
    }

    private fun probeHlsPlaylistPlayable(masterUrl: String, userAgent: String): Boolean {
        return runCatching {
            val masterReq = Request.Builder()
                .url(masterUrl)
                .get()
                .header("User-Agent", userAgent)
                .build()
            val (masterFinalHttpUrl, masterBody) = okHttpClient.newCall(masterReq).execute().use { resp ->
                if (!resp.isSuccessful) return false
                resp.request.url to (resp.body?.string().orEmpty())
            }
            val subLine = masterBody.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?: return false
            val subHttpUrl = masterFinalHttpUrl.resolve(subLine) ?: return false

            val subReq = Request.Builder()
                .url(subHttpUrl)
                .get()
                .header("User-Agent", userAgent)
                .build()
            val (subFinalHttpUrl, subBody) = okHttpClient.newCall(subReq).execute().use { resp ->
                if (!resp.isSuccessful) return false
                resp.request.url to (resp.body?.string().orEmpty())
            }

            // Probe actual first media segment or init map inside child playlist
            val mapUri = Regex("""#EXT-X-MAP:URI="([^"]+)"""")
                .find(subBody)
                ?.groupValues
                ?.getOrNull(1)
            val firstSegmentLine = mapUri ?: subBody.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?: return false
            val segmentUrl = subFinalHttpUrl.resolve(firstSegmentLine)?.toString() ?: return false
            val segCode = probeStreamStatusCode(segmentUrl, userAgent)
            segCode in 200..299
        }.getOrDefault(false)
    }

    private fun executePost(
        url: String,
        spec: InnerTubeClients.ClientSpec,
        body: JSONObject,
        cookieOverride: String? = null,
        allowCookieAuth: Boolean = true,
        visitorIdOverride: String? = null
    ): String {
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", spec.userAgent)
            .header("X-YouTube-Client-Name", spec.clientIdHeader)
            .header("X-YouTube-Client-Version", spec.clientVersion)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")

        val activeCookie = cookieOverride ?: sessionCookie
        val canAttachCookie = allowCookieAuth &&
            InnerTubeAuthHelper.hasValidSessionCookie(activeCookie) &&
            (spec == InnerTubeClients.WEB_REMIX || spec == InnerTubeClients.ANDROID_MUSIC)

        if (spec == InnerTubeClients.WEB_REMIX) {
            builder
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
            if (!canAttachCookie) {
                (visitorIdOverride ?: webRemixVisitorData)?.let { vd ->
                    builder.header("X-Goog-Visitor-Id", vd)
                }
            }
        } else if (spec.requiresWebOrigin) {
            builder
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
            visitorIdOverride?.let { vd -> builder.header("X-Goog-Visitor-Id", vd) }
        } else {
            builder.header("X-Goog-Api-Format-Version", "2")
            visitorIdOverride?.let { vd -> builder.header("X-Goog-Visitor-Id", vd) }
        }

        if (canAttachCookie) {
            val origin = if (url.startsWith(InnerTubeClients.BASE_URL)) {
                "https://music.youtube.com"
            } else {
                "https://www.youtube.com"
            }
            InnerTubeAuthHelper.buildAuthHeaders(activeCookie, origin).forEach { (k, v) ->
                builder.header(k, v)
            }
        }

        okHttpClient.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("InnerTube HTTP ${response.code} for $url")
            }
            if (spec == InnerTubeClients.WEB_REMIX && !canAttachCookie) {
                InnerTubeJsonParser.extractVisitorData(responseBody)?.let { vd ->
                    if (webRemixVisitorData.isNullOrBlank()) {
                        webRemixVisitorData = vd
                    }
                    if (sharedVisitorData.isNullOrBlank()) {
                        sharedVisitorData = vd
                    }
                }
            }
            return responseBody
        }
    }
}
