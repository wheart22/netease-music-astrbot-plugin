package com.example.neteasemusic.data.repository

import com.example.neteasemusic.data.api.NeteaseApiService
import com.example.neteasemusic.data.model.LyricLine
import com.example.neteasemusic.data.model.SearchSong
import com.example.neteasemusic.data.model.SongDetail

/**
 * Metadata prefixes that indicate non-lyric lines (credits, production info, etc.).
 * Mirrors the Python LYRIC_METADATA_PREFIXES tuple in main.py.
 */
private val LYRIC_METADATA_PREFIXES = setOf(
    "作词", "作曲", "编曲", "制作人", "监制", "混音", "母带", "和声",
    "录音", "配唱", "录音室", "企划", "统筹", "特别鸣谢", "出品", "发行",
    "封面", "文案", "吉他", "贝斯", "鼓", "键盘", "弦乐", "人声编辑",
    "program", "producer", "arranger", "composer", "lyricist"
)

/**
 * Placeholder lines that indicate the song has no actual lyrics.
 * Mirrors the Python LYRIC_PLACEHOLDER_LINES set in main.py.
 */
private val LYRIC_PLACEHOLDER_LINES = setOf(
    "纯音乐请欣赏",
    "此歌曲为没有填词的纯音乐请您欣赏",
    "此歌曲纯音乐请您欣赏",
    "暂无歌词",
    "伴奏"
)

/** Regex that matches a leading [mm:ss.xx] or [mm:ss:xx] LRC timestamp tag. */
private val LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{1,3})]""")

/** Regex that strips any bracketed tag from a line (timestamps, metadata tags, etc.). */
private val LRC_TAG_STRIP_REGEX = Regex("""\[[^\]]*]""")

/** Regex that collapses multiple whitespace characters into a single space. */
private val WHITESPACE_REGEX = Regex("""\s+""")

/**
 * Central data repository for all music-related API calls.
 *
 * @param api A [NeteaseApiService] instance bound to the current API base URL.
 */
class MusicRepository(private val api: NeteaseApiService) {

    // ── Song search ───────────────────────────────────────────────────────────

    /**
     * Search songs by [keyword] and return up to [limit] results.
     * Mirrors Python's NeteaseMusicAPI.search_songs().
     */
    suspend fun searchSongs(keyword: String, limit: Int): List<SearchSong> {
        val response = api.searchSongs(keywords = keyword, limit = limit)
        return response.result?.songs ?: emptyList()
    }

    // ── Song detail ───────────────────────────────────────────────────────────

    /**
     * Fetch detailed metadata for a single song by its [songId].
     * Mirrors Python's NeteaseMusicAPI.get_song_details().
     */
    suspend fun getSongDetail(songId: Long): SongDetail? {
        val response = api.getSongDetail(ids = songId.toString())
        return response.songs?.firstOrNull()
    }

    // ── Audio URL ─────────────────────────────────────────────────────────────

    /**
     * Retrieve the best available audio stream URL for [songId].
     * An optional [source] can be passed to pin the audio provider; leave blank
     * to let the API choose automatically.
     * Mirrors Python's NeteaseMusicAPI.get_audio_url() and
     * _extract_match_audio_url() priority logic (proxyUrl > data.url > url).
     *
     * @return The resolved audio URL, or null if none could be found.
     */
    suspend fun getAudioUrl(songId: Long, source: String): String? {
        val response = api.getAudioUrl(
            id = songId,
            source = source.takeIf { it.isNotBlank() }
        )
        if (response.code != 200) return null
        return response.resolvedUrl
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────

    /**
     * Fetch and parse the LRC lyrics for [songId] into a list of timestamped
     * [LyricLine] objects, sorted by time.
     *
     * Lines without a parseable timestamp receive timeMs = -1 and are placed at
     * the start of the list so callers can show them as a header/preamble.
     *
     * Returns an empty list when the song has no lyrics or only placeholders.
     * Mirrors Python's NeteaseMusicAPI.get_song_lyrics() +
     * _clean_lyrics_text() + _is_non_lyric_line().
     */
    suspend fun getLyrics(songId: Long): List<LyricLine> {
        val response = api.getLyrics(id = songId)
        val rawLyric = response.lrc?.lyric ?: return emptyList()
        return parseLrc(rawLyric)
    }

    // ── LRC parsing helpers ───────────────────────────────────────────────────

    /**
     * Parse a raw LRC string into a sorted list of [LyricLine] objects,
     * stripping timestamps, metadata, and placeholder lines.
     */
    fun parseLrc(rawLyric: String): List<LyricLine> {
        if (rawLyric.isBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()

        for (rawLine in rawLyric.lines()) {
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) continue

            // Extract all timestamp matches from the beginning of the line.
            val timestamps = mutableListOf<Long>()
            var cursor = 0
            while (cursor < trimmedLine.length) {
                val match = LRC_TIMESTAMP_REGEX.find(trimmedLine, cursor)
                    ?: break
                // Only collect timestamps at the very start or chained timestamps.
                if (match.range.first != cursor) break
                val min = match.groupValues[1].toLongOrNull() ?: break
                val sec = match.groupValues[2].toLongOrNull() ?: break
                val ms = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: break
                timestamps.add(min * 60_000L + sec * 1_000L + ms)
                cursor = match.range.last + 1
            }

            // Strip all bracketed tags to get the display text.
            val text = LRC_TAG_STRIP_REGEX.replace(trimmedLine, "")
                .let { WHITESPACE_REGEX.replace(it.trim(), " ") }

            // Skip metadata lines and placeholder lines (mirrors _is_non_lyric_line).
            if (text.isBlank() || isNonLyricLine(text)) continue

            if (timestamps.isEmpty()) {
                lines.add(LyricLine(timeMs = -1L, text = text))
            } else {
                timestamps.forEach { ts -> lines.add(LyricLine(timeMs = ts, text = text)) }
            }
        }

        return lines.sortedWith(compareBy { it.timeMs })
    }

    private fun isNonLyricLine(line: String): Boolean {
        // Strip Chinese and English punctuation (。！，：) together with ASCII punctuation and
        // whitespace so that metadata prefixes like "作词：" match the bare prefix "作词".
        val normalized = line
            .replace(Regex("[。！!,.，:：\\s]+"), "")
            .lowercase()
        if (normalized.isEmpty()) return true
        if (normalized in LYRIC_PLACEHOLDER_LINES) return true
        return LYRIC_METADATA_PREFIXES.any { normalized.startsWith(it) }
    }
}
