package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.example.data.local.SettingsPreferences
import com.example.domain.model.EqBandUi
import com.example.domain.model.EqPreset
import com.example.domain.model.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AudioEffectsManager(
    private val settingsPreferences: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attachedSessionId: Int = 0
    private var minLevelMb: Short = -1500
    private var maxLevelMb: Short = 1500

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    init {
        scope.launch {
            val saved = settingsPreferences.settingsFlow.first()
            _state.value = _state.value.copy(
                isEnabled = saved.eqEnabled,
                activePreset = saved.eqPreset,
                bassBoostStrength = saved.bassBoostStrength,
                bands = buildBandsFromNormalized(saved.eqBandLevels)
            )
        }
    }

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId) return
        releaseEffects()
        attachedSessionId = audioSessionId

        val eq = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        val bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()

        if (eq == null) {
            _state.value = _state.value.copy(isAvailable = false)
            return
        }

        equalizer = eq
        bassBoost = bass

        val range = runCatching { eq.bandLevelRange }.getOrNull()
        if (range != null && range.size >= 2) {
            minLevelMb = range[0]
            maxLevelMb = range[1]
        }

        val current = _state.value
        val bandCount = runCatching { eq.numberOfBands.toInt() }.getOrDefault(5).coerceAtMost(5)
        val freqLabels = (0 until bandCount).map { idx ->
            val hz = runCatching { eq.getCenterFreq(idx.toShort()) / 1000 }.getOrDefault(0)
            formatHz(hz)
        }

        val updatedBands = current.bands.mapIndexed { idx, band ->
            val label = freqLabels.getOrNull(idx) ?: band.centerFreqLabel
            band.copy(centerFreqLabel = label, dbLabel = formatDb(band.levelNormalized))
        }

        _state.value = current.copy(
            isAvailable = true,
            bands = updatedBands
        )
        applyCurrentStateToAudioFx()
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        applyCurrentStateToAudioFx()
        persist()
    }

    fun applyPreset(preset: EqPreset) {
        val current = _state.value
        val updatedBands = current.bands.mapIndexed { idx, band ->
            val norm = preset.bandOffsets.getOrElse(idx) { 0f }.coerceIn(-1f, 1f)
            band.copy(levelNormalized = norm, dbLabel = formatDb(norm))
        }
        _state.value = current.copy(
            isEnabled = true,
            activePreset = preset,
            bands = updatedBands,
            bassBoostStrength = preset.bassBoostStrength
        )
        applyCurrentStateToAudioFx()
        persist()
    }

    fun setBandLevel(bandIndex: Int, normalizedLevel: Float) {
        val clamped = normalizedLevel.coerceIn(-1f, 1f)
        val current = _state.value
        val updatedBands = current.bands.map { band ->
            if (band.index == bandIndex) {
                band.copy(levelNormalized = clamped, dbLabel = formatDb(clamped))
            } else {
                band
            }
        }
        _state.value = current.copy(
            isEnabled = true,
            activePreset = EqPreset.CUSTOM,
            bands = updatedBands
        )
        applyCurrentStateToAudioFx()
        persist()
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            activePreset = EqPreset.CUSTOM,
            bassBoostStrength = clamped
        )
        applyCurrentStateToAudioFx()
        persist()
    }

    fun setBypassedForDirectPcm(bypass: Boolean) {
        if (_state.value.isBypassedForDirectPcm == bypass) return
        _state.value = _state.value.copy(isBypassedForDirectPcm = bypass)
        applyCurrentStateToAudioFx()
    }

    private fun applyCurrentStateToAudioFx() {
        val current = _state.value
        val activeEq = current.isEnabled && !current.isBypassedForDirectPcm
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = activeEq
                val count = eq.numberOfBands.toInt().coerceAtMost(current.bands.size)
                for (i in 0 until count) {
                    val norm = current.bands[i].levelNormalized
                    val mb = if (norm >= 0f) {
                        (norm * maxLevelMb).roundToInt().toShort()
                    } else {
                        (-norm * minLevelMb).roundToInt().toShort()
                    }
                    eq.setBandLevel(i.toShort(), mb)
                }
            }
        }
        runCatching {
            bassBoost?.let { bass ->
                bass.enabled = activeEq && current.bassBoostStrength > 0
                if (bass.strengthSupported) {
                    bass.setStrength(current.bassBoostStrength.toShort())
                }
            }
        }
    }

    private fun persist() {
        val snapshot = _state.value
        scope.launch {
            settingsPreferences.saveEqualizerState(
                enabled = snapshot.isEnabled,
                preset = snapshot.activePreset,
                bandLevels = snapshot.bands.map { it.levelNormalized },
                bassBoostStrength = snapshot.bassBoostStrength
            )
        }
    }

    private fun buildBandsFromNormalized(levels: List<Float>): List<EqBandUi> {
        val defaults = EqualizerState.defaultBands()
        return defaults.mapIndexed { idx, band ->
            val norm = levels.getOrElse(idx) { 0f }.coerceIn(-1f, 1f)
            band.copy(levelNormalized = norm, dbLabel = formatDb(norm))
        }
    }

    private fun formatDb(normalized: Float): String {
        val db = (normalized * (maxLevelMb / 100f)).roundToInt()
        return if (db > 0) "+$db dB" else "$db dB"
    }

    private fun formatHz(hz: Int): String {
        return when {
            hz <= 0 -> "-"
            hz >= 1000 -> String.format("%.1f kHz", hz / 1000f)
            else -> "$hz Hz"
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        attachedSessionId = 0
    }
}
