package com.example.player

import androidx.media3.common.Player
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CrossfadeVolumes(
    val outgoing: Float,
    val incoming: Float
)

/**
 * Pure transition and volume-curve rules shared between Mixa's ExoPlayer dual-deck engine
 * and unit tests:
 * - `0s` uses native ExoPlayer single-timeline gapless playback (`pauseAtEndOfMediaItems = false`).
 * - `1..12s` engages `setPauseAtEndOfMediaItems(true)` on the outgoing deck so tracks hold cleanly
 *   at their end boundary while the standby deck fades in via equal-power cosine/sine curves.
 */
object CrossfadeTransitionPolicy {

    const val MIN_CROSSFADE_SECONDS = 0
    const val MAX_CROSSFADE_SECONDS = 12
    const val PRE_CUE_LEAD_MS = 4_500L
    const val MIN_TRACK_DURATION_PADDING_MS = 1_500L

    fun clampCrossfadeSeconds(seconds: Int): Int =
        seconds.coerceIn(MIN_CROSSFADE_SECONDS, MAX_CROSSFADE_SECONDS)

    fun shouldPauseAtEndOfMediaItems(crossfadeSeconds: Int, repeatMode: Int): Boolean {
        return clampCrossfadeSeconds(crossfadeSeconds) > 0 && repeatMode != Player.REPEAT_MODE_ONE
    }

    fun shouldPreCueNextDeck(
        remainingMs: Long,
        durationMs: Long,
        crossfadeMs: Long,
        hasNextItem: Boolean,
        isLiveStream: Boolean
    ): Boolean {
        if (crossfadeMs <= 0L || !hasNextItem || isLiveStream) return false
        if (durationMs <= crossfadeMs + MIN_TRACK_DURATION_PADDING_MS) return false
        return remainingMs in 1L..(crossfadeMs + PRE_CUE_LEAD_MS)
    }

    fun shouldStartCrossfade(
        remainingMs: Long,
        durationMs: Long,
        crossfadeMs: Long,
        hasNextItem: Boolean,
        isLiveStream: Boolean
    ): Boolean {
        if (crossfadeMs <= 0L || !hasNextItem || isLiveStream) return false
        if (durationMs <= crossfadeMs + MIN_TRACK_DURATION_PADDING_MS) return false
        return remainingMs in 1L..crossfadeMs
    }

    /**
     * Equal-power crossfade curve: maintains perceived acoustic loudness across the transition.
     */
    fun computeEqualPowerVolumes(progress: Float): CrossfadeVolumes {
        val t = progress.coerceIn(0f, 1f).toDouble()
        val angle = t * (PI / 2.0)
        return CrossfadeVolumes(
            outgoing = cos(angle).toFloat().coerceIn(0f, 1f),
            incoming = sin(angle).toFloat().coerceIn(0f, 1f)
        )
    }
}
