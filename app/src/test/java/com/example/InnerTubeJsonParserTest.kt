package com.example

import com.example.data.innertube.InnerTubeJsonParser
import com.example.domain.model.SearchFilter
import com.example.domain.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InnerTubeJsonParserTest {

    @Test
    fun `parseSearchResults extracts videoId title artist and duration`() {
        val sampleJson = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicShelfRenderer": {
                              "contents": [{
                                "musicResponsiveListItemRenderer": {
                                  "playlistItemData": { "videoId": "abc123XYZ" },
                                  "thumbnail": {
                                    "musicThumbnailRenderer": {
                                      "thumbnail": {
                                        "thumbnails": [
                                          { "url": "https://lh3.googleusercontent.com/small.jpg", "width": 120 },
                                          { "url": "https://lh3.googleusercontent.com/large.jpg", "width": 544 }
                                        ]
                                      }
                                    }
                                  },
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": { "runs": [{ "text": "Midnight City" }] }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {
                                          "runs": [
                                            { "text": "Song" },
                                            { "text": " • " },
                                            { "text": "M83" },
                                            { "text": " • " },
                                            { "text": "Hurry Up, We're Dreaming" },
                                            { "text": " • " },
                                            { "text": "4:03" }
                                          ]
                                        }
                                      }
                                    }
                                  ]
                                }
                              }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val tracks = InnerTubeJsonParser.parseSearchResults(sampleJson, SearchFilter.SONGS)
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("yt_abc123XYZ", track.id)
        assertEquals(TrackSource.YOUTUBE, track.source)
        assertEquals("Midnight City", track.title)
        assertEquals("M83", track.artist)
        assertEquals("Hurry Up, We're Dreaming", track.album)
        assertEquals(243_000L, track.durationMs)
        assertEquals("https://lh3.googleusercontent.com/large.jpg", track.artworkUri)
        assertEquals("sonora://yt/abc123XYZ", track.playbackUriString)
    }

    @Test
    fun `parsePlayerStream selects highest bitrate opus stream and reads expire`() {
        val samplePlayerJson = """
            {
              "streamingData": {
                "adaptiveFormats": [
                  {
                    "mimeType": "video/mp4; codecs=\"avc1\"",
                    "bitrate": 1200000,
                    "url": "https://rr1.googlevideo.com/videoplayback?expire=1999999999"
                  },
                  {
                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                    "bitrate": 128000,
                    "url": "https://rr1.googlevideo.com/aac?expire=1999999999"
                  },
                  {
                    "mimeType": "audio/webm; codecs=\"opus\"",
                    "bitrate": 160000,
                    "url": "https://rr1.googlevideo.com/opus160?expire=1999999999"
                  }
                ]
              }
            }
        """.trimIndent()

        val opusPreferred = InnerTubeJsonParser.parsePlayerStream(samplePlayerJson, "vid1", preferOpus = true)
        assertNotNull(opusPreferred)
        assertEquals("OPUS", opusPreferred!!.codecLabel)
        assertEquals(160, opusPreferred.bitrateKbps)
        assertEquals(1999999999000L, opusPreferred.expiresAtMs)

        val aacPreferred = InnerTubeJsonParser.parsePlayerStream(samplePlayerJson, "vid1", preferOpus = false)
        assertNotNull(aacPreferred)
        assertEquals("AAC", aacPreferred!!.codecLabel)
        assertTrue(aacPreferred.streamUrl.contains("/aac"))
    }

    @Test
    fun `parseRadioQueue parses playlistPanelVideoRenderer items`() {
        val sampleNextJson = """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "tabbedRenderer": {
                    "watchNextTabbedResultsRenderer": {
                      "tabs": [{
                        "tabRenderer": {
                          "content": {
                            "musicQueueRenderer": {
                              "content": {
                                "playlistPanelRenderer": {
                                  "contents": [
                                    {
                                      "playlistPanelVideoRenderer": {
                                        "videoId": "rad001",
                                        "title": { "runs": [{ "text": "Intro" }] },
                                        "longBylineText": {
                                          "runs": [
                                            { "text": "The xx" },
                                            { "text": " • " },
                                            { "text": "xx" }
                                          ]
                                        },
                                        "lengthText": { "runs": [{ "text": "2:07" }] }
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val queue = InnerTubeJsonParser.parseRadioQueue(sampleNextJson)
        assertEquals(1, queue.size)
        assertEquals("rad001", queue.first().videoId)
        assertEquals("The xx", queue.first().artist)
        assertEquals(127_000L, queue.first().durationMs)
    }
}
