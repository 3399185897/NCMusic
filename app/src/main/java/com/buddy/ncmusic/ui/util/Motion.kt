package com.buddy.ncmusic.ui.util

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer


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
    // 系统设置在一次会话内极少变化，remember 即可，无需监听
    return remember { isReduceMotionEnabled(context) }
}

/** 非组合环境下的等价判断（供 Service / 后台逻辑使用） */
fun isReduceMotionEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    fun scaleOf(key: String): Float = Settings.Global.getFloat(resolver, key, 1f)

    return scaleOf(Settings.Global.ANIMATOR_DURATION_SCALE) == 0f ||
        scaleOf(Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f
}

/**
 * Material 3 官方运动令牌（material3 1.4.0，MotionTokens）。
 *
 * 项目原先大量使用 [androidx.compose.animation.core.FastOutSlowInEasing]。
 * 它属于旧版 Material 的 legacy 曲线，并非 MD3 的 standard/emphasized
 * 令牌。这里集中声明官方数值，避免每个组件各自写一套“近似动画”。
 */
object Md3Motion {
    // ---- Easing（数值与 Compose Material3 MotionTokens 1.4.0 一致）----
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    // ---- Duration ----
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Long1 = 450
    const val Long4 = 600

    /** 空间属性（位移/尺寸）默认使用 medium2。 */
    fun <T> spatial(
        reduceMotion: Boolean,
        durationMillis: Int = Medium2,
        easing: CubicBezierEasing = EmphasizedDecelerate,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis, delayMillis = delayMillis, easing = easing)
    }

    /** 非空间属性（淡入淡出/颜色）默认使用 short4。 */
    fun <T> effects(
        reduceMotion: Boolean,
        durationMillis: Int = Short4,
        easing: CubicBezierEasing = Standard,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis, delayMillis = delayMillis, easing = easing)
    }
}

/**
 * 按压缩放反馈。
 *
 * 触摸类卡片在 Material 3 里的标准做法是「按下时轻微缩小 + 松手回弹」，
 * 让"点到了"这件事**在视觉上先于导航发生**。它比涟漪更克制，
 * 在卡片这种大目标上尤其有效 —— 涟漪在大圆角卡上往往只露出边角。
 *
 * 用法：
 * ```kotlin
 * Column(Modifier.pressScale(interactionSource).clickable(interactionSource, indication = null) { ... })
 * ```
 * 注意 `indication = null`：缩放本身已经是反馈，再叠涟漪会显得吵。
 * 若希望保留涟漪，直接 `clickable {}` 即可，[pressScale] 仍独立生效。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    reduceMotion: Boolean = rememberReduceMotion(),
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            // 按下要"立刻跟手"，松手要有回弹余韵，因此两者用不同刚度
            spring(dampingRatio = 0.62f, stiffness = if (pressed) 1400f else 520f)
        },
        label = "press_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
