package com.buddy.ncmusic.ui.screens.lyric

import androidx.compose.ui.graphics.toArgb
import com.buddy.ncmusic.ui.components.rememberCoverColor
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.ui.theme.orAccent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import com.buddy.ncmusic.ui.util.Md3Motion
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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

/**
 * 元信息行（作词 / 作曲 / 编曲 / 制作人 …）。
 *
 * LRC 里这类行与正文混在一起，时间戳通常是最早的那几个，
 * 若不排除，歌曲开头会把它们当作"正在播放的歌词"高亮放大。
 */
private val META_LINE = Regex("""^\s*(作词|作曲|编曲|制作人|出品|监制|混音|母带|录音|吉他|贝斯|鼓|键盘|和声|OP|SP)\s*[:：]""")

/** 用户手动滑动后，多久恢复自动跟随（毫秒） */
private const val FOLLOW_RESUME_DELAY_MS = 4000L

/**
 * 把某一行滚到指示线位置（居中偏上）。
 *
 * ## 为什么不能一步到位
 *
 * `scrollToItem(index, offset)` 里的 offset 是**像素**，且需要提前知道行高 ——
 * 而目标行在滚动前往往不可见，拿不到它的 size。原先用
 * `visibleItemsInfo.firstOrNull()?.size` 估算，遇到首项是被裁切的半行时
 * 误差很大，加上 offset 符号写反，结果就是"进入歌词页几乎不滚动"。
 *
 * 这里改成两段式：
 *  1. 先把目标行顶到视口顶部（offset = 0），这一帧之后它必然可见；
 *  2. 读到它的真实高度，再按"指示线位置 - 半行高"滚回去。
 *
 * @param animate 是否用动画（进入时用 false，跟随播放时用 true）
 */
private suspend fun LazyListState.centerOnItem(index: Int, animate: Boolean) {
    if (index < 0 || index >= layoutInfo.totalItemsCount) return
    if (animate) animateScrollToItem(index, 0) else scrollToItem(index, 0)
    // 等一帧：让目标行完成布局，才能拿到它的真实位置与高度
    androidx.compose.runtime.withFrameNanos { }

    val info = layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    if (viewport <= 0) return
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return

    // 直接比较"这一行现在在哪"与"它应该在哪"，而不是推算偏移量。
    //
    // 之前用 `viewport * FRACTION - itemHeight / 2` 当滚动量，暗含了
    // "scrollToItem(i, 0) 之后这一行的顶部正好在视口顶端"这个假设 ——
    // 但列表带 contentPadding(vertical = 120dp)，视口起点与内容起点的
    // 定义并不重合，算出来的偏移就整体偏了一段：指示线压在上面的
    // 「作词/编曲」行上，真正的当前行沉到屏幕下方。
    val currentCenter = item.offset + item.size / 2f
    val targetCenter = info.viewportStartOffset + viewport * INDICATOR_FRACTION
    scrollBy(currentCenter - targetCenter)
}

