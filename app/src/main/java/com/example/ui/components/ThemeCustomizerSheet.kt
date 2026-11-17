package com.example.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.UserAudioSettings
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizerSheet(
    settings: UserAudioSettings,
    onDismiss: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (ThemeAccent) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectCornerStyle: (ThemeCornerStyle) -> Unit,
    onSelectBackdropStyle: (PlayerBackdropStyle) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Theme Studio",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Personalize surfaces, accents, geometry & player backdrop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onDismiss()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_theme_studio_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close theme customizer")
                }
            }

            ThemePreviewCard(settings = settings)

            ThemeCustomizationControls(
                settings = settings,
                onSelectThemeMode = onSelectThemeMode,
                onSelectAccent = onSelectAccent,
                onToggleDynamicColor = onToggleDynamicColor,
                onSelectCornerStyle = onSelectCornerStyle,
                onSelectBackdropStyle = onSelectBackdropStyle
            )
        }
    }
}

@Composable
fun ThemePreviewCard(
    settings: UserAudioSettings,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(tokens.heroCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            primary.copy(alpha = 0.18f),
                            tertiary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(tokens.chipCornerRadius))
                    .background(
                        Brush.linearGradient(listOf(primary, tertiary))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${settings.themeMode.label} • ${if (settings.useDynamicColor) "Material You" else settings.themeAccent.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${settings.cornerStyle.label} • ${settings.playerBackdropStyle.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeCustomizationControls(
    settings: UserAudioSettings,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (ThemeAccent) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectCornerStyle: (ThemeCornerStyle) -> Unit,
    onSelectBackdropStyle: (PlayerBackdropStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Surface Mode
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Contrast,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Surface Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = settings.themeMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            MixaHaptics.tick(view)
                            onSelectThemeMode(mode)
                        },
                        label = { Text(mode.label) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = primary.copy(alpha = 0.24f),
                            selectedLabelColor = primary
                        ),
                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // 2. Accent Palette Swatches
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Accent Signature",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeAccent.entries.forEach { accent ->
                    val isSelected = settings.themeAccent == accent && !settings.useDynamicColor
                    val swatchColor = Color(accent.primaryHex)
                    val secondaryColor = Color(accent.tertiaryHex)
                    val haloColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                        label = "swatch_halo"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                            .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                                if (settings.useDynamicColor) {
                                    onToggleDynamicColor(false)
                                }
                                onSelectAccent(accent)
                            }
                            .padding(4.dp)
                            .testTag("theme_accent_${accent.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(haloColor)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(swatchColor, secondaryColor))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val checkTint = if (swatchColor.red * 0.299f + swatchColor.green * 0.587f + swatchColor.blue * 0.114f > 0.58f) {
                                    Color(0xFF0B0B0D)
                                } else {
                                    Color.White
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = accent.label,
                                    tint = checkTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = accent.label.substringBefore(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Dynamic Wallpaper Color (Material You)
        Surface(
            shape = RoundedCornerShape(tokens.cardCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Material You Dynamic Color",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (dynamicSupported) {
                                "Sync palette automatically from your device wallpaper"
                            } else {
                                "Requires Android 12 or newer"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = settings.useDynamicColor && dynamicSupported,
                    onCheckedChange = {
                        if (dynamicSupported) {
                            MixaHaptics.toggle(view, it)
                            onToggleDynamicColor(it)
                        }
                    },
                    enabled = dynamicSupported,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = primary
                    ),
                    modifier = Modifier.testTag("theme_dynamic_color_switch")
                )
            }
        }

        // 4. Corner Geometry
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.RoundedCorner,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Corner Geometry",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemeCornerStyle.entries.forEach { corner ->
                    val selected = settings.cornerStyle == corner
                    FilterChip(
                        selected = selected,
                        onClick = {
                            MixaHaptics.tick(view)
                            onSelectCornerStyle(corner)
                        },
                        label = { Text(corner.label) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = primary.copy(alpha = 0.24f),
                            selectedLabelColor = primary
                        ),
                        modifier = Modifier.testTag("theme_corner_${corner.name.lowercase()}")
                    )
                }
            }
        }

        // 5. Now Playing Backdrop
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Now Playing Backdrop",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlayerBackdropStyle.entries.forEach { backdrop ->
                    val selected = settings.playerBackdropStyle == backdrop
                    FilterChip(
                        selected = selected,
                        onClick = {
                            MixaHaptics.tick(view)
                            onSelectBackdropStyle(backdrop)
                        },
                        label = { Text(backdrop.label) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = primary.copy(alpha = 0.24f),
                            selectedLabelColor = primary
                        ),
                        modifier = Modifier.testTag("theme_backdrop_${backdrop.name.lowercase()}")
                    )
                }
            }
        }
    }
}
