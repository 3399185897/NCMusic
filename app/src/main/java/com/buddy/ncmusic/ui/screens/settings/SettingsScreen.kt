package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.buddy.ncmusic.util.VipTier
import com.buddy.ncmusic.ui.components.VipBadge
import androidx.compose.ui.text.style.TextOverflow
import com.buddy.ncmusic.util.AudioQuality
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.font.FontWeight
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.util.ApiResult
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.ui.theme.ColorMode
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import com.buddy.ncmusic.ui.theme.ColorRevealState
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.buddy.ncmusic.ui.util.Md3Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.playback.AudioEffects
import com.buddy.ncmusic.playback.LyricWindowService
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.playback.UsbAudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.ln
import java.io.File

class SettingsViewModel : ViewModel() {
    private val prefs get() = NCMusicApp.instance.userPreferences
    private val context get() = NCMusicApp.instance

    val userState: Flow<UserState> = prefs.userStateFlow

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    /** EQ 变更计数，用于触发频段 UI 刷新 */
    private val _eqTick = MutableStateFlow(0)
    val eqTick: StateFlow<Int> = _eqTick.asStateFlow()

    /** USB 独占状态文案 */
    private val _usbStatus = MutableStateFlow("")
    val usbStatus: StateFlow<String> = _usbStatus.asStateFlow()

    /** 桌面歌词状态文案 */
    private val _overlayStatus = MutableStateFlow("")
    val overlayStatus: StateFlow<String> = _overlayStatus.asStateFlow()

    init {
        refreshCacheSize()
        viewModelScope.launch {
            val s = prefs.userStateFlow.first()
            _usbStatus.value = UsbAudioManager.apply(context, s.usbExclusive)
            if (s.usbExclusive) PlaybackManager.applyUsbExclusive(true)
        }
        // 已登录但本地无等级（旧数据 / 登录时拉取失败）→ 补拉一次
        viewModelScope.launch {
            val s = prefs.userStateFlow.first()
            if (s.isLoggedIn && s.level <= 0) {
                NCMusicApp.instance.musicRepository.refreshUserLevel(s.userId)
            }
        }
    }

