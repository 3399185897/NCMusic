package com.buddy.ncmusic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot

/**
 * 全局颜色扩散状态。
 *
 * 设置页点击某个主题色时调用 [launch]，把「点击位置（root 坐标）」与「目标颜色」
 * 记录下来；由 [ColorRevealOverlay] 播放一次由该点向外扩散的圆形动画。
 */
object ColorRevealState {
    /** 扩散起点（相对 root 的像素坐标） */
    var origin by mutableStateOf(Offset.Zero)
        private set

    /** 扩散颜色 */
    var color by mutableStateOf<Color?>(null)
        private set

    /** 每次触发自增，作为动画的重启标识 */
    var token by mutableIntStateOf(0)
        private set

    val isActive: Boolean get() = token > 0

    fun launch(originPx: Offset, newColor: Color) {
        origin = originPx
        color = newColor
        token++
    }

    fun clear() {
        token = 0
        color = null
    }
}

/**
 * 颜色扩散遮罩。
 *
 * 应挂在内容最上层（如 `MainActivity` 的 `setContent` 根部 Box 中）。
 * 半径由 0 增长至屏幕对角线长度，形成"从点击处漫开"的过渡，动画结束自动清理。
 */
@Composable
fun ColorRevealOverlay(durationMillis: Int = 520) {
    val token = ColorRevealState.token
    if (token == 0) return
    val revealColor = ColorRevealState.color ?: return
    val origin = ColorRevealState.origin

    val progress = remember(token) { Animatable(0f) }

    LaunchedEffect(token) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        )
        ColorRevealState.clear()
    }

    Canvas(Modifier.fillMaxSize()) {
        val maxRadius = hypot(size.width, size.height)
        val radius = maxRadius * progress.value
        if (radius > 0f) {
            drawCircle(color = revealColor, radius = radius, center = origin)
        }
    }
}