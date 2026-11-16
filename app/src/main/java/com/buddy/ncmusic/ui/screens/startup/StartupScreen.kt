package com.buddy.ncmusic.ui.screens.startup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.data.prefetch.StartupProgress
import com.buddy.ncmusic.ui.components.BrandMarkTile
import com.buddy.ncmusic.ui.util.Md3Motion
import com.buddy.ncmusic.ui.util.rememberReduceMotion

/** 启动阶段的全屏品牌遮罩。 */
@Composable
fun StartupScreen(
    progress: StartupProgress,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progress.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            tween(Md3Motion.Medium3, easing = Md3Motion.Standard)
        },
        label = "startupProgress",
    )

    // 背景改用 surface，而不是 primaryContainer。
    //
    // 原先整屏铺 primaryContainer（品牌红），加上系统启动窗口也是红色，
    // 冷启动的第一印象是一整块红色"应用图标遮罩"。现在：
    //  · 系统启动窗口 = launch_bg（近白 / 纯黑）
    //  · Compose 启动页 = surface（同上）
    //  · 品牌色只保留在中间的图标块上
    // 三帧颜色连续，不再闪红，也不会让人觉得图标被红块盖住。
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 用 primaryContainer 而不是 primary：
                // 实心品牌红的大圆角块看起来就像"被红色遮罩盖住的图标"。
                // 云朵标志：加载中轻微漂浮，进度走完时向外飘散并淡出 ——
                // "一朵云渐渐消失"，而不是硬切到主界面。
                CloudLogo(
                    size = 148.dp,
                    color = MaterialTheme.colorScheme.primary,
                    reduceMotion = reduceMotion,
                    dissolving = progress.total > 0 && progress.completed >= progress.total,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "云音",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "让每一次聆听更轻盈",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(52.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.width(156.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = startupStatus(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = "NCMusic · Material 3",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

private fun startupStatus(progress: StartupProgress): String = when {
    progress.total == 0 -> "正在准备云音"
    progress.completed >= progress.total -> "准备完成"
    progress.progress < 0.3f -> "正在恢复账号与主题"
    progress.progress < 0.72f -> "正在同步榜单与推荐"
    else -> "马上就好"
}

/** 云朵切片：相对中心的偏移与半径（单位 = 画布半径） */
private data class CloudBlob(val x: Float, val y: Float, val r: Float)

/**
 * 云朵由 6 个叠加圆构成 —— 比用 Path 布尔运算便宜得多，
 * 而且在任意尺寸下都不会出现接缝。
 */
private val CLOUD_BLOBS = listOf(
    // 经典的"三圆 + 平底座"云朵：左肩、右肩、中间高一点的那个
    CloudBlob(-0.40f, 0.04f, 0.34f),
    CloudBlob(0.38f, 0.02f, 0.36f),
    CloudBlob(0.00f, -0.20f, 0.40f),
)

/**
 * 云朵品牌标志。
 *
 * ## 动效
 *
 * 1. **漂浮**：每个切片按各自的相位做极小幅度的上下浮动（正弦），
 *    让静止的圆形组合看起来像一团有体积的云，而不是几个圆叠在一起。
 * 2. **消散**：[dissolving] 置 true 后，切片沿各自的径向向外扩散、
 *    同时缩小并淡出 —— 读起来就是"云被吹散"。
 *
 * 两段都用绘制阶段的 `progress` 驱动，不触发重组。
 */
@Composable
private fun CloudLogo(
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    reduceMotion: Boolean,
    dissolving: Boolean,
    modifier: Modifier = Modifier,
) {
    val drift = rememberInfiniteTransition(label = "cloud_drift")
    val flow by drift.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud_flow",
    )
    val dissolve by animateFloatAsState(
        targetValue = if (dissolving) 1f else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            tween(620, easing = Md3Motion.EmphasizedAccelerate)
        },
        label = "cloud_dissolve",
    )

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension * 0.5f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // ---- 平底座 ----
        // 只靠圆堆出来的云会显得像"花"；底部压一条圆角矩形后，
        // 轮廓立刻变成云（上圆下方）。
        val baseAlpha = (1f - dissolve).coerceIn(0f, 1f)
        if (baseAlpha > 0.01f) {
            val shrink = 1f - 0.30f * dissolve
            val bw = unit * 1.72f * shrink
            val bh = unit * 0.66f * shrink
            drawRoundRect(
                color = color.copy(alpha = baseAlpha * 0.96f),
                topLeft = Offset(cx - bw / 2f, cy + unit * 0.30f - bh / 2f - unit * 0.16f * dissolve),
                size = androidx.compose.ui.geometry.Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bh / 2f),
            )
        }

        CLOUD_BLOBS.forEachIndexed { index, blob ->
            // 每片相位错开，漂浮才不像整体平移
            val phase = flow + index * 0.9f
            val bob = if (reduceMotion) 0f else sin(phase) * unit * 0.035f
            // 消散：沿自身径向向外
            val dirX = blob.x.let { if (kotlin.math.abs(it) < 0.001f) 0f else it }
            val dirY = blob.y
            val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.001f)
            val spread = unit * 0.85f * dissolve
            val alpha = (1f - dissolve).coerceIn(0f, 1f)
            if (alpha <= 0.01f) return@forEachIndexed

            drawCircle(
                color = color.copy(alpha = alpha * 0.96f),
                radius = blob.r * unit * (1f - 0.28f * dissolve),
                center = Offset(
                    cx + blob.x * unit + dirX / len * spread,
                    cy + blob.y * unit + bob + dirY / len * spread - unit * 0.18f * dissolve,
                ),
            )
        }
    }
}
