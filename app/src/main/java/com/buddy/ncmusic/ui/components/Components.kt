package com.buddy.ncmusic.ui.components

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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .combinedClickable(onClick = onClick, onLongClick = { showDetail = true })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = song.coverUrl,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)),
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
        IconButton(onClick = { showDetail = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "歌曲信息",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(modifier = modifier.width(140.dp).clickable(onClick = onClick)) {
        CoverImage(
            url = playlist.coverImgUrl,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            contentDescription = playlist.name,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
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
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** 加载中 */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}

/** 底部迷你播放条（支持左右滑动切歌） */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val state by PlaybackManager.state.collectAsState()
    val song = state.song ?: return
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = dragOffset },
    ) {
        Column {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.duration > 0) {
                LinearProgressIndicator(
                    progress = { state.position.toFloat() / state.duration },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                // 直接切歌后再复位，避免协程竞争导致 next/previous 不执行
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
                                // 跟手位移，带阻尼（同步更新，不启动协程）
                                dragOffset = (dragOffset + amount * 0.5f)
                                    .coerceIn(-280f, 280f)
                            },
                        )
                    }
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    url = song.coverUrl,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
                    contentDescription = song.name,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = song.artistNames,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BouncyIconButton(onClick = { PlaybackManager.previous() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                BouncyIconButton(onClick = { PlaybackManager.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                BouncyIconButton(onClick = { PlaybackManager.next() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
