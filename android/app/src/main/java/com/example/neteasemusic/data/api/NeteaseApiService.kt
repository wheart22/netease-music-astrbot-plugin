package com.example.neteasemusic.data.api

import com.example.neteasemusic.data.model.AudioUrlResponse
import com.example.neteasemusic.data.model.LyricResponse
import com.example.neteasemusic.data.model.SearchResponse
import com.example.neteasemusic.data.model.SongDetailResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface that mirrors the NeteaseCloudMusicApi Enhanced endpoints
 * used by the original AstrBot plugin.
 */
interface NeteaseApiService {

    /**
     * Search songs by keyword.
     * Mirrors: GET /search?keywords=...&limit=...&type=1
     */
    @GET("search")
    suspend fun searchSongs(
        @Query("keywords") keywords: String,
        @Query("limit") limit: Int = 5,
        @Query("type") type: Int = 1
    ): SearchResponse

    /**
     * Get detailed information for one or more songs by their IDs.
     * Mirrors: GET /song/detail?ids=...
     */
    @GET("song/detail")
    suspend fun getSongDetail(
        @Query("ids") ids: String
    ): SongDetailResponse

    /**
     * Get lyric data for a song.
     * Mirrors: GET /lyric?id=...
     */
    @GET("lyric")
    suspend fun getLyrics(
        @Query("id") id: Long
    ): LyricResponse

    /**
     * Get the best available audio stream URL via the /song/url/match endpoint.
     * Mirrors: GET /song/url/match?id=...&source=...
     */
    @GET("song/url/match")
    suspend fun getAudioUrl(
        @Query("id") id: Long,
        @Query("source") source: String? = null
    ): AudioUrlResponse
}
