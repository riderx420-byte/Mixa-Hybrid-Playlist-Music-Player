package com.example.data.extensions

import com.example.domain.model.AudioQualityTier
import com.example.domain.model.BlowfishChunkSpec
import com.example.domain.model.ExtensionCapability
import com.example.domain.model.ExtensionManifest
import com.example.domain.model.ExtensionSettingField
import com.example.domain.model.ExtensionSettingType
import com.example.domain.model.InstalledExtension
import com.example.domain.model.RegistryExtensionEntry
import com.example.domain.model.ResolvedExtensionAudio
import com.example.domain.model.StreamVariant
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

object ExtensionManifestParser {

    fun parseRegistry(
        rawJson: String,
        registryUrl: String
    ): Pair<String, List<RegistryExtensionEntry>> {
        val trimmed = rawJson.trim()
        var registryName = inferRegistryName(registryUrl)
        val array: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val obj = JSONObject(trimmed)
            registryName = obj.optString("name").takeIf { it.isNotBlank() } ?: registryName
            obj.optJSONArray("extensions")
                ?: obj.optJSONArray("plugins")
                ?: obj.optJSONArray("items")
                ?: obj.optJSONArray("packages")
                ?: JSONArray()
        }

        val entries = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { item.optString("slug") }.trim()
                val rawDownload = item.optString("downloadUrl")
                    .ifBlank { item.optString("download_url") }
                    .ifBlank { item.optString("url") }
                    .ifBlank { item.optString("packageUrl") }
                    .ifBlank { item.optString("package_url") }
                    .trim()
                if (id.isBlank() || rawDownload.isBlank()) continue

