package com.buddy.ncmusic.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.ErrorView
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.PlaylistCard
import com.buddy.ncmusic.ui.components.SectionTitle
import com.buddy.ncmusic.ui.components.SongListItem

/** 单行歌曲项高度：封面 50 + 内边距 20 + 外层间隔 8 */
private val SONG_ROW_HEIGHT = 78.dp

@Composable
fun HomeScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val dailySongs by vm.dailySongs.collectAsState()
    val radarSongs by vm.radarSongs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    when {
        loading && playlists.isEmpty() && dailySongs.isEmpty() -> LoadingView(Modifier)
        error != null && playlists.isEmpty() -> ErrorView(
            message = error.orEmpty(),
            onRetry = vm::load,
            modifier = Modifier,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (dailySongs.isNotEmpty()) {
                item { SectionTitle("每日推荐") }
                item { SongPager(songs = dailySongs, pageWidth = screenWidth - 32.dp, onPlay = onPlay, onOpenArtist = onOpenArtist) }
            }

            if (radarSongs.isNotEmpty()) {
                item { SectionTitle("雷达推荐") }
                item { SongPager(songs = radarSongs, pageWidth = screenWidth - 32.dp, onPlay = onPlay, onOpenArtist = onOpenArtist) }
            }

            item { SectionTitle("推荐歌单") }
            item {
                // 两行横向滚动歌单
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier.height(400.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 横向分页歌曲列表：一页 4 首，左右滑动翻页 */
@Composable
private fun SongPager(
    songs: List<Song>,
    pageWidth: androidx.compose.ui.unit.Dp,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(songs.chunked(4)) { page ->
            Column(
                modifier = Modifier
                    .width(pageWidth)
                    .height(SONG_ROW_HEIGHT * 4),
            ) {
                page.forEach { song ->
                    SongListItem(
                        song = song,
                        onClick = { onPlay(songs, songs.indexOf(song)) },
                        onArtistClick = onOpenArtist,
                    )
                }
            }
        }
    }
}

@Composable
fun RankScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val toplist by vm.toplist.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    when {
        loading && toplist.isEmpty() -> LoadingView(Modifier)
        error != null && toplist.isEmpty() -> ErrorView(
            message = error.orEmpty(),
            onRetry = vm::load,
            modifier = Modifier,
        )
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(toplist) { rank ->
                RankItem(rank = rank, onClick = { onOpenPlaylist(rank.id) })
            }
        }
    }
}

@Composable
private fun RankItem(rank: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CoverImage(
            url = rank.coverImgUrl,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            contentDescription = rank.name,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = rank.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (rank.playCount > 0) {
                Text(
                    text = formatPlayCount(rank.playCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatPlayCount(count: Double): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> count.toLong().toString()
}
