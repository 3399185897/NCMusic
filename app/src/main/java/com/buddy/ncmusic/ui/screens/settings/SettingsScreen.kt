package com.buddy.ncmusic.ui.screens.settings

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

    fun setThemeMode(v: String) { viewModelScope.launch { prefs.setThemeMode(v) } }
    fun setDynamicColor(v: Boolean) {
        viewModelScope.launch {
            prefs.setDynamicColor(v)
            // 开启莫奈取色时清空自定义色，二者互斥
            if (v) prefs.setCustomColor(0L)
        }
    }

    fun setCustomColor(v: Long) {
        viewModelScope.launch {
            prefs.setCustomColor(v)
            // 选择自定义色时自动关闭莫奈取色
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
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.userState.collectAsState(initial = UserState())
    val cacheSize by vm.cacheSize.collectAsState()
    val eqTick by vm.eqTick.collectAsState()
    val usbStatus by vm.usbStatus.collectAsState()
    val overlayStatus by vm.overlayStatus.collectAsState()

    val context = LocalContext.current

    var showTimerDialog by remember { mutableStateOf(false) }
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
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---------- 外观 ----------
            SectionCard("外观") {
                // 深浅模式横向排列
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    themeOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = state.themeMode == value,
                            onClick = { vm.setThemeMode(value) },
                            label = { Text(label) },
                        )
                    }
                }
                SwitchRow(
                    title = "莫奈动态取色",
                    subtitle = "跟随系统壁纸取色（Android 12+）",
                    checked = state.dynamicColor,
                    onCheckedChange = vm::setDynamicColor,
                )
                var colorExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { colorExpanded = !colorExpanded }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "自定义主题色",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (colorExpanded) "收起 ▲" else "展开 ▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (colorExpanded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    presetColors.forEach { (argb, _) ->
                        val selected = state.customColor == argb
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { vm.setCustomColor(if (selected) 0L else argb) },
                        )
                    }
                }
                Text(
                    text = "再次点击已选颜色可恢复默认；与莫奈取色互斥",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }

            // ---------- 音质（合并为一个可展开控件） ----------
            SectionCard("音质设置") {
                var qualityExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { qualityExpanded = !qualityExpanded }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "默认：${qualityLabelOf(state.quality)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "WiFi ${qualityLabelOf(state.wifiQuality)} · 流量 ${qualityLabelOf(state.mobileQuality)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (qualityExpanded) "收起 ▲" else "展开 ▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (qualityExpanded) {
                    QualitySubGroup("默认音质", state.quality, vm::setQuality)
                    QualitySubGroup("WiFi 下最高音质", state.wifiQuality, vm::setWifiQuality)
                    QualitySubGroup("移动流量下最高音质", state.mobileQuality, vm::setMobileQuality)
                }
            }

            // ---------- 播放增强 ----------
            SectionCard("播放增强") {
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
            }

            // ---------- 桌面歌词 ----------
            SectionCard("桌面歌词") {
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Text(
                    text = "提示：桌面歌词无背景，可直接拖动调整位置（位置自动保存）",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 定时关闭 ----------
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
            SectionCard("缓存位置") {
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

            SectionCard("定时关闭") {
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
                    onValueChange = { vm.setTimerMinutes((it / 5).toInt() * 5) },
                    valueRange = 0f..180f,
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

            // ---------- 启动页 ----------
            SectionCard("启动页") {
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



            // ---------- 缓存 ----------
            SectionCard("缓存") {
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
                RoundSlider(
                    value = state.maxCacheMb.toFloat().coerceIn(128f, 10240f),
                    onValueChange = { vm.setMaxCacheMb((it / 128).toInt() * 128) },
                    valueRange = 128f..10240f,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("128 MB", style = MaterialTheme.typography.labelSmall)
                    Text("2 GB", style = MaterialTheme.typography.labelSmall)
                    Text("5 GB", style = MaterialTheme.typography.labelSmall)
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
            if (state.isLoggedIn) {
                SectionCard("账号") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        OutlinedButton(onClick = vm::logout) { Text("退出登录") }
                    }
                }
            }

            // ---------- 关于（入口，置于最底部） ----------
            val appVersion = remember {
                runCatching {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .versionName
                }.getOrNull().orEmpty().ifBlank { "1.0.0" }
            }
            SectionCard("关于") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAbout)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("关于 NCMusic", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "版本 $appVersion · 开源许可",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(32.dp))
        }
    }
}

/** 设置分组卡片：统一背景、圆角与标题色，提升模块辨识度 */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
/** 圆形滑块：覆盖 Material3 默认的竖条 thumb */
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
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        },
    )
}

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