@Composable
fun LyricScreen(onBack: () -> Unit) {
    val state by PlaybackManager.state.collectAsState()
    val lyrics = state.lyrics
    val listState = rememberLazyListState()

    // 动态取色：当前行沿用封面主色，与播放器/悬浮岛保持同一套色彩语言
    val coverColor = rememberCoverColor(state.song?.coverUrl)

    val prefs = NCMusicApp.instance.userPreferences
    val userState by prefs.userStateFlow.collectAsState(initial = UserState())
    val customLyricColor = userState.lyricColor
    val autoAlign = userState.lyricAutoAlign

    // 关键：用 derivedStateOf 收敛 —— position 每帧都在更新，
    // 但只有「当前行索引」真正变化时才会通知下游重组，避免整列表高频刷新
    // 元信息行（作词/作曲/编曲）不参与"当前行"判定。
    //
    // 这些行的时间戳是 0 或极早，歌曲开头会被当成"正在唱作词：xxx"，
    // 列表中那一行被放大标红 —— 既不是歌词，也会让用户以为进度出错。
    // 判定标准：整行没有中文/日文正文之外的内容，形如「作词：xxx」。
    val lyricMeta = remember(lyrics) {
        lyrics.map { META_LINE.containsMatchIn(it.text.trim()) }
    }

    val currentIndex by remember(lyrics, lyricMeta) {
        derivedStateOf {
            if (lyrics.isEmpty()) {
                0
            } else {
                // 初值取**第一条真正的歌词**，而不是下标 0 ——
                // 歌曲开头 position 很小，所有歌词的时间都还没到，
                // 若从 0 起算，高亮会永远停在第 0 行（通常是「作词：xxx」）。
                var found = lyricMeta.indexOfFirst { !it }
                if (found < 0) found = 0
                for (i in lyrics.indices) {
                    if (lyrics[i].time <= state.position && !lyricMeta[i]) found = i
                }
                found
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

    val followScope = rememberCoroutineScope()
    var followEnabled by remember { mutableStateOf(true) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    // 进入时的无动画定位完成前，不启动跟随，避免两段滚动打架
    var initialJumped by remember { mutableStateOf(false) }

    // 进入页面时定位一次（受设置开关控制）。
    // 用非动画 scrollToItem —— 避免动画被后续操作/重组打断造成抖动
    LaunchedEffect(lyrics, autoAlign) {
        if (!autoAlign || lyrics.isEmpty()) return@LaunchedEffect
        delay(120) // 等列表完成首次布局
        val idx = lyrics
            .indexOfLast { it.time <= PlaybackManager.state.value.position }
            .coerceAtLeast(0)
        runCatching { listState.centerOnItem(idx, animate = false) }
        initialJumped = true
    }

    // ============================================================
    // 播放跟随
    //
    // 原来只有"进入时定位一次"，之后歌词不再跟着走 ——
    // 用户停在歌词页听歌时，当前行会一路往下跑出屏幕。
    // 这里补两层：
    //  · 当前行变化 → 平滑滚到指示线位置（animateScrollToItem）
    //  · 用户一旦手动拖拽 → 立刻暂停跟随，松手 4 秒后自动恢复
    //    （直接恢复会和"用户正在看某一句"冲突，所以留一个从容的窗口）
    // ============================================================

    LaunchedEffect(listState, autoAlign) {
        if (!autoAlign) return@LaunchedEffect
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    followEnabled = false
                    resumeJob?.cancel()
                    resumeJob = null
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    resumeJob?.cancel()
                    resumeJob = followScope.launch {
                        delay(FOLLOW_RESUME_DELAY_MS)
                        followEnabled = true
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(currentIndex, followEnabled, initialJumped, autoAlign) {
        if (!autoAlign || !followEnabled || !initialJumped || lyrics.isEmpty()) {
            return@LaunchedEffect
        }
        runCatching { listState.centerOnItem(currentIndex, animate = true) }
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

                            coverColor = coverColor,
                            // 再点一下歌词即返回播放器。
                            // 与播放器「点封面进歌词」构成对称的一对；滚动不会被吃掉
                            // （clickable 只在手指没有位移时判定为点击）。
                            onClick = onBack,
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
    coverColor: Color?,
    onClick: () -> Unit = {},
) {
    val emphasize = isCurrent || isFocused

    // 颜色策略：
    //  · 正在播放 → **封面动态取色**（跟随当前专辑的色调）
    //  · 用户显式指定过歌词色 → 尊重用户选择
    //  · 其余 → 灰色弱化
    //
    // ⚠️ 此处曾长期失效：判断"是否自定义过"用的是
    // `custom != 0 && custom != -1`，而 `lyricColor` 的默认值
    // 恰恰是 `0xFFFFFFFF`（白色）—— 该条件**恒为真**，
    // 于是永远走 `Color(custom)` 显示白色，`coverColor` 形同虚设。
    // 表现为"正在播放的歌词一直是白色，不随封面变色"。
    //
    // 现在把默认值与旧默认值都视为"未自定义"，让封面取色真正生效。
    val lineColor = when {
        isFocused -> MaterialTheme.colorScheme.onSurface
        isCurrent -> {
            val custom = customColor.toInt()
            val customized = custom != 0 &&
                custom != -1 &&
                custom != Color.White.toArgb()      // 旧默认值，等同"未设置"
            when {
                // 封面取色优先，但**灰调封面要回落到主题色**：
                // 黑白人像、老唱片这类封面的主色接近中性灰，
                // 用作高亮时与周围的 onSurfaceVariant 几乎同色，
                // 表现为"当前行只是变大了一点点"，失去指示作用。
                coverColor != null -> coverColor.orAccent(MaterialTheme.colorScheme.primary)
                customized -> Color(custom)
                else -> MaterialTheme.colorScheme.primary
            }
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
            // 两种点击语义：
            //  · 被指示线压住的那一行 → 跳到该句（原有行为）
            //  · 其余任意一行 → 返回播放器（"再点一下"即可退出歌词页）
            .clickable {
                if (isFocused) PlaybackManager.seekToAndPlay(line.time) else onClick()
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
            // 字号与颜色做过渡，而不是切换 style。
            //
            // 原先靠 `if (emphasize) titleLarge else bodyLarge` 硬切：
            // 播到下一句时，那一行的字号在**一个帧内**从 16sp 跳到 22sp，
            // 行高随之改变，整个列表会向下顶一下 —— 用户看到的是"歌词在抖"。
            // 动画化字号 + 行高后，过渡期间只是平滑放大，不会位移。
            val baseStyle = MaterialTheme.typography.bodyLarge
            val animatedFontSize by animateFloatAsState(
                targetValue = if (emphasize) 21f else 16f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                label = "lyric_font",
            )
            val animatedColor by animateColorAsState(
                targetValue = lineColor,
                animationSpec = tween(Md3Motion.Medium1),
                label = "lyric_color",
            )
            Text(
                text = line.text,
                textAlign = TextAlign.Center,
                style = baseStyle.copy(
                    fontSize = animatedFontSize.sp,
                    lineHeight = (animatedFontSize * 1.42f).sp,
                ),
                color = animatedColor,
            )
            if (line.translation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                val transSize by animateFloatAsState(
                    targetValue = if (emphasize) 15f else 13f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    label = "lyric_trans_font",
                )
                Text(
                    text = line.translation,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = transSize.sp,
                        lineHeight = (transSize * 1.4f).sp,
                    ),
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
