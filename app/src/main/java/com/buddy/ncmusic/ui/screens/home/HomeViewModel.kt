package com.buddy.ncmusic.ui.screens.home

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.cache.ContentCache
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
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

    private val _dailySongs = MutableStateFlow(ContentCache.dailySongs.orEmpty())
    val dailySongs: StateFlow<List<Song>> = _dailySongs.asStateFlow()

    private val _playlists = MutableStateFlow(ContentCache.personalizedPlaylists.orEmpty())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // 初值直接取全局缓存：即使 ViewModel 被重建，界面也能立刻显示已有榜单
    private val _toplist = MutableStateFlow(ContentCache.toplist ?: ToplistCache.data)
    val toplist: StateFlow<List<Playlist>> = _toplist.asStateFlow()

    private val _radarPlaylists = MutableStateFlow(ContentCache.radarPlaylists.orEmpty())
    val radarPlaylists: StateFlow<List<Playlist>> = _radarPlaylists.asStateFlow()

    /** 下拉刷新中（供官方 PullToRefresh 组件绑定） */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * 上次**刷新完成**的时刻（毫秒）。
     *
     * 网易云的雷达 / 推荐歌单是**按天生成**的：同一天内反复刷新，服务端返回的
     * 就是同一批歌单。用户看不到内容变化，就会认为"刷新没反应"。
     * 这里把刷新时刻暴露给页头，下拉后立刻显示「刚刚更新」——
     * 让"请求确实发出并回来了"这件事可见，而不是靠内容变化去猜。
     */
    private val _lastRefreshedAt = MutableStateFlow(0L)
    val lastRefreshedAt: StateFlow<Long> = _lastRefreshedAt.asStateFlow()

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
        // 监听账号身份变化：登录、退出或切换账号后，个性化区块必须立即
        // 换用新账号的数据，不能继续展示上一位用户的内容。
        viewModelScope.launch {
            NCMusicApp.instance.userPreferences.userStateFlow
                .map { it.isLoggedIn to it.userId }
                .distinctUntilChanged()
                .drop(1) // 首次状态由上面的 load() 负责
                .collectLatest { (isLoggedIn, _) ->
                    if (isLoggedIn) {
                        // 立即清掉上一位账号的内存数据，避免新内容返回前仍短暂
                        // 展示旧账号的每日推荐、推荐歌单和雷达卡片。
                        _dailySongs.value = emptyList()
                        _playlists.value = emptyList()
                        _radarPlaylists.value = emptyList()
                        _sectionState.update {
                            it.copy(
                                daily = SectionStatus.Loading,
                                playlists = SectionStatus.Loading,
                                radar = SectionStatus.Loading,
                            )
                        }
                        coroutineScope {
                            launch { loadDaily(force = true) }
                            launch { loadPlaylists(force = true) }
                            launch { loadRadar(force = true) }
                        }
                    } else {
                        _dailySongs.value = emptyList()
                        _playlists.value = emptyList()
                        _radarPlaylists.value = emptyList()
                        _sectionState.update {
                            it.copy(
                                daily = SectionStatus.Empty,
                                playlists = SectionStatus.Empty,
                                radar = SectionStatus.Empty,
                            )
                        }
                    }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _sectionState.value = HomeSectionState()

            // ⚠️ 关键修正：必须用 coroutineScope 把子任务**全部等完**再收 loading。
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

            _loading.value = false
            retryPersonalizedIfEmpty()
        }
    }

    /**
     * 登录态兜底补载。
     *
     * 冷启动（尤其**覆盖升级后的首次启动**）存在一个时序窗口：
     * `Application.onCreate` 里恢复 Cookie 的协程、首页 `load()` 的四个请求、
     * 以及 DataStore 的首次磁盘读取三者并发。若账号级请求抢在 Cookie 写入
     * `api.cookie` 之前发出，服务端会按游客处理 —— 雷达与推荐歌单返回通用数据
     * 甚至空列表，而这一轮结果不会自己变好。
     *
     * 以前只能靠"退出登录再登一次"来打破这个状态。这里在首轮加载结束后做**一次**
     * 兜底：确认登录态已就绪、且三个个性化区块仍有空缺时，强制重载一轮。
     * 只重试一次，避免在真正的接口故障上反复打请求。
     */
    private var personalizedRetried = false

    private fun retryPersonalizedIfEmpty() {
        if (personalizedRetried) return
        viewModelScope.launch {
            val state = runCatching {
                NCMusicApp.instance.userPreferences.userStateFlow.first()
            }.getOrNull() ?: return@launch
            if (!state.isLoggedIn) return@launch
            val allLoaded = _dailySongs.value.isNotEmpty() &&
                _playlists.value.isNotEmpty() &&
                _radarPlaylists.value.isNotEmpty()
            if (allLoaded) return@launch

            personalizedRetried = true
            // 留一点时间给 Cookie 恢复协程收尾
            delay(400)
            runCatching { repo.ensureCookieRestored() }
            coroutineScope {
                launch { loadDaily(force = true) }
                launch { loadPlaylists(force = true) }
                launch { loadRadar(force = true) }
            }
        }
    }

    /** 只重试指定区域，避免整页重载 */
    fun retrySection(section: HomeSection) {
        viewModelScope.launch {
            when (section) {
                HomeSection.DAILY -> {
                    _sectionState.update { it.copy(daily = SectionStatus.Loading) }
                    loadDaily(force = true)
                }
                HomeSection.PLAYLISTS -> {
                    _sectionState.update { it.copy(playlists = SectionStatus.Loading) }
                    loadPlaylists(force = true)
                }
                HomeSection.RADAR -> {
                    _sectionState.update { it.copy(radar = SectionStatus.Loading) }
                    loadRadar(force = true)
                }
                HomeSection.TOPLIST -> {
                    _sectionState.update { it.copy(toplist = SectionStatus.Loading) }
                    loadToplist(force = true)
                }
            }
        }
    }

    private suspend fun loadRadar(force: Boolean = false) {
        // 用**分区**新鲜度：全局 isFresh() 会被榜单等其它分区的写入误判为"新鲜"，
        // 导致雷达有缓存但实际为空时不再请求。
        if (!force && ContentCache.isFreshRadar()) {
            ContentCache.radarPlaylists?.takeIf { it.isNotEmpty() }?.let { data ->
                _radarPlaylists.value = data
                _sectionState.update {
                    it.copy(radar = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready)
                }
                return
            }
        }
        when (val r = repo.radarPlaylists(limit = 7, refresh = force)) {
            is ApiResult.Success -> {
                val data = r.data.distinctBy { it.id }.take(7)
                // 空结果不覆盖：刷新失败时保留上一次的雷达卡片，
                // 而不是把区块直接抹掉。
                if (data.isEmpty() && _radarPlaylists.value.isNotEmpty()) {
                    _sectionState.update { it.copy(radar = SectionStatus.Ready) }
                    return
                }
                _radarPlaylists.value = data
                ContentCache.storeRadar(data)
                // 反向去重：若推荐歌单先返回，这里把与雷达重复的移除。
                //
                // ⚠️ 去重后必须保证"仍有内容"。曾出现：推荐歌单与雷达高度重合，
                // 过滤后列表变空 → 推荐歌单区块整体消失，用户以为功能坏了。
                // 现在的规则是：去重后不足 4 个就放弃去重，宁可重复也要有内容。
                if (_playlists.value.isNotEmpty()) {
                    val radarIds = data.map { x -> x.id }.toSet()
                    val kept = _playlists.value.filter { it.id !in radarIds }
                    if (kept.size >= 4) _playlists.value = kept
                }
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
            // 与 load() 同理：必须等全部完成再收起刷新指示器，
            // 否则转圈停了、内容却还在陆续到位。
            coroutineScope {
                launch { loadPlaylists(force = true) }
                launch { loadRadar(force = true) }
                loadDaily(force = true)
            }
            _refreshing.value = false
            _lastRefreshedAt.value = System.currentTimeMillis()
        }
    }

    private suspend fun loadPlaylists(force: Boolean = false) {
        if (!force && ContentCache.isFreshPlaylists()) {
            ContentCache.personalizedPlaylists?.takeIf { it.isNotEmpty() }?.let { data ->
                _playlists.value = data
                _sectionState.update {
                    it.copy(
                        playlists = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready,
                    )
                }
                return
            }
        }
        when (val r = repo.recommendPlaylists()) {
            is ApiResult.Success -> {
                // 与雷达区块去重：官方客户端里这两个区块都来自账号口味，
                // 数据源（每日推荐歌单）相同，重复展示没有信息量。
                // 雷达先加载完成时这里过滤；反之由 loadRadar 末尾再做一次。
                // ⚠️ 空结果**不能**覆盖已有内容。
                //
                // 下拉刷新时若这一轮请求恰好返回空（令牌刚被覆盖、被限流、
                // 服务端瞬时异常都会这样），原先会 `_playlists.value = emptyList()`，
                // 首页的推荐歌单整块消失 —— 这就是「刷新后推荐歌单不见」。
                // 现在：空结果视为"这轮没拿到"，保留上一份数据。
                if (r.data.isEmpty()) {
                    _sectionState.update {
                        it.copy(
                            playlists = if (_playlists.value.isEmpty()) {
                                SectionStatus.Empty
                            } else {
                                SectionStatus.Ready
                            },
                        )
                    }
                    return
                }

                val radarIds = _radarPlaylists.value.map { it.id }.toSet()
                val filtered = r.data.filter { it.id !in radarIds }
                val data = if (filtered.size >= 4) filtered else r.data
                _playlists.value = data
                // 缓存存**未过滤**的原始数据
                ContentCache.storePersonalized(r.data)
                _sectionState.update { it.copy(playlists = SectionStatus.Ready) }
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
        if (!force && ContentCache.isFreshToplist()) {
            ContentCache.toplist?.let { data ->
                _toplist.value = data
                ToplistCache.data = data
                ToplistCache.loaded = true
                _sectionState.update {
                    it.copy(
                        toplist = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready,
                    )
                }
                return
            }
        }
        // 命中全局缓存：立即回填并返回，不发起请求、不产生等待
        if (!force && ToplistCache.hasData) {
            if (_toplist.value.isEmpty()) _toplist.value = ToplistCache.data
            return
        }
        when (val r = repo.toplist()) {
            is ApiResult.Success -> {
                _toplist.value = r.data
                ContentCache.storeToplist(r.data)
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
    private suspend fun loadDaily(force: Boolean = false) {
        // 只认「每日推荐自己的」缓存时间戳。
        // 并且缓存为空时继续走网络 —— 空列表可能来自未登录/令牌未就绪的那次请求，
        // 不能在 TTL 内一直把它当成有效结果（这正是"已登录却显示不了每日推荐"的成因）。
        if (!force && ContentCache.isFreshDaily()) {
            ContentCache.dailySongs?.takeIf { it.isNotEmpty() }?.let { data ->
                _dailySongs.value = data
                _sectionState.update {
                    it.copy(daily = if (data.isEmpty()) SectionStatus.Empty else SectionStatus.Ready)
                }
                return
            }
        }
        when (val r = repo.recommendSongs()) {
            is ApiResult.Success -> {
                val data = r.data
                // 空结果不回填：保留上一次的内容（如果有），
                // 避免一次瞬时失败把整块抹掉。
                if (data.isNotEmpty()) {
                    _dailySongs.value = data
                    ContentCache.storeDaily(data)
                    _sectionState.update { it.copy(daily = SectionStatus.Ready) }
                } else if (_dailySongs.value.isEmpty()) {
                    _sectionState.update { it.copy(daily = SectionStatus.Empty) }
                } else {
                    _sectionState.update { it.copy(daily = SectionStatus.Ready) }
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
