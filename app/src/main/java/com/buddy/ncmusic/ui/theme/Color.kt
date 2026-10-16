package com.buddy.ncmusic.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 网易云红，作为莫奈取色不可用时的品牌 fallback 强调色
private val BrandRed = Color(0xFFD13030)

/**
 * 浅色方案（品牌红）。
 *
 * ## 为什么要把角色补全
 *
 * 原先只定义了 14 个角色，其余（`outline` / `outlineVariant` / `error` /
 * `surfaceContainer*` / `inverse*` / `scrim` 等）**全部落回 `lightColorScheme()`
 * 的内置默认值** —— 那是 M3 基准紫配色下的取值。
 *
 * 后果是配色体系不自洽：本方案底色偏暖（`#FFF8F6`），
 * 而分割线、错误色、各类容器色却是按冷紫基调推导的，
 * 尤其在**分割线 / 描边**上会出现与背景对比不足的情况。
 *
 * ## 对比度要点
 *
 * - `outline` 用于可见描边，与 surface 的对比度需 >= 3:1
 * - `onSurfaceVariant` 用于次要文字，需 >= 4.5:1（正文标准）
 * - 所有 `onXxx` 均已按对应 `Xxx` 底色校验过
 */
val LightColorScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF715B2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF251A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFF8F6),
    surfaceVariant = Color(0xFFF5DDDA),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    onSurface = Color(0xFF231918),
    onSurfaceVariant = Color(0xFF534341),
    // 容器色阶：与 surface 同属暖白系，逐级加深以体现层次
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EE),
    surfaceContainer = Color(0xFFFFEBE8),
    surfaceContainerHigh = Color(0xFFFFE5E1),
    surfaceContainerHighest = Color(0xFFFFDFDB),
    // 描边：outline 需与 surface 有 >= 3:1 对比，否则分割线"看不见"
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    inverseSurface = Color(0xFF392E2D),
    inverseOnSurface = Color(0xFFFFEDEA),
    inversePrimary = Color(0xFFFFB4A9),
    scrim = Color(0xFF000000),
    surfaceTint = BrandRed,
)

/**
 * 深色方案：**纯黑底 + 灰阶强调**。
 *
 * 深色模式采用中性灰作为主题色（见 primary / secondary / tertiary），
 * 这样纯黑背景下不会出现彩色偏移，也与"深色下不参与取色/自定义色"的规则一致。
 */
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF2E2E2E),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFFBDBDBD),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFFCFCFCF),
    // 纯黑底 + 纯白字：深色模式按用户要求做到极致对比
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF2A2A2A),
    background = Color(0xFF000000),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCFCFCF),
    // 补充容器色阶：darkColorScheme 不传这些会用 M3 默认的偏紫色调，
    // 与纯黑底不协调；这里统一为中性灰阶
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3A3A3A),
    // 以下角色若不显式指定，会落回 darkColorScheme() 的内置默认值。
    // 那些默认值是为浅色系深灰底（#1C1B1F）设计的，与纯黑底搭配时
    // 容易出现过暗或偏紫的观感 —— 故一并补全。
    onSecondary = Color(0xFF1F1F1F),
    onTertiary = Color(0xFF1F1F1F),
    tertiaryContainer = Color(0xFF2E2E2E),
    onTertiaryContainer = Color(0xFFEAEAEA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF904A43),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFE0E0E0),
)
