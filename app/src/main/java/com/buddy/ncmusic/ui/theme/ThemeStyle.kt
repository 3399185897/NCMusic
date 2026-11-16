package com.buddy.ncmusic.ui.theme

/**
 * 配色风格（对应 SukiSU「色彩风格」）。
 *
 * ## 这套参数是怎么来的
 *
 * Material You 的官方实现（material-color-utilities）用 HCT 色彩空间，
 * 由种子色派生出 primary / secondary / tertiary 三组**不同色相与色度**的调色板。
 * 九种风格的本质差异只有三件事：
 *
 * 1. **primary 的色相偏移与色度** —— 主色离种子色多远、多艳
 * 2. **secondary / tertiary 的色相偏移** —— 次要色与点缀色是否换色相
 * 3. **中性色的色度** —— surface 系列是否带一点主题色
 *
 * 因此这里用「色相偏移 + 色度缩放」的组合来近似每种风格，
 * 而不是各写一套完整调色板 —— 保证任意种子色都能得到协调的结果。
 *
 * @param primaryHue primary 的色相偏移（度）
 * @param primaryChroma primary 的色度缩放
 * @param secondaryHue secondary 的色相偏移
 * @param secondaryChroma secondary 的色度缩放
 * @param tertiaryHue tertiary 的色相偏移
 * @param tertiaryChroma tertiary 的色度缩放
 * @param neutralChroma 中性色（surface / outline）的色度缩放
 */
enum class PaletteStyle(
    val key: String,
    val label: String,
    val subtitle: String,
    val primaryHue: Float = 0f,
    val primaryChroma: Float = 1f,
    val secondaryHue: Float = 0f,
    val secondaryChroma: Float = 0.35f,
    val tertiaryHue: Float = 60f,
    val tertiaryChroma: Float = 0.5f,
    // 0.08 → 0.12：配合修正后的钟形曲线，让 surface 系列真正带上主题色调。
    // MD3 的 neutral palette 名义 chroma 是 4（HCT），在浅色面上肉眼可辨。
    val neutralChroma: Float = 0.12f,
) {
    /** 默认：柔和的单色点缀，次要色与主色同色相 */
    TonalSpot(
        key = "TonalSpot",
        label = "色调点缀",
        subtitle = "默认，柔和协调",
    ),

    /** 中性：大幅压低色度，接近灰阶但保留一丝色相 */
    Neutral(
        key = "Neutral",
        label = "中性",
        subtitle = "低饱和、偏灰",
        primaryChroma = 0.30f,
        secondaryChroma = 0.12f,
        tertiaryChroma = 0.22f,
        neutralChroma = 0.04f,
    ),

    /** 鲜艳：提高色度，主色更饱和 */
    Vibrant(
        key = "Vibrant",
        label = "鲜艳",
        subtitle = "高对比、醒目",
        primaryChroma = 1.20f,
        secondaryChroma = 0.55f,
        tertiaryHue = 45f,
        tertiaryChroma = 0.75f,
        neutralChroma = 0.12f,
    ),

    /** 表现力：色相跳跃最大的一种，secondary / tertiary 各走远色相 */
    Expressive(
        key = "Expressive",
        label = "表现力",
        subtitle = "活泼、色相跳跃",
        primaryHue = 12f,
        secondaryHue = 95f,
        secondaryChroma = 0.55f,
        tertiaryHue = 160f,
        tertiaryChroma = 0.65f,
        neutralChroma = 0.10f,
    ),

    /** 彩虹：色相均匀铺开 */
    Rainbow(
        key = "Rainbow",
        label = "彩虹",
        subtitle = "多彩跳跃",
        secondaryHue = 35f,
        secondaryChroma = 0.62f,
        tertiaryHue = 100f,
        tertiaryChroma = 0.80f,
        neutralChroma = 0.10f,
    ),

    /** 果盘：主色偏暖，点缀色跨度大 */
    FruitSalad(
        key = "FruitSalad",
        label = "果盘",
        subtitle = "明快多彩",
        primaryHue = -18f,
        secondaryHue = 55f,
        secondaryChroma = 0.75f,
        tertiaryHue = 115f,
        tertiaryChroma = 0.85f,
        neutralChroma = 0.12f,
    ),

    /** 单色：完全无彩，只有明暗层次 */
    Monochrome(
        key = "Monochrome",
        label = "单色",
        subtitle = "近乎单色",
        primaryChroma = 0f,
        secondaryChroma = 0f,
        tertiaryChroma = 0f,
        neutralChroma = 0f,
    ),

    /** 保真：最大限度贴近种子色本身 */
    Fidelity(
        key = "Fidelity",
        label = "保真",
        subtitle = "贴近种子色",
        primaryChroma = 1.15f,
        secondaryChroma = 0.30f,
        tertiaryHue = 25f,
        tertiaryChroma = 0.45f,
        neutralChroma = 0.05f,
    ),

    /** 内容：按种子色的实际色度推导，中性面几乎不着色 */
    Content(
        key = "Content",
        label = "内容",
        subtitle = "由种子色推导",
        secondaryChroma = 0.40f,
        tertiaryHue = 60f,
        tertiaryChroma = 0.50f,
        neutralChroma = 0.05f,
    ),
    ;

    companion object {
        fun of(key: String?): PaletteStyle =
            entries.firstOrNull { it.key == key } ?: TonalSpot

        /**
         * 哪些风格支持 SPEC_2025。
         *
         * 与 SukiSU（materialkolor 的 `PaletteStyle.supportsSpec2025`）保持一致：
         * 只有 TonalSpot / Neutral / Vibrant / Expressive 四套有 2025 版定义，
         * 其余风格在 2025 下没有官方 tone 表，**必须回退 2021**，
         * 否则会生成一套"既不是 21 也不是 25"的配色。
         */
        val SPEC_2025_STYLES = setOf(TonalSpot, Neutral, Vibrant, Expressive)
    }
}

