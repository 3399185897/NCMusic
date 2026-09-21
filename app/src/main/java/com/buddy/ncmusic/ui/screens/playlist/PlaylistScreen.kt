package com.buddy.ncmusic.ui.screens.playlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.ErrorView
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.SongListItem

@Composable
fun PlaylistScreen(
    playlistId: Long,
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    vm: PlaylistViewModel = viewModel(),
) {
    val playlist by vm.playlist.collectAsState()
    val songs by vm.songs.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = playlist?.name ?: "歌单",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val scope = rememberCoroutineScope()
            var subscribed by remember(playlist?.id) { mutableStateOf(playlist?.subscribed == true) }
            IconButton(onClick = {
                val p = playlist ?: return@IconButton
                val next = !subscribed
                subscribed = next
                scope.launch {
                    when (NCMusicApp.instance.musicRepository.subscribePlaylist(p.id, next)) {
                        is ApiResult.Error -> subscribed = !next
                        else -> Unit
                    }
                }
            }) {
                Icon(
                    imageVector = if (subscribed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (subscribed) "取消收藏" else "收藏歌单",
                    tint = if (subscribed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            loading -> LoadingView()
            error != null -> ErrorView(message = error.orEmpty(), onRetry = vm::load)
            else -> {
                var searchOpen by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }
                val filtered = remember(songs, searchQuery) {
                    if (searchQuery.isBlank()) {
                        songs
                    } else {
                        songs.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                it.artistNames.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        PlaylistHeader(
                            playlist = playlist,
                            songCount = songs.size,
                            onPlayAll = { onPlay(songs, 0) },
                            searchOpen = searchOpen,
                            onToggleSearch = {
                                searchOpen = !searchOpen
                                if (!searchOpen) searchQuery = ""
                            },
                        )
                    }
                    if (searchOpen) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                placeholder = { Text("搜索本歌单（歌名 / 歌手）") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                            )
                        }
                    }
                    if (searchOpen && filtered.isEmpty()) {
                        item {
                            Text(
                                text = "未找到匹配的歌曲",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(filtered) { song ->
                        SongListItem(
                            song = song,
                            onClick = { onPlay(filtered, filtered.indexOf(song)) },
                            onArtistClick = onOpenArtist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: com.buddy.ncmusic.data.model.Playlist?,
    songCount: Int,
    onPlayAll: () -> Unit,
    searchOpen: Boolean = false,
    onToggleSearch: () -> Unit = {},
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(
            url = playlist?.coverImgUrl,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            contentDescription = playlist?.name,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            playlist?.creator?.nickname?.let { creator ->
                Text(
                    text = creator,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$songCount 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlayAll, enabled = songCount > 0) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("播放全部")
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (searchOpen) "关闭搜索" else "搜索本页",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
