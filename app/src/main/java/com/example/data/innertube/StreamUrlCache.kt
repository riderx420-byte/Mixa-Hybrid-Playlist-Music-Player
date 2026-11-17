package com.example.data.innertube

import java.util.concurrent.ConcurrentHashMap

class StreamUrlCache {
    private val cache = ConcurrentHashMap<String, StreamResolution>()

    fun getValid(videoId: String): StreamResolution? {
        val entry = cache[videoId] ?: return null
        return if (entry.isValid()) {
            entry
        } else {
            cache.remove(videoId)
            null
        }
    }

    fun getValidByUrl(url: String): StreamResolution? {
        val entry = cache.values.firstOrNull { it.streamUrl == url } ?: return null
        return if (entry.isValid()) entry else null
    }

    fun put(resolution: StreamResolution) {
        cache[resolution.videoId] = resolution
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    fun clear() {
        cache.clear()
    }
}
