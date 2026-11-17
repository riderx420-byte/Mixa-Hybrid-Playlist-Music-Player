package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.innertube.InnerTubeAuthHelper
import com.example.domain.model.EqPreset
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sonoraDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_settings")

data class UserAudioSettings(
    val autoRadioEnabled: Boolean = true,
    val preferOpusStream: Boolean = true,
    val autoSkipBrokenTracks: Boolean = false,
    val preferredStreamQuality: PreferredStreamQuality = PreferredStreamQuality.AUTO_BEST,
    val directFloatPcmBypassEq: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqBandLevels: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoostStrength: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themeAccent: ThemeAccent = ThemeAccent.CORAL_STUDIO,
    val useDynamicColor: Boolean = false,
    val cornerStyle: ThemeCornerStyle = ThemeCornerStyle.ROUNDED,
    val playerBackdropStyle: PlayerBackdropStyle = PlayerBackdropStyle.GRADIENT,
    val ytMusicCookie: String? = null,
    val ytAccountLabel: String? = null,
    val ytSelectedEmail: String? = null,
    val crossfadeDurationSeconds: Int = 0
) {
    val isYoutubeSignedIn: Boolean
        get() = InnerTubeAuthHelper.hasValidSessionCookie(ytMusicCookie)
}

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val AUTO_RADIO = booleanPreferencesKey("auto_radio_enabled")
        val PREFER_OPUS = booleanPreferencesKey("prefer_opus_stream")
        val AUTO_SKIP_BROKEN = booleanPreferencesKey("auto_skip_broken_tracks")
        val PREFERRED_STREAM_QUALITY = stringPreferencesKey("preferred_stream_quality")
        val DIRECT_FLOAT_PCM_BYPASS_EQ = booleanPreferencesKey("direct_float_pcm_bypass_eq")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val BASS_BOOST = intPreferencesKey("bass_boost_strength")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val CORNER_STYLE = stringPreferencesKey("theme_corner_style")
        val PLAYER_BACKDROP = stringPreferencesKey("player_backdrop_style")
        val YTM_COOKIE = stringPreferencesKey("yt_music_cookie")
        val YTM_ACCOUNT_LABEL = stringPreferencesKey("yt_account_label")
        val YTM_SELECTED_EMAIL = stringPreferencesKey("yt_selected_email")
        val CROSSFADE_DURATION_SECONDS = intPreferencesKey("crossfade_duration_seconds")
    }

    val settingsFlow: Flow<UserAudioSettings> = context.sonoraDataStore.data.map { prefs ->
        val presetName = prefs[Keys.EQ_PRESET] ?: EqPreset.FLAT.name
        val preset = runCatching { EqPreset.valueOf(presetName) }.getOrDefault(EqPreset.FLAT)
        val bandLevels = prefs[Keys.EQ_BANDS]
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 }
            ?: listOf(0f, 0f, 0f, 0f, 0f)

        val streamQuality = prefs[Keys.PREFERRED_STREAM_QUALITY]?.let {
            runCatching { PreferredStreamQuality.valueOf(it) }.getOrNull()
        } ?: PreferredStreamQuality.AUTO_BEST

        val themeMode = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.DARK

        val themeAccent = prefs[Keys.THEME_ACCENT]?.let {
            runCatching { ThemeAccent.valueOf(it) }.getOrNull()
        } ?: ThemeAccent.CORAL_STUDIO

        val cornerStyle = prefs[Keys.CORNER_STYLE]?.let {
            runCatching { ThemeCornerStyle.valueOf(it) }.getOrNull()
        } ?: ThemeCornerStyle.ROUNDED

        val backdropStyle = prefs[Keys.PLAYER_BACKDROP]?.let {
            runCatching { PlayerBackdropStyle.valueOf(it) }.getOrNull()
        } ?: PlayerBackdropStyle.GRADIENT

        UserAudioSettings(
            autoRadioEnabled = prefs[Keys.AUTO_RADIO] ?: true,
            preferOpusStream = prefs[Keys.PREFER_OPUS] ?: true,
            autoSkipBrokenTracks = prefs[Keys.AUTO_SKIP_BROKEN] ?: false,
            preferredStreamQuality = streamQuality,
            directFloatPcmBypassEq = prefs[Keys.DIRECT_FLOAT_PCM_BYPASS_EQ] ?: false,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPreset = preset,
            eqBandLevels = bandLevels,
            bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 0,
            themeMode = themeMode,
            themeAccent = themeAccent,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            cornerStyle = cornerStyle,
            playerBackdropStyle = backdropStyle,
            ytMusicCookie = prefs[Keys.YTM_COOKIE]?.takeIf { it.isNotBlank() },
            ytAccountLabel = prefs[Keys.YTM_ACCOUNT_LABEL]?.takeIf { it.isNotBlank() },
            ytSelectedEmail = prefs[Keys.YTM_SELECTED_EMAIL]?.takeIf { it.isNotBlank() },
            crossfadeDurationSeconds = (prefs[Keys.CROSSFADE_DURATION_SECONDS] ?: 0).coerceIn(0, 12)
        )
    }

    suspend fun setAutoRadioEnabled(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.AUTO_RADIO] = enabled }
    }

    suspend fun setPreferOpus(preferOpus: Boolean) {
        context.sonoraDataStore.edit { it[Keys.PREFER_OPUS] = preferOpus }
    }

    suspend fun setAutoSkipBrokenTracks(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.AUTO_SKIP_BROKEN] = enabled }
    }

    suspend fun setPreferredStreamQuality(quality: PreferredStreamQuality) {
        context.sonoraDataStore.edit { it[Keys.PREFERRED_STREAM_QUALITY] = quality.name }
    }

    suspend fun setDirectFloatPcmBypassEq(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.DIRECT_FLOAT_PCM_BYPASS_EQ] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.sonoraDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setThemeAccent(accent: ThemeAccent) {
        context.sonoraDataStore.edit { it[Keys.THEME_ACCENT] = accent.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCornerStyle(style: ThemeCornerStyle) {
        context.sonoraDataStore.edit { it[Keys.CORNER_STYLE] = style.name }
    }

    suspend fun setPlayerBackdropStyle(style: PlayerBackdropStyle) {
        context.sonoraDataStore.edit { it[Keys.PLAYER_BACKDROP] = style.name }
    }

    suspend fun setCrossfadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        context.sonoraDataStore.edit { it[Keys.CROSSFADE_DURATION_SECONDS] = clamped }
    }

    suspend fun setSelectedGoogleEmail(email: String?) {
        context.sonoraDataStore.edit { prefs ->
            val clean = email?.trim()?.takeIf { it.isNotBlank() }
            if (clean != null) {
                prefs[Keys.YTM_SELECTED_EMAIL] = clean
            } else {
                prefs.remove(Keys.YTM_SELECTED_EMAIL)
            }
        }
    }

    suspend fun saveYouTubeAccount(cookie: String, accountLabel: String?) {
        val sanitized = InnerTubeAuthHelper.sanitizeCookie(cookie)
        context.sonoraDataStore.edit { prefs ->
            prefs[Keys.YTM_COOKIE] = sanitized
            if (!accountLabel.isNullOrBlank()) {
                prefs[Keys.YTM_ACCOUNT_LABEL] = accountLabel.trim()
            } else {
                prefs.remove(Keys.YTM_ACCOUNT_LABEL)
            }
        }
    }

    suspend fun clearYouTubeAccount() {
        context.sonoraDataStore.edit { prefs ->
            prefs.remove(Keys.YTM_COOKIE)
            prefs.remove(Keys.YTM_ACCOUNT_LABEL)
        }
    }

    suspend fun saveEqualizerState(
        enabled: Boolean,
        preset: EqPreset,
        bandLevels: List<Float>,
        bassBoostStrength: Int
    ) {
        context.sonoraDataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = enabled
            prefs[Keys.EQ_PRESET] = preset.name
            prefs[Keys.EQ_BANDS] = bandLevels.joinToString(",")
            prefs[Keys.BASS_BOOST] = bassBoostStrength
        }
    }
}
