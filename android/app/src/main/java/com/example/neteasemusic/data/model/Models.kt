package com.example.neteasemusic.data.model

import com.google.gson.annotations.SerializedName

// ── Search response ───────────────────────────────────────────────────────────

data class SearchResponse(
    @SerializedName("result") val result: SearchResult?
)

data class SearchResult(
    @SerializedName("songs") val songs: List<SearchSong>?
)

data class SearchSong(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<SearchArtist>?,
    @SerializedName("album") val album: SearchAlbum?,
    @SerializedName("duration") val duration: Long
) {
    val artistsText: String
        get() = artists?.joinToString(" / ") { it.name } ?: ""

    val albumName: String
        get() = album?.name ?: "未知专辑"

    val durationText: String
        get() {
            val minutes = duration / 60000
            val seconds = (duration % 60000) / 1000
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
}

data class SearchArtist(
    @SerializedName("name") val name: String
)

data class SearchAlbum(
    @SerializedName("name") val name: String
)

// ── Song detail response ──────────────────────────────────────────────────────

data class SongDetailResponse(
    @SerializedName("songs") val songs: List<SongDetail>?
)

data class SongDetail(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("ar") val artists: List<DetailArtist>?,
    @SerializedName("al") val album: DetailAlbum?,
    @SerializedName("dt") val duration: Long
) {
    val artistsText: String
        get() = artists?.joinToString(" / ") { it.name } ?: ""

    val albumName: String
        get() = album?.name ?: "未知专辑"

    val coverUrl: String
        get() = album?.picUrl ?: ""

    val durationText: String
        get() {
            val minutes = duration / 60000
            val seconds = (duration % 60000) / 1000
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
}

data class DetailArtist(
    @SerializedName("name") val name: String
)

data class DetailAlbum(
    @SerializedName("name") val name: String,
    @SerializedName("picUrl") val picUrl: String?
)

// ── Lyric response ────────────────────────────────────────────────────────────

data class LyricResponse(
    @SerializedName("lrc") val lrc: LrcData?
)

data class LrcData(
    @SerializedName("lyric") val lyric: String?
)

/**
 * A single parsed lyric line with its timestamp (in milliseconds) and display text.
 * Lines without a valid timestamp use -1.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

// ── Audio URL response ────────────────────────────────────────────────────────

data class AudioUrlResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("proxyUrl") val proxyUrl: String?,
    @SerializedName("data") val data: AudioData?,
    @SerializedName("url") val url: String?
) {
    /**
     * Extract the best available URL following the proxyUrl > data.url > url priority
     * that mirrors Python's _extract_match_audio_url logic.
     */
    val resolvedUrl: String?
        get() {
            if (!proxyUrl.isNullOrBlank()) return proxyUrl
            val dataUrl = data?.resolvedUrl
            if (!dataUrl.isNullOrBlank()) return dataUrl
            if (!url.isNullOrBlank()) return url
            return null
        }
}

data class AudioData(
    @SerializedName("proxyUrl") val proxyUrl: String?,
    @SerializedName("url") val url: String?
) {
    val resolvedUrl: String?
        get() {
            if (!proxyUrl.isNullOrBlank()) return proxyUrl
            if (!url.isNullOrBlank()) return url
            return null
        }
}
