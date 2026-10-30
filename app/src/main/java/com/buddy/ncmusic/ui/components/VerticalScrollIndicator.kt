package com.buddy.ncmusic.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.ui.util.Md3Motion

private data class ScrollMetrics(
    val totalItems: Int,
    val visibleCount: Int,
    val firstIndex: Int,
)

/**
 * 列表垂直滚动指示条（右侧浮动）。
 *
 * 特点：
 * - 仅当内容超出视口时才显示
 * - 滚动过程中淡入，停止约 800ms 后淡出
 * - 滑块位置与长度随滚动平滑过渡（`animateFloatAsState`）
 *
 * 用法：与 `LazyColumn` 放在同一个 `Box` 中，并靠右对齐：
 * ```
 * Box {
 *     LazyColumn(state = listState) { ... }
 *     VerticalScrollIndicator(
 *         listState = listState,
 *         modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
 *     )
 * }
 * ```
 */
@Composable
fun VerticalScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    indicatorWidth: Dp = 3.dp,
    minIndicatorHeight: Dp = 40.dp,
) {
    // 先把布局信息压缩成只影响指示条本身的三个值。
    // 直接在组合中读取 layoutInfo 会让指示条在滚动时逐帧重组；
    // derivedStateOf 只在真正影响滑块几何的字段变化时通知下游。
    val metrics by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            ScrollMetrics(
                totalItems = info.totalItemsCount,
                visibleCount = info.visibleItemsInfo.size,
                firstIndex = info.visibleItemsInfo.firstOrNull()?.index ?: 0,
            )
        }
    }
    val scrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val totalItems = metrics.totalItems
    val visibleCount = metrics.visibleCount

    // 内容未超出视口时不显示
    if (totalItems == 0 || visibleCount == 0 || totalItems <= visibleCount) return

    val scrollableCount = (totalItems - visibleCount).coerceAtLeast(1)

    // 滑块长度比例（内容越多越短）
    val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.06f, 1f)
    // 滑块位置比例
    val offsetFraction = (metrics.firstIndex.toFloat() / scrollableCount).coerceIn(0f, 1f)

    val animatedOffset by animateFloatAsState(
        targetValue = offsetFraction,
        animationSpec = tween(Md3Motion.Short2, easing = Md3Motion.Standard),
        label = "scrollOffset",
    )
    val animatedFraction by animateFloatAsState(
        targetValue = thumbFraction,
        animationSpec = tween(Md3Motion.Short4, easing = Md3Motion.Standard),
        label = "scrollThumb",
    )
    // 滚动时淡入，停止后淡出
    val animatedAlpha by animateFloatAsState(
        targetValue = if (scrolling) 1f else 0f,
        animationSpec = tween(
            if (scrolling) Md3Motion.Short3 else Md3Motion.Medium2,
            easing = Md3Motion.Standard,
        ),
        label = "scrollAlpha",
    )

    // 提前算好颜色（remember 不可出现在条件 return 之后，会破坏调用顺序）
    val trackColor = color.copy(alpha = color.alpha * animatedAlpha)

    if (animatedAlpha <= 0.01f) return

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(indicatorWidth + 6.dp),
    ) {
        val trackHeight = size.height
        val thumbHeight = (trackHeight * animatedFraction).coerceAtLeast(minIndicatorHeight.toPx())
        val maxTop = (trackHeight - thumbHeight).coerceAtLeast(0f)
        val top = maxTop * animatedOffset
        val radius = size.width / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

/**
 * 把滚动指示条与列表组合在一起的便捷容器。
 *
 * 
 */
@Composable
fun ListWithScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    indicatorColor: Color? = null,
    /** 是否显示右侧指示条；歌单详情等长列表保留，普通内容页可关闭 */
    showIndicator: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (showIndicator) {
            VerticalScrollIndicator(
                listState = listState,
                color = indicatorColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp),
            )
        }
    }
}
