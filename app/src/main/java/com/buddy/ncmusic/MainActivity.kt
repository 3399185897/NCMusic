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
                // ⚠️ 只订阅**错误信息**这一条细粒度流，而非完整 PlaybackState。
                //
                // 此处唯一用途是读取 errorMessage 弹 Toast。
                // 若订阅完整 state，则播放进度（每秒数次）会让 MainActivity
                // 的整个 setContent 组合域持续重组 —— 而它承载着
                // Surface + NCMusicApp 这棵**全应用最大**的子树。
                val playbackError by PlaybackManager.errorMessageFlow.collectAsState()
                // 信息性提示（如音质降级）单独订阅 —— 与错误分开管理，
                // 否则会被播放成功后的 errorMessage 清理逻辑一并抹掉
                val playbackNotice by PlaybackManager.noticeMessageFlow.collectAsState()
                val ctx = LocalContext.current

                LaunchedEffect(playbackError) {
                    playbackError?.let { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        PlaybackManager.clearError()      // 消费掉，避免重复弹
                    }
                }
                LaunchedEffect(playbackNotice) {
                    playbackNotice?.let { msg ->
                        // 提示用短时长，与错误的"长时长"区分轻重
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        PlaybackManager.clearNotice()
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
                    // ⚠️ 绘制顺序 = 叠放层级，后者在上。
                    //
                    // 原先 ColorRevealOverlay 放在 NCMusicApp **之前**，
                    // 等于把扩散色块压在应用内容**之下** ——
                    // 动画期间用户看到的是**旧界面**，扩散完全不可见
                    // （只有内容带透明背景的少数页面能略微透出）。
                    //
                    // 这里**调换顺序**：内容先画，扩散遮罩后画叠在其上，
                    // 才形成"新颜色从点击处漫开覆盖旧界面"的效果。
                    NCMusicApp()
                    ColorRevealOverlay()
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
