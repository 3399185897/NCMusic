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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo

    // 内容未超出视口时不显示
    if (totalItems == 0 || visibleItems.isEmpty() || totalItems <= visibleItems.size) return

    val firstIndex = visibleItems.first().index
    val visibleCount = visibleItems.size
    val scrollableCount = (totalItems - visibleCount).coerceAtLeast(1)

    // 滑块长度比例（内容越多越短）
    val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.06f, 1f)
    // 滑块位置比例
    val offsetFraction = (firstIndex.toFloat() / scrollableCount).coerceIn(0f, 1f)

    val animatedOffset by animateFloatAsState(
        targetValue = offsetFraction,
        animationSpec = tween(120),
        label = "scrollOffset",
    )
    val animatedFraction by animateFloatAsState(
        targetValue = thumbFraction,
        animationSpec = tween(200),
        label = "scrollThumb",
    )
    // 滚动时淡入，停止后淡出
    val animatedAlpha by animateFloatAsState(
        targetValue = if (listState.isScrollInProgress) 1f else 0f,
        animationSpec = tween(if (listState.isScrollInProgress) 150 else 500),
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
