package com.buddy.ncmusic.playback

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
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = mutableListOf<Song>()
    private var currentIndex = -1

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
                    _state.update {
                        it.copy(duration = player.duration.coerceAtLeast(0L), isLoading = false)
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
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 通知栏 / 耳机等外部操作会直接作用于 ExoPlayer，这里保持状态同步
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(isLoading = false) }
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

    fun playAt(index: Int) {
        ensureInit()
        if (index !in _queue.indices) return
        currentIndex = index
        val song = _queue[index]
        // 立即停止旧曲，确保进度归零
        player.stop()
        _state.update {
            it.copy(
                song = song,
                isLoading = true,
                position = 0L,
                duration = 0L,
                lyrics = emptyList(),
                isLiked = song.id in likedIds,
                isPlaying = true,
            )
        }
        startForegroundService()

        scope.launch {
            val uri = resolveUri(song)
            if (uri == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            runCatching {
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.play()
                loadLyrics(song.id)
            }.onFailure {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /** 是否存在下一曲（供 MediaSession 判断按钮可用性） */
    fun canNext(): Boolean =
        _queue.isNotEmpty() &&
            (currentIndex < _queue.lastIndex || _state.value.mode != PlayMode.SEQUENCE)

    /** 是否存在上一曲 */
    fun canPrevious(): Boolean = _queue.isNotEmpty()

    /** 解析播放地址：本地文件直接返回 file URI */
    private suspend fun resolveUri(song: Song): String? {
        song.localPath?.takeIf { it.isNotBlank() }?.let {
            return java.io.File(it).toURI().toString()
        }
        val q = resolveQuality()
        _state.update { it.copy(quality = q) }
        return when (val r = repository.songUrl(song.id, q)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> null
        }
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
            if (::player.isInitialized) player.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun togglePlayPause() {
        ensureInit()
        val shouldPlay = !_state.value.isPlaying
        if (shouldPlay) player.play() else player.pause()
        _state.update { it.copy(isPlaying = shouldPlay) }
    }

    fun next() {
        if (_queue.isEmpty()) return
        when (_state.value.mode) {
            PlayMode.SINGLE_LOOP -> playAt(currentIndex)
            PlayMode.SHUFFLE -> {
                val candidates = _queue.indices.filter { it != currentIndex }
                val idx = if (candidates.isEmpty()) currentIndex else candidates.random()
                playAt(idx)
            }
            else -> {
                val nextIndex = currentIndex + 1
                if (nextIndex < _queue.size) {
                    playAt(nextIndex)
                } else if (_state.value.mode == PlayMode.LIST_LOOP) {
                    playAt(0)
                }
            }
        }
    }

    fun previous() {
        if (_queue.isEmpty()) return
        if (currentIndex > 0) {
            playAt(currentIndex - 1)
        } else {
            // 已在第一首：循环模式回到最后一首，否则重播当前
            if (_state.value.mode == PlayMode.LIST_LOOP || _state.value.mode == PlayMode.SHUFFLE) {
                playAt(_queue.size - 1)
            } else {
                playAt(0)
            }
        }
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

    /** 循环切换播放模式 */
    fun cycleMode() {
        val next = PlayMode.entries[(_state.value.mode.ordinal + 1) % PlayMode.entries.size]
        _state.update { it.copy(mode = next) }
    }

    fun setMode(mode: PlayMode) {
        _state.update { it.copy(mode = mode) }
    }

    private fun loadLyrics(id: Long) {
        scope.launch {
            val r = repository.lyric(id)
            if (r is ApiResult.Success) {
                val lyrics = LyricParser.parse(r.data.lrc?.lyric)
                _state.update { it.copy(lyrics = lyrics) }
            }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(appContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }
}
