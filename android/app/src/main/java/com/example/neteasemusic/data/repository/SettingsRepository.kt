package com.example.neteasemusic.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "netease_music_prefs"
private const val KEY_API_URL = "api_url"
private const val KEY_MATCH_SOURCE = "match_source"
private const val KEY_SEARCH_LIMIT = "search_limit"
private const val KEY_SEND_DETAIL = "send_detail"
private const val KEY_SEND_COVER = "send_cover"
private const val KEY_SEND_AUDIO = "send_audio"
private const val KEY_SEND_LYRICS = "send_lyrics"

const val DEFAULT_API_URL = "http://192.168.1.1:3000"
const val DEFAULT_MATCH_SOURCE = ""
const val DEFAULT_SEARCH_LIMIT = 5

data class AppSettings(
    val apiUrl: String = DEFAULT_API_URL,
    val matchSource: String = DEFAULT_MATCH_SOURCE,
    val searchLimit: Int = DEFAULT_SEARCH_LIMIT,
    val sendDetail: Boolean = true,
    val sendCover: Boolean = true,
    val sendAudio: Boolean = true,
    val sendLyrics: Boolean = false
)

/**
 * Repository that persists user preferences via [SharedPreferences] and exposes
 * them as a [StateFlow] so the UI can react to changes in real time.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        apiUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL,
        matchSource = prefs.getString(KEY_MATCH_SOURCE, DEFAULT_MATCH_SOURCE) ?: DEFAULT_MATCH_SOURCE,
        searchLimit = prefs.getInt(KEY_SEARCH_LIMIT, DEFAULT_SEARCH_LIMIT),
        sendDetail = prefs.getBoolean(KEY_SEND_DETAIL, true),
        sendCover = prefs.getBoolean(KEY_SEND_COVER, true),
        sendAudio = prefs.getBoolean(KEY_SEND_AUDIO, true),
        sendLyrics = prefs.getBoolean(KEY_SEND_LYRICS, false)
    )

    fun save(settings: AppSettings) {
        prefs.edit {
            putString(KEY_API_URL, settings.apiUrl)
            putString(KEY_MATCH_SOURCE, settings.matchSource)
            putInt(KEY_SEARCH_LIMIT, settings.searchLimit)
            putBoolean(KEY_SEND_DETAIL, settings.sendDetail)
            putBoolean(KEY_SEND_COVER, settings.sendCover)
            putBoolean(KEY_SEND_AUDIO, settings.sendAudio)
            putBoolean(KEY_SEND_LYRICS, settings.sendLyrics)
        }
        _settings.value = settings
    }
}