    /**
     * 刷新"当前缓存"显示值。
     *
     * ⚠️ 口径必须与「最大缓存大小」上限**一致**。
     *
     * 原先统计的是 `cacheDir` **全目录**（含下载中转等临时工作文件），
     * 而容量上限只作用于 `cacheDir/ncmusic_cache/` ——
     * 于是用户把上限设为 1 GB，界面却可能显示 1.5 GB（临时文件被计入），
     * 看上去像是"限制完全没生效"，实际只是两个数字口径不同。
     *
     * 现改为读取 [CacheManager.currentSize]，与淘汰逻辑管理同一批文件。
     */
    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = NCMusicApp.instance.cacheManager.currentSize()
        }
    }

    /**
     * 手动清空缓存。
     *
     * 改走 [CacheManager.clearAll] 统一入口 ——
     * 原先这里自己遍历 cacheDir 删除，与容量淘汰逻辑各写一份，
     * 容易出现「手动清理范围」与「自动淘汰范围」不一致的问题。
     */
    fun clearCache() {
        viewModelScope.launch {
            NCMusicApp.instance.cacheManager.clearAll()
            refreshCacheSize()
        }
    }
    /**
     * 深色模式**独立**，不再联动取色或自定义色。
     *
     * 深色只决定明暗，色调来源（莫奈 / 自定义）由下面两个开关各自决定 ——
     * 两者正交，可以任意组合。此前"开深色就关取色、清自定义色"的写法
     * 会让用户觉得深色开关把别的设置一起清掉了。
     */
    fun setThemeMode(v: String) {
        viewModelScope.launch { prefs.setThemeMode(v) }
    }

    /**
     * 莫奈取色。开启时自动清空自定义色（否则两套色源冲突）。
     */
    fun setDynamicColor(v: Boolean) {
        viewModelScope.launch {
            prefs.setDynamicColor(v)
            // 开启莫奈取色 → 自动关闭自定义色（不触碰深色模式）
            if (v) prefs.setCustomColor(0L)
        }
    }

    /**
     * 自定义主题色。选中时自动关闭莫奈取色（两套色源只保留一个）。
     */
    fun setCustomColor(v: Long) {
        viewModelScope.launch {
            prefs.setCustomColor(v)
            // 选择自定义色 → 自动关闭莫奈取色
            if (v != 0L) prefs.setDynamicColor(false)
        }
    }

    fun setAmoled(v: Boolean) {
        viewModelScope.launch { prefs.setAmoled(v) }
    }

    /**
     * 一次性应用一个颜色模式（对齐 SukiSU 的 ColorMode）。
     *
     * 三项存储值必须在**同一次**写入语义里更新：分三次独立 `edit` 会让
     * 界面短暂处于「深色 + 未取色 + AMOLED」这种非法组合，
     * 表现为切换时闪一下品牌红，再跳回目标配色。
     * 这里顺序为先关 AMOLED → 再设取色 → 最后设明暗，
     * 使每一步的中间态都是合法组合。
     */
    fun applyColorMode(mode: ColorMode) {
        viewModelScope.launch {
            prefs.setAmoled(mode.amoled)
            prefs.setDynamicColor(mode.monet)
            prefs.setThemeMode(mode.themeMode)
            // 切到取色模式时清掉自选色，否则自选色优先级更高、切了没反应
            if (mode.monet) prefs.setCustomColor(0L)
        }
    }
    fun setQuality(v: String) { viewModelScope.launch { prefs.setQuality(v) } }
    fun setWifiQuality(v: String) { viewModelScope.launch { prefs.setWifiQuality(v) } }
    fun setMobileQuality(v: String) { viewModelScope.launch { prefs.setMobileQuality(v) } }
    fun setStartPage(v: String) { viewModelScope.launch { prefs.setStartPage(v) } }
    /**
     * 设置最大缓存。
     *
     * 在**写入前统一钳制**到 [CACHE_MIN_MB]..[CACHE_MAX_MB]。
     * 之所以在这一层做（而不是只靠界面约束）：滑块与精确输入是两条独立入口，
     * 仅在界面侧限制必然出现不一致（如输入 999 GB 后滑块钳到 10 GB、
     * 数字却显示 999 GB）。收口在 VM 后，无论从哪条路径进入结果都一致。
     */
    fun setMaxCacheMb(v: Int) {
        val clamped = v.coerceIn(CACHE_MIN_MB, CACHE_MAX_MB)
        viewModelScope.launch { prefs.setMaxCacheMb(clamped) }
    }

    fun setTimerMinutes(v: Int) {
        viewModelScope.launch { prefs.setTimerMinutes(v) }
        PlaybackManager.setSleepTimer(v)
    }

    fun setVolumeNormalize(v: Boolean) {
        viewModelScope.launch { prefs.setVolumeNormalize(v) }
        AudioEffects.setVolumeNormalize(v)
    }

    fun setVolumeGain(gain: Int) {
        viewModelScope.launch { prefs.setVolumeGain(gain) }
        AudioEffects.setVolumeGain(gain)
    }

    fun setDecodeMode(v: String) {
        viewModelScope.launch { prefs.setDecodeMode(v) }
        PlaybackManager.applyDecodeMode(v == "soft")
    }

    fun setEqEnabled(v: Boolean) {
        viewModelScope.launch { prefs.setEqEnabled(v) }
        AudioEffects.setEqEnabled(v)
        _eqTick.value++
    }

    fun setEqBand(index: Int, levelMb: Short) {
        AudioEffects.setBandLevel(index, levelMb)
        _eqTick.value++
    }

    fun resetEq() {
        AudioEffects.reset()
        _eqTick.value++
    }

    fun setUsbExclusive(v: Boolean) {
        viewModelScope.launch { prefs.setUsbExclusive(v) }
        _usbStatus.value = UsbAudioManager.apply(context, v)
        PlaybackManager.applyUsbExclusive(v)
    }

    fun setLyricColor(argb: Long) {
        viewModelScope.launch { prefs.setLyricColor(argb) }
    }

    fun setLyricFontSize(sp: Int) {
        viewModelScope.launch { prefs.setLyricFontSize(sp) }
    }

    fun setLyricLocked(locked: Boolean) {
        viewModelScope.launch { prefs.setLyricLocked(locked) }
    }

    fun setCacheDirUri(uri: String) {
        viewModelScope.launch { prefs.setCacheDirUri(uri) }
    }

    fun setLyricAutoAlign(enabled: Boolean) {
        viewModelScope.launch { prefs.setLyricAutoAlign(enabled) }
    }

    /**
     * 把相册选中的图片复制到私有目录并记录路径。
     *
     * 直接存 `content://` Uri 在进程重启后可能失效（授权是临时的），
     * 所以这里一次性复制成文件，之后按路径加载。
     */
    fun setCustomBackgroundFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(context.filesDir, "theme").apply { mkdirs() }
                    val out = java.io.File(dir, "custom_bg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    out.absolutePath
                }.getOrNull()
            }
            if (!path.isNullOrBlank()) prefs.setCustomBackground(path)
        }
    }

    fun clearCustomBackground() {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    java.io.File(java.io.File(context.filesDir, "theme"), "custom_bg").delete()
                }
            }
            prefs.setCustomBackground("")
        }
    }

    /** 配色风格（PaletteStyle.key），仅在自选主题色下生效 */
    fun setPaletteStyle(style: String) {
        viewModelScope.launch { prefs.setPaletteStyle(style) }
    }

    /** 色彩标准（SPEC_2021 / SPEC_2025） */
    fun setColorSpec(spec: String) {
        viewModelScope.launch { prefs.setColorSpec(spec) }
    }

    fun setPlayerColorEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPlayerColorEnabled(enabled) }
    }

    /** 底栏样式：floating / rounded / classic */
    fun setBottomBarStyle(style: String) {
        viewModelScope.launch { prefs.setBottomBarStyle(style) }
    }

    /** 悬浮岛与底栏的间距（dp） */
    fun setMiniPlayerGap(gapDp: Int) {
        viewModelScope.launch { prefs.setMiniPlayerGap(gapDp) }
    }

    fun setBottomBarRounded(enabled: Boolean) {
        viewModelScope.launch { prefs.setBottomBarRounded(enabled) }
    }

    fun setHomeShowRadar(show: Boolean) {
        viewModelScope.launch { prefs.setHomeShowRadar(show) }
    }

    fun setHomeShowRecommend(show: Boolean) {
        viewModelScope.launch { prefs.setHomeShowRecommend(show) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setCrossfadeEnabled(enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setCrossfadeSeconds(seconds) }
    }

    /** 开关桌面歌词悬浮窗（必要时跳转悬浮窗权限页） */
    fun setLyricOverlay(v: Boolean) {
        viewModelScope.launch { prefs.setLyricOverlay(v) }
        val ctx = context
        if (!v) {
            runCatching { ctx.stopService(Intent(ctx, LyricWindowService::class.java)) }
            _overlayStatus.value = "已关闭"
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            _overlayStatus.value = "请先授予「显示在其他应用上层」权限"
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } else {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LyricWindowService::class.java))
            }
            _overlayStatus.value = "已开启，播放时显示当前歌词"
        }
    }
    fun logout() { viewModelScope.launch { NCMusicApp.instance.musicRepository.logout() } }
}

/** 悬浮岛与底栏的默认间距（dp）—— 与 UserState.miniPlayerGap 的默认值保持一致 */
private const val DEFAULT_MINI_PLAYER_GAP = 8

/** 最大缓存下限（MB）：1 GB —— 与滑块左端、默认值一致 */
const val CACHE_MIN_MB = 1024

/** 最大缓存上限（MB）：10 GB —— 与滑块右端、精确输入上限一致 */
const val CACHE_MAX_MB = 10240

// 音质档位直接取自 [AudioQuality]，与播放器音质面板、下载选择器同源。
// 曾经这里另维护一份「名称（规格）」的字符串表，与播放器文案漂移；
// 现在统一由 QualityOptionRow 渲染，含短标识 / 英文名 / VIP / 描述 / 规格 / 码率。

