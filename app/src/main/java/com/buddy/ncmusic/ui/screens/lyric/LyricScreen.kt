package com.buddy.ncmusic.ui.screens.lyric

import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.playback.PlaybackManager

@Composable
fun LyricScreen(onBack: () -> Unit) {
    val state by PlaybackManager.state.collectAsState()
    val prefs = NCMusicApp.instance.userPreferences
    val userState by prefs.userStateFlow.collectAsState(initial = UserState())
    val lyrics = state.lyrics
    val position = state.position
    val duration = state.duration
    val listState = rememberLazyListState()
    val currentIndex = remember(lyrics, position) {
        if (lyrics.isEmpty()) 0 else lyrics.indexOfLast { it.time <= position }.coerceAtLeast(0)
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex > 0) listState.animateScrollToItem(currentIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("歌词", style = MaterialTheme.typography.titleMedium)
        }

        // 歌词区：点击任意位置返回专辑封面页
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onBack),
        ) {
            if (lyrics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无歌词（点击返回）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 120.dp),
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isCurrent = index == currentIndex
                        Text(
                            text = line.text,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            style = if (isCurrent) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) {
                                val custom = userState.lyricColor.toInt()
                                if (custom != 0 && custom != -1) Color(custom)
                                else MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            },
                        )
                    }
                }
            }
        }

        // 底部进度条：可拖动调整播放进度
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Slider(
                value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { PlaybackManager.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(formatTime(position), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
