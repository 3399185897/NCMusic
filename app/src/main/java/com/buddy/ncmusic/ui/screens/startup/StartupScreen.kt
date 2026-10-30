package com.buddy.ncmusic.ui.screens.startup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandMarkTile(size = 96.dp)
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(52.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.width(156.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = startupStatus(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = "NCMusic · Material 3",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
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
