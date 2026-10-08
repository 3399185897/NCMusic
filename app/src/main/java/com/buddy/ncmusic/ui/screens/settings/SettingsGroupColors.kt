package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 设置分类的专属色调对（容器色, 内容色）。
 *
 * 设计来源：PixelPlayer 的设置页为每个分类分配一个**不同的 MD3 tonal palette 家族**
 * （蓝 / 粉 / 洋红 / 青 / 琥珀…），但都遵守统一的明度规范——
 * 于是**色彩丰富却不杂乱**，且深浅主题各有一套对比正确的配色。
 *
 * 这里为每个分类返回 `容器色 to 内容色`，用于给分类标题与图标染色。
 */
@Composable
fun settingsGroupColors(group: SettingsGroup): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        when (group) {
            SettingsGroup.ALL        -> Color(0xFF004A77) to Color(0xFFC2E7FF)
            SettingsGroup.APPEARANCE -> Color(0xFF7D5260) to Color(0xFFFFD8E4)
            SettingsGroup.PLAYBACK   -> Color(0xFF633B48) to Color(0xFFFFD8EC)
            SettingsGroup.LYRIC      -> Color(0xFF3E4C63) to Color(0xFFD7E3FF)
            SettingsGroup.AUDIO      -> Color(0xFF6E4E13) to Color(0xFFFFDEAC)
            SettingsGroup.STORAGE    -> Color(0xFF3B4869) to Color(0xFFD9E2FF)
            SettingsGroup.ACCOUNT    -> Color(0xFF324F34) to Color(0xFFCBEFD0)
            SettingsGroup.ABOUT      -> Color(0xFF3F474D) to Color(0xFFDEE3EB)
        }
    } else {
        when (group) {
            SettingsGroup.ALL        -> Color(0xFFD7E3FF) to Color(0xFF005AC1)
            SettingsGroup.APPEARANCE -> Color(0xFFFFD8E4) to Color(0xFF631835)
            SettingsGroup.PLAYBACK   -> Color(0xFFFFD8EC) to Color(0xFF631B4B)
            SettingsGroup.LYRIC      -> Color(0xFFD7E3FF) to Color(0xFF253347)
            SettingsGroup.AUDIO      -> Color(0xFFFFDEAC) to Color(0xFF281900)
            SettingsGroup.STORAGE    -> Color(0xFFD9E2FF) to Color(0xFF27304E)
            SettingsGroup.ACCOUNT    -> Color(0xFFCBEFD0) to Color(0xFF042106)
            SettingsGroup.ABOUT      -> Color(0xFFEFF1F7) to Color(0xFF44474F)
        }
    }
}

/**
 * 分类标题的小色条 —— 让当前分类在长列表中一眼可辨。
 */
@Composable
fun groupAccent(group: SettingsGroup): Color {
    val (container, content) = settingsGroupColors(group)
    // 用内容色作为强调色（在两种主题下都与背景有足够对比）
    return content
}