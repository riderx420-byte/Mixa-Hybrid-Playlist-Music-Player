package com.example.player

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.SonoraApplication
import com.example.data.innertube.StreamResolution
import com.example.domain.model.LiveAudioFormatTelemetry
import com.example.domain.model.ResolvedExtensionAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class SonoraPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var deckA: ExoPlayer
    private lateinit var deckB: ExoPlayer
    private lateinit var activePlayer: ExoPlayer
    private lateinit var standbyPlayer: ExoPlayer

    private lateinit var audioAttributes: AudioAttributes
    private lateinit var dataSourceFactory: SonoraDataSourceFactory
    private lateinit var extractorsFactory: DefaultExtractorsFactory
    private lateinit var loadControl: DefaultLoadControl
    private lateinit var renderersFactory: SonoraRenderersFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var crossfadeJob: Job? = null
    private var preCuedSignature: String? = null
    private var isCrossfading: Boolean = false

    @Volatile
    private var preferOpus: Boolean = true

    @Volatile
    private var crossfadeDurationSeconds: Int = 0

    override fun onCreate() {
        super.onCreate()
        val app = application as SonoraApplication

        dataSourceFactory = SonoraDataSourceFactory(
            context = this,
            innerTubeClient = app.innerTubeClient,
            preferOpusProvider = { preferOpus },
            onStreamResolved = { resolution ->
                latestResolvedStream.value = resolution
            },
            extensionResolver = { rawUri ->
                app.isrcCrossMatchRouter.resolveExtensionUriSync(rawUri)
            },
            onExtensionStreamResolved = { resolved ->
                latestResolvedExtension.value = resolved
            }
        )

        extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        // Generous buffer (25s-60s) prevents dropouts on high-bitrate 24-bit/192kHz lossless & YT streams
        loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25_000,
                60_000,
                2_500,
                5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        renderersFactory = SonoraRenderersFactory(this)

        deckA = buildExoPlayer(handleAudioFocus = true)
        deckB = buildExoPlayer(handleAudioFocus = false)
        activePlayer = deckA
        standbyPlayer = deckB

        attachDeckListener(deckA, app)
        attachDeckListener(deckB, app)

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, activePlayer)
            .setSessionActivity(pendingIntent)
            .build()

        serviceScope.launch {
            app.repository.userSettings.collect { settings ->
                preferOpus = settings.preferOpusStream
                val clampedFade = CrossfadeTransitionPolicy.clampCrossfadeSeconds(
                    settings.crossfadeDurationSeconds
                )
                val changed = clampedFade != crossfadeDurationSeconds
                crossfadeDurationSeconds = clampedFade
                applyTransitionModeToActivePlayer()
                if (changed) {
                    if (clampedFade == 0) {
                        abortCrossfadeAndResetStandby()
                    } else if (activePlayer.isPlaying) {
                        ensureMonitorRunning()
                    }
                }
            }
        }
    }

    private fun buildExoPlayer(handleAudioFocus: Boolean): ExoPlayer {
        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun applyTransitionModeToActivePlayer() {
        if (!::activePlayer.isInitialized) return
        activePlayer.pauseAtEndOfMediaItems = CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(
            crossfadeSeconds = crossfadeDurationSeconds,
            repeatMode = activePlayer.repeatMode
        )
    }

    private fun attachDeckListener(deck: ExoPlayer, app: SonoraApplication) {
        deck.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (deck !== activePlayer) return
                app.audioEffectsManager.attachToSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (deck !== activePlayer) return
                if (deck.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    app.audioEffectsManager.attachToSession(deck.audioSessionId)
                }
                publishAudioTelemetry(app.audioEffectsManager.state.value.isBypassedForDirectPcm)
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    monitorJob?.cancel()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (deck !== activePlayer) return
                if (isPlaying && crossfadeDurationSeconds > 0) {
                    ensureMonitorRunning()
                } else if (!isPlaying && !deck.playWhenReady && isCrossfading) {
                    abortCrossfadeAndResetStandby()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (deck !== activePlayer) return
                applyTransitionModeToActivePlayer()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (deck !== activePlayer) return
                if (!isCrossfading) {
                    preCuedSignature = null
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (deck !== activePlayer) return
                preCuedSignature = null
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                ) {
                    if (isCrossfading) {
                        abortCrossfadeAndResetStandby()
                    } else {
                        resetStandbyDeck()
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (deck !== activePlayer) return
                if (reason == Player.DISCONTINUITY_REASON_SEEK && !isCrossfading) {
                    preCuedSignature = null
                    resetStandbyDeck()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (deck !== activePlayer) return
                // ExoPlayer's setPauseAtEndOfMediaItems(true) pauses right on track boundary
                // instead of auto-advancing inside the same deck. If the track ends before a
                // timed crossfade starts (e.g. short track or unknown duration), transition cleanly.
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    !isCrossfading &&
                    deck.hasNextMediaItem()
                ) {
                    deck.seekToNextMediaItem()
                    deck.prepare()
                    deck.play()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (deck !== activePlayer) return
                publishAudioTelemetry(app.audioEffectsManager.state.value.isBypassedForDirectPcm)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (deck !== activePlayer) return
                val icyTitle = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                val existing = latestAudioTelemetry.value
                if (icyTitle != null && existing != null && icyTitle != existing.liveIcyTitle) {
                    latestAudioTelemetry.value = existing.copy(liveIcyTitle = icyTitle)
                }
            }
        })
    }

    private fun ensureMonitorRunning() {
        if (monitorJob?.isActive == true || crossfadeDurationSeconds <= 0) return
        monitorJob = serviceScope.launch {
            while (isActive && crossfadeDurationSeconds > 0) {
                val player = activePlayer
                if (!player.isPlaying) {
                    delay(300L)
                    continue
                }

                if (!isCrossfading && player.repeatMode != Player.REPEAT_MODE_ONE) {
                    val duration = player.duration
                    val position = player.currentPosition
                    val hasNext = player.hasNextMediaItem()
                    val nextIdx = player.nextMediaItemIndex
                    val isLive = player.isCurrentMediaItemLive

                    if (duration != C.TIME_UNSET && duration > 0L && nextIdx != C.INDEX_UNSET) {
                        val remaining = (duration - position).coerceAtLeast(0L)
                        val crossfadeMs = crossfadeDurationSeconds * 1000L

                        if (CrossfadeTransitionPolicy.shouldPreCueNextDeck(
                                remainingMs = remaining,
                                durationMs = duration,
                                crossfadeMs = crossfadeMs,
                                hasNextItem = hasNext,
                                isLiveStream = isLive
                            )
                        ) {
                            preCueStandbyDeck(player, nextIdx)
                        }

                        if (preCuedSignature != null &&
                            CrossfadeTransitionPolicy.shouldStartCrossfade(
                                remainingMs = remaining,
                                durationMs = duration,
                                crossfadeMs = crossfadeMs,
                                hasNextItem = hasNext,
                                isLiveStream = isLive
                            )
                        ) {
                            val effectiveMs = remaining.coerceIn(300L, crossfadeMs)
                            startCrossfadeTransition(
                                outgoing = player,
                                incoming = standbyPlayer,
                                durationMs = effectiveMs
                            )
                            break
                        }
                    }
                }
                delay(180L)
            }
        }
    }

    private fun preCueStandbyDeck(source: ExoPlayer, nextIndex: Int) {
        val count = source.mediaItemCount
        if (nextIndex !in 0 until count) return
        val items = (0 until count).map { source.getMediaItemAt(it) }
        val signature = "$count:$nextIndex:${items[nextIndex].mediaId}"
        if (preCuedSignature == signature) return

        standbyPlayer.stop()
        standbyPlayer.setAudioAttributes(audioAttributes, false)
        standbyPlayer.pauseAtEndOfMediaItems = true
        standbyPlayer.shuffleModeEnabled = source.shuffleModeEnabled
        standbyPlayer.repeatMode = source.repeatMode
        standbyPlayer.volume = 0f
        standbyPlayer.playWhenReady = false
        standbyPlayer.setMediaItems(items, nextIndex, 0L)
        standbyPlayer.prepare()
        preCuedSignature = signature
    }

    private fun startCrossfadeTransition(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        durationMs: Long
    ) {
        if (isCrossfading) return
        val app = application as SonoraApplication
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            isCrossfading = true
            try {
                // Ensure outgoing holds at track boundary instead of snapping into next item
                outgoing.pauseAtEndOfMediaItems = true
                incoming.volume = 0f
                incoming.playWhenReady = true
                incoming.play()

                // Briefly allow buffered startup if pre-cued remote stream is still finishing headers
                var waitMs = 0L
                while (incoming.playbackState == Player.STATE_BUFFERING && waitMs < 900L && isActive) {
                    delay(40L)
                    waitMs += 40L
                }

                // Hand over MediaSession + AudioFocus cleanly to incoming deck
                outgoing.setAudioAttributes(audioAttributes, false)
                incoming.setAudioAttributes(audioAttributes, true)
                incoming.pauseAtEndOfMediaItems = CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(
                    crossfadeSeconds = crossfadeDurationSeconds,
                    repeatMode = incoming.repeatMode
                )

                activePlayer = incoming
                standbyPlayer = outgoing
                mediaSession?.player = incoming

                if (incoming.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    app.audioEffectsManager.attachToSession(incoming.audioSessionId)
                }
                publishAudioTelemetry(app.audioEffectsManager.state.value.isBypassedForDirectPcm)

                val startTime = SystemClock.elapsedRealtime()
                while (isActive) {
                    if (activePlayer !== incoming || !incoming.playWhenReady || crossfadeDurationSeconds == 0) {
                        break
                    }
                    val elapsed = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
                    val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    val volumes = CrossfadeTransitionPolicy.computeEqualPowerVolumes(progress)
                    outgoing.volume = volumes.outgoing
                    incoming.volume = volumes.incoming
                    if (progress >= 1f) break
                    delay(33L)
                }
            } finally {
                if (activePlayer === incoming) {
                    incoming.volume = 1f
                }
                outgoing.pause()
                outgoing.stop()
                outgoing.clearMediaItems()
                outgoing.volume = 1f
                isCrossfading = false
                preCuedSignature = null
                if (activePlayer.isPlaying && crossfadeDurationSeconds > 0) {
                    ensureMonitorRunning()
                }
            }
        }
    }

    private fun abortCrossfadeAndResetStandby() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossfading = false
        preCuedSignature = null
        if (::activePlayer.isInitialized) {
            activePlayer.volume = 1f
            activePlayer.setAudioAttributes(audioAttributes, true)
        }
        resetStandbyDeck()
    }

    private fun resetStandbyDeck() {
        preCuedSignature = null
        if (!::standbyPlayer.isInitialized) return
        standbyPlayer.pause()
        standbyPlayer.stop()
        standbyPlayer.clearMediaItems()
        standbyPlayer.volume = 1f
        standbyPlayer.setAudioAttributes(audioAttributes, false)
    }

    private fun publishAudioTelemetry(isBypassingDsp: Boolean) {
        if (!::activePlayer.isInitialized) return
        val fmt: Format = activePlayer.audioFormat ?: return
        val mime = fmt.sampleMimeType.orEmpty().lowercase()
        val codec = when {
            mime.contains("flac") -> "FLAC"
            mime.contains("alac") -> "ALAC"
            mime.contains("opus") -> "OPUS"
            mime.contains("vorbis") -> "VORBIS"
            mime.contains("mp4a") || mime.contains("aac") -> "AAC"
            mime.contains("mpeg") -> "MP3"
            mime.contains("raw") -> "PCM"
            else -> "AUDIO"
        }
        val bitDepth = when (fmt.pcmEncoding) {
            C.ENCODING_PCM_FLOAT -> 32
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            else -> if (codec == "FLAC" || codec == "ALAC") 24 else 16
        }
        latestAudioTelemetry.value = LiveAudioFormatTelemetry(
            codecLabel = codec,
            sampleRateHz = fmt.sampleRate.takeIf { it > 0 } ?: 44_100,
            bitDepth = bitDepth,
            bitrateKbps = fmt.bitrate.takeIf { it > 0 }?.div(1000) ?: 0,
            channelCount = fmt.channelCount.takeIf { it > 0 } ?: 2,
            isFloatPcmOutput = true,
            isBypassingDsp = isBypassingDsp,
            liveIcyTitle = latestAudioTelemetry.value?.liveIcyTitle
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        crossfadeJob?.cancel()
        serviceScope.cancel()
        val app = application as? SonoraApplication
        app?.audioEffectsManager?.releaseEffects()
        mediaSession?.release()
        mediaSession = null
        if (::deckA.isInitialized) deckA.release()
        if (::deckB.isInitialized) deckB.release()
        super.onDestroy()
    }

    companion object {
        private val latestResolvedStream = MutableStateFlow<StreamResolution?>(null)
        val resolvedStreamFlow: StateFlow<StreamResolution?> = latestResolvedStream.asStateFlow()

        private val latestAudioTelemetry = MutableStateFlow<LiveAudioFormatTelemetry?>(null)
        val audioTelemetryFlow: StateFlow<LiveAudioFormatTelemetry?> = latestAudioTelemetry.asStateFlow()

        private val latestResolvedExtension = MutableStateFlow<ResolvedExtensionAudio?>(null)
        val resolvedExtensionFlow: StateFlow<ResolvedExtensionAudio?> = latestResolvedExtension.asStateFlow()
    }
}
