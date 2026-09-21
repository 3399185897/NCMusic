package com.buddy.ncmusic.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * 歌曲。网易云不同接口字段命名不一致（ar/al 与 artists/album 并存），
 * 通过 @JsonNames 统一兼容。
 */
@Serializable
data class Song(
    val id: Long = 0,
    val name: String = "",
    @JsonNames("ar", "artists") val artists: List<Artist> = emptyList(),
    @JsonNames("al", "album") val album: Album? = null,
    val duration: Long = 0,
    val fee: Int = 0,
    val mv: Long = 0,
    /** 本地文件路径（本地歌单歌曲专用；网络歌曲为空） */
    @kotlinx.serialization.Transient val localPath: String? = null,
) {
    /** 是否为本地歌曲 */
    val isLocal: Boolean get() = !localPath.isNullOrBlank()

    /** 艺人名，多个用 " / " 连接 */
    val artistNames: String
        get() = if (artists.isEmpty()) "未知艺人" else artists.joinToString(" / ") { it.name }

    /** 专辑封面地址 */
    val coverUrl: String
        get() = album?.picUrl.orEmpty()
}

@Serializable
data class Artist(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    @JsonNames("picUrl", "blurPicUrl", "coverImgUrl") val picUrl: String? = null,
)

/** 歌单中的歌曲 ID 占位（playlist/detail 只返回 id 列表，需另行拉取全量歌曲） */
@Serializable
data class TrackId(
    val id: Long = 0,
)
