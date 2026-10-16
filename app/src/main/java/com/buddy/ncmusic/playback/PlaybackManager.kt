package com.buddy.ncmusic.playback

import com.buddy.ncmusic.util.AudioQuality
import com.buddy.ncmusic.ui.util.isReduceMotionEnabled
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiResult
import com.buddy.ncmusic.util.LyricLine
import com.buddy.ncmusic.util.LyricParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放管理器：持有 ExoPlayer、播放队列与播放状态，
 * 通过 StateFlow 向 Compose UI 暴露单一数据源。UI 与前台服务共用此单例。
 */
object PlaybackManager {

    enum class PlayMode { SEQUENCE, LIST_LOOP, SINGLE_LOOP, SHUFFLE }

    data class PlaybackState(
        val song: Song? = null,
        val isPlaying: Boolean = false,
        val position: Long = 0L,
        val duration: Long = 0L,
        val mode: PlayMode = PlayMode.LIST_LOOP,
        val lyrics: List<LyricLine> = emptyList(),
        val isLoading: Boolean = false,
        val isLiked: Boolean = false,
        val quality: String = "standard",
        /**
         * 播放失败的原因（供 UI 以 Toast 形式提示）。
         *
         * 之所以需要它：解析播放地址失败时若静默返回，
         * 用户看到的是「点了播放毫无反应」，无法判断是网络、
         * 未登录还是版权受限 —— 体验上等同于「功能坏了」。
         */
        val errorMessage: String? = null,

        /**
         * 信息性提示（**非错误**），例如"已自动降级到极高音质"。
         *
         * ## 为什么必须与 [errorMessage] 分开
         *
         * 音质降级提示原先也写进 `errorMessage`，而该字段在三个地方会被清空：
         *  · `playAt` 中解析成功后 `errorMessage = null`
         *  · `STATE_READY` 时清理上一曲残留的错误
         *  · `onIsPlayingChanged(true)` 兜底清理
         *
         * 结果是降级提示刚写入就被紧随其后的清理抹掉，
         * **用户从未看到过它** —— 而这正是当初加它的目的。
         *
         * 语义上两者本就不同：错误需要用户关注并可能采取动作，
         * 提示只是告知，不应随"播放成功"被清除。
         */
        val noticeMessage: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlaybackState())

    /**
     * 完整播放状态。
     *
     * ⚠️ **订阅它就等于订阅全部 10 个字段**，其中包括每秒多次变化的 `position`。
     * 任何只在组合作用域里读一两个字段的组件（例如只想知道"有没有当前歌曲"）
     * 若直接订阅它，就会**随着播放进度每帧重组**。
     *
     * 因此下列细粒度 Flow 才是多数消费端的正确选择；
     * 只有真正需要 `position`（播放器页、歌词页、进度环）才订阅本对象。
     */
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // ============================================================
    // 细粒度派生流
    //
    // 设计意图：把"播放状态"按**变化频率**切分。
    // `position` 是唯一的高频字段（约每 200ms~1s 一次），
    // 其余字段（歌曲、开关、歌词、错误）都是低频的。
    //
    // 消费端订阅自己需要的那一条，就能把 position 的高频更新
    // 完全隔离在少数几个真正需要它的组件内。
    //
    // 全部用 `distinctUntilChanged()`：StateFlow 已经会去重，
    // 但 `map` 之后会把"状态对象变化"转成"派生值"，
    // 显式去重可确保下游只在**派生值真正改变**时收到通知。
    // ============================================================

