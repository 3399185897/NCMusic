package com.buddy.ncmusic.ui.screens.library

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val repo get() = NCMusicApp.instance.musicRepository
    private val prefs get() = NCMusicApp.instance.userPreferences

    val userState: Flow<UserState> = prefs.userStateFlow

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            // ⚠️ 必须先把"账号身份"从用户状态里**提取出来并去重**，
            // 再触发加载 —— 不能直接 collect 整个 UserState。
            //
            // 原因：`userStateFlow` 是 **DataStore 全量偏好**的映射，
            // 任何一项偏好变化都会发射新值。最典型的触发源是
            // 播放时的听歌时长累计（每 30 秒写一次 `total_listen_seconds`）——
            // 原先的写法会让歌单与听歌历史**每 30 秒被重新请求一次**，
            // 既是无谓的网络开销，也会造成列表频繁重建、滚动位置丢失。
            //
            // 现在只关心 (isLoggedIn, userId) 这一对身份标识，
            // 用 distinctUntilChanged 收敛：只有**账号真的变了**才重新加载。
            prefs.userStateFlow
                .map { it.isLoggedIn to it.userId }
                .distinctUntilChanged()
                .collectLatest { (loggedIn, uid) ->
                    if (loggedIn && uid > 0) {
                        loadPlaylists(uid)
                        loadHistory(uid)
                    } else {
                        // 退出登录：清空展示数据，避免上一个账号的内容残留
                        _playlists.value = emptyList()
                        _history.value = emptyList()
                    }
                }
        }
    }

    private suspend fun loadPlaylists(uid: Long) {
        _loading.value = true
        when (val r = repo.userPlaylists(uid)) {
            is ApiResult.Success -> _playlists.value = r.data
            is ApiResult.Error -> _playlists.value = emptyList()
        }
        _loading.value = false
    }

    private suspend fun loadHistory(uid: Long) {
        when (val r = repo.playHistory(uid)) {
            is ApiResult.Success -> _history.value = r.data
            is ApiResult.Error -> Unit
        }
    }

    fun createPlaylist(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.createPlaylist(name)) {
                is ApiResult.Success -> {
                    val uid = prefs.userStateFlow.first().userId
                    if (uid > 0) loadPlaylists(uid)
                    onResult(null)
                }
                is ApiResult.Error -> onResult(r.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }
}
