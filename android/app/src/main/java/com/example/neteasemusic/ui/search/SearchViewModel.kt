package com.example.neteasemusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neteasemusic.data.api.RetrofitClient
import com.example.neteasemusic.data.model.SearchSong
import com.example.neteasemusic.data.repository.MusicRepository
import com.example.neteasemusic.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val songs: List<SearchSong>) : SearchUiState()
    data class Empty(val keyword: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun search() {
        val keyword = _query.value.trim()
        if (keyword.isEmpty()) return

        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.value
                val repo = MusicRepository(RetrofitClient.getService(settings.apiUrl))
                val songs = repo.searchSongs(keyword, settings.searchLimit)
                _uiState.value = if (songs.isEmpty()) {
                    SearchUiState.Empty(keyword)
                } else {
                    SearchUiState.Success(songs)
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(
                    "无法连接到音乐服务器，请检查 API 地址是否正确：${e.localizedMessage}"
                )
            }
        }
    }

    class Factory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(settingsRepository) as T
    }
}
