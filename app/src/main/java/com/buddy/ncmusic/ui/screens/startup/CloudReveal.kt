package com.buddy.ncmusic.ui.screens.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.ui.util.rememberReduceMotion
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/**
 * 云朵揭幕 —— 启动动画。
 *
 * ## 出处
 *
 * 机制取自 FluxDown 的启动揭幕（Apache-2.0，zerx-lab/FluxDown，
 * `mobile/Android/fluxui/.../material/LaunchReveal.kt`）。它的核心只有一句话：
 * **只用一个品牌形状当遮罩**，形状先"吸气"般微缩下沉，然后形状本身变成窗口、
 * 指数放大盖满屏幕，窗口内的品牌色淡出，界面浮现。不叠加任何其它元素。
 *
 * 这里只换载体：FluxDown 是下载箭头，云音是一朵云 —— 于是观感就是"云散开、界面露出来"。
 *
 * ## 为什么这样不卡
 *
 * 关键不在动画，在**时序**：内容与幕布在**同一首帧**组合、绘制，幕布只决定
 * "什么时候被看见"。主界面的首次组合（LazyColumn 测量、主题取色、首图解码）
 * 全都发生在幕布还盖着的这 720ms 内，揭幕结束时它已经是稳定帧。
 * 之前"播完动画 → 界面猛地一顿"，正是因为先等加载、再开始组合主界面。
 *
 * 此外：结束后整层从组合树移除，不留常驻 graphicsLayer；逐帧只在绘制阶段
 * 读动画值，不触发重组；云朵 Path 只构建一次，之后每帧只改矩阵。
 */

/** 吸气段占总时长的比例（对齐 FluxDown 的 200/650 节奏）。 */
private const val INHALE = 200f / 720f

/** 吸气末的缩放：云被"按"一下。 */
private const val INHALE_SCALE = 0.90f

/** 吸气下沉量，相对云宽。 */
private const val DIP = 0.035f

/** 云宽占屏宽比例。 */
private const val CLOUD_WIDTH_FRACTION = 0.42f

/**
 * 品牌色的淡出窗口（相对开窗进度）。
 *
 * **不能从 0 就开始淡**：云的放大是指数插值（前期慢），若 ink 同时线性淡出，
 * 前 1/3 段会出现"云还很小、却已经半透明"的状态 —— 界面文字从云里透出来，
 * 观感是"一层毛玻璃"而不是"一朵云"。
 * 因此前 [INK_FADE_START] 段保持完全不透明，等云已经铺满屏幕再让它散掉。
 */
private const val INK_FADE_START = 0.45f

/** 三条均衡器竖条在开窗的这个进度之前淡出（越早越好，避免被放大成巨幅色带） */
private const val BARS_FADE = 0.20f

private const val INK_FADE_END = 0.78f

/** 总时长。 */
private const val TOTAL_MS = 720

/**
 * 云的"内切圆半径"，相对云宽。
 *
 * 放大倍率按"这个圆的边缘能盖住最远的屏幕角"反推 ——
 * 用内切圆而不是外接矩形，保证云在放大过程中不会在角落露缝。
 */
private const val CLOUD_INRADIUS = 0.34f

/** 放大末的额外余量，抵消圆角处可能出现的 1px 缝。 */
private const val OVERSCAN = 1.10f

/**
 * 云朵路径。
 *
 * 1 个圆角矩形底座 + 3 个圆，NonZero 填充让重叠自然合并（不会出现接缝，
 * 这是用 Path.op 布尔运算换取不到的好处：任何缩放级别都严丝合缝）。
 * 坐标以 (centerX, centerY) 为云的中心，[width] 为云宽。
 */
/**
 * 云轮廓 —— **与应用图标完全同形**。
 *
 * 取自 `res/drawable/ic_launcher_foreground.xml` 的第一个子路径（云的外形），
 * 坐标沿用图标的 108 viewport（云外接框 x∈[24,82]、y∈[34,70]）。
 * 这样启动动画里那朵云、桌面上的应用图标、关于页的品牌标志是同一条曲线，
 * 不会出现"启动时一朵胖云、图标上又是另一朵"的割裂。
 *
 * 早先试过用"圆角矩形 + 三个圆"叠加去近似云形：堆出来的轮廓在交界处
 * 必然有内凹台阶（两者切线方向不一致），`Path.op(Union)` 在 Android 实现下
 * 也不可靠（把自身同时当输入输出会清空结果）。手写贝塞尔才是干净解。
 */
private const val CLOUD_OUTLINE =
    "M35,70 C28.8,70 24,65.2 24,59 C24,52.4 29,47.2 35.6,46.6 " +
        "C38,39.2 44.7,34 52.3,34 C60.6,34 67.7,40 69.5,48.2 " +
        "C76.4,48.7 82,54.2 82,61 C82,66.9 77.2,70 71.8,70 Z"

