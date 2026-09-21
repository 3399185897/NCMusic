package com.buddy.ncmusic.ui.navigation

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
    const val LYRIC = "lyric"
    const val IDENTIFY = "identify"
    const val ARTIST = "artist/{artistId}"
    const val LOCAL = "local"
    const val EQ = "eq"
    const val ABOUT = "about"

    fun playlist(id: Long) = "playlist/$id"

    fun artist(id: Long) = "artist/$id"
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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    MiniPlayer(onOpen = { navController.navigate(Routes.PLAYER) })
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } +
                    fadeIn(tween(240))
            },
            exitTransition = {
                slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 } +
                    fadeOut(tween(200))
            },
            // 返回时上一页仅淡入，避免"从播放器返回原页面还在滑动"的违和感
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } +
                    fadeOut(tween(200))
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        navController.navigate(Routes.PLAYER)
                    },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        navController.navigate(Routes.PLAYER)
                    },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenIdentify = { navController.navigate(Routes.IDENTIFY) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onPlay = { songs, index ->
    PlaybackManager.playQueue(songs, index)
    navController.navigate(Routes.PLAYER)
},
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenLocal = { navController.navigate(Routes.LOCAL) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                        navController.navigate(Routes.PLAYER)
                    },
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        navController.navigate(Routes.PLAYER)
                    },
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(Routes.LOCAL) {
                LocalMusicScreen(
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        navController.navigate(Routes.PLAYER)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.PLAYER,
                enterTransition = {
                    slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(220))
                },
                exitTransition = {
                    slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(200))
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(200))
                },
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLyric = { navController.navigate(Routes.LYRIC) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
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
                        navController.navigate(Routes.PLAYER)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LYRIC) {
                LyricScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.IDENTIFY) {
                IdentifyScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { songs, index ->
                        PlaybackManager.playQueue(songs, index)
                        navController.navigate(Routes.PLAYER)
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
                )
            }
            composable(Routes.EQ) {
                EqScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
