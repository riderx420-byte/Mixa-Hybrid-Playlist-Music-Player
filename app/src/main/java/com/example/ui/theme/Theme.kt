package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode

@Immutable
data class SonoraThemeTokens(
    val isDark: Boolean = true,
    val isAmoled: Boolean = false,
    val isPureWhite: Boolean = false,
    val accent: ThemeAccent = ThemeAccent.CORAL_STUDIO,
    val cornerStyle: ThemeCornerStyle = ThemeCornerStyle.ROUNDED,
    val backdropStyle: PlayerBackdropStyle = PlayerBackdropStyle.GRADIENT,
    val cardCornerRadius: Dp = 16.dp,
    val heroCornerRadius: Dp = 22.dp,
    val chipCornerRadius: Dp = 10.dp,
    val emeraldAccent: Color = EmeraldPulse
)

val LocalSonoraTokens = staticCompositionLocalOf { SonoraThemeTokens() }

private fun contrastOnColor(bg: Color): Color =
    if (bg.luminance() > 0.55f) Color(0xFF0B0B0D) else Color.White

private fun buildCustomColorScheme(
    isDark: Boolean,
    isAmoled: Boolean,
    isPureWhite: Boolean,
    accent: ThemeAccent
): ColorScheme {
    val isMono = accent == ThemeAccent.MONOCHROME
    val basePrimary = Color(accent.primaryHex)
    val baseSecondary = Color(accent.secondaryHex)
    val baseTertiary = Color(accent.tertiaryHex)

    return if (isDark) {
        val bg = if (isAmoled) Color.Black else ObsidianBackground
        val surf = if (isAmoled) Color.Black else ObsidianSurface
        val surfLow = if (isAmoled) Color(0xFF0E0E0E) else Color(0xFF151523)
        val surfVar = if (isAmoled) Color(0xFF161616) else ObsidianSurfaceVariant
        val surfHigh = if (isAmoled) Color(0xFF202020) else ObsidianElevated
        val surfHighest = if (isAmoled) Color(0xFF2B2B2B) else Color(0xFF2E2E46)
        val containerBase = if (isAmoled) Color(0xFF141414) else Color(0xFF171726)

        val primary = if (isMono) Color(0xFFF5F5F7) else basePrimary
        val secondary = if (isMono) Color(0xFFC8C8D0) else baseSecondary
        val tertiary = if (isMono) Color(0xFF90909A) else baseTertiary

        val primaryContainer = lerp(containerBase, primary, if (isMono) 0.16f else 0.30f)
        val onPrimaryContainer = if (isMono) Color.White else lerp(Color.White, primary, 0.24f)
        val secondaryContainer = lerp(containerBase, secondary, if (isMono) 0.14f else 0.28f)
        val onSecondaryContainer = if (isMono) Color.White else lerp(Color.White, secondary, 0.26f)
        val tertiaryContainer = lerp(containerBase, tertiary, if (isMono) 0.14f else 0.28f)
        val onTertiaryContainer = if (isMono) Color.White else lerp(Color.White, tertiary, 0.24f)

        darkColorScheme(
            primary = primary,
            onPrimary = contrastOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = contrastOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = contrastOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = bg,
            onBackground = if (isAmoled) Color.White else TextPrimaryDark,
            surface = surf,
            onSurface = if (isAmoled) Color.White else TextPrimaryDark,
            surfaceVariant = surfVar,
            onSurfaceVariant = if (isAmoled) Color(0xFFA6A6AE) else TextSecondaryDark,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surfLow,
            surfaceContainer = surfVar,
            surfaceContainerHigh = surfHigh,
            surfaceContainerHighest = surfHighest,
            outline = Color.Transparent,
            outlineVariant = Color.Transparent
        )
    } else {
        val bg = if (isPureWhite) Color.White else AlabasterBackground
        val surf = if (isPureWhite) Color.White else AlabasterSurface
        val surfLow = if (isPureWhite) Color(0xFFF3F3F6) else Color(0xFFFFFFFF)
        val surfVar = if (isPureWhite) Color(0xFFEBEBF0) else AlabasterSurfaceVariant
        val surfHigh = if (isPureWhite) Color(0xFFE1E1E8) else Color(0xFFDAD5EA)
        val surfHighest = if (isPureWhite) Color(0xFFD5D5DF) else Color(0xFFCEC7E2)

        val primary = if (isMono) Color(0xFF141418) else lerp(basePrimary, Color(0xFF200010), 0.16f)
        val secondary = if (isMono) Color(0xFF42424C) else lerp(baseSecondary, Color(0xFF332000), 0.20f)
        val tertiary = if (isMono) Color(0xFF6B6B76) else lerp(baseTertiary, Color(0xFF190033), 0.16f)

        val primaryContainer = if (isMono) Color(0xFFEAEAEF) else lerp(Color.White, primary, 0.20f)
        val onPrimaryContainer = if (isMono) Color(0xFF111114) else lerp(Color.Black, primary, 0.42f)
        val secondaryContainer = if (isMono) Color(0xFFEAEAEF) else lerp(Color.White, secondary, 0.20f)
        val onSecondaryContainer = if (isMono) Color(0xFF111114) else lerp(Color.Black, secondary, 0.46f)
        val tertiaryContainer = if (isMono) Color(0xFFEAEAEF) else lerp(Color.White, tertiary, 0.20f)
        val onTertiaryContainer = if (isMono) Color(0xFF111114) else lerp(Color.Black, tertiary, 0.42f)

        lightColorScheme(
            primary = primary,
            onPrimary = contrastOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = contrastOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = contrastOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = bg,
            onBackground = if (isPureWhite) Color(0xFF0A0A0D) else TextPrimaryLight,
            surface = surf,
            onSurface = if (isPureWhite) Color(0xFF0A0A0D) else TextPrimaryLight,
            surfaceVariant = surfVar,
            onSurfaceVariant = if (isPureWhite) Color(0xFF555560) else TextSecondaryLight,
            surfaceContainerLowest = surf,
            surfaceContainerLow = surfLow,
            surfaceContainer = surfVar,
            surfaceContainerHigh = surfHigh,
            surfaceContainerHighest = surfHighest,
            outline = Color.Transparent,
            outlineVariant = Color.Transparent
        )
    }
}

