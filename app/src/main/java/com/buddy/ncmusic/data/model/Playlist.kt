package com.buddy.ncmusic.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String = "",
    @JsonNames("coverImgUrl", "picUrl") val coverImgUrl: String? = null,
    // 官方接口该字段可能返回科学计数法浮点（如 4.095086E7），故用 Double 兼容
    val playCount: Double = 0.0,
    val trackCount: Int = 0,
    val description: String? = null,
    val creator: Creator? = null,
    val tracks: List<Song> = emptyList(),
    val trackIds: List<TrackId> = emptyList(),
    val userId: Long = 0,
    // 5 = 我喜欢的音乐，用于识别特殊歌单
    val specialType: Int = 0,
    // 是否为收藏的歌单（false = 自己创建）
    val subscribed: Boolean = false,
)

@Serializable
data class Creator(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
)
