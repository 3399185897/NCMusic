package com.buddy.ncmusic.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.SectionTitle

@Composable
fun LibraryScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val userState by vm.userState.collectAsState(initial = UserState())
    val playlists by vm.playlists.collectAsState()
    val history by vm.history.collectAsState()
    val loading by vm.loading.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建歌单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                    )
                    createError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isEmpty()) {
                        createError = "请输入歌单名称"
                    } else {
                        vm.createPlaylist(name) { err ->
                            if (err == null) {
                                showCreateDialog = false
                                newName = ""
                                createError = null
                            } else {
                                createError = err
                            }
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            },
        )
    }

    val likedPlaylist = playlists.firstOrNull { it.specialType == 5 }
    val createdPlaylists = playlists.filter { it.specialType != 5 && !it.subscribed }
    val subscribedPlaylists = playlists.filter { it.specialType != 5 && it.subscribed }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        // 头像与设置（顶部留白后整体下移）
        item {
            if (userState.isLoggedIn) {
                LoggedInHeader(userState = userState, onOpenSettings = onOpenSettings)
            } else {
                NotLoggedInHeader(onLogin = onOpenLogin, onOpenSettings = onOpenSettings)
            }
        }

        // 听歌统计
        item {
            var showStats by remember { mutableStateOf(false) }
            if (showStats) {
                ListenStatsDialog(state = userState, onDismiss = { showStats = false })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            ),
                        ),
                    )
                    .clickable { showStats = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "累计听歌时长",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatListenTime(userState.totalListenSeconds),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = "查看近七日 \u203a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // 块一：我喜欢的音乐（独立成块）
        likedPlaylist?.let { liked ->
            item {
                SectionTitle("我喜欢的音乐", modifier = Modifier.padding(top = 8.dp))
            }
            item {
                BlockCard {
                    PlaylistRow(playlist = liked, onClick = { onOpenPlaylist(liked.id) })
                }
            }
        }

        // 块二：最近播放（独立成块）
        if (userState.isLoggedIn && history.isNotEmpty()) {
            item {
                SectionTitle("最近播放", modifier = Modifier.padding(top = 8.dp))
            }
            item {
                BlockCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenHistory)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(
                            url = history.firstOrNull()?.coverUrl,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentDescription = "最近播放",
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("最近播放", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${history.size} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "\u203a",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 块二：创建的歌单
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("创建的歌单", modifier = Modifier.weight(1f))
                if (userState.isLoggedIn) {
                    IconButton(onClick = {
                        newName = ""
                        createError = null
                        showCreateDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新建歌单")
                    }
                }
            }
        }
        if (loading) {
            item { LoadingView(Modifier.fillMaxWidth().height(120.dp)) }
        } else if (createdPlaylists.isEmpty()) {
            item {
                Text(
                    text = if (userState.isLoggedIn) "暂无歌单" else "登录后同步你的歌单",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                BlockCard {
                    createdPlaylists.forEach { playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                    }
                }
            }
        }

        // 块三：收藏的歌单
        if (subscribedPlaylists.isNotEmpty()) {
            item {
                SectionTitle("收藏的歌单", modifier = Modifier.padding(top = 8.dp))
            }
            item {
                BlockCard {
                    subscribedPlaylists.forEach { playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                    }
                }
            }
        }

        // 块四：本地歌单（置于底部）
        item {
            SectionTitle("本地", modifier = Modifier.padding(top = 8.dp))
        }
        item {
            BlockCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLocal)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("本地歌单", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "播放设备中的本地音乐",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "\u203a",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

}

@Composable
private fun LoggedInHeader(userState: UserState, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (userState.avatarUrl.isNotBlank()) {
            CoverImage(
                url = userState.avatarUrl,
                modifier = Modifier.size(56.dp).clip(CircleShape),
                contentDescription = userState.nickname,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = userState.nickname,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
        }
    }
}

@Composable
private fun NotLoggedInHeader(onLogin: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "未登录",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onLogin) { Text("登录") }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = playlist.coverImgUrl,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            contentDescription = playlist.name,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatListenTime(sec: Long): String {
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    return if (hours > 0) "$hours 小时 $minutes 分钟" else "$minutes 分钟"
}

@Composable
private fun ListenStatsDialog(state: UserState, onDismiss: () -> Unit) {
    val days = remember(state.listenByDay) { buildLast7Days(state.listenByDay) }
    val maxSec = remember(days) { days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("近七日听歌时长") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    days.forEach { (label, sec) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(formatShortDuration(sec), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(((sec.toFloat() / maxSec) * 110f).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "累计：${formatListenTime(state.totalListenSeconds)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun buildLast7Days(map: Map<String, Long>): List<Pair<String, Long>> {
    val keyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val labelFmt = java.text.SimpleDateFormat("M/d", java.util.Locale.US)
    return (6 downTo 0).map { offset ->
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -offset)
        labelFmt.format(c.time) to (map[keyFmt.format(c.time)] ?: 0L)
    }
}

private fun formatShortDuration(sec: Long): String = when {
    sec >= 3600 -> "%.1fh".format(sec / 3600.0)
    sec >= 60 -> "${sec / 60}m"
    sec > 0 -> "${sec}s"
    else -> "—"
}


/** 我的页面中的分组卡片块 */
@Composable
private fun BlockCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 6.dp),
    ) {
        content()
    }
}
