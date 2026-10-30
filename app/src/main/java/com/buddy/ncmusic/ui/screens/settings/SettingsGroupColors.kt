package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 设置页统一使用同一套 MD3 强调色。
 *
 * 分类依靠图标、标题与间距区分，不再为每个分类维护一套独立色板；
 * 这样切换莫奈取色或自定义主题色时，整个设置页保持一致。
 */
@Composable
fun settingsGroupColors(group: SettingsGroup): Pair<Color, Color> =
    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

@Composable
fun groupAccent(group: SettingsGroup): Color = MaterialTheme.colorScheme.primary
