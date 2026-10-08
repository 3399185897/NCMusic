package com.buddy.ncmusic

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
                // 分层：基础背景 → 颜色扩散 → 内容
                // 扩散效果刻意置于内容**之下**，这样文字与控件始终清晰，
                // 不会出现「一层色块盖住界面」的问题
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
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
