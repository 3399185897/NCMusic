package com.buddy.ncmusic.playback

import android.content.Context
import android.content.SharedPreferences
import com.buddy.ncmusic.data.model.Song
import kotlinx.serialization.json.Json

/**
 * 播放快照：进程被系统回收后，用来把「上次听到哪」还原回来。
 *
 * ## 为什么单独用 SharedPreferences 而不是 DataStore
 *
 * 1. **写入频率高**：进度每 5 秒落一次盘。DataStore 每次 `edit` 都会走一遍
 *    协程调度 + 原子文件替换，高频写入没有必要。SharedPreferences 的
 *    `apply()` 是内存写 + 后台异步落盘，正是这种场景该用的。
 *
 * 2. **不能污染 UserState**：`userStateFlow` 被设置页、主题、底栏等大量订阅。
 *    把播放快照塞进 `UserState`，意味着每 5 秒一次进度写入都会让整条流
 *    重新发射 —— 全站重组，代价远超收益。
 *
 * 3. **启动时要同步读**：恢复发生在 `ensureInit` 之前，拿不到就用不上。
 *
 * ## 存了什么
 *
 * 队列会整体序列化成 JSON。一首歌约 300~600 字节，100 首大约 40KB，
 * 对 SharedPreferences 是安全量级；只保留前 200 首，避免超长队列拖慢启动。
 */
object PlaybackSnapshotStore {

    private const val FILE = "ncmusic_playback"
    private const val KEY_QUEUE = "queue_json"
    private const val KEY_INDEX = "queue_index"
    private const val KEY_POSITION = "position_ms"
    private const val KEY_PLAYING = "was_playing"

    /** 队列上限：超出部分不落盘（恢复时仍然从当前曲往前取） */
    private const val MAX_QUEUE = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    data class Snapshot(
        val queue: List<Song>,
        val index: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * 保存快照。
     *
     * 以当前曲为基准裁剪队列：保留当前曲前后的窗口，
     * 避免"队列 500 首、当前在第 400 首"时每次都要写一整份。
     */
    fun save(context: Context, queue: List<Song>, index: Int, positionMs: Long, wasPlaying: Boolean) {
        if (queue.isEmpty() || index !in queue.indices) return
        runCatching {
            val trimmed = trim(queue, index)
            prefs(context).edit()
                .putString(KEY_QUEUE, json.encodeToString(trimmed.second))
                .putInt(KEY_INDEX, trimmed.first)
                .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
                .putBoolean(KEY_PLAYING, wasPlaying)
                .apply()
        }
    }

    /** 只更新进度（不重写队列），供进度循环调用 */
    fun savePosition(context: Context, positionMs: Long, wasPlaying: Boolean) {
        runCatching {
            prefs(context).edit()
                .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
                .putBoolean(KEY_PLAYING, wasPlaying)
                .apply()
        }
    }

    fun load(context: Context): Snapshot? = runCatching {
        val p = prefs(context)
        val raw = p.getString(KEY_QUEUE, null) ?: return null
        val queue = json.decodeFromString<List<Song>>(raw)
        if (queue.isEmpty()) return null
        val index = p.getInt(KEY_INDEX, 0).coerceIn(0, queue.lastIndex)
        Snapshot(
            queue = queue,
            index = index,
            positionMs = p.getLong(KEY_POSITION, 0L),
            wasPlaying = p.getBoolean(KEY_PLAYING, false),
        )
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
    }

    /**
     * 裁剪队列，返回 (新的当前下标, 裁剪后的队列)。
     *
     * 规则：当前曲之前最多留 20 首（够"上一首"用），之后最多留 180 首。
     */
    private fun trim(queue: List<Song>, index: Int): Pair<Int, List<Song>> {
        if (queue.size <= MAX_QUEUE) return index to queue
        val before = 20
        val after = MAX_QUEUE - before - 1
        val start = (index - before).coerceAtLeast(0)
        val end = (start + MAX_QUEUE).coerceAtMost(queue.size)
        val slice = queue.subList(start, end)
        return (index - start) to slice.toList()
    }
}
