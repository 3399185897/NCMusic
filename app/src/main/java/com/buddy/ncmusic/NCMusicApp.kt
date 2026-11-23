package com.buddy.ncmusic

import android.app.Application
import com.buddy.ncmusic.data.cache.CacheManager
import com.buddy.ncmusic.data.local.UserPreferences
import com.buddy.ncmusic.data.repository.MusicRepository
import com.buddy.ncmusic.playback.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NCMusicApp : Application() {

    companion object {
        lateinit var instance: NCMusicApp
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userPreferences: UserPreferences by lazy { UserPreferences(this) }
    val musicRepository: MusicRepository by lazy { MusicRepository(userPreferences) }

    /** 缓存容量管理：让「最大缓存大小」设置真正参与淘汰 */
    val cacheManager: CacheManager by lazy { CacheManager(this, userPreferences) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 启动时恢复登录 Cookie
        appScope.launch {
            val state = userPreferences.userStateFlow.first()
            musicRepository.restoreCookie(state.cookie)
        }
        // 启动时按用户设置的上限回收一次缓存：
        // 上次运行可能留下了超出当前上限的数据（用户调小过上限）
        appScope.launch { runCatching { cacheManager.enforceLimit() } }

        // 恢复「上次听到哪」。
        //
        // 只还原记忆（队列 / 当前曲 / 进度），不解析地址、不自动播放：
        // 解析要联网，会让启动变慢；自动播放在静音场合会突然出声。
        // 用户点一下播放，PlaybackManager 会从记下的位置继续。
        //
        // 放在主线程：这里是 MutableList 队列的唯一写入窗口，
        // 此时还没有任何播放请求在飞，不存在竞争。
        runCatching { PlaybackManager.restoreFromSnapshot() }
    }
}
