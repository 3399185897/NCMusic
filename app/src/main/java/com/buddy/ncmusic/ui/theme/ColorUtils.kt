package com.buddy.ncmusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * 在任意背景色上选择可读性最高的前景色。
 *
 * 封面取色与用户自定义主题色都不受 Material 色板约束，
 * 固定使用白字会在浅黄/浅青等背景上失去对比度。这里直接比较
 * 黑白两色的 WCAG 对比度，返回更清晰的一项。
 */
fun Color.contrastOn(): Color {
    val l = luminance()
    val whiteContrast = 1.05f / (l + 0.05f)
    val blackContrast = (l + 0.05f) / 0.05f
    return if (whiteContrast >= blackContrast) Color.White else Color(0xFF1A1A1A)
}

/**
 * 封面主色是否**足够鲜艳**，可作为强调色。
 *
 * 取色来自封面本身，而大量专辑封面是灰调 / 黑白 / 低饱和的
 * （人像、纪录片、老唱片）。直接用它当播放键底色，得到的是一颗
 * 灰按钮 —— 主操作在整页里反而最不起眼。
 *
 * 阈值取 HSV 饱和度 0.18：低于它的颜色在视觉上已接近中性灰，
 * 此时回落到主题色，保证主操作始终有色彩焦点。
 */
fun Color.isVividEnough(threshold: Float = 0.18f): Boolean {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv[1] >= threshold
}

/** 鲜艳则用自身，否则退回 [fallback] */
fun Color.orAccent(fallback: Color): Color =
    if (isVividEnough()) this else fallback
