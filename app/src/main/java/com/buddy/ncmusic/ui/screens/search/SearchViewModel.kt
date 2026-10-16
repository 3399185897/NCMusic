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

    /**
     * 失败性质。界面据此区分"网络失败（可重试）"与"服务端返回空结果"。
     * 这是 `ApiResult.ErrorKind` 贯通到 UI 的落点。
     */
    private val _errorKind = MutableStateFlow<ApiResult.ErrorKind?>(null)
    val errorKind: StateFlow<ApiResult.ErrorKind?> = _errorKind.asStateFlow()

    /** 是否已执行过一次搜索（用于区分"还没搜"与"搜了但没结果"） */
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched.asStateFlow()

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
            _errorKind.value = null
            _searched.value = false
            // ⚠️ 必须复位 searching。
            //
            // 原先漏了这一步：若上一次搜索已把 searching 置 true，
            // 随后用户清空输入框时 searchJob 被取消，而被取消的协程
            // **不会执行到 doSearch 末尾的 `_searching.value = false`** ——
            // 于是 searching 永久停在 true，界面顶部进度条一直转。
            _searching.value = false
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

    /**
     * 执行搜索。
     *
     * 用 try/finally 保证 [searching] **在任何路径下都能复位**：
     *  · 正常返回
     *  · 请求抛异常（如未预期的运行时异常）
     *  · 协程被取消（用户改动输入 / 点了别的搜索）
     *
     * 原先没有 finally —— 任一异常路径都会让 searching 卡在 true，
     * 表现为「进度条一直转，但结果永远不会出现」。
     *
     * 注意：CancellationException 必须**继续向上抛**（不吞掉），
     * 否则会破坏结构化并发；finally 会在抛出前正常执行。
     */
    private suspend fun doSearch(keyword: String) {
        _searching.value = true
        _error.value = null
        try {
            when (val r = repo.search(keyword)) {
                is ApiResult.Success -> {
                    _results.value = r.data
                    _error.value = null
                    _errorKind.value = null
                }
                is ApiResult.Error -> {
                    // 保留上一次结果（若有）不清空 —— 空结果会与"搜索失败"混淆；
                    // 错误仅通过 _error 暴露，由界面区分展示。
                    _error.value = r.message
                    _errorKind.value = r.kind
                }
            }
        } finally {
            _searching.value = false
            // 标记"已搜过"：即使被取消也说明用户发起过搜索，
            // 界面据此决定是否展示空态文案（而非把空白当加载中）
            _searched.value = true
        }
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
