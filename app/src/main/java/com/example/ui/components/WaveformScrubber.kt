package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

const val WAVEFORM_BAR_COUNT = 45
val WaveformTealPlayed = Color(0xFF20D6B5)
val WaveformDarkMutedUnplayed = Color(0xFF2A2D30)

/**
 * Generates deterministic, musical-contour amplitudes in [0.18f, 1.0f] for the 45-bar scrubber
 * seeded from the track identifier so each song exhibits a unique silhouette.
 */
fun generateWaveformBars(
    seedKey: String,
    barCount: Int = WAVEFORM_BAR_COUNT
): List<Float> {
    if (barCount <= 0) return emptyList()
    var state = seedKey.hashCode().toLong() * 6364136223846793005L + 1442695040888963407L
    val phaseA = ((state ushr 8) and 0xFFL) / 255f * (2f * PI.toFloat())
    val phaseB = ((state ushr 16) and 0xFFL) / 255f * (2f * PI.toFloat())

    return List(barCount) { index ->
        state = state * 6364136223846793005L + 1442695040888963407L
        val prng = ((state ushr 33) and 0xFFFFL).toFloat() / 65535f
        val t = index.toFloat() / (barCount - 1).coerceAtLeast(1)

        // Phrase envelope tapers gently at intro/outro and peaks through verses/chorus
        val edgeEnvelope = sin(t * PI).toFloat().coerceIn(0.32f, 1f)
        val macroWave = 0.5f + 0.5f * sin(t * 7.5f + phaseA).toFloat()
        val microWave = 0.5f + 0.5f * sin(t * 19.0f + phaseB).toFloat()
        val transientSpike = if ((index + abs(seedKey.hashCode())) % 5 == 0) 0.16f else 0f

        val raw = 0.20f +
            (edgeEnvelope * 0.42f * macroWave) +
            (0.20f * microWave) +
            (0.18f * prng) +
            transientSpike
        raw.coerceIn(0.18f, 1.0f)
    }
}

fun waveformXToProgress(xPx: Float, widthPx: Float): Float {
    if (widthPx <= 0f) return 0f
    return (xPx / widthPx).coerceIn(0f, 1f)
}

/**
 * Interactive 45-bar vertical audio waveform scrubber with played (#20D6B5) and muted (#2A2D30)
 * contrast, drag & tap seeking with bar-tick haptics, and aligned start/end timestamps underneath.
 */
