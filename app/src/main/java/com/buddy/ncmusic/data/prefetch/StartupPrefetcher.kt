package com.buddy.ncmusic.data.prefetch

import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.cache.ContentCache
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

data class StartupProgress(
    val progress: Float = 0f,
    val completed: Int = 0,
    val total: Int = 0,
)

/**
 * 启动预热。
 *
 * 所有请求并行执行，失败只影响对应区块，不阻塞应用进入。结果写入
 * [ContentCache]，首页、发现页和搜索页随后直接复用。
 */
object StartupPrefetcher {
    private val mutex = Mutex()

    private val _progress = MutableStateFlow(StartupProgress())
    val progress: StateFlow<StartupProgress> = _progress.asStateFlow()

    private val rankIds = listOf(19723756L, 3778678L, 2884035L, 3779629L)

    suspend fun warmUp(isLoggedIn: Boolean) {
        mutex.withLock {
            val repo = NCMusicApp.instance.musicRepository

            // ⚠️ 必须先确保 Cookie 恢复，再发起任何预热请求。
            //
            // Cookie 恢复在 `Application.onCreate` 里是**另一个**协程，
            // 与本预热没有先后保证。若预热抢跑：
            //   · 账号级接口（每日推荐 / 雷达 / 推荐歌单）会以未登录身份发出
            //   · 返回的通用数据被写进 ContentCache
            //   · 首页在缓存 TTL 内一直展示这批"未登录默认数据"，
            //     表现为「不随账号变化」「与官方客户端不一致」
            repo.ensureCookieRestored()
            val tasks = buildList<suspend () -> Unit> {
                if (!ContentCache.isFresh() || ContentCache.toplist == null) {
                    add {
                        (repo.toplist() as? ApiResult.Success)?.data?.let(ContentCache::storeToplist)
                    }
                }
                if (!ContentCache.isFresh() || ContentCache.personalizedPlaylists == null) {
                    add {
                        (repo.personalizedPlaylists() as? ApiResult.Success)?.data
                            ?.let(ContentCache::storePersonalized)
                    }
                }
                if (!ContentCache.isFresh() || ContentCache.radarPlaylists == null) {
                    add {
                        (repo.radarPlaylists(limit = 7) as? ApiResult.Success)?.data
                            ?.let(ContentCache::storeRadar)
                    }
                }
                if (!ContentCache.isFresh() || ContentCache.hotWords == null) {
                    add {
                        (repo.searchHot() as? ApiResult.Success)?.data
                            ?.let(ContentCache::storeHotWords)
                    }
                }
                rankIds.forEach { id ->
                    if (ContentCache.rankDetail(id) == null) {
                        add {
                            (repo.playlistDetail(id) as? ApiResult.Success)?.data
                                ?.let { ContentCache.storeRankDetail(id, it) }
                        }
                    }
                }
                if (isLoggedIn) {
                    if (!ContentCache.isFresh() || ContentCache.dailySongs == null) {
                        add {
                            (repo.recommendSongs() as? ApiResult.Success)?.data
                                ?.let(ContentCache::storeDaily)
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                _progress.value = StartupProgress(1f, 0, 0)
                return
            }

            val total = tasks.size
            val completed = AtomicInteger(0)
            _progress.value = StartupProgress(0f, 0, total)

            supervisorScope {
                tasks.map { task ->
                    async(Dispatchers.IO) {
                        try {
                            task()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            // 单个区块失败不应阻断启动；页面会展示自己的错误/空态。
                        } finally {
                            val done = completed.incrementAndGet()
                            _progress.value = StartupProgress(
                                progress = done.toFloat() / total,
                                completed = done,
                                total = total,
                            )
                        }
                    }
                }.awaitAll()
            }
            _progress.value = StartupProgress(1f, total, total)
        }
    }
}