    /** 是否存在当前歌曲（供底部安全区等"只关心有无"的场景使用） */
    val hasSongFlow: StateFlow<Boolean> = _state
        .map { it.song != null }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 当前歌曲 id；无歌曲时为 -1（供列表项判断"是否当前曲"） */
    val currentSongIdFlow: StateFlow<Long> = _state
        .map { it.song?.id ?: -1L }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, -1L)

    /** 当前歌曲对象（供悬浮岛等需要完整歌曲信息的场景） */
    val currentSongFlow: StateFlow<Song?> = _state
        .map { it.song }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** 是否正在播放 */
    val isPlayingFlow: StateFlow<Boolean> = _state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 是否正在加载（缓冲 / 解析地址中） */
    val isLoadingFlow: StateFlow<Boolean> = _state
        .map { it.isLoading }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 播放进度（毫秒）—— 唯一的高频流，仅播放器 / 歌词 / 进度环订阅 */
    val positionFlow: StateFlow<Long> = _state
        .map { it.position }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    /** 播放失败信息（供全局 Toast 使用，不随进度重组） */
    val errorMessageFlow: StateFlow<String?> = _state
        .map { it.errorMessage }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** 信息性提示（非错误），与 [errorMessageFlow] 分离管理 */
    val noticeMessageFlow: StateFlow<String?> = _state
        .map { it.noticeMessage }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _queue = mutableListOf<Song>()
    private var currentIndex = -1

    /**
     * 播放请求序号。
     *
     * 快速切歌时会有多个 `resolveUri` 请求并发在飞，而**先发出的未必先返回**
     * （网络抖动、本地与在线路径耗时差异）。若不做校验，
     * 迟到的旧响应会执行 `setMediaItem`，把已经切到的新歌覆盖掉 ——
     * 表现为「点了 B 却播回 A」。
     *
     * 每次 [playAt] 自增该序号；响应回来后只有**序号仍等于当前值**才允许继续。
     */
    private var playRequestSeq = 0

    /**
     * 当前在飞的播放准备任务。
     *
     * 与外层 `scope` 分开持有，是为了能**主动取消**：
     * 旧请求在 `resolveUri` 的网络等待中即可被打断，省掉一次无用请求。
     */
    private var playJob: Job? = null

    /**
     * 用户当前的**播放意图**（true = 想播，false = 想停）。
     *
     * 必要性：播放地址解析是异步的，期间用户完全可能按暂停。
     * 若请求返回后无条件 `player.play()`，用户的暂停操作就被吞掉了 ——
     * 表现为「按了暂停，过一会儿自己又响起来」。
     *
     * [playAt] 开始时置 true；[togglePlayPause] 暂停时置 false；
     * 请求返回后**先检查该意图**再无歧义地决定 play 还是 pause。
     */
    @Volatile
    private var desiredPlaying = false

    /**
     * 是否正处于「准备新曲」的过程中。
     *
     * 必要性：[playAt] 开头会调用 `player.stop()`，而 stop 会触发
     * [Player.STATE_IDLE]。若该回调异步执行，就会在 `isLoading = true`
     * 之后把它改回 false —— 表现为「切歌后不显示加载，界面像是没反应」。
     *
     * 用该标记把「有意的重新准备」与「播放器真的空闲了」区分开。
     */
    @Volatile
    private var preparingNewTrack = false

    private val likedIds = mutableSetOf<Long>()

    /** 用户手动选择的音质；为 null 时按网络类型自动选择 */
    @Volatile
    private var manualQuality: String? = null

    /** 定时关闭任务 */
    private var sleepTimerJob: Job? = null

    /** 待累计的听歌毫秒数（每 30 秒落盘一次） */
    private var pendingListenMs = 0L

    lateinit var player: ExoPlayer
        private set

    private val repository get() = NCMusicApp.instance.musicRepository
    private val appContext get() = NCMusicApp.instance

    /** 解码模式：true = 软件解码优先（允许回退） */
    @Volatile
    private var decodeModeSoft = false

    /** USB 独占是否开启 */
    @Volatile
    private var usbExclusiveEnabled = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.update { it.copy(isLoading = true) }

                Player.STATE_READY -> {
                    preparingNewTrack = false
                    _state.update {
                        it.copy(
                            duration = player.duration.coerceAtLeast(0L),
                            isLoading = false,
                            // 进入可播放状态即清错误：否则上一曲的报错会一直残留
                            errorMessage = null,
                        )
                    }
                    // 此时 audioSessionId 才有效，把音效挂到真实会话上（EQ / 响度）
                    runCatching { AudioEffects.attach(player.audioSessionId) }
                }

                Player.STATE_ENDED -> {
                    _state.update { it.copy(isPlaying = false) }
                    if (_state.value.mode == PlayMode.SINGLE_LOOP) {
                        playAt(currentIndex)
                    } else {
                        next()
                    }
                }

                /**
                 * IDLE 表示播放器已停止或尚未准备。
                 *
                 * 原先未处理该分支：一旦进入 IDLE 而 isLoading 仍为 true，
                 * 界面就会**永久停在加载转圈**上。
                 * 这里统一收口 —— 只要不是正在准备新曲，就不该继续转圈。
                 */
                Player.STATE_IDLE -> {
                    // 仅在「非准备中」时收掉加载态：
                    // playAt 主动 stop() 也会走到这里，那属于正常流程，不能清。
                    if (!preparingNewTrack) {
                        _state.update { it.copy(isLoading = false, isPlaying = false) }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 通知栏 / 耳机等外部操作会直接作用于 ExoPlayer，这里保持状态同步
            _state.update {
                it.copy(
                    isPlaying = isPlaying,
                    // 真正开始播放时说明一切就绪，兜底清掉加载态与错误，
                    // 防止个别机型不上报 STATE_READY 导致界面卡在转圈
                    isLoading = if (isPlaying) false else it.isLoading,
                    errorMessage = if (isPlaying) null else it.errorMessage,
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // 原先只清 isLoading，不给任何原因 ——
            // 用户看到「转圈停了但没声音」，无法判断发生了什么。
            // 现在统一转成可读文案，与「地址解析失败」走同一套提示。
            _state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = playErrorMessageOf(error),
                )
            }
        }
    }

    private fun createPlayer() {
        player = ExoPlayer.Builder(appContext).build()
        AudioEffects.attach(player.audioSessionId)
        player.addListener(playerListener)
    }

    /** 切换解码模式（重建播放器使配置生效） */
    fun applyDecodeMode(soft: Boolean) {
        decodeModeSoft = soft
        if (!::player.isInitialized) return
        val resume = _queue.isNotEmpty() && currentIndex >= 0
        player.release()
        createPlayer()
        // MediaSession 持有的是旧 player 的包装，重启服务以重建会话
        runCatching {
            val ctx = appContext
            ctx.stopService(Intent(ctx, PlaybackService::class.java))
            ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
        }
        if (resume) playAt(currentIndex)
    }

    /** USB 独占开关：独占时旁路 EQ / 响度增强，保持输出纯净 */
    fun applyUsbExclusive(enabled: Boolean) {
        usbExclusiveEnabled = enabled
        if (enabled) {
            AudioEffects.setEqEnabled(false)
            AudioEffects.setVolumeNormalize(false)
        } else {
            scope.launch {
                val s = appContext.userPreferences.userStateFlow.first()
                AudioEffects.setEqEnabled(s.eqEnabled)
                AudioEffects.setVolumeNormalize(s.volumeNormalize)
            }
        }
    }

    fun ensureInit() {
        if (::player.isInitialized) return
        createPlayer()

        // 进度刷新 + 听歌时长累计
        scope.launch {
            while (isActive) {
                val s = _state.value
                if (::player.isInitialized && player.isPlaying && !s.isLoading) {
                    _state.update { it.copy(position = player.currentPosition) }
                    pendingListenMs += 500L
                    if (pendingListenMs >= 30_000L) {
                        val sec = pendingListenMs / 1000
                        pendingListenMs = 0L
                        appContext.userPreferences.addListenSeconds(sec)
                    }
                }
                delay(500)
            }
        }

        // 设置与登录态监听：EQ / 音量均衡 / 我喜欢列表
        scope.launch {
            var lastLoggedIn = false
            var lastUserId = 0L
            appContext.userPreferences.userStateFlow.collect { s ->
                if (!usbExclusiveEnabled) {
                    AudioEffects.setEqEnabled(s.eqEnabled)
                    AudioEffects.setVolumeNormalize(s.volumeNormalize)
                }
                if (s.isLoggedIn != lastLoggedIn || s.userId != lastUserId) {
                    lastLoggedIn = s.isLoggedIn
                    lastUserId = s.userId
                    likedIds.clear()
                    if (s.isLoggedIn) likedIds.addAll(loadLikedIds(s.userId))
                    _state.update { st ->
                        st.copy(isLiked = st.song?.id?.let { it in likedIds } ?: false)
                    }
                }
            }
        }
    }

    /** 播放整个队列，从 startIndex 开始 */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        ensureInit()
        _queue.clear()
        _queue.addAll(songs)
        playAt(startIndex)
    }

    fun playAt(index: Int, fadeIn: Boolean = false, fadeInMs: Long = 0L) {
        ensureInit()
        if (index !in _queue.indices) return
        currentIndex = index
        val song = _queue[index]
        // 标记进入准备流程：使 stop() 触发的 STATE_IDLE 不误清加载态
        preparingNewTrack = true
        // ⚠️ 此处**不再立即 stop()**。
        //
        // 原先在函数开头就停掉播放器，而紧接着是异步的地址解析 ——
        // 解析期间播放器已停，音频完全静音（配合 switchTo 的提前淡出
        // 更会叠加出一段"长静音"）。
        //
        // 现在把 stop() 推迟到**地址就绪之后**（见下方协程）：
        // 解析期间旧曲继续播放，用户感知到的切换延迟显著降低。
        _state.update {
            it.copy(
                song = song,
                isLoading = true,
                position = 0L,
                duration = 0L,
                lyrics = emptyList(),
                isLiked = song.id in likedIds,
                // 切歌时清掉上一曲的提示：降级信息只对当次播放有意义
                noticeMessage = null,
                // ⚠️ 这里**不再提前把 isPlaying 置为 true**。
                //
                // 原先在这一步就置 true，而此刻播放地址还没解析、播放器还没准备，
                // 于是「是否在播放」变成了一个**预测值**而非事实：
                //  · 地址解析失败 -> 状态卡在"正在播放"，界面按钮显示暂停图标
                //  · setMediaItem/prepare 抛异常 -> 同上
                //  · 用户在解析期间按暂停 -> 请求完成后仍会 play()，暂停被吞掉
                //
                // 正确做法：以 Media3 的 onIsPlayingChanged 为准（见 playerListener）。
                // 这里只声明"准备中"，同时记录用户的播放意图。
                isPlaying = false,
            )
        }
        // 记录本次播放意图：用户可能在解析完成前就按下暂停，
        // 该意图必须能覆盖尚未完成的请求（见 desiredPlaying）。
        desiredPlaying = true
        startForegroundService()

        // ⚠️ 顺序很关键：**必须先自增序号，再取消旧任务**。
        //
        // 被取消的旧协程会执行其 `finally` 块，其中判断
        // `if (seq == playRequestSeq)` 才清 `preparingNewTrack`。
        // 若先 cancel 后自增，旧协程看到的仍是**旧的** playRequestSeq
        // （即等于它自己的 seq），于是它会误清掉本次新请求刚设下的标记 ——
        // 导致 STATE_IDLE 分支提前收掉加载态，界面不显示转圈。
        val seq = ++playRequestSeq

        // 取消上一次尚未完成的准备流程（它已过期，但还在等网络）
        playJob?.cancel()
        playJob = scope.launch {
            try {
                val uri = resolveUri(song)

                // 关键校验：解析期间用户可能已经又切了歌。
                // 此时本协程即便未被取消（例如取消发生在挂起点之后），
                // 也不能再改播放器 —— 序号已过期。
                if (seq != playRequestSeq) return@launch

                // ---------- 失败分支 1：拿不到播放地址 ----------
                if (uri == null) {
                    // 用户已切歌但当前歌曲无法播放：旧曲不应继续出声
                    runCatching { if (::player.isInitialized) player.stop() }
                    // 只有仍是最新请求才允许改状态，避免旧请求污染界面
                    if (seq == playRequestSeq) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                // isPlaying 保持 false —— 从头到尾就没播起来
                                isPlaying = false,
                                errorMessage = playFailureReason(),
                            )
                        }
                    }
                    return@launch
                }

                // 成功进入播放流程时清掉上一次的错误
                _state.update { it.copy(errorMessage = null) }

                // ---------- 失败分支 2：设置媒体项 / 准备抛异常 ----------
                val started = runCatching {
                    // 二次校验：setMediaItem 之前再确认一次，缩小竞态窗口
                    if (seq != playRequestSeq) return@runCatching false

                    // ★ 地址已就绪，此刻才淡出旧曲并切换。
                    // 这样"静音"仅持续淡出时长本身，不叠加解析耗时。
                    if (fadeIn && fadeInMs > 0 && player.isPlaying) {
                        runCatching { fadeVolume(player.volume.coerceIn(0f, 1f), 0f, fadeInMs) }
                    }

                    player.stop()
                    player.setMediaItem(MediaItem.fromUri(uri))
                    player.prepare()
                    true
                }.getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    runCatching { if (::player.isInitialized) player.stop() }
                    if (seq == playRequestSeq) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                // 关键：此前未复位 isPlaying，导致界面永久停留在
                                // "正在播放"（按钮显示暂停图标），而实际毫无声音
                                isPlaying = false,
                                errorMessage = playErrorMessageOf(e),
                            )
                        }
                    }
                    false
                }
                if (!started) return@launch

                // ---------- 应用用户的播放/暂停意图 ----------
                //
                // 到这里媒体项已就绪。但**解析期间用户可能按过暂停** ——
                // 必须尊重那一刻的意图，而不是无脑 play()。
                // 该判断放在 setMediaItem/prepare 之后，确保"暂停"作用于已就绪的项。
                applyDesiredPlayState(seq, fadeIn, fadeInMs)

                // 歌词同样要校验：切歌后旧歌词不能覆盖新歌歌词
                if (seq == playRequestSeq) {
                    loadLyrics(song.id, seq)
                }
            } finally {
                // 无论成功、失败还是被取消，都要清掉"准备中"标记 ——
                // 否则 STATE_IDLE 分支会永远被它挡住，加载态无法收口。
                // 但**只有仍是最新请求**才清，避免旧协程误清新请求的标记。
                if (seq == playRequestSeq) {
                    preparingNewTrack = false
                }
            }
        }
    }

    /**
     * 按用户最新意图决定是否真正开始播放。
     *
     * 与 [desiredPlaying] 配合，解决「解析期间按暂停、请求返回后仍自动播放」。
     *
     * @param seq 调用方持有的请求序号；期间若已切歌则不做任何操作
     */
    private fun applyDesiredPlayState(seq: Int, fadeIn: Boolean, fadeInMs: Long) {
        if (seq != playRequestSeq) return

        if (!desiredPlaying) {
            // 用户在等待期间已选择暂停：媒体项已就绪但**不要起播**。
            // 显式 pause 一次以对齐 Media3 内部状态，避免它按"自动播放"处理。
            runCatching { player.pause() }
            _state.update { it.copy(isLoading = false, isPlaying = false) }
            return
        }

        runCatching {
            // 淡入：加载完成后从 0 音量渐升到正常
            if (fadeIn) player.volume = 0f
            player.play()
        }
        // isPlaying 交给 onIsPlayingChanged 上报，这里不预测
        if (fadeIn && fadeInMs > 0) {
            scope.launch { fadeVolume(0f, 1f, fadeInMs) }
        }
    }

    /** 是否存在下一曲（供 MediaSession 判断按钮可用性） */
    fun canNext(): Boolean =
        _queue.isNotEmpty() &&
            (currentIndex < _queue.lastIndex || _state.value.mode != PlayMode.SEQUENCE)

    /** 是否存在上一曲 */
    fun canPrevious(): Boolean = _queue.isNotEmpty()

    /**
     * 解析播放地址。
     *
     * 同时把**服务端实际给出的音质**写回 state ——
     * 之前这里写的是 `quality = q`（请求值），降级后界面就会显示
     * 「实际 128k，标签写着无损」这种虚假信息。
     *
     * @return 可播放 URI；失败返回 null（原因由调用方另行取用）
     */
    private suspend fun resolveUri(song: Song): String? {
        song.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            // 本地文件：音质标签由扩展名推断，不走在线档位
            _state.update { it.copy(quality = "local") }
            return localUriOf(path)
        }

        val requested = resolveQuality()
        when (val r = repository.songUrl(song.id, requested)) {
            is ApiResult.Success -> {
                val resolved = r.data
                // 记录**真实命中**的档位
                _state.update { it.copy(quality = resolved.level) }

                // 实际低于请求时给出一次性提示，避免用户困惑「我明明选了无损」
                if (resolved.level != resolved.requestedLevel) {
                    // 写入 noticeMessage 而非 errorMessage ——
                    // 后者会被随后的清理逻辑抹掉，且语义上这不是"错误"
                    _state.update {
                        it.copy(
                            noticeMessage = "当前账号无「${qualityLabelOf(resolved.requestedLevel)}」" +
                                "权限，已自动切换为「${qualityLabelOf(resolved.level)}」",
                        )
                    }
                }
                return resolved.url
            }
            is ApiResult.Error -> {
                // 保留请求音质用于界面展示（此时尚未真正播放）
                _state.update { it.copy(quality = requested) }
                return null
            }
        }
    }

    /**
     * 把 [Song.localPath] 转成可播放的 URI 字符串。
     *
     * ## 为什么不能一律 `File(path).toURI()`
     *
     * `localPath` 实际存在**三种**形态（来源见 `LocalMusicScreen`）：
     *
     * | 来源 | 形态 | 位置 |
     * |---|---|---|
     * | 本地媒体扫描 | 普通文件路径 | `scanLocal` 赋 `path` |
     * | 已下载（SAF 目录） | **`content://` 文档 URI** | `docUri.toString()` |
     * | 本地文件构造 | `File.absolutePath` | `fileToSong()` |
     *
     * 对第 2 种，`java.io.File("content://...")` 会拼出
     * `file:///content:/media/external/…` 这种**协议错乱**的地址 ——
     * ExoPlayer 打不开，表现为「本地下载的歌点了没反应」。
     *
     * ## 处理规则
     *
     * 含 `://` 即为**已带协议的 URI**，原样返回（交给 ContentResolver 处理）；
     * 否则视为文件系统路径，用 `File.toURI()` 正确转义（空格、中文等）。
     */
    private fun localUriOf(path: String): String {
        val trimmed = path.trim()
        if (trimmed.contains("://")) return trimmed
        return java.io.File(trimmed).toURI().toString()
    }

    /**
     * 音质档位的中文标签。
     *
     * ⚠️ 不再自行维护映射表 —— 原先这里写死了各档名称，
     * 其中 `hires` 还是过时的「Hi-Res」（官方现叫「高清臻音」），
     * 与播放器面板显示的档位名对不上。
     * 现统一取自 [AudioQuality]，保证全应用同一档位只有一种叫法。
     */
    private fun qualityLabelOf(level: String): String = when (level) {
        // 本地文件没有在线档位概念，单独处理
        "local" -> "本地"
        else -> AudioQuality.of(level)?.label ?: level
    }

    /**
     * 把异常统一转成用户可读的失败原因。
     *
     * 之前「地址解析失败」与「播放器报错」走的是两套互不相干的路径：
     * 前者有文案，后者完全静默。这里收口成一个入口，
     * 保证 [PlaybackState.errorMessage]、`isLoading`、`isPlaying` 三者语义一致。
     */
    private fun playErrorMessageOf(e: Throwable): String = when (e) {
        is PlaybackException -> when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "网络连接失败，请检查网络后重试"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            -> "音频源不可用（可能已失效或受版权限制）"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> "音频文件已损坏或格式不受支持"
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            -> "解码失败，可尝试切换音质或关闭音效"
            else -> "播放出错（错误码 ${e.errorCode}）"
        }
        else -> "播放失败：${e.message ?: e.javaClass.simpleName}"
    }

    /**
     * 生成播放失败的用户可读原因。
     *
     * 网易云对**未登录**用户不返回播放地址（实测所有音质档均返回空 url），
     * 这是服务端策略而非客户端缺陷 —— 因此这里把最常见的原因单独识别出来，
     * 直接引导用户登录，而不是给出含糊的「播放失败」。
     */
    private suspend fun playFailureReason(): String {
        val loggedIn = runCatching {
            appContext.userPreferences.userStateFlow.first().isLoggedIn
        }.getOrDefault(false)
        return if (loggedIn) {
            "该歌曲暂无可用播放源（可能受版权限制或需要会员）"
        } else {
            "未登录状态下无法获取播放地址，请先登录后再试"
        }
    }

    /** 消费并清除播放错误（UI 弹出提示后调用，避免重组时重复提示） */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** 消费并清除信息性提示（与 [clearError] 分开，互不干扰） */
    fun clearNotice() {
        _state.update { it.copy(noticeMessage = null) }
    }

    /** 决定当前使用的音质：手动选择优先，否则按网络类型（WiFi / 流量）自动取上限 */
    private suspend fun resolveQuality(): String {
        manualQuality?.let { return it }
        val ps = appContext.userPreferences.userStateFlow.first()
        return if (isWifi()) ps.wifiQuality else ps.mobileQuality
    }

    /**
     * 加载「我喜欢」的歌曲 id：
     * 优先使用 likelist；失败或为空时回退到「我喜欢的音乐」歌单的 trackIds。
     */
    private suspend fun loadLikedIds(uid: Long): Set<Long> {
        when (val r = repository.likelist(uid)) {
            is ApiResult.Success -> if (r.data.isNotEmpty()) return r.data.toSet()
            is ApiResult.Error -> Unit
        }
        return runCatching {
            val playlists = (repository.userPlaylists(uid) as? ApiResult.Success)?.data
                ?: return emptySet()
            val liked = playlists.firstOrNull { it.specialType == 5 } ?: return emptySet()
            val detail = (repository.playlistDetail(liked.id) as? ApiResult.Success)?.data
                ?: return emptySet()
            detail.tracks.map { it.id }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun isWifi(): Boolean = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return@runCatching true
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching true
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(true)

    /** 设置定时关闭（分钟，0 = 取消） */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes <= 0) return
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            // 同步意图：定时暂停后，若仍有在飞的 playAt 请求也不应自动起播
            desiredPlaying = false
            if (::player.isInitialized) player.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun togglePlayPause() {
        ensureInit()
        val shouldPlay = !_state.value.isPlaying

        // 记录意图：若此刻有未完成的 playAt 请求在飞，
        // 它会读取该值并据此决定最终 play 还是 pause（见 applyDesiredPlayState），
        // 从而保证"暂停"不会被随后返回的网络响应覆盖。
        desiredPlaying = shouldPlay

        val ps = appContext.userPreferences
        if (shouldPlay) {
            // 恢复播放：淡入，避免起播爆音
            player.volume = 0f
            player.play()
            scope.launch {
                val s = ps.userStateFlow.first()
                val ms = if (s.crossfadeEnabled) 220L else 0L
                if (ms > 0) fadeVolume(0f, 1f, ms) else player.volume = 1f
            }
        } else {
            // 暂停：先淡出再暂停，避免截断爆音
            scope.launch {
                val s = ps.userStateFlow.first()
                val ms = if (s.crossfadeEnabled) 200L else 0L
                if (ms > 0) fadeVolume(1f, 0f, ms)
                player.pause()
                player.volume = 1f
            }
        }
        // ⚠️ 不在这里写 isPlaying。
        // 播放时以 onIsPlayingChanged 为准（真正出声才算）；
        // 暂停时 Media3 会立刻回调 isPlaying=false，同样无需预测。
        // 但暂停需要**立即**反馈到界面（淡出有 200ms），故只在这一侧先行更新。
        if (!shouldPlay) {
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun next() {
        if (_queue.isEmpty()) return
        when (_state.value.mode) {
            PlayMode.SINGLE_LOOP -> switchTo(currentIndex)
            PlayMode.SHUFFLE -> {
                val candidates = _queue.indices.filter { it != currentIndex }
                val idx = if (candidates.isEmpty()) currentIndex else candidates.random()
                switchTo(idx)
            }
            else -> {
                val nextIndex = currentIndex + 1
                if (nextIndex < _queue.size) {
                    switchTo(nextIndex)
                } else if (_state.value.mode == PlayMode.LIST_LOOP) {
                    switchTo(0)
                }
            }
        }
    }

    fun previous() {
        if (_queue.isEmpty()) return
        if (currentIndex > 0) {
            switchTo(currentIndex - 1)
        } else {
            // 已在第一首：循环模式回到最后一首，否则重播当前
            if (_state.value.mode == PlayMode.LIST_LOOP || _state.value.mode == PlayMode.SHUFFLE) {
                switchTo(_queue.size - 1)
            } else {
                switchTo(0)
            }
        }
    }

    /** 取消中的淡出协程（避免连续切歌时多个淡出叠加） */
    private var fadeJob: Job? = null

    /**
     * 统一切歌入口：根据「淡入淡出」设置决定是否做音量渐变过渡。
     * 淡出 → 切歌 → 淡入（淡入在 [playAt] 加载完成后执行）。
     */
    private fun switchTo(index: Int) {
        // 取消上一个未完成的淡出，避免连续切歌时叠加
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val ps = appContext.userPreferences.userStateFlow.first()
            // 重置音量，避免上次取消残留的中间值
            if (::player.isInitialized) player.volume = 1f
            if (!ps.crossfadeEnabled) {
                playAt(index)
                return@launch
            }
            val halfMs = ps.crossfadeSeconds.coerceIn(1, 8) * 1000L / 2

            // ⚠️ 这里**不再先做淡出**。
            //
            // 原先的顺序是「淡出旧曲 → playAt」，而 playAt 内部要先
            // `player.stop()` 再**异步解析播放地址**（在线歌曲需等网络）。
            // 于是从"旧曲静音"到"新曲出声"之间夹着**整个解析过程** ——
            // 网络慢时会出现数秒的完全静音。
            //
            // 现在把淡出交给 playAt：它会在**地址就绪之后**才淡出旧曲，
            // 解析期间旧曲继续出声，静音时长收敛为纯粹的淡出时长。
            playAt(index, fadeIn = true, fadeInMs = halfMs)
        }
    }

    /** 音量线性渐变（挂起，可被取消） */
    private suspend fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        // 减动效 / 时长为 0：直接置终值，不做逐帧渐变。
        // "淡入淡出"本质是听觉上的动效，同样应尊重系统的移除动画偏好。
        if (durationMs <= 0L || isReduceMotionEnabled(appContext)) {
            player.volume = to
            return
        }
        val steps = 16
        val stepMs = durationMs / steps
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            player.volume = from + (to - from) * f
            delay(stepMs)
        }
        player.volume = to
    }

    /** 切换音质并立即重载当前歌曲 */
    fun changeQuality(q: String) {
        manualQuality = q
        _state.update { it.copy(quality = q) }
        if (_queue.isNotEmpty() && currentIndex >= 0) {
            playAt(currentIndex)
        }
    }

    /** 喜欢/取消喜欢当前歌曲（乐观更新） */
    fun toggleLike() {
        val song = _state.value.song ?: return
        val like = !_state.value.isLiked
        _state.update { it.copy(isLiked = like) }
        if (like) likedIds.add(song.id) else likedIds.remove(song.id)
        scope.launch { repository.likeSong(song.id, like) }
    }

    fun seekTo(position: Long) {
        ensureInit()
        _state.update { it.copy(position = position) }
        player.seekTo(position)
    }

    /** 跳转到指定位置并确保开始播放（用于从歌词行直接播放） */
    fun seekToAndPlay(position: Long) {
        ensureInit()
        _state.update { it.copy(position = position) }
        player.seekTo(position)
        if (!_state.value.isPlaying) {
            // 与 togglePlayPause 一致：更新意图，isPlaying 交给 Media3 回调
            desiredPlaying = true
            player.play()
        }
    }

    /** 循环切换播放模式 */
    fun cycleMode() {
        val next = PlayMode.entries[(_state.value.mode.ordinal + 1) % PlayMode.entries.size]
        _state.update { it.copy(mode = next) }
    }

    fun setMode(mode: PlayMode) {
        _state.update { it.copy(mode = mode) }
    }

    /**
     * 加载歌词。
     *
     * @param seq 该请求所属的播放序号 —— 歌词请求同样存在竞态：
     *   切歌后若旧歌词晚到，会把新歌的歌词覆盖成上一首的内容。
     */
    private fun loadLyrics(id: Long, seq: Int) {
        scope.launch {
            val r = repository.lyric(id)
            if (r is ApiResult.Success && seq == playRequestSeq) {
                // 合并官方翻译歌词（tlyric）
                val lyrics = LyricParser.parse(r.data.lrc?.lyric, r.data.tlyric?.lyric)
                _state.update { it.copy(lyrics = lyrics) }
            }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(appContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }
}
