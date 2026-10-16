package com.buddy.ncmusic.ui.components

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.util.AudioQuality
import com.buddy.ncmusic.util.audioSpecText
import com.buddy.ncmusic.util.VipTier

/**
 * 「当前歌曲音质」面板内容。
 *
 * ## 设计目标
 *
 * 信息结构与网易云官方客户端的音质面板**逐项对齐**：
 * 中文名 + 英文名、会员徽标、描述文案、规格数值（采样率 / 位深 / 声道 / 码率）、
 * 当前项打勾。档次由高到低排列（最好的在最上面），与官方一致。
 *
 * ## 与官方的差异（有意保留）
 *
 * 视觉用 Material 3 主题色而非官方的黄色卡片 ——
 * 本项目整体是 MD3 风格，此处只对齐**内容**，不照搬配色。
 *
 * ## 不可用档位的处理
 *
 * 服务端给出了 `maxbr` 时，超出该曲目能力的档位**保留在列表中但置灰禁用**，
 * 而不是直接隐藏 —— 这样用户能看到完整的官方档位体系，
 * 又不会选中一个永远拿不到地址的档位（那表现为"选了没反应"）。
 * 环绕声 / 全景声类档位在歌曲对象里没有对应码率字段，无法判断，一律可用。
 */
@Composable
fun QualitySheetContent(
    song: Song?,
    currentLevel: String,
    onPick: (AudioQuality) -> Unit,
    /**
     * 封面动态取色。
     *
     * 传入后，选中态底色与勾选标记都跟随封面色调 ——
     * 与播放器的进度条、歌曲标题、播放大按钮保持同一色系。
     *
     * 之所以要改：面板原先用主题色 `primaryContainer`，
     * 而播放器其余部分都跟随封面取色 —— 打开面板时突然切到品牌红，
     * 视觉上"跳"了一下。为 `null` 时回退到主题色。
     */
    accentColor: androidx.compose.ui.graphics.Color? = null,
) {
    val maxBr = song?.maxBr ?: 0
    // 取色优先，未取到则回退主题色
    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        // ---------- 标题行 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "当前歌曲音质",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            // 官方此处有一个「?」说明入口；本地没有对应帮助页，
            // 用一句规格说明替代，避免放一个点了没反应的图标
            Text(
                text = song?.audioSpecHint().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---------- 档位列表（高 -> 低）----------
        AudioQuality.visibleDescending.forEach { q ->
            QualityRow(
                quality = q,
                selected = q.level == currentLevel,
                enabled = AudioQuality.availableFor(q.level, maxBr),
                accent = accent,
                onClick = { onPick(q) },
            )
        }
    }
}

/** 单个档位行 */
@Composable
private fun QualityRow(
    quality: AudioQuality,
    selected: Boolean,
    enabled: Boolean,
    /** 封面取色（已回退为具体颜色，不会是 null） */
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    // 未选中的可用项不加底色（靠留白分隔），选中项用**封面取色**淡染 ——
    // 原来是给所有项都铺一层容器色，再给选中项叠主题色，层次容易糊在一起
    val alpha = if (enabled) 1f else 0.38f
    val onContainer = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(AppShapes.of(16.dp))
            .background(
                if (selected) accent.copy(alpha = 0.16f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ---------- 短标识图标 ----------
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = quality.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                // 选中项让短标识也带上取色，进一步呼应封面
                color = if (selected) accent.copy(alpha = alpha)
                else onContainer.copy(alpha = alpha * 0.85f),
            )
        }

        Spacer(Modifier.width(14.dp))

        // ---------- 名称 + 描述 ----------
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quality.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = onContainer.copy(alpha = alpha),
                    maxLines = 1,
                )
                if (quality.englishName.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = quality.englishName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (quality.vip != VipTier.NONE) {
                    Spacer(Modifier.width(6.dp))
                    VipBadge(quality.vip, alpha)
                }
            }

            // 描述 + 规格（官方把规格接在描述之后，用逗号分隔）
            val sub = listOf(quality.description, quality.specText)
                .filter { it.isNotBlank() }
                .joinToString("，")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 该曲目拿不到该档时的说明 —— 避免用户以为是 App 出错
            if (!enabled) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "当前歌曲不提供此音质",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }

        // ---------- 选中标记 ----------
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    // 封面取色的明度不可控（浅色封面上取色可能接近白），
                    // 故按亮度自动选黑/白前景，保证勾选始终可见
                    tint = accent.contrastForeground(),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** VIP / SVIP 徽标 */
@Composable
private fun VipBadge(tier: VipTier, alpha: Float = 1f) {
    Box(
        modifier = Modifier
            // 用描边而非实心：实心徽标在彩色卡片上会与选中态的底色打架，
            // 描边既保留了"会员专属"的提示，又不抢主信息的对比度
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.5f),
                shape = AppShapes.of(6.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = tier.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 按背景色亮度选择可读的前景（黑或白）。
 *
 * 封面取色来自专辑图，亮度完全不可控 ——
 * 若固定用白色勾选，遇到浅色封面（取色接近白/黄）就会看不清。
 * 0.5 是常用的分界；用 luminance() 而非简单平均，符合感知亮度定义。
 */
private fun androidx.compose.ui.graphics.Color.contrastForeground():
    androidx.compose.ui.graphics.Color =
    if (luminance() > 0.5f) androidx.compose.ui.graphics.Color.Black
    else androidx.compose.ui.graphics.Color.White

/** 标题右侧的一句规格提示 */
private fun Song.audioSpecHint(): String =
    audioSpecText()?.let { "本曲最高 $it" }.orEmpty()
