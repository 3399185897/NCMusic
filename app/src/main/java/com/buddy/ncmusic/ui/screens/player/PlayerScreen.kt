package com.buddy.ncmusic.ui.screens.player

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.ui.components.WaveProgressBar
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.playback.LyricWindowService
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.ui.components.rememberCoverColor
import com.buddy.ncmusic.playback.PlaybackManager.PlayMode
import com.buddy.ncmusic.ui.components.BouncyIconButton
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.QueueMusic
import com.buddy.ncmusic.ui.components.Pause
import com.buddy.ncmusic.ui.components.Repeat
import com.buddy.ncmusic.ui.components.RepeatOne
import com.buddy.ncmusic.ui.components.Shuffle
import com.buddy.ncmusic.ui.components.SkipNext
import com.buddy.ncmusic.ui.components.SkipPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenLyric: () -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    /**
     * 是否展开。由宿主（NavGraph）控制：播放器**常驻组合树**，
     * 展开/收起只是整页位移动画，不涉及页面重建 ——
     * 借鉴 PixelPlayer「统一 Sheet」设计，避免 NavHost 转场期间
     * 「新页面首次组合 + 旧页面仍在组合」的双重开销。
     */
    expanded: Boolean = true,
) {
    val state by PlaybackManager.state.collectAsState()
    val song = state.song
    var qualityMenuOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = NCMusicApp.instance.userPreferences
    val userState by prefs.userStateFlow.collectAsState(initial = UserState())
    val overlayOn = userState.lyricOverlay

    /** 开关桌面歌词悬浮窗 */
    fun toggleOverlay() {
        val next = !overlayOn
        scope.launch { prefs.setLyricOverlay(next) }
        if (!next) {
            runCatching { context.stopService(Intent(context, LyricWindowService::class.java)) }
            Toast.makeText(context, "桌面歌词已关闭", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Toast.makeText(context, "请授予「显示在其他应用上层」权限后再次开启", Toast.LENGTH_LONG).show()
        } else {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LyricWindowService::class.java),
                )
            }
            Toast.makeText(context, "桌面歌词已开启", Toast.LENGTH_SHORT).show()
        }
    }

    val coverColor = if (userState.playerColorEnabled) {
        rememberCoverColor(song?.coverUrl)
    } else {
        null
    }
    val currentLyricIndex = remember(state.lyrics, state.position) {
        state.lyrics.indexOfLast { it.time <= state.position }.coerceAtLeast(0)
    }
    val currentLine = state.lyrics.getOrNull(currentLyricIndex)?.text.orEmpty()
    val currentTranslation = state.lyrics.getOrNull(currentLyricIndex)?.translation.orEmpty()
    val nextLine = state.lyrics.getOrNull(currentLyricIndex + 1)?.text.orEmpty()
    val nextTranslation = state.lyrics.getOrNull(currentLyricIndex + 1)?.translation.orEmpty()

    // 整页位移：展开时 translationY = 0，收起时下移出屏。
    //
    // ⚠️ 用 Animatable + 在 graphicsLayer 的 lambda 内读取（见下方），
    // 使动画只驱动**绘制阶段**，每帧只重绘、不重组 —— Compose 动画的性能惯例。
    // 若写成 `val v by animateFloatAsState(...)` 并在组合作用域使用，
    // 每帧都会重组这整个 500+ 行的播放页，必然掉帧。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val offsetY = remember { Animatable(0f) }
        var offsetInitialized by remember { mutableStateOf(false) }

        LaunchedEffect(expanded, heightPx) {
            val target = if (expanded) 0f else heightPx
            if (!offsetInitialized) {
                // 首次测量后直接归位，避免「刚进页面就播一次收起动画」
                offsetY.snapTo(target)
                offsetInitialized = true
            } else {
                offsetY.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }

        // 展开时接管系统返回键 → 收起播放器
        BackHandler(enabled = expanded) { onBack() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offsetY.value }
                // 不透明打底：下方渐变的首色是**半透明**的专辑色（alpha 0.30），
                // 若没有实色垫底，作为覆盖层时会透出下层页面内容（「背景透明化」）。
                //
                // ⚠️ 这个打底必须放在**位移层内部**（即 graphicsLayer 之后）。
                // 若放在外层（BoxWithConstraints 上），播放器收起时只有内层移出屏幕，
                // 外层实色仍铺满整屏 → 会盖住整个界面，表现为「打开应用白屏」。
                .background(MaterialTheme.colorScheme.surface)
                .background(
                    Brush.verticalGradient(
                    colors = listOf(
                        (coverColor ?: MaterialTheme.colorScheme.surface).copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        // 内边距加在内容层而非背景层 —— 渐变背景得以延伸到状态栏下
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "正在播放",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Box {
                    IconButton(onClick = { qualityMenuOpen = true }) {
                        Text(
                            text = qualityLabel(state.quality),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = qualityMenuOpen, onDismissRequest = { qualityMenuOpen = false }) {
                        qualityOptions.forEach { (q, label) ->
                            DropdownMenuItem(
                                text = { Text(if (q == state.quality) "$label ✓" else label) },
                                onClick = {
                                    PlaybackManager.changeQuality(q)
                                    qualityMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            if (song == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无播放内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            // 内容区：居中排列，紧凑不留大空白
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CoverImage(
                    url = song.coverUrl,
                    modifier = Modifier
                        .size(260.dp)
                        .shadow(24.dp, AppShapes.of(24.dp))
                        .clip(AppShapes.of(24.dp))
                        .clickable(onClick = onOpenLyric),
                    contentDescription = song.name,
                )
                Spacer(Modifier.height(16.dp))
                // 歌词区固定高度，避免切换音质时布局抖动
                // 歌词区：当前行 + 翻译 / 下一行 + 翻译，四段完整显示不截断
                Column(
                    modifier = Modifier.height(124.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (currentLine.isNotEmpty()) {
                        // ---- 当前行 ----
                        Text(
                            text = currentLine,
                            style = MaterialTheme.typography.titleSmall,
                            color = coverColor ?: MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentTranslation.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = currentTranslation,
                                style = MaterialTheme.typography.bodySmall,
                                color = (coverColor ?: MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(9.dp))

                        // ---- 下一行（含翻译，整体弱化以区分主次）----
                        Text(
                            text = nextLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (nextTranslation.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = nextTranslation,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artistNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        song.artists.firstOrNull()?.id?.let(onOpenArtist)
                    },
                )
            }

            // 进度与控制（紧凑贴近内容区）
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                // 桌面歌词开关（进度条上方最右侧）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { toggleOverlay() }) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "桌面歌词开关",
                            tint = if (overlayOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 波浪进度条：时间与波形同行（拖动时左侧时间实时跟随）
                val durationMs = state.duration.coerceAtLeast(1L)
                var scrubRatio by remember { mutableFloatStateOf(-1f) }
                val shownMs = if (scrubRatio >= 0f) {
                    (scrubRatio * durationMs).toLong()
                } else {
                    state.position
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(shownMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    WaveProgressBar(
                        progress = state.position.toFloat() / durationMs,
                        onSeek = { PlaybackManager.seekTo((it * durationMs).toLong()) },
                        isPlaying = state.isPlaying,
                        onScrub = { ratio -> scrubRatio = ratio },
                        playedColor = coverColor ?: MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatTime(state.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BouncyIconButton(onClick = { PlaybackManager.cycleMode() }) {
                            Icon(
                                imageVector = modeIcon(state.mode),
                                contentDescription = playModeLabel(state.mode),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 圆形底 + 主色图标，与播放键形成「主次分明的圆形控件组」
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                )
                                .clickable { PlaybackManager.previous() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 播放/暂停键：尺寸与圆角随状态做 spring 变形，
                        // 播放时为"圆角方形"、暂停时为"正圆"，形成可辨识的形态切换
                        val playSize by animateDpAsState(
                            targetValue = if (state.isPlaying) 66.dp else 60.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "playSize",
                        )
                        val playCorner by animateDpAsState(
                            targetValue = if (state.isPlaying) 22.dp else 30.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "playCorner",
                        )

                        Box(
                            modifier = Modifier
                                .size(playSize)
                                .clip(AppShapes.of(playCorner))
                                .background(coverColor ?: MaterialTheme.colorScheme.primary)
                                .clickable { PlaybackManager.togglePlayPause() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                // 图标切换：缩放 + 淡入淡出，避免生硬跳变
                                AnimatedContent(
                                    targetState = state.isPlaying,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.55f) + fadeIn(
                                            tween(180, easing = FastOutSlowInEasing),
                                        )) togetherWith (scaleOut(targetScale = 0.55f) + fadeOut(
                                            tween(140, easing = FastOutSlowInEasing),
                                        ))
                                    },
                                    label = "playIcon",
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) "暂停" else "播放",
                                        modifier = Modifier.size(34.dp),
                                        tint = MaterialTheme.colorScheme.surface,
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 圆形底 + 主色图标，与播放键形成「主次分明的圆形控件组」
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                )
                                .clickable { PlaybackManager.next() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 爱心：图标切换做缩放+淡入淡出，收藏态再叠一层弹性放大
                        val likeScale by animateFloatAsState(
                            targetValue = if (state.isLiked) 1.14f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "likeScale",
                        )
                        BouncyIconButton(onClick = { PlaybackManager.toggleLike() }) {
                            AnimatedContent(
                                targetState = state.isLiked,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.4f) + fadeIn(
                                        tween(180, easing = FastOutSlowInEasing),
                                    )) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut(
                                        tween(140, easing = FastOutSlowInEasing),
                                    ))
                                },
                                label = "likeIcon",
                            ) { liked ->
                                Icon(
                                    imageVector = if (liked) Icons.Filled.Favorite
                                    else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (liked) "取消喜欢" else "喜欢",
                                    modifier = Modifier.size(26.dp).scale(likeScale),
                                    tint = if (liked) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}


private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun modeIcon(mode: PlayMode) = when (mode) {
    PlayMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    else -> Icons.Filled.Repeat
}

private fun playModeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENCE -> "顺序播放"
    PlayMode.LIST_LOOP -> "列表循环"
    PlayMode.SINGLE_LOOP -> "单曲循环"
    PlayMode.SHUFFLE -> "随机播放"
}

private val qualityOptions = listOf(
    "standard" to "标准",
    "higher" to "较高",
    "exhigh" to "极高",
    "lossless" to "无损",
    "hires" to "Hi-Res",
    "jymaster" to "超清母带",
)

private fun qualityLabel(q: String): String =
    qualityOptions.firstOrNull { it.first == q }?.second ?: "标准"
