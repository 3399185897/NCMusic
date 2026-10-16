package com.buddy.ncmusic.util

import com.buddy.ncmusic.data.model.Song

/**
 * 会员等级标记，用于在音质列表中显示与官方一致的徽标。
 */
enum class VipTier(val label: String) {
    /** 无需会员 */
    NONE(""),

    /** 黑胶 VIP */
    VIP("VIP"),

    /** 黑胶 SVIP */
    SVIP("SVIP"),
}

/**
 * 音质档位 —— **与网易云官方客户端的「当前歌曲音质」逐项对齐**。
 *
 * ## 对齐来源
 *
 * 档位标识（[level]）取自网易云 `/song/enhance/player/url/v1` 接口
 * 实际接受的 token；中文名、英文名、描述文案、规格数值（采样率 / 位深 /
 * 声道 / 码率）与会员等级均按官方 App 的展示内容填写。
 *
 * ## 关键修正（相对上一版）
 *
 * | 档位 | 上一版 | 官方 |
 * |---|---|---|
 * | 无损 | 44.1kHz/16bit | **最高 48kHz/16bit** |
 * | 高清臻音 | 名为「Hi-Res」 | 官方现叫「高清臻音 / Spatial Audio」 |
 * | 沉浸环绕声 | **缺失** | 官方第 6 档（`jyeffect`） |
 * | 臻音全景声 | **缺失** | 官方第 7 档（`sky`） |
 *
 * ## 关于 `higher`（较高）
 *
 * 官方客户端的「当前歌曲音质」列表**不展示**该档位（仅 7 档），
 * 但接口仍接受 `higher` 作为降级目标。因此保留在枚举中、
 * 用 [visible] 标记为不在界面列出，避免破坏降级链。
 */