private val startPageOptions = listOf(
    "home" to "推荐",
    "discover" to "发现",
    "library" to "我的",
)

/** 深色模式三态：开启 / 跟随系统 / 关闭 */
private val themeModeOptions = listOf(
    "dark" to "开启",
    "system" to "跟随系统",
    "light" to "关闭",
)

private val themeOptions = listOf(
    "system" to "跟随系统",
    "light" to "浅色",
    "dark" to "深色",
)

private val timerOptions = listOf(
    0 to "关闭",
    15 to "15 分钟",
    30 to "30 分钟",
    60 to "60 分钟",
    90 to "90 分钟",
)

private val cacheOptions = listOf(
    200 to "200 MB",
    500 to "500 MB",
    1024 to "1 GB",
    2048 to "2 GB",
)


/** 可选歌词颜色。首项须与 `UserPreferences.DEFAULT_LYRIC_COLOR` 保持一致。 */
private val lyricColors = listOf(
    0xFFFFFFFF to "白",
    0xFF000000 to "黑",
    0xFFFFD54F to "金",
    0xFF81D4FA to "蓝",
    0xFFF48FB1 to "粉",
    0xFFA5D6A7 to "绿",
    0xFFFF8A65 to "橙",
)

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEq: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    /** 初始分类：ALL = 分类总览页；其他 = 该分类的子页 */
    initialGroup: SettingsGroup = SettingsGroup.ALL,
    /** 打开主题设置页 */
    onOpenTheme: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    // 展开/收起等内容动画统一受系统「移除动态效果」约束
    val reduceMotion = com.buddy.ncmusic.ui.util.rememberReduceMotion()
    val state by vm.userState.collectAsState(initial = UserState())
    val cacheSize by vm.cacheSize.collectAsState()
    val eqTick by vm.eqTick.collectAsState()
    val usbStatus by vm.usbStatus.collectAsState()
    val overlayStatus by vm.overlayStatus.collectAsState()

    val context = LocalContext.current

    var showTimerDialog by remember { mutableStateOf(false) }
    // 单页视图：所有分组始终渲染（不再按分类过滤）
    val selectedGroup = SettingsGroup.ALL
    var timerInput by remember { mutableStateOf("") }
    var showBottomBarDialog by remember { mutableStateOf(false) }
    var showIslandGapDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var cacheInput by remember { mutableStateOf("") }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("自定义定时关闭") },
            text = {
                OutlinedTextField(
                    value = timerInput,
                    onValueChange = { s -> timerInput = s.filter { it.isDigit() }.take(4) },
                    label = { Text("分钟（1-9999）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    timerInput.toIntOrNull()?.let { vm.setTimerMinutes(it) }
                    showTimerDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimerDialog = false }) { Text("取消") } },
        )
    }

    if (showBottomBarDialog) {
        AlertDialog(
            onDismissRequest = { showBottomBarDialog = false },
            title = { Text("底栏样式") },
            text = {
                Column {
                    listOf(
                        "floating" to ("药丸悬浮" to "选中项展开文字，悬浮于内容之上"),
                        "rounded" to ("圆角平铺" to "标准导航栏，顶部圆角"),
                        "classic" to ("直角平铺" to "标准导航栏，顶部平角"),
                    ).forEach { (value, pair) ->
                        val (title, subtitle) = pair
                        val checked = state.bottomBarStyle == value ||
                            (value == "rounded" && state.bottomBarStyle == "classic" &&
                                state.bottomBarRounded) ||
                            (value == "classic" && state.bottomBarStyle == "classic" &&
                                !state.bottomBarRounded)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setBottomBarStyle(value)
                                    vm.setBottomBarRounded(value != "classic")
                                    showBottomBarDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = checked, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBottomBarDialog = false }) { Text("关闭") }
            },
        )
    }

    if (showIslandGapDialog) {
        // 拖动时先改本地 state（预览实时跟手），松手才落盘 ——
        // 每拖动一像素写一次 DataStore 会让滑块明显发涩。
        var draftGap by remember { mutableIntStateOf(state.miniPlayerGap) }
        AlertDialog(
            onDismissRequest = {
                vm.setMiniPlayerGap(draftGap)
                com.buddy.ncmusic.ui.util.IslandGapPreview.live = null
                showIslandGapDialog = false
            },
            title = { Text("悬浮岛间距") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 预览：底栏条固定，药丸按间距抬高
                    IslandGapPreview(gapDp = draftGap)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$draftGap dp",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (draftGap != DEFAULT_MINI_PLAYER_GAP) {
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = {
                                draftGap = DEFAULT_MINI_PLAYER_GAP
                                vm.setMiniPlayerGap(DEFAULT_MINI_PLAYER_GAP)
                            }) { Text("恢复默认") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = draftGap.toFloat(),
                        onValueChange = {
                            draftGap = it.roundToInt()
                            // 实时预览：弹窗背后的悬浮岛会跟着升降
                            com.buddy.ncmusic.ui.util.IslandGapPreview.live = draftGap
                        },
                        onValueChangeFinished = {
                            vm.setMiniPlayerGap(draftGap)
                            com.buddy.ncmusic.ui.util.IslandGapPreview.live = null
                        },
                        valueRange = 0f..40f,
                        // 连续滑块：40 个刻度会让轨道铺满小圆点，视觉上"毛糙"。
                        // 取值仍按 1dp 对齐（onValueChange 里 roundToInt）。
                        steps = 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "0 = 紧贴底栏，40 = 明显悬浮；越大越会压住列表内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setMiniPlayerGap(draftGap)
                    com.buddy.ncmusic.ui.util.IslandGapPreview.live = null
                    showIslandGapDialog = false
                }) { Text("完成") }
            },
        )
    }

    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("自定义最大缓存") },
            text = {
                OutlinedTextField(
                    value = cacheInput,
                    onValueChange = { s -> cacheInput = s.filter { it.isDigit() }.take(3) },
                    // 上限与滑块一致（1–10 GB）——
                    // 原先写 1-999，允许输入远超滑块上限的值，造成两处矛盾
                    label = { Text("GB（1-${CACHE_MAX_MB / 1024}）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cacheInput.toIntOrNull()?.let { vm.setMaxCacheMb(it * 1024) }
                    showCacheDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showCacheDialog = false }) { Text("取消") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 单页分组（对齐 SukiSU）：不再保留「分类总览 → 二级页」的两层结构。
            // 12 个细碎卡片按用途收敛为 6 组，组内靠 2dp 细缝连成一张卡，
            // 组标题浮在卡片外 —— 与 SukiSU 设置页同一套层级。

            // ========== 外观 ==========
            GroupBlock("外观") {
                SectionCard(
                    title = "外观",
                    group = SettingsGroup.APPEARANCE,
                    selected = selectedGroup,
                ) {
                    // 配色相关的设置在「主题设置」页集中管理（对齐 SukiSU）：
                    // 颜色模式、色彩风格预览带、色彩标准、自选主题色。
                    // 这里只保留一个入口行，避免首页设置被十几颗选项撑得很长。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTheme)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.Palette)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("主题设置", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "颜色模式、配色风格",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = runCatching {
                                com.buddy.ncmusic.ui.theme.PaletteStyle.of(state.paletteStyle).label
                            }.getOrDefault("色调点缀"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SwitchRow(
                        title = "播放器封面取色",
                        subtitle = "根据封面主色渲染播放器与悬浮岛；关闭后统一使用主题色",
                        checked = state.playerColorEnabled,
                        onCheckedChange = vm::setPlayerColorEnabled,
                        icon = Icons.Default.Album,
                    )

                    GroupDivider()
                    // 底栏样式：三选一。
                    //
                    // 原先把三个单选项平铺在卡片里（占 3 行 + 一个裸标题），
                    // 与「主题设置」这种"一行 → 弹窗选择"的入口不一致，
                    // 也让「外观」卡片比其他分组高出近一倍。
                    // 收敛成一行（右侧显示当前样式），点开再选。
                    val bottomBarLabel = when {
                        state.bottomBarStyle == "floating" -> "药丸悬浮"
                        state.bottomBarStyle == "classic" && !state.bottomBarRounded -> "直角平铺"
                        else -> "圆角平铺"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBottomBarDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.ViewAgenda)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("底栏样式", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "当前：$bottomBarLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    GroupDivider()
                    // 悬浮岛与底栏的间距（垂直方向）。
                    //
                    // 用滑块而不是几个预设档位：间距是连续量，用户往往要边拖边看，
                    // 弹窗里同时给一张示意图，拖动时药丸会跟着抬高。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIslandGapDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.PlayArrow)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("悬浮岛间距", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "当前：${state.miniPlayerGap} dp（与底栏的距离）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IslandGapPreview(gapDp = state.miniPlayerGap, mini = true)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GroupDivider()
                GroupLabel("主页")
                SectionCard(
                    title = "主页组件",
                    group = SettingsGroup.APPEARANCE,
                    selected = selectedGroup,
                ) {
                    SwitchRow(
                        title = "雷达歌单",
                        subtitle = "官方账号级雷达，固定精选 7 张并随账号变化",
                        checked = state.homeShowRadar,
                        onCheckedChange = vm::setHomeShowRadar,
                        icon = Icons.Default.Radar,
                    )
                    SwitchRow(
                        title = "推荐歌单",
                        subtitle = "歌单墙以外的个性化歌单列表",
                        checked = state.homeShowRecommend,
                        onCheckedChange = vm::setHomeShowRecommend,
                        icon = Icons.Default.Recommend,
                    )
                }
                GroupDivider()
                GroupLabel("启动页")
                SectionCard(
                    title = "启动页",
                    group = SettingsGroup.APPEARANCE,
                    selected = selectedGroup,
                ) {
                    // 与其他区块保持同一套边距（标题用 vertical = 8.dp），
                    // 原先这里是 4.dp，导致该行比相邻区块更贴边、看起来"没对齐"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        startPageOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = state.startPage == value,
                                onClick = { vm.setStartPage(value) },
                                label = { Text(label) },
                                // 选中态统一用 primaryContainer，
                                // 与设置行图标、主题页选中条同一套强调色
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            // ========== 播放 ==========
            GroupBlock("播放") {
                SectionCard(
                    title = "音质设置",
                    group = SettingsGroup.AUDIO,
                    selected = selectedGroup,
                ) {
                    // 不折叠；仅保留 WiFi / 流量两档（默认音质已移除）
                    QualitySubGroup("WiFi 下最高音质", state.wifiQuality, vm::setWifiQuality)
                    GroupDivider()
                    QualitySubGroup("移动流量下最高音质", state.mobileQuality, vm::setMobileQuality)
                    Text(
                        text = "播放时按当前网络类型自动选择对应音质上限",
                        // 缩进到内容列，与两档音质的文字左沿对齐
                        modifier = Modifier.padding(start = 70.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GroupDivider()
                GroupLabel("增强")
                SectionCard(
                    title = "播放增强",
                    group = SettingsGroup.PLAYBACK,
                    selected = selectedGroup,
                ) {
                    SwitchRow(
                        title = "音量均衡",
                        icon = Icons.Default.VolumeUp,
                        subtitle = "统一不同歌曲响度（响度增强）",
                        checked = state.volumeNormalize,
                        onCheckedChange = vm::setVolumeNormalize,
                    )
                    // 开关联动的内容用展开/收起，而不是瞬间插入 ——
                    // 卡片高度突变会把下面的行整体推一次，观感很"跳"。
                    AnimatedVisibility(
                        visible = state.volumeNormalize,
                        enter = expandVertically(
                            animationSpec = Md3Motion.spatial(
                                reduceMotion,
                                Md3Motion.Medium2,
                                Md3Motion.EmphasizedDecelerate,
                            ),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = Md3Motion.effects(reduceMotion)),
                        exit = shrinkVertically(
                            animationSpec = Md3Motion.spatial(
                                reduceMotion,
                                Md3Motion.Short4,
                                Md3Motion.EmphasizedAccelerate,
                            ),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = Md3Motion.effects(reduceMotion)),
                    ) {
                        var gain by remember(state.volumeGain) { mutableFloatStateOf(state.volumeGain.toFloat()) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("增益", style = MaterialTheme.typography.bodyMedium)
                            RoundSlider(
                                value = gain,
                                onValueChange = {
                                    gain = it
                                    vm.setVolumeGain(it.toInt())
                                },
                                // 增益保持连续可调（0.01 dB 精度），故不设 steps
                                valueRange = 0f..1200f,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            Text(
                                text = "%.1f dB".format(gain / 100f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    GroupDivider()
                    SwitchRow(
                        title = "均衡器（EQ）",
                        icon = Icons.Default.Equalizer,
                        subtitle = "开关总控，点击下方进入曲线调节",
                        checked = state.eqEnabled,
                        onCheckedChange = vm::setEqEnabled,
                    )
                    // 缩进到内容列（70dp = 40dp 图标 + 14dp 间距 + 16dp 卡边距）：
                    // 原先左对齐 16dp，与上下的图标列错开，看起来像一行游离的链接
                    TextButton(
                        onClick = onOpenEq,
                        modifier = Modifier.padding(start = 66.dp),
                    ) { Text("打开均衡器 ›") }
                    GroupDivider()
                    SwitchRow(
                        title = "USB 独占输出",
                        icon = Icons.Default.Usb,
                        subtitle = usbStatus.ifBlank { "通过 USB 音频设备独占输出（OTG）" },
                        checked = state.usbExclusive,
                        onCheckedChange = vm::setUsbExclusive,
                    )
                    // 解码模式：同样收敛为一行 + 弹窗（原来 3 行）
                    GroupDivider()
                    var showDecodeDialog by remember { mutableStateOf(false) }
                    val decodeLabel = if (state.decodeMode == "soft") "软件解码" else "硬件解码"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDecodeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.Memory)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("解码模式", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "当前：$decodeLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showDecodeDialog) {
                        AlertDialog(
                            onDismissRequest = { showDecodeDialog = false },
                            title = { Text("解码模式") },
                            text = {
                                Column {
                                    listOf("hard" to "硬件解码", "soft" to "软件解码").forEach { (v, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    vm.setDecodeMode(v)
                                                    showDecodeDialog = false
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(selected = state.decodeMode == v, onClick = null)
                                            Spacer(Modifier.width(12.dp))
                                            Text(label, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showDecodeDialog = false }) { Text("关闭") }
                            },
                        )
                    }

                    GroupDivider()
                    SwitchRow(
                        title = "切歌淡入淡出",
                        icon = Icons.Default.SwapHoriz,
                        subtitle = "切换歌曲时音量渐变过渡，衔接更自然",
                        checked = state.crossfadeEnabled,
                        onCheckedChange = vm::setCrossfadeEnabled,
                    )
                    AnimatedVisibility(
                        visible = state.crossfadeEnabled,
                        enter = expandVertically(
                            animationSpec = Md3Motion.spatial(
                                reduceMotion,
                                Md3Motion.Medium2,
                                Md3Motion.EmphasizedDecelerate,
                            ),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = Md3Motion.effects(reduceMotion)),
                        exit = shrinkVertically(
                            animationSpec = Md3Motion.spatial(
                                reduceMotion,
                                Md3Motion.Short4,
                                Md3Motion.EmphasizedAccelerate,
                            ),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = Md3Motion.effects(reduceMotion)),
                    ) {
                        Text(
                            "淡入淡出时长：${state.crossfadeSeconds} 秒",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        RoundSlider(
                            value = state.crossfadeSeconds.toFloat(),
                            onValueChange = { vm.setCrossfadeSeconds(it.toInt().coerceIn(1, 8)) },
                            valueRange = 1f..8f,
                            steps = 6,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                GroupDivider()
                GroupLabel("定时关闭")
                SectionCard(
                    title = "定时关闭",
                    group = SettingsGroup.PLAYBACK,
                    selected = selectedGroup,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.Timer)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = if (state.timerMinutes <= 0) "当前未开启"
                            else "${state.timerMinutes} 分钟后停止播放",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.timerMinutes > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                timerInput = if (state.timerMinutes > 0) state.timerMinutes.toString() else ""
                                showTimerDialog = true
                            },
                        ) { Text("自定义…") }
                    }
                    RoundSlider(
                        value = state.timerMinutes.toFloat().coerceIn(0f, 180f),
                        // 值由 store 驱动；步长交给 Slider 的 steps 处理，
                        // 避免在 onValueChange 里再量化导致「滑块位置与数字不同步」
                        onValueChange = { vm.setTimerMinutes(it.toInt()) },
                        valueRange = 0f..180f,
                        steps = 35,                       // 0,5,10,…,180 共 37 个值
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 标签必须落在**等距刻度**上：轨道分 4 段即 0/60/120/180。
                        // 原先写 关闭/30/60/120/180（5 个）与滑块实际位置完全对不上
                        Text("关闭", style = MaterialTheme.typography.labelSmall)
                        Text("60", style = MaterialTheme.typography.labelSmall)
                        Text("120", style = MaterialTheme.typography.labelSmall)
                        Text("180 分钟", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ========== 歌词 ==========
            GroupBlock("歌词") {
                SectionCard(
                    title = "歌词",
                    group = SettingsGroup.LYRIC,
                    selected = selectedGroup,
                ) {
                    SwitchRow(
                        title = "自动对齐当前歌词",
                        icon = Icons.Default.CenterFocusStrong,
                        subtitle = "进入歌词页时自动定位到正在播放的歌词；滑动浏览后停止操作 3 秒自动切回",
                        checked = state.lyricAutoAlign,
                        onCheckedChange = vm::setLyricAutoAlign,
                    )
                }
                GroupDivider()
                GroupLabel("桌面歌词")
                SectionCard(
                    title = "桌面歌词",
                    group = SettingsGroup.LYRIC,
                    selected = selectedGroup,
                ) {
                    SwitchRow(
                        title = "歌词悬浮窗",
                        icon = Icons.Default.Subtitles,
                        subtitle = overlayStatus.ifBlank { "在桌面显示当前歌词（需悬浮窗权限）" },
                        checked = state.lyricOverlay,
                        onCheckedChange = vm::setLyricOverlay,
                    )

                    GroupDivider()
                    SwitchRow(
                        title = "锁定歌词位置",
                        icon = Icons.Default.Lock,
                        subtitle = "锁定后桌面歌词无法拖动（也可点悬浮窗左侧锁图标切换）",
                        checked = state.lyricLocked,
                        onCheckedChange = vm::setLyricLocked,
                    )

                    GroupDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.Palette)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("歌词颜色", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "桌面歌词的文字颜色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // 7 颗色圈 × 54dp + 边距 = 378dp，超出内容宽（约 320dp），
                    // 原先用 Row 排列直接溢出到屏幕外（截图里绿色只剩半个）。
                    // FlowRow 会自动折行，窄屏也不会被裁。
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 62.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // state.lyricColor 已在数据层归一化（0 -> 默认色），
                        // 这里直接比较即可 —— 无需重复防御，
                        // 否则归一化规则会散落在多处，改一处就漏一处。
                        lyricColors.forEach { (argb, label) ->
                            val selected = state.lyricColor == argb

                            // 选中态改为「外环 + 留白 + 圆点」，而不是在圆点上直接描粗边。
                            //
                            // 原实现是给 32dp 的圆点本体画 3dp border ——
                            // border 居中于边界，会**向内侵占约 1.5dp**，
                            // 于是选中时颜色圆点看起来反而变小了；
                            // 且 3dp 的品牌色环紧贴圆点颜色，两种颜色直接相切，
                            // 观感很跳。
                            //
                            // 现在把「指示环」与「颜色本身」分成两层，
                            // 中间留出 5dp 呼吸，颜色圆点尺寸在任何状态下都恒定不变。
                            // 环色用 onSurface（中性色）而非主题色 ——
                            // 圆点本身已有 7 种色相，再加一个品牌红只会更杂。
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(48.dp)
                                    .clickable { vm.setLyricColor(argb) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = CircleShape,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(argb))
                                            .then(
                                                // 未选中时给一层细描边：
                                                // 白色圆点在浅色背景上否则会"消失"
                                                if (selected) {
                                                    Modifier
                                                } else {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outlineVariant,
                                                        shape = CircleShape,
                                                    )
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.FormatSize)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("歌词字号", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "当前 ${state.lyricFontSize} sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RoundSlider(
                            value = state.lyricFontSize.toFloat(),
                            onValueChange = { vm.setLyricFontSize(it.toInt()) },
                            valueRange = 12f..34f,
                            steps = 21,                   // 12,13,…,34 共 23 个值
                            modifier = Modifier.width(140.dp),
                        )
                    }

                    Text(
                        text = "提示：桌面歌词无背景，可直接拖动调整位置（位置自动保存）",
                        modifier = Modifier.padding(start = 70.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ========== 存储 ==========
            GroupBlock("存储") {
                val dirPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    uri?.let {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        vm.setCacheDirUri(it.toString())
                    }
                }
                SectionCard(
                    title = "缓存位置",
                    group = SettingsGroup.STORAGE,
                    selected = selectedGroup,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 与其他行一致：40dp 方形图标位（此前这一行没有图标，
                        // 在同一张卡里显得"凹进去"一格）
                        IconTile(icon = Icons.Default.FolderOpen)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("下载 / 缓存目录", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = state.cacheDirUri.ifBlank { "默认（应用私有目录）" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    Row(Modifier.padding(start = 66.dp)) {
                        TextButton(onClick = { dirPicker.launch(null) }) { Text("修改位置…") }
                        if (state.cacheDirUri.isNotBlank()) {
                            TextButton(onClick = { vm.setCacheDirUri("") }) { Text("恢复默认") }
                        }
                    }
                }
                GroupDivider()
                GroupLabel("缓存")
                SectionCard(
                    title = "缓存",
                    group = SettingsGroup.STORAGE,
                    selected = selectedGroup,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.DeleteSweep)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("当前缓存", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = formatSize(cacheSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = vm::clearCache) { Text("清除缓存") }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = Icons.Default.DataUsage)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "最大缓存大小",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (state.maxCacheMb >= 1024) {
                                "%.1f GB".format(state.maxCacheMb / 1024f)
                            } else {
                                "${state.maxCacheMb} MB"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // 范围取 1GB..10GB、每 1GB 一档。
                    //
                    // 修复「滑块与底部数字对不上」：原先刻度标签用 SpaceBetween 等距排列，
                    // 但取值 128/2048/5120/10240 并非等距（2GB 实际在 19% 位置，标签却画在 33%），
                    // 于是标签与滑块位置完全错位。改为等距取值后，标签与刻度严格对应。
                    RoundSlider(
                        value = state.maxCacheMb.toFloat()
                            .coerceIn(CACHE_MIN_MB.toFloat(), CACHE_MAX_MB.toFloat()),
                        onValueChange = { vm.setMaxCacheMb(it.toInt()) },
                        valueRange = CACHE_MIN_MB.toFloat()..CACHE_MAX_MB.toFloat(),
                        steps = 8,                        // 1,2,…,10 GB 共 10 档
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 等距四档：1 / 4 / 7 / 10 GB —— 与滑块位置严格对应
                        Text("1 GB", style = MaterialTheme.typography.labelSmall)
                        Text("4 GB", style = MaterialTheme.typography.labelSmall)
                        Text("7 GB", style = MaterialTheme.typography.labelSmall)
                        Text("10 GB", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            cacheInput = (state.maxCacheMb / 1024).takeIf { it > 0 }?.toString() ?: ""
                            showCacheDialog = true
                        },
                        modifier = Modifier.padding(start = 66.dp),
                    ) { Text("精确输入…") }
                }
            }

            // ========== 账号 ==========
            GroupBlock("账号") {
            SectionCard(
                title = "账号",
                group = SettingsGroup.ACCOUNT,
                selected = selectedGroup,
            ) {
                if (state.isLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(
                            url = state.avatarUrl,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            contentDescription = state.nickname,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.nickname.ifBlank { "网易云用户" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(AppShapes.pill)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "Lv.${if (state.level > 0) state.level else 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        OutlinedButton(onClick = vm::logout) { Text("退出登录") }
                    }
                } else {
                    Text(
                        text = "未登录",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            }

            // ========== 关于 ==========
            GroupBlock("关于") {
            SectionCard(
                title = "关于",
                group = SettingsGroup.ABOUT,
                selected = selectedGroup,
            ) {
                // 概要信息 + 入口，点击进入详细关于页（项目仓库 / 许可等）
                AboutRow("应用名称", "NCMusic")
                // 版本号直接读 BuildConfig：原先靠包管理器查询的 appVersion
                // 变量在重构中被删掉，这里回归最稳的来源（编译期常量，无 IO）
                AboutRow(
                    "版本",
                    "${com.buddy.ncmusic.BuildConfig.VERSION_NAME} " +
                        "(${com.buddy.ncmusic.BuildConfig.VERSION_CODE})",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAbout)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "查看详细信息",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            }
            Spacer(Modifier.size(32.dp))
        }
    }
}

/**
 * 一组设置项：**唯一的卡片容器**（对齐 SukiSU 的 SettingsGroup）。
 *
 * 结构：组标题浮在卡片外 → 一张 `surfaceContainer` 圆角卡 → 组内多个
 * [SectionCard] 内容块用 1dp 细线分隔。
 *
 * 之前是每 3~6 行就一张独立卡片（一屏能出现 3 张），层级全靠标题文字区分，
 * 滚动时"卡中卡"的观感很明显。收敛为「每组一张卡」后，
 * 页面只剩下 6 个视觉块，与 SukiSU 的分组节奏一致。
 */
@Composable
private fun GroupBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.of(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            content()
        }
    }
}

/**
 * 组内小节标签。
 *
 * 一组里的内容块各管一个子话题（外观组下有「主页」「启动页」），
 * 光靠一条细线分隔，用户读不出"这里换话题了"。
 * 标签用 labelMedium + primary，比正文小一档 —— 层级在组标题之下。
 */
/**
 * 悬浮岛间距示意图。
 *
 * 底部浅色条代表底栏，上方圆角胶囊代表悬浮岛，两者之间的距离按 [gapDp] 等比映射。
 * [mini] = true 时缩到 34×22，用于设置行右侧的即时提示。
 */
@Composable
private fun IslandGapPreview(gapDp: Int, mini: Boolean = false) {
    val w = if (mini) 34.dp else 200.dp
    val h = if (mini) 22.dp else 96.dp
    val barH = if (mini) 6.dp else 26.dp
    val pillH = if (mini) 8.dp else 22.dp
    // 间距映射：40dp 对应可抬升高度的 100%
    val travel = if (mini) 8f else 52f
    val lift = (gapDp.coerceIn(0, 40) / 40f) * travel
    Box(
        modifier = Modifier
            .size(w, h)
            .clip(RoundedCornerShape(if (mini) 6.dp else 14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // 底栏示意
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barH)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        )
        // 悬浮岛示意：底部对齐后再向上偏移 gap
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = if (mini) 5.dp else 28.dp,
                    end = if (mini) 5.dp else 28.dp,
                )
                .padding(bottom = barH + lift.dp)
                .fillMaxWidth()
                .height(pillH)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 组内分隔线：左侧避让图标块，与行内容起始齐平 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

/**
 * 组内的一个内容块。
 *
 * 只负责「按分类过滤」与内边距，**不再**自带标题与底色 ——
 * 标题与卡片已上移到 [GroupBlock]。`title` 仍保留在签名里，
 * 因为它是这一块内容在语义上的名字（也便于后续需要时复用）。
 */
@Composable
private fun SectionCard(
    title: String,
    group: SettingsGroup,
    selected: SettingsGroup,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 「全部」视图显示所有分类；否则只显示当前分类
    if (selected != SettingsGroup.ALL && selected != group) return
    // title 目前仅用于语义；加个引用避免"未使用参数"的告警噪音
    @Suppress("UNUSED_EXPRESSION")
    title
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        content()
    }
}

/**
 * 设置行左侧的图标方块（SukiSU / KernelSU 风格）。
 *
 * 统一 40dp 圆角方块 + `primaryContainer` 底色 + `onPrimaryContainer` 图标，
 * 让每一行有稳定的视觉锚点；相比纯文字行，浏览时更容易按图标定位。
 */
@Composable
private fun IconTile(
    icon: ImageVector,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(AppShapes.of(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    // 禁用态：控件变灰且不可交互（用于"当前模式下该选项无意义"的场景）
    enabled: Boolean = true,
    // 左侧图标（可选）：传了才渲染 IconTile，保持行列对齐
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon = icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                // 禁用时降透明度：让"不可用"在视觉上先于交互被感知
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        // 拇指内嵌 ✓ / ✗ 图标并带淡入淡出：状态一眼可辨，无需看轨道颜色
        //
        // 配色严格对齐 m3.material.io/components/switch 的角色映射：
        //   选中 —— track=primary / thumb=onPrimary / icon=onPrimaryContainer
        //   未选中 —— track=surfaceContainerHighest / thumb=outline / icon=surfaceContainerHighest
        Switch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = {
                AnimatedContent(
                    targetState = checked,
                    transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(110)) },
                    label = "switch_thumb",
                ) { on ->
                    Icon(
                        // ⚠️ 原先用 Add(＋) 旋转 45° 想得到 ✓ —— 但加号转 45° 是「×」，
                        // 于是开启/关闭两个状态都显示成叉，无法区分。
                        // material-icons-extended 已依赖，直接用 Check。
                        imageVector = if (on) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            },
            colors = SwitchDefaults.colors(
                // ---- 选中态 ----
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                checkedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // ---- 未选中态 ----
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}


/** 音质子分组（默认 / WiFi / 流量） */
/**
 * 设置页统一滑块 —— **MD3 规范样式**：
 * - thumb 为**竖条**（4×36dp 圆角矩形），而非圆点
 * - 轨道内部另有一个**跟随进度的小圆点**，增强刻度感
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
        // thumb：MD3 竖条
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        },
        // 轨道：完全自绘，确保圆点只有**一套**。
        //
        // ⚠️ 不能对 `SliderDefaults.Track` 再叠一层自己的点：
        // 当 `steps > 0` 时，Material3 的原生 Track 会**自行绘制刻度点**，
        // 与手动添加的点重叠后会出现「大小不一、排列错乱」的观感。
        // 因此这里整体自绘；`steps` 仅用于让 Slider 吸附取值，不再借它画点。
        track = { sliderState ->
            val fraction = if (valueRange.endInclusive > valueRange.start) {
                ((sliderState.value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            } else {
                0f
            }
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val dotOnActive = MaterialTheme.colorScheme.onPrimary
            val dotOnInactive = MaterialTheme.colorScheme.onSurfaceVariant

            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val barHeight = 10.dp.toPx()
                val corner = CornerRadius(barHeight / 2f)
                val centerY = size.height / 2f
                val filledWidth = size.width * fraction

                // 1) 底轨
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - barHeight / 2f),
                    size = Size(size.width, barHeight),
                    cornerRadius = corner,
                )
                // 2) 已填充段
                if (filledWidth > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, centerY - barHeight / 2f),
                        size = Size(filledWidth, barHeight),
                        cornerRadius = corner,
                    )
                }
                // 3) 等距圆点 —— 全程唯一的一套点
                val dotRadius = 2.5.dp.toPx()
                repeat(TRACK_DOT_COUNT) { i ->
                    // 位置用 i/(N-1)：首点落在 0%、末点落在 100%，
                    // 与下方 Arrangement.SpaceBetween 的刻度标签严格对应。
                    // （原先用 (i+0.5)/N，点全部落在标签之间，两侧都在错位）
                    val cx = size.width * i / (TRACK_DOT_COUNT - 1).toFloat()
                    drawCircle(
                        color = if (cx <= filledWidth) dotOnActive else dotOnInactive,
                        radius = dotRadius,
                        center = Offset(cx, centerY),
                    )
                }
            }
        },
    )
}

/**
 * 轨道上等距圆点的个数。
 *
 * 取 5 与 MD3 停靠点滑块的观感一致：点距足够疏朗，
 * 在窄轨道上也不会糊成一条线。
 */
private const val TRACK_DOT_COUNT = 4

/** 关于页信息行 */
@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualitySubGroup(
    title: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // 一行摘要 + 弹窗选择。
    //
    // 原先是把 7 个档位全部平铺（每档 2~3 行），单是这个分组就占 2 屏多，
    // 「播放」一组能滚到 4 屏 —— 设置页最重的负担就在这里。
    // SukiSU 的设置项一律是"一行摘要 → 点开再选"，这里对齐同一模式。
    var expanded by remember { mutableStateOf(false) }
    val current = AudioQuality.visibleDescending.firstOrNull { it.level == selected }
        ?: AudioQuality.visibleDescending.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShapes.of(12.dp))
                // 与 IconTile 同一套配色（primaryContainer / onPrimaryContainer）：
                // 原先用 surfaceContainerHighest + primary 文字，在同一张卡里
                // 是唯一一块"灰底蓝字"，和上下行的蓝底图标对不上。
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = current?.shortLabel.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = buildString {
                    append(current?.label.orEmpty())
                    current?.kbps?.let { if (it > 0) append(" · 最高 $it kbps") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        current?.vip?.let { if (it != VipTier.NONE) VipBadge(it) }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AudioQuality.visibleDescending.forEach { q ->
                        QualityOptionRow(
                            quality = q,
                            selected = q.level == selected,
                            onClick = {
                                onSelect(q.level)
                                expanded = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text("关闭") }
            },
        )
    }
}

/**
 * 音质选项列表 —— 视觉语言与播放器音质面板（QualitySheet）保持一致。
 */
@Composable
private fun QualityOptionList(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        AudioQuality.visibleDescending.forEach { q ->
            QualityOptionRow(
                quality = q,
                selected = q.level == selected,
                onClick = { onSelect(q.level) },
            )
        }
        Spacer(Modifier.size(4.dp))
    }
}

/**
 * 单个音质选项。
 *
 * 与播放器面板（`QualitySheet.QualityRow`）同一套结构：
 * **短标识圆形图标 + 名称 / 英文名 / VIP 徽标 + 描述 / 规格 / 码率**，
 * 右侧是单选圆点（设置页是"偏好"，需要明确的单选语义）。
 *
 * 比播放器多显示一项**码率**：设置页是在挑选"上限"，
 * 码率是用户判断档位高低最直接的依据。
 */
@Composable
private fun QualityOptionRow(
    quality: AudioQuality,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 短标识（与播放器同尺寸 40dp，保证两处观感一致）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = quality.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quality.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (quality.englishName.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = quality.englishName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (quality.vip != VipTier.NONE) {
                    Spacer(Modifier.width(6.dp))
                    VipBadge(quality.vip)
                }
            }

            // 第二行：描述 + 规格 + 码率。
            //
            // ⚠️ 去重：官方描述本身常已含规格与码率
            // （如「极高」的 description 是「近CD音质的细节体验，最高320kbps」），
            // 再无条件追加就会出现「最高320kbps，320 kbps」这种重复。
            val desc = quality.description
            // 描述里已经出现码率（"320kbps" 或 "2000 kbps"）时，
            // specText 与 kbps 都不再追加 —— 否则会出现
            // 「最高320kbps，320 kbps」这种重复（注意空格差异会导致包含判断失配）。
            val descHasKbps = desc.contains("kbps", ignoreCase = true)
            val detail = buildList {
                if (desc.isNotBlank()) add(desc)
                val specIsKbps = quality.specText.contains("kbps", ignoreCase = true)
                if (quality.specText.isNotBlank() &&
                    !desc.contains(quality.specText) &&
                    !(descHasKbps && specIsKbps)
                ) {
                    add(quality.specText)
                }
                if (quality.kbps > 0 && !descHasKbps && !specIsKbps) {
                    add("${quality.kbps} kbps")
                }
            }.joinToString("，")
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun qualityLabelOf(q: String): String =
    AudioQuality.of(q)?.let { "${it.label}（${it.specText}）" } ?: q
