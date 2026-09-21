package com.buddy.ncmusic.data.model

import kotlinx.serialization.Serializable

// ---------------- 登录 ----------------

@Serializable
data class QrKeyResponse(
    val code: Int = 0,
    val data: QrKeyData? = null,
)

@Serializable
data class QrKeyData(val unikey: String = "")

@Serializable
data class QrCreateResponse(
    val code: Int = 0,
    val data: QrCreateData? = null,
)

@Serializable
data class QrCreateData(
    val qrurl: String? = null,
    val qrimg: String? = null,
)

/** 二维码轮询结果：800 待扫 / 801 已扫未确认 / 802 已授权 / 803 过期 */
@Serializable
data class QrCheckResponse(
    val code: Int = 0,
    val message: String? = null,
    val cookie: String? = null,
)

@Serializable
data class LoginStatusResponse(
    val code: Int = 0,
    val cookie: String? = null,
    val account: Account? = null,
    val profile: Profile? = null,
    val data: LoginStatusData? = null,
) {
    val isLoggedIn: Boolean
        get() = (data?.code ?: code) == 200

    val userAccount: Account?
        get() = account ?: data?.account

    val userProfile: Profile?
        get() = profile ?: data?.profile
}

@Serializable
data class LoginStatusData(
    val code: Int = 0,
    val account: Account? = null,
    val profile: Profile? = null,
)

@Serializable
data class Account(
    val id: Long = 0,
    val userName: String = "",
)

@Serializable
data class Profile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val signature: String? = null,
)

// ---------------- 推荐 / 排行榜 ----------------

@Serializable
data class PersonalizedResponse(
    val code: Int = 0,
    val result: List<Playlist> = emptyList(),
)

@Serializable
data class RecommendSongsResponse(
    val code: Int = 0,
    val data: RecommendSongsData? = null,
)

@Serializable
data class RecommendSongsData(
    val dailySongs: List<Song> = emptyList(),
)

@Serializable
data class ToplistDetailResponse(
    val code: Int = 0,
    val list: List<Playlist> = emptyList(),
)

// ---------------- 搜索 ----------------

@Serializable
data class SearchResponse(
    val code: Int = 0,
    val result: SearchResult? = null,
)

@Serializable
data class SearchResult(
    val songs: List<Song> = emptyList(),
    val songCount: Int = 0,
)

@Serializable
data class SearchHotResponse(
    val code: Int = 0,
    val data: List<HotWord> = emptyList(),
)

@Serializable
data class HotWord(val searchWord: String = "")

// ---------------- 歌单 ----------------

@Serializable
data class UserPlaylistResponse(
    val code: Int = 0,
    val playlist: List<Playlist> = emptyList(),
)

@Serializable
data class PlaylistDetailResponse(
    val code: Int = 0,
    val playlist: Playlist? = null,
)

@Serializable
data class PlaylistTracksResponse(
    val code: Int = 0,
    val songs: List<Song> = emptyList(),
)

// ---------------- 歌曲 / 歌词 ----------------

@Serializable
data class SongDetailResponse(
    val code: Int = 0,
    val songs: List<Song> = emptyList(),
)

@Serializable
data class SongUrlResponse(
    val code: Int = 0,
    val data: List<SongUrlData> = emptyList(),
)

@Serializable
data class SongUrlData(
    val id: Long = 0,
    val url: String? = null,
    val br: Int = 0,
    val level: String? = null,
    val fee: Int = 0,
)

@Serializable
data class LyricResponse(
    val code: Int = 0,
    val lrc: LyricData? = null,
    val tlyric: LyricData? = null,
)

@Serializable
data class LyricData(val lyric: String? = null)
