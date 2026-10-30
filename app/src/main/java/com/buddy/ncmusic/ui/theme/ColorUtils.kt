package com.buddy.ncmusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
