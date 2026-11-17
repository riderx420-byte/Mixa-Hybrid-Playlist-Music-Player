package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Custom Media3 RenderersFactory prioritizing 32-bit float PCM output (supporting high-resolution
 * 24-bit / 192 kHz lossless streams without premature 16-bit integer truncation) and asynchronous queueing.
 */
@OptIn(UnstableApi::class)
class SonoraRenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        forceEnableMediaCodecAsynchronousQueueing()
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
