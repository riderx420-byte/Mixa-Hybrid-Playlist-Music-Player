package com.example

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.example.data.innertube.GoogleDeviceAccountHelper
import com.example.data.lyrics.LrcParser
import com.example.player.CrossfadeTransitionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mixa", appName)
    }

    @Test
    fun `google device account helper builds prefilled youtube music login url`() {
        val url = GoogleDeviceAccountHelper.buildPrefilledLoginUrl("listener@gmail.com")
        assertTrue(url.contains("Email=listener%40gmail.com"))
        assertTrue(url.contains("music.youtube.com"))
    }

    @Test
    fun `crossfade policy controls pauseAtEndOfMediaItems for gapless vs crossfade`() {
        assertFalse(CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(0, Player.REPEAT_MODE_OFF))
        assertFalse(CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(0, Player.REPEAT_MODE_ALL))
        assertTrue(CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(5, Player.REPEAT_MODE_OFF))
        assertTrue(CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(5, Player.REPEAT_MODE_ALL))
        assertFalse(CrossfadeTransitionPolicy.shouldPauseAtEndOfMediaItems(5, Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `crossfade policy computes equal-power volumes and trigger windows`() {
        val start = CrossfadeTransitionPolicy.computeEqualPowerVolumes(0f)
        assertEquals(1f, start.outgoing, 0.001f)
        assertEquals(0f, start.incoming, 0.001f)

        val mid = CrossfadeTransitionPolicy.computeEqualPowerVolumes(0.5f)
        assertEquals(0.7071f, mid.outgoing, 0.01f)
        assertEquals(0.7071f, mid.incoming, 0.01f)

        val end = CrossfadeTransitionPolicy.computeEqualPowerVolumes(1f)
        assertEquals(0f, end.outgoing, 0.001f)
        assertEquals(1f, end.incoming, 0.001f)

        assertTrue(
            CrossfadeTransitionPolicy.shouldPreCueNextDeck(
                remainingMs = 8_000L,
                durationMs = 180_000L,
                crossfadeMs = 5_000L,
                hasNextItem = true,
                isLiveStream = false
            )
        )
        assertFalse(
            CrossfadeTransitionPolicy.shouldStartCrossfade(
                remainingMs = 8_000L,
                durationMs = 180_000L,
                crossfadeMs = 5_000L,
                hasNextItem = true,
                isLiveStream = false
            )
        )
        assertTrue(
            CrossfadeTransitionPolicy.shouldStartCrossfade(
                remainingMs = 4_500L,
                durationMs = 180_000L,
                crossfadeMs = 5_000L,
                hasNextItem = true,
                isLiveStream = false
            )
        )
        assertFalse(
            CrossfadeTransitionPolicy.shouldStartCrossfade(
                remainingMs = 2_000L,
                durationMs = 4_000L,
                crossfadeMs = 5_000L,
                hasNextItem = true,
                isLiveStream = false
            )
        )
    }

    @Test
    fun `lrc parser handles metadata multi timestamps enhanced tokens offset and sync lookup`() {
        val raw = """
            [ti: Midnight Horizon]
            [ar: Mixa Studio]
            [al: Waves]
            [offset:500]
            [00:10.50]First <00:10.90>line here
            [00:20.00][01:05.25]Repeating chorus line
            [00:35.0]Third line
        """.trimIndent()

        val doc = LrcParser.parse(raw, sourceLabel = "Test .lrc")
        assertTrue(doc.isSynced)
        assertEquals("Midnight Horizon", doc.title)
        assertEquals("Mixa Studio", doc.artist)
        assertEquals("Waves", doc.album)
        assertEquals(500L, doc.offsetMs)
        assertEquals(4, doc.lines.size)

        // Offset of 500ms shifts [00:10.50] -> 10_000ms
        assertEquals(10_000L, doc.lines[0].timeMs)
        assertEquals("First line here", doc.lines[0].text)
        assertEquals(19_500L, doc.lines[1].timeMs)
        assertEquals("Repeating chorus line", doc.lines[1].text)
        assertEquals(34_500L, doc.lines[2].timeMs)
        assertEquals("Third line", doc.lines[2].text)
        assertEquals(64_750L, doc.lines[3].timeMs)
        assertEquals("Repeating chorus line", doc.lines[3].text)

        assertEquals(-1, LrcParser.findActiveLineIndex(doc.lines, 5_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(doc.lines, 10_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(doc.lines, 19_499L))
        assertEquals(1, LrcParser.findActiveLineIndex(doc.lines, 25_000L))
        assertEquals(3, LrcParser.findActiveLineIndex(doc.lines, 80_000L))
    }

    @Test
    fun `lrc parser marks plain unsynced lyrics properly`() {
        val plain = LrcParser.parse("Line one\nLine two\nLine three", sourceLabel = "Plain")
        assertFalse(plain.isSynced)
        assertEquals(3, plain.lines.size)
        assertEquals("Line two", plain.lines[1].text)
    }

    @Test
    fun `curated lossless radio catalog contains valid unique stations across hi-res and cd flac`() {
        val stations = com.example.data.streams.HiResStreamCatalog.curatedHiResStations
        assertTrue(stations.size >= 15)
        assertEquals(stations.size, stations.map { it.id }.distinct().size)
        stations.forEach { station ->
            assertEquals(com.example.domain.model.TrackSource.DIRECT_STREAM, station.source)
            assertTrue(station.isLiveStream)
            val uri = station.contentUri.orEmpty()
            assertTrue(
                "Expected http(s) url for ${station.id}",
                uri.startsWith("https://") || uri.startsWith("http://")
            )
            assertTrue(com.example.data.streams.HiResStreamCatalog.isCuratedStation(station.id))
        }
        assertTrue(stations.any { it.isHiRes })
        assertTrue(stations.any { it.isLossless && !it.isHiRes })
    }

    @Test
    fun `playlist computes cross-source counts collage artworks and total duration`() {
        val tracks = listOf(
            com.example.domain.model.Track(
                id = "l1",
                source = com.example.domain.model.TrackSource.LOCAL,
                title = "Local Track",
                artist = "Artist",
                contentUri = "file:///a.flac",
                durationMs = 120_000L
            ),
            com.example.domain.model.Track(
                id = "y1",
                source = com.example.domain.model.TrackSource.YOUTUBE,
                title = "YT Track",
                artist = "Artist",
                artworkUri = "https://img/1.jpg",
                contentUri = "yt",
                durationMs = 180_000L
            ),
            com.example.domain.model.Track(
                id = "e1",
                source = com.example.domain.model.TrackSource.EXTENSION,
                title = "Ext Track",
                artist = "Artist",
                artworkUri = "https://img/2.jpg",
                contentUri = "ext"
            ),
            com.example.domain.model.Track(
                id = "r1",
                source = com.example.domain.model.TrackSource.DIRECT_STREAM,
                title = "Radio",
                artist = "Station",
                contentUri = "https://stream.radioparadise.com/flacm",
                isLiveStream = true
            )
        )
        val playlist = com.example.domain.model.Playlist(id = 1L, name = "Hybrid", tracks = tracks)
        assertEquals(4, playlist.trackCount)
        assertEquals(1, playlist.localCount)
        assertEquals(1, playlist.youtubeCount)
        assertEquals(1, playlist.extensionCount)
        assertEquals(1, playlist.radioCount)
        assertEquals(2, playlist.collageArtworkUris.size)
        assertEquals(300_000L, playlist.totalDurationMs)
    }
}


