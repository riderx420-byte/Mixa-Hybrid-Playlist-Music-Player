package com.example.data.extensions

import android.content.Context
import android.net.Uri
import com.example.data.local.db.ExtensionRegistryEntity
import com.example.data.local.db.InstalledExtensionEntity
import com.example.data.local.db.SonoraDao
import com.example.domain.model.ExtensionRegistryInfo
import com.example.domain.model.InstalledExtension
import com.example.domain.model.RegistryExtensionEntry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Manages Mornye-style registry feeds and .sflx ZIP archives in app-private storage.
 * Enforces SHA-256 verification and Zip-Slip canonical path checks.
 */
class ExtensionPackageManager(
    private val context: Context,
    private val dao: SonoraDao,
    private val okHttpClient: OkHttpClient,
    private val secretsStore: ExtensionSecretsStore
) {
    private val extensionsRootDir: File
        get() = File(context.filesDir, "sonora_extensions").apply { mkdirs() }

    val registriesFlow: Flow<List<ExtensionRegistryInfo>> = dao.observeExtensionRegistries().map { rows ->
        rows.map { row ->
            ExtensionRegistryInfo(
                url = row.url,
                name = row.name,
                addedAt = row.addedAt,
                lastSyncedAt = row.lastSyncedAt,
                entries = ExtensionManifestParser.decodeRegistryEntries(row.entriesJson)
            )
        }
    }

    val installedExtensionsFlow: Flow<List<InstalledExtension>> = dao.observeInstalledExtensions().map { rows ->
        rows.mapNotNull { it.toDomainOrNull() }
    }

    suspend fun getEnabledExtensions(): List<InstalledExtension> = withContext(Dispatchers.IO) {
        dao.getEnabledExtensionsOnce().mapNotNull { it.toDomainOrNull() }
    }

    suspend fun getInstalledExtension(extensionId: String): InstalledExtension? = withContext(Dispatchers.IO) {
        dao.getInstalledExtensionById(extensionId)?.toDomainOrNull()
    }

    suspend fun addAndSyncRegistry(rawUrl: String): Result<ExtensionRegistryInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = rawUrl.trim()
            require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
                "Registry URL must start with https:// or http://"
            }
            val request = Request.Builder()
                .url(cleanUrl)
                .header("Accept", "application/json")
                .build()
            val bodyText = okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Failed to fetch registry (HTTP ${resp.code})")
                }
                resp.body?.string().orEmpty()
            }
            require(bodyText.isNotBlank()) { "Registry response was empty" }
            val (name, entries) = ExtensionManifestParser.parseRegistry(bodyText, cleanUrl)
            val now = System.currentTimeMillis()
            val existing = dao.getExtensionRegistriesOnce().firstOrNull { it.url == cleanUrl }
            val entity = ExtensionRegistryEntity(
                url = cleanUrl,
                name = name,
                entriesJson = ExtensionManifestParser.encodeRegistryEntries(entries),
                addedAt = existing?.addedAt ?: now,
                lastSyncedAt = now
            )
            dao.upsertExtensionRegistry(entity)
            ExtensionRegistryInfo(
                url = cleanUrl,
                name = name,
                addedAt = entity.addedAt,
                lastSyncedAt = now,
                entries = entries
            )
        }
    }

    suspend fun syncAllRegistries(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.getExtensionRegistriesOnce()
            var synced = 0
            existing.forEach { reg ->
                addAndSyncRegistry(reg.url).onSuccess { synced++ }
            }
            synced
        }
    }

    suspend fun removeRegistry(url: String) = withContext(Dispatchers.IO) {
        dao.deleteExtensionRegistry(url)
    }

    suspend fun installFromRegistryEntry(
        entry: RegistryExtensionEntry,
        registryUrl: String
    ): Result<InstalledExtension> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(entry.downloadUrl).build()
            val bytes = okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Download failed (HTTP ${resp.code})")
                }
                resp.body?.bytes() ?: throw IOException("Empty .sflx package")
            }
            installPackageBytes(
                archiveBytes = bytes,
                expectedSha256 = entry.sha256,
                registryUrl = registryUrl,
                fallbackEntry = entry
            )
        }
    }

    suspend fun installFromLocalUri(uri: Uri): Result<InstalledExtension> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Unable to read selected .sflx file")
            installPackageBytes(
                archiveBytes = bytes,
                expectedSha256 = null,
                registryUrl = null,
                fallbackEntry = null
            )
        }
    }

    suspend fun installPackageBytes(
        archiveBytes: ByteArray,
        expectedSha256: String?,
        registryUrl: String?,
        fallbackEntry: RegistryExtensionEntry?
    ): InstalledExtension {
        val actualSha256 = computeSha256Hex(archiveBytes)
        if (!expectedSha256.isNullOrBlank()) {
            val cleanExpected = expectedSha256.trim().lowercase()
            require(actualSha256 == cleanExpected) {
                "SHA-256 verification failed for .sflx package (expected $cleanExpected, got $actualSha256)"
            }
        }

        val entriesMap = unpackZipInMemory(archiveBytes)
        val manifestRaw = entriesMap["manifest.json"]?.toString(Charsets.UTF_8)
            ?: entriesMap.entries.firstOrNull { it.key.endsWith("/manifest.json") }?.value?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException(".sflx archive is missing manifest.json")

        val parsedManifest = ExtensionManifestParser.parseManifest(
            rawJson = manifestRaw,
            fallbackId = fallbackEntry?.id
        )
        val mergedHosts = (parsedManifest.allowedHosts + fallbackEntry?.allowedHosts.orEmpty())
            .filter { it.isNotBlank() }
            .toSet()
        val manifest = parsedManifest.copy(allowedHosts = mergedHosts)

        val scriptBytes = entriesMap[manifest.entryFile]
            ?: entriesMap.entries.firstOrNull { it.key.endsWith("/${manifest.entryFile}") }?.value
            ?: entriesMap["index.js"]
            ?: throw IllegalArgumentException(".sflx archive is missing entry script (${manifest.entryFile})")

        val targetDir = File(extensionsRootDir, manifest.id)
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        val canonicalTarget = targetDir.canonicalPath
        entriesMap.forEach { (relPath, content) ->
            val destFile = File(targetDir, relPath)
            if (!destFile.canonicalPath.startsWith("$canonicalTarget${File.separator}")) {
                throw SecurityException("Invalid zip entry path: $relPath")
            }
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(content)
        }
        // Guarantee entry file exists cleanly at root of targetDir
        File(targetDir, manifest.entryFile).writeBytes(scriptBytes)

        val entity = InstalledExtensionEntity(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            registryUrl = registryUrl,
            manifestJson = ExtensionManifestParser.encodeManifest(manifest),
            isEnabled = true,
            installedAt = System.currentTimeMillis(),
            sha256 = actualSha256,
            localDirPath = targetDir.absolutePath,
            lastError = null
        )
        dao.upsertInstalledExtension(entity)
        return InstalledExtension(
            id = entity.id,
            name = entity.name,
            version = entity.version,
            registryUrl = entity.registryUrl,
            manifest = manifest,
            isEnabled = entity.isEnabled,
            installedAt = entity.installedAt,
            sha256 = entity.sha256,
            localDirPath = entity.localDirPath,
            lastError = null
        )
    }

    suspend fun setExtensionEnabled(extensionId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setExtensionEnabled(extensionId, enabled)
    }

    suspend fun recordExtensionError(extensionId: String, error: String?) = withContext(Dispatchers.IO) {
        dao.updateExtensionLastError(extensionId, error)
    }

    suspend fun uninstallExtension(extensionId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getInstalledExtensionById(extensionId)
        if (existing != null) {
            runCatching { File(existing.localDirPath).deleteRecursively() }
        }
        runCatching { File(extensionsRootDir, extensionId).deleteRecursively() }
        secretsStore.clearExtensionSettings(extensionId)
        dao.deleteInstalledExtension(extensionId)
    }

    fun readEntryScript(extension: InstalledExtension): String {
        val dir = File(extension.localDirPath)
        val entryFile = File(dir, extension.manifest.entryFile)
        val scriptFile = if (entryFile.exists()) entryFile else File(dir, "index.js")
        if (!scriptFile.exists()) {
            throw IOException("Extension script missing for ${extension.name}")
        }
        return scriptFile.readText(Charsets.UTF_8)
    }

    private fun InstalledExtensionEntity.toDomainOrNull(): InstalledExtension? {
        return runCatching {
            val baseManifest = ExtensionManifestParser.parseManifest(manifestJson, fallbackId = id)
            val diskManifestFile = File(localDirPath, "manifest.json")
            val effectiveManifest = if (baseManifest.allowedHosts.isEmpty() && diskManifestFile.exists()) {
                val diskParsed = runCatching {
                    ExtensionManifestParser.parseManifest(diskManifestFile.readText(Charsets.UTF_8), fallbackId = id)
                }.getOrNull()
                if (diskParsed != null && diskParsed.allowedHosts.isNotEmpty()) {
                    baseManifest.copy(allowedHosts = diskParsed.allowedHosts)
                } else {
                    baseManifest
                }
            } else {
                baseManifest
            }
            InstalledExtension(
                id = id,
                name = name,
                version = version,
                registryUrl = registryUrl,
                manifest = effectiveManifest,
                isEnabled = isEnabled,
                installedAt = installedAt,
                sha256 = sha256,
                localDirPath = localDirPath,
                lastError = lastError
            )
        }.getOrNull()
    }

    private fun unpackZipInMemory(zipBytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = entry.name.replace('\\', '/').trimStart('/')
                    if (!cleanName.contains("..")) {
                        map[cleanName] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    companion object {
        fun computeSha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
