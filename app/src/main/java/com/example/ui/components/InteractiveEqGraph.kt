package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.EqBandUi
import com.example.ui.theme.LocalSonoraTokens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object EqSnapHelper {
    const val CENTER_SNAP_THRESHOLD = 0.075f
    const val MAX_DB_DISPLAY = 15
    const val BASS_HAPTIC_STEP = 25

    data class SnapAdjustment(
        val snappedLevel: Float,
        val discreteDb: Int,
        val snappedToCenter: Boolean,
        val steppedTick: Boolean
    )

    data class BassHapticSpec(
        val shouldTrigger: Boolean,
        val bucket: Int,
        val normalizedIntensity: Float,
        val durationMs: Long,
        val amplitude: Int,
        val isHarshZone: Boolean,
        val isMaxPunch: Boolean
    )

    fun evaluateAdjustment(
        previousLevel: Float,
        rawNewLevel: Float
    ): SnapAdjustment {
        val clampedRaw = rawNewLevel.coerceIn(-1f, 1f)
        val inCenterZone = abs(clampedRaw) <= CENTER_SNAP_THRESHOLD
        val snapped = if (inCenterZone) 0f else clampedRaw
        val prevDb = (previousLevel.coerceIn(-1f, 1f) * MAX_DB_DISPLAY).roundToInt()
        val nextDb = (snapped * MAX_DB_DISPLAY).roundToInt()

        val snappedToCenter = snapped == 0f && abs(previousLevel) > 0.001f
        val steppedTick = !snappedToCenter && nextDb != prevDb

        return SnapAdjustment(
            snappedLevel = snapped,
            discreteDb = nextDb,
            snappedToCenter = snappedToCenter,
            steppedTick = steppedTick
        )
    }

    fun evaluateBassHaptic(previousStrength: Int, newStrength: Int): BassHapticSpec {
        val prevClamped = previousStrength.coerceIn(0, 1000)
        val nextClamped = newStrength.coerceIn(0, 1000)
        val prevBucket = prevClamped / BASS_HAPTIC_STEP
        val nextBucket = nextClamped / BASS_HAPTIC_STEP
        val hitBoundary = (nextClamped == 0 && prevClamped > 0) ||
            (nextClamped == 1000 && prevClamped < 1000)
        val changed = (prevBucket != nextBucket) || hitBoundary
        val intensity = (nextClamped / 1000f).coerceIn(0f, 1f)
        val curved = intensity * intensity * 0.58f + intensity * 0.42f
        val amplitude = if (nextClamped == 0) {
            20
        } else {
            (32 + (curved * 223f).roundToInt()).coerceIn(1, 255)
        }
        val durationMs = if (nextClamped == 0) {
            6L
        } else {
            (8L + (curved * 22f).roundToInt()).coerceIn(6L, 30L)
        }
        return BassHapticSpec(
            shouldTrigger = changed,
            bucket = nextBucket,
            normalizedIntensity = intensity,
            durationMs = durationMs,
            amplitude = amplitude,
            isHarshZone = intensity >= 0.70f,
            isMaxPunch = intensity >= 0.90f
        )
    }

    fun performCenterSnapHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun performStepTickHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun dispatchHaptics(view: View, adjustment: SnapAdjustment) {
        when {
            adjustment.snappedToCenter -> performCenterSnapHaptic(view)
            adjustment.steppedTick -> performStepTickHaptic(view)
        }
    }

    @Suppress("DEPRECATION")
    fun dispatchBassHaptic(context: Context, view: View, spec: BassHapticSpec) {
        if (!spec.shouldTrigger) return
        val vibrator: Vibrator? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()

        if (vibrator != null && vibrator.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val handled = runCatching {
                when {
                    vibrator.hasAmplitudeControl() && spec.isMaxPunch -> {
                        val prePulseAmp = (spec.amplitude * 0.85f).roundToInt().coerceIn(1, 255)
                        val halfDur = (spec.durationMs / 2L).coerceAtLeast(8L)
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, halfDur, 16L, spec.durationMs),
                                intArrayOf(0, prePulseAmp, 0, spec.amplitude),
                                -1
                            )
                        )
                    }
                    vibrator.hasAmplitudeControl() -> {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(spec.durationMs, spec.amplitude)
                        )
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val effectId = when {
                            spec.isHarshZone -> VibrationEffect.EFFECT_HEAVY_CLICK
                            spec.normalizedIntensity >= 0.35f -> VibrationEffect.EFFECT_CLICK
                            else -> VibrationEffect.EFFECT_TICK
                        }
                        vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                    }
                    else -> {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(spec.durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    }
                }
            }.isSuccess
            if (handled) return
        }

        // Fallback via View haptic feedback with escalating harshness
        when {
            spec.isMaxPunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            spec.isHarshZone ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            spec.normalizedIntensity >= 0.35f ->
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            else -> performStepTickHaptic(view)
        }
    }
}