/** 当前风格是否支持 SPEC_2025（不支持则由 [ColorStandard.effectiveFor] 回退） */
val PaletteStyle.supportsSpec2025: Boolean
    get() = this in PaletteStyle.SPEC_2025_STYLES

/**
 * 色彩标准（对应 SukiSU「色彩标准」）。
 *
 * - `SPEC_2021`：Material 3 初版的 tone 取值（primary 40 / 深色 80），整体更内敛
 * - `SPEC_2025`：Material 3 Expressive 的取值（primary 45 / 深色 82，色度更高），
 *   主色更亮更饱和，容器与背景对比略强
 *
 * 差异体现在两处：**主色 tone** 与**整体色度系数**。
 */
enum class ColorStandard(
    val key: String,
    val label: String,
    val subtitle: String,
    /** 整体色度系数：2025 更艳 */
    val chromaScale: Float,
    /** 浅色主题的 primary tone */
    val primaryTone: Int,
    /** 深色主题的 primary tone */
    val primaryToneDark: Int,
    /** 浅色主题的 primaryContainer tone */
    val primaryContainerTone: Int = 90,
    /** 深色主题的 primaryContainer tone */
    val primaryContainerToneDark: Int = 30,
) {
    SPEC_2021(
        key = "SPEC_2021",
        label = "SPEC_2021",
        subtitle = "Material 3 初版，内敛克制",
        chromaScale = 1f,
        primaryTone = 40,
        primaryToneDark = 80,
    ),
    SPEC_2025(
        key = "SPEC_2025",
        label = "SPEC_2025",
        subtitle = "Material 3 Expressive，更亮更饱和",
        chromaScale = 1.18f,
        primaryTone = 45,
        primaryToneDark = 82,
        primaryContainerTone = 90,
        primaryContainerToneDark = 30,
    ),
    ;

    companion object {
        fun of(key: String?): ColorStandard =
            entries.firstOrNull { it.key == key } ?: SPEC_2021
    }

    /**
     * 实际生效的标准。
     *
     * 选了 SPEC_2025 但当前风格不支持时，静默回退 SPEC_2021 ——
     * 与 SukiSU 的 `ColorSpec.SpecVersion.effectiveFor(style)` 同语义。
     * 不做提示：界面上的"色彩标准"仍显示用户的选择，
     * 真正的差异只在配色本身（避免为一个兼容细节增加弹窗）。
     */
    fun effectiveFor(style: PaletteStyle): ColorStandard =
        if (this == SPEC_2025 && !style.supportsSpec2025) SPEC_2021 else this
}

/**
 * 颜色模式（对齐 SukiSU 的 `ColorMode`）。
 *
 * SukiSU 把「明暗」与「取色来源」合成**一个**列表，而不是两个独立开关 ——
 * 因为「莫奈 + 浅色 + 纯黑」这类组合本身是非法的，
 * 用两个开关表达必然出现互斥冲突（我们之前就踩过：关深色会顺带关莫奈）。
 *
 * 这里同样收敛为一个 7 项单选；每项只描述一个**合法组合**。
 */
enum class ColorMode(
    val key: String,
    val label: String,
    val subtitle: String,
    /** 对应 UserState.themeMode：system / light / dark */
    val themeMode: String,
    /** 是否启用壁纸取色（Monet） */
    val monet: Boolean,
    /** 是否启用 AMOLED 纯黑背景 */
    val amoled: Boolean,
) {
    SYSTEM("system", "跟随系统", "浅色 / 深色跟随系统设置", "system", false, false),
    LIGHT("light", "浅色", "始终使用浅色", "light", false, false),
    DARK("dark", "深色", "始终使用深色", "dark", false, false),
    MONET_SYSTEM("monet_system", "跟随系统 · 壁纸取色", "Monet 动态取色", "system", true, false),
    MONET_LIGHT("monet_light", "浅色 · 壁纸取色", "Monet 动态取色", "light", true, false),
    MONET_DARK("monet_dark", "深色 · 壁纸取色", "Monet 动态取色", "dark", true, false),
    DARK_AMOLED("dark_amoled", "深色 · 纯黑", "AMOLED 纯黑背景，省电且纯粹", "dark", true, true),
    ;

    companion object {
        fun of(key: String?): ColorMode = entries.firstOrNull { it.key == key } ?: MONET_SYSTEM

        /** 由三项存储值还原出当前模式（找不到精确匹配时回退到最接近的一项） */
        fun from(themeMode: String, monet: Boolean, amoled: Boolean): ColorMode {
            if (amoled) return DARK_AMOLED
            entries.firstOrNull {
                it.amoled == amoled && it.themeMode == themeMode && it.monet == monet
            }?.let { return it }
            // 兜底：只按明暗匹配（例如用户此前从未设过取色开关）
            return entries.firstOrNull { !it.amoled && it.themeMode == themeMode && it.monet == monet }
                ?: MONET_SYSTEM
        }
    }
}
