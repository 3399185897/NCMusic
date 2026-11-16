package com.buddy.ncmusic.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 整套配色的过渡动画 —— 对应 SukiSU 的 `ColorScheme.animateAsState()`。
 *
 * ## 为什么值得做
 *
 * 切换「色彩风格 / 自选主题色 / 深浅色」时，配色是**整张表**一起换的。
 * 若直接替换 `ColorScheme`，界面会在同一帧从旧色跳到新色：
 * 底栏、卡片、按钮、文字全部瞬变，观感是"闪一下"。
 * SukiSU 的做法是把 48 个 color role 各自交给 `animateColorAsState`，
 * 用默认的 `spring()` 插值 —— 于是换色像一层颜色在界面上"洇开"。
 *
 * ## 代价
 *
 * 每次主题重组都会创建 48 个动画状态。这些状态只在**目标色变化时**才真正跑动画，
 * 稳定态下只是读取一个 `State`，开销可接受（与 SukiSU 同量级）。
 */
@Composable
fun ColorScheme.animateAsState(): ColorScheme {
    @Composable
    fun animated(color: Color, name: String): Color = animateColorAsState(
        targetValue = color,
        animationSpec = spring(),
        label = "theme_$name",
    ).value

    return ColorScheme(
        primary = animated(primary, "primary"),
        onPrimary = animated(onPrimary, "onPrimary"),
        primaryContainer = animated(primaryContainer, "primaryContainer"),
        onPrimaryContainer = animated(onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = animated(inversePrimary, "inversePrimary"),
        secondary = animated(secondary, "secondary"),
        onSecondary = animated(onSecondary, "onSecondary"),
        secondaryContainer = animated(secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animated(onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animated(tertiary, "tertiary"),
        onTertiary = animated(onTertiary, "onTertiary"),
        tertiaryContainer = animated(tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = animated(onTertiaryContainer, "onTertiaryContainer"),
        background = animated(background, "background"),
        onBackground = animated(onBackground, "onBackground"),
        surface = animated(surface, "surface"),
        onSurface = animated(onSurface, "onSurface"),
        surfaceVariant = animated(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animated(onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = animated(surfaceTint, "surfaceTint"),
        inverseSurface = animated(inverseSurface, "inverseSurface"),
        inverseOnSurface = animated(inverseOnSurface, "inverseOnSurface"),
        error = animated(error, "error"),
        onError = animated(onError, "onError"),
        errorContainer = animated(errorContainer, "errorContainer"),
        onErrorContainer = animated(onErrorContainer, "onErrorContainer"),
        outline = animated(outline, "outline"),
        outlineVariant = animated(outlineVariant, "outlineVariant"),
        scrim = animated(scrim, "scrim"),
        surfaceBright = animated(surfaceBright, "surfaceBright"),
        surfaceDim = animated(surfaceDim, "surfaceDim"),
        surfaceContainer = animated(surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = animated(surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = animated(surfaceContainerHighest, "surfaceContainerHighest"),
        surfaceContainerLow = animated(surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = animated(surfaceContainerLowest, "surfaceContainerLowest"),
        primaryFixed = animated(primaryFixed, "primaryFixed"),
        primaryFixedDim = animated(primaryFixedDim, "primaryFixedDim"),
        onPrimaryFixed = animated(onPrimaryFixed, "onPrimaryFixed"),
        onPrimaryFixedVariant = animated(onPrimaryFixedVariant, "onPrimaryFixedVariant"),
        secondaryFixed = animated(secondaryFixed, "secondaryFixed"),
        secondaryFixedDim = animated(secondaryFixedDim, "secondaryFixedDim"),
        onSecondaryFixed = animated(onSecondaryFixed, "onSecondaryFixed"),
        onSecondaryFixedVariant = animated(onSecondaryFixedVariant, "onSecondaryFixedVariant"),
        tertiaryFixed = animated(tertiaryFixed, "tertiaryFixed"),
        tertiaryFixedDim = animated(tertiaryFixedDim, "tertiaryFixedDim"),
        onTertiaryFixed = animated(onTertiaryFixed, "onTertiaryFixed"),
        onTertiaryFixedVariant = animated(onTertiaryFixedVariant, "onTertiaryFixedVariant"),
    )
}

/**
 * AMOLED 纯黑背景（仅用于**非** `md3Scheme` 分支的兜底配色）。
 *
 * 与 SukiSU 的 `amoledBackground()` 有一处**有意的偏离**：
 * 它把 `surfaceContainer*` 也全部压成纯黑，于是卡片与背景完全同色 ——
 * 在它的界面上靠间距与图标仍能分辨，但我们设置页是"多张卡片堆叠"，
 * 全黑会让分组结构彻底消失（看起来是一整块黑）。
 *
 * 因此这里只把「页面级」表面压到纯黑，容器色保留极低 tone 的层次：
 * 远看仍是纯黑，近看卡片边界仍在。
 * （`md3Scheme` 分支里由 [md3Scheme] 自己按同一规则生成。）
 */
fun ColorScheme.amoledSurface(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)
