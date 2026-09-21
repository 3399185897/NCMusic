package com.buddy.ncmusic.ui.screens.library

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
            prefs.userStateFlow.collectLatest { state ->
                if (state.isLoggedIn) {
                    loadPlaylists(state.userId)
                    loadHistory(state.userId)
                } else {
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
