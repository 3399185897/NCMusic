package com.buddy.ncmusic.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 播放器包装器：让 MediaSession 始终认为「上一曲 / 下一曲」可用。
 *
 * 背景：本项目的播放队列由 [PlaybackManager] 自身维护，ExoPlayer 内始终只有 1 个
 * mediaItem，因此 `COMMAND_SEEK_TO_NEXT / PREVIOUS` 默认为不可用，系统媒体卡片
 * （HyperOS 的音乐控制条）据此隐藏切歌按钮。
 *
 * 这里通过 [ForwardingPlayer] 显式声明这几个命令可用，并把切歌请求转发回
 * [PlaybackManager]（它才持有真实队列），从而让通知栏出现完整的
 * 「上一曲 / 播放暂停 / 下一曲」三个按钮，且全部可点。
 */
class NcPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        -> true

        else -> super.isCommandAvailable(command)
    }

    override fun hasNextMediaItem(): Boolean = PlaybackManager.canNext()

    override fun hasPreviousMediaItem(): Boolean = PlaybackManager.canPrevious()

    override fun seekToNextMediaItem() {
        PlaybackManager.next()
    }

    override fun seekToPreviousMediaItem() {
        PlaybackManager.previous()
    }

    override fun seekToNext() {
        PlaybackManager.next()
    }

    override fun seekToPrevious() {
        PlaybackManager.previous()
    }
}
