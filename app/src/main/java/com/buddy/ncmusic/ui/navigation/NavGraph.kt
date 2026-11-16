package com.buddy.ncmusic.ui.navigation

import com.buddy.ncmusic.ui.components.FloatingBarItem
import com.buddy.ncmusic.ui.components.FloatingBottomBar
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import com.buddy.ncmusic.ui.util.rememberReduceMotion
import com.buddy.ncmusic.ui.util.IslandGapPreview
import com.buddy.ncmusic.ui.util.Md3Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.buddy.ncmusic.ui.screens.settings.SettingsGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import com.buddy.ncmusic.ui.screens.stats.StatsScreen
import androidx.compose.ui.Alignment
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.R
import com.buddy.ncmusic.playback.PlaybackManager
import com.buddy.ncmusic.ui.components.MiniPlayer
import com.buddy.ncmusic.data.prefetch.StartupPrefetcher
import com.buddy.ncmusic.ui.screens.artist.ArtistScreen
import com.buddy.ncmusic.ui.screens.discover.DiscoverScreen
import com.buddy.ncmusic.ui.screens.about.AboutScreen
import com.buddy.ncmusic.ui.screens.eq.EqScreen
import com.buddy.ncmusic.ui.screens.history.HistoryScreen
import com.buddy.ncmusic.ui.screens.home.HomeScreen
import com.buddy.ncmusic.ui.screens.identify.IdentifyScreen
import com.buddy.ncmusic.ui.screens.library.LibraryScreen
import com.buddy.ncmusic.ui.screens.local.LocalMusicScreen
import com.buddy.ncmusic.ui.screens.login.LoginScreen
import com.buddy.ncmusic.ui.screens.lyric.LyricScreen
import com.buddy.ncmusic.ui.screens.player.PlayerScreen
import com.buddy.ncmusic.ui.screens.playlist.PlaylistScreen
import com.buddy.ncmusic.ui.screens.settings.SettingsScreen

/** 导航路由 */
object Routes {
    /**
     * 主容器：三个 Tab 由 [androidx.compose.foundation.pager.HorizontalPager] 承载。
     *
     * 之所以不再让 HOME / DISCOVER / LIBRARY 各自作为 NavHost 目的地：
     * NavHost 切 Tab 会**销毁旧页、重建新页**，每次回到首页都要重新组合
     * LazyColumn 一屏内容 → 首帧必掉；而且只能靠淡入淡出转场（横向滑动会
     * 让两页同时重绘，代价更高）。Pager 让三页常驻，滑动天然连续，
     * 与 SukiSU 的 ViewPager2 行为一致。
     */
    const val MAIN = "main"
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val LOGIN = "login"
    const val PLAYLIST = "playlist/{playlistId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    /** 设置分类子页：settings_group/{groupId} */
    const val SETTINGS_GROUP = "settings_group/{groupId}"
    const val LYRIC = "lyric"
    const val IDENTIFY = "identify"
    const val ARTIST = "artist/{artistId}"
    const val LOCAL = "local"
    const val EQ = "eq"
    const val ABOUT = "about"
    const val STATS = "stats"
    const val THEME_SETTINGS = "theme_settings"

    fun playlist(id: Long) = "playlist/$id"

    fun artist(id: Long) = "artist/$id"

