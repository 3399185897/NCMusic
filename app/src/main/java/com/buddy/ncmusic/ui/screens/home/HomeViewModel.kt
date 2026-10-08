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

    // 初值直接取全局缓存：即使 ViewModel 被重建，界面也能立刻显示已有榜单
    private val _toplist = MutableStateFlow(ToplistCache.data)
    val toplist: StateFlow<List<Playlist>> = _toplist.asStateFlow()

    private val _radarPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val radarPlaylists: StateFlow<List<Playlist>> = _radarPlaylists.asStateFlow()

    /** 下拉刷新中（供官方 PullToRefresh 组件绑定） */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

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
        when (val r = repo.radarPlaylists()) {
            is ApiResult.Success -> _radarPlaylists.value = r.data
            is ApiResult.Error -> Unit
        }
    }

    /**
     * 下拉刷新：同时刷新推荐歌单与个性化推荐。
     * 用 [_refreshing] 驱动官方 PullToRefresh 组件的指示器。
     */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            launch { loadPlaylists() }
            launch { loadRadar() }
            loadDaily()
            _refreshing.value = false
        }
    }

    private suspend fun loadPlaylists() {
        when (val r = repo.personalizedPlaylists()) {
            is ApiResult.Success -> _playlists.value = r.data
            is ApiResult.Error -> if (_playlists.value.isEmpty()) _error.value = r.message
        }
    }

    private suspend fun loadToplist(force: Boolean = false) {
        // 命中全局缓存：立即回填并返回，不发起请求、不产生等待
        if (!force && ToplistCache.hasData) {
            if (_toplist.value.isEmpty()) _toplist.value = ToplistCache.data
            return
        }
        when (val r = repo.toplist()) {
            is ApiResult.Success -> {
                _toplist.value = r.data
                ToplistCache.data = r.data
                ToplistCache.loaded = true
            }
            is ApiResult.Error -> {
                // 请求失败时退回缓存，避免榜单区变成空白
                if (ToplistCache.hasData) _toplist.value = ToplistCache.data
            }
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
