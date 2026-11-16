package com.buddy.ncmusic.ui.theme

import androidx.compose.runtime.remember
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
    // 配色风格（TonalSpot / Vibrant / Expressive …），仅在自选主题色下生效
    paletteStyle: String = PaletteStyle.TonalSpot.key,
    // 色彩标准（SPEC_2021 / SPEC_2025），影响主色 tone 与整体色度
    colorSpec: String = ColorStandard.SPEC_2021.key,
    // 深色下是否使用 AMOLED 纯黑背景
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 底色来源的优先级：**自定义色 > 莫奈取色 > 内置配色**。
    //
    // 深色只决定**明暗**，取色只决定**色调来源**，两者正交。
    val hasCustom = customColor != 0L
    val style = remember(paletteStyle) { PaletteStyle.of(paletteStyle) }
    val requestedSpec = remember(colorSpec) { ColorStandard.of(colorSpec) }
    // 风格不支持 SPEC_2025 时静默回退（同 SukiSU 的 effectiveFor）
    val spec = remember(requestedSpec, style) { requestedSpec.effectiveFor(style) }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    // 「跟随 Monet + 默认风格」时直接用系统取色方案；
    // 其余情况以壁纸主色为种子重新展开（否则改风格界面无变化）。
    val useSystemScheme = !hasCustom && dynamicColor && dynamicAvailable &&
        style == PaletteStyle.TonalSpot && spec == ColorStandard.SPEC_2021 && !amoled

    // ⚠️ 必须 remember。
    //
    // md3Scheme 一次要算 20 多个 tone，每个 tone 还要二分 20 次反解明度 ≈
    // 400+ 次色彩空间换算；systemScheme 也要重新解析系统调色板。
    // 这些原先都写在组合体里 —— 主题一旦重组（主界面任何偏好变化都会触发），
    // 就在主线程重算整张调色板，正好落在页面切换的那一帧上。
    val colorScheme = remember(customColor, dynamicColor, darkTheme, style, spec, useSystemScheme, amoled) {
        when {
            useSystemScheme -> systemScheme(context, darkTheme)
            hasCustom -> md3Scheme(Color(customColor.toInt()), darkTheme, style, spec, amoled)
            dynamicColor && dynamicAvailable ->
                md3Scheme(Color(systemScheme(context, darkTheme).primary.value), darkTheme, style, spec, amoled)
            darkTheme -> if (amoled) DarkColorScheme.amoledSurface() else DarkColorScheme
            else -> LightColorScheme
        }
    }

    // 换色时整张色板做 spring 过渡（对齐 SukiSU 的 ColorScheme.animateAsState）
    val animatedScheme = colorScheme.animateAsState()

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        shapes = NCMusicShapes,
        content = content,
    )
}

/**
 * 系统动态取色方案（非 Composable，便于在 `remember` 里缓存）。
 *
 * 之前是 `@Composable fun dynamicScheme()`，且一次组合里被调用两次
 * （一次取底色、一次取种子色），每次都重新解析系统调色板。
 */
