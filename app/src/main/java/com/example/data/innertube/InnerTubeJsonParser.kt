package com.example.data.innertube

import com.example.domain.model.SearchFilter
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object InnerTubeJsonParser {

    fun extractVisitorData(rawJson: String): String? {
        return runCatching {
            val root = JSONObject(rawJson)
            root.optJSONObject("responseContext")
                ?.optString("visitorData")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun parseAccountDisplayName(rawJson: String): String? {
        return runCatching {
            val root = JSONObject(rawJson)
            var foundName: String? = null
            var foundHandle: String? = null
            fun scan(node: Any?) {
                when (node) {
                    is JSONObject -> {
                        if (node.has("activeAccountHeaderRenderer")) {
                            val hdr = node.optJSONObject("activeAccountHeaderRenderer")
                            val name = extractRunsText(hdr?.optJSONObject("accountName"))
                            val handle = extractRunsText(hdr?.optJSONObject("channelHandle"))
                            val email = extractRunsText(hdr?.optJSONObject("email"))
                            if (name.isNotBlank()) foundName = name
                            if (handle.isNotBlank()) foundHandle = handle
                            else if (email.isNotBlank()) foundHandle = email
                            return
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            scan(node.opt(keys.next()))
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) {
                            scan(node.opt(i))
                        }
                    }
                }
            }
            scan(root)
            when {
                !foundName.isNullOrBlank() && !foundHandle.isNullOrBlank() -> "$foundName ($foundHandle)"
                !foundName.isNullOrBlank() -> foundName
                !foundHandle.isNullOrBlank() -> foundHandle
                else -> null
            }
        }.getOrNull()
    }

    fun parseSearchSuggestions(rawJson: String): List<String> {
        val suggestions = linkedSetOf<String>()
        runCatching {
            val root = JSONObject(rawJson)
            val contents = root.optJSONArray("contents") ?: return emptyList()
            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i)
                    ?.optJSONObject("searchSuggestionsSectionRenderer")
                    ?: continue
                val items = section.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val renderer = items.optJSONObject(j)
                        ?.optJSONObject("searchSuggestionRenderer")
                        ?: continue
                    val runsText = extractRunsText(renderer.optJSONObject("suggestion"))
                    if (runsText.isNotBlank()) {
                        suggestions.add(runsText)
                    }
                }
            }
        }
        return suggestions.toList()
    }

    fun parseSearchResults(rawJson: String, filter: SearchFilter): List<Track> {
        val results = mutableListOf<Track>()
        val seenIds = hashSetOf<String>()
        runCatching {
            val root = JSONObject(rawJson)
            collectMusicResponsiveItems(root) { itemObj ->
                val track = parseResponsiveListItem(itemObj, filter)
                if (track != null && seenIds.add(track.id)) {
                    results.add(track)
                }
            }
        }
        return results
    }

    fun parseExploreTracks(rawJson: String): List<Track> {
        val results = mutableListOf<Track>()
        val seenIds = hashSetOf<String>()
        runCatching {
            val root = JSONObject(rawJson)
            collectMusicResponsiveItems(root) { itemObj ->
                val track = parseResponsiveListItem(itemObj, SearchFilter.SONGS)
                if (track != null && seenIds.add(track.id)) {
                    results.add(track)
                }
            }
            if (results.size < 10) {
                collectTwoRowItems(root) { twoRowObj ->
                    val track = parseTwoRowItem(twoRowObj)
                    if (track != null && seenIds.add(track.id)) {
                        results.add(track)
                    }
                }
            }
        }
        return results
    }

    fun parseRadioQueue(rawJson: String): List<Track> {
        val results = mutableListOf<Track>()
        val seenIds = hashSetOf<String>()
        runCatching {
            val root = JSONObject(rawJson)
            collectPlaylistPanelVideos(root) { panelVideo ->
                val videoId = panelVideo.optString("videoId").takeIf { it.isNotBlank() } ?: return@collectPlaylistPanelVideos
                val title = extractRunsText(panelVideo.optJSONObject("title")).ifBlank { "Unknown Track" }
                val longByline = extractRunsList(panelVideo.optJSONObject("longBylineText"))
                val shortByline = extractRunsList(panelVideo.optJSONObject("shortBylineText"))
                val artist = longByline.firstOrNull { !isSeparatorOrMeta(it) }
                    ?: shortByline.firstOrNull { !isSeparatorOrMeta(it) }
                    ?: "YouTube Music"
                val album = longByline.drop(1).firstOrNull { !isSeparatorOrMeta(it) && !looksLikeDuration(it) }.orEmpty()
                val durationText = extractRunsText(panelVideo.optJSONObject("lengthText"))
                val durationMs = parseDurationMs(durationText)
                val artworkUrl = extractBestThumbnail(panelVideo.optJSONObject("thumbnail"))

                val track = Track(
                    id = "yt_$videoId",
                    source = TrackSource.YOUTUBE,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    artworkUri = artworkUrl,
                    contentUri = "${Track.YOUTUBE_VIRTUAL_SCHEME}$videoId",
                    videoId = videoId,
                    audioFormatLabel = "OPUS"
                )
                if (seenIds.add(track.id)) {
                    results.add(track)
                }
            }
        }
        return results
    }

    fun parsePlayabilityStatus(rawJson: String): PlayabilityResult? {
        return runCatching {
            val root = JSONObject(rawJson)
            val ps = root.optJSONObject("playabilityStatus") ?: return null
            val status = ps.optString("status").takeIf { it.isNotBlank() } ?: return null
            val reason = ps.optString("reason").takeIf { it.isNotBlank() }
                ?: ps.optJSONObject("errorScreen")
                    ?.optJSONObject("playerErrorMessageRenderer")
                    ?.optJSONObject("reason")
                    ?.let { extractRunsText(it) }
                    ?.takeIf { it.isNotBlank() }
            PlayabilityResult(status = status, reason = reason)
        }.getOrNull()
    }

    fun parsePlayerStream(
        rawJson: String,
        videoId: String,
        preferOpus: Boolean,
        userAgent: String = InnerTubeClients.ANDROID_VR.userAgent,
        clientLabel: String = "ANDROID_VR"
    ): StreamResolution? {
        return parsePlayerCandidates(
            rawJson = rawJson,
            videoId = videoId,
            preferOpus = preferOpus,
            userAgent = userAgent,
            clientLabel = clientLabel
        ).firstOrNull { !it.isHls }
    }

    fun parsePlayerCandidates(
        rawJson: String,
        videoId: String,
        preferOpus: Boolean,
        userAgent: String = InnerTubeClients.ANDROID_VR.userAgent,
        clientLabel: String = "ANDROID_VR"
    ): List<StreamResolution> {
        return runCatching {
            val root = JSONObject(rawJson)
            val streamingData = root.optJSONObject("streamingData") ?: return emptyList()
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
            val candidates = mutableListOf<StreamResolution>()

            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = fmt.optString("mimeType")
                if (!mimeType.startsWith("audio/")) continue

                val directUrl = fmt.optString("url").takeIf { it.isNotBlank() } ?: continue
                val bitrateBps = fmt.optInt("bitrate", fmt.optInt("averageBitrate", 128000))
                val bitrateKbps = (bitrateBps / 1000).coerceAtLeast(32)
                val codecLabel = when {
                    mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                    mimeType.contains("mp4a", ignoreCase = true) -> "AAC"
                    else -> "AUDIO"
                }
                val expiresAtMs = extractExpireTimestampMs(directUrl)

                candidates.add(
                    StreamResolution(
                        videoId = videoId,
                        streamUrl = directUrl,
                        mimeType = mimeType,
                        codecLabel = codecLabel,
                        bitrateKbps = bitrateKbps,
                        expiresAtMs = expiresAtMs,
                        userAgent = userAgent,
                        isHls = false,
                        clientLabel = clientLabel
                    )
                )
            }

            val sorted = candidates.sortedWith(
                compareByDescending<StreamResolution> {
                    if (preferOpus) it.codecLabel == "OPUS" else it.codecLabel == "AAC"
                }.thenByDescending { it.bitrateKbps }
            ).toMutableList()

            val hlsUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }
            if (hlsUrl != null) {
                sorted.add(
                    StreamResolution(
                        videoId = videoId,
                        streamUrl = hlsUrl,
                        mimeType = "application/x-mpegURL",
                        codecLabel = "HLS",
                        bitrateKbps = 128,
                        expiresAtMs = extractExpireTimestampMs(hlsUrl),
                        userAgent = userAgent,
                        isHls = true,
                        clientLabel = "${clientLabel}_HLS"
                    )
                )
            }

            sorted
        }.getOrDefault(emptyList())
    }

    private fun parseResponsiveListItem(
        renderer: JSONObject,
        filter: SearchFilter
    ): Track? {
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }

        if (videoId.isNullOrBlank()) return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        val title = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractRunsText(it) }
            ?.takeIf { it.isNotBlank() } ?: return null

        val secondaryRuns = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractRunsList(it) }
            .orEmpty()

        val filteredMeta = secondaryRuns.filter { !isSeparatorOrMeta(it) }
        val durationPart = filteredMeta.lastOrNull { looksLikeDuration(it) }
        val durationMs = parseDurationMs(durationPart.orEmpty())

        val descriptiveParts = filteredMeta.filter {
            !looksLikeDuration(it) &&
                !it.equals("Song", ignoreCase = true) &&
                !it.equals("Video", ignoreCase = true) &&
                !it.equals("Album", ignoreCase = true) &&
                !it.equals("Artist", ignoreCase = true) &&
                !it.contains("views", ignoreCase = true) &&
                !it.contains("plays", ignoreCase = true)
        }

        val artist = descriptiveParts.firstOrNull() ?: if (filter == SearchFilter.ARTISTS) title else "YouTube Music"
        val album = descriptiveParts.getOrNull(1).orEmpty()

        val artworkUrl = extractBestThumbnail(
            renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
        )

        return Track(
            id = "yt_$videoId",
            source = TrackSource.YOUTUBE,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUri = artworkUrl,
            contentUri = "${Track.YOUTUBE_VIRTUAL_SCHEME}$videoId",
            videoId = videoId,
            audioFormatLabel = "OPUS"
        )
    }

    private fun parseTwoRowItem(renderer: JSONObject): Track? {
        val videoId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("thumbnailOverlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: return null

        val title = extractRunsText(renderer.optJSONObject("title")).takeIf { it.isNotBlank() } ?: return null
        val subtitle = extractRunsList(renderer.optJSONObject("subtitle"))
            .filter { !isSeparatorOrMeta(it) }
        val artist = subtitle.firstOrNull() ?: "YouTube Music"
        val artworkUrl = extractBestThumbnail(
            renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
        )

        return Track(
            id = "yt_$videoId",
            source = TrackSource.YOUTUBE,
            title = title,
            artist = artist,
            durationMs = 0L,
            artworkUri = artworkUrl,
            contentUri = "${Track.YOUTUBE_VIRTUAL_SCHEME}$videoId",
            videoId = videoId,
            audioFormatLabel = "OPUS"
        )
    }

    private fun collectMusicResponsiveItems(node: Any?, onFound: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                if (node.has("musicResponsiveListItemRenderer")) {
                    node.optJSONObject("musicResponsiveListItemRenderer")?.let(onFound)
                } else {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        collectMusicResponsiveItems(node.opt(key), onFound)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectMusicResponsiveItems(node.opt(i), onFound)
                }
            }
        }
    }

    private fun collectTwoRowItems(node: Any?, onFound: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                if (node.has("musicTwoRowItemRenderer")) {
                    node.optJSONObject("musicTwoRowItemRenderer")?.let(onFound)
                } else {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        collectTwoRowItems(node.opt(keys.next()), onFound)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectTwoRowItems(node.opt(i), onFound)
                }
            }
        }
    }

    private fun collectPlaylistPanelVideos(node: Any?, onFound: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                if (node.has("playlistPanelVideoRenderer")) {
                    node.optJSONObject("playlistPanelVideoRenderer")?.let(onFound)
                } else {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        collectPlaylistPanelVideos(node.opt(keys.next()), onFound)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectPlaylistPanelVideos(node.opt(i), onFound)
                }
            }
        }
    }

    private fun extractRunsText(textObj: JSONObject?): String {
        if (textObj == null) return ""
        val simple = textObj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = textObj.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
    }

    private fun extractRunsList(textObj: JSONObject?): List<String> {
        if (textObj == null) return emptyList()
        val runs = textObj.optJSONArray("runs") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            val part = runs.optJSONObject(i)?.optString("text")?.trim().orEmpty()
            if (part.isNotBlank()) list.add(part)
        }
        return list
    }

    private fun extractBestThumbnail(container: JSONObject?): String? {
        val thumbnails = container?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: container?.optJSONArray("thumbnails")
            ?: return null
        if (thumbnails.length() == 0) return null
        var bestUrl: String? = null
        var bestWidth = -1
        for (i in 0 until thumbnails.length()) {
            val t = thumbnails.optJSONObject(i) ?: continue
            val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
            val width = t.optInt("width", 0)
            if (width >= bestWidth) {
                bestWidth = width
                bestUrl = if (url.startsWith("//")) "https:$url" else url
            }
        }
        return bestUrl
    }

    private fun isSeparatorOrMeta(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed == "•" || trimmed == "·" || trimmed == "&" || trimmed == "," || trimmed == "-"
    }

    private fun looksLikeDuration(text: String): Boolean {
        return Regex("""^\d{1,2}(:\d{2}){1,2}$""").matches(text.trim())
    }

    fun parseDurationMs(durationText: String): Long {
        val parts = durationText.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun extractExpireTimestampMs(url: String): Long {
        val match = Regex("""[?&]expire=(\d+)""").find(url)
        val epochSec = match?.groupValues?.getOrNull(1)?.let {
            runCatching { URLDecoder.decode(it, "UTF-8").toLong() }.getOrNull()
        }
        return if (epochSec != null && epochSec > 0L) {
            epochSec * 1000L
        } else {
            System.currentTimeMillis() + 5 * 3600_000L
        }
    }
}
