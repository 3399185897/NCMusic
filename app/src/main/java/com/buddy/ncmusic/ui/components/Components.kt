package com.buddy.ncmusic.ui.components

import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.aspectRatio
import com.buddy.ncmusic.ui.theme.AppShapes
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.launch

/** 通用封面图 */
@Composable
fun CoverImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    AsyncImage(
        model = url?.fixNeteaseImage(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

/**
 * 内容底部安全区高度。
 *
 * 仅比悬浮岛略大一点，且**悬浮岛不显示时不预留大空间**：
 * - 有歌曲播放（悬浮岛可见）：预留 92dp（岛高约 76 + 上下呼吸间距）
 * - 无歌曲播放：仅 14dp，让内容几乎贴到底栏
 *
 * 用法：`contentPadding = PaddingValues(bottom = rememberBottomSafeSpace())`
 */
@Composable
fun rememberBottomSafeSpace(): androidx.compose.ui.unit.Dp {
    val state by com.buddy.ncmusic.playback.PlaybackManager.state.collectAsState()
    return if (state.song != null) 92.dp else 14.dp
}

/** 带按压缩放动效的 IconButton（全局统一交互） */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bouncy",
    )
    IconButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        interactionSource = interaction,
    ) {
        content()
    }
}

/** 网易云图片需带 param 尺寸参数，否则可能加载失败/过大 */
private fun String.fixNeteaseImage(): String =
    if (contains("music.126.net") && !contains("param=")) "$this?param=300y300" else this

/** 歌曲列表项（独立卡片区块，带间隔；长按查看详情） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((Long) -> Unit)? = null,
) {
    var showDetail by remember { mutableStateOf(false) }
    if (showDetail) SongDetailDialog(song = song, onDismiss = { showDetail = false })

    val playback by PlaybackManager.state.collectAsState()
    // 仅在「当前歌曲且正在播放」时显示跳动动效
    val isCurrent = playback.song?.id == song.id && playback.isPlaying

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(AppShapes.of(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .combinedClickable(onClick = onClick, onLongClick = { showDetail = true })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = song.coverUrl,
            modifier = Modifier.size(50.dp).clip(AppShapes.of(10.dp)),
            contentDescription = song.name,
        )
        Spacer(Modifier.width(12.dp))
        if (isCurrent) {
            PlayingIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = song.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artistNames,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = if (onArtistClick != null) {
                    Modifier.clickable { song.artists.firstOrNull()?.id?.let(onArtistClick) }
                } else {
                    Modifier
                },
            )
        }
        // 详情按钮：窄长的竖条圆角矩形（瘦高），底色随主题色
        Box(
            modifier = Modifier
                .size(width = 21.dp, height = 37.dp)
                .clip(AppShapes.of(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                .clickable { showDetail = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "歌曲信息",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 歌曲详情对话框（长按列表项或点击右侧三点触发） */
