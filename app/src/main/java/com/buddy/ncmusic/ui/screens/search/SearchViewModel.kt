package com.buddy.ncmusic.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.cache.ContentCache
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

    /** 搜索失败的性质，用于区分“可重试的网络错误”和不可重试的数据错误。 */
    private val _errorKind = MutableStateFlow<ApiResult.ErrorKind?>(null)
    val errorKind: StateFlow<ApiResult.ErrorKind?> = _errorKind.asStateFlow()

    /** 是否已经完成过一次有效搜索；用于区分“尚未搜索”和“搜索后确实为空”。 */
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched.asStateFlow()

    private var searchJob: Job? = null

    /**
     * 搜索请求代次。
     *
     * 取消旧协程后，旧协程仍可能执行到 `finally`。若只依赖 Job 状态，
     * 旧请求会把新请求的 searching/error 清掉。每次查询递增代次，
     * 所有状态回写都校验代次，保证只有最新请求能修改 UI。
     */
    private var searchGeneration = 0L

    init {
        loadHot()
        viewModelScope.launch {
            prefs.userStateFlow.collect { s -> _history.value = s.searchHistory }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        val generation = ++searchGeneration
        searchJob?.cancel()
        _searched.value = false
        _error.value = null
        _errorKind.value = null
        if (text.isBlank()) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        _results.value = emptyList()
        // 350ms 防抖：接近 Material 3 的 medium 时长，避免每个拼音声母都发请求。
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            doSearch(text, generation)
        }
    }

    fun searchNow(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        _query.value = kw
        val generation = ++searchGeneration
        searchJob?.cancel()
        _results.value = emptyList()
        _searched.value = false
        _error.value = null
        _errorKind.value = null
        _searching.value = true
        searchJob = viewModelScope.launch {
            prefs.addSearchHistory(kw)
            doSearch(kw, generation)
        }
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { prefs.removeSearchHistory(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { prefs.clearSearchHistory() }
    }

    private suspend fun doSearch(keyword: String, generation: Long) {
        if (generation != searchGeneration) return
        _searching.value = true
        _error.value = null
        _errorKind.value = null
        try {
            when (val r = repo.search(keyword)) {
                is ApiResult.Success -> if (generation == searchGeneration) {
                    _results.value = r.data
                    _searched.value = true
                }

                is ApiResult.Error -> if (generation == searchGeneration) {
                    _results.value = emptyList()
                    _error.value = r.message
                    _errorKind.value = r.kind
                }
            }
        } finally {
            // 只有仍是最新请求才收掉加载态；取消中的旧请求不得干扰新请求。
            if (generation == searchGeneration) _searching.value = false
        }
    }

    private fun loadHot() {
        viewModelScope.launch {
            if (ContentCache.isFresh()) {
                ContentCache.hotWords?.let {
                    _hotWords.value = it.take(20)
                    return@launch
                }
            }
            when (val r = repo.searchHot()) {
                is ApiResult.Success -> {
                    ContentCache.storeHotWords(r.data)
                    _hotWords.value = r.data.take(20)
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
