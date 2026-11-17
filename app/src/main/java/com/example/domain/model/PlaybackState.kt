package com.example.domain.model

enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}

enum class StreamErrorCategory {
    HTTP_403_FORBIDDEN,
    HTTP_410_EXPIRED,
    RESTRICTED_OR_LOGIN,
    NETWORK_UNAVAILABLE,
    FORMAT_OR_CODEC,
    LOCAL_FILE_MISSING,
    UNKNOWN
}

data class StreamErrorInfo(
    val category: StreamErrorCategory,
    val title: String,
    val detail: String,
    val httpStatus: Int? = null,
    val videoId: String? = null,
    val failedClient: String? = null,
    val attemptCount: Int = 0,
    val canSwitchFormat: Boolean = true,
    val suggestsSignIn: Boolean = false
)

data class LiveAudioFormatTelemetry(
    val codecLabel: String = "",
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val bitrateKbps: Int = 0,
    val channelCount: Int = 2,
    val isFloatPcmOutput: Boolean = true,
    val isBypassingDsp: Boolean = false,
    val liveIcyTitle: String? = null
)

data class PlaybackState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val isRadioActive: Boolean = false,
    val isLoadingRadio: Boolean = false,
    val radioSeedTitle: String? = null,
    val activeStreamInfo: String = "",
    val audioTelemetry: LiveAudioFormatTelemetry? = null,
    val activeQualityTier: AudioQualityTier? = null,
    val errorMessage: String? = null,
    val streamError: StreamErrorInfo? = null
)

enum class EqPreset(
    val displayName: String,
    val bandOffsets: List<Float>,
    val bassBoostStrength: Int
) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f), 0),
    BASS_BOOST("Bass Boost", listOf(0.8f, 0.45f, 0f, 0.1f, 0.2f), 750),
    STUDIO_CLARITY("Studio Clarity", listOf(0.2f, -0.1f, 0.35f, 0.55f, 0.65f), 200),
    VOCAL_PRESENCE("Vocal", listOf(-0.2f, 0.15f, 0.65f, 0.55f, 0.15f), 100),
    ELECTRONIC("Electronic", listOf(0.7f, 0.4f, -0.15f, 0.45f, 0.75f), 600),
    LATE_NIGHT("Late Night", listOf(0.35f, 0.15f, 0.25f, 0.1f, -0.25f), 300),
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f), 0)
}

data class EqBandUi(
    val index: Int,
    val centerFreqLabel: String,
    val levelNormalized: Float, // -1f to 1f
    val dbLabel: String
)

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isBypassedForDirectPcm: Boolean = false,
    val activePreset: EqPreset = EqPreset.FLAT,
    val bands: List<EqBandUi> = defaultBands(),
    val bassBoostStrength: Int = 0 // 0..1000
) {
    companion object {
        fun defaultBands(): List<EqBandUi> = listOf(
            EqBandUi(0, "60 Hz", 0f, "0 dB"),
            EqBandUi(1, "230 Hz", 0f, "0 dB"),
            EqBandUi(2, "910 Hz", 0f, "0 dB"),
            EqBandUi(3, "3.6 kHz", 0f, "0 dB"),
            EqBandUi(4, "14 kHz", 0f, "0 dB")
        )
    }
}
