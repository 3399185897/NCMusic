package com.buddy.ncmusic.ui.screens.settings

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.font.FontWeight
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.util.ApiResult
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import com.buddy.ncmusic.ui.theme.AppShapes
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.SwitchDefaults
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

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = withContext(Dispatchers.IO) { dirSize(context.cacheDir) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            refreshCacheSize()
        }
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 深色模式 / 自定义主题色 / 莫奈取色 —— 三者互斥：
     * - 开「深色模式」→ 要固定深色配色，关闭莫奈取色并清空自定义色
     * - 开「跟随系统」→ 保留自动取色能力
     * - 关「深色模式」→ 回退到浅色（light）
     */
    fun setThemeMode(v: String) {
        viewModelScope.launch {
            prefs.setThemeMode(v)
            if (v == "dark" || v == "light") {
                prefs.setDynamicColor(false)
                prefs.setCustomColor(0L)
            }
        }
    }

    fun setDynamicColor(v: Boolean) {
        viewModelScope.launch {
            prefs.setDynamicColor(v)
            // 开启莫奈取色 → 清空自定义色，并把深浅模式交还系统
            if (v) {
                prefs.setCustomColor(0L)
                prefs.setThemeMode("system")
            }
        }
    }

    fun setCustomColor(v: Long) {
        viewModelScope.launch {
            prefs.setCustomColor(v)
            // 选择自定义色 → 关闭莫奈取色
            if (v != 0L) prefs.setDynamicColor(false)
        }
    }
    fun setQuality(v: String) { viewModelScope.launch { prefs.setQuality(v) } }
    fun setWifiQuality(v: String) { viewModelScope.launch { prefs.setWifiQuality(v) } }
    fun setMobileQuality(v: String) { viewModelScope.launch { prefs.setMobileQuality(v) } }
    fun setStartPage(v: String) { viewModelScope.launch { prefs.setStartPage(v) } }
    fun setMaxCacheMb(v: Int) { viewModelScope.launch { prefs.setMaxCacheMb(v) } }

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

    fun setPlayerColorEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPlayerColorEnabled(enabled) }
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

private val qualityOptions = listOf(
    "standard" to "标准（128k）",
    "higher" to "较高（192k）",
    "exhigh" to "极高（320k）",
    "lossless" to "无损（FLAC）",
    "hires" to "Hi-Res",
    "jymaster" to "超清母带",
)