@Composable
fun SongDetailDialog(song: Song, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val shareIntentContext = LocalContext.current
    var downloading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var showQualityPicker by remember { mutableStateOf(false) }

    if (showQualityPicker) {
        AlertDialog(
            onDismissRequest = { showQualityPicker = false },
            title = { Text("选择下载音质") },
            text = {
                Column {
                    DOWNLOAD_QUALITIES.forEach { (q, label, kbps) ->
                        val sizeText = estimateSizeMb(song.duration, kbps)
                            .let { if (it > 0) "约 %.1f MB".format(it) else "大小未知" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showQualityPicker = false
                                    downloading = true
                                    statusMsg = "下载中（$label）…"
                                    scope.launch {
                                        when (val r = NCMusicApp.instance.musicRepository.downloadSong(song, q)) {
                                            is ApiResult.Success -> statusMsg = "已下载：${r.data.name}"
                                            is ApiResult.Error -> statusMsg = "下载失败：${r.message}"
                                        }
                                        downloading = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityPicker = false }) { Text("取消") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("歌曲详情") },
        text = {
            Column {
                DetailRow("歌名", song.name)
                DetailRow("歌手", song.artistNames)
                DetailRow("专辑", song.album?.name ?: "未知")
                DetailRow("时长", formatDuration(song.duration))
                DetailRow("歌曲 ID", song.id.toString())
                statusMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${song.name} - ${song.artistNames}\nhttps://music.163.com/song?id=${song.id}",
                        )
                    }
                    runCatching {
                        shareIntentContext?.startActivity(
                            Intent.createChooser(share, "分享歌曲")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("分享") }
                TextButton(
                    enabled = !downloading,
                    onClick = { showQualityPicker = true },
                ) { Text("下载") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

/** 可选下载音质：code / 名称 / 近似码率(kbps) */
private val DOWNLOAD_QUALITIES = listOf(
    Triple("standard", "标准", 128),
    Triple("higher", "较高", 192),
    Triple("exhigh", "极高（推荐）", 320),
    Triple("lossless", "无损 FLAC", 900),
    Triple("hires", "Hi-Res", 1500),
    Triple("jymaster", "超清母带", 2000),
)

/** 按码率与歌曲时长估算文件大小 */
private fun estimateSizeMb(durationMs: Long, kbps: Int): Double {
    if (durationMs <= 0) return 0.0
    return durationMs / 1000.0 * kbps / 8.0 / 1024.0
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** 歌单卡片 */
@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 封面与文字合成一个独立卡片块（有底色与圆角，而非漂浮在页面背景上）
    Column(
        modifier = modifier
            .width(142.dp)
            .clip(AppShapes.of(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        CoverImage(
            url = playlist.coverImgUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AppShapes.of(13.dp)),
            contentDescription = playlist.name,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 小节标题 */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 正在播放动效：三条跳动的竖线 */
@Composable
fun PlayingIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing")
    val bars = listOf(0, 1, 2).map { i ->
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 420 + i * 130,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }
    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(h.value)
                    .clip(AppShapes.of(2.dp))
                    .background(color),
            )
        }
    }
}

/** 加载中 */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // MD3 Loading indicator：
        //  - 弧线在旋转的同时**弧长伸缩**（indeterminate 规范）
        //  - 显示低对比轨道，使"未完成量"可读（MD3 要求 track 对比度不足 3:1 时需可见）
        //  - 圆头线帽 + 4dp 线宽，对齐 MD3 的中等尺寸规格
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 4.dp,
            strokeCap = StrokeCap.Round,
        )
    }
}

/** 错误提示 + 重试 */
@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        // MD3 推荐：恢复类动作用 Filled tonal（中等强调），
        // 比 Outlined 更有"可点击"的实感，又不至于像 Filled 那样抢焦点
        FilledTonalButton(onClick = onRetry) { Text("重试") }
    }
}

/** 悬浮岛显隐动画的统一缓动曲线 */
private val EASING = FastOutSlowInEasing

/**
 * 底部悬浮岛播放条（胶囊造型）。
 *
 * 参考主流音乐 App 的迷你播放器：
 * - **胶囊外形**：大圆角 + 投影，四周留边悬浮于列表之上
 * - **专辑取色**：背景取自当前封面主色的淡化版本
 * - **圆形封面**（非圆角方形）
 * - **三个圆形控件**：上一首 / 下一首为浅色圆底，
 *   播放暂停为**实心专辑色圆底 + 白色图标**，形成视觉焦点
 * - 左右滑动切歌
 *
 * @param visible 是否可见。
 *   为 false 时**仅做淡出 + 下移动画，组件本身保留在组合树中**。
 *   这样做是为了避免「移除再重建」带来的开销：
 *   本组件内含 [rememberCoverColor]（Palette 解码位图取色）与 Coil 图片加载，
 *   若随路由切换反复销毁重建，回到 Tab 时会重新取色，
 *   异步结果返回的瞬间颜色发生跳变 —— 即用户感知的「卡一帧」。
 *   常驻组合后这些内部状态只初始化一次，显隐只是 alpha / 位移的动画，无重建成本。
 */
@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val state by PlaybackManager.state.collectAsState()
    val song = state.song ?: return
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val prefs = NCMusicApp.instance.userPreferences
    val playerColorOn by prefs.userStateFlow.collectAsState(initial = UserState())
    val coverColor = if (playerColorOn.playerColorEnabled) {
        rememberCoverColor(song.coverUrl)
    } else {
        null
    }
    val accent = coverColor ?: MaterialTheme.colorScheme.primary
    // 专辑色淡染表面：既保留专辑个性，又保证前景文字对比度
    val containerColor = accent.copy(alpha = 0.30f)
        .compositeOver(MaterialTheme.colorScheme.surface)
    val onContainer = MaterialTheme.colorScheme.onSurface

    // 播放进度：供环绕封面的圆环使用
    val progress = if (state.duration > 0) {
        (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 显隐动画：不可见时淡出并微微下移（呼应它悬浮在底部的位置）
    //
    // ⚠️ 这里刻意**不用 `by` 委托**读取动画值。
    // `val v by animateFloatAsState(...)` 会在**组合作用域**解包 State，
    // 导致动画每推进一帧就触发一次**重组**（recompose）——
    // 悬浮岛内含封面、环形进度、多个控件，每帧重组会明显掉帧。
    // 保留 State 对象并在 `graphicsLayer { }` 的 lambda 内读取（见下方），
    // 动画就只驱动**绘制阶段**，每帧只重绘、不重组 —— 这是 Compose 动画的关键性能惯例。
    val visibilityState = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 220 else 160, easing = EASING),
        label = "miniPlayerVisibility",
    )
    val hideOffsetPx = with(LocalDensity.current) { 28.dp.toPx() }

    Surface(
        shape = AppShapes.of(30.dp),
        color = containerColor,
        // 不加阴影：阴影会在岛的圆角外形成一层灰色遮挡，
        // 这里希望四周完全透出下层内容
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .graphicsLayer {
                // 在绘制阶段读取动画值：每帧只重绘，不触发重组
                val v = visibilityState.value
                translationX = dragOffset
                alpha = v
                translationY = (1f - v) * hideOffsetPx
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(visible) {
                        // 不可见时不接收任何手势 —— 否则这个透明的悬浮岛会拦截下层点击
                        if (!visible) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragAccum < -70f -> PlaybackManager.next()
                                    dragAccum > 70f -> PlaybackManager.previous()
                                }
                                dragAccum = 0f
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragAccum = 0f
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, amount ->
                                dragAccum += amount
                                dragOffset = (dragOffset + amount * 0.5f)
                                    .coerceIn(-280f, 280f)
                            },
                        )
                    }
                    .clickable(enabled = visible, onClick = onOpen)
                    .padding(start = 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圆形封面 + 外圈环形播放进度
                Box(
                    modifier = Modifier.size(50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverImage(
                        url = song.coverUrl,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentDescription = song.name,
                    )
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(50.dp),
                        strokeWidth = 2.5.dp,
                        color = accent,
                        trackColor = onContainer.copy(alpha = 0.13f),
                    )
                }
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                    )
                    Text(
                        text = song.artistNames,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.65f),
                    )
                }

                // 上一首：浅色圆底
                CircleControlButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = onContainer,
                    onClick = { PlaybackManager.previous() },
                )
                Spacer(Modifier.width(6.dp))

                // 播放 / 暂停：实心专辑色圆底 + 白色图标（视觉焦点）
                FilledIconButton(
                    onClick = { PlaybackManager.togglePlayPause() },
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = Color.White,
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))

                // 下一首：浅色圆底
                CircleControlButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = onContainer,
                    onClick = { PlaybackManager.next() },
                )
            }

    }
}

