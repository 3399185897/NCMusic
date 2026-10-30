package com.buddy.ncmusic.ui.screens.discover

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import com.buddy.ncmusic.ui.components.ExpressiveLoadingIndicator
import com.buddy.ncmusic.ui.components.rememberBottomSafeSpace
import com.buddy.ncmusic.ui.theme.AppShapes
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.cache.ContentCache
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
/**
 * 搜索失败提示。
 *
 * 与「没有找到相关歌曲」（正常空结果）区分开的必要性：
 * 两者对用户意味着**完全不同的下一步动作** ——
 * 前者应重试或检查网络，后者说明关键词本身没匹配到内容。
 */
@Composable
private fun SearchFailure(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (canRetry) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

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
    // 错误与"是否搜过"：用于把**网络失败**与**正常空结果**区分开。
    // 此前两者共用同一句"没有找到相关歌曲" —— 网络断了也这么提示，
    // 用户既不知道真正原因，也没有重试入口。
    val searchError by searchVm.error.collectAsState()
    val searchErrorKind by searchVm.errorKind.collectAsState()
    val searched by searchVm.searched.collectAsState()

    var ranks by remember { mutableStateOf<List<RankData>>(emptyList()) }
    // 榜单需并行请求 4 个歌单，加载期间原先是一片空白；补一个加载态
    var ranksLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (ContentCache.hasAllRankDetails(RANK_DEFS.map { it.first })) {
            ranks = RANK_DEFS.mapNotNull { (id, fallback) ->
                ContentCache.rankDetail(id)?.let { playlist ->
                    RankData(
                        id = id,
                        name = playlist.name.ifBlank { fallback },
                        songs = playlist.tracks.take(RANK_SONG_COUNT),
                    )
                }
            }
            ranksLoading = false
            return@LaunchedEffect
        }
        // 四个榜单并行请求，避免顺序加载造成的长时间等待
        ranks = coroutineScope {
            RANK_DEFS.map { (id, fallback) ->
                async {
                    when (val r = NCMusicApp.instance.musicRepository.playlistDetail(id)) {
                        is ApiResult.Success -> RankData(
                            id = id,
                            name = r.data.name.ifBlank { fallback },
                            songs = r.data.tracks.take(RANK_SONG_COUNT),
                        ).also { ContentCache.storeRankDetail(id, r.data) }
                        is ApiResult.Error -> null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        ranksLoading = false
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // ============================================================
    // 输入框自身的状态
    //
    // 用 TextFieldValue 而非 String：需要判断 `composition != null`
    // 才能区分「正在用拼音组词」与「已经上屏」—— 见下方 onValueChange。
    // ============================================================
    var field by remember {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    // 外部改动 query（点历史 / 热词）时同步到输入框。
    // 组词期间不覆盖，否则会打断输入法正在组合的拼音。
    LaunchedEffect(query) {
        if (field.composition == null && field.text != query) {
            field = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ⚠️ 输入框必须是**唯一且位置固定**的实例。
        //
        // 此前它分别出现在「空查询」与「有关键词」两个分支里，
        // 而分支条件正是 `query.isNotBlank()` ——
        // 用户敲下第一个字符，条件翻转，整个分支被替换，
        // 输入框随之**销毁重建**：焦点丢失、输入法组词中断。
        //
        // 症状与反馈完全吻合：
        //  · 拼音内联显示（组词文本写进输入框）→ 第一个字母就让
        //    query 非空 → 分支翻转 → 打不出完整的字
        //  · 候选词显示在输入法内 → 组词期不写回 query，
        //    能一次上屏；但空格确认后立刻切到结果分支，
        //    看起来像「空格后直接搜索」
        //
        // 现在输入框提到条件之外，切换内容时不再重建。
        SearchField(
            value = field,
            onValueChange = { nv ->
                field = nv
                // 组词中不触发搜索：等上屏后再请求，
                // 避免拿 "ni" / "nih" 这类半成品拼音去搜
                if (nv.composition == null && nv.text != query) {
                    searchVm.onQueryChange(nv.text)
                }
            },
            onSearch = { searchVm.searchNow(field.text) },
            onClear = {
                field = TextFieldValue("")
                searchVm.onQueryChange("")
            },
            onOpenIdentify = onOpenIdentify,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (query.isNotBlank()) {
            // ---------- 搜索结果 ----------
            //
            // 用 weight(1f) 而非 fillMaxSize()：
            // 在 Column 内，子项的 maxHeight 是**无界**的，
            // 而 fillMaxSize() 会把这个无界约束继续传给内部的 LazyColumn ——
            // 「可垂直滚动的组件被赋予了无限高约束」会直接抛异常。
            // weight(1f) 让本区占据输入框之外的剩余高度，约束是有界的。
            Column(Modifier.weight(1f)) {
                // 搜索结果属于“进行中的内容更新”，使用 MD3 的线性进度条；
                // 固定 4dp 占位，避免 searching 切换时列表整体上下跳动。
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (searching) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
                // 三级分支，括号结构与原 if/else 一致：
                //   失败（可重试） > 确实搜过但无结果 > 结果列表
                if (searchError != null && !searching) {
                    SearchFailure(
                        message = searchError!!,
                        // 网络类失败才给重试；解析错误重试通常无效
                        canRetry = searchErrorKind != ApiResult.ErrorKind.PARSE,
                        onRetry = { searchVm.searchNow(query) },
                    )
                } else if (searched && results.isEmpty() && !searching) {
                    Text(
                        text = "没有找到相关歌曲",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                            // 同上：外层已是有界约束，这里用 weight 占满剩余高度
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = rememberBottomSafeSpace()),
                        ) {
                        itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                            SongListItem(
                                song = song,
                                onClick = { onPlay(results, index) },
                                onArtistClick = onOpenArtist,
                            )
                        }
                    }
                }
            }
        } else {
            // ---------- 发现首页 ----------
            LazyColumn(
                // 同上：Column 的直接子项必须用 weight 拿到有界的高度约束
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = rememberBottomSafeSpace()),
            ) {
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
                        items(history, key = { it }) { keyword ->
                            InputChip(
                                selected = false,
                                onClick = { searchVm.searchNow(keyword) },
                                label = { Text(keyword) },
                                trailingIcon = {
                                    // ⚠️ 这里**不能**用 48dp。
                                    //
                                    // InputChip 的最小高度只有 32dp，把 48dp 的
                                    // 触摸目标塞进 trailingIcon 槽，整颗 chip 会被
                                    // 撑到 60dp 左右 —— 视觉上就是"搜索历史的框太大"。
                                    // 图标槽按 MD3 规范取 16dp 图标 + 24dp 命中区，
                                    // 与 chip 自身高度协调。
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable { searchVm.removeHistory(keyword) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "删除该记录",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
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
                if (ranksLoading) {
                    // 用与全局加载态一致的 M3 Expressive 指示器，
                    // 避免此处出现"空白等待"或退化为另一种风格的转圈
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveLoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                } else {
                    // 卡片宽度 = 屏宽 - 左16 - 右16 - 露出下一张的 32dp。
                    //
                    // 原先取 `screenWidth - 32.dp`（正好一屏），横向滚动时
                    // **看不到下一张的任何边缘**，用户不会意识到可以左右滑。
                    // 留 32dp 露出量后，既有"还有更多"的视觉暗示，
                    // 又保证卡内 10 首歌的信息密度不下降。
                    val cardWidth = (screenWidth - 64.dp).coerceAtLeast(240.dp)
                    val ranksListState = rememberLazyListState()

                    LazyRow(
                        state = ranksListState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        // 吸附翻页：松手后自动对齐到卡片边界，
                        // 避免停在"半张卡"的尴尬位置（原先无吸附，停止位置随意）
                        flingBehavior = rememberSnapFlingBehavior(ranksListState),
                    ) {
                        items(ranks, key = { it.id }) { rank ->
                            RankCard(
                                rank = rank,
                                pageWidth = cardWidth,
                                onPlay = onPlay,
                                onOpenPlaylist = onOpenPlaylist,
                            )
                        }
                    }

                    // 位置反馈：告诉用户当前在第几张 / 共几张
                    if (ranks.size > 1) {
                        val current by remember {
                            derivedStateOf {
                                (ranksListState.firstVisibleItemIndex + 1).coerceIn(1, ranks.size)
                            }
                        }
                        Text(
                            text = "$current / ${ranks.size}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, end = 20.dp),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(pageWidth)
            .padding(end = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                // 榜单名长短不一（"云音乐飙升榜" vs "云音乐中文说唱榜"），
                // 不限制行数会与右侧「查看更多」争夺宽度并挤压布局。
                // 限 1 行 + 省略号：始终单行显示，长名超出部分以 … 收尾。
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { onOpenPlaylist(rank.id) }) {
                Text("查看更多 ›", maxLines = 1)
            }
        }
        rank.songs.forEachIndexed { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.of(10.dp))
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
                    modifier = Modifier.size(40.dp).clip(AppShapes.of(8.dp)),
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
}

/** 搜索框：听歌识曲按钮内嵌右侧，搜索框整体居中占满宽度 */
@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onOpenIdentify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索歌曲") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Clear, contentDescription = "清空搜索")
                    }
                }
                IconButton(onClick = onOpenIdentify) {
                    Icon(Icons.Filled.Mic, contentDescription = "听歌识曲")
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
