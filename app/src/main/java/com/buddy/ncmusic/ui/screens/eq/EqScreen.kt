package com.buddy.ncmusic.ui.screens.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.playback.AudioEffects
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** 曲线上的可调频率点（对数分布，覆盖 31Hz ~ 16kHz），比设备频段更多、调节更细 */
private val DISPLAY_FREQS = listOf(
    31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f,
)

/**
 * 独立均衡器页面：可视化频响曲线，直接在曲线上拖动调节各频段增益。
 */
@Composable
fun EqScreen(onBack: () -> Unit) {
    val prefs = NCMusicApp.instance.userPreferences
    val state by prefs.userStateFlow.collectAsState(initial = UserState())
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }

    // 进入页面先挂载（未播放时挂全局会话，播放后会自动切换到播放器会话）
    LaunchedEffect(Unit) {
        AudioEffects.attachGlobal()
        tick++
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("均衡器", style = MaterialTheme.typography.titleMedium)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用均衡器", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "关闭时不做任何音效处理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.eqEnabled,
                    onCheckedChange = { v ->
                        scope.launch { prefs.setEqEnabled(v) }
                        AudioEffects.setEqEnabled(v)
                        tick++
                    },
                )
            }

            val bandCount = remember(tick, state.eqEnabled) { AudioEffects.bandCount() }

            if (bandCount <= 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "请先播放一首歌以激活均衡器\n（部分设备需在播放中才能调节）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedButton(onClick = {
                        AudioEffects.attachGlobal()
                        tick++
                    }) { Text("重新检测") }
                }
                return@Column
            }

            val range = remember(tick) { AudioEffects.levelRange() }
            val minL = range.first.toFloat()
            val maxL = range.second.toFloat()
            val span = (maxL - minL).coerceAtLeast(1f)
            // 设备真实频段（用于映射）
            val bandFreqs = remember(tick) {
                List(bandCount) { AudioEffects.bandFreq(it).coerceAtLeast(1).toFloat() }
            }
            // 曲线显示点（更多、可拖动）
            val freqs = DISPLAY_FREQS
            val levels = remember(tick) {
                mutableStateListOf(
                    *Array(freqs.size) { i ->
                        val nearest = bandFreqs.indices.minByOrNull {
                            abs(ln(bandFreqs[it]) - ln(freqs[i]))
                        } ?: 0
                        AudioEffects.bandLevel(nearest).toFloat()
                    },
                )
            }
            // 显示点 -> 最近的设备频段
            fun bandIndexFor(displayIndex: Int): Int =
                bandFreqs.indices.minByOrNull {
                    abs(ln(bandFreqs[it]) - ln(freqs[displayIndex]))
                } ?: 0

            val logMin = remember(freqs) { ln(freqs.min().toDouble()).toFloat() }
            val logSpan = remember(freqs) {
                (ln(freqs.max().toDouble()).toFloat() - logMin).coerceAtLeast(0.001f)
            }

            val primary = MaterialTheme.colorScheme.primary
            val grid = MaterialTheme.colorScheme.outlineVariant
            val surface = MaterialTheme.colorScheme.surface

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .pointerInput(tick, bandCount) {
                        fun handle(px: Float, py: Float) {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val freqAt = exp((logMin + (px / w) * logSpan).toDouble()).toFloat()
                            val idx = freqs.indices.minByOrNull {
                                abs(ln(freqs[it]) - ln(freqAt.coerceAtLeast(1f)))
                            } ?: return
                            val lv = (maxL - (py / h) * span).coerceIn(minL, maxL)
                            levels[idx] = lv
                            // 映射到最近的硬件频段
                            AudioEffects.setBandLevel(
                                bandIndexFor(idx),
                                lv.toInt().toShort(),
                            )
                        }
                        detectDragGestures(
                            onDragStart = { offset -> handle(offset.x, offset.y) },
                            onDrag = { change, _ ->
                                handle(change.position.x, change.position.y)
                                change.consume()
                            },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                for (i in 0..4) {
                    val y = h * i / 4f
                    drawLine(
                        color = grid.copy(alpha = if (i == 2) 0.9f else 0.35f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = if (i == 2) 3f else 1f,
                    )
                }
                val points = freqs.indices.map { i ->
                    Offset(
                        x = ((ln(freqs[i]) - logMin) / logSpan) * w,
                        y = ((maxL - levels[i]) / span) * h,
                    )
                }
                val path = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                }
                drawPath(
                    path = path,
                    color = primary,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                points.forEach { p ->
                    drawCircle(color = primary, radius = 13f, center = p)
                    drawCircle(color = surface, radius = 6f, center = p)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                freqs.forEach { f ->
                    Text(
                        text = formatFreq(f.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "在曲线上拖动控制点即可调节对应频段增益",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = {
                    AudioEffects.reset()
                    tick++
                }) { Text("重置均衡器（10 段可调）") }
            }
            Spacer(Modifier.size(32.dp))
        }
    }
}

private fun formatFreq(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else hz.toString()
