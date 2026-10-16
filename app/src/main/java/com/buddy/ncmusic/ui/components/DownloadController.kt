package com.buddy.ncmusic.ui.components

import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiResult
import com.buddy.ncmusic.util.AudioQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 下载任务的全局状态。
 *
 * ## 为什么做成单例而不是各界面各持一份
 *
 * 下载入口有两处：播放器的「歌曲详情」面板、歌曲列表的长按详情对话框。
 * 若各自维护 `downloading` 布尔量：
 *  · 在 A 处发起的下载，切到 B 处看不见进度
 *  · 两处同时发起会重复下载同一首歌
 *
 * 收敛到一个全局状态后，任何人都能看到当前任务与进度。
 *
 * ## 进度语义
 *
 * [Progress.ratio] 为 `0f..1f`；**`-1f` 表示总长未知**
 * （服务端未返回 `Content-Length`，无法计算百分比）——
 * 此时界面应显示"不确定进度"的样式，而不是画一条 0% 的假进度条。
 */
object DownloadController {

    /**
     * 一次下载任务的状态。
     *
     * @param songId 正在下载的歌曲 id；0 表示当前无任务
     * @param title 展示名（歌名），用于面板标题
     * @param ratio 进度 `0f..1f`；`-1f` = 总长未知
     * @param message 完成或失败后的结果文案
     * @param finished 是否已结束（成功或失败）
     * @param success 结束时是否成功
     */
    data class Progress(
        val songId: Long = 0L,
        val title: String = "",
        val ratio: Float = 0f,
        val message: String? = null,
        val finished: Boolean = false,
        val success: Boolean = false,
    ) {
        /** 是否有进行中的任务 */
        val running: Boolean get() = songId != 0L && !finished
    }

    private val _state = MutableStateFlow(Progress())
    val state: StateFlow<Progress> = _state.asStateFlow()

    /**
     * 发起下载。
     *
     * 若已有任务在跑，**直接忽略**本次请求 —— 避免并发下载同一首歌
     * 造成两份临时文件与目标文件竞争。
     */
    fun download(song: Song, quality: String, scope: CoroutineScope) {
        if (_state.value.running) return

        val label = AudioQuality.of(quality)?.label ?: quality
        _state.value = Progress(songId = song.id, title = song.name, ratio = 0f)

        scope.launch {
            val result = NCMusicApp.instance.musicRepository.downloadSong(song, quality) { ratio ->
                // 只更新进度，不动其它字段；ratio 可能为 -1（总长未知）
                _state.value = _state.value.copy(ratio = ratio)
            }
            _state.value = _state.value.copy(
                finished = true,
                success = result is ApiResult.Success,
                ratio = if (result is ApiResult.Success) 1f else _state.value.ratio,
                message = when (result) {
                    is ApiResult.Success -> "已下载：$label"
                    is ApiResult.Error -> "下载失败：${result.message}"
                },
            )
        }
    }

    /** 关闭面板 / 清除结果（用户确认后调用） */
    fun clear() {
        _state.value = Progress()
    }
}
