package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import com.buddy.ncmusic.ui.theme.rememberMonetSeed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.ui.theme.ColorMode
import com.buddy.ncmusic.ui.theme.ColorStandard
import com.buddy.ncmusic.ui.theme.PaletteStyle
import com.buddy.ncmusic.ui.theme.previewScheme
import androidx.compose.runtime.collectAsState

/**
 * 主题设置页（对齐 SukiSU 的结构）。
 *
 * 页面自上而下：
 * ```
 * 预览卡片        用当前配色渲染的迷你界面 + 标准名
 * 颜色            色彩风格预览 / 风格 / 标准 / 自选主题色
 * 背景            系统壁纸取色（Monet）、播放器封面取色
 * ```
 *
 * 与 SukiSU 一致的地方：
 * - 顶部是「预览 + 说明」而不是直接堆选项，用户先看到结果再决定
 * - 「颜色」是**可折叠**的卡片，展开后是风格预览带 + 三个入口行
 * - 每个入口行右侧显示**当前值**（TonalSpot / SPEC_2025），点开才选
 */
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.userState.collectAsState(initial = UserState())
    val dark = when (state.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val style = remember(state.paletteStyle) { PaletteStyle.of(state.paletteStyle) }
    val spec = remember(state.colorSpec) { ColorStandard.of(state.colorSpec) }
    // 种子色：自选色 > 壁纸主色 > 品牌红。
    // 与 NCMusicTheme 的取色顺序保持一致，预览才会等于实际结果。
    val monetSeed = rememberMonetSeed(dark)
    val seed = when {
        state.customColor != 0L -> state.customColor
        state.dynamicColor && monetSeed != null -> monetSeed
        else -> 0xFFD13030
    }

    var colorExpanded by remember { mutableStateOf(true) }
    // 当前颜色模式：由「明暗 + 取色 + AMOLED」三项存储值反推
    val colorMode = remember(state.themeMode, state.dynamicColor, state.amoled) {
        ColorMode.from(state.themeMode, state.dynamicColor, state.amoled)
    }
    var showColorModeDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showSpecDialog by remember { mutableStateOf(false) }
    var showSeedDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }

    // 相册选图（Photo Picker：不需要存储权限）
    val pickBackground = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) vm.setCustomBackgroundFromUri(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        // ---------- 顶栏 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "主题设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ---------- 预览 ----------
        ThemePreviewCard(
            style = style,
            spec = spec,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "预览 · ${if (spec == ColorStandard.SPEC_2025) "Material 3 Expressive" else "Material 3"}",
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // ---------- 颜色 ----------
        ThemeSectionCard(
            icon = Icons.Default.Palette,
            title = "颜色",
            subtitle = "Monet、强调色与配色",
            expanded = colorExpanded,
            onToggle = { colorExpanded = !colorExpanded },
        ) {
            if (colorExpanded) {
                // 当前模式写进副标题，而不是右侧 value：
                // 模式名最长可到「跟随系统 · 壁纸取色」，与副标题挤在同一行会换行错位。
                SettingRow(
                    icon = Icons.Default.DarkMode,
                    title = "颜色模式",
                    subtitle = "当前：${colorMode.label}",
                    value = "",
                    onClick = { showColorModeDialog = true },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                // 风格预览带：每颗圆用该风格**真实生成**的配色，
                // 选中项打勾 —— 与 SukiSU 的色系预览一致。
                val darkForPreview = dark
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(PaletteStyle.entries, key = { it.key }) { item ->
                        StyleSwatch(
                            scheme = remember(seed, darkForPreview, item, spec) {
                                previewScheme(seed, darkForPreview, item, spec)
                            },
                            selected = item == style,
                            onClick = { vm.setPaletteStyle(item.key) },
                        )
                    }
                }

                SettingRow(
                    icon = Icons.Default.ColorLens,
                    title = "色彩风格",
                    subtitle = null,
                    value = style.key,
                    onClick = { showStyleDialog = true },
                )
                SettingRow(
                    icon = Icons.Default.Tune,
                    title = "色彩标准",
                    subtitle = null,
                    value = spec.key,
                    onClick = { showSpecDialog = true },
                )
                SettingRow(
                    icon = Icons.Default.Colorize,
                    title = "自选主题色",
                    subtitle = "改用该色生成配色",
                    value = if (state.customColor != 0L) {
                        themeSeedColors.firstOrNull { it.first == state.customColor }?.second ?: "自定义"
                    } else {
                        "Monet"
                    },
                    onClick = { showSeedDialog = true },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                SettingRow(
                    icon = Icons.Default.Image,
                    title = "自定义背景",
                    subtitle = "铺在全应用内容之下",
                    value = if (state.customBackground.isNotBlank()) "已设置" else "未设置",
                    onClick = { showBackgroundDialog = true },
                )
            }
        }

        // 背景不再单独成卡：
        //  · 「使用系统桌面壁纸 / Monet」已并入上面的「颜色模式」单选（两者本就同一件事）；
        //  · 「播放器封面取色」在 设置 → 外观 已有同一开关，这里保留会形成两处入口。
        // 只剩「自定义背景」一项，作为「颜色」卡的最后一行。
        Spacer(Modifier.height(24.dp))
    }

    // ---------- 弹窗 ----------
    if (showColorModeDialog) {
        PickerDialog(
            title = "颜色模式",
            subtitle = "决定明暗与取色来源，纯黑仅在深色下生效",
            onDismiss = { showColorModeDialog = false },
        ) {
            ColorMode.entries.forEach { item ->
                PickerRow(
                    selected = item == colorMode,
                    title = item.label,
                    subtitle = item.subtitle,
                    trailing = null,
                    swatch = null,
                    onClick = {
                        vm.applyColorMode(item)
                        showColorModeDialog = false
                    },
                )
            }
        }
    }

    if (showStyleDialog) {
        PickerDialog(
            title = "色彩风格",
            subtitle = "以主题色为种子，按所选风格生成整套方案",
            onDismiss = { showStyleDialog = false },
        ) {
            PaletteStyle.entries.forEach { item ->
                PickerRow(
                    selected = item == style,
                    title = item.label,
                    subtitle = item.subtitle,
                    trailing = item.key,
                    swatch = remember(seed, dark, item, spec) {
                        previewScheme(seed, dark, item, spec).primary
                    },
                    onClick = {
                        vm.setPaletteStyle(item.key)
                        showStyleDialog = false
                    },
                )
            }
        }
    }

    if (showSpecDialog) {
        PickerDialog(
            title = "色彩标准",
            subtitle = "影响主色明度与整体色度",
            onDismiss = { showSpecDialog = false },
        ) {
            ColorStandard.entries.forEach { item ->
                PickerRow(
                    selected = item == spec,
                    title = item.label,
                    subtitle = item.subtitle,
                    trailing = null,
                    swatch = null,
                    onClick = {
                        vm.setColorSpec(item.key)
                        showSpecDialog = false
                    },
                )
            }
        }
    }

    if (showBackgroundDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundDialog = false },
            title = { Text("自定义背景") },
            text = {
                Text(
                    "选择一张图片作为全应用背景。图片会复制到应用私有目录，" +
                        "并叠加主题色渐变遮罩以保证文字可读。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundDialog = false
                        pickBackground.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                ) { Text("选择图片") }
            },
            dismissButton = {
                if (state.customBackground.isNotBlank()) {
                    TextButton(
                        onClick = {
                            vm.clearCustomBackground()
                            showBackgroundDialog = false
                        },
                    ) { Text("移除背景") }
                } else {
                    TextButton(onClick = { showBackgroundDialog = false }) { Text("取消") }
                }
            },
        )
    }

    if (showSeedDialog) {
        PickerDialog(
            title = "自选主题色",
            subtitle = "选择后自动关闭 Monet；再次点击已选颜色可恢复默认",
            onDismiss = { showSeedDialog = false },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                // 用 FlowRow 而不是固定 6 列：
                // 固定列宽下每行 6×40dp + 5×10dp = 290dp，超过 AlertDialog
                // 的内容宽度，最右侧两个色块会被裁成竖条。
                // FlowRow 按可用宽度自动换行，不同屏宽都不会溢出。
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    themeSeedColors.forEach { (argb, _) ->
                        val selected = state.customColor == argb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable {
                                    vm.setCustomColor(if (selected) 0L else argb)
                                    showSeedDialog = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        vm.setCustomColor(0L)
                        showSeedDialog = false
                    },
                ) { Text("恢复默认（跟随 Monet）") }
            }
        }
    }
}

