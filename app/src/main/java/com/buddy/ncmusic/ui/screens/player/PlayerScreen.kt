package com.buddy.ncmusic.ui.screens.player

import com.buddy.ncmusic.ui.components.ExpressiveLoadingIndicator
import com.buddy.ncmusic.ui.components.DownloadController
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import com.buddy.ncmusic.util.audioSpecText
import com.buddy.ncmusic.util.AudioQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import com.buddy.ncmusic.ui.theme.AppShapes
import com.buddy.ncmusic.ui.components.WaveProgressBar
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.playback.LyricWindowService
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.ui.components.rememberCoverColor
import com.buddy.ncmusic.playback.PlaybackManager.PlayMode
import com.buddy.ncmusic.ui.components.BouncyIconButton
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.QueueMusic
import com.buddy.ncmusic.ui.components.Pause
import com.buddy.ncmusic.ui.components.Repeat
import com.buddy.ncmusic.ui.components.RepeatOne
import com.buddy.ncmusic.ui.components.Shuffle
import com.buddy.ncmusic.ui.components.SkipNext
import com.buddy.ncmusic.ui.components.SkipPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenLyric: () -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    /**
     * 是否展开。由宿主（NavGraph）控制：播放器**常驻组合树**，
     * 展开/收起只是整页位移动画，不涉及页面重建 ——
     * 借鉴 PixelPlayer「统一 Sheet」设计，避免 NavHost 转场期间
     * 「新页面首次组合 + 旧页面仍在组合」的双重开销。
     */
    expanded: Boolean = true,
) {
    val state by PlaybackManager.state.collectAsState()
    val song = state.song
    var qualityMenuOpen by remember { mutableStateOf(false) }
    // 歌曲详情面板（下载 / 分享 / 关于）
    var songDetailSheet by remember { mutableStateOf(false) }
    // 详情面板内的下载音质选择
    var detailQualityPicker by remember { mutableStateOf(false) }
    // 「关于歌曲」信息弹窗
    var showSongInfo by remember { mutableStateOf(false) }

    // 本地 / 已下载曲目：不参与在线音质切换。
    //
    // 判据用 localPath —— 本地扫描与下载都会写入该字段，
    // 而在线歌曲的 localPath 恒为 null。
    val isLocalTrack = song?.localPath?.isNotBlank() == true

    // 音质标签的显示文本。
    //
    // 本地文件没有「音质档位」可言，展示**真实编码格式**（由扩展名推断），
    // 而不是沿用当前设置里的在线音质 —— 否则会出现
    // 「播着 MP3，顶上却写着超清母带」这种误导。
    val qualityText = if (isLocalTrack) {
        val ext = song?.localPath?.substringAfterLast('.', "")?.uppercase().orEmpty()
        if (ext.isNotBlank()) ext else "本地"
    } else {
        qualityLabel(state.quality)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = NCMusicApp.instance.userPreferences
    val userState by prefs.userStateFlow.collectAsState(initial = UserState())
    val overlayOn = userState.lyricOverlay

    /** 开关桌面歌词悬浮窗 */
    fun toggleOverlay() {
        val next = !overlayOn
        scope.launch { prefs.setLyricOverlay(next) }
        if (!next) {
            runCatching { context.stopService(Intent(context, LyricWindowService::class.java)) }
            Toast.makeText(context, "桌面歌词已关闭", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Toast.makeText(context, "请授予「显示在其他应用上层」权限后再次开启", Toast.LENGTH_LONG).show()
        } else {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LyricWindowService::class.java),
                )
            }
            Toast.makeText(context, "桌面歌词已开启", Toast.LENGTH_SHORT).show()
        }
    }

    val coverColor = if (userState.playerColorEnabled) {
        rememberCoverColor(song?.coverUrl)
    } else {
        null
    }
    val currentLyricIndex = remember(state.lyrics, state.position) {
        state.lyrics.indexOfLast { it.time <= state.position }.coerceAtLeast(0)
    }
    val currentLine = state.lyrics.getOrNull(currentLyricIndex)?.text.orEmpty()
    val currentTranslation = state.lyrics.getOrNull(currentLyricIndex)?.translation.orEmpty()
    val nextLine = state.lyrics.getOrNull(currentLyricIndex + 1)?.text.orEmpty()
    val nextTranslation = state.lyrics.getOrNull(currentLyricIndex + 1)?.translation.orEmpty()

    // 整页位移：展开时 translationY = 0，收起时下移出屏。
    //
    // ⚠️ 用 Animatable + 在 graphicsLayer 的 lambda 内读取（见下方），
    // 使动画只驱动**绘制阶段**，每帧只重绘、不重组 —— Compose 动画的性能惯例。
    // 若写成 `val v by animateFloatAsState(...)` 并在组合作用域使用，
    // 每帧都会重组这整个 500+ 行的播放页，必然掉帧。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // ⚠️ maxWidth / maxHeight 是 BoxWithConstraints 的**作用域成员**，
        // 在嵌套的 Column/Row lambda 里无法隐式访问（编译器报
        // "cannot be called with an implicit receiver"）。
        // 因此在作用域内**先取出为普通局部变量**，供下层直接使用。
        val boxMaxWidth = maxWidth
        val boxMaxHeight = maxHeight

        // 封面尺寸：0.34 倍可用高度，限制在 120~260dp。
        // 上限保证大屏上与原有视觉规格一致；下限保证极矮屏仍有可辨识的封面。
        val coverSize = (boxMaxHeight * 0.34f).coerceIn(120.dp, 260.dp)
        val offsetY = remember { Animatable(0f) }

        // ---------- 封面连续转场参数 ----------
        //
        // 目标：从迷你播放器点开时，全屏封面应**从悬浮岛封面的大小长出**，
        // 而不是凭空出现在最终尺寸上 —— 后者会让展开显得"跳"。
        //
        // 做法：按展开进度对封面做**等比缩放 + 淡入**。
        //  进度 0（完全收起）= 悬浮岛封面比例（40dp / coverSize）
        //  进度 1（完全展开）= 1.0
        //
        // 之所以用 graphicsLayer 在**绘制阶段**读取进度：
        // 缩放不参与布局（不影响占位），且每帧只重绘不重组 ——
        // 这是低成本做法，不需要引入 SharedTransitionLayout 那套机制
        // （后者要求两个界面同时存在于组合树中，与本项目的
        //  "播放器为同树覆盖层"架构不匹配）。
        val density = LocalDensity.current
        val coverSizePx = with(density) { coverSize.toPx() }
        val miniCoverPx = with(density) { 40.dp.toPx() }
        val coverStartScale =
            if (coverSizePx > 0f) (miniCoverPx / coverSizePx).coerceIn(0.1f, 1f) else 1f
        var offsetInitialized by remember { mutableStateOf(false) }

        LaunchedEffect(expanded, heightPx) {
            val target = if (expanded) 0f else heightPx
            if (!offsetInitialized) {
                // 首次测量后直接归位，避免「刚进页面就播一次收起动画」
                offsetY.snapTo(target)
                offsetInitialized = true
            } else {
                offsetY.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }

        // 展开时接管系统返回键 → 收起播放器
        BackHandler(enabled = expanded) { onBack() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offsetY.value }
                // 不透明打底：下方渐变的首色是**半透明**的专辑色（alpha 0.30），
                // 若没有实色垫底，作为覆盖层时会透出下层页面内容（「背景透明化」）。
                //
                // ⚠️ 这个打底必须放在**位移层内部**（即 graphicsLayer 之后）。
                // 若放在外层（BoxWithConstraints 上），播放器收起时只有内层移出屏幕，
                // 外层实色仍铺满整屏 → 会盖住整个界面，表现为「打开应用白屏」。
                .background(MaterialTheme.colorScheme.surface)
                .background(
                    Brush.verticalGradient(
                    colors = listOf(
                        (coverColor ?: MaterialTheme.colorScheme.surface).copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        // 内边距加在内容层而非背景层 —— 渐变背景得以延伸到状态栏下
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "正在播放",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Box {
                    // 不用 IconButton —— 它是**固定 48dp** 的图标按钮容器，
                    // 而音质文案长度差异很大（「标准」2 字 →「超清母带」4 字），
                    // 4 字时会超出 48dp 被裁掉，表现为「文字显示不全」。
                    // 改用自适应宽度的可点击 Box，并保留 ≥48dp 的触摸高度。
                    Box(
                        modifier = Modifier
                            .clip(AppShapes.of(10.dp))
                            .clickable(enabled = !isLocalTrack) { qualityMenuOpen = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = qualityText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isLocalTrack) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    DropdownMenu(expanded = qualityMenuOpen, onDismissRequest = { qualityMenuOpen = false }) {
                        // ⚠️ 只列出**该曲目官方支持**的档位。
                        //
                        // 原先固定列出全部 6 档 —— 若某首歌最高只到「极高」，
                        // 用户选「无损」后服务端拿不到地址，会静默降级回原档位，
                        // 表现为"选了音质完全不生效"。
                        //
                        // `song.maxBr` 为 0 时（接口未给出）不限制，退化为原行为。
                        val available = AudioQuality.supportedBy(song?.maxBr ?: 0)
                        available.forEach { q ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (q.level == state.quality) "${q.label} ✓" else q.label)
                                },
                                onClick = {
                                    PlaybackManager.changeQuality(q.level)
                                    qualityMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            // ============================================================
            // 歌曲详情面板：下载 / 分享 / 关于
            //
            // 放在播放器内（而非另开页面）的原因：这是围绕"当前这首歌"的
            // 一次性操作，停留在播放器上更符合使用节奏，
            // 也避免为几个动作引入新的导航目的地。
            // ============================================================
            val sheetSong = state.song
            if (songDetailSheet && sheetSong != null) {
                ModalBottomSheet(
                    onDismissRequest = { songDetailSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                        Text(
                            text = sheetSong.name,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sheetSong.artistNames,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))

                        // ---------- 下载 ----------
                        DetailAction(
                            icon = Icons.Filled.Download,
                            label = "下载歌曲",
                            enabled = !isLocalTrack,
                        ) {
                            detailQualityPicker = !detailQualityPicker
                        }

                        if (detailQualityPicker) {
                            // 只列出该曲目支持的档位（与右上角音质菜单同一口径）
                            val dlQualities = AudioQuality.supportedBy(sheetSong.maxBr)
                            dlQualities.reversed().forEach { q ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            DownloadController.download(
                                                song = sheetSong,
                                                quality = q.level,
                                                scope = scope,
                                            )
                                            detailQualityPicker = false
                                        }
                                        .padding(horizontal = 40.dp, vertical = 10.dp),
                                ) {
                                    Text(q.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // ---------- 下载进度 ----------
                        val dl by DownloadController.state.collectAsState()
                        if (dl.songId == sheetSong.id) {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                if (dl.running) {
                                    Text(
                                        text = "下载中…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    if (dl.ratio >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { dl.ratio },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${(dl.ratio * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        // 服务端未给长度：用不确定进度，不画假百分比
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                } else if (dl.finished) {
                                    Text(
                                        text = dl.message.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (dl.success) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(onClick = { DownloadController.clear() }) {
                                        Text("知道了")
                                    }
                                }
                            }
                        }

                        // ---------- 分享 ----------
                        DetailAction(icon = Icons.Filled.Share, label = "分享歌曲") {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${sheetSong.name} - ${sheetSong.artistNames}\n" +
                                        "https://music.163.com/song?id=${sheetSong.id}",
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(share, "分享歌曲"),
                                )
                            }
                            songDetailSheet = false
                        }

                        // ---------- 关于歌曲 ----------
                        DetailAction(icon = Icons.Filled.Info, label = "关于歌曲") {
                            showSongInfo = true
                        }
                    }
                }
            }

            // 「关于歌曲」信息弹窗
            val infoSong = state.song
            if (showSongInfo && infoSong != null) {
                AlertDialog(
                    onDismissRequest = { showSongInfo = false },
                    title = { Text("关于歌曲") },
                    text = {
                        Column {
                            SongInfoRow("歌曲", infoSong.name)
                            SongInfoRow("歌手", infoSong.artistNames)
                            infoSong.album?.name?.takeIf { it.isNotBlank() }?.let {
                                SongInfoRow("专辑", it)
                            }
                            if (infoSong.duration > 0) {
                                SongInfoRow("时长", formatTime(infoSong.duration))
                            }
                            AudioQuality.highestOf(infoSong.maxBr)?.let {
                                SongInfoRow("官方最高音质", it.label)
                            }
                            infoSong.audioSpecText()?.let {
                                SongInfoRow("音频规格", it)
                            }
                            SongInfoRow("歌曲 ID", infoSong.id.toString())
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSongInfo = false }) { Text("关闭") }
                    },
                )
            }

            if (song == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无播放内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            // 内容区：居中排列，紧凑不留大空白
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 窄屏（含横屏）收敛横向内边距，避免内容被压缩变形
                    .padding(horizontal = if (boxMaxWidth < 360.dp) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CoverImage(
                    url = song.coverUrl,
                    modifier = Modifier
                        // ⚠️ 封面不再固定 260dp。
                        //
                        // 固定值在三种场景下必然出问题：
                        //  · 矮屏（720×1280 老机型、分屏）—— 总高超屏，控件被挤压
                        //  · 横屏 —— 高度骤减，260dp 几乎占满屏高
                        //  · 大字体（无障碍 200%）—— 文字变高，纵向空间更紧张
                        //
                        // 现改为按**可用高度**自适应：常规竖屏约 250~270dp
                        // （与原先 260dp 基本一致，观感不变），
                        // 矮屏/横屏则相应收窄，为歌词与控制区让出空间。
                        .size(coverSize)
                        .shadow(24.dp, AppShapes.of(24.dp))
                        .clip(AppShapes.of(24.dp))
                        // 连续转场：随展开进度由小放大并淡入。
                        // 在 graphicsLayer 的 lambda 内读取 offsetY，
                        // 属绘制阶段取值，不触发组合期重组。
                        .graphicsLayer {
                            val progress = if (heightPx > 0f) {
                                (1f - offsetY.value / heightPx).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                            val sc = coverStartScale +
                                (1f - coverStartScale) * progress
                            scaleX = sc
                            scaleY = sc
                            alpha = progress
                        }
                        .clickable(onClick = onOpenLyric),
                    contentDescription = song.name,
                )
                Spacer(Modifier.height(16.dp))
                // 歌词区固定高度，避免切换音质时布局抖动
                // 歌词区：当前行 + 翻译 / 下一行 + 翻译，四段完整显示不截断
                Column(
                    // 高度随屏高自适应：
                    // 固定 124dp 在矮屏上会与控制区争抢空间，把播放按钮挤到边缘。
                    // 下限 96dp 保证大字体下四行歌词仍能显示，上限 124dp 与原值一致。
                    modifier = Modifier
                        .height((boxMaxHeight * 0.16f).coerceIn(96.dp, 124.dp))
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (currentLine.isNotEmpty()) {
                        // ---- 当前行 ----
                        Text(
                            text = currentLine,
                            style = MaterialTheme.typography.titleSmall,
                            color = coverColor ?: MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentTranslation.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = currentTranslation,
                                style = MaterialTheme.typography.bodySmall,
                                color = (coverColor ?: MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(9.dp))

                        // ---- 下一行（含翻译，整体弱化以区分主次）----
                        Text(
                            text = nextLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (nextTranslation.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = nextTranslation,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artistNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        song.artists.firstOrNull()?.id?.let(onOpenArtist)
                    },
                )

                // 音频规格（采样率 / 位深）—— 小字、弱化，不抢主信息
                //
                // 说明：采样率与位深按该曲目**可得的最高档位**推导，
                // 而非用户当前选择的档位 —— 后者可能因账号权限拿不到，
                // 那样显示的规格会与实听不符。服务端未给出码率时不显示。
                song.audioSpecText()?.let { spec ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = spec,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }

            // 进度与控制（紧凑贴近内容区）
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                // 进度条上方一行：左「歌曲详情」，右「桌面歌词开关」
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    // 两端分布：把详情入口放在左侧（贴近阅读起点），
                    // 歌词开关保持在右侧不动
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 歌曲详情入口：下载 / 分享 / 关于
                    IconButton(onClick = { songDetailSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "歌曲详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { toggleOverlay() }) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "桌面歌词开关",
                            tint = if (overlayOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 波浪进度条：时间与波形同行（拖动时左侧时间实时跟随）
                val durationMs = state.duration.coerceAtLeast(1L)
                var scrubRatio by remember { mutableFloatStateOf(-1f) }
                val shownMs = if (scrubRatio >= 0f) {
                    (scrubRatio * durationMs).toLong()
                } else {
                    state.position
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(shownMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    WaveProgressBar(
                        progress = state.position.toFloat() / durationMs,
                        onSeek = { PlaybackManager.seekTo((it * durationMs).toLong()) },
                        isPlaying = state.isPlaying,
                        onScrub = { ratio -> scrubRatio = ratio },
                        playedColor = coverColor ?: MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatTime(state.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BouncyIconButton(onClick = { PlaybackManager.cycleMode() }) {
                            Icon(
                                imageVector = modeIcon(state.mode),
                                contentDescription = playModeLabel(state.mode),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 圆形底 + 主色图标，与播放键形成「主次分明的圆形控件组」
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                )
                                .clickable { PlaybackManager.previous() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 播放/暂停键：尺寸与圆角随状态做 spring 变形，
                        // 播放时为"圆角方形"、暂停时为"正圆"，形成可辨识的形态切换
                        val playSize by animateDpAsState(
                            targetValue = if (state.isPlaying) 66.dp else 60.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "playSize",
                        )
                        val playCorner by animateDpAsState(
                            targetValue = if (state.isPlaying) 22.dp else 30.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "playCorner",
                        )

                        Box(
                            modifier = Modifier
                                .size(playSize)
                                .clip(AppShapes.of(playCorner))
                                .background(coverColor ?: MaterialTheme.colorScheme.primary)
                                .clickable { PlaybackManager.togglePlayPause() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.isLoading) {
                                // 同悬浮岛：加载态统一用多边形指示器。
                                // 容器 30dp ≈ 原 24dp 圆环的视觉分量（0.79 缩放）。
                                ExpressiveLoadingIndicator(
                                    modifier = Modifier.size(30.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                )
                            } else {
                                // 图标切换：缩放 + 淡入淡出，避免生硬跳变
                                AnimatedContent(
                                    targetState = state.isPlaying,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.55f) + fadeIn(
                                            tween(180, easing = FastOutSlowInEasing),
                                        )) togetherWith (scaleOut(targetScale = 0.55f) + fadeOut(
                                            tween(140, easing = FastOutSlowInEasing),
                                        ))
                                    },
                                    label = "playIcon",
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) "暂停" else "播放",
                                        modifier = Modifier.size(34.dp),
                                        tint = MaterialTheme.colorScheme.surface,
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 圆形底 + 主色图标，与播放键形成「主次分明的圆形控件组」
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                )
                                .clickable { PlaybackManager.next() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 爱心：图标切换做缩放+淡入淡出，收藏态再叠一层弹性放大
                        val likeScale by animateFloatAsState(
                            targetValue = if (state.isLiked) 1.14f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "likeScale",
                        )
                        BouncyIconButton(onClick = { PlaybackManager.toggleLike() }) {
                            AnimatedContent(
                                targetState = state.isLiked,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.4f) + fadeIn(
                                        tween(180, easing = FastOutSlowInEasing),
                                    )) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut(
                                        tween(140, easing = FastOutSlowInEasing),
                                    ))
                                },
                                label = "likeIcon",
                            ) { liked ->
                                Icon(
                                    imageVector = if (liked) Icons.Filled.Favorite
                                    else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (liked) "取消喜欢" else "喜欢",
                                    modifier = Modifier.size(26.dp).scale(likeScale),
                                    tint = if (liked) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}



/** 详情面板的一行操作项 */
@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/** 「关于歌曲」弹窗里的一行信息 */
@Composable
private fun SongInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun modeIcon(mode: PlayMode) = when (mode) {
    PlayMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    else -> Icons.Filled.Repeat
}

private fun playModeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENCE -> "顺序播放"
    PlayMode.LIST_LOOP -> "列表循环"
    PlayMode.SINGLE_LOOP -> "单曲循环"
    PlayMode.SHUFFLE -> "随机播放"
}

private fun qualityLabel(q: String): String = when (q) {
    // 本地文件由扩展名承担显示，这里只作兜底
    "local" -> "本地"
    // 未知档位不再一律显示「标准」—— 那会把"服务端给了意料外的值"掩盖成正常状态
    else -> AudioQuality.of(q)?.label ?: q
}
