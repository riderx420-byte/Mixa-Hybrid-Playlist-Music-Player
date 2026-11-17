package com.example.data.streams

import com.example.domain.model.AudioQualityTier
import com.example.domain.model.StreamVariant
import com.example.domain.model.Track
import com.example.domain.model.TrackSource

/**
 * Curated open-access lossless & Hi-Res internet radio stations with tiered bitrate variants
 * so users can experience Automatic Best Source + live Quality Switching out of the box.
 */
object HiResStreamCatalog {

    val curatedHiResStations: List<Track> = listOf(
        Track(
            id = "stream_rp_main_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Paradise • Main Mix (Lossless)",
            artist = "Eclectic Studio Radio • Listener Supported",
            album = "Hi-Res Web Radio",
            artworkUri = "https://radioparadise.com/graphics/rp_logo_square.png",
            contentUri = "https://stream.radioparadise.com/flacm",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "rp_main_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "https://stream.radioparadise.com/flacm",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                ),
                StreamVariant(
                    id = "rp_main_aac320",
                    label = "Studio High (AAC 320 kbps)",
                    url = "https://stream.radioparadise.com/aac-320",
                    codecLabel = "AAC",
                    bitrateKbps = 320,
                    sampleRateHz = 44_100,
                    tier = AudioQualityTier.HIGH_ADAPTIVE
                ),
                StreamVariant(
                    id = "rp_main_aac128",
                    label = "Efficiency (AAC 128 kbps)",
                    url = "https://stream.radioparadise.com/aac-128",
                    codecLabel = "AAC",
                    bitrateKbps = 128,
                    sampleRateHz = 44_100,
                    tier = AudioQualityTier.DATA_SAVER
                )
            ),
            activeVariantId = "rp_main_flac"
        ),
        Track(
            id = "stream_rp_mellow_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Paradise • Mellow Mix (Lossless)",
            artist = "Chill / Acoustic / Ambient",
            album = "Hi-Res Web Radio",
            artworkUri = "https://radioparadise.com/graphics/rp_logo_square.png",
            contentUri = "https://stream.radioparadise.com/mellow-flacm",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "rp_mellow_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "https://stream.radioparadise.com/mellow-flacm",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                ),
                StreamVariant(
                    id = "rp_mellow_aac320",
                    label = "Studio High (AAC 320 kbps)",
                    url = "https://stream.radioparadise.com/mellow-320",
                    codecLabel = "AAC",
                    bitrateKbps = 320,
                    sampleRateHz = 44_100,
                    tier = AudioQualityTier.HIGH_ADAPTIVE
                ),
                StreamVariant(
                    id = "rp_mellow_aac128",
                    label = "Efficiency (AAC 128 kbps)",
                    url = "https://stream.radioparadise.com/mellow-128",
                    codecLabel = "AAC",
                    bitrateKbps = 128,
                    sampleRateHz = 44_100,
                    tier = AudioQualityTier.DATA_SAVER
                )
            ),
            activeVariantId = "rp_mellow_flac"
        ),
        Track(
            id = "stream_rp_rock_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Paradise • Rock Mix (Lossless)",
            artist = "Indie & Progressive Rock",
            album = "Hi-Res Web Radio",
            artworkUri = "https://radioparadise.com/graphics/rp_logo_square.png",
            contentUri = "https://stream.radioparadise.com/rock-flacm",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "rp_rock_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "https://stream.radioparadise.com/rock-flacm",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                ),
                StreamVariant(
                    id = "rp_rock_aac320",
                    label = "Studio High (AAC 320 kbps)",
                    url = "https://stream.radioparadise.com/rock-320",
                    codecLabel = "AAC",
                    bitrateKbps = 320,
                    sampleRateHz = 44_100,
                    tier = AudioQualityTier.HIGH_ADAPTIVE
                )
            ),
            activeVariantId = "rp_rock_flac"
        ),
        Track(
            id = "stream_jb_radio2_hires",
            source = TrackSource.DIRECT_STREAM,
            title = "JB Radio-2 • Audiophile 192 kHz Hi-Res",
            artist = "24-Bit Studio Master Stream",
            album = "Hi-Res Web Radio",
            contentUri = "https://maggie.torontocast.com:8076/flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 3200,
            sampleRateHz = 192_000,
            bitDepth = 24,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "jb2_hires_192",
                    label = "Hi-Res Lossless (FLAC 24-bit / 192 kHz)",
                    url = "https://maggie.torontocast.com:8076/flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 3200,
                    sampleRateHz = 192_000,
                    bitDepth = 24,
                    tier = AudioQualityTier.HI_RES_LOSSLESS
                ),
                StreamVariant(
                    id = "jb2_mp3_320",
                    label = "Studio High (320 kbps)",
                    url = "https://maggie.torontocast.com:8076/mp3",
                    codecLabel = "MP3",
                    bitrateKbps = 320,
                    sampleRateHz = 44_100,
                    tier = AudioQualityTier.HIGH_ADAPTIVE
                )
            ),
            activeVariantId = "jb2_hires_192"
        ),
        Track(
            id = "stream_radio_calico_hires",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Calico • 24-bit / 48 kHz Lossless",
            artist = "Curated Hi-Res Radio",
            album = "Hi-Res Web Radio",
            contentUri = "https://radio3.radio-calico.com:8443/calico",
            audioFormatLabel = "FLAC",
            bitrateKbps = 2100,
            sampleRateHz = 48_000,
            bitDepth = 24,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "calico_flac_24",
                    label = "Hi-Res Lossless (FLAC 24-bit / 48 kHz)",
                    url = "https://radio3.radio-calico.com:8443/calico",
                    codecLabel = "FLAC",
                    bitrateKbps = 2100,
                    sampleRateHz = 48_000,
                    bitDepth = 24,
                    tier = AudioQualityTier.HI_RES_LOSSLESS
                )
            ),
            activeVariantId = "calico_flac_24"
        ),
        Track(
            id = "stream_rp_global_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Paradise • Global Mix (Lossless)",
            artist = "World / Global Eclectic • Commercial Free",
            album = "Hi-Res Web Radio",
            artworkUri = "https://radioparadise.com/graphics/rp_logo_square.png",
            contentUri = "https://stream.radioparadise.com/s/world-etc-flacm",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "rp_global_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "https://stream.radioparadise.com/s/world-etc-flacm",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "rp_global_flac"
        ),
        Track(
            id = "stream_rp_eclectic_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Paradise • Eclectic Mix (Lossless)",
            artist = "Beyond / Future / Indie Cuts",
            album = "Hi-Res Web Radio",
            artworkUri = "https://radioparadise.com/graphics/rp_logo_square.png",
            contentUri = "https://stream.radioparadise.com/eclectic-flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "rp_eclectic_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "https://stream.radioparadise.com/eclectic-flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "rp_eclectic_flac"
        ),
        Track(
            id = "stream_intense_radio_hires",
            source = TrackSource.DIRECT_STREAM,
            title = "Intense Radio • 24-bit Lossless",
            artist = "Dance / Electronic / Club FLAC",
            album = "Hi-Res Web Radio",
            contentUri = "https://secure.live-streams.nl/flac.flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 2000,
            sampleRateHz = 44_100,
            bitDepth = 24,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "intense_flac_24",
                    label = "Hi-Res Lossless (FLAC 24-bit / 44.1 kHz)",
                    url = "https://secure.live-streams.nl/flac.flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 2000,
                    sampleRateHz = 44_100,
                    bitDepth = 24,
                    tier = AudioQualityTier.HI_RES_LOSSLESS
                )
            ),
            activeVariantId = "intense_flac_24"
        ),
        Track(
            id = "stream_naim_radio_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Naim Radio • Audiophile Reference",
            artist = "Eclectic Reference Showcase • Naim Audio",
            album = "Hi-Res Web Radio",
            contentUri = "http://mscp3.live-streams.nl:8360/flac.flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "naim_radio_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://mscp3.live-streams.nl:8360/flac.flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "naim_radio_flac"
        ),
        Track(
            id = "stream_naim_jazz_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Naim Jazz HD • CD Lossless",
            artist = "Contemporary & Classic Jazz • Naim Audio",
            album = "Hi-Res Web Radio",
            contentUri = "http://mscp3.live-streams.nl:8340/jazz-flac.flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "naim_jazz_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://mscp3.live-streams.nl:8340/jazz-flac.flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "naim_jazz_flac"
        ),
        Track(
            id = "stream_naim_classical_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Naim Classical HD • CD Lossless",
            artist = "Orchestral & Chamber • Naim Audio",
            album = "Hi-Res Web Radio",
            contentUri = "http://mscp3.live-streams.nl:8250/class-flac.flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "naim_class_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://mscp3.live-streams.nl:8250/class-flac.flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "naim_class_flac"
        ),
        Track(
            id = "stream_hionline_chill_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Hi On Line • Chill In Zone (FLAC)",
            artist = "Ambient / Lounge / Downtempo",
            album = "Hi-Res Web Radio",
            contentUri = "http://mscp2.live-streams.nl:8100/flac.flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "hionline_chill_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://mscp2.live-streams.nl:8100/flac.flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "hionline_chill_flac"
        ),
        Track(
            id = "stream_radioblues_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Radio Blues • CD Lossless",
            artist = "24/7 Classic & Modern Blues • Ogg FLAC",
            album = "Hi-Res Web Radio",
            contentUri = "https://streams.radiomast.io/radioblues-flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "radioblues_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "https://streams.radiomast.io/radioblues-flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "radioblues_flac"
        ),
        Track(
            id = "stream_sector_progressive_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Sector Radio • Progressive (FLAC)",
            artist = "Progressive Electronic & Melodic",
            album = "Hi-Res Web Radio",
            contentUri = "http://89.223.45.5:8000/progressive-flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "sector_prog_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://89.223.45.5:8000/progressive-flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "sector_prog_flac"
        ),
        Track(
            id = "stream_sector_classical_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Sector Radio • Nota Classical (FLAC)",
            artist = "Classical Masterworks • Hi-Fi Stream",
            album = "Hi-Res Web Radio",
            contentUri = "http://89.223.45.5:8000/nota-flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "sector_nota_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://89.223.45.5:8000/nota-flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "sector_nota_flac"
        ),
        Track(
            id = "stream_sector_space_flac",
            source = TrackSource.DIRECT_STREAM,
            title = "Sector Radio • Space Ambient (FLAC)",
            artist = "Deep Synth / Space Ambient",
            album = "Hi-Res Web Radio",
            contentUri = "http://89.223.45.5:8000/space-flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "sector_space_flac",
                    label = "CD Lossless (FLAC 16-bit / 44.1 kHz)",
                    url = "http://89.223.45.5:8000/space-flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 1411,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    tier = AudioQualityTier.CD_LOSSLESS
                )
            ),
            activeVariantId = "sector_space_flac"
        ),
        Track(
            id = "stream_superstereo1_hires",
            source = TrackSource.DIRECT_STREAM,
            title = "SuperStereo 1 • 80s Hi-Res FLAC",
            artist = "Remastered 80s Pop & Synth • Hi-Res",
            album = "Hi-Res Web Radio",
            contentUri = "http://198.204.228.202:8160/flac",
            audioFormatLabel = "FLAC",
            bitrateKbps = 2400,
            sampleRateHz = 96_000,
            bitDepth = 24,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "superstereo1_flac",
                    label = "Hi-Res Lossless (FLAC 24-bit / 96 kHz)",
                    url = "http://198.204.228.202:8160/flac",
                    codecLabel = "FLAC",
                    bitrateKbps = 2400,
                    sampleRateHz = 96_000,
                    bitDepth = 24,
                    tier = AudioQualityTier.HI_RES_LOSSLESS
                )
            ),
            activeVariantId = "superstereo1_flac"
        ),
        Track(
            id = "stream_superstereo2_hires",
            source = TrackSource.DIRECT_STREAM,
            title = "SuperStereo 2 • Master Hi-Res FLAC",
            artist = "Classic Hits & Audiophile Masters",
            album = "Hi-Res Web Radio",
            contentUri = "http://icecast.centaury.cl:7570/SuperStereoHiRes2",
            audioFormatLabel = "FLAC",
            bitrateKbps = 2400,
            sampleRateHz = 96_000,
            bitDepth = 24,
            isLiveStream = true,
            folderName = "Direct Hi-Res",
            variants = listOf(
                StreamVariant(
                    id = "superstereo2_flac",
                    label = "Hi-Res Lossless (FLAC 24-bit / 96 kHz)",
                    url = "http://icecast.centaury.cl:7570/SuperStereoHiRes2",
                    codecLabel = "FLAC",
                    bitrateKbps = 2400,
                    sampleRateHz = 96_000,
                    bitDepth = 24,
                    tier = AudioQualityTier.HI_RES_LOSSLESS
                )
            ),
            activeVariantId = "superstereo2_flac"
        )
    )

    private val curatedIds: Set<String> = curatedHiResStations.map { it.id }.toSet()

    fun isCuratedStation(trackId: String): Boolean = trackId in curatedIds
}
