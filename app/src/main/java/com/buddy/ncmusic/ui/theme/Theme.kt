package com.buddy.ncmusic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 应用主题：优先使用 Material You 莫奈动态取色（Android 12+），
 * 否则回退到内置的品牌配色（网易云红）。
 */
@Composable
fun NCMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 是否启用莫奈取色，可在设置中关闭
    dynamicColor: Boolean = true,
    // 自定义主题色（ARGB Long，0 = 未设置）
    customColor: Long = 0L,
    content: @Composable () -> Unit,
) {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (customColor != 0L) {
        val c = Color(customColor.toInt())
        val bg = if (darkTheme) blend(Color(0xFF1C1B1F), c, 0.12f)
        else blend(Color(0xFFFFFBFE), c, 0.08f)
        val surface = if (darkTheme) blend(Color(0xFF1C1B1F), c, 0.16f)
        else blend(Color(0xFFFFFBFE), c, 0.12f)
        base.copy(
            primary = c,
            onPrimary = Color.White,
            primaryContainer = c.copy(alpha = 0.28f),
            onPrimaryContainer = if (darkTheme) Color.White else Color.Black,
            secondaryContainer = blend(
                if (darkTheme) Color(0xFF4A4458) else Color(0xFFE8DEF8), c, 0.45f,
            ),
            onSecondaryContainer = if (darkTheme) Color(0xFFE8DEF8) else Color(0xFF1D192B),
            background = bg,
            onBackground = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F),
            surface = surface,
            onSurface = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F),
            // 底部导航栏 / 卡片等容器色，必须同步，否则切换主题后底栏不变色
            surfaceContainerLowest = blend(
                if (darkTheme) Color(0xFF0F0D13) else Color(0xFFFFFFFF), c, 0.10f,
            ),
            surfaceContainerLow = blend(
                if (darkTheme) Color(0xFF1D1B20) else Color(0xFFF7F2FA), c, 0.14f,
            ),
            surfaceContainer = blend(
                if (darkTheme) Color(0xFF211F26) else Color(0xFFF3EDF7), c, 0.18f,
            ),
            surfaceContainerHigh = blend(
                if (darkTheme) Color(0xFF2B2930) else Color(0xFFECE6F0), c, 0.20f,
            ),
            surfaceContainerHighest = blend(
                if (darkTheme) Color(0xFF36343B) else Color(0xFFE6E0E9), c, 0.22f,
            ),
            surfaceVariant = blend(
                if (darkTheme) Color(0xFF49454F) else Color(0xFFE7E0EC), c, 0.22f,
            ),
            outlineVariant = blend(
                if (darkTheme) Color(0xFF49454F) else Color(0xFFCAC4D0), c, 0.20f,
            ),
        )
    } else {
        base
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** 按 alpha 将 overlay 混入 base，生成带主题色调的背景色 */
private fun blend(base: Color, overlay: Color, alpha: Float): Color = Color(
    red = base.red + (overlay.red - base.red) * alpha,
    green = base.green + (overlay.green - base.green) * alpha,
    blue = base.blue + (overlay.blue - base.blue) * alpha,
    alpha = 1f,
)
