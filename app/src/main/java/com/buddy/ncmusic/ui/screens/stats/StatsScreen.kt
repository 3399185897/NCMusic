package com.buddy.ncmusic.ui.screens.stats

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.ui.components.ListWithScrollIndicator
import com.buddy.ncmusic.ui.theme.AppShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 听歌统计页。
 *
 * 布局结构（参考 PixelPlayer 的 Stats 设计）：
 * 1. **Hero 区** —— 两张并排概览卡（累计时长 / 听歌天数）
 * 2. **趋势卡** —— 近 7 天柱状图，带高度增长动画
 * 3. **对比卡** —— 本周 vs 上周，含升降百分比
 * 4. **纪录卡** —— 单日最高与日均
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val prefs = NCMusicApp.instance.userPreferences
    val userState by prefs.userStateFlow.collectAsState(initial = UserState())

    val totalSeconds = userState.totalListenSeconds
    val byDay = userState.listenByDay

    val last7 = remember(byDay) { buildLastDays(byDay, 7) }
    val maxOfWeek = remember(last7) { last7.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L }

    // 派生指标
    val activeDays = remember(byDay) { byDay.count { it.value > 0 } }
    val avgPerDay = if (activeDays > 0) totalSeconds / activeDays else 0L
    val bestDay = remember(byDay) { byDay.maxByOrNull { it.value } }

    // 本周（近 7 天）与上周（前 7 天）对比
    val thisWeek = remember(byDay) { sumRange(byDay, 0, 7) }
    val lastWeek = remember(byDay) { sumRange(byDay, 7, 14) }
    val weekDelta = when {
        lastWeek <= 0L && thisWeek > 0L -> 100f
        lastWeek <= 0L -> 0f
        else -> ((thisWeek - lastWeek).toFloat() / lastWeek * 100f)
    }

    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("听歌统计", style = MaterialTheme.typography.titleMedium)
        }

        ListWithScrollIndicator(
                listState = listState,
                modifier = Modifier.fillMaxSize(),
                showIndicator = false,
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ---------- Hero 区 ----------
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        // 两卡等高：左侧值为「累计听歌时长」，文案长度会随数据增长
                        // （如 "45 分钟" → "128 小时 30 分"），若不锁定高度，
                        // 文本折行会让这张卡比右侧「听歌天数」高出一截 —— 表现为「组件大小不一」
                        modifier = Modifier.height(IntrinsicSize.Min),
                    ) {
                        HeroTile(
                            modifier = Modifier.weight(1f),
                            label = "累计听歌",
                            value = formatDuration(totalSeconds),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        HeroTile(
                            modifier = Modifier.weight(1f),
                            label = "听歌天数",
                            value = "${activeDays} 天",
                            // 用 secondary：tertiary 在动态取色下易与背景接近，导致对比不足
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // ---------- 近 7 天趋势 ----------
                item {
                    SectionCard("近 7 天") {
                        if (totalSeconds <= 0L) {
                            EmptyHint()
                        } else {
                            WeeklyBars(days = last7, maxSeconds = maxOfWeek)
                        }
                    }
                }

                // ---------- 周对比 ----------
                item {
                    SectionCard("本周对比") {
                        if (totalSeconds <= 0L) {
                            EmptyHint()
                        } else {
                            WeekCompare(
                                thisWeek = thisWeek,
                                lastWeek = lastWeek,
                                deltaPercent = weekDelta,
                            )
                        }
                    }
                }

                // ---------- 纪录 ----------
                item {
                    SectionCard("纪录") {
                        StatLine(
                            label = "日均时长",
                            value = formatDuration(avgPerDay),
                        )
                        Spacer(Modifier.height(10.dp))
                        StatLine(
                            label = "单日最高",
                            value = bestDay?.let { formatDuration(it.value) } ?: "--",
                            subtitle = bestDay?.key,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 组件 ----------------

@Composable
private fun HeroTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.of(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.20f),
                        accent.copy(alpha = 0.07f),
                    ),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            // 单行 + 不折行：确保任何长度的时长文案都不会撑高卡片
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.of(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun WeeklyBars(
    days: List<Pair<String, Long>>,
    maxSeconds: Long,
) {
    // 统一的加载动画：所有柱子一起长出来
    val grow by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "barsGrow",
    )

    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        days.forEach { (label, sec) ->
            val ratio = (sec.toFloat() / maxSeconds).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = if (sec > 0) formatShort(sec) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height((96f * ratio * grow).dp.coerceAtLeast(if (sec > 0) 5.dp else 2.dp))
                        .clip(AppShapes.of(8.dp))
                        .background(
                            if (sec > 0) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                    ),
                                )
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekCompare(
    thisWeek: Long,
    lastWeek: Long,
    deltaPercent: Float,
) {
    val baseline = maxOf(thisWeek, lastWeek, 1L)
    val up = deltaPercent >= 0f

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(thisWeek),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (up) "↑ ${"%.0f".format(deltaPercent)}%" else "↓ ${"%.0f".format(-deltaPercent)}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (up) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "上周 ${formatDuration(lastWeek)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        // 本周占比条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(AppShapes.of(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((thisWeek.toFloat() / baseline).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(AppShapes.of(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyHint() {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "还没有听歌记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------- 数据工具 ----------------

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 取最近 n 天（含今天），返回 (星期标签, 秒数)，按时间正序 */
private fun buildLastDays(byDay: Map<String, Long>, n: Int): List<Pair<String, Long>> {
    val today = LocalDate.now()
    return (n - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val key = date.format(DATE_FMT)
        val label = DAY_LABELS[(date.dayOfWeek.value - 1).coerceIn(0, 6)]
        label to (byDay[key] ?: 0L)
    }
}

/** 汇总 [fromIdx, toIdx) 天区间（0 = 今天往前数）的秒数 */
private fun sumRange(byDay: Map<String, Long>, fromIdx: Int, toIdx: Int): Long {
    val today = LocalDate.now()
    var sum = 0L
    for (i in fromIdx until toIdx) {
        val key = today.minusDays(i.toLong()).format(DATE_FMT)
        sum += byDay[key] ?: 0L
    }
    return sum
}

/** 0 秒显示为 0 分钟，避免出现"0 小时 0 分" */
private fun formatDuration(sec: Long): String {
    if (sec <= 0L) return "0 分钟"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return when {
        // 三位数小时时省略分钟，避免文案过长挤压卡片
        h >= 100 -> "$h 小时"
        h > 0 && m > 0 -> "$h 小时 $m 分"
        h > 0 -> "$h 小时"
        else -> "${m.coerceAtLeast(1)} 分钟"
    }
}

private fun formatShort(sec: Long): String = when {
    sec >= 3600 -> "%.1fh".format(sec / 3600f)
    sec >= 60 -> "${sec / 60}m"
    else -> "${sec}s"
}