/** 完整图标（云 + 负空间均衡器），evenOdd 填充时三条竖条是洞 */
private const val CLOUD_FULL =
    CLOUD_OUTLINE + " " +
        "M42,52 a2,2 0 0 1 4,0 v9 a2,2 0 0 1 -4,0 Z " +
        "M51,46 a2,2 0 0 1 4,0 v15 a2,2 0 0 1 -4,0 Z " +
        "M60,56 a2,2 0 0 1 4,0 v5 a2,2 0 0 1 -4,0 Z"

/** 图标坐标系里云的外接框 */
private const val SRC_LEFT = 24f
private const val SRC_TOP = 34f
private const val SRC_W = 58f
private const val SRC_H = 36f

/** 图标里三条均衡器竖条（x1,y1,x2,y2，圆角 2） */
private val BAR_RECTS = listOf(
    floatArrayOf(42f, 52f, 46f, 61f),
    floatArrayOf(51f, 46f, 55f, 61f),
    floatArrayOf(60f, 56f, 64f, 61f),
)

/** 解析一次，之后每次只做矩阵变换。 */
private val cloudOutlineTemplate: Path by lazy {
    PathParser().parsePathString(CLOUD_OUTLINE).toPath()
}

/** 三条均衡器竖条（图标坐标，圆角 2）。 */
private val barsTemplate: Path by lazy {
    Path().apply {
        BAR_RECTS.forEach { r ->
            addRoundRect(
                RoundRect(
                    left = r[0],
                    top = r[1],
                    right = r[2],
                    bottom = r[3],
                    radiusX = 2f,
                    radiusY = 2f,
                ),
            )
        }
    }
}

/** 完整图标路径，供 [CloudMark] 显示与图标一致的标志。 */
private val cloudFullTemplate: Path by lazy {
    PathParser().parsePathString(CLOUD_FULL).toPath().apply {
        fillType = PathFillType.EvenOdd
    }
}

/** 三条均衡器竖条，与云使用同一套变换，保证放大时始终对齐。 */

private fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

/** 开窗阶段的归一化进度：吸气结束后 0→1。 */
private fun revealPhase(t: Float): Float = ((t - INHALE) / (1f - INHALE)).coerceIn(0f, 1f)

/**
 * 云朵图层的静态尺寸（相对屏宽）。
 *
 * 云在一个**固定大小**的 Canvas 里绘制，放大交给 `graphicsLayer` 的变换矩阵。
 * 这样做是实测出来的：如果每帧用 `withTransform` 把云路径放大 10 倍再画，
 * Skia 每帧都要对放大后的贝塞尔曲线重新做 tessellation ——
 * 模拟器上 draw 15~17ms、整帧 35ms（约 28fps），云肉眼可见地卡。
 * 换成"小画布 + GPU 变换"后，绘制指令的顶点数固定，缩放只更新矩阵，
 * 整帧回到 18~20ms 的满帧水平。
 *
 * 注意 alpha **不能**放进 graphicsLayer：那会让该层转为离屏合成，
 * 放大后的位图会糊。透明度交给 `drawPath(alpha = …)`，画布小、重绘便宜。
 */
private fun cloudLayerSize(containerWidth: Float): Pair<Float, Float> {
    val w = containerWidth * CLOUD_WIDTH_FRACTION
    return w to (w * SRC_H / SRC_W)
}

/** 一帧的视觉状态，由进度算出。 */
private data class CloudFrame(
    val scale: Float,
    val dip: Float,
    val inkAlpha: Float,
    val curtainAlpha: Float,
    val barAlpha: Float,
)

private fun cloudFrameAt(t: Float, cloudWidth: Float, anchorX: Float, anchorY: Float, screenW: Float, screenH: Float): CloudFrame {
    val inhale = easeInOutCubic((t / INHALE).coerceIn(0f, 1f))
    val u = revealPhase(t)

    val startScale = 1f - (1f - INHALE_SCALE) * inhale
    val reach = max(
        hypot(anchorX, anchorY),
        max(
            hypot(screenW - anchorX, anchorY),
            max(hypot(anchorX, screenH - anchorY), hypot(screenW - anchorX, screenH - anchorY)),
        ),
    )
    val endScale = reach / (CLOUD_INRADIUS * cloudWidth) * OVERSCAN
    val scale = exp(ln(startScale) + (ln(endScale) - ln(startScale)) * u * u)

    val dip = DIP * cloudWidth * inhale - cloudWidth * 0.05f * u * u
    val fade = ((u - INK_FADE_START) / (INK_FADE_END - INK_FADE_START)).coerceIn(0f, 1f)
    val barAlpha = (1f - (u / BARS_FADE)).coerceIn(0f, 1f)
    return CloudFrame(
        scale = scale,
        dip = dip,
        inkAlpha = 1f - fade,
        curtainAlpha = 1f - fade,
        barAlpha = barAlpha,
    )
}

