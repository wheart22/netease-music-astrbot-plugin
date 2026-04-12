package com.example.neteasemusic.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.neteasemusic.PlaybackService
import com.example.neteasemusic.data.api.RetrofitClient
import com.example.neteasemusic.data.model.LyricLine
import com.example.neteasemusic.data.model.SongDetail
import com.example.neteasemusic.data.repository.MusicRepository
import com.example.neteasemusic.data.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Ready(
        val detail: SongDetail,
        val audioUrl: String?,
        val audioError: String?,
        val lyrics: List<LyricLine>
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class PlayerViewModel(
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionPollJob: Job? = null
    private var currentLyrics: List<LyricLine> = emptyList()

    // ── Data loading ──────────────────────────────────────────────────────────

    fun loadSong(songId: Long) {
        _uiState.value = PlayerUiState.Loading
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.value
                val repo = MusicRepository(RetrofitClient.getService(settings.apiUrl))

                val detail = repo.getSongDetail(songId)
                    ?: throw IllegalStateException("无法获取歌曲详细信息")

                val audioUrl: String?
                val audioError: String?
                if (settings.sendAudio) {
                    val url = repo.getAudioUrl(songId, settings.matchSource)
                    audioUrl = url
                    audioError = if (url == null) "暂时没有匹配到可播放链接，可能需要VIP或没有版权" else null
                } else {
                    audioUrl = null
                    audioError = null
                }

                val lyrics: List<LyricLine> = if (settings.sendLyrics) {
                    repo.getLyrics(songId)
                } else {
                    emptyList()
                }

                currentLyrics = lyrics

                _uiState.value = PlayerUiState.Ready(
                    detail = detail,
                    audioUrl = audioUrl,
                    audioError = audioError,
                    lyrics = lyrics
                )

                if (audioUrl != null) {
                    setupMediaController(audioUrl)
                }
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error(
                    "加载歌曲失败：${e.localizedMessage}"
                )
            }
        }
    }

    // ── Media controller ──────────────────────────────────────────────────────

    private fun setupMediaController(audioUrl: String) {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            mediaController = controller

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) startPositionPolling() else stopPositionPolling()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _durationMs.value = controller.duration.coerceAtLeast(0L)
                    }
                }
            })

            controller.setMediaItem(MediaItem.fromUri(audioUrl))
            controller.prepare()
            controller.play()
        }, MoreExecutors.directExecutor())
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _positionMs.value = positionMs
        updateActiveLyric(positionMs)
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (true) {
                val controller = mediaController ?: break
                val pos = controller.currentPosition.coerceAtLeast(0L)
                _positionMs.value = pos
                updateActiveLyric(pos)
                // 250 ms: balances lyric highlight responsiveness against CPU/battery usage.
                delay(250)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
    }

    private fun updateActiveLyric(positionMs: Long) {
        val lyrics = currentLyrics.filter { it.timeMs >= 0 }
        if (lyrics.isEmpty()) {
            _activeLyricIndex.value = -1
            return
        }
        var index = lyrics.indexOfLast { it.timeMs <= positionMs }
        if (index < 0) index = 0
        _activeLyricIndex.value = index
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        stopPositionPolling()
        MediaController.releaseFuture(controllerFuture ?: return)
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(settingsRepository, context.applicationContext) as T
    }
}
