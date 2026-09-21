package com.buddy.ncmusic.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repo get() = NCMusicApp.instance.musicRepository
    private val prefs get() = NCMusicApp.instance.userPreferences

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _hotWords = MutableStateFlow<List<String>>(emptyList())
    val hotWords: StateFlow<List<String>> = _hotWords.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _results = MutableStateFlow<List<Song>>(emptyList())
    val results: StateFlow<List<Song>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadHot()
        viewModelScope.launch {
            prefs.userStateFlow.collect { s -> _history.value = s.searchHistory }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _results.value = emptyList()
            _error.value = null
            return
        }
        // 300ms 防抖
        searchJob = viewModelScope.launch {
            delay(300)
            doSearch(text)
        }
    }

    fun searchNow(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        _query.value = kw
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            prefs.addSearchHistory(kw)
            doSearch(kw)
        }
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { prefs.removeSearchHistory(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { prefs.clearSearchHistory() }
    }

    private suspend fun doSearch(keyword: String) {
        _searching.value = true
        _error.value = null
        when (val r = repo.search(keyword)) {
            is ApiResult.Success -> _results.value = r.data
            is ApiResult.Error -> _error.value = r.message
        }
        _searching.value = false
    }

    private fun loadHot() {
        viewModelScope.launch {
            when (val r = repo.searchHot()) {
                is ApiResult.Success -> _hotWords.value = r.data.take(20)
                is ApiResult.Error -> Unit
            }
        }
    }
}