@Composable
fun SonoraTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    themeAccent: ThemeAccent = ThemeAccent.CORAL_STUDIO,
    dynamicColor: Boolean = false,
    cornerStyle: ThemeCornerStyle = ThemeCornerStyle.ROUNDED,
    playerBackdropStyle: PlayerBackdropStyle = PlayerBackdropStyle.GRADIENT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT, ThemeMode.PURE_WHITE -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val isAmoled = themeMode == ThemeMode.AMOLED
    val isPureWhite = themeMode == ThemeMode.PURE_WHITE

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseDynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            when {
                isAmoled -> baseDynamic.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0E0E0E),
                    surfaceContainer = Color(0xFF161616),
                    surfaceContainerHigh = Color(0xFF202020),
                    surfaceContainerHighest = Color(0xFF2B2B2B),
                    outline = Color.Transparent,
                    outlineVariant = Color.Transparent
                )
                isPureWhite -> baseDynamic.copy(
                    background = Color.White,
                    surface = Color.White,
                    surfaceContainerLowest = Color.White,
                    surfaceContainerLow = Color(0xFFF3F3F6),
                    surfaceContainer = Color(0xFFEBEBF0),
                    surfaceContainerHigh = Color(0xFFE1E1E8),
                    surfaceContainerHighest = Color(0xFFD5D5DF),
                    outline = Color.Transparent,
                    outlineVariant = Color.Transparent
                )
                else -> baseDynamic.copy(
                    outline = Color.Transparent,
                    outlineVariant = Color.Transparent
                )
            }
        }
        else -> buildCustomColorScheme(
            isDark = isDark,
            isAmoled = isAmoled,
            isPureWhite = isPureWhite,
            accent = themeAccent
        )
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape((cornerStyle.smallDp - 4).coerceAtLeast(2).dp),
        small = RoundedCornerShape(cornerStyle.smallDp.dp),
        medium = RoundedCornerShape(cornerStyle.mediumDp.dp),
        large = RoundedCornerShape(cornerStyle.largeDp.dp),
        extraLarge = RoundedCornerShape((cornerStyle.largeDp + 6).dp)
    )

    val tokens = SonoraThemeTokens(
        isDark = isDark,
        isAmoled = isAmoled,
        isPureWhite = isPureWhite,
        accent = themeAccent,
        cornerStyle = cornerStyle,
        backdropStyle = playerBackdropStyle,
        cardCornerRadius = cornerStyle.mediumDp.dp,
        heroCornerRadius = cornerStyle.largeDp.dp,
        chipCornerRadius = cornerStyle.smallDp.dp,
        emeraldAccent = EmeraldPulse
    )

    CompositionLocalProvider(LocalSonoraTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SonoraTypography,
            shapes = shapes,
            content = content
        )
    }
}
