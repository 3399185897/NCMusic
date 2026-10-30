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

    fun rankDetail(id: Long): Playlist? =
        if (isFresh()) rankDetails[id] else null

    fun hasAllRankDetails(ids: Collection<Long>): Boolean =
        isFresh() && ids.all(rankDetails::containsKey)

    fun storePersonalized(data: List<Playlist>) {
        personalizedPlaylists = data
        updatedAt = System.currentTimeMillis()
    }

    fun storeRadar(data: List<Playlist>) {
        radarPlaylists = data
        updatedAt = System.currentTimeMillis()
    }

    fun storeDaily(data: List<Song>) {
        dailySongs = data
        updatedAt = System.currentTimeMillis()
    }

    fun storeToplist(data: List<Playlist>) {
        toplist = data
        updatedAt = System.currentTimeMillis()
    }

    fun storeHotWords(data: List<String>) {
        hotWords = data
        updatedAt = System.currentTimeMillis()
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
    }

    fun clear() {
        personalizedPlaylists = null
        radarPlaylists = null
        dailySongs = null
        toplist = null
        hotWords = null
        rankDetails.clear()
        updatedAt = 0L
    }
}