internal fun yOffsetToNormalized(
    yPx: Float,
    canvasHeightPx: Float,
    verticalPadPx: Float
): Float {
    val minY = verticalPadPx
    val maxY = (canvasHeightPx - verticalPadPx).coerceAtLeast(minY + 1f)
    val clampedY = yPx.coerceIn(minY, maxY)
    val fractionFromTop = (clampedY - minY) / (maxY - minY)
    return (1f - 2f * fractionFromTop).coerceIn(-1f, 1f)
}

internal fun xOffsetToBandIndex(
    xPx: Float,
    canvasWidthPx: Float,
    bandCount: Int = 5
): Int {
    if (canvasWidthPx <= 0f || bandCount <= 0) return 0
    return ((xPx / canvasWidthPx) * bandCount).toInt().coerceIn(0, bandCount - 1)
}

/**
 * Interactive 5-bar Frequency Response Equalizer graph.
 * Drag vertically on any of the 5 frequency bars to adjust gain with step haptics,
 * or return a bar to 0 dB (drag into center zone or double-tap) for a tactile center-snap detent.
 */
@Composable
fun InteractiveEqResponseGraph(
    bands: List<EqBandUi>,
    enabled: Boolean,
    onBandLevelChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceLowestColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val view = LocalView.current
    val density = LocalDensity.current

    val currentBands by rememberUpdatedState(bands)
    val currentOnBandChanged by rememberUpdatedState(onBandLevelChanged)

    var activeBandIndex by remember { mutableStateOf<Int?>(null) }
    val verticalPadPx = remember(density) { with(density) { 16.dp.toPx() } }

    // Smooth visual highlight scale for active node
    val animatedActiveScale by animateFloatAsState(
        targetValue = if (activeBandIndex != null) 1.35f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "eq_node_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.chipCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        // Top live dB readout per band
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            bands.forEach { band ->
                val isSelected = activeBandIndex == band.index
                val isCentered = abs(band.levelNormalized) <= 0.001f
                Text(
                    text = band.dbLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected || !isCentered) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        !enabled -> mutedColor.copy(alpha = 0.6f)
                        isSelected -> primary
                        isCentered -> mutedColor
                        else -> secondary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val list = currentBands
                            if (list.isEmpty()) return@detectTapGestures
                            val bandIdx = xOffsetToBandIndex(offset.x, size.width.toFloat(), list.size)
                            val band = list.getOrNull(bandIdx) ?: return@detectTapGestures
                            activeBandIndex = bandIdx
                            if (abs(band.levelNormalized) > 0.001f) {
                                EqSnapHelper.performCenterSnapHaptic(view)
                            } else {
                                EqSnapHelper.performStepTickHaptic(view)
                            }
                            currentOnBandChanged(band.index, 0f)
                            activeBandIndex = null
                        },
                        onTap = { offset ->
                            val list = currentBands
                            if (list.isEmpty()) return@detectTapGestures
                            val bandIdx = xOffsetToBandIndex(offset.x, size.width.toFloat(), list.size)
                            val band = list.getOrNull(bandIdx) ?: return@detectTapGestures
                            val raw = yOffsetToNormalized(offset.y, size.height.toFloat(), verticalPadPx)
                            val adj = EqSnapHelper.evaluateAdjustment(band.levelNormalized, raw)
                            EqSnapHelper.dispatchHaptics(view, adj)
                            currentOnBandChanged(band.index, adj.snappedLevel)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val list = currentBands
                            if (list.isEmpty()) return@detectDragGestures
                            val bandIdx = xOffsetToBandIndex(startOffset.x, size.width.toFloat(), list.size)
                            activeBandIndex = bandIdx
                            val band = list.getOrNull(bandIdx) ?: return@detectDragGestures
                            val raw = yOffsetToNormalized(startOffset.y, size.height.toFloat(), verticalPadPx)
                            val adj = EqSnapHelper.evaluateAdjustment(band.levelNormalized, raw)
                            EqSnapHelper.dispatchHaptics(view, adj)
                            currentOnBandChanged(band.index, adj.snappedLevel)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val bandIdx = activeBandIndex ?: return@detectDragGestures
                            val band = currentBands.getOrNull(bandIdx) ?: return@detectDragGestures
                            val raw = yOffsetToNormalized(change.position.y, size.height.toFloat(), verticalPadPx)
                            val adj = EqSnapHelper.evaluateAdjustment(band.levelNormalized, raw)
                            EqSnapHelper.dispatchHaptics(view, adj)
                            currentOnBandChanged(band.index, adj.snappedLevel)
                        },
                        onDragEnd = { activeBandIndex = null },
                        onDragCancel = { activeBandIndex = null }
                    )
                }
                .testTag("interactive_eq_graph_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val minY = verticalPadPx
                val maxY = (h - verticalPadPx).coerceAtLeast(minY + 1f)
                val centerY = (minY + maxY) / 2f
                val trackHeight = maxY - minY

                val gridColor = (if (tokens.isDark) Color.White else Color.Black).copy(alpha = 0.08f)
                val centerGridColor = (if (tokens.isDark) Color.White else Color.Black).copy(alpha = 0.22f)

                // Top (+15 dB) & bottom (-15 dB) dashed guides
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, minY),
                    end = Offset(w, minY),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, (minY + centerY) / 2f),
                    end = Offset(w, (minY + centerY) / 2f),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, (centerY + maxY) / 2f),
                    end = Offset(w, (centerY + maxY) / 2f),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, maxY),
                    end = Offset(w, maxY),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )

                // Zero dB center snap reference line
                drawLine(
                    color = centerGridColor,
                    start = Offset(0f, centerY),
                    end = Offset(w, centerY),
                    strokeWidth = 2f
                )

                val count = bands.size.coerceAtLeast(1)
                val colWidth = w / count
                val barWidth = min(colWidth * 0.34f, 22.dp.toPx())
                val barCorner = CornerRadius(barWidth / 2f, barWidth / 2f)
                val trackBgColor = (if (tokens.isDark) Color.White else Color.Black).copy(alpha = 0.09f)

                val points = bands.mapIndexed { idx, band ->
                    val norm = band.levelNormalized.coerceIn(-1f, 1f)
                    val cx = colWidth * (idx + 0.5f)
                    val cy = centerY - norm * (trackHeight / 2f)
                    val isActive = activeBandIndex == idx

                    // Recessed full-height equalizer bar track
                    drawRoundRect(
                        color = trackBgColor,
                        topLeft = Offset(cx - barWidth / 2f, minY),
                        size = Size(barWidth, trackHeight),
                        cornerRadius = barCorner
                    )

                    // Bipolar active bar fill anchored from center 0 dB baseline
                    if (abs(norm) > 0.005f) {
                        val topY = min(centerY, cy)
                        val fillH = max(abs(cy - centerY), 2f)
                        val fillBrush = if (!enabled) {
                            Brush.verticalGradient(listOf(mutedColor.copy(alpha = 0.45f), mutedColor.copy(alpha = 0.25f)))
                        } else if (norm >= 0f) {
                            Brush.verticalGradient(
                                colors = listOf(primary, secondary),
                                startY = topY,
                                endY = centerY
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(secondary.copy(alpha = 0.65f), tertiary.copy(alpha = 0.78f)),
                                startY = centerY,
                                endY = cy
                            )
                        }
                        drawRoundRect(
                            brush = fillBrush,
                            topLeft = Offset(cx - barWidth / 2f, topY),
                            size = Size(barWidth, fillH),
                            cornerRadius = barCorner
                        )
                    } else {
                        // Crisp center detent indicator on 0 dB line
                        drawRoundRect(
                            color = if (enabled) primary.copy(alpha = 0.6f) else mutedColor.copy(alpha = 0.4f),
                            topLeft = Offset(cx - barWidth * 0.55f, centerY - 2.dp.toPx()),
                            size = Size(barWidth * 1.1f, 4.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }

                    Offset(cx, cy) to isActive
                }

                // Smooth Spline Frequency Response Curve connecting the 5 bars
                val coords = points.map { it.first }
                if (coords.size >= 2) {
                    val curvePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.lastIndex) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        }
                    }

                    // Translucent baseline fill from 0 dB line to curve
                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(coords.last().x, centerY)
                        lineTo(coords.first().x, centerY)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                (if (enabled) primary else mutedColor).copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = minY,
                            endY = maxY
                        )
                    )

                    val curveBrush = if (enabled) {
                        Brush.horizontalGradient(listOf(primary, secondary, tertiary))
                    } else {
                        Brush.horizontalGradient(listOf(mutedColor, mutedColor))
                    }
                    drawPath(
                        path = curvePath,
                        brush = curveBrush,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Interactive Thumb Nodes on each bar
                points.forEach { (pt, isActive) ->
                    val baseRadius = 6.5.dp.toPx()
                    val nodeRadius = if (isActive) baseRadius * animatedActiveScale else baseRadius
                    if (isActive && enabled) {
                        drawCircle(
                            color = primary.copy(alpha = 0.25f),
                            radius = nodeRadius * 1.9f,
                            center = pt
                        )
                    }
                    drawCircle(
                        color = surfaceLowestColor,
                        radius = nodeRadius + 2.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = if (!enabled) mutedColor else if (isActive) secondary else primary,
                        radius = nodeRadius,
                        center = pt
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom frequency band labels + center snap hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bands.forEach { band ->
                val isSelected = activeBandIndex == band.index
                Text(
                    text = band.centerFreqLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected && enabled) primary else mutedColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
