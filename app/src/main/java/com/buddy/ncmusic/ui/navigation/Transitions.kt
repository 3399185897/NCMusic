package com.buddy.ncmusic.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import com.buddy.ncmusic.ui.util.Md3Motion

/**
 * Material 3 页面转场。
 *
 * 使用 M3 官方的 emphasized decelerate / emphasized accelerate 曲线：
 * - 进入由短距离位移 + 淡入组成，避免整页长距离滑动带来的大面积重绘；
 * - 退出比进入更快，形成明确的层级与方向感；
 * - 系统开启“移除动态效果”时统一退化为瞬时切换。
 */

fun enterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium3,
            Md3Motion.EmphasizedDecelerate,
        ),
        initialOffsetX = { it / 4 },
    ) + fadeIn(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.StandardDecelerate,
        ),
    )

fun exitTransition(reduceMotion: Boolean = false): ExitTransition =
    slideOutHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetX = { -it / 8 },
    ) + fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short3,
            Md3Motion.StandardAccelerate,
        ),
    )

fun popEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium3,
            Md3Motion.EmphasizedDecelerate,
        ),
        initialOffsetX = { -it / 5 },
    ) + fadeIn(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.StandardDecelerate,
        ),
    )

fun popExitTransition(reduceMotion: Boolean = false): ExitTransition =
    slideOutHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetX = { it / 8 },
    ) + fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short3,
            Md3Motion.StandardAccelerate,
        ),
    )

/** 底部 Tab 的共享轴横向转场；[forward] 表示从低序号向高序号切换。 */
fun tabEnterTransition(forward: Boolean, reduceMotion: Boolean = false): EnterTransition {
    val direction = if (forward) 1 else -1
    return slideInHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium2,
            Md3Motion.EmphasizedDecelerate,
        ),
        initialOffsetX = { direction * it / 6 },
    ) + fadeIn(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.StandardDecelerate,
        ),
    )
}

fun tabExitTransition(forward: Boolean, reduceMotion: Boolean = false): ExitTransition {
    val direction = if (forward) -1 else 1
    return slideOutHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetX = { direction * it / 10 },
    ) + fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short3,
            Md3Motion.StandardAccelerate,
        ),
    )
}

// ---------------- 播放页：自底部升起 ----------------

fun playerEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInVertically(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Long1,
            Md3Motion.EmphasizedDecelerate,
        ),
        initialOffsetY = { it / 4 },
    ) + fadeIn(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Medium1,
            Md3Motion.StandardDecelerate,
        ),
    )

fun playerExitTransition(reduceMotion: Boolean = false): ExitTransition =
    fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.StandardAccelerate,
        ),
    ) + slideOutVertically(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetY = { it / 8 },
    )

fun playerPopExitTransition(reduceMotion: Boolean = false): ExitTransition =
    slideOutVertically(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium2,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetY = { it / 8 },
    ) + fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Medium1,
            Md3Motion.StandardAccelerate,
        ),
    )

fun playerPopEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    slideInVertically(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium2,
            Md3Motion.EmphasizedDecelerate,
        ),
        initialOffsetY = { it / 8 },
    ) + fadeIn(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Medium1,
            Md3Motion.StandardDecelerate,
        ),
    )
