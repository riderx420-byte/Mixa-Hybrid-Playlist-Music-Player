package com.example

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ThemeMode
import com.example.ui.SonoraApp
import com.example.ui.SonoraViewModel
import com.example.ui.theme.SonoraTheme

class MainActivity : ComponentActivity() {

    private val app: SonoraApplication
        get() = application as SonoraApplication

    private val viewModel: SonoraViewModel by viewModels {
        SonoraViewModel.Factory(
            repository = app.repository,
            playbackController = app.playbackController,
            audioEffectsManager = app.audioEffectsManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRateMode()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDark = when (userSettings.themeMode) {
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                ThemeMode.LIGHT, ThemeMode.PURE_WHITE -> false
                ThemeMode.SYSTEM -> systemDark
            }

            LaunchedEffect(effectiveDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            SonoraTheme(
                themeMode = userSettings.themeMode,
                themeAccent = userSettings.themeAccent,
                dynamicColor = userSettings.useDynamicColor,
                cornerStyle = userSettings.cornerStyle,
                playerBackdropStyle = userSettings.playerBackdropStyle
            ) {
                SonoraApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableHighRefreshRateMode()
    }

    @Suppress("DEPRECATION")
    private fun enableHighRefreshRateMode() {
        runCatching {
            val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager?.defaultDisplay
            }
            val currentMode = activeDisplay?.mode ?: return@runCatching
            val modes = activeDisplay.supportedModes ?: return@runCatching
            val bestMode = modes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: modes.maxByOrNull { it.refreshRate }
                ?: currentMode

            val targetRate = bestMode.refreshRate.coerceAtLeast(60f)
            val attrs = window.attributes
            var changed = false
            if (attrs.preferredDisplayModeId != bestMode.modeId) {
                attrs.preferredDisplayModeId = bestMode.modeId
                changed = true
            }
            if (attrs.preferredRefreshRate != targetRate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                attrs.preferredRefreshRate = targetRate
                changed = true
            }
            if (changed) {
                window.attributes = attrs
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.playbackController.connect()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            // Keep MediaSessionService alive in background; disconnect UI controller cleanly
            app.playbackController.disconnect()
        }
    }
}
