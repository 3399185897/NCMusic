package com.buddy.ncmusic.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.buddy.ncmusic.ui.util.pressScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.ErrorView
import com.buddy.ncmusic.ui.components.ExpressiveLoadingIndicator
import com.buddy.ncmusic.ui.components.ListWithScrollIndicator
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.PagerDots
import com.buddy.ncmusic.ui.components.PlayingIndicator
import com.buddy.ncmusic.ui.components.RadarCarousel
import com.buddy.ncmusic.ui.components.SongDetailDialog
import com.buddy.ncmusic.ui.components.rememberBottomSafeSpace
import com.buddy.ncmusic.ui.theme.AppShapes
import java.util.Calendar
import java.util.Locale

/** 页面左右安全边距：页头、小节标题、卡片共用同一根基准线 */
private val PageMargin = 20.dp

/** 每日推荐每页歌曲数：4 首 × 58dp ≈ 232dp，一张卡能完整容纳而不吞掉下半屏 */
private const val SONGS_PER_PAGE = 4

/** 单行歌曲高度（封面 44 + 上下留白）。分页宽度固定，故高度必须固定。 */
private val SONG_ROW_HEIGHT = 58.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val dailySongs by vm.dailySongs.collectAsState()
    val radarPlaylists by vm.radarPlaylists.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val loading by vm.loading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val lastRefreshedAt by vm.lastRefreshedAt.collectAsState()
    val error by vm.error.collectAsState()

    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // 主页组件显隐（设置 → 外观 → 主页组件）
    val prefs by NCMusicApp.instance.userPreferences.userStateFlow
        .collectAsState(initial = null)
    val showRadar = prefs?.homeShowRadar ?: true
    val showRecommend = prefs?.homeShowRecommend ?: true

    // 下拉刷新状态：提到 when 外层，避免在 else 分支里再包一层作用域
    val ptrState = rememberPullToRefreshState()

    val hasDaily = dailySongs.isNotEmpty()
    val hasRadar = showRadar && radarPlaylists.isNotEmpty()
    val hasPlaylists = showRecommend && playlists.isNotEmpty()

    when {
        loading && playlists.isEmpty() && dailySongs.isEmpty() -> LoadingView(Modifier)
        error != null && playlists.isEmpty() && dailySongs.isEmpty() -> ErrorView(
            message = error.orEmpty(),
            onRetry = vm::load,
            modifier = Modifier,
        )
        else -> PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
            // 指示器换用 M3 Expressive 的 Loading indicator，
            // 与加载态共用同一套形状变形语言（几何取自官方 MaterialShapes）
            indicator = {
                PullToRefreshDefaults.IndicatorBox(
                    state = ptrState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    elevation = 2.dp,
                ) {
                    ExpressiveLoadingIndicator(modifier = Modifier.size(32.dp))
                }
            },
        ) {
            ListWithScrollIndicator(
                listState = listState,
                modifier = Modifier.fillMaxSize(),
                showIndicator = false,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // 底部安全区随悬浮岛显隐自适应（无岛时几乎贴底，不留大空白）
                    contentPadding = PaddingValues(bottom = rememberBottomSafeSpace()),
                ) {
                    // ---------- 页头（大标题 + 日期 + 刷新）----------
                    item(key = "header") {
                        HomeHeader(lastRefreshedAt = lastRefreshedAt)
                    }

                    // ---------- 每日推荐（单张整合卡）----------
                    if (hasDaily) {
                        item(key = "daily") {
                            DailyRecommendCard(
                                songs = dailySongs,
                                pageWidth = screenWidth - PageMargin * 2 - 20.dp,
                                onPlay = onPlay,
                                onOpenArtist = onOpenArtist,
                            )
                        }
                    }

                    // ---------- 雷达歌单（官方账号级推荐 · MD3 Hero Carousel）----------
                    if (hasRadar) {
                        item(key = "radar_header") {
                            HomeSectionHeader(
                                title = "雷达歌单",
                                subtitle = "官方根据你的口味生成",
                            )
                        }
                        item(key = "radar") {
                            RadarCarousel(
                                playlists = radarPlaylists,
                                onOpenPlaylist = onOpenPlaylist,
                            )
                        }
                    }

                    // ---------- 推荐歌单 ----------
                    if (hasPlaylists) {
                        item(key = "playlist_header") {
                            HomeSectionHeader(
                                title = "推荐歌单",
                                subtitle = "根据你常听的歌曲挑选",
                            )
                        }
                        item(key = "playlists") {
                            PlaylistGrid(
                                playlists = playlists,
                                onOpenPlaylist = onOpenPlaylist,
                            )
                        }
                    }

                    item(key = "tail") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * 页头：SukiSU 式「大标题 + 说明行 + 圆形动作」。
 *
 * 原先首页直接用第一个小节标题当页面上沿，既没有页面身份，
 * 也让第一个小节紧贴状态栏 —— 这是"不像原生应用"最主要的一处。
 */
@Composable
private fun HomeHeader(
    lastRefreshedAt: Long,
) {
    val dateLine = remember { todayLine() }
    // 刷新后 90 秒内显示「刚刚更新」，之后显示具体时刻。
    //
    // 雷达与推荐歌单在服务端是**按天生成**的，同一天内多次刷新内容不变；
    // 没有这条反馈，用户只能凭内容判断，结论必然是"刷新没反应"。
    // 「刚刚更新」需要自己走时：没有状态变化就不会重组，字幕会一直停在
    // 刚刷新那一刻。这里只在 90 秒窗口内每 5 秒推进一次，之后停止。
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastRefreshedAt) {
        if (lastRefreshedAt <= 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now - lastRefreshedAt > 100_000L) break
            kotlinx.coroutines.delay(5_000)
        }
    }
    val refreshHint = if (lastRefreshedAt <= 0L) {
        null
    } else {
        val elapsed = now - lastRefreshedAt
        if (elapsed < 90_000L) {
            "刚刚更新"
        } else {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastRefreshedAt }
            "%02d:%02d 更新".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "推荐",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (refreshHint == null) dateLine else "$dateLine · $refreshHint",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 小节标题：主色只用于强调，标题本身回归 onSurface，避免整页都是彩色大字 */
@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, top = 26.dp, bottom = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 每日推荐：整块合并为**一张** tonal 卡片。
 *
 * 重构要点：
 * 1. 原先 4 行各自一张 `surfaceContainerLow` 卡 → 4 个独立色块堆叠，
 *    视觉上像设置项列表，而不是"一份推荐"；
 * 2. 页头放在卡内，右侧给「播放」按钮，卡片自身成为一个完整的区块；
 * 3. 底部补分页指示器 —— 30 首分 8 页，此前完全没有滚动提示。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyRecommendCard(
    songs: List<Song>,
    pageWidth: Dp,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val pages = remember(songs) { songs.chunked(SONGS_PER_PAGE) }
    val pagerState = rememberLazyListState()
    val currentPage by remember(pagerState) {
        derivedStateOf { pagerState.firstVisibleItemIndex }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin),
        shape = AppShapes.of(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 12.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "每日推荐",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${songs.size} 首 · 每天 6:00 更新",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 用实色 primary 而非 FilledTonalButton：
                // tonal 变体在浅色下是 primaryContainer（本主题里是浅粉），
                // 与整卡的中性底色放一起像"另一个体系的按钮"。
                // 实色主按钮在同一张卡里只有一个，视觉上是明确的主动作。
                Button(
                    onClick = { onPlay(songs, 0) },
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("播放", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyRow(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 10.dp),
                // 分页必须吸附：此前自由滚动会停在两页中间，看起来像"列表错位"
                flingBehavior = rememberSnapFlingBehavior(pagerState),
            ) {
                itemsIndexed(pages, key = { _, page -> page.firstOrNull()?.id ?: -1L }) { pageIndex, page ->
                    Column(Modifier.width(pageWidth)) {
                        page.forEachIndexed { offset, song ->
                            DailySongRow(
                                song = song,
                                onClick = { onPlay(songs, pageIndex * SONGS_PER_PAGE + offset) },
                                onArtistClick = onOpenArtist,
                            )
                            if (offset != page.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                        // 不足 4 首时补空，保证每页高度一致（避免翻页时卡片抖动）
                        repeat(SONGS_PER_PAGE - page.size) {
                            Spacer(Modifier.height(SONG_ROW_HEIGHT))
                        }
                    }
                }
            }

            PagerDots(
                count = pages.size,
                current = currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 14.dp),
            )
        }
    }
}

/** 每日推荐里的歌曲行：无独立底色，靠细分割线分隔（卡内列表的标准做法） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailySongRow(
    song: Song,
    onClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
) {
    var showDetail by remember { mutableStateOf(false) }
    if (showDetail) SongDetailDialog(song = song, onDismiss = { showDetail = false })

    // 与 SongListItem 同样的细粒度订阅：只要「当前曲目 + 播放态」两个低频值
    val currentSongId by PlaybackManager.currentSongIdFlow.collectAsState()
    val playing by PlaybackManager.isPlayingFlow.collectAsState()
    val isCurrent = currentSongId == song.id && playing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.of(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDetail = true },
            )
            .heightIn(min = SONG_ROW_HEIGHT)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = song.coverUrl,
            modifier = Modifier
                .size(44.dp)
                .clip(AppShapes.of(12.dp)),
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
            Text(
                text = song.artistNames,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    song.artists.firstOrNull()?.id?.let(onArtistClick)
                },
            )
        }
        IconButton(
            onClick = { showDetail = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "歌曲信息",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 推荐歌单：3 列 × 2 行网格。
 *
 * 原先是一条横向 shelf（3 张卡 + 第 4 张被屏幕右缘裁掉）。问题有两个：
 * 1. 第 4 张永远只露半截，用户看到的永远是"显示不全"；
 * 2. shelf 是高 170dp 的横向滚动区，手指落在上面时纵向滚动会被吃掉，
 *    而它又正好贴着底栏 —— 观感上像"被底栏压住"。
 *
 * 改成固定网格后，6 张卡一次性完整呈现，不再有横向裁切，
 * 也让页面的纵向节奏与上面的雷达卡保持一致。
 */
@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    columns: Int = 3,
    maxRows: Int = 2,
) {
    val visible = playlists.take(columns * maxRows)
    if (visible.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        visible.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { playlist ->
                    PlaylistTile(
                        playlist = playlist,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
                // 末行不满时补占位，保证每列宽度一致（否则最后一行会被拉宽）
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // ⚠️ 不要对整列做 clip：圆角会把左下角「N 首」的第一个字切掉
    // （18dp 圆角在 ~100dp 宽的卡片上正好压到文字的左下笔画）。
    // 封面自己已有 16dp 圆角，点击水波纹用默认矩形即可。
    // 按下时整张卡轻微内缩：给"点到了"一个视觉确认，
    // 这类 100dp 左右的目标用缩放比涟漪更清晰（涟漪只在大圆角边缘露一角）。
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.pressScale(interaction).clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
    ) {
        CoverImage(
            url = playlist.coverImgUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AppShapes.of(16.dp)),
            contentDescription = playlist.name,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (playlist.trackCount > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${playlist.trackCount} 首",
                modifier = Modifier.padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 「11月13日 周五」；用 Calendar 而非 java.time，minSdk 24 无需脱糖 */
private fun todayLine(): String {
    val calendar = Calendar.getInstance()
    val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    val weekday = weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    return String.format(
        Locale.CHINA,
        "%d月%d日 %s",
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        weekday,
    )
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
            items(toplist, key = { it.id }) { rank ->
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
            modifier = Modifier.size(56.dp).clip(AppShapes.of(8.dp)),
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
