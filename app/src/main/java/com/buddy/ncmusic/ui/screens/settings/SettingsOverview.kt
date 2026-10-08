package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 设置分类总览。
 *
 * **严格对齐 PixelPlayer 的设计**：
 *
 * 1. 所有分类项在**同一个容器**内，靠 `2dp` 细缝分隔；卡片底色统一为
 *    `surfaceContainer`（**不**给每项染底）
 * 2. 色彩只体现在**左侧 56dp 圆形图标容器**上（分类专属色），
 *    标题与副标题一律用 `onSurface`（不染色）
 * 3. 用**形状渐变**把各项连成一体：
 *    - 首项：上圆角 24dp / 下圆角 4dp
 *    - 中间：全 4dp
 *    - 末项：上圆角 4dp / 下圆角 24dp
 *    - 只有一项：全 24dp
 *
 * 这样整块看起来是「一张大卡片被分成多行」，而不是一堆散落的小卡片。
 */
@Composable
fun SettingsOverview(onOpenGroup: (SettingsGroup) -> Unit) {
    // 除「全部」外的分类都出现在总览里
    val groups = SettingsGroup.entries.filter { it != SettingsGroup.ALL }
    val totalItems = groups.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        groups.forEachIndexed { index, g ->
            val (container, content) = settingsGroupColors(g)
            SettingsCategoryItem(
                group = g,
                containerColor = container,
                contentColor = content,
                shape = groupShapeFor(index, totalItems),
                onClick = { onOpenGroup(g) },
            )
            // 项间 2dp 细缝（与示例一致）
            if (index < totalItems - 1) {
                Spacer(Modifier.height(2.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * 按位置推导圆角形状 —— 让整组看起来是一张被切分的大卡片。
 *
 * 说明：平滑圆角库 [AppShapes] 只支持**统一半径**，无法指定单角，
 * 因此这里用 `RoundedCornerShape` 的单角参数实现「首尾大圆角、中间小圆角」的效果
 * —— 这与示例（PixelPlayer）的 `shapeFor(index)` 实现方式一致。
 */
private fun groupShapeFor(index: Int, total: Int): Shape = when {
    total == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    index == total - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(4.dp)
}

/**
 * 单个分类项。
 *
 * 结构与示例一致：`Surface(88dp 高)` → 圆形色块图标位（56dp）+ 标题/副标题。
 * 因核心图标库只有 11 个基础图标，这里用**分类名首字**代替图标，
 * 圆形色块与配色的设计意图保持不变。
 */
@Composable
private fun SettingsCategoryItem(
    group: SettingsGroup,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    shape: Shape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize(),
        ) {
            // 图标容器：56dp 圆形 + 分类专属色（唯一的彩色元素）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(containerColor),
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = groupSubtitle(group),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 分类的说明文字 */
private fun groupSubtitle(group: SettingsGroup): String = when (group) {
    SettingsGroup.APPEARANCE -> "主题模式、取色与启动页"
    SettingsGroup.PLAYBACK -> "音量均衡、均衡器与淡入淡出"
    SettingsGroup.LYRIC -> "桌面歌词悬浮窗与对齐方式"
    SettingsGroup.AUDIO -> "音质与解码模式"
    SettingsGroup.STORAGE -> "缓存上限、缓存目录与清理"
    SettingsGroup.ACCOUNT -> "登录状态与账号操作"
    SettingsGroup.ABOUT -> "版本信息与开源许可"
    SettingsGroup.ALL -> "全部设置"
}