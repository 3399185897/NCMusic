package com.buddy.ncmusic.ui.navigation

import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    fun playlist(id: Long) = "playlist/$id"

    fun artist(id: Long) = "artist/$id"

    fun settingsGroup(groupId: String) = "settings_group/$groupId"
}

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomTabs = listOf(
    TabItem(Routes.HOME, R.string.tab_home, Icons.Default.Home),
    TabItem(Routes.DISCOVER, R.string.tab_discover, Icons.Default.Search),
    TabItem(Routes.LIBRARY, R.string.tab_library, Icons.Default.Person),
)

/** Tab 在底栏中的序号，用于判断切换方向；非 Tab 路由返回 -1 */
private fun tabIndexOf(route: String?): Int =
    bottomTabs.indexOfFirst { it.route == route }

@Composable
fun NCMusicApp() {
    val startPage by NCMusicApp.instance.userPreferences.userStateFlow
        .collectAsState(initial = null)
    val startDest = when (startPage?.startPage) {
        "discover" -> Routes.DISCOVER
        "library" -> Routes.LIBRARY
        else -> Routes.HOME
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    // 记录切换前所处的 Tab 序号：转场 lambda 中据此决定滑动方向
    var lastTabIndex by rememberSaveable { mutableIntStateOf(0) }

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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier.clip(
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ),
                ) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                // 记录来源 Tab，供转场判断方向
                                val from = tabIndexOf(currentRoute)
                                if (from >= 0) lastTabIndex = from
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
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
            startDestination = startDest,
            modifier = Modifier.padding(
                // 沉浸式页面（播放器 / 歌词）由自身处理状态栏内边距，
                // 让背景能延伸到状态栏下，避免顶部出现空白条
                top = if (playerOpen || currentRoute == Routes.LYRIC) {
                    0.dp
                } else {
                    innerPadding.calculateTopPadding()
                },
                // 不为悬浮岛预留高度 —— 内容可延伸到最底，
                // 岛直接叠在内容之上，四周透出的是页面内容而非空白
                bottom = innerPadding.calculateBottomPadding(),
            ),
            // 统一转场：350ms 固定节奏 + 视差 + 缩放（见 Transitions.kt）
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() },
        ) {
            composable(
                route = Routes.HOME,
                // Tab 平行切换：**按序号决定滑动方向**，序号增大向右推进、减小向左退回，
                // 从而避免此前"从我的返回发现时方向相反"的问题。
                enterTransition = {
                    if (tabIndexOf(targetState.destination.route) >= lastTabIndex) {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeIn(tween(240))
                    } else {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
                            fadeIn(tween(240))
                    }
                },
                exitTransition = {
                    if (tabIndexOf(targetState.destination.route) >= lastTabIndex) {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
                            fadeOut(tween(200))
                    } else {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 } +
                            fadeOut(tween(200))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeOut(tween(200))
                },
            ) {
                HomeScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(
                route = Routes.DISCOVER,
                // Tab 平行切换：**按序号决定滑动方向**，序号增大向右推进、减小向左退回，
                // 从而避免此前"从我的返回发现时方向相反"的问题。
                enterTransition = {
                    if (tabIndexOf(targetState.destination.route) >= lastTabIndex) {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeIn(tween(240))
                    } else {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
                            fadeIn(tween(240))
                    }
                },
                exitTransition = {
                    if (tabIndexOf(targetState.destination.route) >= lastTabIndex) {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
                            fadeOut(tween(200))
                    } else {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 } +
                            fadeOut(tween(200))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeOut(tween(200))
                },
            ) {
                DiscoverScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenIdentify = { navController.navigate(Routes.IDENTIFY) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(
                route = Routes.LIBRARY,
                // Tab 平行切换：**按序号决定滑动方向**，序号增大向右推进、减小向左退回，
                // 从而避免此前"从我的返回发现时方向相反"的问题。
                enterTransition = {
                    if (tabIndexOf(targetState.destination.route) >= lastTabIndex) {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeIn(tween(240))
                    } else {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
                            fadeIn(tween(240))
                    }
                },
                exitTransition = {
                    if (tabIndexOf(targetState.destination.route) >= lastTabIndex) {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
                            fadeOut(tween(200))
                    } else {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 } +
                            fadeOut(tween(200))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeOut(tween(200))
                },
            ) {
                LibraryScreen(
                    onPlay = { songs, index ->
    PlaybackManager.playQueue(songs, index)
    playerOpen = true
},
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
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
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        playerOpen = true
                    },
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
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
            composable(Routes.LYRIC) {
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
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEq = { navController.navigate(Routes.EQ) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenGroup = { navController.navigate(Routes.settingsGroup(it.id)) },
                )
            }
            composable(
                route = Routes.SETTINGS_GROUP,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty()
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEq = { navController.navigate(Routes.EQ) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    initialGroup = SettingsGroup.fromId(groupId) ?: SettingsGroup.ALL,
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
        }

            // 迷你播放器：悬浮在底栏正上方
            //
            // 这里**始终组合** MiniPlayer，仅通过 visible 控制显隐动画 —— 而不用 `if` 增删。
            // 原因：MiniPlayer 内部有 `rememberCoverColor`（Palette 解码位图取色）与
            // Coil 图片加载；若每次路由切换都把它移除再重建，
            // 关闭播放器回到 Tab 时会重新取色，取色结果返回的瞬间颜色跳变 —— 即「卡一帧」。
            // 常驻组合后这些状态只初始化一次，显隐只是 alpha/位移动画，无重建开销。
            MiniPlayer(
                onOpen = { playerOpen = true },
                // 播放器展开时隐藏悬浮岛
                visible = showMiniPlayer && !playerOpen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
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
            onOpenArtist = { navController.navigate(Routes.artist(it)) },
            expanded = playerOpen,
        )
    }
    }

}