    fun settingsGroup(groupId: String) = "settings_group/$groupId"
}

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomTabs = listOf(
    TabItem(Routes.HOME, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(Routes.DISCOVER, R.string.tab_discover, Icons.Filled.Search, Icons.Outlined.Search),
    TabItem(Routes.LIBRARY, R.string.tab_library, Icons.Filled.Person, Icons.Outlined.Person),
)

/**
 * 三个 Tab 的横向容器。
 *
 * 与 SukiSU 的 `ViewPager2` 同构：页面常驻、手指横向拖动、底栏选中态跟随。
 * 这样做同时解决两件事 ——
 *  1. 翻页是真正的滑动，而不是淡入淡出；
 *  2. 切走的页面不会被销毁，回到首页时不再重新组合一屏内容（首帧卡顿的来源）。
 *
 * `beyondViewportPageCount = 1` 让相邻页保持组合：三页总量可控，
 * 换来滑动时不需要"边滑边组合新页面"。
 */
@Composable
private fun MainPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    prefetchNeighbors: Int,
    onPlay: (List<com.buddy.ncmusic.data.model.Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenIdentify: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    // 相邻页预取要**延后**开启。
    //
    // 冷启动时若 `beyondViewportPageCount = 1`，Pager 会在首帧就把「发现」页
    // 一并组合 —— 那是 4 个榜单 × 10 行 + 搜索框，是整棵树上最重的一屏，
    // 实测把冷启动的 p99 拉到 125ms，正好对应"第一次进主页卡一下"。
    // 先只组合当前页，等首屏稳定后再打开预取：
    // 那时用户还没开始滑，预取不会与首帧抢主线程。
    val reduceMotion = rememberReduceMotion()

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = prefetchNeighbors,
        key = { it },
    ) { page ->
        // 页面级入场动画已移除。
        //
        // 之前这里是「首次组合时淡入 + 上浮 10dp」。实测它正是**切页掉帧**的主因：
        //  · Pager 横向滑动本身已经是转场，再叠一层淡入属于重复表达；
        //  · 动画期间需要额外的 alpha 图层，而目标页同时正在首次组合整屏内容，
        //    两笔开销撞在同一帧上（janky 从 0.3% 升到 1.1%）。
        // MD3 的容器变换（Pager 滑动）本就不建议再叠加独立入场动画。
        Box(Modifier.fillMaxSize()) {
        when (page) {
            0 -> HomeScreen(
                onPlay = onPlay,
                onOpenPlaylist = onOpenPlaylist,
                onOpenArtist = onOpenArtist,
            )
            1 -> DiscoverScreen(
                onPlay = onPlay,
                onOpenPlaylist = onOpenPlaylist,
                onOpenIdentify = onOpenIdentify,
                onOpenArtist = onOpenArtist,
            )
            else -> LibraryScreen(
                onPlay = onPlay,
                onOpenPlaylist = onOpenPlaylist,
                onOpenHistory = onOpenHistory,
                onOpenLocal = onOpenLocal,
                onOpenSettings = onOpenSettings,
                onOpenStats = onOpenStats,
                onOpenLogin = onOpenLogin,
            )
        }
        }
    }
}

/** Tab 在底栏中的序号，用于判断切换方向；非 Tab 路由返回 -1 */
private fun tabIndexOf(route: String?): Int =
    bottomTabs.indexOfFirst { it.route == route }

