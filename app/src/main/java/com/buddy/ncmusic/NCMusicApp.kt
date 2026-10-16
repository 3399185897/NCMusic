package com.buddy.ncmusic

import android.app.Application
import com.buddy.ncmusic.data.cache.CacheManager
import com.buddy.ncmusic.data.local.UserPreferences
import com.buddy.ncmusic.data.repository.MusicRepository
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

        // 清理上次异常退出残留的临时工作文件。
        //
        // 正常流程下这些文件（dl_* / art_* / identify.m4a）用完即删，
        // 但进程被杀、崩溃时会残留。只清理**静默超过 1 小时**的，
        // 因此绝不会误删本次启动后新发起的下载。
        appScope.launch { runCatching { cacheManager.cleanupStaleTempFiles() } }
    }
}
