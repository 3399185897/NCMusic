package com.buddy.ncmusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.ui.util.rememberReduceMotion

/**
 * 分页指示器：当前页为「长胶囊」，其余为等宽圆点。
 *
 * 之所以不用「N 个圆点」：每日推荐有 30 首（8 页），8 个同尺寸圆点既拥挤，
 * 也难以一眼看出「当前在第几页」。长胶囊 + 圆点的组合在 MD3 里属于
 * 「carousel indicator」的既有语言，页数多时依然清爽。
 */
@Composable
fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    activeWidth: androidx.compose.ui.unit.Dp = 18.dp,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
) {
    if (count <= 1) return
    val reduceMotion = rememberReduceMotion()
    val safeCurrent = current.coerceIn(0, count - 1)

    Row(
        modifier = modifier.semantics {
            contentDescription = "第 ${safeCurrent + 1} 页，共 $count 页"
        },
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(count) { index ->
            val active = index == safeCurrent
            // 只对「当前」与「上一页」做宽度动画：8 个点全部常驻动画没有视觉收益，
            // 反而在快速滑动时同时跑 8 条动画。
            val width by animateDpAsState(
                targetValue = if (active) activeWidth else dotSize,
                animationSpec = if (reduceMotion) {
                    tween(0)
                } else {
                    tween(durationMillis = 220, easing = FastOutSlowInEasing)
                },
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .height(dotSize)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}
