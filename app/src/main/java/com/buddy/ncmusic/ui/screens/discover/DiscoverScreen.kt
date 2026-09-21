package com.buddy.ncmusic.ui.screens.discover

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.Mic
import com.buddy.ncmusic.ui.components.SectionTitle
import com.buddy.ncmusic.ui.components.SongListItem
import com.buddy.ncmusic.ui.screens.search.SearchViewModel
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 榜单定义：仅保留官方四大主榜 */
private val RANK_DEFS = listOf(
    19723756L to "飙升榜",
    3778678L to "热歌榜",
    2884035L to "原创榜",
    3779629L to "新歌榜",
)

/** 每个榜单外显的歌曲数（减少以加快加载） */
private const val RANK_SONG_COUNT = 6

private data class RankData(val id: Long, val name: String, val songs: List<Song>)

/**
 * 「发现」页：搜索 + 热搜 + 分类排行榜。
 * 搜索框内嵌听歌识曲；排行榜横向翻页，每榜单外显前 10 首。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenIdentify: () -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    searchVm: SearchViewModel = viewModel(),
) {
    val query by searchVm.query.collectAsState()
    val hotWords by searchVm.hotWords.collectAsState()
    val history by searchVm.history.collectAsState()
    val results by searchVm.results.collectAsState()
    val searching by searchVm.searching.collectAsState()

    var ranks by remember { mutableStateOf<List<RankData>>(emptyList()) }
    LaunchedEffect(Unit) {
        // 四个榜单并行请求，避免顺序加载造成的长时间等待
        ranks = coroutineScope {
            RANK_DEFS.map { (id, fallback) ->
                async {
                    when (val r = NCMusicApp.instance.musicRepository.playlistDetail(id)) {
                        is ApiResult.Success -> RankData(
                            id = id,
                            name = r.data.name.ifBlank { fallback },
                            songs = r.data.tracks.take(RANK_SONG_COUNT),
                        )
                        is ApiResult.Error -> null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    if (query.isNotBlank()) {
        Column(Modifier.fillMaxSize()) {
            SearchField(
                query = query,
                onQueryChange = searchVm::onQueryChange,
                onOpenIdentify = onOpenIdentify,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (results.isEmpty() && !searching) {
                Text(
                    text = "没有找到相关歌曲",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(results) { song ->
                        SongListItem(
                            song = song,
                            onClick = { onPlay(results, results.indexOf(song)) },
                            onArtistClick = onOpenArtist,
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SearchField(
                query = query,
                onQueryChange = searchVm::onQueryChange,
                onOpenIdentify = onOpenIdentify,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // 搜索历史（可逐条删除 / 一键清空）
        if (history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("搜索历史", modifier = Modifier.weight(1f))
                    TextButton(onClick = { searchVm.clearHistory() }) { Text("清空") }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history) { keyword ->
                        InputChip(
                            selected = false,
                            onClick = { searchVm.searchNow(keyword) },
                            label = { Text(keyword) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除该记录",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { searchVm.removeHistory(keyword) },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (hotWords.isNotEmpty()) {
            item { SectionTitle("热门搜索") }
            item {
                // 自然换行排列，间隔一致（不再按列分组，顺序即数据顺序）
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    hotWords.take(12).forEach { word ->
                        SuggestionChip(
                            onClick = { searchVm.searchNow(word) },
                            label = { Text(word) },
                        )
                    }
                }
            }
        }

        item { SectionTitle("排行榜") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(ranks) { rank ->
                    RankCard(
                        rank = rank,
                        pageWidth = screenWidth - 32.dp,
                        onPlay = onPlay,
                        onOpenPlaylist = onOpenPlaylist,
                    )
                }
            }
        }
    }
}

/** 单个榜单：标题 + 查看更多 + 前 10 首 */
@Composable
private fun RankCard(
    rank: RankData,
    pageWidth: Dp,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(pageWidth)
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { onOpenPlaylist(rank.id) }) { Text("查看更多 ›") }
        }
        rank.songs.forEachIndexed { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPlay(rank.songs, index) }
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (index < 3) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CoverImage(
                    url = song.coverUrl,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    contentDescription = song.name,
                )
                Spacer(Modifier.width(10.dp))
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
            }
        }
    }
}

/** 搜索框：听歌识曲按钮内嵌右侧，搜索框整体居中占满宽度 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenIdentify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索歌曲") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onOpenIdentify) {
                Icon(Icons.Filled.Mic, contentDescription = "听歌识曲")
            }
        },
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
    )
}
