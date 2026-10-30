package com.buddy.ncmusic.ui.theme

import kotlin.math.pow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Material 3 标准形状令牌。 */
private val NCMusicShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 应用主题：优先使用 Material You 莫奈动态取色（Android 12+），
 * 否则回退到内置的品牌配色（网易云红）。
 */
@Composable
fun NCMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 是否启用莫奈取色，可在设置中关闭
    dynamicColor: Boolean = true,
    // 自定义主题色（ARGB Long，0 = 未设置）
    customColor: Long = 0L,
    content: @Composable () -> Unit,
) {
    // 底色来源的优先级：**自定义色 > 莫奈取色 > 内置配色**。
    //
    // ## 深色与取色不再互斥
    //
    // 原先这里是 `if (darkTheme) DarkColorScheme else ...` 的硬短路 ——
    // 深色下无论取色/自定义色设成什么都走灰阶。结果两个开关必然互相压制：
    // 开了深色，莫奈开关点了没反应；要能取色，就得关掉深色。
    //
    // 现在深色只决定**明暗**，取色只决定**色调来源**，两者正交：
    // 深色 + 取色 → dynamicDarkColorScheme；深色 + 自定义 → 自定义色的深色变体。
    val hasCustom = customColor != 0L
    val base = when {
        !hasCustom && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicScheme(darkTheme)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 自定义主题色：按 MD3 的 tone 表生成**整套**配色。
    //
    // 原先这里手工 blend 各个 surface，只覆盖了 primary 系列：
    // secondary 直接取种子色、tertiary 完全没填 —— 换主色后界面是"一色刷到底"，
    // 次要强调与点缀仍是内置配色。现统一交给 md3Scheme() 按 tone 生成。
    val colorScheme = if (hasCustom) {
        md3Scheme(Color(customColor.toInt()), darkTheme)
    } else {
        base
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NCMusicShapes,
        content = content,
    )
}

@Composable
@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("NewApi") // 调用方已用 Build.VERSION.SDK_INT 守卫；Compose 函数中 lint 无法跨函数推断
private fun dynamicScheme(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    // 按明暗选用对应的取色方案 —— 深色下用 light 版会让界面雪白
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

/** 按 alpha 将 overlay 混入 base，生成带主题色调的背景色 */
/**
 * 取种子色的某个 **tone**（0–100）颜色。
 *
 * ## 为什么不能直接拿 tone 当明度
 *
 * tone 是 MD3 里的 **L\***（感知明度），不是 HSV 的 V。
 * 同一个 tone 40，`#B3261E`（高饱和）的 V 约为 0.70，
 * 而 `#775652`（低饱和）的 V 只有 0.52 —— 饱和不同，V 就不同。
 *
 * 因此这里**按目标 L\* 二分反解 V**：让生成色的相对亮度恰好等于该 tone 对应的亮度。
 * 这样无论种子色饱和度高低，取到的 tone 都准确。
 *
 * 色度则用钟形收敛：tone 50 附近最饱和，趋近 0/100 时趋近无彩 ——
 * 保证 tone 100 = 纯白、tone 0 = 纯黑（否则 onPrimary 会变成粉色、
 * surface 会整页染色）。
 */
private fun tonal(
    seed: Color,
    tone: Int,
    hueShift: Float = 0f,
    chromaScale: Float = 1f,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val h = (hsv[0] + hueShift + 360f) % 360f
    val t = tone.coerceIn(0, 100)

    // 钟形色度曲线
    val bell = kotlin.math.sin(Math.PI.toFloat() * t / 100f).pow(1.3f)
    val sat = (hsv[1] * chromaScale * bell).coerceIn(0f, 1f)

    // 目标相对亮度：L* -> Y
    val targetY = if (t <= 0) 0f
    else ((t + 16f) / 116f).toDouble().pow(3.0).toFloat()

    // 二分求 V，使相对亮度命中 targetY
    var lo = 0f
    var hi = 1f
    repeat(20) {
        val mid = (lo + hi) / 2f
        hsv[0] = h
        hsv[1] = sat
        hsv[2] = mid
        if (relativeLuminance(android.graphics.Color.HSVToColor(hsv)) < targetY) lo = mid else hi = mid
    }
    hsv[0] = h
    hsv[1] = sat
    hsv[2] = (lo + hi) / 2f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** WCAG 相对亮度（线性化后加权） */
private fun relativeLuminance(argb: Int): Float {
    fun lin(c: Int): Float {
        val f = c / 255f
        return if (f <= 0.04045f) f / 12.92f
        else ((f + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    val r = lin((argb shr 16) and 0xFF)
    val g = lin((argb shr 8) and 0xFF)
    val b = lin(argb and 0xFF)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * 由单个种子色生成一整套 MD3 配色。
 *
 * 各角色取固定的 tone（与 Material Theme Builder 的 baseline 一致）：
 * ```
 * primary             40 / 80        primaryContainer            90 / 30
 * secondary           40 / 80（色度 ×0.45）
 * tertiary            40 / 80（色相 +60°，色度 ×0.6）
 * surface             98 / 6         surfaceContainer*        100 → 90 递减
 * ```
 * 深色主题的底色沿用本项目既定的**纯黑**（不掺主题色）。
 * error 系列保持 MD3 标准红 —— 错误语义不应随主题色漂移。
 */
private fun md3Scheme(seed: Color, dark: Boolean): ColorScheme {
    fun p(tone: Int) = tonal(seed, tone)
    fun s(tone: Int) = tonal(seed, tone, chromaScale = 0.45f)
    fun t(tone: Int) = tonal(seed, tone, hueShift = 60f, chromaScale = 0.60f)

    return if (dark) {
        LightColorScheme.copy(
            primary = p(80), onPrimary = p(20),
            primaryContainer = p(30), onPrimaryContainer = p(90),
            secondary = s(80), onSecondary = s(20),
            secondaryContainer = s(30), onSecondaryContainer = s(90),
            tertiary = t(80), onTertiary = t(20),
            tertiaryContainer = t(30), onTertiaryContainer = t(90),
            background = Color(0xFF000000), onBackground = p(90),
            surface = Color(0xFF000000), onSurface = p(90),
            surfaceVariant = p(30), onSurfaceVariant = p(80),
            surfaceContainerLowest = p(4),
            surfaceContainerLow = p(10),
            surfaceContainer = p(12),
            surfaceContainerHigh = p(17),
            surfaceContainerHighest = p(22),
            outline = p(60), outlineVariant = p(30),
            surfaceTint = p(80), inversePrimary = p(40),
            inverseSurface = p(90), inverseOnSurface = p(20),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        LightColorScheme.copy(
            primary = p(40), onPrimary = p(100),
            primaryContainer = p(90), onPrimaryContainer = p(10),
            secondary = s(40), onSecondary = s(100),
            secondaryContainer = s(90), onSecondaryContainer = s(10),
            tertiary = t(40), onTertiary = t(100),
            tertiaryContainer = t(90), onTertiaryContainer = t(10),
            background = p(98), onBackground = p(10),
            surface = p(98), onSurface = p(10),
            surfaceVariant = p(90), onSurfaceVariant = p(30),
            surfaceContainerLowest = p(100),
            surfaceContainerLow = p(96),
            surfaceContainer = p(94),
            surfaceContainerHigh = p(92),
            surfaceContainerHighest = p(90),
            outline = p(50), outlineVariant = p(80),
            surfaceTint = p(40), inversePrimary = p(80),
            inverseSurface = p(20), inverseOnSurface = p(95),
        )
    }
}

private fun blend(base: Color, overlay: Color, alpha: Float): Color = Color(
    red = base.red + (overlay.red - base.red) * alpha,
    green = base.green + (overlay.green - base.green) * alpha,
    blue = base.blue + (overlay.blue - base.blue) * alpha,
    alpha = 1f,
)