private val startPageOptions = listOf(
    "home" to "推荐",
    "discover" to "发现",
    "library" to "我的",
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

private val lyricColors = listOf(
    0xFFFFFFFF to "白",
    0xFF000000 to "黑",
    0xFFFFD54F to "金",
    0xFF81D4FA to "蓝",
    0xFFF48FB1 to "粉",
    0xFFA5D6A7 to "绿",
    0xFFFF8A65 to "橙",
)

private val presetColors = listOf(
    0xFFE53935 to "红",
    0xFFD81B60 to "玫红",
    0xFFEC407A to "粉",
    0xFF8E24AA to "紫",
    0xFF5E35B1 to "深紫",
    0xFF3949AB to "靛蓝",
    0xFF1E88E5 to "蓝",
    0xFF039BE5 to "天蓝",
    0xFF00ACC1 to "青",
    0xFF00897B to "蓝绿",
    0xFF43A047 to "绿",
    0xFF7CB342 to "嫩绿",
    0xFFC0CA33 to "黄绿",
    0xFFFDD835 to "黄",
    0xFFFB8C00 to "橙",
    0xFFF4511E to "深橙",
    0xFF6D4C41 to "棕",
    0xFF546E7A to "蓝灰",
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
    /** 点击分类卡片时回调（由导航层跳转到对应子页） */
    onOpenGroup: (SettingsGroup) -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.userState.collectAsState(initial = UserState())
    val cacheSize by vm.cacheSize.collectAsState()
    val eqTick by vm.eqTick.collectAsState()
    val usbStatus by vm.usbStatus.collectAsState()
    val overlayStatus by vm.overlayStatus.collectAsState()

    val context = LocalContext.current

    var showTimerDialog by remember { mutableStateOf(false) }
    // 当前分类固定为导航传入的分类（不再用顶部标签切换）
    val selectedGroup = initialGroup
    var timerInput by remember { mutableStateOf("") }
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

    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("自定义最大缓存") },
            text = {
                OutlinedTextField(
                    value = cacheInput,
                    onValueChange = { s -> cacheInput = s.filter { it.isDigit() }.take(3) },
                    label = { Text("GB（1-999）") },
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
                // 总览页显示「设置」；子页显示该分类名称
                text = if (selectedGroup == SettingsGroup.ALL) "设置" else selectedGroup.label,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---------- 分类总览（仅总览页显示）----------
            // 总览页**只**呈现分类卡片，不渲染任何具体设置项 —— 与 PixelPlayer 结构一致
            if (selectedGroup == SettingsGroup.ALL) {
                SettingsOverview(onOpenGroup = { onOpenGroup(it) })
            } else {
            // ---------- 外观 ----------
            SectionCard(
                title = "外观",
                group = SettingsGroup.APPEARANCE,
                selected = selectedGroup,
            ) {
                // 深色模式：两个开关（深色模式 / 跟随系统）
                // 三者互斥：深色模式、莫奈取色、自定义主题色
                SwitchRow(
                    title = "深色模式",
                    subtitle = "固定使用深色配色（关闭则使用浅色）",
                    checked = state.themeMode == "dark",
                    onCheckedChange = { on -> vm.setThemeMode(if (on) "dark" else "light") },
                )
                SwitchRow(
                    title = "跟随系统",
                    subtitle = "深浅色随系统设置自动切换",
                    checked = state.themeMode == "system",
                    onCheckedChange = { on ->
                        if (on) vm.setThemeMode("system")
                        // 关闭「跟随系统」时回到浅色，避免状态悬空
                        else vm.setThemeMode("light")
                    },
                )
                Text(
                    text = "深色模式、自定义主题色、莫奈取色三者互斥",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "莫奈动态取色",
                    subtitle = "跟随系统壁纸取色（Android 12+）",
                    checked = state.dynamicColor,
                    onCheckedChange = vm::setDynamicColor,
                )
                Text(
                    "自定义主题色",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    presetColors.forEach { (argb, _) ->
                        val selected = state.customColor == argb
                        // 记录该色块在 root 中的中心坐标，用于颜色扩散动画起点
                        var centerInRoot by remember { mutableStateOf(Offset.Zero) }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .onGloballyPositioned { coords ->
                                    centerInRoot = coords.positionInRoot() +
                                        Offset(coords.size.width / 2f, coords.size.height / 2f)
                                }
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    // 从点击处扩散新颜色
                                    ColorRevealState.launch(centerInRoot, Color(argb))
                                    vm.setCustomColor(if (selected) 0L else argb)
                                },
                        )
                    }
                }
                Text(
                    text = "再次点击已选颜色可恢复默认",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SwitchRow(
                    title = "播放器封面取色",
                    subtitle = "根据封面主色渲染播放器与悬浮岛；关闭后统一使用主题色",
                    checked = state.playerColorEnabled,
                    onCheckedChange = vm::setPlayerColorEnabled,
                )

            // ---------- 启动页（自「行为」页移入）----------
            SectionCard(
                title = "启动页",
                group = SettingsGroup.APPEARANCE,
                selected = selectedGroup,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    startPageOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = state.startPage == value,
                            onClick = { vm.setStartPage(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }



            }

            // ---------- 音质（合并为一个可展开控件） ----------
            SectionCard(
                title = "音质设置",
                group = SettingsGroup.AUDIO,
                selected = selectedGroup,
            ) {
                // 不折叠；仅保留 WiFi / 流量两档（默认音质已移除）
                QualitySubGroup("WiFi 下最高音质", state.wifiQuality, vm::setWifiQuality)
                QualitySubGroup("移动流量下最高音质", state.mobileQuality, vm::setMobileQuality)
                Text(
                    text = "播放时按当前网络类型自动选择对应音质上限",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 播放增强 ----------
            SectionCard(
                title = "播放增强",
                group = SettingsGroup.PLAYBACK,
                selected = selectedGroup,
            ) {
                SwitchRow(
                    title = "音量均衡",
                    subtitle = "统一不同歌曲响度（响度增强）",
                    checked = state.volumeNormalize,
                    onCheckedChange = vm::setVolumeNormalize,
                )
                if (state.volumeNormalize) {
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
                SwitchRow(
                    title = "均衡器（EQ）",
                    subtitle = "开关总控，点击下方进入曲线调节",
                    checked = state.eqEnabled,
                    onCheckedChange = vm::setEqEnabled,
                )
                TextButton(
                    onClick = onOpenEq,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text("打开均衡器 ›") }
                SwitchRow(
                    title = "USB 独占输出",
                    subtitle = usbStatus.ifBlank { "通过 USB 音频设备独占输出（OTG）" },
                    checked = state.usbExclusive,
                    onCheckedChange = vm::setUsbExclusive,
                )
                Text(
                    "解码模式",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OptionGroup(
                    listOf("hard" to "硬件解码", "soft" to "软件解码"),
                    state.decodeMode,
                    vm::setDecodeMode,
                )

                SwitchRow(
                    title = "切歌淡入淡出",
                    subtitle = "切换歌曲时音量渐变过渡，衔接更自然",
                    checked = state.crossfadeEnabled,
                    onCheckedChange = vm::setCrossfadeEnabled,
                )
                if (state.crossfadeEnabled) {
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

            // ---------- 定时关闭（自「行为」页移入）----------
            // ---------- 歌词 ----------
            SectionCard(
                title = "歌词",
                group = SettingsGroup.LYRIC,
                selected = selectedGroup,
            ) {
                SwitchRow(
                    title = "自动对齐当前歌词",
                    subtitle = "进入歌词页时自动定位到正在播放的歌词；滑动浏览后停止操作 3 秒自动切回",
                    checked = state.lyricAutoAlign,
                    onCheckedChange = vm::setLyricAutoAlign,
                )
            }

            // ---------- 缓存位置 ----------
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                Row(Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = { dirPicker.launch(null) }) { Text("修改位置…") }
                    if (state.cacheDirUri.isNotBlank()) {
                        TextButton(onClick = { vm.setCacheDirUri("") }) { Text("恢复默认") }
                    }
                }
            }

            SectionCard(
                title = "定时关闭",
                group = SettingsGroup.PLAYBACK,
                selected = selectedGroup,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.timerMinutes <= 0) "未开启"
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
                    Text("关闭", style = MaterialTheme.typography.labelSmall)
                    Text("30", style = MaterialTheme.typography.labelSmall)
                    Text("60", style = MaterialTheme.typography.labelSmall)
                    Text("120", style = MaterialTheme.typography.labelSmall)
                    Text("180 分钟", style = MaterialTheme.typography.labelSmall)
                }
            }


            // ---------- 桌面歌词 ----------
            SectionCard(
                title = "桌面歌词",
                group = SettingsGroup.LYRIC,
                selected = selectedGroup,
            ) {
                SwitchRow(
                    title = "歌词悬浮窗",
                    subtitle = overlayStatus.ifBlank { "在桌面显示当前歌词（需悬浮窗权限）" },
                    checked = state.lyricOverlay,
                    onCheckedChange = vm::setLyricOverlay,
                )

                SwitchRow(
                    title = "锁定歌词位置",
                    subtitle = "锁定后桌面歌词无法拖动（也可点悬浮窗左侧锁图标切换）",
                    checked = state.lyricLocked,
                    onCheckedChange = vm::setLyricLocked,
                )

                Text(
                    text = "歌词颜色",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(Modifier.padding(horizontal = 16.dp)) {
                    lyricColors.forEach { (argb, _) ->
                        val selected = state.lyricColor == argb
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(32.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { vm.setLyricColor(argb) },
                        )
                    }
                }

                Text(
                    text = "歌词字号：${state.lyricFontSize} sp",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                RoundSlider(
                    value = state.lyricFontSize.toFloat(),
                    onValueChange = { vm.setLyricFontSize(it.toInt()) },
                    valueRange = 12f..34f,
                    steps = 21,                       // 12,13,…,34 共 23 个值
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Text(
                    text = "提示：桌面歌词无背景，可直接拖动调整位置（位置自动保存）",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 缓存 ----------
            SectionCard(
                title = "缓存",
                group = SettingsGroup.STORAGE,
                selected = selectedGroup,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    value = state.maxCacheMb.toFloat().coerceIn(1024f, 10240f),
                    onValueChange = { vm.setMaxCacheMb(it.toInt()) },
                    valueRange = 1024f..10240f,
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
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text("精确输入…") }
            }

            // ---------- 账号 ----------
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

            // ---------- 关于（入口，置于最底部） ----------
            val appVersion = remember {
                runCatching {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .versionName
                }.getOrNull().orEmpty().ifBlank { "1.0.0" }
            }
            SectionCard(
                title = "关于",
                group = SettingsGroup.ABOUT,
                selected = selectedGroup,
            ) {
                // 概要信息 + 入口，点击进入详细关于页（项目仓库 / 许可等）
                AboutRow("应用名称", "NCMusic")
                AboutRow("版本", appVersion)
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

            Spacer(Modifier.size(32.dp))
            }
        }
    }
}

/** 设置分组卡片：统一背景、圆角与标题色，提升模块辨识度 */
@Composable
private fun SectionCard(
    title: String,
    group: SettingsGroup,
    selected: SettingsGroup,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 「全部」视图显示所有分类；否则只显示当前分类
    if (selected != SettingsGroup.ALL && selected != group) return
    val titleColor = groupAccent(group)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            // MD3 容器色阶：设置分组属于「次要容器」，
            // 使用 surfaceContainerHigh 而非 surfaceVariant ——
            // 后者是 M2 遗留角色，在 MD3 中语义已改为「非主色表面」，用它做卡片底色会导致明度关系失准
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 分类色条：让当前分类一眼可辨
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(AppShapes.of(2.dp))
                        .background(titleColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                )
            }
            content()
        }
    }
}


@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 拇指内嵌 ✓ / ✗ 图标并带淡入淡出：状态一眼可辨，无需看轨道颜色
        //
        // 配色严格对齐 m3.material.io/components/switch 的角色映射：
        //   选中 —— track=primary / thumb=onPrimary / icon=onPrimaryContainer
        //   未选中 —— track=surfaceContainerHighest / thumb=outline / icon=surfaceContainerHighest
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = {
                AnimatedContent(
                    targetState = checked,
                    transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(110)) },
                    label = "switch_thumb",
                ) { on ->
                    Icon(
                        // 核心图标库无 Check，用 Add 旋转 45° 得到等效的 ✓
                        imageVector = if (on) Icons.Default.Add else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier
                            .size(SwitchDefaults.IconSize)
                            .rotate(if (on) 45f else 0f),
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

@Composable
private fun <T> OptionGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == value, onClick = { onSelect(value) })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.size(4.dp))
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
                    val cx = size.width * (i + 0.5f) / TRACK_DOT_COUNT
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
private const val TRACK_DOT_COUNT = 5

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
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    OptionGroup(qualityOptions, selected, onSelect)
}

private fun qualityLabelOf(q: String): String =
    qualityOptions.firstOrNull { it.first == q }?.second ?: q
