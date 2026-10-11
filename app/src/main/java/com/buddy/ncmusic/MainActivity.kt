package com.buddy.ncmusic

import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import com.buddy.ncmusic.playback.PlaybackManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import com.buddy.ncmusic.ui.theme.ColorRevealOverlay
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.buddy.ncmusic.ui.navigation.NCMusicApp
import com.buddy.ncmusic.ui.theme.NCMusicTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果无需处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            val state by NCMusicApp.instance.userPreferences.userStateFlow
                .collectAsState(initial = null)

            val darkTheme = when (state?.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            NCMusicTheme(
                darkTheme = darkTheme,
                dynamicColor = state?.dynamicColor ?: true,
                customColor = state?.customColor ?: 0L,
            ) {
                // 让状态栏 / 导航栏的图标颜色跟随**应用主题**，而不是系统主题。
                //
                // enableEdgeToEdge() 的默认行为是按系统深色状态决定图标明暗：
                // 应用切成纯黑深色、而系统仍是浅色时，系统栏图标会是深色 ——
                // 在纯黑背景上完全看不见（用户反馈的"返回按钮不可见"）。
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as Activity).window
                    val ctrl = WindowCompat.getInsetsController(window, view)
                    ctrl.isAppearanceLightStatusBars = !darkTheme
                    ctrl.isAppearanceLightNavigationBars = !darkTheme
                }

                // 播放失败提示：PlaybackManager 通过 errorMessage 上报原因，
                // 这里统一以 Toast 呈现 —— 播放可从任意页面触发，
                // 用全局 Toast 比在各页面各接一个提示更省事，也不会漏。
                val playback by PlaybackManager.state.collectAsState()
                val ctx = LocalContext.current
                LaunchedEffect(playback.errorMessage) {
                    playback.errorMessage?.let { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        PlaybackManager.clearError()      // 消费掉，避免重复弹
                    }
                }

                // 分层：基础背景 → 颜色扩散 → 内容
                //
                // ⚠️ 必须用 Surface 而非 Box + background：
                //   Box 只画背景色，**不会设置 LocalContentColor**，
                //   它会一直保持默认的 Color.Black —— 于是纯黑主题下
                //   未显式指定颜色的文字、图标、返回按钮全部变成黑底黑字而"消失"。
                //   Surface 会依据 color 自动给出匹配的 contentColor（黑底 -> 白字），
                //   整棵子树的默认前景色随之正确。
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    ColorRevealOverlay()
                    NCMusicApp()
                }
            }
        }
    }

    /** Android 13+ 需要运行时授予通知权限，否则播放控制通知不显示 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
