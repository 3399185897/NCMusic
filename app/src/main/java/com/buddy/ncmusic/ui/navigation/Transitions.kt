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

// ---------------- 普通页面：左右推入 ----------------

/** 进入：从右侧全宽滑入 */
fun enterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(TRANSITION_DURATION, easing = EASING),
        initialOffsetX = { it },
    )

/** 退出：仅左移 1/3 + 淡出 —— 与新页面形成视差 */
fun exitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(TRANSITION_DURATION, easing = EASING),
        targetOffsetX = { -it / 3 },
    ) + fadeOut(animationSpec = tween(TRANSITION_DURATION, easing = EASING))

/**
 * 返回进入：从左 1/5 滑入 + 淡入。
 *
 * 注意：此处**刻意不加 scaleIn** —— 整页缩放需为内容建立独立图层并逐帧重采样，
 * 在列表/播放页等复杂内容上会造成明显掉帧（返回时"卡一帧"的主因）。
 * 位移量也从 1/3 收到 1/5，进一步降低重绘面积。
 */
fun popEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(TRANSITION_DURATION, easing = EASING),
        initialOffsetX = { -it / 5 },
    ) + fadeIn(animationSpec = tween(TRANSITION_DURATION, easing = EASING))

/** 返回退出：向右滑出 + 淡出（同样避免整页缩放） */
fun popExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(TRANSITION_DURATION, easing = EASING),
        targetOffsetX = { it },
    ) + fadeOut(animationSpec = tween(TRANSITION_DURATION, easing = EASING))

// ---------------- 播放页：自底部升起 ----------------

/** 播放页进入：自底部滑入（位移量收敛到 1/3，兼顾观感与流畅度） */
fun playerEnterTransition(): EnterTransition =
    slideInVertically(
        animationSpec = tween(TRANSITION_DURATION, easing = EASING),
        initialOffsetY = { it / 3 },
    ) + fadeIn(animationSpec = tween(260, easing = EASING))

/**
 * 播放页退出：淡出 + **小幅下移**（而非整屏滑出）。
 *
 * 播放页包含大图封面、渐变背景与歌词，整屏位移会逐帧重绘全页导致掉帧；
 * 缩短时长并限制位移量后可保持流畅。
 */
fun playerExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(200, easing = EASING)) +
        slideOutVertically(
            animationSpec = tween(200, easing = EASING),
            targetOffsetY = { it / 8 },
        )

/** 播放页被覆盖（如进入歌词页）：仅轻微下移，保留层次感（不做缩放） */
fun playerPopExitTransition(): ExitTransition =
    slideOutVertically(
        animationSpec = tween(280, easing = EASING),
        targetOffsetY = { it / 8 },
    ) + fadeOut(animationSpec = tween(240, easing = EASING))

/** 从歌词页/其他页返回播放页：上移复位（不做缩放） */
fun playerPopEnterTransition(): EnterTransition =
    slideInVertically(
        animationSpec = tween(280, easing = EASING),
        initialOffsetY = { it / 8 },
    ) + fadeIn(animationSpec = tween(240, easing = EASING))
