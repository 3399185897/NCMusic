package com.buddy.ncmusic.ui.screens.home

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
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

/**
 * 首页各区域的加载状态。
 *
 * 用「区域 -> 状态」的映射而非单一布尔量，
 * 是为了让每个区块能独立地处于「加载中 / 成功 / 失败」，
 * 并支持**只重试失败的那一个**。
 */
data class HomeSectionState(
    val daily: SectionStatus = SectionStatus.Loading,
    val playlists: SectionStatus = SectionStatus.Loading,
    val radar: SectionStatus = SectionStatus.Loading,
    val toplist: SectionStatus = SectionStatus.Loading,
)

/**
 * 单个区域的状态。
 *
 * [Empty] 是**成功**的一种：服务端正常响应但确实没有数据。
 * 这与 [Failed] 必须区分 —— 前者应显示空态文案，后者才提供重试按钮。
 * 这正是 `ApiResult` 补充 `ErrorKind` 后才有能力表达的差异。
 */
sealed interface SectionStatus {
    data object Loading : SectionStatus
    data object Ready : SectionStatus
    data object Empty : SectionStatus
    data class Failed(val message: String, val canRetry: Boolean = true) : SectionStatus
}

/** 可单独重试的首页区域 */
enum class HomeSection { DAILY, PLAYLISTS, RADAR, TOPLIST }

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

    /**
     * 各区域的**独立**加载 / 失败状态。
     *
     * 原先只有一个全局 `loading` + `error`，导致：
     *  1) 某个区域先失败就把整页判为失败，其余区域即使正常也不显示；
     *  2) 无法针对单个区域做「重试」，只能整页重载。
     *
     * 首页由四个互不相干的区块组成（每日推荐 / 推荐歌单 / 雷达 / 榜单），
     * 它们的可用性本就应当各自独立 —— 雷达挂了不该拖累歌单。
     */
    private val _sectionState = MutableStateFlow(HomeSectionState())
    val sectionState: StateFlow<HomeSectionState> = _sectionState.asStateFlow()

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
            _sectionState.value = HomeSectionState()
            try {
                // ⚠️ 用 coroutineScope 把子任务**全部等完**再收 loading。
                //
                // 原先写的是 `launch { ... }` —— launch 是异步的、不返回结果，
                // 于是 `_loading.value = false` 会在子请求还在飞的时候就执行，
                // 表现为「首页瞬间结束加载 → 内容空白一两秒后突然出现」。
                //
                // coroutineScope 会挂起直到其内部所有子协程完成，语义上正是所需。
                coroutineScope {
                    launch { loadPlaylists() }
                    launch { loadToplist() }
                    launch { loadRadar() }
                    loadDaily()
                }
            } finally {
                // try/finally 保证**任何路径**都会收掉加载态：
                //  · 正常完成
                //  · 子请求抛异常（如未预期的运行时异常）
                //  · 协程被取消（页面销毁 / 重复触发 refresh）
                //
                // 原先没有 finally —— 任一异常都会让首页**永久停在加载转圈**，
                // 只能杀进程恢复。CancellationException 仍会向上传播（不吞掉），
                // finally 在其之前执行。
                _loading.value = false
            }
        }
    }

    /** 只重试指定区域，避免整页重载 */
    fun retrySection(section: HomeSection) {
        viewModelScope.launch {
            when (section) {
                HomeSection.DAILY -> {
                    _sectionState.update { it.copy(daily = SectionStatus.Loading) }
                    loadDaily()
                }
                HomeSection.PLAYLISTS -> {
                    _sectionState.update { it.copy(playlists = SectionStatus.Loading) }
                    loadPlaylists()
                }
                HomeSection.RADAR -> {
                    _sectionState.update { it.copy(radar = SectionStatus.Loading) }
                    loadRadar()
                }
                HomeSection.TOPLIST -> {
                    _sectionState.update { it.copy(toplist = SectionStatus.Loading) }
                    loadToplist(force = true)
                }
            }
        }
    }

    private suspend fun loadRadar() {
        when (val r = repo.radarPlaylists()) {
            is ApiResult.Success -> {
                val data = r.data
                _radarPlaylists.value = data
                _sectionState.update {
                    it.copy(radar = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready)
                }
            }
            is ApiResult.Error -> {
                // 雷达需要登录，AUTH 类失败不提示重试（重试也不会成功）
                _sectionState.update {
                    it.copy(
                        radar = SectionStatus.Failed(
                            message = r.message,
                            canRetry = r.kind != ApiResult.ErrorKind.AUTH,
                        ),
                    )
                }
            }
        }
    }

    /**
     * 下拉刷新：同时刷新推荐歌单与个性化推荐。
     * 用 [_refreshing] 驱动官方 PullToRefresh 组件的指示器。
     */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                // 与 load() 同理：必须等全部完成再收起刷新指示器，
                // 否则转圈停了、内容却还在陆续到位。
                coroutineScope {
                    launch { loadPlaylists() }
                    launch { loadRadar() }
                    loadDaily()
                }
            } finally {
                // 同 load()：异常/取消路径也必须收起指示器，
                // 否则下拉刷新会一直转，且无法再次触发。
                _refreshing.value = false
            }
        }
    }

    private suspend fun loadPlaylists() {
        when (val r = repo.personalizedPlaylists()) {
            is ApiResult.Success -> {
                val data = r.data
                _playlists.value = data
                _sectionState.update {
                    it.copy(
                        playlists = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready,
                    )
                }
            }
            is ApiResult.Error -> {
                // 已有数据时不覆盖为失败：展示旧数据比展示错误更有用
                if (_playlists.value.isEmpty()) {
                    _sectionState.update {
                        it.copy(playlists = SectionStatus.Failed(r.message))
                    }
                } else {
                    _sectionState.update { it.copy(playlists = SectionStatus.Ready) }
                }
            }
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
                _sectionState.update {
                    it.copy(
                        toplist = if (r.data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready,
                    )
                }
            }
            is ApiResult.Error -> {
                // 请求失败时退回缓存，避免榜单区变成空白
                if (ToplistCache.hasData) {
                    _toplist.value = ToplistCache.data
                    _sectionState.update { it.copy(toplist = SectionStatus.Ready) }
                } else {
                    _sectionState.update { it.copy(toplist = SectionStatus.Failed(r.message)) }
                }
            }
        }
    }

    /** 每日推荐需登录，未登录时静默失败 */
    private suspend fun loadDaily() {
        when (val r = repo.recommendSongs()) {
            is ApiResult.Success -> {
                val data = r.data
                _dailySongs.value = data
                _sectionState.update {
                    it.copy(daily = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready)
                }
            }
            is ApiResult.Error -> {
                if (_dailySongs.value.isEmpty()) {
                    // 每日推荐需登录：AUTH 类失败应引导登录而非提供重试
                    _sectionState.update {
                        it.copy(
                            daily = SectionStatus.Failed(
                                message = r.message,
                                canRetry = r.kind != ApiResult.ErrorKind.AUTH,
                            ),
                        )
                    }
                } else {
                    _sectionState.update { it.copy(daily = SectionStatus.Ready) }
                }
            }
        }
    }
}
