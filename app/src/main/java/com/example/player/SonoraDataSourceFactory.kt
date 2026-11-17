package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.data.innertube.InnerTubeClient
import com.example.data.innertube.InnerTubeClients
import com.example.data.innertube.StreamResolution
import com.example.domain.model.ResolvedExtensionAudio
import com.example.domain.model.Track
import okhttp3.Interceptor
import kotlin.math.min

@OptIn(UnstableApi::class)
class SonoraDataSourceFactory(
    context: Context,
    private val innerTubeClient: InnerTubeClient,
    private val preferOpusProvider: () -> Boolean,
    private val onStreamResolved: (StreamResolution) -> Unit,
    private val extensionResolver: ((String) -> ResolvedExtensionAudio)? = null,
    private val onExtensionStreamResolved: ((ResolvedExtensionAudio) -> Unit)? = null
) : DataSource.Factory {

    @Volatile
    private var lastResolvedUserAgent: String? = null

    private val clientAwareOkHttp = innerTubeClient.okHttpClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            val urlStr = req.url.toString()
            val currentUa = req.header("User-Agent")
            val needsUa = currentUa.isNullOrBlank() || currentUa.startsWith("okhttp/")
            val activeCookie = innerTubeClient.sessionCookie
            val attachCookie = !activeCookie.isNullOrBlank() &&
                innerTubeClient.shouldAttachCookieForStream(urlStr) &&
                req.header("Cookie").isNullOrBlank()
            val addWebOrigin = urlStr.contains("googlevideo.com") &&
                InnerTubeClients.isWebOriginStreamUrl(urlStr) &&
                req.header("Origin").isNullOrBlank()

            if (urlStr.contains("googlevideo.com") && (needsUa || attachCookie || addWebOrigin)) {
                val reqBuilder = req.newBuilder()
                if (needsUa) {
                    val targetUa = InnerTubeClients.userAgentForStreamUrl(urlStr)
                        ?: innerTubeClient.getCachedStreamByUrl(urlStr)?.userAgent
                        ?: lastResolvedUserAgent
                        ?: InnerTubeClients.ANDROID_VR_161.userAgent
                    reqBuilder.header("User-Agent", targetUa)
                }
                if (attachCookie) {
                    reqBuilder.header("Cookie", activeCookie!!)
                }
                if (addWebOrigin) {
                    reqBuilder.header("Origin", "https://www.youtube.com")
                    if (req.header("Referer").isNullOrBlank()) {
                        reqBuilder.header("Referer", "https://www.youtube.com/")
                    }
                }
                chain.proceed(reqBuilder.build())
            } else if (!urlStr.contains("googlevideo.com") && req.header("Icy-MetaData").isNullOrBlank()) {
                val reqBuilder = req.newBuilder()
                    .header("Icy-MetaData", "1")
                chain.proceed(reqBuilder.build())
            } else {
                chain.proceed(req)
            }
        })
        .build()

    private val okHttpFactory = OkHttpDataSource.Factory(clientAwareOkHttp)

    private val defaultUpstreamFactory = DefaultDataSource.Factory(context, okHttpFactory)

    private val resolvingFactory = ResolvingDataSource.Factory(
        DataSource.Factory {
            ChunkedYoutubeDataSource(
                upstream = defaultUpstreamFactory.createDataSource(),
                innerTubeClient = innerTubeClient,
                preferOpusProvider = preferOpusProvider,
                onStreamResolved = onStreamResolved
            )
        },
        ResolvingDataSource.Resolver { dataSpec: DataSpec ->
            val rawUri = dataSpec.uri.toString()
            val isStandardYt = rawUri.startsWith(Track.YOUTUBE_VIRTUAL_SCHEME)
            val isHlsYt = rawUri.startsWith(Track.YOUTUBE_HLS_SCHEME)

            val isExtensionUri = rawUri.startsWith(Track.EXTENSION_VIRTUAL_SCHEME)
            if (isStandardYt || isHlsYt) {
                val rawSuffix = if (isStandardYt) {
                    rawUri.removePrefix(Track.YOUTUBE_VIRTUAL_SCHEME)
                } else {
                    rawUri.removePrefix(Track.YOUTUBE_HLS_SCHEME)
                }
                val videoId = rawSuffix.substringBefore("?").trim()
                val codecOverride = runCatching { dataSpec.uri.getQueryParameter("codec") }.getOrNull()
                val preferOpus = when (codecOverride?.lowercase()) {
                    "aac" -> false
                    "opus" -> true
                    else -> preferOpusProvider()
                }
                val resolved = innerTubeClient.resolveStreamSync(
                    videoId = videoId,
                    preferOpus = preferOpus,
                    allowHlsFallback = isHlsYt
                )
                lastResolvedUserAgent = resolved.userAgent
                onStreamResolved(resolved)
                val updatedHeaders = HashMap(dataSpec.httpRequestHeaders).apply {
                    put("User-Agent", resolved.userAgent)
                    if (InnerTubeClients.isWebOriginStreamUrl(resolved.streamUrl)) {
                        put("Origin", "https://www.youtube.com")
                        put("Referer", "https://www.youtube.com/")
                    }
                }
                dataSpec.buildUpon()
                    .setUri(Uri.parse(resolved.streamUrl))
                    .setHttpRequestHeaders(updatedHeaders)
                    .setCustomData(StreamSpecMeta(videoId = videoId, preferOpus = preferOpus))
                    .build()
            } else if (isExtensionUri && extensionResolver != null) {
                val resolved = extensionResolver.invoke(rawUri)
                onExtensionStreamResolved?.invoke(resolved)
                val updatedHeaders = HashMap(dataSpec.httpRequestHeaders).apply {
                    resolved.headers.forEach { (k, v) -> put(k, v) }
                }
                val customMeta: Any? = resolved.decryptionSpec?.let { BlowfishStreamSpecMeta(it) }
                dataSpec.buildUpon()
                    .setUri(Uri.parse(resolved.streamUrl))
                    .setHttpRequestHeaders(updatedHeaders)
                    .setCustomData(customMeta)
                    .build()
            } else {
                val matchedUa = InnerTubeClients.userAgentForStreamUrl(rawUri)
                    ?: innerTubeClient.getCachedStreamByUrl(rawUri)?.userAgent
                    ?: lastResolvedUserAgent
                if (rawUri.contains("googlevideo.com") && matchedUa != null) {
                    val updatedHeaders = HashMap(dataSpec.httpRequestHeaders).apply {
                        put("User-Agent", matchedUa)
                        if (InnerTubeClients.isWebOriginStreamUrl(rawUri)) {
                            put("Origin", "https://www.youtube.com")
                            put("Referer", "https://www.youtube.com/")
                        }
                    }
                    dataSpec.buildUpon()
                        .setHttpRequestHeaders(updatedHeaders)
                        .build()
                } else {
                    dataSpec
                }
            }
        }
    )

    override fun createDataSource(): DataSource = resolvingFactory.createDataSource()
}

