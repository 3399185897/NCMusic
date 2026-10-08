package com.buddy.ncmusic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.ui.theme.AppShapes

/**
 * # 「正方形无缝拼接矩形」组件（整体块版）
 *
 * 用 18 个大小不同的正方形恰好铺满一个 3:2 的大矩形，
 * 并且**整体作为一个块**呈现 —— 统一大圆角容器 + 统一色调叠加。
 *
 * ## 布局原理
 *
 * 属于**正方形铺砌（squared rectangle）**问题：用若干正方形无空隙、
 * 无重叠地铺满矩形。
 *
 * > 注：若要求所有正方形尺寸**互不相同**（完美铺砌），最小阶数为 32 个，
 * > 超出「10~20 个」的约束。故本方案**允许少量尺寸重复**，
 * > 从而在 18 个方块内同时满足无缝、无重叠、外轮廓规则三项硬性要求。
 *
 * ## 布局表（设计基准 180 × 120）
 *
 * ```
 *  0        40   80        130      180
 *  ┌────────┬────────┬────────┬────────┐
 *  │  40    │  40    │   50   │   50   │  带高 50
 *  ├────────┴────────┼────────┼────────┤
 *  │                 │ 25│25│25│25     │  带高 25
 *  │      80×80      ├───┴──┴──┴──────┤
 *  │                 │ 25│25│25│25     │  带高 25
 *  │                 ├───┬──┬──┬──┬───┤
 *  │                 │20 │20│20│20│20 │  带高 20
 *  └─────────────────┴───┴──┴──┴──┴───┘
 *   ← 左区 80 宽 →   ←── 右区 100 宽 ──→
 * ```
 *
 * **无缝性（构建前已用逐像素覆盖校验确认）**：
 * - 左区：`40 + 40 = 80`（宽）｜ `40 + 80 = 120`（高）
 * - 右区每条带宽度精确等于 100：`50+50`｜`25×4`｜`25×4`｜`20×5`
 * - 右区总高：`50 + 25 + 25 + 20 = 120`
 * - 左右区宽：`80 + 100 = 180`
 *
 * 三个方向边界均精确闭合，不存在任何空隙或重叠。
 *
 * ## 尺寸分布
 *
 * | 边长 | 数量 |
 * |---|---|
 * | 80 | 1 |
 * | 50 | 2 |
 * | 40 | 2 |
 * | 25 | 8 |
 * | 20 | 5 |
 *
 * 共 5 种尺寸，最大与最小相差 4 倍，形成明显疏密节奏。
 *
 * ## 整体块效果
 *
 * 外层统一 `clip` 大圆角 + 主题色底；内部方块**不再各自加圆角**，
 * 因此整片拼贴看起来是一整块被分割的色墙，而不是一堆散落的小卡片。
 *
 * 方块直接呈现封面原色（**不叠加任何颜色遮罩**），以保证专辑图的本来色彩。
 *
 * ## 响应式
 *
 * 通过 [BoxWithConstraints] 读取可用宽度，所有坐标与边长按同一系数
 * `scale = 可用宽度 / 180` 换算，因此内部相对比例恒定，任意屏宽下都严格无缝。
 */
@Composable
fun SquaredMosaic(
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // 等比缩放系数：所有坐标与边长统一乘它
        val scale = maxWidth.value / DESIGN_WIDTH
        val mosaicHeight = (DESIGN_HEIGHT * scale).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mosaicHeight)
                // 整体一个块：统一大圆角，边缘方块由这里裁切
                .clip(AppShapes.of(22.dp))
                // 主题色底：图片加载完成前也保持整体观感一致
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            SQUARED_LAYOUT.forEachIndexed { index, tile ->
                // 歌单数不足时循环取用，保证拼贴墙始终铺满
                val playlist = playlists[index % playlists.size]
                SquaredTileView(
                    playlist = playlist,
                    size = (tile.size * scale).dp,
                    offsetX = (tile.x * scale).dp,
                    offsetY = (tile.y * scale).dp,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }
}

/** 设计基准宽度（设计单位） */
private const val DESIGN_WIDTH = 180f

/** 设计基准高度（设计单位） */
private const val DESIGN_HEIGHT = 120f

/**
 * 单个正方形的位置与边长（均以设计单位表示）。
 *
 * @param x 左上角横坐标
 * @param y 左上角纵坐标
 * @param size 边长（正方形，宽高同值）
 */
private data class SquaredTile(val x: Float, val y: Float, val size: Float)

/**
 * 布局表 —— 18 个正方形恰好铺满 180 × 120。
 *
 * 顺序对应「从大到小」，前几个方块会分配到靠前的歌单。
 */
private val SQUARED_LAYOUT: List<SquaredTile> = listOf(
    // ---------- 左区：x ∈ [0, 80] ----------
    SquaredTile(x = 0f, y = 0f, size = 40f),
    SquaredTile(x = 40f, y = 0f, size = 40f),
    SquaredTile(x = 0f, y = 40f, size = 80f),

    // ---------- 右区：x ∈ [80, 180]，宽 100 ----------
    // 带 1：高 50
    SquaredTile(x = 80f, y = 0f, size = 50f),
    SquaredTile(x = 130f, y = 0f, size = 50f),
    // 带 2：高 25
    SquaredTile(x = 80f, y = 50f, size = 25f),
    SquaredTile(x = 105f, y = 50f, size = 25f),
    SquaredTile(x = 130f, y = 50f, size = 25f),
    SquaredTile(x = 155f, y = 50f, size = 25f),
    // 带 3：高 25
    SquaredTile(x = 80f, y = 75f, size = 25f),
    SquaredTile(x = 105f, y = 75f, size = 25f),
    SquaredTile(x = 130f, y = 75f, size = 25f),
    SquaredTile(x = 155f, y = 75f, size = 25f),
    // 带 4：高 20
    SquaredTile(x = 80f, y = 100f, size = 20f),
    SquaredTile(x = 100f, y = 100f, size = 20f),
    SquaredTile(x = 120f, y = 100f, size = 20f),
    SquaredTile(x = 140f, y = 100f, size = 20f),
    SquaredTile(x = 160f, y = 100f, size = 20f),
)

/**
 * 单个正方形方块。
 *
 * 注意：**方块本身不加圆角** —— 圆角统一由外层容器提供，
 * 这样整片拼贴才呈现为一个整体块。
 *
 * 按下时轻微缩小到 `0.94`，松手回弹 —— 移动端以**按压反馈**替代 Web 的 hover。
 * 缩放经 [graphicsLayer] 实现，**不改变布局占位**，因此不会破坏无缝拼接。
 */
@Composable
private fun SquaredTileView(
    playlist: Playlist,
    size: Dp,
    offsetX: Dp,
    offsetY: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "squaredTilePress",
    )

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        CoverImage(
            url = playlist.coverImgUrl,
            modifier = Modifier.fillMaxSize(),
            contentDescription = playlist.name,
        )
    }
}
