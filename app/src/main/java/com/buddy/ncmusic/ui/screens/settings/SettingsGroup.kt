package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 设置分类。
 *
 * 把原先「一页 11 个分组的长列表」按用途归类：
 * - 总览页只呈现分类项（`/settings`）
 * - 点击进入各分类子页（`/settings_group/{id}`），子页只渲染该分类的分组
 *
 * 图标取自 `material-icons-extended`（已在 app/build.gradle.kts 中引入）。
 * R8 会裁掉未引用的图标，实际体积增量远小于库本身。
 */
enum class SettingsGroup(
    val id: String,
    val label: String,
    val icon: ImageVector,
) {
    ALL("all", "全部", Icons.Default.Settings),
    APPEARANCE("appearance", "外观", Icons.Default.Palette),
    PLAYBACK("playback", "播放", Icons.Default.PlayCircle),
    LYRIC("lyric", "歌词", Icons.Default.Subtitles),
    AUDIO("audio", "音质", Icons.Default.GraphicEq),
    STORAGE("storage", "存储", Icons.Default.Storage),
    ACCOUNT("account", "账号", Icons.Default.Person),
    ABOUT("about", "关于", Icons.Default.Info),
    ;

    companion object {
        /** 默认选中的分类 */
        val DEFAULT = ALL

        /** 由路由参数还原分类（未知 id 返回 null） */
        fun fromId(id: String): SettingsGroup? = entries.find { it.id == id }
    }
}
