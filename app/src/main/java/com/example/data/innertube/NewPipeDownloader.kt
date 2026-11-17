package com.example.data.innertube

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class NewPipeOkHttpDownloader(
    private val client: OkHttpClient,
    private val sessionCookieProvider: () -> String? = { null }
) : Downloader() {

    override fun execute(request: NewPipeRequest): Response {
        val headersBuilder = Headers.Builder()
        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                headersBuilder.add(key, value)
            }
        }
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            headersBuilder.add("User-Agent", InnerTubeClients.WEB_REMIX.userAgent)
        }

        val urlStr = request.url()
        val requestUa = request.headers()["User-Agent"]?.firstOrNull().orEmpty()
        val isWebClientRequest = requestUa.contains("Mozilla", ignoreCase = true) ||
            request.headers()["X-YouTube-Client-Name"]?.firstOrNull() in setOf("1", "2", "67")

        val cookie = sessionCookieProvider()
        if (InnerTubeAuthHelper.hasValidSessionCookie(cookie) &&
            isWebClientRequest &&
            (urlStr.contains("youtube.com") || urlStr.contains("googlevideo.com"))
        ) {
            val origin = if (urlStr.contains("music.youtube.com")) {
                "https://music.youtube.com"
            } else {
                "https://www.youtube.com"
            }
            if (urlStr.contains("youtube.com")) {
                InnerTubeAuthHelper.buildAuthHeaders(cookie, origin).forEach { (k, v) ->
                    headersBuilder.set(k, v)
                }
            } else if (!cookie.isNullOrBlank()) {
                headersBuilder.set("Cookie", cookie)
            }
        }

        // Track actual User-Agent sent by NewPipe per client tag so ExoPlayer reuses exact matching UA
        if (urlStr.contains("/youtubei/v1/player") && requestUa.isNotBlank()) {
            val clientHeader = request.headers()["X-YouTube-Client-Name"]?.firstOrNull()
            NewPipeStreamExtractor.recordClientUserAgent(clientHeader, requestUa)
        }

        val body = request.dataToSend()?.toRequestBody(null)
        val okRequest = Request.Builder()
            .url(urlStr)
            .headers(headersBuilder.build())
            .method(request.httpMethod(), body)
            .build()

        client.newCall(okRequest).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("Rate limited by YouTube (429)", request.url())
            }
            val bodyString = resp.body?.string().orEmpty()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                bodyString,
                resp.request.url.toString()
            )
        }
    }
}

object NewPipeStreamExtractor {

    private val initialized = AtomicBoolean(false)
    private val capturedClientUas = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var currentCookieProvider: () -> String? = { null }

    fun recordClientUserAgent(clientHeaderId: String?, userAgent: String) {
        val tag = when (clientHeaderId) {
            "1" -> "WEB"
            "2" -> "MWEB"
            "3" -> "ANDROID"
            "5" -> "IOS"
            "7" -> "TVHTML5"
            "28" -> "ANDROID_VR"
            "67" -> "WEB_REMIX"
            "85" -> "TVHTML5_SIMPLY_EMBEDDED_PLAYER"
            else -> null
        }
        if (tag != null && userAgent.isNotBlank()) {
            capturedClientUas[tag] = userAgent
        }
    }

    fun resolveUserAgentForUrl(url: String): String {
        val tag = InnerTubeClients.extractClientTag(url)?.uppercase(Locale.US)
        if (tag != null) {
            capturedClientUas[tag]?.let { return it }
            InnerTubeClients.userAgentForClientTag(tag)?.let { return it }
        }
        return InnerTubeClients.WEB_REMIX.userAgent
    }

    fun ensureInitialized(
        okHttpClient: OkHttpClient,
        sessionCookieProvider: () -> String? = { null }
    ) {
        currentCookieProvider = sessionCookieProvider
        if (initialized.compareAndSet(false, true)) {
            val locale = Locale.getDefault()
            val lang = locale.language.takeIf { it.length == 2 } ?: "en"
            val country = locale.country.takeIf { it.length == 2 } ?: "US"
            NewPipe.init(
                NewPipeOkHttpDownloader(okHttpClient) { currentCookieProvider() },
                Localization(lang, country)
            )
        }
    }

    fun extractCandidates(
        okHttpClient: OkHttpClient,
        videoId: String,
        preferOpus: Boolean,
        sessionCookieProvider: () -> String? = { null }
    ): List<StreamResolution> {
        return runCatching {
            ensureInitialized(okHttpClient, sessionCookieProvider)
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl)
            extractor.fetchPage()

            val progressiveCandidates = extractor.audioStreams
                .orEmpty()
                .filter { stream ->
                    stream.isUrl &&
                        !stream.content.isNullOrBlank() &&
                        stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
                }
                .mapNotNull { stream ->
                    val url = stream.content ?: return@mapNotNull null
                    val mime = stream.format?.mimeType ?: "audio/mp4"
                    val codec = when {
                        mime.contains("opus", ignoreCase = true) ||
                            mime.contains("webm", ignoreCase = true) -> "OPUS"
                        else -> "AAC"
                    }
                    val bitrate = stream.averageBitrate.takeIf { it > 0 } ?: 128
                    val matchedUa = resolveUserAgentForUrl(url)
                    val tag = InnerTubeClients.extractClientTag(url) ?: "NEWPIPE"
                    StreamResolution(
                        videoId = videoId,
                        streamUrl = url,
                        mimeType = mime,
                        codecLabel = codec,
                        bitrateKbps = bitrate,
                        expiresAtMs = extractExpireMs(url),
                        userAgent = matchedUa,
                        isHls = false,
                        clientLabel = "NP_$tag"
                    )
                }
                .sortedWith(
                    compareByDescending<StreamResolution> {
                        if (preferOpus) it.codecLabel == "OPUS" else it.codecLabel == "AAC"
                    }.thenByDescending { it.bitrateKbps }
                )
                .toMutableList()

            val hlsUrl = runCatching { extractor.hlsUrl }.getOrNull()?.takeIf { it.isNotBlank() }
            if (hlsUrl != null) {
                val hlsUa = resolveUserAgentForUrl(hlsUrl)
                val tag = InnerTubeClients.extractClientTag(hlsUrl) ?: "IOS"
                progressiveCandidates.add(
                    StreamResolution(
                        videoId = videoId,
                        streamUrl = hlsUrl,
                        mimeType = "application/x-mpegURL",
                        codecLabel = "HLS",
                        bitrateKbps = 128,
                        expiresAtMs = extractExpireMs(hlsUrl),
                        userAgent = hlsUa,
                        isHls = true,
                        clientLabel = "NP_${tag}_HLS"
                    )
                )
            }

            progressiveCandidates.toList()
        }.getOrDefault(emptyList())
    }

    private fun extractExpireMs(url: String): Long {
        val match = Regex("""[?&]expire=(\d+)""").find(url)
        val epochSec = match?.groupValues?.getOrNull(1)?.toLongOrNull()
        return if (epochSec != null && epochSec > 0L) {
            epochSec * 1000L
        } else {
            System.currentTimeMillis() + 5 * 3600_000L
        }
    }
}
