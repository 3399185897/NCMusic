package com.buddy.ncmusic.util

import com.buddy.ncmusic.data.model.Song

/**
 * 音质档位。
 *
 * 网易云共 6 档，按码率由低到高排列。这里是**唯一**的档位定义处 ——
 * 播放器、下载器、设置页均以它为准，避免各处各写一份列表而出现不一致。
 */
enum class AudioQuality(
    val level: String,
    val label: String,
    /** 该档位的目标码率（kbps），用于估算文件大小 */
    val kbps: Int,
    /** 码率下限（bps）。服务端 maxbr 达到该值即认为支持此档 */
    val minBr: Int,
    /** 采样率描述，用于显示音频规格 */
    val sampleRateHz: Int,
    /** 位深（bit），用于显示音频规格 */
    val bitDepth: Int,
) {
    STANDARD("standard", "标准", 128, 0, 44_100, 16),
    HIGHER("higher", "较高", 192, 192_000, 44_100, 16),
    EXHIGH("exhigh", "极高", 320, 320_000, 44_100, 16),
    LOSSLESS("lossless", "无损", 900, 900_000, 44_100, 16),
    HIRES("hires", "Hi-Res", 1500, 1_500_000, 96_000, 24),
    JYMASTER("jymaster", "超清母带", 2000, 1_900_000, 192_000, 24);

    companion object {
        /** 全部档位，由低到高 */
        val all: List<AudioQuality> = entries.toList()

        /** 由 level 字符串查档位 */
        fun of(level: String): AudioQuality? = entries.firstOrNull { it.level == level }

        /**
         * 某曲目**实际支持**的档位列表。
         *
         * @param maxBr 服务端给出的最高码率；`0` 表示未知 ——
         *   此时**不限制**，返回全部档位（宁可多列，也好过把能播的档位藏起来）。
         */
        fun supportedBy(maxBr: Int): List<AudioQuality> =
            if (maxBr <= 0) all else all.filter { it.minBr <= maxBr }

        /** 某曲目支持的最高档位；未知时返回 null（表示不限制） */
        fun highestOf(maxBr: Int): AudioQuality? =
            if (maxBr <= 0) null else all.lastOrNull { it.minBr <= maxBr }

        /** 把 level 收敛到 [maxBr] 允许的最高档 */
        fun clamp(level: String, maxBr: Int): String {
            if (maxBr <= 0) return level
            val allowed = supportedBy(maxBr)
            val wanted = of(level) ?: return level
            if (wanted in allowed) return level
            return allowed.lastOrNull()?.level ?: "standard"
        }
    }
}

/** 供 UI 显示：`44.1 kHz / 16 bit` 形式的音频规格文本 */
fun Song.audioSpecText(): String? {
    // 服务端未给出码率时无从判断，不显示（避免显示错误信息）
    if (maxBr <= 0) return null
    // 按**实际可得的最高档位**描述规格，而不是用户选择的档位 ——
    // 后者可能因账号权限拿不到（那时显示的规格与实听不符）
    val q = AudioQuality.highestOf(maxBr) ?: return null
    return "${formatSampleRate(q.sampleRateHz)} / ${q.bitDepth} bit"
}

/** 44100 -> `44.1 kHz`；96000 -> `96 kHz` */
private fun formatSampleRate(hz: Int): String {
    val khz = hz / 1000.0
    return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
}
