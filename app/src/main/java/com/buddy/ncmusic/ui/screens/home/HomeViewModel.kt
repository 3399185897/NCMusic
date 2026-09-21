package com.buddy.ncmusic.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repo get() = NCMusicApp.instance.musicRepository

    private val _dailySongs = MutableStateFlow<List<Song>>(emptyList())
    val dailySongs: StateFlow<List<Song>> = _dailySongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _toplist = MutableStateFlow<List<Playlist>>(emptyList())
    val toplist: StateFlow<List<Playlist>> = _toplist.asStateFlow()

    private val _radarSongs = MutableStateFlow<List<Song>>(emptyList())
    val radarSongs: StateFlow<List<Song>> = _radarSongs.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
        // 监听登录态：登录成功后自动重新加载每日推荐
        viewModelScope.launch {
            NCMusicApp.instance.userPreferences.userStateFlow.collectLatest { state ->
                if (state.isLoggedIn) loadDaily()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            launch { loadPlaylists() }
            launch { loadToplist() }
            launch { loadRadar() }
            loadDaily()
            _loading.value = false
        }
    }

    private suspend fun loadRadar() {
        when (val r = repo.radarSongs()) {
            is ApiResult.Success -> _radarSongs.value = r.data
            is ApiResult.Error -> Unit
        }
    }

    private suspend fun loadPlaylists() {
        when (val r = repo.personalizedPlaylists()) {
            is ApiResult.Success -> _playlists.value = r.data
            is ApiResult.Error -> if (_playlists.value.isEmpty()) _error.value = r.message
        }
    }

    private suspend fun loadToplist() {
        when (val r = repo.toplist()) {
            is ApiResult.Success -> _toplist.value = r.data
            is ApiResult.Error -> Unit
        }
    }

    /** 每日推荐需登录，未登录时静默失败 */
    private suspend fun loadDaily() {
        when (val r = repo.recommendSongs()) {
            is ApiResult.Success -> _dailySongs.value = r.data
            is ApiResult.Error -> Unit
        }
    }
}