@Composable
fun NCMusicApp() {
    val userState by NCMusicApp.instance.userPreferences.userStateFlow
        .collectAsState(initial = null)
    val loadedState = userState
    // DataStore 是异步的。若首帧先用默认值创建 NavHost，之后即便读到
    // startPage=discover/library，NavController 的起始目的地也不会再改，
    // 用户的“启动页”设置等于失效。因此先等偏好就绪，再创建导航图。
    if (loadedState == null) {
        // DataStore 首次读取通常只要 50~150ms。
        // 这里画的是**揭幕动画的第 0 帧**（同一朵云、同色、同位置），
        // 所以从背板过渡到揭幕是连续的，不会"跳一下"。
        com.buddy.ncmusic.ui.screens.startup.CloudLaunchBackdrop()
        return
    }

    // 揭幕是否仍在进行。用 rememberSaveable：进程被回收后恢复不重播。
    var revealActive by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        // 预热**不阻塞首帧**：主界面立刻开始组合，数据在后台补齐。
        //
        // 之前的写法是 warmUp 完成后才 appReady=true，再组合主界面 ——
        // 于是"主界面首次组合"这件事发生在遮罩即将淡出的时刻，
        // 与淡出动画撞在同一批帧上，用户看到的就是"动画放完卡一下"。
        // 现在预热是旁路任务，它的结果只影响首页有无数据，不影响何时揭幕。
        launch {
            withTimeoutOrNull(4_000L) {
                StartupPrefetcher.warmUp(isLoggedIn = loadedState.isLoggedIn)
            }
        }
    }
    // 仅本次启动读取一次；在设置页修改“启动页”不应重配当前 NavController。
    val initialTab = remember {
        when (loadedState.startPage) {
            "discover" -> 1
            "library" -> 2
            else -> 0
        }
    }

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    // 三个 Tab 的宿主 Pager：页面常驻，滑动即切换
    val pagerState = rememberPagerState(initialPage = initialTab) { bottomTabs.size }

    // 相邻页预取开关。提升到这一层是因为底栏与 Pager 都要用：
    // 底栏点击时立即置 1，不必等启动计时器。
    var prefetchNeighbors by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(700)
        prefetchNeighbors = 1
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val onMain = backStackEntry?.destination?.route == Routes.MAIN
    // 位于主容器时，用 Pager 当前页对应的 route 表示"当前 Tab"
    val currentRoute = if (onMain) {
        bottomTabs.getOrNull(pagerState.currentPage)?.route
    } else {
        backStackEntry?.destination?.route
    }

    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    // 只有「有底栏 + 确有歌曲」时才需要迷你播放器及其占位空间；
    // 未播放歌曲时若仍预留高度，底部会露出一块空白区域
    //
    // 注意：这里**只订阅「是否有歌」这一布尔值**，而不是收集整个 PlaybackState。
    // 播放进度 `position` 每 500ms 更新一次，若直接 `collectAsState()` 整个状态，
    // 会让本 NavGraph 每 500ms 重组一次，进而波及所有页面 —— 表现为周期性掉帧。
    val hasSong by remember {
        PlaybackManager.state
            .map { it.song != null }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    val showMiniPlayer = showBottomBar && hasSong

    // 系统「减少动态效果」偏好：为 true 时所有转场退化为瞬时切换
    val reduceMotion = rememberReduceMotion()

    // MD3 Snackbar：播放错误与音质降级等提示统一在 Scaffold 内展示。
    // 原先 error 走 Toast，而 noticeMessage 根本没有消费端 ——
    // 即使 PlaybackManager 写入了“已降级到极高音质”，用户也永远看不到。
    val snackbarHostState = remember { SnackbarHostState() }
    val playbackError by PlaybackManager.errorMessageFlow.collectAsState()
    val playbackNotice by PlaybackManager.noticeMessageFlow.collectAsState()
    LaunchedEffect(playbackError) {
        playbackError?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            PlaybackManager.clearError()
        }
    }
    LaunchedEffect(playbackNotice) {
        playbackNotice?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            PlaybackManager.clearNotice()
        }
    }
    val systemNavigationBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    // 播放器改为**同树覆盖层**（不再走 NavHost 路由）。
    //
    // 原因：NavHost 转场期间「新页面首次组合 + 旧页面仍在组合」双重开销，
    // 而播放页有 500+ 行内容（大图封面、渐变背景、歌词、滑动手势），
    // 首次组合必然掉帧。改为常驻覆盖层后，展开/收起只是整页位移动画（见 PlayerScreen.expanded）。
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    // 预组合：等首页首帧渲染完成后再挂载播放器，既不拖慢冷启动，
    // 又保证用户首次点击播放器时已是「纯动画」而非「首次组合」。
    var playerReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        playerReady = true
    }

    // 最外层 Box：让播放器能覆盖在 Scaffold 之上（含底部导航栏）。
    // Scaffold 的 bottomBar 绘制在 content **之上**，若播放器放在 content 内，
    // 底部导航栏会盖住播放器 → 表现为「无法全屏」。
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        // 背景透明：颜色扩散效果位于本层之下，需要透出
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                // 仅底栏；迷你播放器改为悬浮在内容之上（见下方 Box）
                //
                // 两种样式由「设置 → 外观 → 底栏样式」切换：
                //  · floating：药丸悬浮 + 选中项横向展开文字（参考 SukiSU）
                //  · classic / rounded：Material 标准平铺导航栏
                if (loadedState.bottomBarStyle == "floating") {
                    FloatingBottomBar(
                        items = bottomTabs.map {
                            FloatingBarItem(
                                label = stringResource(it.labelRes),
                                selectedIcon = it.selectedIcon,
                                unselectedIcon = it.unselectedIcon,
                            )
                        },
                        selectedIndex = tabIndexOf(currentRoute).coerceAtLeast(0),
                        onSelect = { index ->
                            // 用户已经要切页了：立刻打开相邻页预取，
                            // 不必等启动计时器 —— 否则目标页可能还没组合过
                            prefetchNeighbors = 1
                            // 底栏点击与横向滑动走同一条路径，选中态由 pagerState 驱动
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    )
                } else {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier.clip(
                        RoundedCornerShape(
                            topStart = if (loadedState.bottomBarRounded) 24.dp else 0.dp,
                            topEnd = if (loadedState.bottomBarRounded) 24.dp else 0.dp,
                        ),
                    ),
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                prefetchNeighbors = 1
                                scope.launch {
                                    pagerState.animateScrollToPage(tabIndexOf(tab.route))
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
                }
            }
        },
    ) { innerPadding ->
        // Box 叠加：MiniPlayer 浮在内容之上（而非占据独立区域），
        // 这样岛四周透出的是页面内容，不会出现白色隔断
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.padding(
                // 沉浸式页面（播放器 / 歌词）由自身处理状态栏内边距，
                // 让背景能延伸到状态栏下，避免顶部出现空白条
                top = if (playerOpen || currentRoute == Routes.LYRIC) {
                    0.dp
                } else {
                    innerPadding.calculateTopPadding()
                },
                // 不为悬浮岛预留高度 —— 内容可延伸到最底。
                //
                // 药丸悬浮模式下**连底栏高度也不留**：否则内容会在底栏上方
                // 被硬切一刀，胶囊下方是一条纯色空白，看起来像"贴底的条"。
                // 底部留白改由各列表的 rememberBottomSafeSpace() 负责。
                bottom = if (loadedState.bottomBarStyle == "floating" && showBottomBar) {
                    0.dp
                } else {
                    innerPadding.calculateBottomPadding()
                },
            ),
            // 统一转场：350ms 固定节奏 + 视差 + 缩放（见 Transitions.kt）
            // 转场传入减动效偏好：系统开启「移除动画」时退化为瞬时切换
            enterTransition = { enterTransition(reduceMotion) },
            exitTransition = { exitTransition(reduceMotion) },
            popEnterTransition = { popEnterTransition(reduceMotion) },
            popExitTransition = { popExitTransition(reduceMotion) },
        ) {
            composable(Routes.MAIN) {
                MainPager(
                    pagerState = pagerState,
                    prefetchNeighbors = prefetchNeighbors,
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenArtist = { id ->
                        // ⚠️ 必须先收起全屏播放器再跳转。
                        //
                        // 播放器是"同树覆盖层"（非 NavHost 路由），若只 navigate 不收起，
                        // 歌手页会**叠在播放器下方** —— 用户点击后画面毫无变化（仍是播放器），
                        // 需手动下滑才看到目标页面，等同"点击无反应"。
                        playerOpen = false
                        navController.navigate(Routes.artist(id))
                    },
                    onOpenIdentify = { navController.navigate(Routes.IDENTIFY) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenLocal = { navController.navigate(Routes.LOCAL) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                )
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("playlistId") ?: 0L
                PlaylistScreen(
                    playlistId = id,
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { id ->
                            // ⚠️ 必须先收起全屏播放器再跳转。
                            //
                            // 播放器是"同树覆盖层"（非 NavHost 路由），
                            // 若只 navigate 不收起，歌手页会**叠在播放器下方**——
                            // 用户点击后画面毫无变化（仍是播放器），
                            // 需手动下滑才看到目标页面，等同"点击无反应"。
                            playerOpen = false
                            navController.navigate(Routes.artist(id))
                        },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { id ->
                            // ⚠️ 必须先收起全屏播放器再跳转。
                            //
                            // 播放器是"同树覆盖层"（非 NavHost 路由），
                            // 若只 navigate 不收起，歌手页会**叠在播放器下方**——
                            // 用户点击后画面毫无变化（仍是播放器），
                            // 需手动下滑才看到目标页面，等同"点击无反应"。
                            playerOpen = false
                            navController.navigate(Routes.artist(id))
                        },
                )
            }
            composable(Routes.LOCAL) {
                LocalMusicScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("artistId") ?: 0L
                ArtistScreen(
                    artistId = id,
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            // 歌词页用**淡入淡出**，与播放器的"上滑推出"形成对照：
            //  · 播放器是"从底部推上来"，本质是层级推进；
            //  · 歌词是同一首歌的另一面（同一空间里的转场），
            //    淡入更像"贴上一层"而不是"打开新页面"，
            //    退出时同样淡出，回到播放器不会有方向感上的突兀。
            composable(
                Routes.LYRIC,
                enterTransition = {
                    fadeIn(
                        animationSpec = tween(
                            Md3Motion.Medium2,
                            easing = Md3Motion.StandardDecelerate,
                        ),
                    )
                },
                exitTransition = {
                    fadeOut(
                        animationSpec = tween(
                            Md3Motion.Medium1,
                            easing = Md3Motion.StandardAccelerate,
                        ),
                    )
                },
                popEnterTransition = {
                    fadeIn(
                        animationSpec = tween(
                            Md3Motion.Medium2,
                            easing = Md3Motion.StandardDecelerate,
                        ),
                    )
                },
                popExitTransition = {
                    fadeOut(
                        animationSpec = tween(
                            Md3Motion.Medium1,
                            easing = Md3Motion.StandardAccelerate,
                        ),
                    )
                },
            ) {
                LyricScreen(
                    onBack = {
                        navController.popBackStack()
                        // 歌词页是从播放器进入的，返回时恢复播放器展开状态
                        playerOpen = true
                    },
                )
            }
            composable(Routes.IDENTIFY) {
                IdentifyScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(onBack = { navController.popBackStack() })
            }
            // 设置已改为**单页分组**（对齐 SukiSU）：不再有「分类总览 → 二级页」。
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEq = { navController.navigate(Routes.EQ) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenTheme = { navController.navigate(Routes.THEME_SETTINGS) },
                )
            }
            // 旧路由保留一版：已存在的返回栈/深链仍能落到设置主页，
            // 而不是崩在「找不到 destination」。
            composable(
                route = Routes.SETTINGS_GROUP,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEq = { navController.navigate(Routes.EQ) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenTheme = { navController.navigate(Routes.THEME_SETTINGS) },
                )
            }
            composable(Routes.EQ) {
                EqScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STATS) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.THEME_SETTINGS) {
                com.buddy.ncmusic.ui.screens.settings.ThemeSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

            // 迷你播放器：悬浮在底栏正上方
            //
            // 这里**始终组合** MiniPlayer，仅通过 visible 控制显隐动画 —— 而不用 `if` 增删。
            // 原因：MiniPlayer 内部有 `rememberCoverColor`（Palette 解码位图取色）与
            // Coil 图片加载；若每次路由切换都把它移除再重建，
            // 关闭播放器回到 Tab 时会重新取色，取色结果返回的瞬间颜色跳变 —— 即「卡一帧」。
            // 常驻组合后这些状态只初始化一次，显隐只是 alpha/位移动画，无重建开销。
            // 悬浮岛与底栏的间距由「外观 → 悬浮岛间距」决定（0~40dp）。
            //
            // ⚠️ 基准不是 `innerPadding` 的底边，而是**药丸的顶边**：
            // FloatingBottomBar 自己在药丸上方留了 12dp（它的 vertical padding），
            // 若直接拿 innerPadding 当基准，gap=0 时悬浮岛与药丸之间仍隔着这 12dp，
            // 用户设成 0 却发现"还是没贴住"。
            // MiniPlayer 内部还给阴影留了 4dp 底部内边距，同样要扣掉。
            //
            // 拖动滑块时优先用实时预览值，让悬浮岛跟着升降。
            val islandGap = (IslandGapPreview.live ?: loadedState.miniPlayerGap)
                .coerceIn(0, 40).dp
            val barOwnInset = if (loadedState.bottomBarStyle == "floating") 12.dp else 0.dp
            val islandSelfInset = 4.dp
            // 无底栏时（播放器/二级页）不需要扣除，直接贴屏幕底部安全区
            val islandBase = if (showBottomBar) {
                (innerPadding.calculateBottomPadding() - barOwnInset - islandSelfInset)
                    .coerceAtLeast(0.dp)
            } else {
                innerPadding.calculateBottomPadding()
            }
            MiniPlayer(
                onOpen = { playerOpen = true },
                // 播放器展开时隐藏悬浮岛
                visible = showMiniPlayer && !playerOpen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = islandBase + islandGap,
                    ),
            )
        }
    }

    // 播放器覆盖层：位于 Scaffold **之外**，因此能覆盖底部导航栏与整个屏幕。
    // 常驻组合树（playerReady 之后），展开/收起由 PlayerScreen 内部的 Animatable 位移动画驱动。
    if (playerReady) {
        PlayerScreen(
            onBack = { playerOpen = false },
            onOpenLyric = {
                // 先收起播放器再进入歌词页，避免两层叠加
                playerOpen = false
                navController.navigate(Routes.LYRIC)
            },
            onOpenArtist = { id ->
                            // ⚠️ 必须先收起全屏播放器再跳转。
                            //
                            // 播放器是"同树覆盖层"（非 NavHost 路由），
                            // 若只 navigate 不收起，歌手页会**叠在播放器下方**——
                            // 用户点击后画面毫无变化（仍是播放器），
                            // 需手动下滑才看到目标页面，等同"点击无反应"。
                            playerOpen = false
                            navController.navigate(Routes.artist(id))
                        },
            expanded = playerOpen,
        )
    }

    // Snackbar 放在最外层、播放器之后：播放器是同树全屏覆盖层，
    // 若把 SnackbarHost 放进 Scaffold，展开播放器时提示会被整页盖住。
    val snackbarBottom = systemNavigationBottom + when {
        playerOpen -> 16.dp
        showMiniPlayer -> 80.dp + 72.dp + 8.dp
        showBottomBar -> 80.dp + 8.dp
        else -> 16.dp
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = snackbarBottom),
    )
    // 云朵揭幕层：位于所有界面之上（含播放器覆盖层），只负责"什么时候被看见"。
    //
    // 注意它**不再等待任何加载**：下面整个主界面已经在同一帧里组合好了，
    // 云在这里散开的同时，界面早已是稳定帧 —— 这正是卡顿消失的原因。
    if (revealActive) {
        com.buddy.ncmusic.ui.screens.startup.CloudReveal(
            onFinished = { revealActive = false },
        )
    }
    }

}
