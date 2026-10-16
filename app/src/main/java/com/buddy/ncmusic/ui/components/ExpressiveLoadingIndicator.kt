package com.buddy.ncmusic.ui.components

import com.buddy.ncmusic.ui.util.rememberReduceMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.ui.theme.ExpressiveShapes

/**
 * Material 3 Expressive 的 Loading indicator。
 *
 * ## 为什么是"移植"而非"直接调用"
 *
 * 官方组件 `androidx.compose.material3.LoadingIndicator` 随 material3 **1.5.0**
 * 发布，而 1.5.0 依赖 Compose 1.12 → 要求 **AGP 9.1.0 + compileSdk 37**；
 * 本项目为 AGP 8.13.0 / SDK 36，升级工具链的代价远超收益。
 * 因此这里**移植官方实现**：几何取自 AOSP 的 `MaterialShapes.kt`
 * （见 [ExpressiveShapes]，顶点数值未改动），运动参数取自官方
 * `LoadingIndicator.kt` 的常量。
 *
 * ## 三条并行的、刻意不同步的动画
 *
 * | 通道 | 参数 | 来源 |
 * |---|---|---|
 * | 整体旋转 | 4666ms 一圈、线性、**全程不停**、不因变形重置 | `GlobalRotationDurationMillis` |
 * | 形状变形 | 每次落定即切换下一个 | 弹簧驱动，无固定间隔 |
 * | 变形曲线 | `spring(dampingRatio = 0.6, stiffness = 200)`，约 650ms 落定 | 官方 spec |
 *
 * 形状序列为官方 `indeterminateIndicatorPolygons` 的顺序：
 * SoftBurst → Cookie9Sided → Pentagon → Pill → Sunny → Cookie4Sided → Oval
 *
 * ## 无障碍
 *
 * 系统「关闭动画」（`ANIMATOR_DURATION_SCALE == 0`）时**停止旋转**，
 * 但**保留形状变形** —— 与官方行为一致：变形承担主要的信息传达，
 * 旋转只是节奏性的装饰。
 *
 * @param modifier 建议外部给 48dp 容器尺寸，形状会按 0.79 比例内缩居中
 * @param color 指示器颜色，默认 `colorScheme.primary`
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val context = LocalContext.current

    // 无障碍：系统关闭动画时停掉旋转。
    // 改用共享工具，与全项目其余动画保持同一判定口径
    // （原先只读 ANIMATOR_DURATION_SCALE，漏了 TRANSITION_ANIMATION_SCALE）。
    val reduceMotion = rememberReduceMotion()

    // ---------- 官方形状序列 ----------
    val polygons = remember {
        listOf(
            ExpressiveShapes.SoftBurst,
            ExpressiveShapes.Cookie9Sided,
            ExpressiveShapes.Pentagon,
            ExpressiveShapes.Pill,
            ExpressiveShapes.Sunny,
            ExpressiveShapes.Cookie4Sided,
            ExpressiveShapes.Oval,
        )
    }
    // Morph 需要**预先配对**：每个 shape 都要与下一个能做对应点映射。
    // 不做预配对而在切换时临时构造，会丢掉首尾衔接的连续性。
    val morphs = remember(polygons) {
        List(polygons.size) { i -> Morph(polygons[i], polygons[(i + 1) % polygons.size]) }
    }

    // ---------- 通道 ①：整体旋转 ----------
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = GLOBAL_ROTATION_MILLIS,
                        easing = LinearEasing,
                    ),
                ),
            )
        }
    }

    // ---------- 通道 ②/③：形状变形 + 90° 自转 ----------
    var index by remember { mutableIntStateOf(0) }
    val morphProgress = remember { Animatable(0f) }
    var spinTurns by remember { mutableIntStateOf(0) }

    LaunchedEffect(morphs) {
        while (true) {
            morphProgress.snapTo(0f)
            // 弹簧驱动：落定即返回，因此**无需额外延时**就进入下一次变形，
            // 这正好实现了官方「落定即开始下一次」的节奏。
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 200f,
                ),
            )
            index = (index + 1) % morphs.size
            spinTurns += 1
        }
    }

    // 每次变形附带 90° 自转（用同一根弹簧，与变形同步落定）
    val extraSpin by animateFloatAsState(
        targetValue = spinTurns * 90f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "expressive_spin",
    )

    Canvas(modifier) {
        rotate(rotation.value + extraSpin) {
            // 指示器尺寸 = 容器的 0.79（官方 ActiveIndicatorScale）
            val side = size.minDimension * ACTIVE_INDICATOR_SCALE
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f

            val cubics = morphs[index].asCubics(morphProgress.value)
            val path = Path()

            // 每条 Cubic 提供 anchor0 / control0 / control1 / anchor1，
            // 首条先 moveTo 到起点，其余顺次 cubicTo 串联。
            cubics.forEachIndexed { i, c ->
                val p0 = Offset(left + c.anchor0X * side, top + c.anchor0Y * side)
                val p1 = Offset(left + c.control0X * side, top + c.control0Y * side)
                val p2 = Offset(left + c.control1X * side, top + c.control1Y * side)
                val p3 = Offset(left + c.anchor1X * side, top + c.anchor1Y * side)
                if (i == 0) path.moveTo(p0.x, p0.y)
                path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            path.close()

            drawPath(path = path, color = color)
        }
    }
}

/**
 * 带容器的 Loading indicator。
 *
 * 容器用 `primaryContainer`、形状用 `onPrimaryContainer` ——
 * 两者在 M3 色板中保证 **≥ 3:1** 的对比度，满足指示器的可辨识性要求。
 */
@Composable
fun ContainedExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = CONTAINER_SIZE,
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(AppShapes.of(containerSize / 2))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(containerSize),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ---------- 常量（对齐官方 LoadingIndicator 规格）----------

/** 容器边长：官方 `LoadingIndicatorDefaults.ContainerWidth/Height`。 */
private val CONTAINER_SIZE = 48.dp

/**
 * 活动形状相对容器的比例：官方 `ActiveIndicatorScale ≈ 0.79`
 * （38dp 形状置于 48dp 容器中）。
 */
private const val ACTIVE_INDICATOR_SCALE = 0.79f

/** 官方 `GlobalRotationDurationMillis` —— 一圈 4666ms。 */
private const val GLOBAL_ROTATION_MILLIS = 4666
