package com.example.data.lyrics

data class LrcLine(
    val timeMs: Long,
    val text: String
)

data class LrcDocument(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val offsetMs: Long = 0L,
    val lines: List<LrcLine>,
    val isSynced: Boolean,
    val sourceLabel: String = "LRC",
    val rawLrc: String = ""
)

/**
 * Parses standard & enhanced `.lrc` time-synchronized lyric files and converts plain lyric text
 * cleanly when no `[mm:ss.xx]` timestamps are present.
 *
 * Supported tags:
 * - Metadata: `[ti:...]`, `[ar:...]`, `[al:...]`, `[offset:+/-ms]`
 * - Multiple timestamps per line: `[00:18.20][01:32.50]Chorus line`
 * - Fractional precision: `.xx` (centiseconds), `.xxx` (milliseconds), `:xx`, or omitted `[00:18]`
 * - Word-level karaoke tokens `<00:18.45>` stripped cleanly for line display.
 */
object LrcParser {

    private val METADATA_TAG_REGEX = Regex("""^\[([a-zA-Z]+)\s*:\s*(.*?)\]$""")
    private val TIMESTAMP_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val INLINE_WORD_TIMING_REGEX = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(rawInput: String, sourceLabel: String = "LRC"): LrcDocument {
        val cleanedInput = rawInput.removePrefix("\uFEFF").trim()
        if (cleanedInput.isBlank()) {
            return LrcDocument(
                lines = emptyList(),
                isSynced = false,
                sourceLabel = sourceLabel,
                rawLrc = rawInput
            )
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offsetMs = 0L

        val timedEntries = mutableListOf<LrcLine>()
        val plainLines = mutableListOf<String>()

        cleanedInput.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            val metaMatch = METADATA_TAG_REGEX.matchEntire(trimmed)
            if (metaMatch != null && !TIMESTAMP_TAG_REGEX.containsMatchIn(trimmed)) {
                val key = metaMatch.groupValues[1].lowercase()
                val value = metaMatch.groupValues[2].trim()
                when (key) {
                    "ti" -> if (value.isNotBlank()) title = value
                    "ar" -> if (value.isNotBlank()) artist = value
                    "al" -> if (value.isNotBlank()) album = value
                    "offset" -> offsetMs = value.toLongOrNull() ?: 0L
                }
                return@forEach
            }

            val timestampMatches = TIMESTAMP_TAG_REGEX.findAll(trimmed).toList()
            if (timestampMatches.isNotEmpty()) {
                val lyricBody = trimmed
                    .replace(TIMESTAMP_TAG_REGEX, "")
                    .replace(INLINE_WORD_TIMING_REGEX, "")
                    .replace(Regex("""\s{2,}"""), " ")
                    .trim()

                timestampMatches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = match.groupValues[3]
                    val fractionMs = when (fracStr.length) {
                        0 -> 0L
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        else -> fracStr.take(3).toLongOrNull() ?: 0L
                    }
                    val rawTimeMs = (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
                    val adjustedTimeMs = (rawTimeMs - offsetMs).coerceAtLeast(0L)
                    timedEntries.add(LrcLine(timeMs = adjustedTimeMs, text = lyricBody))
                }
            } else {
                val cleanPlain = trimmed
                    .replace(INLINE_WORD_TIMING_REGEX, "")
                    .trim()
                if (cleanPlain.isNotBlank()) {
                    plainLines.add(cleanPlain)
                }
            }
        }

        if (timedEntries.isNotEmpty()) {
            val sorted = timedEntries
                .sortedBy { it.timeMs }
                .dropLastWhile { it.text.isBlank() }
            return LrcDocument(
                title = title,
                artist = artist,
                album = album,
                offsetMs = offsetMs,
                lines = sorted,
                isSynced = sorted.isNotEmpty(),
                sourceLabel = sourceLabel,
                rawLrc = cleanedInput
            )
        }

        val fallbackLines = plainLines.mapIndexed { idx, line ->
            LrcLine(timeMs = idx * 1_000L, text = line)
        }
        return LrcDocument(
            title = title,
            artist = artist,
            album = album,
            offsetMs = offsetMs,
            lines = fallbackLines,
            isSynced = false,
            sourceLabel = sourceLabel,
            rawLrc = cleanedInput
        )
    }

    /**
     * Binary search for the active lyric line matching Media3's [positionMs].
     * Returns `-1` when playback is before the very first timestamp or when [lines] is empty.
     */
    fun findActiveLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines.first().timeMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
