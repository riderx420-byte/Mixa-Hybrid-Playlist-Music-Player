package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.innertube.InnerTubeAuthHelper
import com.example.data.innertube.InnerTubeClient
import com.example.data.innertube.InnerTubeClients
import com.example.data.innertube.PlayabilityResult
import com.example.data.innertube.StreamExtractionException
import com.example.data.local.SettingsPreferences
import com.example.data.local.db.CachedTrackEntity
import com.example.data.local.db.PlaylistEntity
import com.example.data.local.db.PlaylistTrackCrossRef
import com.example.data.local.db.SonoraDatabase
import com.example.domain.model.PlayerBackdropStyle
import com.example.domain.model.StreamErrorCategory
import com.example.domain.model.ThemeAccent
import com.example.domain.model.ThemeCornerStyle
import com.example.domain.model.ThemeMode
import com.example.domain.model.Track
import com.example.domain.model.TrackSource
import com.example.player.RecoveryDecision
import com.example.player.StreamErrorHandler
import java.net.UnknownHostException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraUnifiedEngineTest {

    private lateinit var database: SonoraDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `unified Track to MediaItem roundtrip preserves local and youtube metadata`() {
        val localTrack = Track(
            id = "local_42",
            source = TrackSource.LOCAL,
            title = "Lossless Echoes",
            artist = "Studio Band",
            album = "Master Tape",
            durationMs = 215_000L,
            artworkUri = "content://media/external/audio/albumart/9",
            contentUri = "content://media/external/audio/media/42",
            audioFormatLabel = "FLAC",
            folderName = "HiRes"
        )
        val ytTrack = Track(
            id = "yt_dQw4w9WgXcQ",
            source = TrackSource.YOUTUBE,
            title = "Streamed Pulse",
            artist = "Electronic Act",
            durationMs = 192_000L,
            artworkUri = "https://lh3.googleusercontent.com/cover.jpg",
            contentUri = "sonora://yt/dQw4w9WgXcQ",
            videoId = "dQw4w9WgXcQ",
            audioFormatLabel = "OPUS"
        )

        val restoredLocal = Track.fromMediaItem(localTrack.toMediaItem())
        val restoredYt = Track.fromMediaItem(ytTrack.toMediaItem())

        assertNotNull(restoredLocal)
        assertEquals(TrackSource.LOCAL, restoredLocal!!.source)
        assertEquals("FLAC", restoredLocal.audioFormatLabel)
        assertEquals("content://media/external/audio/media/42", restoredLocal.playbackUriString)

        assertNotNull(restoredYt)
        assertEquals(TrackSource.YOUTUBE, restoredYt!!.source)
        assertEquals("dQw4w9WgXcQ", restoredYt.videoId)
        assertEquals("sonora://yt/dQw4w9WgXcQ", restoredYt.playbackUriString)
    }

    @Test
    fun `hybrid playlist mixes local and youtube tracks in Room DB`() = runTest {
        val dao = database.sonoraDao()

        val local = Track(
            id = "local_1",
            source = TrackSource.LOCAL,
            title = "Offline Vinyl",
            artist = "Local Artist",
            contentUri = "content://media/external/audio/media/1",
            audioFormatLabel = "FLAC"
        )
        val yt = Track(
            id = "yt_stream99",
            source = TrackSource.YOUTUBE,
            title = "Cloud Synth",
            artist = "YT Artist",
            contentUri = "sonora://yt/stream99",
            videoId = "stream99",
            audioFormatLabel = "OPUS"
        )

        dao.upsertTracks(listOf(CachedTrackEntity.fromDomain(local), CachedTrackEntity.fromDomain(yt)))
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Roadtrip Hybrid"))
        dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(playlistId, local.id, position = 0))
        dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(playlistId, yt.id, position = 1))

        val loaded = dao.observePlaylistWithTracks(playlistId).first()
        val orderedIds = dao.observeOrderedPlaylistTrackIds(playlistId).first()
        val domain = loaded!!.toDomain(orderedIds)

        assertEquals(2, domain.trackCount)
        assertEquals(1, domain.localCount)
        assertEquals(1, domain.youtubeCount)
        assertEquals("local_1", domain.tracks[0].id)
        assertEquals("yt_stream99", domain.tracks[1].id)
    }

    @Test
    fun `stream url client tag maps to exact InnerTube User-Agent`() {
        val iosSegment = "https://rr2---sn-npoe7ne6.googlevideo.com/videoplayback/id/123/c/IOS/itag/234"
        val vrProgressive = "https://rr4---sn-a5mekn7k.googlevideo.com/videoplayback?expire=9999999&c=ANDROID_VR&itag=251"
        val tvStream = "https://rr1---sn.googlevideo.com/videoplayback?c=TVHTML5_SIMPLY_EMBEDDED_PLAYER&itag=140"

        assertEquals(InnerTubeClients.IOS.userAgent, InnerTubeClients.userAgentForStreamUrl(iosSegment))
        assertEquals(InnerTubeClients.ANDROID_VR_161.userAgent, InnerTubeClients.userAgentForStreamUrl(vrProgressive))
        assertEquals(InnerTubeClients.TVHTML5_EMBED.userAgent, InnerTubeClients.userAgentForStreamUrl(tvStream))
    }

    @Test
    fun `theme customization preferences persist across DataStore updates`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        prefs.setThemeMode(ThemeMode.AMOLED)
        prefs.setThemeAccent(ThemeAccent.CYAN_PULSE)
        prefs.setUseDynamicColor(false)
        prefs.setCornerStyle(ThemeCornerStyle.PILL)
        prefs.setPlayerBackdropStyle(PlayerBackdropStyle.ARTWORK_BLUR)

        val saved = prefs.settingsFlow.first()
        assertEquals(ThemeMode.AMOLED, saved.themeMode)
        assertEquals(ThemeAccent.CYAN_PULSE, saved.themeAccent)
        assertEquals(false, saved.useDynamicColor)
        assertEquals(ThemeCornerStyle.PILL, saved.cornerStyle)
        assertEquals(PlayerBackdropStyle.ARTWORK_BLUR, saved.playerBackdropStyle)
    }

    @Test
    fun `sapisidhash header and cookie persistence work for YouTube Music login`() = runTest {
        val raw = "Cookie: SID=abc123; __Secure-3PAPISID=xyz_secret_987\n"
        val sanitized = InnerTubeAuthHelper.sanitizeCookie(raw)
        assertEquals("SID=abc123; __Secure-3PAPISID=xyz_secret_987", sanitized)
        assertTrue(InnerTubeAuthHelper.hasValidSessionCookie(sanitized))
        assertFalse(InnerTubeAuthHelper.hasValidSessionCookie("VISITOR_INFO1_LIVE=only_guest"))

        val authHeader = InnerTubeAuthHelper.buildAuthorizationHeader(
            cookie = sanitized,
            origin = "https://music.youtube.com",
            timestampSeconds = 1700000000L
        )
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("SAPISIDHASH 1700000000_"))
        assertEquals(40, authHeader.substringAfter("_").length)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.saveYouTubeAccount(sanitized, "Premium Listener")
        val signedIn = prefs.settingsFlow.first()
        assertTrue(signedIn.isYoutubeSignedIn)
        assertEquals("Premium Listener", signedIn.ytAccountLabel)
        assertEquals(sanitized, signedIn.ytMusicCookie)

        prefs.clearYouTubeAccount()
        val signedOut = prefs.settingsFlow.first()
        assertFalse(signedOut.isYoutubeSignedIn)
        assertNull(signedOut.ytAccountLabel)
        assertNull(signedOut.ytMusicCookie)
    }

    @Test
    fun `full white theme mode, monochrome accent, and client-scoped stream cookie rules work`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        prefs.setThemeMode(ThemeMode.PURE_WHITE)
        prefs.setThemeAccent(ThemeAccent.MONOCHROME)
        val saved = prefs.settingsFlow.first()
        assertEquals(ThemeMode.PURE_WHITE, saved.themeMode)
        assertEquals(ThemeAccent.MONOCHROME, saved.themeAccent)
        assertEquals("Full Black", ThemeMode.AMOLED.label)
        assertEquals("Full White", ThemeMode.PURE_WHITE.label)

        val client = com.example.data.innertube.InnerTubeClient()
        assertTrue(
            client.shouldAttachCookieForStream(
                "https://rr1---sn.googlevideo.com/videoplayback?c=ANDROID_MUSIC&itag=251"
            )
        )
        assertFalse(
            client.shouldAttachCookieForStream(
                "https://rr1---sn.googlevideo.com/videoplayback?c=ANDROID_VR&itag=251"
            )
        )
        assertFalse(
            client.shouldAttachCookieForStream(
                "https://rr1---sn.googlevideo.com/videoplayback?c=IOS&itag=140"
            )
        )
    }

    @Test
    fun `stream error handler executes 3-step recovery ladder and honors auto-skip setting`() {
        val client = InnerTubeClient()
        val handler = StreamErrorHandler(client)
        val ytTrack = Track(
            id = "yt_Gayw5AQvWcQ",
            source = TrackSource.YOUTUBE,
            title = "Shararat",
            artist = "Shashwat Sachdev",
            videoId = "Gayw5AQvWcQ"
        )
        val http403Ex = StreamExtractionException(
            videoId = "Gayw5AQvWcQ",
            httpStatus = 403,
            playability = null,
            triedClients = listOf("ANDROID_VR_161", "TVHTML5_EMBED", "NEWPIPE"),
            message = "Unable to extract playable audio stream for Gayw5AQvWcQ (HTTP 403)"
        )

        val step1 = handler.evaluateAndPlanRecovery(
            track = ytTrack,
            error = http403Ex,
            defaultPreferOpus = true,
            isYoutubeSignedIn = false,
            autoSkipBrokenTracks = false,
            hasNextTrack = true
        )
        assertTrue(step1 is RecoveryDecision.RetryStream)
        assertEquals(false, (step1 as RecoveryDecision.RetryStream).preferOpus)
        assertEquals(false, step1.allowHlsFallback)

        val step2 = handler.evaluateAndPlanRecovery(
            track = ytTrack,
            error = http403Ex,
            defaultPreferOpus = true,
            isYoutubeSignedIn = false,
            autoSkipBrokenTracks = false,
            hasNextTrack = true
        )
        assertTrue(step2 is RecoveryDecision.RetryStream)
        assertTrue((step2 as RecoveryDecision.RetryStream).allowHlsFallback)

        val step3 = handler.evaluateAndPlanRecovery(
            track = ytTrack,
            error = http403Ex,
            defaultPreferOpus = true,
            isYoutubeSignedIn = false,
            autoSkipBrokenTracks = false,
            hasNextTrack = true
        )
        assertTrue(step3 is RecoveryDecision.RetryStream)
        assertTrue((step3 as RecoveryDecision.RetryStream).purgeVideoCache)

        val step4Surface = handler.evaluateAndPlanRecovery(
            track = ytTrack,
            error = http403Ex,
            defaultPreferOpus = true,
            isYoutubeSignedIn = false,
            autoSkipBrokenTracks = false,
            hasNextTrack = true
        )
        assertTrue(step4Surface is RecoveryDecision.SurfaceError)
        val surfaced = (step4Surface as RecoveryDecision.SurfaceError).info
        assertEquals(StreamErrorCategory.HTTP_403_FORBIDDEN, surfaced.category)
        assertEquals(403, surfaced.httpStatus)
        assertTrue(surfaced.suggestsSignIn)
        assertTrue(surfaced.canSwitchFormat)

        val step5AutoSkip = handler.evaluateAndPlanRecovery(
            track = ytTrack,
            error = http403Ex,
            defaultPreferOpus = true,
            isYoutubeSignedIn = true,
            autoSkipBrokenTracks = true,
            hasNextTrack = true
        )
        assertTrue(step5AutoSkip is RecoveryDecision.AutoSkipNext)
    }

    @Test
    fun `stream error handler classifies network, login-required, and web-origin stream URLs`() {
        val handler = StreamErrorHandler(InnerTubeClient())
        val ytTrack = Track(
            id = "yt_abc123",
            source = TrackSource.YOUTUBE,
            title = "Test Track",
            artist = "Artist",
            videoId = "abc123"
        )

        val netInfo = handler.classifyError(
            track = ytTrack,
            error = UnknownHostException("Unable to resolve host"),
            attemptCount = 1,
            isYoutubeSignedIn = false
        )
        assertEquals(StreamErrorCategory.NETWORK_UNAVAILABLE, netInfo.category)

        val loginInfo = handler.classifyError(
            track = ytTrack,
            error = StreamExtractionException(
                videoId = "abc123",
                httpStatus = 0,
                playability = PlayabilityResult("LOGIN_REQUIRED", "Sign in to confirm your age"),
                triedClients = listOf("ANDROID_VR_161"),
                message = "Sign in to confirm your age"
            ),
            attemptCount = 1,
            isYoutubeSignedIn = false
        )
        assertEquals(StreamErrorCategory.RESTRICTED_OR_LOGIN, loginInfo.category)
        assertTrue(loginInfo.suggestsSignIn)

        assertTrue(InnerTubeClients.isWebOriginStreamUrl("https://rr1---sn.googlevideo.com/videoplayback?c=TVHTML5_SIMPLY_EMBEDDED_PLAYER&itag=140"))
        assertTrue(InnerTubeClients.isWebOriginStreamUrl("https://rr1---sn.googlevideo.com/videoplayback?c=WEB_EMBEDDED_PLAYER&itag=251"))
        assertFalse(InnerTubeClients.isWebOriginStreamUrl("https://rr1---sn.googlevideo.com/videoplayback?c=ANDROID_VR&itag=251"))
        assertEquals(
            "https://www.youtube.com",
            InnerTubeClients.buildContextJson(InnerTubeClients.TVHTML5_EMBED)
                .getJSONObject("thirdParty")
                .getString("embedUrl")
        )
    }

    @Test
    fun `equalizer snap helper detects dB ticks and center-snap haptic detent`() {
        // Adjusting away from 0 dB triggers a step tick
        val stepAway = com.example.ui.components.EqSnapHelper.evaluateAdjustment(
            previousLevel = 0f,
            rawNewLevel = 0.40f
        )
        assertEquals(0.40f, stepAway.snappedLevel, 0.001f)
        assertTrue(stepAway.steppedTick)
        assertFalse(stepAway.snappedToCenter)

        // Returning within the center deadzone snaps cleanly to 0f and triggers center snap haptic
        val returnToCenter = com.example.ui.components.EqSnapHelper.evaluateAdjustment(
            previousLevel = 0.25f,
            rawNewLevel = 0.04f
        )
        assertEquals(0f, returnToCenter.snappedLevel, 0.0001f)
        assertEquals(0, returnToCenter.discreteDb)
        assertTrue(returnToCenter.snappedToCenter)
        assertFalse(returnToCenter.steppedTick)

        // Micro-jitter while already at 0 dB stays locked at 0f without spamming haptics
        val holdCenter = com.example.ui.components.EqSnapHelper.evaluateAdjustment(
            previousLevel = 0f,
            rawNewLevel = -0.03f
        )
        assertEquals(0f, holdCenter.snappedLevel, 0.0001f)
        assertFalse(holdCenter.snappedToCenter)
        assertFalse(holdCenter.steppedTick)

        // Column & vertical drag coordinate mapping
        assertEquals(0, com.example.ui.components.xOffsetToBandIndex(20f, 500f, 5))
        assertEquals(2, com.example.ui.components.xOffsetToBandIndex(250f, 500f, 5))
        assertEquals(4, com.example.ui.components.xOffsetToBandIndex(490f, 500f, 5))
        assertEquals(1f, com.example.ui.components.yOffsetToNormalized(16f, 200f, 16f), 0.001f)
        assertEquals(0f, com.example.ui.components.yOffsetToNormalized(100f, 200f, 16f), 0.001f)
        assertEquals(-1f, com.example.ui.components.yOffsetToNormalized(184f, 200f, 16f), 0.001f)
    }

    @Test
    fun `sub bass haptic spec escalates amplitude duration and harshness monotonically`() {
        val low = com.example.ui.components.EqSnapHelper.evaluateBassHaptic(0, 100)
        val mid = com.example.ui.components.EqSnapHelper.evaluateBassHaptic(375, 450)
        val harsh = com.example.ui.components.EqSnapHelper.evaluateBassHaptic(700, 750)
        val maxPunch = com.example.ui.components.EqSnapHelper.evaluateBassHaptic(900, 950)
        val sameBucket = com.example.ui.components.EqSnapHelper.evaluateBassHaptic(405, 420)

        assertTrue(low.shouldTrigger)
        assertTrue(mid.shouldTrigger)
        assertTrue(harsh.shouldTrigger)
        assertTrue(maxPunch.shouldTrigger)
        assertFalse(sameBucket.shouldTrigger)

        assertFalse(low.isHarshZone)
        assertFalse(mid.isHarshZone)
        assertTrue(harsh.isHarshZone)
        assertFalse(harsh.isMaxPunch)
        assertTrue(maxPunch.isHarshZone)
        assertTrue(maxPunch.isMaxPunch)

        assertTrue(low.amplitude < mid.amplitude)
        assertTrue(mid.amplitude < harsh.amplitude)
        assertTrue(harsh.amplitude < maxPunch.amplitude)
        assertTrue(low.durationMs <= mid.durationMs)
        assertTrue(mid.durationMs <= harsh.durationMs)
        assertTrue(harsh.durationMs <= maxPunch.durationMs)
    }

    @Test
    fun `waveform scrubber generates 45 deterministic bars and maps drag offsets cleanly`() {
        val barsA = com.example.ui.components.generateWaveformBars("yt_Gayw5AQvWcQ")
        val barsA2 = com.example.ui.components.generateWaveformBars("yt_Gayw5AQvWcQ")
        val barsB = com.example.ui.components.generateWaveformBars("yt_otherTrack99")

        assertEquals(45, barsA.size)
        assertEquals(barsA, barsA2)
        assertTrue(barsA != barsB)
        assertTrue(barsA.all { it in 0.18f..1.0f })

        assertEquals(0f, com.example.ui.components.waveformXToProgress(-10f, 450f), 0.001f)
        assertEquals(0.5f, com.example.ui.components.waveformXToProgress(225f, 450f), 0.001f)
        assertEquals(1f, com.example.ui.components.waveformXToProgress(500f, 450f), 0.001f)
    }

    @Test
    fun `artwork mesh color extractor isolates warm crimson and gold dominant clusters`() {
        val warmRed = android.graphics.Color.rgb(210, 38, 42)
        val warmGold = android.graphics.Color.rgb(232, 168, 44)
        val darkBorder = android.graphics.Color.rgb(12, 12, 14)

        val pixels = IntArray(120) { idx ->
            when {
                idx < 60 -> warmRed
                idx < 100 -> warmGold
                else -> darkBorder
            }
        }
        val mesh = com.example.ui.components.extractMeshColorsFromPixels(
            pixels = pixels,
            fallbackPrimary = androidx.compose.ui.graphics.Color.Cyan,
            fallbackSecondary = androidx.compose.ui.graphics.Color.Magenta
        )

        // Primary glow should lean warm red (R strongly dominates B)
        assertTrue(mesh.primaryGlow.red > 0.65f)
        assertTrue(mesh.primaryGlow.blue < 0.30f)
        // Secondary glow should capture warm golden highlight (high R + elevated G)
        assertTrue(mesh.secondaryGlow.red > 0.65f)
        assertTrue(mesh.secondaryGlow.green > 0.45f)

        val emptyFallback = com.example.ui.components.extractMeshColorsFromPixels(
            pixels = IntArray(0),
            fallbackPrimary = androidx.compose.ui.graphics.Color.Cyan,
            fallbackSecondary = androidx.compose.ui.graphics.Color.Magenta
        )
        assertEquals(androidx.compose.ui.graphics.Color.Cyan, emptyFallback.primaryGlow)
        assertEquals(androidx.compose.ui.graphics.Color.Magenta, emptyFallback.secondaryGlow)
    }

    @Test
    fun `direct stream prober parses FLAC 24-bit 192kHz and 96kHz headers plus WAV PCM headers`() {
        // Construct fLaC + STREAMINFO (sampleRate=192000, channels=2, bps=24)
        // 192000 = 0x2EE00 (20 bits): b0 = 0x2E, b1 = 0xE0, b2 upper nibble = 0x0
        // channels=2 -> encoded as 1 (bits 3..1 = 0b001 -> 0x02)
        // bps=24 -> encoded as 23 = 0b10111 -> bit0 of b2 = 1, upper nibble of b3 = 0b0111 (0x70)
        val flac192 = ByteArray(42)
        flac192[0] = 'f'.code.toByte()
        flac192[1] = 'L'.code.toByte()
        flac192[2] = 'a'.code.toByte()
        flac192[3] = 'C'.code.toByte()
        flac192[4] = 0x00 // STREAMINFO
        flac192[18] = 0x2E.toByte()
        flac192[19] = 0xE0.toByte()
        flac192[20] = 0x03.toByte() // sr low=0, ch-1=1 (0x02), bps high=1 (0x01)
        flac192[21] = 0x70.toByte() // bps low = 0b0111 << 4

        val parsedFlac = com.example.data.streams.DirectStreamProber.parseFlacStreamInfo(flac192)
        assertNotNull(parsedFlac)
        assertEquals("FLAC", parsedFlac!!.codecLabel)
        assertEquals(192_000, parsedFlac.sampleRateHz)
        assertEquals(24, parsedFlac.bitDepth)
        assertEquals(2, parsedFlac.channelCount)

        // Construct minimal RIFF WAVE fmt header: IEEE Float 32-bit / 96000 Hz stereo
        val wav96 = ByteArray(44)
        "RIFF".toByteArray().copyInto(wav96, 0)
        "WAVE".toByteArray().copyInto(wav96, 8)
        "fmt ".toByteArray().copyInto(wav96, 12)
        wav96[16] = 16 // fmt chunk size
        wav96[20] = 3  // audioFormat = 3 (IEEE Float PCM)
        wav96[22] = 2  // channels = 2
        // sampleRate = 96000 = 0x00017700 LE -> 0x00, 0x77, 0x01, 0x00
        wav96[24] = 0x00.toByte()
        wav96[25] = 0x77.toByte()
        wav96[26] = 0x01.toByte()
        wav96[27] = 0x00.toByte()
        wav96[34] = 32 // 32 bits per sample

        val parsedWav = com.example.data.streams.DirectStreamProber.parseWavHeader(wav96)
        assertNotNull(parsedWav)
        assertEquals("PCM FLOAT", parsedWav!!.codecLabel)
        assertEquals(96_000, parsedWav.sampleRateHz)
        assertEquals(32, parsedWav.bitDepth)
        assertEquals(2, parsedWav.channelCount)

        // PLS & M3U entry extraction
        val plsBody = "[playlist]\nNumberOfEntries=2\nFile1=https://stream.radioparadise.com/flacm\nTitle1=RP\nFile2=http://backup/aac\n"
        assertEquals(
            listOf("https://stream.radioparadise.com/flacm", "http://backup/aac"),
            com.example.data.streams.DirectStreamProber.parsePlsEntries(plsBody)
        )
        val m3uBody = "#EXTM3U\n#EXTINF:-1,Hi-Res\nhttps://example.org/master.flac\n"
        assertEquals(
            listOf("https://example.org/master.flac"),
            com.example.data.streams.DirectStreamProber.parseM3uEntries(m3uBody)
        )
    }

    @Test
    fun `stream quality resolver chooses Wi-Fi Hi-Res vs metered adaptive and supports downgrade`() {
        val station = com.example.data.streams.HiResStreamCatalog.curatedHiResStations
            .first { it.id == "stream_jb_radio2_hires" }

        // Unmetered Wi-Fi + AUTO_BEST -> picks 24-bit / 192 kHz FLAC
        val wifiResolved = com.example.data.streams.StreamQualityResolver.applyPolicyToTrack(
            track = station,
            isUnmeteredWifi = true,
            preference = com.example.domain.model.PreferredStreamQuality.AUTO_BEST
        )
        assertEquals("jb2_hires_192", wifiResolved.activeVariantId)
        assertEquals(192_000, wifiResolved.sampleRateHz)
        assertEquals(24, wifiResolved.bitDepth)
        assertTrue(wifiResolved.isHiRes)

        // Metered cellular + AUTO_BEST -> steps to Studio High 320 kbps
        val cellularResolved = com.example.data.streams.StreamQualityResolver.applyPolicyToTrack(
            track = station,
            isUnmeteredWifi = false,
            preference = com.example.domain.model.PreferredStreamQuality.AUTO_BEST
        )
        assertEquals("jb2_mp3_320", cellularResolved.activeVariantId)

        // Metered cellular + ALWAYS_HI_RES -> forces 192 kHz FLAC
        val forcedHiRes = com.example.data.streams.StreamQualityResolver.applyPolicyToTrack(
            track = station,
            isUnmeteredWifi = false,
            preference = com.example.domain.model.PreferredStreamQuality.ALWAYS_HI_RES
        )
        assertEquals("jb2_hires_192", forcedHiRes.activeVariantId)

        // Downgrade fallback steps cleanly from 192 kHz FLAC -> 320 kbps MP3
        val downgraded = com.example.data.streams.StreamQualityResolver.downgradeVariant(wifiResolved)
        assertNotNull(downgraded)
        assertEquals("jb2_mp3_320", downgraded!!.id)
    }

    @Test
    fun `direct Hi-Res stream persists through MediaItem bundle, Room DB, and DataStore preferences`() = runTest {
        val hiResTrack = com.example.data.streams.HiResStreamCatalog.curatedHiResStations
            .first { it.id == "stream_jb_radio2_hires" }

        // MediaItem round-trip
        val restored = Track.fromMediaItem(hiResTrack.toMediaItem())
        assertNotNull(restored)
        assertEquals(TrackSource.DIRECT_STREAM, restored!!.source)
        assertEquals(192_000, restored.sampleRateHz)
        assertEquals(24, restored.bitDepth)
        assertTrue(restored.isLiveStream)
        assertEquals(2, restored.variants.size)
        assertEquals("jb2_hires_192", restored.activeVariantId)

        // Room DB persistence
        val dao = database.sonoraDao()
        dao.upsertTrack(CachedTrackEntity.fromDomain(hiResTrack))
        val directList = dao.observeDirectStreamTracks().first()
        assertEquals(1, directList.size)
        val dbRestored = directList.first().toDomain()
        assertEquals(192_000, dbRestored.sampleRateHz)
        assertEquals(24, dbRestored.bitDepth)
        assertEquals(2, dbRestored.variants.size)

        // DataStore preferences persistence
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.setPreferredStreamQuality(com.example.domain.model.PreferredStreamQuality.ALWAYS_HI_RES)
        prefs.setDirectFloatPcmBypassEq(true)
        val savedSettings = prefs.settingsFlow.first()
        assertEquals(com.example.domain.model.PreferredStreamQuality.ALWAYS_HI_RES, savedSettings.preferredStreamQuality)
        assertTrue(savedSettings.directFloatPcmBypassEq)
    }
}