/** 预设种子色 —— 与 SukiSU 的色板密度接近，覆盖常见色相 */
internal val themeSeedColors = listOf(
    0xFFE53935 to "红色",
    0xFFFB8C00 to "琥珀",
    0xFFFDD835 to "黄色",
    0xFF43A047 to "绿色",
    0xFF00897B to "蓝绿",
    0xFF00ACC1 to "青色",
    0xFF1E88E5 to "蓝色",
    0xFF5E35B1 to "靛蓝",
    0xFF8E24AA to "紫色",
    0xFFEC407A to "粉色",
    0xFF6D4C41 to "棕色",
    0xFF546E7A to "蓝灰",
)

/**
 * 顶部预览卡片：用**当前配色**渲染本应用的首页缩略图。
 *
 * 这里刻意不套用参考项目的界面结构 —— 用户要看的是「我自己的 App
 * 换成这套配色后长什么样」。因此按首页真实的层次来画：
 * 顶栏问候 → 雷达歌单（大卡）→ 推荐歌单（双列）→ 悬浮底栏药丸。
 *
 * 所有颜色都取自 `MaterialTheme.colorScheme`，与真实界面 100% 同源。
 */
@Composable
private fun ThemePreviewCard(
    style: PaletteStyle,
    spec: ColorStandard,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(200.dp).height(392.dp),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(7.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surface),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 10.dp)) {
                    // ---- 页头：大标题 + 日期（对应新版首页）----
                    Text(
                        text = "推荐",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.26f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cs.onSurfaceVariant.copy(alpha = 0.38f)),
                    )

                    Spacer(Modifier.height(9.dp))

                    // ---- 每日推荐：整块一张卡 ----
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "每日推荐",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                )
                                Box(
                                    Modifier
                                        .padding(top = 3.dp)
                                        .fillMaxWidth(0.62f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(cs.onSurfaceVariant.copy(alpha = 0.42f)),
                                )
                            }
                            // 播放按钮：实色主色（与真实首页一致）
                            Row(
                                Modifier
                                    .height(18.dp)
                                    .clip(CircleShape)
                                    .background(cs.primary)
                                    .padding(horizontal = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(cs.onPrimary),
                                )
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    Modifier
                                        .size(width = 12.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(cs.onPrimary),
                                )
                            }
                        }

                        Spacer(Modifier.height(7.dp))

                        // 三行歌曲：封面块 + 两条文字线 + 右侧三点
                        repeat(3) { i ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (i) {
                                                0 -> cs.primaryContainer
                                                1 -> cs.secondaryContainer
                                                else -> cs.tertiaryContainer
                                            },
                                        ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(if (i == 1) 0.82f else 0.66f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cs.onSurface.copy(alpha = 0.75f)),
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.38f)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cs.onSurfaceVariant.copy(alpha = 0.45f)),
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(2.5.dp)
                                        .clip(CircleShape)
                                        .background(cs.onSurfaceVariant.copy(alpha = 0.5f)),
                                )
                            }
                        }

                        Spacer(Modifier.height(5.dp))
                        // 分页点：当前页长胶囊
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(width = 10.dp, height = 3.dp)
                                    .clip(CircleShape)
                                    .background(cs.primary),
                            )
                            repeat(4) {
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(cs.outlineVariant),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ---- 雷达歌单：小节标题 + 居中大卡 ----
                    Text(
                        text = "雷达歌单",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        // 两侧邻卡的窄条
                        Box(
                            Modifier
                                .width(12.dp)
                                .height(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.surfaceContainerHighest),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cs.surfaceContainer)
                                .padding(5.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(cs.primaryContainer, cs.tertiaryContainer),
                                        ),
                                    ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cs.onSurface.copy(alpha = 0.7f)),
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cs.onSurfaceVariant.copy(alpha = 0.4f)),
                            )
                        }
                        Box(
                            Modifier
                                .width(12.dp)
                                .height(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.surfaceContainerHighest),
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // ---- 悬浮底栏药丸（选中项放大 + 文字）----
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .height(21.dp)
                                .clip(CircleShape)
                                .background(cs.primary)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(cs.onPrimary))
                            Spacer(Modifier.width(3.dp))
                            Box(
                                Modifier
                                    .size(width = 12.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cs.onPrimary),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        repeat(2) {
                            Box(
                                Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(cs.onSurfaceVariant.copy(alpha = 0.55f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 可折叠分组卡片（标题行带图标 + 副标题 + 展开箭头） */
@Composable
private fun ThemeSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeIconTile(icon)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onToggle != null) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 左侧图标方块（SukiSU 设置行的视觉锚点） */
@Composable
private fun ThemeIconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(AppShapes.of(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 带图标的「进入选择」行 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeIconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 带图标的开关行 */
@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeIconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 风格预览圆：外圈容器色、中圈次色、内圈主色；选中打勾 */
@Composable
private fun StyleSwatch(
    scheme: androidx.compose.material3.ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(scheme.primaryContainer)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** 通用选择弹窗 */
@Composable
private fun PickerDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 弹窗里的单行选项 */
@Composable
private fun PickerRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    trailing: String?,
    swatch: Color?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.of(16.dp))
            .background(
                // 选中态用 primaryContainer：本页所有行首图标块都是 primaryContainer，
                // 用 secondaryContainer 会出现「蓝图标 + 粉选中条」的两套强调色。
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
