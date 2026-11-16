package com.buddy.ncmusic.data.cache

import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Song
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程级内容缓存。
 *
 * 启动遮罩会并行预热首页、排行榜与热搜；各页面随后直接从缓存恢复，
 * 避免“遮罩里刚请求完，进入首页又请求一遍”的重复流量。
 *
 * 只缓存成功结果，TTL 10 分钟。失败请求仍由各页面自己重试。
 */
object ContentCache {
    private const val TTL_MS = 10 * 60 * 1000L

    @Volatile
    private var updatedAt: Long = 0L

    /**
     * 分区时间戳。
     *
     * ⚠️ 原先只有一个全局 `updatedAt`：任何一个分区写入都会把整块缓存标记为"新鲜"。
     * 于是「榜单刚刷新过」会让「每日推荐」也被认为新鲜 —— 即便它的值是 null 或空表，
     * 首页就不再发请求，表现为「每日推荐/推荐歌单偶尔整个消失」。
     */
    private val stamps = ConcurrentHashMap<String, Long>()

    private fun stampOf(key: String): Long = stamps[key] ?: 0L

    private fun isFreshKey(key: String, now: Long): Boolean {
        val t = stampOf(key)
        return t > 0L && now - t <= TTL_MS
    }

    @Volatile
    var personalizedPlaylists: List<Playlist>? = null
        private set

    @Volatile
    var radarPlaylists: List<Playlist>? = null
        private set

    @Volatile
    var dailySongs: List<Song>? = null
        private set

    @Volatile
    var toplist: List<Playlist>? = null
        private set

    @Volatile
    var hotWords: List<String>? = null
        private set

    private val rankDetails = ConcurrentHashMap<Long, Playlist>()

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        updatedAt > 0L && now - updatedAt <= TTL_MS

    fun isFreshDaily(now: Long = System.currentTimeMillis()): Boolean =
        isFreshKey(KEY_DAILY, now) && dailySongs != null

    fun isFreshPlaylists(now: Long = System.currentTimeMillis()): Boolean =
        isFreshKey(KEY_PLAYLISTS, now) && personalizedPlaylists != null

    fun isFreshRadar(now: Long = System.currentTimeMillis()): Boolean =
        isFreshKey(KEY_RADAR, now) && radarPlaylists != null

    fun isFreshToplist(now: Long = System.currentTimeMillis()): Boolean =
        isFreshKey(KEY_TOPLIST, now) && toplist != null

    fun isFreshHotWords(now: Long = System.currentTimeMillis()): Boolean =
        isFreshKey(KEY_HOT_WORDS, now) && hotWords != null

    fun rankDetail(id: Long): Playlist? =
        if (isFresh()) rankDetails[id] else null

    fun hasAllRankDetails(ids: Collection<Long>): Boolean =
        isFresh() && ids.all(rankDetails::containsKey)

    private fun mark(key: String) {
        val now = System.currentTimeMillis()
        stamps[key] = now
        updatedAt = now
    }

    // ⚠️ 空结果一律不写缓存。
    //
    // 曾出现：未登录（或 Cookie 尚未恢复）时请求每日推荐，接口返回
    // `{"code":200,"recommend":[]}` —— 解析出空列表并被写进缓存。
    // 随后登录、或 Cookie 恢复完成，首页在 TTL 内看到"缓存存在"就
    // 直接采用这个空列表，于是**已登录却一直显示空**。
    // 不缓存空值 → 下次进入页面会重新请求。

    fun storePersonalized(data: List<Playlist>) {
        if (data.isEmpty()) return
        personalizedPlaylists = data
        mark(KEY_PLAYLISTS)
    }

    fun storeRadar(data: List<Playlist>) {
        if (data.isEmpty()) return
        radarPlaylists = data
        mark(KEY_RADAR)
    }

    fun storeDaily(data: List<Song>) {
        if (data.isEmpty()) return
        dailySongs = data
        mark(KEY_DAILY)
    }

    fun storeToplist(data: List<Playlist>) {
        if (data.isEmpty()) return
        toplist = data
        mark(KEY_TOPLIST)
    }

    fun storeHotWords(data: List<String>) {
        if (data.isEmpty()) return
        hotWords = data
        mark(KEY_HOT_WORDS)
    }

    fun storeRankDetail(id: Long, data: Playlist) {
        rankDetails[id] = data
        updatedAt = System.currentTimeMillis()
    }

    /** 账号切换后清除个性化内容；公共榜单与热搜保持可用。 */
    fun clearUserScoped() {
        personalizedPlaylists = null
        radarPlaylists = null
        dailySongs = null
        stamps.remove(KEY_PLAYLISTS)
        stamps.remove(KEY_RADAR)
        stamps.remove(KEY_DAILY)
    }

    fun clear() {
        personalizedPlaylists = null
        radarPlaylists = null
        dailySongs = null
        toplist = null
        hotWords = null
        rankDetails.clear()
        stamps.clear()
        updatedAt = 0L
    }

    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_RADAR = "radar"
    private const val KEY_DAILY = "daily"
    private const val KEY_TOPLIST = "toplist"
    private const val KEY_HOT_WORDS = "hotWords"
}
