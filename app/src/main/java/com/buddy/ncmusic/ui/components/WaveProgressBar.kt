package com.buddy.ncmusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** 相位推进周期：约 1.5 秒滚动一个完整波形 */
private const val PHASE_PERIOD_SECONDS = 1.5f
private const val TWO_PI = (2 * PI).toFloat()

/**
 * 波浪形播放进度条。
 *
 * 视觉构成：
 * - **已播放段**：正弦波形
 * - **未播放段**：细直线（低对比度）
 * - **末端**：跟随波形起伏的进度圆点
 *
 * 播放状态联动：
 * - **播放中**：波形相位持续滚动（流动感），波幅为设定值
 * - **暂停时**：相位**冻结在当前位置**，波幅平滑衰减为 0 → 波形摊平成直线
 *
 * 交互：点击任意位置跳转；横向拖动精确 seek，拖动期间通过 [onScrub] 上报实时进度。
 *
 * @param progress    当前进度 0f..1f
 * @param onSeek      跳转回调（0f..1f）
 * @param isPlaying   是否正在播放（决定波浪是否流动 / 是否摊平）
 * @param onScrub     拖动中的实时进度回调（松手时传 -1f）
 * @param amplitude   波幅；越小越平缓
 * @param wavelength  波长；越大波峰间距越宽
 */
@Composable
fun WaveProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    playedColor: Color,
    trackColor: Color,
    onScrub: ((Float) -> Unit)? = null,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 36.dp,
    strokeWidth: Dp = 3.5.dp,
    dotRadius: Dp = 5.5.dp,
) {
    val density = LocalDensity.current
    val ampTargetPx = with(density) { amplitude.toPx() }
    val wavePx = with(density) { wavelength.toPx() }
    val strokePx = with(density) { strokeWidth.toPx() }
    val dotPx = with(density) { dotRadius.toPx() }

    // ---------- 相位：播放时持续累加，暂停时冻结在当前位置 ----------
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = (now - lastFrame) / 1_000_000_000f
                lastFrame = now
                // 累加（而非重置），保证暂停后恢复时相位连续
                phase = (phase + dt * (TWO_PI / PHASE_PERIOD_SECONDS)) % TWO_PI
            }
        }
    }

    // ---------- 波幅：暂停时衰减为 0，波形自然摊平成直线 ----------
    val waveAmp by animateFloatAsState(
        targetValue = if (isPlaying) ampTargetPx else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "waveAmp",
    )

    // 拖动中的临时进度（非空表示正在拖动）
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val shown = (scrubbing ?: progress).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(amplitude * 2 + dotRadius * 2 + 6.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        scrubbing = ratio
                        onScrub?.invoke(ratio)
                    },
                    onDragEnd = {
                        scrubbing?.let(onSeek)
                        scrubbing = null
                        onScrub?.invoke(-1f)
                    },
                    onDragCancel = {
                        scrubbing = null
                        onScrub?.invoke(-1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        scrubbing = ratio
                        onScrub?.invoke(ratio)
                    },
                )
            },
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val playedWidth = width * shown

        // ---------- 未播放段：细直线 ----------
        if (playedWidth < width - 1f) {
            drawLine(
                color = trackColor,
                start = Offset(playedWidth + dotPx, centerY),
                end = Offset(width, centerY),
                strokeWidth = strokePx * 0.6f,
                cap = StrokeCap.Round,
            )
        }

        // ---------- 已播放段：正弦波（暂停时 waveAmp = 0，即为直线）----------
        if (playedWidth > 1f) {
            val path = Path()
            val step = 1.2f
            var x = 0f
            while (x <= playedWidth) {
                val y = centerY + waveAmp * sin((x / wavePx) * TWO_PI + phase)
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            drawPath(
                path = path,
                color = playedColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // ---------- 末端圆点：跟随波形起伏 ----------
            val endY = centerY + waveAmp * sin((playedWidth / wavePx) * TWO_PI + phase)
            drawCircle(
                color = playedColor,
                radius = dotPx,
                center = Offset(playedWidth, endY),
            )
        }
    }
}
