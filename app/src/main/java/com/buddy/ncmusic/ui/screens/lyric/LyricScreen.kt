package com.buddy.ncmusic.ui.screens.lyric

import com.buddy.ncmusic.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.util.LyricLine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 指示线位置：视口高度的 42%（中部偏上） */
private const val INDICATOR_FRACTION = 0.42f

/** 滑动停止后隐藏指示线的延迟 */
private const val HIDE_INDICATOR_DELAY_MS = 2600L

@Composable
fun LyricScreen(onBack: () -> Unit) {
    val state by PlaybackManager.state.collectAsState()
    val lyrics = state.lyrics
    val listState = rememberLazyListState()

    val prefs = NCMusicApp.instance.userPreferences
    val userState by prefs.userStateFlow.collectAsState(initial = UserState())
    val customLyricColor = userState.lyricColor
    val autoAlign = userState.lyricAutoAlign

    // 关键：用 derivedStateOf 收敛 —— position 每帧都在更新，
    // 但只有「当前行索引」真正变化时才会通知下游重组，避免整列表高频刷新
    val currentIndex by remember(lyrics) {
        derivedStateOf {
            if (lyrics.isEmpty()) {
                0
            } else {
                lyrics.indexOfLast { it.time <= state.position }.coerceAtLeast(0)
            }
        }
    }

    // 指示线：仅手动滑动期间显示
    var showIndicator by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            showIndicator = true
        } else {
            delay(HIDE_INDICATOR_DELAY_MS)
            showIndicator = false
        }
    }

    // 指示线压住的那一行
    val focusIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            if (viewportHeight <= 0f) return@derivedStateOf -1
            val lineY = info.viewportStartOffset + viewportHeight * INDICATOR_FRACTION
            info.visibleItemsInfo
                .firstOrNull { lineY >= it.offset && lineY < it.offset + it.size }
                ?.index ?: -1
        }
    }

    // 进入页面时定位一次（受设置开关控制）。
    // 用非动画 scrollToItem —— 避免动画被后续操作/重组打断造成抖动
    LaunchedEffect(lyrics, autoAlign) {
        if (!autoAlign || lyrics.isEmpty()) return@LaunchedEffect
        delay(120) // 等列表完成首次布局，确保能取到行高
        runCatching {
            val info = listState.layoutInfo
            val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
            if (viewportHeight <= 0) return@runCatching
            val rowHeight = info.visibleItemsInfo.firstOrNull()?.size ?: (viewportHeight / 9)
            val offset = (viewportHeight * INDICATOR_FRACTION - rowHeight / 2f)
                .roundToInt().coerceAtLeast(0)
            val idx = lyrics
                .indexOfLast { it.time <= PlaybackManager.state.value.position }
                .coerceAtLeast(0)
            listState.scrollToItem(idx, offset)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("歌词", style = MaterialTheme.typography.titleMedium)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (lyrics.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无歌词（点击返回）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 120.dp),
                ) {
                    // 稳定的 key：让 Compose 能复用列表项、跳过未变化的行
                    itemsIndexed(
                        items = lyrics,
                        key = { index, line -> "${line.time}_$index" },
                    ) { index, line ->
                        LyricRow(
                            line = line,
                            isCurrent = index == currentIndex,
                            isFocused = showIndicator && index == focusIndex,
                            customColor = customLyricColor,
                        )
                    }
                }

                // 指示线：滑动时浮在歌词之上
                if (showIndicator) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(INDICATOR_FRACTION)
                            .padding(start = 64.dp, end = 52.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单行歌词。
 *
 * 抽成独立 Composable 的意义：Compose 可对其做**参数级跳过** ——
 * 当某行的 [line] / [isCurrent] / [isFocused] 都未变化时直接跳过重组，
 * 避免"当前行切换导致整个可见列表重绘"。
 */
@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    isFocused: Boolean,
    customColor: Long,
) {
    val emphasize = isCurrent || isFocused

    // 颜色策略：
    // - 直线压住 → onSurface（深色主题即白色，浅色主题自动转深色）
    // - 正在播放 → 保持自定义歌词色 / 主题色
    // - 其余 → 灰色弱化
    val lineColor = when {
        isFocused -> MaterialTheme.colorScheme.onSurface
        isCurrent -> {
            val custom = customColor.toInt()
            if (custom != 0 && custom != -1) Color(custom) else MaterialTheme.colorScheme.primary
        }
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.of(10.dp))
            .background(
                if (isFocused) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(enabled = isFocused) {
                PlaybackManager.seekToAndPlay(line.time)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧时间：仅被指示线压住的行显示（固定宽度占位，避免布局抖动）
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) {
            if (isFocused) {
                Text(
                    text = formatTime(line.time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 歌词原文 + 官方翻译
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = line.text,
                textAlign = TextAlign.Center,
                style = if (emphasize) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.bodyLarge,
                color = lineColor,
            )
            if (line.translation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = line.translation,
                    textAlign = TextAlign.Center,
                    style = if (emphasize) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.bodySmall,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    } else {
                        lineColor.copy(alpha = if (emphasize) 0.82f else 0.55f)
                    },
                )
            }
        }

        // 右侧播放按钮：仅被指示线压住的行显示
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (isFocused) {
                IconButton(
                    onClick = { PlaybackManager.seekToAndPlay(line.time) },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "从此行播放",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
