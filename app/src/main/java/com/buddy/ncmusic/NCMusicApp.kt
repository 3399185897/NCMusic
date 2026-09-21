package com.buddy.ncmusic

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 启动时恢复登录 Cookie
        appScope.launch {
            val state = userPreferences.userStateFlow.first()
            musicRepository.restoreCookie(state.cookie)
        }
    }
}
