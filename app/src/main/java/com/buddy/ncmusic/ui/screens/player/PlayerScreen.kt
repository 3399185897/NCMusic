package com.buddy.ncmusic.ui.screens.player

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

    val coverColor = rememberCoverColor(song?.coverUrl)
    val currentLyricIndex = remember(state.lyrics, state.position) {
        state.lyrics.indexOfLast { it.time <= state.position }.coerceAtLeast(0)
    }
    val currentLine = state.lyrics.getOrNull(currentLyricIndex)?.text.orEmpty()
    val nextLine = state.lyrics.getOrNull(currentLyricIndex + 1)?.text.orEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        (coverColor ?: MaterialTheme.colorScheme.surface).copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
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
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onOpenLyric),
                    contentDescription = song.name,
                )
                Spacer(Modifier.height(24.dp))
                // 歌词区固定高度，避免切换音质时布局抖动
                Column(
                    modifier = Modifier.height(46.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (currentLine.isNotEmpty()) {
                        Text(
                            text = currentLine,
                            style = MaterialTheme.typography.titleSmall,
                            color = coverColor ?: MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = nextLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                Slider(
                    value = state.position.toFloat().coerceIn(0f, state.duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { PlaybackManager.seekTo(it.toLong()) },
                    valueRange = 0f..state.duration.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = coverColor ?: MaterialTheme.colorScheme.primary,
                        activeTrackColor = coverColor ?: MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(formatTime(state.position), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(state.duration), style = MaterialTheme.typography.bodySmall)
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
                        BouncyIconButton(onClick = { PlaybackManager.previous() }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FilledIconButton(onClick = { PlaybackManager.togglePlayPause() }) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BouncyIconButton(onClick = { PlaybackManager.next() }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BouncyIconButton(onClick = { PlaybackManager.toggleLike() }) {
                            Icon(
                                imageVector = if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "喜欢",
                                tint = if (state.isLiked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 从封面图片提取主色，作为歌词高亮/强调色 */
@Composable
private fun rememberCoverColor(url: String?): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        color = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).build(),
                ).drawable?.toBitmap()
                bitmap?.let {
                    val p = Palette.from(it).generate()
                    (p.vibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: p.mutedSwatch?.rgb)
                }
            }.getOrNull()?.let { Color(it) }
        }
    }
    return color
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
