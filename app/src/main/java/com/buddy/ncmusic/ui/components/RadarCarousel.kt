package com.buddy.ncmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.ui.theme.AppShapes

/**
 * 首页雷达歌单：Material 3 居中 Hero Carousel。
 *
 * 主卡完整显示，两侧卡片由官方 Carousel 自动压缩、裁切；滑动吸附与
 * 尺寸插值全部交给 Material 3，不再手写 wheel/parallax。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarCarousel(
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) return

    val state = rememberCarouselState { playlists.size }
    val currentPage by remember(state) {
        derivedStateOf { state.currentItem.coerceIn(0, playlists.lastIndex) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalCenteredHeroCarousel(
            state = state,
            modifier = Modifier.fillMaxWidth().height(244.dp),
            itemSpacing = 12.dp,
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state),
            minSmallItemWidth = CarouselDefaults.MinSmallItemSize,
            maxSmallItemWidth = 72.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { index ->
            val playlist = playlists[index]
            Surface(
                onClick = { onOpenPlaylist(playlist.id) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(AppShapes.of(28.dp)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    CoverImage(
                        url = playlist.coverImgUrl,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentDescription = playlist.name,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            Text(
                                text = if (playlist.trackCount > 0) {
                                    "${playlist.trackCount} 首 · 官方雷达"
                                } else {
                                    "官方雷达 · 随账号推荐"
                                },
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }

        if (playlists.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "${currentPage + 1} / ${playlists.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
