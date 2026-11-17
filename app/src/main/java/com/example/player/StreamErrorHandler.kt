package com.example.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.example.data.innertube.InnerTubeClient
import com.example.data.innertube.StreamExtractionException
import com.example.domain.model.StreamErrorCategory
import com.example.domain.model.StreamErrorInfo
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

sealed interface RecoveryDecision {
    data class RetryStream(
        val preferOpus: Boolean,
        val allowHlsFallback: Boolean,
        val purgeVideoCache: Boolean,
        val statusHint: String
    ) : RecoveryDecision

    data class AutoSkipNext(val info: StreamErrorInfo) : RecoveryDecision

    data class SurfaceError(val info: StreamErrorInfo) : RecoveryDecision
}

/**
 * Dedicated in-app Stream Error Handling & Recovery Layer.
 *
 * Classifies Media3 / OkHttp / InnerTube / NewPipe failures, coordinates automated
 * multi-stage recovery (codec flip -> HLS adaptive fallback -> cache purge), and translates
 * remaining failures into actionable user recovery options.
 */
class StreamErrorHandler(
    private val innerTubeClient: InnerTubeClient
) {
    private val trackAttempts = ConcurrentHashMap<String, Int>()
    private val lastErrorsByTrack = ConcurrentHashMap<String, StreamErrorInfo>()

    fun resetAttempts(trackId: String?) {
        if (!trackId.isNullOrBlank()) {
            trackAttempts.remove(trackId)
            lastErrorsByTrack.remove(trackId)
        }
    }

    fun resetAll() {
        trackAttempts.clear()
        lastErrorsByTrack.clear()
    }

    fun evaluateAndPlanRecovery(
        track: Track?,
        error: Throwable,
        defaultPreferOpus: Boolean,
        isYoutubeSignedIn: Boolean,
        autoSkipBrokenTracks: Boolean,
        hasNextTrack: Boolean
    ): RecoveryDecision {
        val trackKey = track?.id ?: "unknown"
        val attempt = trackAttempts.merge(trackKey, 1) { old, one -> old + one } ?: 1
        val info = classifyError(
            track = track,
            error = error,
            attemptCount = attempt,
            isYoutubeSignedIn = isYoutubeSignedIn
        )
        lastErrorsByTrack[trackKey] = info

        if (track?.source == TrackSource.YOUTUBE && !track.videoId.isNullOrBlank()) {
            val videoId = track.videoId
            innerTubeClient.getCachedStream(videoId)?.let { cached ->
                innerTubeClient.markStreamUrlFailed(videoId, cached.streamUrl)
            }

            when {
                // Step 1: Flip codec preference (Opus <-> AAC progressive) on another InnerTube client
                attempt == 1 && info.category != StreamErrorCategory.LOCAL_FILE_MISSING -> {
                    return RecoveryDecision.RetryStream(
                        preferOpus = !defaultPreferOpus,
                        allowHlsFallback = false,
                        purgeVideoCache = false,
                        statusHint = "Switching stream codec & client…"
                    )
                }

                // Step 2: Switch to adaptive HLS manifest fallback (Media3 HlsMediaSource)
                attempt == 2 && info.category != StreamErrorCategory.LOCAL_FILE_MISSING -> {
                    return RecoveryDecision.RetryStream(
                        preferOpus = defaultPreferOpus,
                        allowHlsFallback = true,
                        purgeVideoCache = false,
                        statusHint = "Trying adaptive HLS fallback…"
                    )
                }

                // Step 3: Clear track blacklist/visitor cache and do one clean multi-client pass
                attempt == 3 && info.category != StreamErrorCategory.NETWORK_UNAVAILABLE -> {
                    innerTubeClient.clearFailedUrlsForVideo(videoId)
                    return RecoveryDecision.RetryStream(
                        preferOpus = false,
                        allowHlsFallback = true,
                        purgeVideoCache = true,
                        statusHint = "Refreshing stream tokens…"
                    )
                }
            }
        }

        return if (autoSkipBrokenTracks && hasNextTrack) {
            RecoveryDecision.AutoSkipNext(info)
        } else {
            RecoveryDecision.SurfaceError(info)
        }
    }

    fun classifyError(
        track: Track?,
        error: Throwable,
        attemptCount: Int = 1,
        isYoutubeSignedIn: Boolean = false
    ): StreamErrorInfo {
        val videoId = track?.videoId
        val extraction = findCause<StreamExtractionException>(error)
        val httpError = findCause<HttpDataSource.InvalidResponseCodeException>(error)
        val playbackEx = error as? PlaybackException

        val httpCode = httpError?.responseCode
            ?: extraction?.httpStatus?.takeIf { it > 0 }
        val playability = extraction?.playability
        val failedClient = videoId?.let { innerTubeClient.getCachedStream(it)?.clientLabel }
            ?: extraction?.triedClients?.lastOrNull()

        if (track?.source == TrackSource.LOCAL) {
            return StreamErrorInfo(
                category = StreamErrorCategory.LOCAL_FILE_MISSING,
                title = "Local audio file unavailable",
                detail = "File may have been moved or permission was revoked. Rescan or re-import from Library.",
                httpStatus = null,
                videoId = null,
                attemptCount = attemptCount,
                canSwitchFormat = false,
                suggestsSignIn = false
            )
        }

        if (playability != null && !playability.isPlayable && playability.requiresSignIn) {
            return StreamErrorInfo(
                category = StreamErrorCategory.RESTRICTED_OR_LOGIN,
                title = "Sign-in or age check required",
                detail = playability.reason
                    ?: "YouTube Music requires an authenticated session for this track.",
                httpStatus = httpCode,
                videoId = videoId,
                failedClient = failedClient,
                attemptCount = attemptCount,
                canSwitchFormat = true,
                suggestsSignIn = !isYoutubeSignedIn
            )
        }

        val networkCause = findCause<UnknownHostException>(error)
            ?: findCause<SocketTimeoutException>(error)
            ?: findCause<HttpDataSource.HttpDataSourceException>(error)?.takeIf {
                httpCode == null && it !is HttpDataSource.InvalidResponseCodeException
            }
        if (networkCause != null && httpCode == null && extraction == null) {
            return StreamErrorInfo(
                category = StreamErrorCategory.NETWORK_UNAVAILABLE,
                title = "Network connection interrupted",
                detail = "Check your internet connection and tap Retry.",
                httpStatus = null,
                videoId = videoId,
                failedClient = failedClient,
                attemptCount = attemptCount,
                canSwitchFormat = true,
                suggestsSignIn = false
            )
        }

        return when {
            httpCode == 403 -> StreamErrorInfo(
                category = StreamErrorCategory.HTTP_403_FORBIDDEN,
                title = "Stream blocked by server (HTTP 403)",
                detail = buildString {
                    val tried = extraction?.triedClients?.size ?: 0
                    if (tried > 0) {
                        append("Tested $tried stream clients. ")
                    }
                    if (!isYoutubeSignedIn) {
                        append("Try AAC/HLS fallback or sign in to YouTube Music.")
                    } else {
                        append("Tap Switch Format to try AAC/HLS or skip to next track.")
                    }
                },
                httpStatus = 403,
                videoId = videoId,
                failedClient = failedClient,
                attemptCount = attemptCount,
                canSwitchFormat = true,
                suggestsSignIn = !isYoutubeSignedIn
            )

            httpCode == 410 || httpCode == 404 -> StreamErrorInfo(
                category = StreamErrorCategory.HTTP_410_EXPIRED,
                title = "Stream token expired (HTTP $httpCode)",
                detail = "Cached CDN URL expired. Tap Retry to fetch a fresh audio stream.",
                httpStatus = httpCode,
                videoId = videoId,
                failedClient = failedClient,
                attemptCount = attemptCount,
                canSwitchFormat = true,
                suggestsSignIn = false
            )

            playbackEx?.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                playbackEx?.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                StreamErrorInfo(
                    category = StreamErrorCategory.FORMAT_OR_CODEC,
                    title = "Unsupported audio container or codec",
                    detail = "Tap Switch Format to fall back to AAC or HLS audio.",
                    httpStatus = httpCode,
                    videoId = videoId,
                    failedClient = failedClient,
                    attemptCount = attemptCount,
                    canSwitchFormat = true,
                    suggestsSignIn = false
                )

            else -> StreamErrorInfo(
                category = StreamErrorCategory.UNKNOWN,
                title = httpCode?.let { "Stream error (HTTP $it)" }
                    ?: playability?.reason
                    ?: (extraction?.message ?: "Stream playback interrupted"),
                detail = "Recovery attempts exhausted ($attemptCount). Tap Retry, Switch Format, or Skip.",
                httpStatus = httpCode,
                videoId = videoId,
                failedClient = failedClient,
                attemptCount = attemptCount,
                canSwitchFormat = true,
                suggestsSignIn = !isYoutubeSignedIn && httpCode == 403
            )
        }
    }

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var cur: Throwable? = error
        var depth = 0
        while (cur != null && depth < 12) {
            if (cur is T) return cur
            cur = cur.cause
            depth++
        }
        return null
    }
}
