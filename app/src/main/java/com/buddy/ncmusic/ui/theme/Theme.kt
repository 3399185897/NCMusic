package com.buddy.ncmusic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    // 底色来源的优先级：深色模式 > 莫奈取色 > 品牌配色。
    //
    // ⚠️ 原先 dynamicColor 排在 darkTheme 之前 —— 于是只要开着取色，
    // 深色模式就被完全忽略（深色开关形同虚设）。
    // 深色是用户的显式选择，优先级必须高于自动取色。
    val base = when {
        darkTheme -> DarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    // 深色模式：**强制使用灰阶主题**，不参与自定义色与取色。
    //
    // 与其在 UI 上逐个禁用（还容易漏），不如在这里直接短路 ——
    // 深色下无论 customColor 是什么都走 DarkColorScheme，
    // 这样"开启深色即灰色且不可更改"是由数据流保证的，而非靠界面约束。
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else if (customColor != 0L) {
        val c = Color(customColor.toInt())
        // 深色模式底色固定为**纯黑**（不掺主题色），保证对比度与"纯黑模式"的观感一致
        val bg = if (darkTheme) Color(0xFF000000)
        else blend(Color(0xFFFFFBFE), c, 0.08f)
        val surface = if (darkTheme) Color(0xFF000000)
        else blend(Color(0xFFFFFBFE), c, 0.12f)
        base.copy(
            primary = c,
            onPrimary = Color.White,
            // 深色下用不透明深色容器（而非半透明），否则叠在纯黑上几乎不可见
            primaryContainer = if (darkTheme) blend(Color(0xFF000000), c, 0.30f)
            else c.copy(alpha = 0.28f),
            onPrimaryContainer = if (darkTheme) Color.White else Color.Black,
            secondaryContainer = blend(
                if (darkTheme) Color(0xFF4A4458) else Color(0xFFE8DEF8), c, 0.45f,
            ),
            onSecondaryContainer = if (darkTheme) Color(0xFFE8DEF8) else Color(0xFF1D192B),
            background = bg,
            onBackground = if (darkTheme) Color.White else Color(0xFF1C1B1F),
            surface = surface,
            onSurface = if (darkTheme) Color.White else Color(0xFF1C1B1F),
            // 底部导航栏 / 卡片等容器色，必须同步，否则切换主题后底栏不变色
            surfaceContainerLowest = blend(
                if (darkTheme) Color(0xFF000000) else Color(0xFFFFFFFF), c, 0.10f,
            ),
            surfaceContainerLow = blend(
                if (darkTheme) Color(0xFF0A0A0A) else Color(0xFFF7F2FA), c, 0.14f,
            ),
            surfaceContainer = blend(
                if (darkTheme) Color(0xFF121212) else Color(0xFFF3EDF7), c, 0.18f,
            ),
            surfaceContainerHigh = blend(
                if (darkTheme) Color(0xFF1A1A1A) else Color(0xFFECE6F0), c, 0.20f,
            ),
            surfaceContainerHighest = blend(
                if (darkTheme) Color(0xFF242424) else Color(0xFFE6E0E9), c, 0.22f,
            ),
            surfaceVariant = blend(
                if (darkTheme) Color(0xFF2E2E2E) else Color(0xFFE7E0EC), c, 0.22f,
            ),
            outlineVariant = blend(
                if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFCAC4D0), c, 0.20f,
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
