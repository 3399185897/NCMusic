package com.buddy.ncmusic.data.cache

import android.content.Context
import com.buddy.ncmusic.data.local.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缓存容量管理。
 *
 * ## 为什么需要这个类
 *
 * 设置页里「最大缓存大小」滑块此前**没有任何执行点**：
 * 它只被读出来显示，从未参与任何淘汰逻辑 —— 属于典型的
 * 「设置了但不起作用」的无效设置。用户把上限调到 1 GB，
 * 缓存照样可以涨到几个 GB。
 *
 * 本类把它接上：每次写入缓存文件后调用 [enforceLimit]，
 * 超限时按 **LRU（最近最少使用）** 淘汰，直到回落至上限以内。
 *
 * ## 淘汰策略
 *
 * 以**最后修改时间**作为访问时间的近似值 —— 缓存文件写入后
 * 我们不会再去 touch 它，所以「最后修改」就等于「最后一次被使用」。
 * 这是文件系统缓存最常见的近似做法，无需额外维护访问记录。
 */
class CacheManager(
    private val context: Context,
    private val prefs: UserPreferences,
) {

    /** 缓存根目录：应用内置 cacheDir */
    private val cacheRoot: File get() = context.cacheDir

    /**
     * 按设置的上限回收缓存。
     *
     * 调用时机：每次写入新的缓存文件之后。
     * 高频调用是安全的 —— 未超限时会立刻返回，不做任何文件操作。
     *
     * @param protect 本次刚写入的文件，**永不淘汰**。
     *   否则极端情况下可能把刚写好的文件自己删掉，
     *   表现为「下载成功但文件不存在」。
     * @return 被淘汰的文件数
     */
    suspend fun enforceLimit(protect: File? = null): Int = withContext(Dispatchers.IO) {
        val limitBytes = runCatching {
            prefs.userStateFlow.first().maxCacheMb
        }.getOrDefault(DEFAULT_LIMIT_MB).toLong() * 1024L * 1024L

        val files = cacheRoot.walkBottomUp()
            .filter { it.isFile && it.exists() }
            .filter { protect == null || it.absolutePath != protect.absolutePath }
            .toList()

        val total = files.sumOf { it.length() }
        if (total <= limitBytes) return@withContext 0

        // 按最后修改时间升序 —— 最久未使用的排在最前
        var freed = 0L
        var removed = 0
        val target = total - limitBytes

        for (f in files.sortedBy { it.lastModified() }) {
            if (freed >= target) break
            val size = f.length()
            if (f.delete()) {
                freed += size
                removed++
            }
        }

        // 顺带清理淘汰后留下的空目录，避免目录数量无限增长
        cacheRoot.walkBottomUp()
            .filter { it.isDirectory && it != cacheRoot }
            .forEach { dir -> if (dir.listFiles()?.isEmpty() == true) dir.delete() }

        removed
    }

    /** 当前缓存占用（字节） */
    suspend fun currentSize(): Long = withContext(Dispatchers.IO) {
        cacheRoot.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 清空整个缓存目录。
     *
     * 注意：缓存目录里可能含有**下载中转文件**，正在下载时清理会使其失败。
     * 因此调用方应在无下载任务时使用（设置页的手动清理满足这一条件）。
     */
    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            cacheRoot.listFiles()?.forEach { it.deleteRecursively() }
            true
        }.getOrDefault(false)
    }

    private companion object {
        /** 读取失败时的兜底上限，与 UserPreferences 的默认值保持一致 */
        const val DEFAULT_LIMIT_MB = 500
    }
}