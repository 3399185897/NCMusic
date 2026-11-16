package com.buddy.ncmusic.ui.navigation

import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.buddy.ncmusic.ui.util.Md3Motion

/**
 * Material 3 页面转场。
 *
 * 采用 SukiSU（ViewPager2 / shared-axis）的**横向推入**：
 * - 新页自右侧推入、旧页向左退 1/4 屏，形成方向一致的"翻页"感；
 * - 位移取 1/4 屏而非全屏：全屏位移会迫使两页整宽重绘，长列表页掉帧；
 * - 系统开启「移除动态效果」时统一退化为瞬时切换。
 */

fun enterTransition(reduceMotion: Boolean = false): EnterTransition =
    // Shared-axis X：新页从右侧推入，同时轻微淡入。
    //
    // 之前用「顺序化淡入淡出」，用户反馈"不像翻页"。SukiSU 的二级页
    // 是横向推入（ViewPager2 / shared axis），这里对齐同一手感：
    // 位移只取 1/4 屏 —— 全屏位移会让两页同时重绘整宽，长列表页掉帧明显，
    // 1/4 屏已经足够表达"从右边过来"的方向感。
    slideInHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium2,
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
    // 旧页向左退 1/4 屏并淡出，与新页形成推入关系
    slideOutHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium2,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetX = { -it / 4 },
    ) + fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.StandardAccelerate,
        ),
    )

fun popEnterTransition(reduceMotion: Boolean = false): EnterTransition =
    // 返回：上一页从左侧回到原位
    slideInHorizontally(
        animationSpec = Md3Motion.spatial(
            reduceMotion,
            Md3Motion.Medium2,
            Md3Motion.EmphasizedDecelerate,
        ),
        initialOffsetX = { -it / 4 },
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
            Md3Motion.Medium2,
            Md3Motion.EmphasizedAccelerate,
        ),
        targetOffsetX = { it / 4 },
    ) + fadeOut(
        animationSpec = Md3Motion.effects(
            reduceMotion,
            Md3Motion.Short4,
            Md3Motion.StandardAccelerate,
        ),
    )

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