private data class StreamSpecMeta(
    val videoId: String,
    val preferOpus: Boolean
)

/**
 * Splits progressive `googlevideo.com/videoplayback` fetches into bounded byte-range chunks (512 KB)
 * and transparently fails over to fresh client streams (up to 2 hops) at the exact byte offset if
 * GoogleVideo rejects a chunk mid-playback with HTTP 403/404/410, or reconnects once on transient IO errors.
 */
@OptIn(UnstableApi::class)
private class ChunkedYoutubeDataSource(
    private val upstream: DataSource,
    private val innerTubeClient: InnerTubeClient,
    private val preferOpusProvider: () -> Boolean,
    private val onStreamResolved: (StreamResolution) -> Unit
) : BaseDataSource(/* isNetwork = */ true) {

    private var rootSpec: DataSpec? = null
    private var activeVideoId: String? = null
    private var activePreferOpus: Boolean = true
    private var currentSpecPos: Long = 0L
    private var totalStreamLength: Long = C.LENGTH_UNSET.toLong()
    private var remainingRequested: Long = C.LENGTH_UNSET.toLong()
    private var chunkedMode: Boolean = false
    private var activeNonYtSource: DataSource = upstream
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val urlStr = dataSpec.uri.toString()
        val isProgressiveYoutube = urlStr.contains("googlevideo.com") &&
            urlStr.contains("videoplayback") &&
            !urlStr.contains(".m3u8") &&
            !urlStr.contains("manifest.googlevideo.com")

        if (!isProgressiveYoutube) {
            chunkedMode = false
            val blowfishMeta = dataSpec.customData as? BlowfishStreamSpecMeta
            activeNonYtSource = if (blowfishMeta != null) {
                BlowfishChunkDataSource(upstream = upstream, chunkSpec = blowfishMeta.spec)
            } else {
                upstream
            }
            opened = true
            val len = activeNonYtSource.open(dataSpec)
            transferStarted(dataSpec)
            return len
        }

        activeNonYtSource = upstream
        chunkedMode = true
        rootSpec = dataSpec
        val meta = dataSpec.customData as? StreamSpecMeta
        activeVideoId = meta?.videoId
            ?: (dataSpec.customData as? String)
            ?: innerTubeClient.getCachedStreamByUrl(urlStr)?.videoId
        activePreferOpus = meta?.preferOpus ?: preferOpusProvider()
        currentSpecPos = dataSpec.position.coerceAtLeast(0L)
        totalStreamLength = extractClenFromUrl(urlStr)
        remainingRequested = dataSpec.length

        openNextChunk(failoversRemaining = 2, transientRetryDone = false)
        opened = true
        transferStarted(dataSpec)

        return when {
            remainingRequested != C.LENGTH_UNSET.toLong() -> remainingRequested
            totalStreamLength != C.LENGTH_UNSET.toLong() ->
                (totalStreamLength - currentSpecPos).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunkedMode) {
            val read = activeNonYtSource.read(buffer, offset, length)
            if (read > 0) bytesTransferred(read)
            return read
        }
        if (length == 0) return 0
        if (remainingRequested == 0L) return C.RESULT_END_OF_INPUT
        if (totalStreamLength != C.LENGTH_UNSET.toLong() && currentSpecPos >= totalStreamLength) {
            return C.RESULT_END_OF_INPUT
        }

        while (true) {
            val readLen = if (remainingRequested != C.LENGTH_UNSET.toLong()) {
                min(length.toLong(), remainingRequested).toInt()
            } else {
                length
            }
            val bytesRead = upstream.read(buffer, offset, readLen)
            if (bytesRead != C.RESULT_END_OF_INPUT) {
                currentSpecPos += bytesRead
                if (remainingRequested != C.LENGTH_UNSET.toLong()) {
                    remainingRequested -= bytesRead
                }
                bytesTransferred(bytesRead)
                return bytesRead
            }

            if (totalStreamLength != C.LENGTH_UNSET.toLong() && currentSpecPos >= totalStreamLength) {
                return C.RESULT_END_OF_INPUT
            }
            if (remainingRequested == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            if (!openNextChunk(failoversRemaining = 2, transientRetryDone = false)) {
                return C.RESULT_END_OF_INPUT
            }
        }
    }

    private fun openNextChunk(
        failoversRemaining: Int,
        transientRetryDone: Boolean
    ): Boolean {
        runCatching { upstream.close() }
        val baseSpec = rootSpec ?: return false
        if (totalStreamLength != C.LENGTH_UNSET.toLong() && currentSpecPos >= totalStreamLength) {
            return false
        }

        var desiredLen = CHUNK_SIZE_BYTES
        if (remainingRequested != C.LENGTH_UNSET.toLong()) {
            desiredLen = min(desiredLen, remainingRequested)
        }
        if (totalStreamLength != C.LENGTH_UNSET.toLong()) {
            val avail = (totalStreamLength - currentSpecPos).coerceAtLeast(0L)
            if (avail == 0L) return false
            desiredLen = min(desiredLen, avail)
        }
        if (desiredLen <= 0L) return false

        val chunkSpec = baseSpec.buildUpon()
            .setPosition(currentSpecPos)
            .setLength(desiredLen)
            .build()

        return try {
            upstream.open(chunkSpec)
            if (totalStreamLength == C.LENGTH_UNSET.toLong()) {
                parseContentRangeTotal(upstream.responseHeaders)?.let { total ->
                    totalStreamLength = total
                }
            }
            true
        } catch (httpErr: HttpDataSource.InvalidResponseCodeException) {
            val vid = activeVideoId
            val recoverable = httpErr.responseCode == 403 ||
                httpErr.responseCode == 410 ||
                httpErr.responseCode == 404
            if (failoversRemaining > 0 && recoverable && !vid.isNullOrBlank()) {
                innerTubeClient.markStreamUrlFailed(vid, baseSpec.uri.toString())
                val fresh = innerTubeClient.resolveStreamSync(
                    videoId = vid,
                    preferOpus = activePreferOpus,
                    allowHlsFallback = false
                )
                onStreamResolved(fresh)
                val updatedHeaders = HashMap(baseSpec.httpRequestHeaders).apply {
                    put("User-Agent", fresh.userAgent)
                    if (InnerTubeClients.isWebOriginStreamUrl(fresh.streamUrl)) {
                        put("Origin", "https://www.youtube.com")
                        put("Referer", "https://www.youtube.com/")
                    } else {
                        remove("Origin")
                        remove("Referer")
                    }
                }
                val nextRoot = baseSpec.buildUpon()
                    .setUri(Uri.parse(fresh.streamUrl))
                    .setHttpRequestHeaders(updatedHeaders)
                    .build()
                rootSpec = nextRoot
                val freshTotal = extractClenFromUrl(fresh.streamUrl)
                if (freshTotal != C.LENGTH_UNSET.toLong()) {
                    totalStreamLength = freshTotal
                }
                openNextChunk(
                    failoversRemaining = failoversRemaining - 1,
                    transientRetryDone = false
                )
            } else {
                throw httpErr
            }
        } catch (ioErr: java.io.IOException) {
            if (!transientRetryDone && ioErr !is com.example.data.innertube.StreamExtractionException) {
                openNextChunk(
                    failoversRemaining = failoversRemaining,
                    transientRetryDone = true
                )
            } else {
                throw ioErr
            }
        }
    }

    override fun getUri(): Uri? =
        (if (!chunkedMode) activeNonYtSource.uri else upstream.uri) ?: rootSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (!chunkedMode) activeNonYtSource.responseHeaders else upstream.responseHeaders

    override fun close() {
        rootSpec = null
        activeVideoId = null
        val nonYt = activeNonYtSource
        val wasChunked = chunkedMode
        activeNonYtSource = upstream
        chunkedMode = false
        if (opened) {
            opened = false
            runCatching { if (!wasChunked) nonYt.close() else upstream.close() }
            transferEnded()
        }
    }

    private fun extractClenFromUrl(url: String): Long {
        val match = Regex("""[?&]clen=(\d+)""").find(url)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
    }

    private fun parseContentRangeTotal(headers: Map<String, List<String>>): Long? {
        val raw = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?: return null
        val slashIdx = raw.lastIndexOf('/')
        if (slashIdx < 0 || slashIdx == raw.lastIndex) return null
        return raw.substring(slashIdx + 1).trim().toLongOrNull()
    }

    private companion object {
        const val CHUNK_SIZE_BYTES = 512L * 1024L
    }
}
