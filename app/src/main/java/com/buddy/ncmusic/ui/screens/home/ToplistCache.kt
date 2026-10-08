package com.buddy.ncmusic.ui.screens.home

import com.buddy.ncmusic.data.model.Playlist

/**
 * 排行榜数据的进程级缓存。
 *
 * **存在意义**：`RankScreen` 与 `HomeScreen` 各自通过 `viewModel()` 创建了
 * **不同**的 `HomeViewModel` 实例。若把缓存放在 ViewModel 内部字段，
 * 每次进入排行榜页都会因实例重建而重置，导致**每次都重新请求**。
 *
 * 把数据提到进程级单例后：
 * - 首次加载成功后写入缓存
 * - 之后任何 ViewModel 实例都能**立即读到数据**，不再出现加载等待
 * - 下拉刷新时传 `force = true` 才会真正重新请求
 */
internal object ToplistCache {
    var data: List<Playlist> = emptyList()
    var loaded: Boolean = false

    /** 是否有可用缓存 */
    val hasData: Boolean get() = loaded && data.isNotEmpty()
}
