package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ExtensionCapability(val key: String) {
    SEARCH("search"),
    STREAM("stream"),
    METADATA_ONLY("metadata_only"),
    ISRC_MATCH("isrc_match"),
    ALBUMS("albums"),
    PLAYLISTS("playlists"),
    LYRICS("lyrics");

    companion object {
        fun parse(raw: String): ExtensionCapability? {
            val normalized = raw.trim().lowercase().replace("-", "_")
            return entries.firstOrNull { it.key == normalized }
        }
    }
}

enum class ExtensionSettingType {
    TEXT,
    SECRET,
    BOOLEAN,
    SELECT
}

@Serializable
data class ExtensionSettingField(
    val key: String,
    val label: String,
    val type: ExtensionSettingType = ExtensionSettingType.TEXT,
    val defaultValue: String = "",
    val description: String = "",
    val options: List<String> = emptyList()
)

@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "Community",
    val description: String = "",
    val minAppVersion: String = "1.0",
    val iconUrl: String? = null,
    val entryFile: String = "index.js",
    val capabilities: Set<ExtensionCapability> = setOf(ExtensionCapability.SEARCH, ExtensionCapability.STREAM),
    val allowedHosts: Set<String> = emptySet(),
    val settingsSchema: List<ExtensionSettingField> = emptyList(),
    val isMetadataOnly: Boolean = false,
    val priority: Int = 50
) {
    val canStreamAudio: Boolean
        get() = !isMetadataOnly &&
            (ExtensionCapability.STREAM in capabilities || ExtensionCapability.METADATA_ONLY !in capabilities)
}

@Serializable
data class RegistryExtensionEntry(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "Community",
    val description: String = "",
    val category: String = "Music",
    val tags: List<String> = emptyList(),
    val iconUrl: String? = null,
    val downloadUrl: String,
    val sha256: String? = null,
    val minAppVersion: String = "1.0",
    val capabilities: Set<ExtensionCapability> = emptySet(),
    val allowedHosts: Set<String> = emptySet()
)

data class ExtensionRegistryInfo(
    val url: String,
    val name: String,
    val addedAt: Long,
    val lastSyncedAt: Long,
    val entries: List<RegistryExtensionEntry>
)

data class InstalledExtension(
    val id: String,
    val name: String,
    val version: String,
    val registryUrl: String?,
    val manifest: ExtensionManifest,
    val isEnabled: Boolean,
    val installedAt: Long,
    val sha256: String?,
    val localDirPath: String,
    val lastError: String? = null
)

@Serializable
data class BlowfishChunkSpec(
    val keyHex: String,
    val chunkSizeBytes: Int = 2048,
    val encryptedChunkModulo: Int = 3
)

data class ResolvedExtensionAudio(
    val streamUrl: String,
    val variants: List<StreamVariant> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val decryptionSpec: BlowfishChunkSpec? = null,
    val resolvedByExtensionId: String? = null,
    val resolvedByLabel: String? = null,
    val mimeTypeHint: String? = null,
    val isYouTubeFallback: Boolean = false,
    val fallbackVideoId: String? = null
)

sealed class ExploreSource(val id: String, val displayName: String) {
    data object YouTubeMusic : ExploreSource(id = "youtube_music", displayName = "YouTube Music")
    data class ExtensionProvider(
        val extensionId: String,
        val name: String,
        val isMetadataOnly: Boolean,
        val tags: List<String> = emptyList()
    ) : ExploreSource(id = extensionId, displayName = name)
}