/** 浅色圆底图标按钮（用于上一首 / 下一首） */
@Composable
private fun CircleControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    size: Dp = 40.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = contentColor,
        )
    }
}

/**
 * 从封面提取用于 UI 强调的主色。
 *
 * 候选优先级：vibrant → lightVibrant → darkVibrant → muted → dominant。
 * **关键**：逐级过滤饱和度 / 明度过低的颜色 —— 专辑封面偏白或偏灰时，
 * `dominantSwatch` 常返回接近白色的色，直接使用会导致"取色看起来没生效"。
 */
@Composable
fun rememberCoverColor(url: String?): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        color = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(url)
                            // 关键：Coil 默认返回 HARDWARE 位图，而 Palette 无法读取，
                            // 会导致所有 swatch 为 null（取色静默失败）
                            .allowHardware(false)
                            .build(),
                    ).drawable?.toBitmap() ?: return@runCatching null

                    val palette = Palette.from(bitmap).generate()
                    val candidates = listOfNotNull(
                        palette.vibrantSwatch,
                        palette.lightVibrantSwatch,
                        palette.darkVibrantSwatch,
                        palette.mutedSwatch,
                        palette.lightMutedSwatch,
                        palette.darkMutedSwatch,
                        palette.dominantSwatch,
                    )

                    val hsv = FloatArray(3)
                    // 优先选饱和度足够的色；全都偏灰时再退回首个候选
                    val picked = candidates.firstOrNull { swatch ->
                        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                        hsv[1] > 0.18f && hsv[2] > 0.15f
                    } ?: candidates.firstOrNull()

                    picked?.rgb
                }.getOrNull()?.let { Color(it) }
            }
        }
    }
    return color
}
