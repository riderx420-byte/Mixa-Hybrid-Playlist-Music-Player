package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkMeshColors(
    val primaryGlow: Color,
    val secondaryGlow: Color
)

/**
 * Extracts a rich dominant color and a warm/contrasting secondary highlight from sampled ARGB pixels.
 * Prefers saturated tones with balanced luminance so pure black or pure white margins don't mute the glow.
 */
fun extractMeshColorsFromPixels(
    pixels: IntArray,
    fallbackPrimary: Color,
    fallbackSecondary: Color
): ArtworkMeshColors {
    if (pixels.isEmpty()) {
        return ArtworkMeshColors(fallbackPrimary, fallbackSecondary)
    }

    val bucketCount = 12
    val bucketWeight = FloatArray(bucketCount)
    val bucketR = DoubleArray(bucketCount)
    val bucketG = DoubleArray(bucketCount)
    val bucketB = DoubleArray(bucketCount)
    val hsv = FloatArray(3)

    val step = (pixels.size / 900).coerceAtLeast(1)
    for (i in pixels.indices step step) {
        val px = pixels[i]
        val alpha = AndroidColor.alpha(px)
        if (alpha < 96) continue

        AndroidColor.colorToHSV(px, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]

        if (value < 0.12f || (value > 0.94f && sat < 0.15f) || sat < 0.14f) continue

        val warmthBoost = if (hue <= 60f || hue >= 330f) 1.28f else 1.0f
        val luminanceWeight = 1f - abs(value - 0.52f)
        val weight = (sat * sat * 2.2f + 0.15f) * luminanceWeight.coerceAtLeast(0.2f) * warmthBoost

        val bucket = ((hue / 30f).toInt()).coerceIn(0, bucketCount - 1)
        bucketWeight[bucket] += weight
        bucketR[bucket] += AndroidColor.red(px) * weight.toDouble()
        bucketG[bucket] += AndroidColor.green(px) * weight.toDouble()
        bucketB[bucket] += AndroidColor.blue(px) * weight.toDouble()
    }

    var bestBucket = -1
    var bestWeight = 0f
    for (b in 0 until bucketCount) {
        if (bucketWeight[b] > bestWeight) {
            bestWeight = bucketWeight[b]
            bestBucket = b
        }
    }

    if (bestBucket < 0 || bestWeight <= 0f) {
        return ArtworkMeshColors(fallbackPrimary, fallbackSecondary)
    }

    fun bucketToColor(b: Int): Color {
        val w = bucketWeight[b].toDouble().coerceAtLeast(1e-4)
        val r = (bucketR[b] / w).roundToInt().coerceIn(0, 255)
        val g = (bucketG[b] / w).roundToInt().coerceIn(0, 255)
        val bl = (bucketB[b] / w).roundToInt().coerceIn(0, 255)
        return Color(r / 255f, g / 255f, bl / 255f, 1f)
    }

    val primaryColor = bucketToColor(bestBucket)

    var secondBucket = -1
    var secondWeight = 0f
    for (b in 0 until bucketCount) {
        val dist = abs(b - bestBucket).let { kotlin.math.min(it, bucketCount - it) }
        if (dist >= 1 && bucketWeight[b] > secondWeight) {
            // Prefer buckets shifted at least ~30° away from primary
            val separationBonus = if (dist >= 2) 1.25f else 0.7f
            val scored = bucketWeight[b] * separationBonus
            if (scored > secondWeight) {
                secondWeight = scored
                secondBucket = b
            }
        }
    }

    val secondaryColor = if (secondBucket >= 0 && bucketWeight[secondBucket] > bestWeight * 0.12f) {
        bucketToColor(secondBucket)
    } else {
        // Warm golden-tinted complement when artwork is single-hue
        val primaryArgb = primaryColor.toArgb()
        AndroidColor.colorToHSV(primaryArgb, hsv)
        hsv[0] = (hsv[0] + 34f) % 360f
        hsv[1] = (hsv[1] * 0.92f).coerceIn(0.35f, 1f)
        hsv[2] = (hsv[2] * 1.15f).coerceIn(0.45f, 0.95f)
        Color(AndroidColor.HSVToColor(hsv))
    }

    return ArtworkMeshColors(primaryColor, secondaryColor)
}

@Composable
fun rememberArtworkMeshColors(
    artworkUri: String?,
    fallbackPrimary: Color,
    fallbackSecondary: Color
): ArtworkMeshColors {
    val context = LocalContext.current
    var rawColors by remember(fallbackPrimary, fallbackSecondary) {
        mutableStateOf(ArtworkMeshColors(fallbackPrimary, fallbackSecondary))
    }

    LaunchedEffect(artworkUri, fallbackPrimary, fallbackSecondary) {
        if (artworkUri.isNullOrBlank()) {
            rawColors = ArtworkMeshColors(fallbackPrimary, fallbackSecondary)
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false)
                    .size(72)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap: Bitmap? = (result as? SuccessResult)?.drawable?.toBitmap(
                    width = 64,
                    height = 64,
                    config = Bitmap.Config.ARGB_8888
                )
                if (bitmap != null) {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    extractMeshColorsFromPixels(pixels, fallbackPrimary, fallbackSecondary)
                } else {
                    ArtworkMeshColors(fallbackPrimary, fallbackSecondary)
                }
            }.getOrElse {
                ArtworkMeshColors(fallbackPrimary, fallbackSecondary)
            }
        }
        rawColors = extracted
    }

    val animatedPrimary by animateColorAsState(
        targetValue = rawColors.primaryGlow,
        animationSpec = tween(durationMillis = 520),
        label = "mesh_primary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = rawColors.secondaryGlow,
        animationSpec = tween(durationMillis = 520),
        label = "mesh_secondary"
    )

    return ArtworkMeshColors(
        primaryGlow = animatedPrimary,
        secondaryGlow = animatedSecondary
    )
}