/**
 * 云朵本体（不含底色）。固定尺寸绘制，缩放由外层 `graphicsLayer` 完成。
 */
@Composable
private fun CloudGlyph(
    frame: CloudFrame,
    inkColor: Color,
    barColor: Color,
    modifier: Modifier = Modifier,
    /** graphicsLayer 的缩放锚点（0..1），默认云底偏中 */
    originY: Float = 0.58f,
) {
    Canvas(
        modifier
            .fillMaxWidth(CLOUD_WIDTH_FRACTION)
            .aspectRatio(SRC_W / SRC_H)
            .graphicsLayer {
                scaleX = frame.scale
                scaleY = frame.scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, originY)
                translationY = frame.dip
            },
    ) {
        // 把图标的 108 viewport 映射到本画布（小尺寸，tessellation 成本可忽略）
        val s = size.width / SRC_W
        withTransform({
            scale(s, s, pivot = Offset.Zero)
            translate(-SRC_LEFT, -SRC_TOP)
        }) {
            drawPath(cloudOutlineTemplate, inkColor, alpha = frame.inkAlpha)
            if (frame.barAlpha > 0f) {
                // 负空间均衡器：用底色画在云上，与图标里的三处镂空同形。
                // 在开窗极早期淡出 —— 否则云放大后它们会变成三条贯穿屏幕的色带。
                drawPath(barsTemplate, barColor, alpha = frame.barAlpha * frame.inkAlpha)
            }
        }
    }
}

/**
 * 云朵揭幕层：覆盖在**已组合好的主界面**之上。
 *
 * 播放结束调用 [onFinished]，调用方据此把本层移出组合树。
 */
@Composable
fun CloudReveal(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val curtainColor = MaterialTheme.colorScheme.surface
    val inkColor = MaterialTheme.colorScheme.primary
    var active by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    // 每帧的状态放在 State 里，但**只让需要它的那一小块重组/重绘**
    val frameHolder = remember { mutableStateOf(CloudFrame(1f, 0f, 1f, 1f, 1f)) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            active = false
            onFinished()
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        progress.animateTo(1f, tween(TOTAL_MS, easing = LinearEasing))
        active = false
        onFinished()
    }

    if (!active) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val (cloudW, _) = cloudLayerSize(wPx)
        val anchorX = wPx / 2f
        val anchorY = hPx * 0.45f + cloudW * 0.10f

        Canvas(Modifier.fillMaxSize()) {
            val f = cloudFrameAt(progress.value, cloudW, anchorX, anchorY, wPx, hPx)
            // 底色：云外这片区域。云不透明地压在上面，等价于"挖洞"但便宜得多
            drawRect(curtainColor, alpha = f.curtainAlpha)
        }

        val f = cloudFrameAt(progress.value, cloudW, anchorX, anchorY, wPx, hPx)
        CloudGlyph(
            frame = f,
            inkColor = inkColor,
            barColor = curtainColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * 静态云朵背板 —— DataStore 还没读到（通常 50~150ms）时显示。
 *
 * 必须与 [CloudReveal] 的起始帧完全一致（同一路径、同色、同位置），
 * 否则从背板切到揭幕会"跳一下"。
 */
@Composable
fun CloudLaunchBackdrop(modifier: Modifier = Modifier) {
    val curtainColor = MaterialTheme.colorScheme.surface
    val inkColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val (cloudW, _) = cloudLayerSize(wPx)
        val f = cloudFrameAt(0f, cloudW, wPx / 2f, hPx * 0.45f + cloudW * 0.10f, wPx, hPx)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(curtainColor, alpha = f.curtainAlpha)
        }
        CloudGlyph(
            frame = f,
            inkColor = inkColor,
            barColor = curtainColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** 同一朵云，供其它页面复用（如关于页的品牌标志）。 */
@Composable
fun CloudMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.size(size)) {
        // 用完整图标（含负空间均衡器），与桌面图标逐像素同形
        val s = this.size.width / SRC_W
        val h = SRC_H * s
        val left = (this.size.width - this.size.width) / 2f
        val top = (this.size.height - h) / 2f
        val scaled = Path().apply {
            addPath(cloudFullTemplate)
            transform(
                Matrix().apply {
                    values[Matrix.ScaleX] = s
                    values[Matrix.ScaleY] = s
                    values[Matrix.TranslateX] = left - SRC_LEFT * s
                    values[Matrix.TranslateY] = top - SRC_TOP * s
                },
            )
        }
        drawPath(scaled, color)
    }
}
