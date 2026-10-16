package com.buddy.ncmusic.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

/**
 * 统一的页面转场动画。
 *
 * 设计要点（借鉴 PixelPlayer）：
 * 1. **统一节奏** —— 全部 350ms + FastOutSlowInEasing，切换观感一致
 * 2. **视差（parallax）** —— 新页面全宽滑入，旧页面只移动 1/3，形成层次纵深
 * 3. **缩放配合** —— 返回时叠加 scaleIn / scaleOut，强化空间感
 * 4. **播放页特殊处理** —— 由底部上滑（呼应 MiniPlayer 的位置），而非左右推入
 */

/** 统一转场时长（毫秒） */
const val TRANSITION_DURATION = 350

private val EASING = FastOutSlowInEasing

/**
 * 动画规格工厂。
 *
 * 这些函数原本各自写死固定时长的 tween，**完全无视系统的「移除动画」偏好** ——
 * 用户开启无障碍的移除动画后，页面切换依然满屏位移与淡入淡出。
 *
 * 现在统一走这里：减动效时返回 `snap()`（瞬时完成），否则按原参数播放。
 *
 * 注意非减动效分支**必须与原参数完全一致**（350ms + FastOutSlowInEasing），
 * 这是本项目既定的视觉节奏，不因本次改动而调整。
 */
private fun <T> motionSpec(
    reduceMotion: Boolean,
    durationMillis: Int = TRANSITION_DURATION,
): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (reduceMotion) {
        androidx.compose.animation.core.snap()
    } else {
        tween(durationMillis, easing = EASING)
    }

// ---------------- 普通页面：左右推入 ----------------

/** 进入：从右侧全宽滑入 */
fun enterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInHorizontally(
        animationSpec = motionSpec(reduceMotion),
        initialOffsetX = { it },
    )

/** 退出：仅左移 1/3 + 淡出 —— 与新页面形成视差 */
fun exitTransition(reduceMotion: Boolean = false): ExitTransition =
    slideOutHorizontally(
        animationSpec = motionSpec(reduceMotion),
        targetOffsetX = { -it / 3 },
    ) + fadeOut(animationSpec = motionSpec(reduceMotion))

/**
 * 返回进入：从左 1/5 滑入 + 淡入。
 *
 * 注意：此处**刻意不加 scaleIn** —— 整页缩放需为内容建立独立图层并逐帧重采样，
 * 在列表/播放页等复杂内容上会造成明显掉帧（返回时"卡一帧"的主因）。
 * 位移量也从 1/3 收到 1/5，进一步降低重绘面积。
 */
fun popEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInHorizontally(
        animationSpec = motionSpec(reduceMotion),
        initialOffsetX = { -it / 5 },
    ) + fadeIn(animationSpec = motionSpec(reduceMotion))

/** 返回退出：向右滑出 + 淡出（同样避免整页缩放） */
fun popExitTransition(reduceMotion: Boolean = false): ExitTransition =
    slideOutHorizontally(
        animationSpec = motionSpec(reduceMotion),
        targetOffsetX = { it },
    ) + fadeOut(animationSpec = motionSpec(reduceMotion))

// ---------------- 播放页：自底部升起 ----------------

/** 播放页进入：自底部滑入（位移量收敛到 1/3，兼顾观感与流畅度） */
fun playerEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInVertically(
        animationSpec = motionSpec(reduceMotion),
        initialOffsetY = { it / 3 },
    ) + fadeIn(animationSpec = motionSpec(reduceMotion, 260))

/**
 * 播放页退出：淡出 + **小幅下移**（而非整屏滑出）。
 *
 * 播放页包含大图封面、渐变背景与歌词，整屏位移会逐帧重绘全页导致掉帧；
 * 缩短时长并限制位移量后可保持流畅。
 */
fun playerExitTransition(reduceMotion: Boolean = false): ExitTransition =
    fadeOut(animationSpec = motionSpec(reduceMotion, 200)) +
        slideOutVertically(
            animationSpec = motionSpec(reduceMotion, 200),
            targetOffsetY = { it / 8 },
        )

/** 播放页被覆盖（如进入歌词页）：仅轻微下移，保留层次感（不做缩放） */
fun playerPopExitTransition(reduceMotion: Boolean = false): ExitTransition =
    slideOutVertically(
        animationSpec = motionSpec(reduceMotion, 280),
        targetOffsetY = { it / 8 },
    ) + fadeOut(animationSpec = motionSpec(reduceMotion, 240))

/** 从歌词页/其他页返回播放页：上移复位（不做缩放） */
fun playerPopEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInVertically(
        animationSpec = motionSpec(reduceMotion, 280),
        initialOffsetY = { it / 8 },
    ) + fadeIn(animationSpec = motionSpec(reduceMotion, 240))