@Composable
fun WaveformScrubber(
    trackId: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLiveStream: Boolean = false
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val mutedLabelColor = if (tokens.isDark) Color(0xFFB3B6BC) else MaterialTheme.colorScheme.onSurfaceVariant
    val unplayedBarColor = if (tokens.isDark) WaveformDarkMutedUnplayed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    val safeDuration = durationMs.coerceAtLeast(1L)
    var isSeeking by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var lastHapticBar by remember { mutableIntStateOf(-1) }

    val bars = remember(trackId) { generateWaveformBars(trackId, WAVEFORM_BAR_COUNT) }
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val currentDuration by rememberUpdatedState(safeDuration)

    val displayProgress = if (isSeeking) {
        scrubProgress
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val displayPosMs = if (isSeeking) {
        (scrubProgress * safeDuration).toLong()
    } else {
        positionMs.coerceIn(0L, safeDuration)
    }

    val pulseTransition = rememberInfiniteTransition(label = "waveform_head_pulse")
    val headPulse by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_pulse_factor"
    )
    val livePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_live_phase"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        val interactiveModifier = if (!isLiveStream) {
            Modifier
                .pointerInput(trackId, safeDuration) {
                    detectTapGestures { offset ->
                        val p = waveformXToProgress(offset.x, size.width.toFloat())
                        EqSnapHelper.performStepTickHaptic(view)
                        currentOnSeekTo((p * currentDuration).toLong())
                    }
                }
                .pointerInput(trackId, safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            isSeeking = true
                            val p = waveformXToProgress(startOffset.x, size.width.toFloat())
                            scrubProgress = p
                            lastHapticBar = (p * (WAVEFORM_BAR_COUNT - 1)).roundToInt()
                            EqSnapHelper.performStepTickHaptic(view)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val p = waveformXToProgress(change.position.x, size.width.toFloat())
                            scrubProgress = p
                            val barIdx = (p * (WAVEFORM_BAR_COUNT - 1)).roundToInt()
                            if (barIdx != lastHapticBar) {
                                lastHapticBar = barIdx
                                EqSnapHelper.performStepTickHaptic(view)
                            }
                        },
                        onDragEnd = {
                            MixaHaptics.gestureEnd(view)
                            currentOnSeekTo((scrubProgress * currentDuration).toLong())
                            isSeeking = false
                            lastHapticBar = -1
                        },
                        onDragCancel = {
                            isSeeking = false
                            lastHapticBar = -1
                        }
                    )
                }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(interactiveModifier)
                .testTag("now_playing_seek_slider")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = bars.size
                if (count == 0) return@Canvas

                val totalWidth = size.width
                val totalHeight = size.height
                val slotWidth = totalWidth / count
                val barWidth = (slotWidth * 0.56f).coerceIn(2.dp.toPx(), 4.5.dp.toPx())
                val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

                val exactPlayIndex = displayProgress * (count - 1)
                val activeIndex = exactPlayIndex.roundToInt().coerceIn(0, count - 1)

                bars.forEachIndexed { index, amp ->
                    val xLeft = index * slotWidth + (slotWidth - barWidth) / 2f

                    if (isLiveStream) {
                        val liveMod = if (isPlaying) {
                            0.70f + 0.30f * sin(livePhase + index * 0.38f)
                        } else {
                            0.65f
                        }
                        val barHeight = (totalHeight * 0.86f * amp * liveMod)
                            .coerceIn(6.dp.toPx(), totalHeight)
                        val yTop = (totalHeight - barHeight) / 2f
                        val barColor = if (isPlaying) {
                            WaveformTealPlayed.copy(alpha = (0.58f + 0.42f * liveMod).coerceIn(0.45f, 1f))
                        } else {
                            unplayedBarColor
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(xLeft, yTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    } else {
                        val isPlayed = index <= activeIndex && (displayProgress > 0f || isSeeking)
                        val isHeadBar = index == activeIndex
                        val pulseScale = if (isHeadBar && isPlaying && !isSeeking) headPulse else 1f
                        val barHeight = (totalHeight * 0.88f * amp * pulseScale)
                            .coerceIn(6.dp.toPx(), totalHeight)
                        val yTop = (totalHeight - barHeight) / 2f

                        val barColor = when {
                            isHeadBar && isSeeking -> Color.White
                            isPlayed -> WaveformTealPlayed
                            else -> unplayedBarColor
                        }

                        if (isHeadBar && (isPlaying || isSeeking)) {
                            drawRoundRect(
                                color = WaveformTealPlayed.copy(alpha = 0.28f),
                                topLeft = Offset(xLeft - 1.5.dp.toPx(), (yTop - 2.dp.toPx()).coerceAtLeast(0f)),
                                size = Size(barWidth + 3.dp.toPx(), (barHeight + 4.dp.toPx()).coerceAtMost(totalHeight)),
                                cornerRadius = corner
                            )
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(xLeft, yTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isLiveStream) {
                Text(
                    text = if (isPlaying) "LIVE • STREAMING" else "LIVE • PAUSED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) WaveformTealPlayed else mutedLabelColor
                )
                Text(
                    text = "FLOAT PCM",
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedLabelColor
                )
            } else {
                Text(
                    text = formatDurationMs(displayPosMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSeeking) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSeeking) WaveformTealPlayed else mutedLabelColor
                )
                Text(
                    text = formatDurationMs(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedLabelColor
                )
            }
        }
    }
}