enum class AudioQuality(
    /** 接口 token，原样传给服务端 */
    val level: String,
    /** 中文名（官方名称） */
    val label: String,
    /** 英文名；官方部分档位无英文名，为空串 */
    val englishName: String,
    /** 列表图标内的短标识（官方用「标 / HQ / SQ」这类缩写） */
    val shortLabel: String,
    /** 描述文案（官方文案） */
    val description: String,
    /**
     * 规格文本，**与官方逐字一致**。
     *
     * 注意：无损是 `48kHz/16bit`（不是 44.1kHz）；
     * 环绕声 / 全景声类用声道数描述而非采样率 —— 这些都与官方相同。
     */
    val specText: String,
    /** 会员等级 */
    val vip: VipTier,
    /** 该档位的估计码率（kbps），仅用于下载体积估算；0 表示不适用 */
    val kbps: Int,
    /**
     * 码率下限（bps）。服务端 `maxbr` 达到该值即认为该曲支持此档。
     *
     * **`0` 表示不参与过滤** —— 环绕声 / 全景声类档位在歌曲对象的
     * `h/m/l/sq/hr` 里没有对应字段，无从判断，故一律视为可用
     * （宁可多列，也好过把能播的档位藏起来）。
     */
    val minBr: Int,
    /** 是否在「当前歌曲音质」列表中展示 */
    val visible: Boolean = true,
) {
    STANDARD(
        level = "standard",
        label = "标准",
        englishName = "",
        shortLabel = "标",
        description = "",
        specText = "128kbps",
        vip = VipTier.NONE,
        kbps = 128,
        minBr = 0,
    ),
    HIGHER(
        level = "higher",
        label = "较高",
        englishName = "",
        shortLabel = "较",
        description = "",
        specText = "192kbps",
        vip = VipTier.NONE,
        kbps = 192,
        minBr = 192_000,
        // 官方「当前歌曲音质」不列出该档，仅作接口降级目标
        visible = false,
    ),
    EXHIGH(
        level = "exhigh",
        label = "极高",
        englishName = "HQ",
        shortLabel = "HQ",
        description = "近CD音质的细节体验",
        specText = "最高320kbps",
        vip = VipTier.VIP,
        kbps = 320,
        minBr = 320_000,
    ),
    LOSSLESS(
        level = "lossless",
        label = "无损",
        englishName = "SQ",
        shortLabel = "SQ",
        description = "高保真无损音质",
        specText = "最高48kHz/16bit",
        vip = VipTier.VIP,
        kbps = 900,
        minBr = 900_000,
    ),
    HIRES(
        level = "hires",
        label = "高清臻音",
        englishName = "Spatial Audio",
        shortLabel = "HR",
        description = "高频细节还原与清晰沉浸感",
        specText = "96kHz/24bit",
        vip = VipTier.VIP,
        kbps = 1500,
        minBr = 1_500_000,
    ),
    JYEFFECT(
        level = "jyeffect",
        label = "沉浸环绕声",
        englishName = "Surround Audio",
        shortLabel = "环",
        description = "环绕音感",
        specText = "最高5.1声道",
        vip = VipTier.SVIP,
        kbps = 1500,
        // 歌曲对象无对应码率字段，不参与过滤
        minBr = 0,
    ),
    SKY(
        level = "sky",
        label = "臻音全景声",
        englishName = "Audio Vivid",
        shortLabel = "全",
        description = "沉浸三维空间音频",
        specText = "最高7.1.4声道",
        vip = VipTier.SVIP,
        kbps = 1800,
        minBr = 0,
    ),
    JYMASTER(
        level = "jymaster",
        label = "超清母带",
        englishName = "Master",
        shortLabel = "母",
        description = "极致细节",
        specText = "192kHz/24bit",
        vip = VipTier.SVIP,
        kbps = 2000,
        minBr = 1_900_000,
    );

    companion object {
        /** 全部档位（含不展示的 `higher`），由低到高 */
        val all: List<AudioQuality> = entries.toList()

        /**
         * 界面上展示的档位，**由高到低** —— 与官方列表顺序一致
         * （最好的在最上面）。
         */
        val visibleDescending: List<AudioQuality> = entries.filter { it.visible }.reversed()

        /** 由 level 字符串查档位 */
        fun of(level: String): AudioQuality? = entries.firstOrNull { it.level == level }

        /**
         * [level] 是否在该曲目可提供的范围内。
         *
         * `maxBr <= 0`（服务端未给出码率）时一律返回 `true` ——
         * 无从判断就不要替用户做决定。
         */
        fun availableFor(level: String, maxBr: Int): Boolean {
            val q = of(level) ?: return true
            if (maxBr <= 0) return true
            if (q.minBr == 0) return true
            return q.minBr <= maxBr
        }

        /**
         * 某曲目支持的档位（含不展示项）。
         *
         * 下载器用它过滤可选项；界面则用
         * [visibleDescending] + [availableFor] 组合，
         * 以便「列出全部但禁用不可用项」。
         */
        fun supportedBy(maxBr: Int): List<AudioQuality> =
            if (maxBr <= 0) all else all.filter { availableFor(it.level, maxBr) }

        /** 某曲目可得的**最高**档位；未知时返回 null */
        fun highestOf(maxBr: Int): AudioQuality? =
            if (maxBr <= 0) null
            else visibleDescending.firstOrNull { availableFor(it.level, maxBr) }

        /** 把 level 收敛到 [maxBr] 允许的最高档 */
        fun clamp(level: String, maxBr: Int): String {
            if (maxBr <= 0) return level
            if (availableFor(level, maxBr)) return level
            return visibleDescending.firstOrNull { availableFor(it.level, maxBr) }?.level
                ?: "standard"
        }
    }
}

/**
 * 供播放器显示：当前曲目的音频规格，**文案与官方一致**。
 *
 * 按该曲目**可得的最高档位**推导，而非用户当前选择的档位 ——
 * 后者可能因账号权限拿不到，那样显示的规格会与实听不符。
 * 服务端未给出码率时返回 null（不显示，好过显示错误信息）。
 */
fun Song.audioSpecText(): String? {
    if (maxBr <= 0) return null
    val q = AudioQuality.highestOf(maxBr) ?: return null
    return q.specText
}
