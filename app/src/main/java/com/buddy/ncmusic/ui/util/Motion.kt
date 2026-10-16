package com.buddy.ncmusic.ui.util

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import android.os.Looper
import android.os.Handler
import android.database.ContentObserver
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 「减少动态效果」偏好。
 *
 * ## 依据的系统信号
 *
 * Android 通过**两个**独立设置表达"用户希望少动效"，且它们语义不同：
 *
 * | 设置项 | 取值 | 语义 |
 * |---|---|---|
 * | `ANIMATOR_DURATION_SCALE` | 0 | **完全关闭**动画（无障碍"移除动画"） |
 * | `TRANSITION_ANIMATION_SCALE` | 0 | 同上，作用于窗口转场 |
 *
 * 系统设置页里的「移除动画」会把两者都置 0。
 * 只要**任一为 0**，就应当按"不要动画"处理 ——
 * 这是无障碍要求，不是可选优化。
 *
 * ## 用法
 *
 * ```kotlin
 * val reduceMotion = rememberReduceMotion()
 * val spec = if (reduceMotion) snap() else tween(350)
 * ```
 *
 * ## 为什么要有这个统一入口
 *
 * 原先只有 [ExpressiveLoadingIndicator] 内部自己读了一次系统设置，
 * 其余所有动画（转场、淡入淡出、歌词切换、颜色扩散、转场缩放）
 * 都**完全无视**该偏好 —— 用户开了"移除动画"却依然满屏动效，
 * 属于明显的无障碍缺陷。这里把它抽成可复用能力，逐处接入。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current

    // ⚠️ 不能只 `remember { ... }` 读一次。
    //
    // 系统「移除动画」是**运行时可切换**的 —— 用户完全可能
    // 一边用着 App 一边去设置里打开它（这正是无障碍功能的典型使用方式）。
    // 只读一次会导致：本次会话内所有动画继续照播，直到重启 App 才生效。
    //
    // 这里用 ContentObserver 监听两个 scale 键的 URI，
    // 变化时重新求值并触发重组。开销极小（仅设置变更时回调一次）。
    var reduce by remember { mutableStateOf(isReduceMotionEnabled(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = isReduceMotionEnabled(context)
            }
        }
        val resolver = context.contentResolver
        // 两个键都要监听：系统「移除动画」会把它们同时置 0，
        // 但个别 ROM 只改其中一个
        listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
        ).forEach { key ->
            runCatching {
                resolver.registerContentObserver(
                    Settings.Global.getUriFor(key),
                    false,
                    observer,
                )
            }
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    return reduce
}

/** 非组合环境下的等价判断（供 Service / 后台逻辑使用） */
fun isReduceMotionEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    fun scaleOf(key: String): Float = Settings.Global.getFloat(resolver, key, 1f)

    return scaleOf(Settings.Global.ANIMATOR_DURATION_SCALE) == 0f ||
        scaleOf(Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f
}