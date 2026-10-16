package com.buddy.ncmusic.ui.components

import com.buddy.ncmusic.util.AudioQuality
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
    // ⚠️ 只订阅 `hasSongFlow` 而非完整 state。
    //
    // 该函数在各列表页被用作 contentPadding，若订阅完整 PlaybackState，
    // **播放进度每动一次都会让整个列表页重组** ——
    // 而它实际只关心「有没有当前歌曲」这一个布尔量。
    val hasSong by com.buddy.ncmusic.playback.PlaybackManager.hasSongFlow.collectAsState()

    // ⚠️ 这里只需为**悬浮岛**留位，不需要叠加底部导航栏高度。
    //
    // 原因：`NavHost` 已经通过
    //   `modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())`
    // 为导航栏预留了空间（Scaffold 的 innerPadding 已含其高度）。
    // 若此处再加一次导航栏高度，就是**重复计算** ——
    // 内容底部会空出约 80dp 的无效留白，列表滚到底时明显"离底太远"。
    //
    // 悬浮岛的真实高度构成（见 MiniPlayer）：
    //   外层上下 padding 4×2 + 内层 7×2 + 内容 50 = 72dp
    // 再加 12dp 视觉呼吸 = 84dp。
    //
    // 无歌曲时没有悬浮岛，导航栏也已由 NavHost 处理，
    // 只需保留很小的收尾间距。
    return if (hasSong) 84.dp else 8.dp
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

    // ⚠️ 拆成两条细粒度流，而不是订阅完整 state。
    //
    // SongItem 会在**长列表里被实例化几十上百次**，
    // 若每个实例都订阅含 `position` 的完整状态，
    // 播放中就等于每秒把整个列表重组数遍 —— 这是列表页最贵的一处开销。
    //
    // 它实际只依赖两个低频值：当前歌曲 id 与是否正在播放。
    val currentSongId by PlaybackManager.currentSongIdFlow.collectAsState()
    val playing by PlaybackManager.isPlayingFlow.collectAsState()
    // 仅在「当前歌曲且正在播放」时显示跳动动效
    val isCurrent = currentSongId == song.id && playing

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
    var showQualityPicker by remember { mutableStateOf(false) }

    if (showQualityPicker) {
        AlertDialog(
            onDismissRequest = { showQualityPicker = false },
            title = { Text("选择下载音质") },
            text = {
                Column {
                    // 与播放器音质面板**同一口径**：官方展示的档位、
                    // 按高低排序、超出该曲目能力的置灰。
                    // （download 走同一套 level token，两边不一致会让用户困惑）
                    AudioQuality.visibleDescending.forEach { q ->
                        val available = AudioQuality.availableFor(q.level, song.maxBr)
                        val sizeText = estimateSizeMb(song.duration, q.kbps)
                            .let { if (it > 0) "约 %.1f MB".format(it) else "大小未知" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = available) {
                                    showQualityPicker = false
                                    // 交给全局控制器：进度在对话框与播放器面板间共享
                                    DownloadController.download(song, q.level, scope)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // 名称与规格都用官方文案
                                text = if (available) q.label else "${q.label}（本曲不支持）",
                                modifier = Modifier.weight(1f),
                                color = if (available) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
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
                // 下载进度：进行中显示进度条，结束后显示结果
                val dl by DownloadController.state.collectAsState()
                if (dl.songId == song.id && (dl.running || dl.finished)) {
                    Spacer(Modifier.height(10.dp))
                    if (dl.running) {
                        Text(
                            text = "下载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (dl.ratio >= 0f) {
                            LinearProgressIndicator(
                                progress = { dl.ratio },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${(dl.ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // 服务端未返回总长：用不确定进度，不显示假百分比
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    } else {
                        Text(
                            text = dl.message.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dl.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
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
                // 有下载任务在跑时禁用入口，避免重复发起
                val dlBusy by DownloadController.state.collectAsState()
                TextButton(
                    enabled = !dlBusy.running,
                    onClick = { showQualityPicker = true },
                ) { Text("下载") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

/** 可选下载音质：code / 名称 / 近似码率(kbps) */
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
        // M3 Expressive Loading indicator：
        // 官方 7 种 MaterialShapes 在弹簧驱动下依次变形，
        // 同时整体以 4666ms 匀速旋转 —— 两条通道刻意不同步。
        // 几何取自 AOSP，运动参数取自官方常量（详见该组件 KDoc）。
        ExpressiveLoadingIndicator(modifier = Modifier.size(48.dp))
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
    // 歌曲对象用细粒度流：它只在切歌时变化，与播放进度无关
    val song = PlaybackManager.currentSongFlow.collectAsState().value ?: return
    // 播放 / 加载这类低频开关同样用细粒度流，
    // 避免因订阅完整 state 而跟着 position 一起重组
    val playing by PlaybackManager.isPlayingFlow.collectAsState()
    val loading by PlaybackManager.isLoadingFlow.collectAsState()
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

    // 播放进度：供环绕封面的圆环使用。
    //
    // ⚠️ 这里**不在组合作用域解包** position ——
    // 那是每秒数次的组合期重组。改为在绘制 lambda 内读取
    // `PlaybackManager.state.value`，重组被替换为**仅重绘**，
    // 成本低一个数量级（与下面显隐动画的处理思路一致）。
    val progressProvider: () -> Float = {
        val snap = PlaybackManager.state.value
        if (snap.duration > 0) {
            (snap.position.toFloat() / snap.duration).coerceIn(0f, 1f)
        } else {
            0f
        }
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
                        // 直接传入"读快照"的 lambda：
                        // 进度在**绘制阶段**求值，不触发组合期重组
                        progress = progressProvider,
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
                    if (loading) {
                        // 加载态改用 Expressive 多边形指示器，
                        // 与下拉刷新、首页加载、搜索加载保持同一套视觉语言。
                        //
                        // 尺寸说明：多边形只占容器的 0.79（官方 ActiveIndicatorScale），
                        // 因此容器取 28dp 才能得到与原来 22dp 圆环相当的分量。
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放",
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
