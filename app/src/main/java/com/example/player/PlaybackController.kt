package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.innertube.InnerTubeClient
import com.example.data.repository.MusicRepository
import com.example.data.streams.StreamQualityResolver
import com.example.domain.model.AudioQualityTier
import com.example.domain.model.PlaybackState
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.RepeatModeState
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import com.example.domain.model.formatSampleRateKhz
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackController(
    private val context: Context,
    private val repository: MusicRepository,
    private val innerTubeClient: InnerTubeClient,
    private val audioEffectsManager: AudioEffectsManager? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var recoveryJob: Job? = null
    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var radioSeedIds = hashSetOf<String>()

    private val streamErrorHandler = StreamErrorHandler(innerTubeClient)

    @Volatile
    private var preferOpusSetting: Boolean = true

    @Volatile
    private var autoSkipBrokenSetting: Boolean = false

    @Volatile
    private var isYoutubeSignedInSetting: Boolean = false

    @Volatile
    private var preferredStreamQualitySetting: PreferredStreamQuality = PreferredStreamQuality.AUTO_BEST

    @Volatile
    private var directFloatPcmBypassEqSetting: Boolean = false

    @Volatile
    private var isRecoveringStream: Boolean = false

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        scope.launch {
            SonoraPlaybackService.resolvedStreamFlow.collect { res ->
                if (res != null) {
                    val current = _playbackState.value.currentTrack
                    if (current?.videoId == res.videoId) {
                        _playbackState.update {
                            it.copy(
                                activeStreamInfo = "${res.codecLabel} • ${res.bitrateKbps} KBPS • ${res.clientLabel}"
                            )
                        }
                    }
                }
            }
        }
        scope.launch {
            SonoraPlaybackService.resolvedExtensionFlow.collect { res ->
                if (res != null) {
                    _playbackState.update { state ->
                        val active = state.currentTrack
                        if (active?.source == TrackSource.EXTENSION) {
                            val updatedTrack = active.copy(
                                variants = if (res.variants.isNotEmpty()) res.variants else active.variants,
                                activeVariantId = active.activeVariantId ?: res.variants.firstOrNull()?.id,
                                resolvedByExtensionId = res.resolvedByExtensionId ?: active.resolvedByExtensionId
                            )
                            val updatedInfo = buildExtensionStreamBanner(updatedTrack, state.audioTelemetry, res.resolvedByLabel)
                            state.copy(
                                currentTrack = updatedTrack,
                                activeStreamInfo = updatedInfo
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
        scope.launch {
            SonoraPlaybackService.audioTelemetryFlow.collect { telemetry ->
                if (telemetry != null) {
                    _playbackState.update { state ->
                        val activeTrack = state.currentTrack
                        val updatedStreamInfo = when (activeTrack?.source) {
                            TrackSource.DIRECT_STREAM -> buildDirectStreamBanner(activeTrack, telemetry)
                            TrackSource.EXTENSION -> buildExtensionStreamBanner(activeTrack, telemetry, null)
                            else -> state.activeStreamInfo
                        }
                        state.copy(
                            audioTelemetry = telemetry,
                            activeStreamInfo = updatedStreamInfo
                        )
                    }
                }
            }
        }
        scope.launch {
            repository.userSettings.collect { settings ->
                preferOpusSetting = settings.preferOpusStream
                autoSkipBrokenSetting = settings.autoSkipBrokenTracks
                isYoutubeSignedInSetting = settings.isYoutubeSignedIn
                preferredStreamQualitySetting = settings.preferredStreamQuality
                directFloatPcmBypassEqSetting = settings.directFloatPcmBypassEq
                updateDirectPcmBypassState(_playbackState.value.currentTrack)
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, SonoraPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    attachPlayerListener(controller)
                    syncFromPlayer(controller)
                    pendingQueue?.let { (tracks, startIndex) ->
                        pendingQueue = null
                        playTracksInternal(controller, tracks, startIndex)
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun disconnect() {
        progressJob?.cancel()
        recoveryJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, enableAutoRadio: Boolean = false) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val unmeteredWifi = isUnmeteredWifi()
        val resolvedTracks = tracks.map { track ->
            StreamQualityResolver.applyPolicyToTrack(
                track = track,
                isUnmeteredWifi = unmeteredWifi,
                preference = preferredStreamQualitySetting
            )
        }
        val target = resolvedTracks[safeIndex]

        recoveryJob?.cancel()
        isRecoveringStream = false
        streamErrorHandler.resetAll()
        _playbackState.update {
            it.copy(
                isRadioActive = enableAutoRadio && target.source == TrackSource.YOUTUBE,
                radioSeedTitle = if (enableAutoRadio) target.title else null,
                errorMessage = null,
                streamError = null
            )
        }
        radioSeedIds.clear()
        resolvedTracks.mapNotNullTo(radioSeedIds) { it.videoId }

        val controller = mediaController
        if (controller == null) {
            pendingQueue = resolvedTracks to safeIndex
            connect()
            return
        }
        playTracksInternal(controller, resolvedTracks, safeIndex)
    }

    fun switchStreamVariant(variantId: String) {
        val controller = mediaController ?: return
        val currentTrack = _playbackState.value.currentTrack ?: return
        val idx = controller.currentMediaItemIndex
        if (idx !in 0 until controller.mediaItemCount) return

        if (currentTrack.source == TrackSource.YOUTUBE && !currentTrack.videoId.isNullOrBlank()) {
            val useOpus = variantId == "yt_opus"
            val vid = currentTrack.videoId
            val resumePos = controller.currentPosition.coerceAtLeast(0L)
            launchStreamRecovery(
                controller = controller,
                track = currentTrack,
                videoId = vid,
                resumePositionMs = resumePos,
                decision = RecoveryDecision.RetryStream(
                    preferOpus = useOpus,
                    allowHlsFallback = true,
                    purgeVideoCache = true,
                    statusHint = if (useOpus) "Switching to Opus 160 kbps…" else "Switching to AAC / HLS…"
                )
            )
            return
        }

        val targetVariant = currentTrack.variants.firstOrNull { it.id == variantId } ?: return
        val updatedTrack = StreamQualityResolver.applyPolicyToTrack(
            track = currentTrack,
            isUnmeteredWifi = isUnmeteredWifi(),
            preference = preferredStreamQualitySetting,
            explicitVariantId = targetVariant.id
        )
        val resumePos = if (updatedTrack.isLiveStream) 0L else controller.currentPosition.coerceAtLeast(0L)

        recoveryJob?.cancel()
        isRecoveringStream = false
        controller.replaceMediaItem(idx, updatedTrack.toMediaItem())
        controller.seekTo(idx, resumePos)
        controller.prepare()
        controller.play()
        syncFromPlayer(controller)
    }

    fun startRadio(seedTrack: Track) {
        val videoId = seedTrack.videoId ?: return
        _playbackState.update {
            it.copy(
                isRadioActive = true,
                isLoadingRadio = true,
                radioSeedTitle = seedTrack.title,
                errorMessage = null,
                streamError = null
            )
        }
        playTracks(listOf(seedTrack), startIndex = 0, enableAutoRadio = true)
        fetchAndAppendRadioTracks(videoId)
    }

    fun playNext(track: Track) {
        val controller = mediaController
        val resolved = StreamQualityResolver.applyPolicyToTrack(
            track = track,
            isUnmeteredWifi = isUnmeteredWifi(),
            preference = preferredStreamQualitySetting
        )
        if (controller == null || controller.mediaItemCount == 0) {
            playTracks(listOf(resolved), 0)
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1)
            .coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, resolved.toMediaItem())
        syncFromPlayer(controller)
    }

    fun addToQueue(track: Track) {
        val controller = mediaController
        val resolved = StreamQualityResolver.applyPolicyToTrack(
            track = track,
            isUnmeteredWifi = isUnmeteredWifi(),
            preference = preferredStreamQualitySetting
        )
        if (controller == null || controller.mediaItemCount == 0) {
            playTracks(listOf(resolved), 0)
            return
        }
        controller.addMediaItem(resolved.toMediaItem())
        syncFromPlayer(controller)
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            recoveryJob?.cancel()
            isRecoveringStream = false
            _playbackState.update { it.copy(errorMessage = null, streamError = null) }
            controller.seekTo(index, 0L)
            controller.prepare()
            controller.play()
            syncFromPlayer(controller)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in 0 until controller.mediaItemCount && toIndex in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromIndex, toIndex)
            syncFromPlayer(controller)
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
            syncFromPlayer(controller)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            val hasActiveError = _playbackState.value.streamError != null ||
                _playbackState.value.errorMessage != null
            if (hasActiveError) {
                retryCurrentTrack(forceAlternateFormat = false)
                return
            }
            _playbackState.update { it.copy(errorMessage = null, streamError = null) }
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            recoveryJob?.cancel()
            isRecoveringStream = false
            _playbackState.update { it.copy(errorMessage = null, streamError = null) }
            controller.seekToNextMediaItem()
            controller.prepare()
            controller.play()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        recoveryJob?.cancel()
        isRecoveringStream = false
        _playbackState.update { it.copy(errorMessage = null, streamError = null) }
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.prepare()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        if (_playbackState.value.currentTrack?.isLiveStream == true) return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        syncFromPlayer(controller)
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncFromPlayer(controller)
    }

    fun setAutoRadioEnabled(enabled: Boolean) {
        _playbackState.update {
            it.copy(
                isRadioActive = enabled,
                radioSeedTitle = if (enabled) it.currentTrack?.title else null
            )
        }
        if (enabled) {
            checkAndTriggerAutoRadio()
        }
    }

    fun retryCurrentTrack(forceAlternateFormat: Boolean = false) {
        val controller = mediaController ?: return
        val currentTrack = _playbackState.value.currentTrack ?: return
        val resumePos = if (currentTrack.isLiveStream) 0L else _playbackState.value.positionMs.coerceAtLeast(0L)
        val videoId = currentTrack.videoId

        streamErrorHandler.resetAttempts(currentTrack.id)

        if (currentTrack.source == TrackSource.DIRECT_STREAM || currentTrack.source == TrackSource.EXTENSION) {
            val idx = controller.currentMediaItemIndex
            if (forceAlternateFormat) {
                val downgrade = StreamQualityResolver.downgradeVariant(currentTrack)
                if (downgrade != null) {
                    switchStreamVariant(downgrade.id)
                    return
                }
            }
            _playbackState.update {
                it.copy(isBuffering = true, errorMessage = null, streamError = null)
            }
            if (idx in 0 until controller.mediaItemCount) {
                controller.seekTo(idx, resumePos)
            }
            controller.prepare()
            controller.play()
            return
        }

        if (currentTrack.source != TrackSource.YOUTUBE || videoId.isNullOrBlank()) {
            _playbackState.update {
                it.copy(isBuffering = true, errorMessage = null, streamError = null)
            }
            val idx = controller.currentMediaItemIndex
            if (idx in 0 until controller.mediaItemCount) {
                controller.seekTo(idx, resumePos)
            }
            controller.prepare()
            controller.play()
            return
        }

        innerTubeClient.clearFailedUrlsForVideo(videoId)
        val decision = if (forceAlternateFormat) {
            RecoveryDecision.RetryStream(
                preferOpus = !preferOpusSetting,
                allowHlsFallback = true,
                purgeVideoCache = true,
                statusHint = "Switching to AAC / HLS stream…"
            )
        } else {
            RecoveryDecision.RetryStream(
                preferOpus = preferOpusSetting,
                allowHlsFallback = true,
                purgeVideoCache = true,
                statusHint = "Retrying stream connection…"
            )
        }
        launchStreamRecovery(controller, currentTrack, videoId, resumePos, decision)
    }

    fun retryCurrentWithHls() {
        retryCurrentTrack(forceAlternateFormat = true)
    }

    fun clearStreamCache() {
        streamErrorHandler.resetAll()
        innerTubeClient.clearAllStreamCaches()
        repository.clearStreamCache()
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null, streamError = null) }
    }

    private fun playTracksInternal(controller: MediaController, tracks: List<Track>, startIndex: Int) {
        scope.launch {
            repository.cacheTracksMetadata(tracks)
        }
        val mediaItems = tracks.map { it.toMediaItem() }
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
        syncFromPlayer(controller)
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncFromPlayer(controller)
                if (isPlaying) startProgressLoop() else progressJob?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isRecoveringStream = false
                    streamErrorHandler.resetAttempts(_playbackState.value.currentTrack?.id)
                    _playbackState.update { it.copy(errorMessage = null, streamError = null) }
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    progressJob?.cancel()
                }
                syncFromPlayer(controller)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                recoveryJob?.cancel()
                isRecoveringStream = false
                _playbackState.update { it.copy(errorMessage = null, streamError = null) }
                syncFromPlayer(controller)
                mediaItem?.let { item ->
                    Track.fromMediaItem(item)?.let { track ->
                        streamErrorHandler.resetAttempts(track.id)
                        scope.launch { repository.recordTrackPlayed(track) }
                    }
                }
                checkAndTriggerAutoRadio()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncFromPlayer(controller)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncFromPlayer(controller)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                syncFromPlayer(controller)
            }

            override fun onPlayerError(error: PlaybackException) {
                progressJob?.cancel()
                val currentTrack = _playbackState.value.currentTrack
                val savedResumePos = if (currentTrack?.isLiveStream == true) 0L else controller.currentPosition.coerceAtLeast(0L)

                // Automatic tier step-down for multi-variant direct & extension Hi-Res streams
                if (currentTrack?.source == TrackSource.DIRECT_STREAM ||
                    currentTrack?.source == TrackSource.EXTENSION
                ) {
                    val lowerVariant = StreamQualityResolver.downgradeVariant(currentTrack)
                    if (lowerVariant != null) {
                        _playbackState.update {
                            it.copy(
                                isBuffering = true,
                                activeStreamInfo = "Stepping down to ${lowerVariant.codecLabel}…",
                                errorMessage = null,
                                streamError = null
                            )
                        }
                        switchStreamVariant(lowerVariant.id)
                        return
                    }
                }

                val videoId = currentTrack?.videoId
                val decision = streamErrorHandler.evaluateAndPlanRecovery(
                    track = currentTrack,
                    error = error,
                    defaultPreferOpus = preferOpusSetting,
                    isYoutubeSignedIn = isYoutubeSignedInSetting,
                    autoSkipBrokenTracks = autoSkipBrokenSetting,
                    hasNextTrack = controller.hasNextMediaItem()
                )

                when (decision) {
                    is RecoveryDecision.RetryStream -> {
                        if (currentTrack != null && !videoId.isNullOrBlank()) {
                            launchStreamRecovery(
                                controller = controller,
                                track = currentTrack,
                                videoId = videoId,
                                resumePositionMs = savedResumePos,
                                decision = decision
                            )
                        }
                    }

                    is RecoveryDecision.AutoSkipNext -> {
                        isRecoveringStream = false
                        if (controller.hasNextMediaItem()) {
                            _playbackState.update {
                                it.copy(
                                    isBuffering = true,
                                    isPlaying = false,
                                    activeStreamInfo = "Skipped unplayable track",
                                    errorMessage = null,
                                    streamError = null
                                )
                            }
                            controller.seekToNextMediaItem()
                            controller.prepare()
                            controller.play()
                        } else {
                            applyTerminalError(decision.info, savedResumePos)
                        }
                    }

                    is RecoveryDecision.SurfaceError -> {
                        isRecoveringStream = false
                        if (videoId != null) {
                            innerTubeClient.invalidateCachedStream(videoId)
                        }
                        applyTerminalError(decision.info, savedResumePos)
                    }
                }
            }
        })
        if (controller.isPlaying) {
            startProgressLoop()
        }
    }

    private fun launchStreamRecovery(
        controller: MediaController,
        track: Track,
        videoId: String,
        resumePositionMs: Long,
        decision: RecoveryDecision.RetryStream
    ) {
        recoveryJob?.cancel()
        isRecoveringStream = true
        if (decision.purgeVideoCache) {
            innerTubeClient.clearFailedUrlsForVideo(videoId)
        } else {
            innerTubeClient.invalidateCachedStream(videoId)
        }
        _playbackState.update {
            it.copy(
                isBuffering = true,
                isPlaying = false,
                positionMs = resumePositionMs,
                activeStreamInfo = decision.statusHint,
                errorMessage = null,
                streamError = null
            )
        }
        recoveryJob = scope.launch {
            val result = innerTubeClient.resolveStream(
                videoId = videoId,
                preferOpus = decision.preferOpus,
                allowHlsFallback = decision.allowHlsFallback
            )
            result.onSuccess { res ->
                isRecoveringStream = false
                val idx = controller.currentMediaItemIndex
                if (idx in 0 until controller.mediaItemCount) {
                    val baseItem = track.toMediaItem()
                    val codecQuery = if (decision.preferOpus) "opus" else "aac"
                    val updatedItem = if (res.isHls) {
                        baseItem.buildUpon()
                            .setUri(Uri.parse("${Track.YOUTUBE_HLS_SCHEME}$videoId?codec=$codecQuery"))
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                    } else {
                        baseItem.buildUpon()
                            .setUri(Uri.parse("${Track.YOUTUBE_VIRTUAL_SCHEME}$videoId?codec=$codecQuery"))
                            .build()
                    }
                    controller.replaceMediaItem(idx, updatedItem)
                    controller.seekTo(idx, resumePositionMs)
                    controller.prepare()
                    controller.play()
                    _playbackState.update {
                        it.copy(
                            activeStreamInfo = "${res.codecLabel} • ${res.bitrateKbps} KBPS • ${res.clientLabel}",
                            errorMessage = null,
                            streamError = null
                        )
                    }
                }
            }.onFailure { err ->
                isRecoveringStream = false
                val terminalInfo = streamErrorHandler.classifyError(
                    track = track,
                    error = err,
                    attemptCount = 3,
                    isYoutubeSignedIn = isYoutubeSignedInSetting
                )
                if (autoSkipBrokenSetting && controller.hasNextMediaItem()) {
                    _playbackState.update {
                        it.copy(
                            isBuffering = true,
                            isPlaying = false,
                            activeStreamInfo = "Skipped unplayable track",
                            errorMessage = null,
                            streamError = null
                        )
                    }
                    controller.seekToNextMediaItem()
                    controller.prepare()
                    controller.play()
                } else {
                    applyTerminalError(terminalInfo, resumePositionMs)
                }
            }
        }
    }

    private fun applyTerminalError(info: com.example.domain.model.StreamErrorInfo, positionMs: Long) {
        _playbackState.update {
            it.copy(
                isBuffering = false,
                isPlaying = false,
                positionMs = positionMs,
                errorMessage = info.title,
                streamError = info
            )
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController ?: break
                if (!controller.isPlaying || controller.playbackState != Player.STATE_READY) {
                    break
                }
                val duration = controller.duration.takeIf { it > 0L }
                    ?: _playbackState.value.currentTrack?.durationMs
                    ?: 0L
                val safePos = controller.currentPosition.coerceIn(0L, duration.coerceAtLeast(1L))
                _playbackState.update {
                    it.copy(
                        positionMs = safePos,
                        durationMs = duration,
                        bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(safePos)
                    )
                }
                delay(120L)
            }
        }
    }

    private fun syncFromPlayer(controller: MediaController) {
        val count = controller.mediaItemCount
        val queue = (0 until count).mapNotNull { idx ->
            Track.fromMediaItem(controller.getMediaItemAt(idx))
        }
        val currentIdx = controller.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentTrack = if (currentIdx >= 0) queue[currentIdx] else null

        updateDirectPcmBypassState(currentTrack)

        val cachedRes = currentTrack?.videoId?.let { innerTubeClient.getCachedStream(it) }
        val telemetry = SonoraPlaybackService.audioTelemetryFlow.value
        val prevStreamInfo = _playbackState.value.activeStreamInfo

        val streamInfo = when {
            isRecoveringStream && prevStreamInfo.isNotBlank() -> prevStreamInfo
            currentTrack == null -> ""
            currentTrack.source == TrackSource.DIRECT_STREAM -> buildDirectStreamBanner(currentTrack, telemetry)
            currentTrack.source == TrackSource.EXTENSION -> buildExtensionStreamBanner(currentTrack, telemetry, null)
            currentTrack.source == TrackSource.LOCAL -> buildString {
                append("LOCAL • ${currentTrack.audioFormatLabel}")
                if (currentTrack.isLossless && telemetry != null && telemetry.sampleRateHz > 0) {
                    append(" • ${formatSampleRateKhz(telemetry.sampleRateHz)}")
                }
            }
            cachedRes != null -> "${cachedRes.codecLabel} • ${cachedRes.bitrateKbps} KBPS • ${cachedRes.clientLabel}"
            else -> "YT MUSIC • ${currentTrack.audioFormatLabel}"
        }

        val activeTier = currentTrack?.activeVariant?.tier ?: when {
            currentTrack == null -> null
            currentTrack.isHiRes -> AudioQualityTier.HI_RES_LOSSLESS
            currentTrack.isLossless -> AudioQualityTier.CD_LOSSLESS
            else -> AudioQualityTier.HIGH_ADAPTIVE
        }

        val duration = if (currentTrack?.isLiveStream == true) {
            0L
        } else {
            controller.duration.takeIf { it > 0L } ?: currentTrack?.durationMs ?: 0L
        }

        _playbackState.update { prev ->
            prev.copy(
                currentTrack = currentTrack,
                queue = queue,
                currentIndex = currentIdx,
                isPlaying = controller.isPlaying,
                isBuffering = (controller.playbackState == Player.STATE_BUFFERING || isRecoveringStream) &&
                    prev.errorMessage == null && prev.streamError == null,
                positionMs = if (currentTrack?.isLiveStream == true) 0L else controller.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
                    Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
                    else -> RepeatModeState.OFF
                },
                activeStreamInfo = streamInfo,
                audioTelemetry = telemetry ?: prev.audioTelemetry,
                activeQualityTier = activeTier
            )
        }
    }

    private fun buildDirectStreamBanner(
        track: Track,
        telemetry: com.example.domain.model.LiveAudioFormatTelemetry?
    ): String {
        val variant = track.activeVariant
        val codec = telemetry?.codecLabel?.takeIf { it.isNotBlank() } ?: variant?.codecLabel ?: track.audioFormatLabel
        val sr = (telemetry?.sampleRateHz ?: 0).takeIf { it > 0 } ?: variant?.sampleRateHz ?: track.sampleRateHz ?: 0
        val depth = variant?.bitDepth ?: track.bitDepth ?: telemetry?.bitDepth?.takeIf { it > 0 } ?: 0
        val kbps = variant?.bitrateKbps ?: telemetry?.bitrateKbps?.takeIf { it > 0 } ?: track.bitrateKbps

        return buildList {
            add(codec)
            if (depth >= 24 && sr > 0) {
                add("${depth}-BIT / ${formatSampleRateKhz(sr)}")
            } else if (sr > 0) {
                add(formatSampleRateKhz(sr))
            }
            if (kbps != null && kbps > 0) {
                add("$kbps KBPS")
            }
            if (track.isLiveStream) add("LIVE")
        }.joinToString(" • ")
    }

    private fun buildExtensionStreamBanner(
        track: Track,
        telemetry: com.example.domain.model.LiveAudioFormatTelemetry?,
        resolvedByLabel: String?
    ): String {
        val origin = (track.sourceLabel ?: track.extensionId ?: "EXT").uppercase()
        val bridge = resolvedByLabel ?: track.resolvedByExtensionId
        val prefix = if (!bridge.isNullOrBlank() && !bridge.equals(track.extensionId, ignoreCase = true)) {
            "$origin → ${bridge.uppercase()}"
        } else {
            origin
        }
        val variant = track.activeVariant
        val codec = telemetry?.codecLabel?.takeIf { it.isNotBlank() } ?: variant?.codecLabel ?: track.audioFormatLabel
        val sr = (telemetry?.sampleRateHz ?: 0).takeIf { it > 0 } ?: variant?.sampleRateHz ?: track.sampleRateHz ?: 0
        val depth = variant?.bitDepth ?: track.bitDepth ?: telemetry?.bitDepth?.takeIf { it > 0 } ?: 0
        return buildList {
            add(prefix)
            add(codec)
            if (depth >= 24 && sr > 0) {
                add("${depth}-BIT / ${formatSampleRateKhz(sr)}")
            } else if (sr > 0) {
                add(formatSampleRateKhz(sr))
            }
        }.joinToString(" • ")
    }

    private fun updateDirectPcmBypassState(currentTrack: Track?) {
        val shouldBypass = directFloatPcmBypassEqSetting && (currentTrack?.isLossless == true)
        audioEffectsManager?.setBypassedForDirectPcm(shouldBypass)
    }

    private fun isUnmeteredWifi(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return true
            val caps = cm.getNetworkCapabilities(network) ?: return true
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.getOrDefault(true)
    }

    private fun checkAndTriggerAutoRadio() {
        val state = _playbackState.value
        if (!state.isRadioActive || state.isLoadingRadio) return
        val remaining = state.queue.size - 1 - state.currentIndex
        if (remaining <= 2) {
            val seedVideoId = state.queue.lastOrNull { it.videoId != null }?.videoId
                ?: state.currentTrack?.videoId
                ?: return
            fetchAndAppendRadioTracks(seedVideoId)
        }
    }

    private fun fetchAndAppendRadioTracks(videoId: String) {
        _playbackState.update { it.copy(isLoadingRadio = true) }
        scope.launch {
            val result = repository.getRadioQueue(videoId)
            result.onSuccess { fetched ->
                val newTracks = fetched.filter { track ->
                    val vid = track.videoId
                    vid != null && radioSeedIds.add(vid)
                }
                if (newTracks.isNotEmpty()) {
                    repository.cacheTracksMetadata(newTracks)
                    mediaController?.let { controller ->
                        controller.addMediaItems(newTracks.map { it.toMediaItem() })
                        syncFromPlayer(controller)
                    }
                }
            }.onFailure { err ->
                _playbackState.update {
                    it.copy(errorMessage = "Radio queue update failed: ${err.localizedMessage}")
                }
            }
            _playbackState.update { it.copy(isLoadingRadio = false) }
        }
    }
}