@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("NewApi")
private fun systemScheme(context: android.content.Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

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

    // 钟形色度曲线。
    //
    // 指数从 1.3 降到 0.55：原曲线在 tone 94~98（surface / surfaceContainer）
    // 处几乎归零 —— 实测 `#FDFBFD` 与纯白肉眼无差，于是**切换主题色时背景完全不变**，
    // 用户看到的就是"颜色改了但界面没变"。
    // 平方根形状在两端衰减更缓，高 tone 仍保留约 25%~35% 的色度，
    // 背景会呈现清晰的色偏，而 tone 50 附近（outline）不受影响。
    // 端点仍强制为 0：tone 100 必须是纯白，否则 onPrimary 会带色。
    val bell = if (t <= 0 || t >= 100) {
        0f
    } else {
        kotlin.math.sin(Math.PI.toFloat() * t / 100f).pow(0.55f)
    }
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
 * ## 三个输入各管一件事
 *
 * - **seed**：色相与色度的起点
 * - **[style]**：三组主色的色相偏移与色度分配（primary / secondary / tertiary）
 * - **[spec]**：tone 取值与整体色度系数（2021 内敛 / 2025 明快）
 *
 * ## tone 取值
 *
 * ```
 * primary        spec.primaryTone / 深色 primaryToneDark
 * container      90 / 30（深色）
 * surface        98 / 深色纯黑           surfaceContainer*  100 → 90 递减
 * outline        50 / 80
 * ```
 *
 * 深色主题的 background / surface 沿用本项目既定的**纯黑**；
 * 其余容器色仍带主题色调，避免整屏只有黑白两色。
 * error 系列保持 MD3 标准红 —— 错误语义不应随主题色漂移。
 */
private fun md3Scheme(
    seed: Color,
    dark: Boolean,
    style: PaletteStyle,
    spec: ColorStandard,
    amoled: Boolean = false,
): ColorScheme {
    val cs = spec.chromaScale
    fun p(tone: Int) = tonal(seed, tone, style.primaryHue, style.primaryChroma * cs)
    fun s(tone: Int) = tonal(seed, tone, style.secondaryHue, style.secondaryChroma * cs)
    fun t(tone: Int) = tonal(seed, tone, style.tertiaryHue, style.tertiaryChroma * cs)
    fun n(tone: Int) = tonal(seed, tone, 0f, style.neutralChroma * cs)

    return if (dark) {
        LightColorScheme.copy(
            primary = p(spec.primaryToneDark), onPrimary = p(20),
            primaryContainer = p(spec.primaryContainerToneDark), onPrimaryContainer = p(90),
            secondary = s(spec.primaryToneDark), onSecondary = s(20),
            secondaryContainer = s(spec.primaryContainerToneDark), onSecondaryContainer = s(90),
            tertiary = t(spec.primaryToneDark), onTertiary = t(20),
            tertiaryContainer = t(spec.primaryContainerToneDark), onTertiaryContainer = t(90),
            // 深色底：**不是**纯黑，而是极低 tone 的中性色。
            //
            // 之前这里直接写死 #000000，导致「深色」与「深色·纯黑(AMOLED)」
            // 看起来完全一样 —— AMOLED 失去了存在意义。
            // 按 MD3 的深色规范，surface 取 tone 6；AMOLED 模式才压到真纯黑。
            background = if (amoled) Color(0xFF000000) else n(6),
            onBackground = n(90),
            surface = if (amoled) Color(0xFF000000) else n(6),
            onSurface = n(90),
            surfaceVariant = n(30), onSurfaceVariant = n(80),
            // AMOLED：页面级表面纯黑，容器保留极低 tone 以维持卡片层次
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = if (amoled) n(5) else n(10),
            surfaceContainer = if (amoled) n(8) else n(12),
            surfaceContainerHigh = if (amoled) n(11) else n(17),
            surfaceContainerHighest = if (amoled) n(14) else n(22),
            outline = n(60), outlineVariant = n(30),
            surfaceTint = p(spec.primaryToneDark), inversePrimary = p(40),
            inverseSurface = n(90), inverseOnSurface = n(20),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        LightColorScheme.copy(
            primary = p(spec.primaryTone), onPrimary = p(100),
            primaryContainer = p(spec.primaryContainerTone), onPrimaryContainer = p(10),
            secondary = s(spec.primaryTone), onSecondary = s(100),
            secondaryContainer = s(spec.primaryContainerTone), onSecondaryContainer = s(10),
            tertiary = t(spec.primaryTone), onTertiary = t(100),
            tertiaryContainer = t(spec.primaryContainerTone), onTertiaryContainer = t(10),
            background = n(98), onBackground = n(10),
            surface = n(98), onSurface = n(10),
            surfaceVariant = n(90), onSurfaceVariant = n(30),
            surfaceContainerLowest = n(100),
            surfaceContainerLow = n(96),
            surfaceContainer = n(94),
            surfaceContainerHigh = n(92),
            surfaceContainerHighest = n(90),
            outline = n(50), outlineVariant = n(80),
            surfaceTint = p(spec.primaryTone), inversePrimary = p(80),
            inverseSurface = n(20), inverseOnSurface = n(95),
        )
    }
}

private fun blend(base: Color, overlay: Color, alpha: Float): Color = Color(
    red = base.red + (overlay.red - base.red) * alpha,
    green = base.green + (overlay.green - base.green) * alpha,
    blue = base.blue + (overlay.blue - base.blue) * alpha,
    alpha = 1f,
)

/**
 * 风格预览用配色。
 *
 * 与 [NCMusicTheme] 走**同一套** [md3Scheme]，因此预览色与实际应用后的
 * 结果完全一致 —— 预览不会"看起来选了 A、应用后是 B"。
 */
internal fun previewScheme(
    seed: Long,
    dark: Boolean,
    style: PaletteStyle,
    spec: ColorStandard,
): ColorScheme = md3Scheme(Color(seed.toInt()), dark, style, spec)

/**
 * 取「Monet 种子色」——用于风格预览。
 *
 * 系统动态方案里的 `primary` 已经过 HCT 取样，色相与壁纸一致，
 * 拿来当种子足够接近；重点是它**不随风格变化**，
 * 因此预览带不会因为切换风格而整体漂移。
 */
@Composable
internal fun rememberMonetSeed(dark: Boolean): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return remember(dark) {
        systemScheme(context, dark).primary.toArgb().toLong() and 0xFFFFFFFFL
    }
}
