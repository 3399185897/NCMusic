package com.buddy.ncmusic.data.cache

import android.content.Context
import com.buddy.ncmusic.data.local.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缓存与临时文件管理。
 *
 * ## 目录划分（这是本次修订的核心）
 *
 * `cacheDir` 之下实际存在**两类性质完全不同**的内容：
 *
 * | 类型 | 位置 | 生命周期 | 可否按容量淘汰 |
 * |---|---|---|---|
 * | 应用缓存 | `cacheDir/ncmusic_cache/` | 可长期保留、可重建 | ✅ 受上限约束 |
 * | 临时工作文件 | `cacheDir/` 根下（`dl_*`、`art_*`、`identify.m4a`…） | **秒级**，用完即删 | ❌ 绝不 |
 *
 * **先前的缺陷**：`enforceLimit()` 扫描**整个** `cacheDir` 并按最后修改时间淘汰 ——
 * 而那里放的全是**正在使用中的下载中转文件**
 * （见 `MusicRepository` 的 `dl_*` / `art_*`、`IdentifyScreen` 的 `identify.m4a`）。
 * 于是"清理缓存"极可能删掉**正在写入的临时文件**，直接导致下载失败。
 *
 * 现在只对 [cacheRoot]（专属子目录）执行容量限制；
 * 临时文件另走 [cleanupStaleTempFiles]，且有足够长的静默期保护。
 *
 * ## 关于淘汰顺序（不冒充 LRU）
 *
 * 本类**不声称**是 LRU。真正的 LRU 需要记录每次访问时间，
 * 而缓存的读取路径（ExoPlayer 播放）并不经过本类，无从获知访问事实。
 *
 * 实际采用的是 **FIFO（最早写入优先）**：以文件最后修改时间近似"写入时间"，
 * 优先淘汰最早写入的。对"写一次、读多次"的媒体缓存，该近似是合理的；
 * 但它**不等于**淘汰"最久未使用"的项 —— 二者在访问分布不均时会不一致。
 */
class CacheManager(
    private val context: Context,
    private val prefs: UserPreferences,
) {

    /** `cacheDir` 根 —— 内含临时工作文件，**不整体扫描** */
    private val cacheDirRoot: File get() = context.cacheDir

    /**
     * 受容量管理的应用缓存目录。
     *
     * 独立子目录的意义：与临时工作文件物理隔离，
     * 使"按上限淘汰"永远不会误伤进行中的下载 / 识别任务。
     */
    private val cacheRoot: File get() = File(cacheDirRoot, CACHE_DIR_NAME)

    /**
     * 按设置的上限回收**应用缓存**。
     *
     * 只作用于 [cacheRoot]，不触碰 `cacheDir` 根下的临时文件。
     * 高频调用安全 —— 未超限时立即返回，不做任何文件操作。
     *
     * @param protect 本次刚写入的文件，**永不淘汰**
     * @return 被淘汰的文件数
     */
    suspend fun enforceLimit(protect: File? = null): Int = withContext(Dispatchers.IO) {
        if (!cacheRoot.exists()) return@withContext 0

        val limitMb = runCatching {
            prefs.userStateFlow.first().maxCacheMb
        }.getOrDefault(DEFAULT_LIMIT_MB)
        val limitBytes = limitMb.toLong() * 1024L * 1024L

        val files = cacheRoot.walkBottomUp()
            .filter { it.isFile && it.exists() }
            .filter { protect == null || it.absolutePath != protect.absolutePath }
            .filter { !isLikelyActive(it) }        // 保护疑似正在写入的文件
            .toList()

        val total = files.sumOf { it.length() }
        if (total <= limitBytes) return@withContext 0

        val target = total - limitBytes
        var freed = 0L
        var removed = 0

        // FIFO：按最后修改时间升序（最早写入的先淘汰）
        for (f in files.sortedBy { it.lastModified() }) {
            if (freed >= target) break
            val size = f.length()
            if (f.delete()) {
                freed += size
                removed++
            }
        }

        pruneEmptyDirs()
        removed
    }

    /**
     * 清理 `cacheDir` 根下**明显陈旧的临时工作文件**。
     *
     * 这类文件正常流程下用完即删，但**进程被杀 / 崩溃 / 断电**时会残留。
     *
     * 「陈旧」判据是最后修改时间早于 [STALE_TEMP_MS]（1 小时）——
     * 阈值取得长的原因：下载无损大文件可能持续数分钟，
     * 阈值过短会把**正在进行**的下载临时文件误删。
     * 即用"足够长的静默期"替代"精确的忙碌检测"，
     * 以极小的误删概率换取实现可靠性。
     *
     * @return 被清理的文件数
     */
    suspend fun cleanupStaleTempFiles(): Int = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() - STALE_TEMP_MS
        var removed = 0
        runCatching {
            cacheDirRoot.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < deadline && isTempWorkingFile(f.name)) {
                    if (f.delete()) removed++
                }
            }
        }
        removed
    }

    /** 是否为应用产生的临时工作文件 */
    private fun isTempWorkingFile(name: String): Boolean =
        name.startsWith(TMP_PREFIX_DL) ||
            name.startsWith(TMP_PREFIX_ART) ||
            name == TMP_IDENTIFY

    /** 推断文件是否可能正被写入（近期被修改过） */
    private fun isLikelyActive(f: File): Boolean =
        System.currentTimeMillis() - f.lastModified() < ACTIVE_WINDOW_MS

    /** 当前**受管理**的应用缓存占用（字节） */
    suspend fun currentSize(): Long = withContext(Dispatchers.IO) {
        cacheRoot.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 清空应用缓存目录。
     *
     * **只清 [cacheRoot]**，不动 `cacheDir` 根下的临时工作文件 ——
     * 后者可能正被进行中的下载 / 听歌识曲使用。
     * 若需清理陈旧临时文件，用 [cleanupStaleTempFiles]。
     */
    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            cacheRoot.listFiles()?.forEach { it.deleteRecursively() }
            true
        }.getOrDefault(false)
    }

    /** 清理淘汰后遗留的空目录 */
    private fun pruneEmptyDirs() {
        runCatching {
            cacheRoot.walkBottomUp()
                .filter { it.isDirectory && it != cacheRoot }
                .forEach { dir -> if (dir.listFiles()?.isEmpty() == true) dir.delete() }
        }
    }

    companion object {
        /** 受管理的应用缓存子目录名 */
        const val CACHE_DIR_NAME = "ncmusic_cache"

        /**
         * 读取设置失败时的兜底上限。
         * 与 `UserPreferences.maxCacheMb` 默认值、设置页滑块下限**三处统一**（1 GB）。
         */
        const val DEFAULT_LIMIT_MB = 1024

        /** 疑似活跃文件的判定窗口：2 分钟内修改过即视为可能正在写入 */
        private const val ACTIVE_WINDOW_MS = 2 * 60 * 1000L

        /** 临时文件的陈旧阈值：1 小时 */
        private const val STALE_TEMP_MS = 60 * 60 * 1000L

        private const val TMP_PREFIX_DL = "dl_"
        private const val TMP_PREFIX_ART = "art_"
        private const val TMP_IDENTIFY = "identify.m4a"
    }
}