                val resolvedDownload = resolveRelativeUrl(registryUrl, rawDownload)
                val caps = parseCapabilities(item.optJSONArray("capabilities"))
                    .ifEmpty { parseCapabilities(item.optJSONArray("tags")) }
                val hosts = parseStringSet(
                    item.optJSONArray("allowedHosts")
                        ?: item.optJSONArray("allowed_hosts")
                        ?: item.optJSONArray("domains")
                        ?: item.optJSONArray("hosts")
                )
                val tags = parseStringList(item.optJSONArray("tags"))
                add(
                    RegistryExtensionEntry(
                        id = sanitizeExtensionId(id),
                        name = item.optString("name").ifBlank { id },
                        version = item.optString("version").ifBlank { "1.0.0" },
                        author = item.optString("author").ifBlank { "Community" },
                        description = item.optString("description"),
                        category = item.optString("category").ifBlank { "Music" },
                        tags = tags,
                        iconUrl = item.optString("iconUrl")
                            .ifBlank { item.optString("icon_url") }
                            .ifBlank { item.optString("icon") }
                            .takeIf { it.isNotBlank() }
                            ?.let { resolveRelativeUrl(registryUrl, it) },
                        downloadUrl = resolvedDownload,
                        sha256 = item.optString("sha256")
                            .ifBlank { item.optString("checksum") }
                            .ifBlank { item.optString("hash") }
                            .trim()
                            .takeIf { it.isNotBlank() },
                        minAppVersion = item.optString("minAppVersion")
                            .ifBlank { item.optString("min_app_version") }
                            .ifBlank { "1.0" },
                        capabilities = caps,
                        allowedHosts = hosts
                    )
                )
            }
        }
        return registryName to entries
    }

    fun parseManifest(rawJson: String, fallbackId: String? = null): ExtensionManifest {
        val obj = JSONObject(rawJson)
        val rawId = obj.optString("id").ifBlank { fallbackId.orEmpty() }
        val id = sanitizeExtensionId(rawId.ifBlank { "ext_${System.currentTimeMillis()}" })
        val name = obj.optString("name").ifBlank { id }
        val caps = parseCapabilities(obj.optJSONArray("capabilities")).toMutableSet()
        val metadataOnly = obj.optBoolean("isMetadataOnly", obj.optBoolean("metadata_only", false)) ||
            obj.optString("type").equals("metadata", ignoreCase = true) ||
            ExtensionCapability.METADATA_ONLY in caps
        if (metadataOnly) {
            caps += ExtensionCapability.METADATA_ONLY
        }
        if (caps.isEmpty()) {
            caps += ExtensionCapability.SEARCH
            if (!metadataOnly) caps += ExtensionCapability.STREAM
        }

        val permObj = obj.optJSONObject("permissions")
        val allowedHosts = (parseStringSet(
            obj.optJSONArray("allowedHosts")
                ?: obj.optJSONArray("allowed_hosts")
                ?: obj.optJSONArray("domains")
                ?: obj.optJSONArray("hosts")
                ?: permObj?.optJSONArray("network")
                ?: permObj?.optJSONArray("domains")
                ?: permObj?.optJSONArray("hosts")
        ) + parseStringSet(obj.optJSONArray("network"))).toSet()

        val settingsArray = obj.optJSONArray("settingsSchema")
            ?: obj.optJSONArray("settings_schema")
            ?: obj.optJSONArray("settings")
            ?: obj.optJSONArray("config")
            ?: JSONArray()

        val settingsSchema = buildList {
            for (i in 0 until settingsArray.length()) {
                val sObj = settingsArray.optJSONObject(i) ?: continue
                val key = sObj.optString("key").ifBlank { sObj.optString("id") }.trim()
                if (key.isBlank()) continue
                val typeRaw = sObj.optString("type").trim().lowercase()
                val type = when {
                    typeRaw == "secret" || typeRaw == "password" || typeRaw == "token" || sObj.optBoolean("secret", false) ->
                        ExtensionSettingType.SECRET
                    typeRaw == "boolean" || typeRaw == "bool" || typeRaw == "toggle" || typeRaw == "switch" ->
                        ExtensionSettingType.BOOLEAN
                    typeRaw == "select" || typeRaw == "enum" || typeRaw == "dropdown" ->
                        ExtensionSettingType.SELECT
                    else -> ExtensionSettingType.TEXT
                }
                add(
                    ExtensionSettingField(
                        key = key,
                        label = sObj.optString("label").ifBlank { sObj.optString("title") }.ifBlank { key },
                        type = type,
                        defaultValue = sObj.optString("defaultValue")
                            .ifBlank { sObj.optString("default") },
                        description = sObj.optString("description"),
                        options = parseStringList(sObj.optJSONArray("options"))
                    )
                )
            }
        }

        val defaultPriority = when {
            metadataOnly -> 10
            id.contains("qobuz", ignoreCase = true) || name.contains("qobuz", ignoreCase = true) -> 90
            id.contains("deezer", ignoreCase = true) || name.contains("deezer", ignoreCase = true) -> 80
            else -> 50
        }

        return ExtensionManifest(
            id = id,
            name = name,
            version = obj.optString("version").ifBlank { "1.0.0" },
            author = obj.optString("author").ifBlank { "Community" },
            description = obj.optString("description"),
            minAppVersion = obj.optString("minAppVersion")
                .ifBlank { obj.optString("min_app_version") }
                .ifBlank { "1.0" },
            iconUrl = obj.optString("iconUrl")
                .ifBlank { obj.optString("icon_url") }
                .takeIf { it.isNotBlank() },
            entryFile = obj.optString("entryFile")
                .ifBlank { obj.optString("entry") }
                .ifBlank { obj.optString("main") }
                .ifBlank { "index.js" },
            capabilities = caps,
            allowedHosts = allowedHosts,
            settingsSchema = settingsSchema,
            isMetadataOnly = metadataOnly,
            priority = obj.optInt("priority", defaultPriority)
        )
    }

    fun encodeRegistryEntries(entries: List<RegistryExtensionEntry>): String {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("version", entry.version)
                    put("author", entry.author)
                    put("description", entry.description)
                    put("category", entry.category)
                    put("tags", JSONArray(entry.tags))
                    put("iconUrl", entry.iconUrl)
                    put("downloadUrl", entry.downloadUrl)
                    put("sha256", entry.sha256)
                    put("minAppVersion", entry.minAppVersion)
                    put("capabilities", JSONArray(entry.capabilities.map { it.key }))
                    put("allowedHosts", JSONArray(entry.allowedHosts.toList()))
                }
            )
        }
        return arr.toString()
    }

    fun decodeRegistryEntries(rawJson: String): List<RegistryExtensionEntry> {
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val downloadUrl = item.optString("downloadUrl")
                    if (id.isBlank() || downloadUrl.isBlank()) continue
                    add(
                        RegistryExtensionEntry(
                            id = id,
                            name = item.optString("name").ifBlank { id },
                            version = item.optString("version").ifBlank { "1.0.0" },
                            author = item.optString("author").ifBlank { "Community" },
                            description = item.optString("description"),
                            category = item.optString("category").ifBlank { "Music" },
                            tags = parseStringList(item.optJSONArray("tags")),
                            iconUrl = item.optString("iconUrl").takeIf { it.isNotBlank() },
                            downloadUrl = downloadUrl,
                            sha256 = item.optString("sha256").takeIf { it.isNotBlank() },
                            minAppVersion = item.optString("minAppVersion").ifBlank { "1.0" },
                            capabilities = parseCapabilities(item.optJSONArray("capabilities")),
                            allowedHosts = parseStringSet(item.optJSONArray("allowedHosts"))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeManifest(manifest: ExtensionManifest): String {
        return JSONObject().apply {
            put("id", manifest.id)
            put("name", manifest.name)
            put("version", manifest.version)
            put("author", manifest.author)
            put("description", manifest.description)
            put("minAppVersion", manifest.minAppVersion)
            put("iconUrl", manifest.iconUrl)
            put("entryFile", manifest.entryFile)
            put("capabilities", JSONArray(manifest.capabilities.map { it.key }))
            put("allowedHosts", JSONArray(manifest.allowedHosts.toList()))
            put("isMetadataOnly", manifest.isMetadataOnly)
            put("priority", manifest.priority)
            val schemaArr = JSONArray()
            manifest.settingsSchema.forEach { field ->
                schemaArr.put(
                    JSONObject().apply {
                        put("key", field.key)
                        put("label", field.label)
                        put("type", field.type.name.lowercase())
                        put("defaultValue", field.defaultValue)
                        put("description", field.description)
                        put("options", JSONArray(field.options))
                    }
                )
            }
            put("settingsSchema", schemaArr)
        }.toString()
    }

    fun parseTracksFromJsResult(
        rawJson: String,
        extension: InstalledExtension
    ): List<Track> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || trimmed == "null" || trimmed == "undefined") return emptyList()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                val sections = obj.optJSONArray("sections") ?: obj.optJSONArray("shelves")
                if (sections != null && sections.length() > 0) {
                    val flattened = JSONArray()
                    for (i in 0 until sections.length()) {
                        val sec = sections.optJSONObject(i) ?: continue
                        val secItems = sec.optJSONArray("items")
                            ?: sec.optJSONArray("tracks")
                            ?: sec.optJSONArray("results")
                            ?: continue
                        for (j in 0 until secItems.length()) {
                            secItems.optJSONObject(j)?.let { flattened.put(it) }
                        }
                    }
                    if (flattened.length() > 0) flattened else extractPrimaryArray(obj)
                } else {
                    extractPrimaryArray(obj)
                }
            }
            else -> return emptyList()
        }

        return buildList {
            for (i in 0 until array.length()) {
                val rawItem = array.optJSONObject(i) ?: continue
                val unwrapped = rawItem.optJSONObject("track") ?: rawItem
                parseSingleTrack(unwrapped, extension)?.let { add(it) }
            }
        }
    }

    private fun extractPrimaryArray(obj: JSONObject): JSONArray {
        return obj.optJSONArray("tracks")
            ?: obj.optJSONArray("items")
            ?: obj.optJSONArray("results")
            ?: obj.optJSONArray("songs")
            ?: obj.optJSONArray("data")
            ?: obj.optJSONArray("albums")
            ?: obj.optJSONArray("artists")
            ?: JSONArray().apply { put(obj) }
    }

    fun parseSingleTrack(obj: JSONObject, extension: InstalledExtension): Track? {
        val attrs = obj.optJSONObject("attributes")
        val providerId = obj.optString("id")
            .ifBlank { obj.optString("trackId") }
            .ifBlank { obj.optString("track_id") }
            .ifBlank { obj.optString("apple_music_id") }
            .ifBlank { obj.optString("qobuz_id") }
            .ifBlank { obj.optString("deezer_id") }
            .ifBlank { obj.optString("spotify_id") }
            .trim()
        val title = obj.optString("title")
            .ifBlank { obj.optString("name") }
            .ifBlank { obj.optString("track_name") }
            .ifBlank { attrs?.optString("name").orEmpty() }
            .trim()
        if (title.isBlank()) return null
        val effectiveProviderId = providerId.ifBlank { "item_${title.hashCode()}" }

        val artist = obj.optString("artist")
            .ifBlank { obj.optString("artists") }
            .ifBlank { obj.optString("artistName") }
            .ifBlank { obj.optString("artist_name") }
            .ifBlank { obj.optString("performer") }
            .ifBlank { attrs?.optString("artistName").orEmpty() }
            .ifBlank { extractJoinedArtists(obj.optJSONArray("artists")) }
            .ifBlank { "Unknown Artist" }
        val album = obj.optString("album")
            .ifBlank { obj.optString("albumTitle") }
            .ifBlank { obj.optString("albumName") }
            .ifBlank { obj.optString("album_name") }
            .ifBlank { attrs?.optString("albumName").orEmpty() }

        val durationRaw = obj.optLong("durationMs", -1L)
        val durationMs = when {
            durationRaw > 0L -> durationRaw
            obj.optLong("duration_ms", -1L) > 0L -> obj.optLong("duration_ms")
            (attrs?.optLong("durationInMillis", -1L) ?: -1L) > 0L -> attrs!!.optLong("durationInMillis")
            obj.optLong("duration", -1L) > 0L -> {
                val sec = obj.optLong("duration")
                if (sec > 10_000L) sec else sec * 1000L
            }
            else -> 0L
        }

        val rawArtwork = obj.optString("artworkUrl")
            .ifBlank { obj.optString("artworkUri") }
            .ifBlank { obj.optString("artwork_url") }
            .ifBlank { obj.optString("coverUrl") }
            .ifBlank { obj.optString("cover_url") }
            .ifBlank { obj.optString("imageUrl") }
            .ifBlank { obj.optString("image_url") }
            .ifBlank { obj.optString("cover") }
            .ifBlank { obj.optString("thumbnail") }
            .ifBlank { obj.optJSONObject("artwork")?.optString("url").orEmpty() }
            .ifBlank { attrs?.optJSONObject("artwork")?.optString("url").orEmpty() }
        val artwork = rawArtwork
            .takeIf { it.isNotBlank() }
            ?.replace("{w}", "512")
            ?.replace("{h}", "512")

        val isrc = obj.optString("isrc")
            .ifBlank { attrs?.optString("isrc").orEmpty() }
            .ifBlank { obj.optJSONObject("externalIds")?.optString("isrc").orEmpty() }
            .ifBlank { obj.optJSONObject("external_ids")?.optString("isrc").orEmpty() }
            .trim()
            .takeIf { it.isNotBlank() }

        val sampleRateHz = obj.optInt("sampleRateHz", obj.optInt("sample_rate", 0)).takeIf { it > 0 }
        val bitDepth = obj.optInt("bitDepth", obj.optInt("bit_depth", 0)).takeIf { it > 0 }
        val bitrateKbps = obj.optInt("bitrateKbps", obj.optInt("bitrate", 0)).takeIf { it > 0 }
        val codecLabel = obj.optString("codec")
            .ifBlank { obj.optString("format") }
            .ifBlank {
                when {
                    extension.manifest.isMetadataOnly -> "ISRC"
                    (bitDepth ?: 0) >= 24 || (sampleRateHz ?: 0) > 48_000 -> "FLAC"
                    else -> "EXT"
                }
            }
            .uppercase()

        val metadataOnly = obj.optBoolean("isMetadataOnly", extension.manifest.isMetadataOnly)
        val directStreamUrl = obj.optString("streamUrl")
            .ifBlank { obj.optString("downloadUrl") }
            .ifBlank { obj.optString("download_url") }
            .ifBlank { obj.optString("url") }
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val variants = parseVariantsArray(obj.optJSONArray("variants"), directStreamUrl, codecLabel, bitrateKbps, sampleRateHz, bitDepth)
        val virtualUri = Track.buildExtensionVirtualUri(
            extensionId = extension.id,
            providerTrackId = effectiveProviderId,
            isrc = isrc,
            title = title,
            artist = artist
        )

        return Track(
            id = "ext_${extension.id}_$effectiveProviderId",
            source = TrackSource.EXTENSION,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUri = artwork,
            contentUri = virtualUri,
            audioFormatLabel = codecLabel,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            variants = variants,
            activeVariantId = variants.firstOrNull()?.id,
            extensionId = extension.id,
            providerTrackId = effectiveProviderId,
            isrc = isrc,
            isMetadataOnly = metadataOnly,
            sourceLabel = extension.name
        )
    }

    private fun extractJoinedArtists(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val str = arr.optString(i).takeIf { it.isNotBlank() && !it.startsWith("{") }
            if (str != null) {
                names += str
            } else {
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { names += it }
            }
        }
        return names.joinToString(", ")
    }

    fun parseResolvedAudioFromJsResult(
        rawJson: String,
        extension: InstalledExtension
    ): ResolvedExtensionAudio? {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || trimmed == "null" || trimmed == "undefined") return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val variant = StreamVariant(
                id = "${extension.id}_direct",
                label = "${extension.name} Stream",
                url = trimmed,
                codecLabel = inferCodecFromUrl(trimmed),
                tier = AudioQualityTier.CD_LOSSLESS
            )
            return ResolvedExtensionAudio(
                streamUrl = trimmed,
                variants = listOf(variant),
                resolvedByExtensionId = extension.id,
                resolvedByLabel = extension.name
            )
        }

        val rawObj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val obj = rawObj.optJSONObject("data") ?: rawObj
        val directUrl = obj.optString("url")
            .ifBlank { obj.optString("streamUrl") }
            .ifBlank { obj.optString("stream_url") }
            .ifBlank { obj.optString("downloadUrl") }
            .ifBlank { obj.optString("download_url") }
            .ifBlank { obj.optString("directUrl") }
            .ifBlank { obj.optString("direct_url") }
            .ifBlank { obj.optString("fileUrl") }
            .ifBlank { obj.optString("file_url") }
            .trim()
        val headersObj = obj.optJSONObject("headers")
        val headers = buildMap {
            headersObj?.keys()?.forEach { key ->
                val value = headersObj.optString(key)
                if (value.isNotBlank()) put(key, value)
            }
        }
        val decObj = obj.optJSONObject("decryption") ?: obj.optJSONObject("blowfish")
        val blowfishSpec = parseBlowfishSpec(
            decObj = decObj,
            fallbackKeyHex = obj.optString("blowfishKey")
                .ifBlank { obj.optString("blowfish_key") }
                .ifBlank { obj.optString("keyHex") }
        )

        val variants = parseVariantsArray(
            array = obj.optJSONArray("variants") ?: obj.optJSONArray("qualities") ?: obj.optJSONArray("streams"),
            fallbackUrl = directUrl.takeIf { it.isNotBlank() },
            fallbackCodec = obj.optString("codec").ifBlank { obj.optString("format") }.ifBlank { inferCodecFromUrl(directUrl) },
            fallbackBitrate = obj.optInt("bitrateKbps", obj.optInt("bitrate", 0)).takeIf { it > 0 },
            fallbackSr = obj.optInt("sampleRateHz", obj.optInt("sample_rate", 0)).takeIf { it > 0 },
            fallbackBitDepth = obj.optInt("bitDepth", obj.optInt("bit_depth", 0)).takeIf { it > 0 },
            defaultHeaders = headers,
            defaultBlowfish = blowfishSpec
        )

        val effectiveUrl = directUrl.ifBlank { variants.firstOrNull()?.url.orEmpty() }
        if (!effectiveUrl.startsWith("http://") && !effectiveUrl.startsWith("https://")) {
            return null
        }

        val chosenSpec = variants.firstOrNull { it.url == effectiveUrl }?.decryptionSpec ?: blowfishSpec
        val chosenHeaders = variants.firstOrNull { it.url == effectiveUrl }?.headers?.takeIf { it.isNotEmpty() } ?: headers

        return ResolvedExtensionAudio(
            streamUrl = effectiveUrl,
            variants = variants,
            headers = chosenHeaders,
            decryptionSpec = chosenSpec,
            resolvedByExtensionId = extension.id,
            resolvedByLabel = extension.name,
            mimeTypeHint = obj.optString("mimeType").takeIf { it.isNotBlank() }
        )
    }

    private fun parseVariantsArray(
        array: JSONArray?,
        fallbackUrl: String?,
        fallbackCodec: String,
        fallbackBitrate: Int?,
        fallbackSr: Int?,
        fallbackBitDepth: Int?,
        defaultHeaders: Map<String, String> = emptyMap(),
        defaultBlowfish: BlowfishChunkSpec? = null
    ): List<StreamVariant> {
        val list = mutableListOf<StreamVariant>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url").ifBlank { item.optString("streamUrl") }.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                val codec = item.optString("codec")
                    .ifBlank { item.optString("format") }
                    .ifBlank { inferCodecFromUrl(url) }
                    .uppercase()
                val sr = item.optInt("sampleRateHz", item.optInt("sample_rate", fallbackSr ?: 0)).takeIf { it > 0 }
                val bd = item.optInt("bitDepth", item.optInt("bit_depth", fallbackBitDepth ?: 0)).takeIf { it > 0 }
                val br = item.optInt("bitrateKbps", item.optInt("bitrate", fallbackBitrate ?: 0)).takeIf { it > 0 }
                val tier = inferTier(codec, sr, bd, br)
                val spec = parseBlowfishSpec(
                    decObj = item.optJSONObject("decryption") ?: item.optJSONObject("blowfish"),
                    fallbackKeyHex = item.optString("blowfishKey")
                ) ?: defaultBlowfish
                val hdrs = buildMap {
                    putAll(defaultHeaders)
                    val hObj = item.optJSONObject("headers")
                    hObj?.keys()?.forEach { k ->
                        val v = hObj.optString(k)
                        if (v.isNotBlank()) put(k, v)
                    }
                }
                list += StreamVariant(
                    id = item.optString("id").ifBlank { "ext_v_$i" },
                    label = item.optString("label").ifBlank { "$codec${bd?.let { " ${it}-bit" } ?: ""}" },
                    url = url,
                    codecLabel = codec,
                    bitrateKbps = br,
                    sampleRateHz = sr,
                    bitDepth = bd,
                    tier = tier,
                    headers = hdrs,
                    decryptionSpec = spec
                )
            }
        }
        if (list.isEmpty() && !fallbackUrl.isNullOrBlank()) {
            val tier = inferTier(fallbackCodec, fallbackSr, fallbackBitDepth, fallbackBitrate)
            list += StreamVariant(
                id = "ext_primary",
                label = buildString {
                    append(fallbackCodec.uppercase())
                    if (fallbackBitDepth != null && fallbackSr != null) {
                        append(" ${fallbackBitDepth}-bit")
                    }
                },
                url = fallbackUrl,
                codecLabel = fallbackCodec.uppercase(),
                bitrateKbps = fallbackBitrate,
                sampleRateHz = fallbackSr,
                bitDepth = fallbackBitDepth,
                tier = tier,
                headers = defaultHeaders,
                decryptionSpec = defaultBlowfish
            )
        }
        return list
    }

    private fun parseBlowfishSpec(decObj: JSONObject?, fallbackKeyHex: String): BlowfishChunkSpec? {
        val keyHex = (decObj?.optString("keyHex")
            ?.ifBlank { decObj.optString("key") }
            ?.ifBlank { fallbackKeyHex }
            ?: fallbackKeyHex).trim()
        if (keyHex.length != 32) return null
        val chunkSize = decObj?.optInt("chunkSizeBytes", 2048)?.takeIf { it > 0 } ?: 2048
        val modulo = decObj?.optInt("modulo", 3)?.takeIf { it > 0 } ?: 3
        return BlowfishChunkSpec(
            keyHex = keyHex,
            chunkSizeBytes = chunkSize,
            encryptedChunkModulo = modulo
        )
    }

    private fun inferTier(
        codec: String,
        sampleRateHz: Int?,
        bitDepth: Int?,
        bitrateKbps: Int?
    ): AudioQualityTier {
        val upper = codec.uppercase()
        return when {
            (bitDepth ?: 0) >= 24 || (sampleRateHz ?: 0) > 48_000 -> AudioQualityTier.HI_RES_LOSSLESS
            upper.contains("FLAC") || upper.contains("ALAC") || upper.contains("WAV") -> AudioQualityTier.CD_LOSSLESS
            (bitrateKbps ?: 0) in 1..140 -> AudioQualityTier.DATA_SAVER
            else -> AudioQualityTier.HIGH_ADAPTIVE
        }
    }

    private fun inferCodecFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".flac") || lower.contains("format=flac") -> "FLAC"
            lower.contains(".alac") || lower.contains(".m4a") -> "ALAC"
            lower.contains(".wav") -> "WAV"
            lower.contains(".mp3") -> "MP3"
            else -> "AUDIO"
        }
    }

    private fun parseCapabilities(array: JSONArray?): Set<ExtensionCapability> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                ExtensionCapability.parse(array.optString(i))?.let { add(it) }
            }
        }
    }

    private fun parseStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val v = array.optString(i).trim().lowercase()
                if (v.isNotBlank()) add(v)
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val v = array.optString(i).trim()
                if (v.isNotBlank()) add(v)
            }
        }
    }

    fun sanitizeExtensionId(raw: String): String {
        return raw.lowercase().replace(Regex("[^a-z0-9._-]"), "_").trim('_').ifBlank { "ext" }
    }

    private fun resolveRelativeUrl(baseUrl: String, target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        return runCatching { URI(baseUrl).resolve(target).toString() }.getOrDefault(target)
    }

    private fun inferRegistryName(url: String): String {
        val parsedHost = runCatching { URI(url).host }.getOrNull()
        return when {
            url.contains("mornye", ignoreCase = true) -> "Mornye Extension Registry"
            !parsedHost.isNullOrBlank() -> parsedHost
            else -> "Custom Registry"
        }
    }
}
