package com.buddy.ncmusic.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.ui.theme.AppShapes

/**
 * 首页雷达歌单：居中大卡 + 两侧露出邻卡。
 *
 * ## 为什么不用 `HorizontalCenteredHeroCarousel`
 *
 * M3 的居中 Hero 卡片把「首卡居中」交给内部 keyline 计算，而 keyline 的槽位宽度
 * 又由 `contentPadding` 反推 —— 两者互相依赖，导致首卡实际落点无法用公开参数
 * 精确控制。实测同一个 padding 下卡片始终比屏幕中心偏左 20~60dp，
 * 而下方分页点仍在正中，错位肉眼可见；调 padding 只是在逼近，跨屏幕尺寸又会失准。
 *
 * 这里改为受控 `LazyRow`：
 * - 卡片宽度固定 → 高度固定（正方形封面 + 文字行），不再随容器插值；
 * - `contentPadding = (屏宽 − 卡宽)/2` → 首卡**天然居中**，吸附后每张都居中；
 * - 邻卡按屏幕边缘裁切，形成稳定的「左右各露一截」。
 *
 * 视觉参数按 SukiSU 的卡片语言取值：28dp 圆角、`surfaceContainer` 底色、
 * 封面 18dp 内圆角、文字不与封面重叠。
 */
@Composable
fun RadarCarousel(
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) return

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // 0.585 ≈ 393dp 屏上的 230dp：一屏只放得下一张主卡，两侧各露 ~64dp 邻卡
    val cardWidth = (screenWidth * 0.585f).coerceIn(210.dp, 300.dp)
    val coverSide = cardWidth - 20.dp
    val sidePadding = (screenWidth - cardWidth) / 2

    val count = playlists.size
    // 无限循环：把数据源按取模"铺满"一个极大区间，从区间中点起步，
    // 于是左右都能一直滑下去，不会在首/尾卡住。
    // 不用真实首尾相接的 hack（那样要么复制首尾卡，要么在手势里改索引，
    // 都会破坏吸附与滚动惯性的连续性）。
    val loopCount = if (count > 1) Int.MAX_VALUE else 1
    val startIndex = remember(count) {
        if (count <= 1) {
            0
        } else {
            val middle = Int.MAX_VALUE / 2
            middle - middle % count
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val currentPage by remember(listState, count) {
        derivedStateOf {
            if (count <= 1) 0 else listState.firstVisibleItemIndex % count
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            items(loopCount) { index ->
                val playlist = playlists[index % count]
                Surface(
                    onClick = { onOpenPlaylist(playlist.id) },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .width(cardWidth)
                        .clip(AppShapes.of(26.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CoverImage(
                            url = playlist.coverImgUrl,
                            modifier = Modifier
                                .size(coverSide)
                                .clip(AppShapes.of(18.dp)),
                            contentDescription = playlist.name,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            // 无底色方向指示：只表达"可进入"，不与封面抢视觉
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        PagerDots(
            count = playlists.size,
            current = currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp),
        )
    }
}
