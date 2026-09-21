package com.buddy.ncmusic.data.repository

import android.graphics.Bitmap
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.core.network.direct.DirectApi
import com.buddy.ncmusic.data.local.UserPreferences
import com.buddy.ncmusic.data.local.UserState
import com.buddy.ncmusic.data.model.LoginStatusResponse
import com.buddy.ncmusic.data.model.LyricResponse
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.QrCheckResponse
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.util.ApiException
import com.buddy.ncmusic.util.ApiResult
import com.buddy.ncmusic.util.QrCodeUtil

/**
 * 数据仓库：App 内置加密直连网易云官方，无需任何中间服务器。
 */
class MusicRepository(private val prefs: UserPreferences) {

    private val api = DirectApi()

    /** 启动时恢复登录 Cookie */
    fun restoreCookie(cookie: String?) {
        api.cookie = cookie?.takeIf { it.isNotBlank() }
    }

    // ---------------- 登录 ----------------

    suspend fun createQrKey(): ApiResult<String> = safeApiCall {
        api.qrKey() ?: throw ApiException(-1, "获取二维码失败")
    }

    /** 本地渲染登录二维码（纯本地，无网络），失败返回 null */
    fun createQrBitmap(key: String): Bitmap? =
        QrCodeUtil.toBitmap("https://music.163.com/login?codekey=$key")

    suspend fun checkQr(key: String): ApiResult<QrCheckResponse> = safeApiCall {
        val r = api.qrCheck(key)
        // 网易云语义：801 待扫码 / 802 已扫码待确认 / 803 授权成功。
        // 只有 803 的响应才携带登录 Cookie，此前用 802 会拿不到 Cookie 导致假成功。
        if (r.code == 803) finishQrLogin(r.cookie)
        r
    }

    private suspend fun finishQrLogin(cookie: String?) {
        if (!cookie.isNullOrBlank()) api.cookie = cookie
        runCatching {
            val status = api.loginStatus()
            val profile = status.userProfile ?: return@runCatching
            saveSession(
                cookie = status.cookie ?: api.cookie.orEmpty(),
                userId = profile.userId,
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl.orEmpty(),
            )
        }
    }

    suspend fun cellphoneLogin(phone: String, password: String): ApiResult<UserState> = safeApiCall {
        finishLogin(api.cellphoneLogin(phone, password), "登录失败，请检查账号密码")
    }

    /** 发送短信验证码 */
    suspend fun sendCaptcha(phone: String): ApiResult<Boolean> = safeApiCall {
        if (!api.sendCaptcha(phone)) throw ApiException(-1, "验证码发送失败，请稍后重试")
        true
    }

    /** 验证码登录 */
    suspend fun captchaLogin(phone: String, captcha: String): ApiResult<UserState> = safeApiCall {
        finishLogin(api.captchaLogin(phone, captcha), "登录失败，请检查验证码")
    }

    private suspend fun finishLogin(r: LoginStatusResponse, errorMsg: String): UserState {
        val profile = r.userProfile
        if (r.isLoggedIn && profile != null) {
            val cookie = r.cookie ?: api.cookie.orEmpty()
            saveSession(cookie, profile.userId, profile.nickname, profile.avatarUrl.orEmpty())
            return UserState(
                cookie = cookie,
                userId = profile.userId,
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl.orEmpty(),
            )
        }
        throw ApiException(r.code, errorMsg)
    }

    /**
     * 使用 Cookie 登录：用户从浏览器 / 其他客户端复制含 MUSIC_U 的 Cookie。
     * 校验通过后持久化，后续请求自动携带。
     */
    suspend fun loginWithCookie(rawCookie: String): ApiResult<Boolean> = safeApiCall {
        val ck = rawCookie.trim()
        if (ck.isBlank() || !ck.contains("MUSIC_U")) {
            throw ApiException(-1, "Cookie 格式不正确，需包含 MUSIC_U")
        }
        api.cookie = ck
        val r = api.loginStatus()
        val profile = r.userProfile
        if (r.isLoggedIn && profile != null) {
            saveSession(ck, profile.userId, profile.nickname, profile.avatarUrl.orEmpty())
            true
        } else {
            api.cookie = null
            throw ApiException(-1, "Cookie 无效或已过期")
        }
    }

    suspend fun logout(): ApiResult<Unit> = safeApiCall {
        api.cookie = null
        prefs.clearLogin()
    }

    suspend fun checkLoginStatus(): ApiResult<Boolean> = safeApiCall {
        val r = api.loginStatus()
        val profile = r.userProfile
        if (r.isLoggedIn && profile != null) {
            saveSession(
                cookie = r.cookie ?: api.cookie.orEmpty(),
                userId = profile.userId,
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl.orEmpty(),
            )
            true
        } else {
            false
        }
    }

    private suspend fun saveSession(cookie: String, userId: Long, nickname: String, avatarUrl: String) {
        api.cookie = cookie
        prefs.saveLogin(userId, nickname, avatarUrl, cookie)
    }

    // ---------------- 推荐 / 排行榜 ----------------

    suspend fun personalizedPlaylists(): ApiResult<List<Playlist>> = safeApiCall {
        api.personalizedPlaylists(30)
    }

    suspend fun recommendSongs(): ApiResult<List<Song>> = safeApiCall {
        api.recommendSongs()
    }

    suspend fun toplist(): ApiResult<List<Playlist>> = safeApiCall {
        api.toplist()
    }

    // ---------------- 搜索 ----------------

    suspend fun search(keyword: String): ApiResult<List<Song>> = safeApiCall {
        api.search(keyword, limit = 30, offset = 0)
    }

    suspend fun searchHot(): ApiResult<List<String>> = safeApiCall {
        api.searchHot()
    }

    // ---------------- 歌单 ----------------

    suspend fun userPlaylists(uid: Long): ApiResult<List<Playlist>> = safeApiCall {
        api.userPlaylists(uid)
    }

    suspend fun artistDetail(artistId: Long): ApiResult<Pair<String, List<Song>>> = safeApiCall {
        api.artistDetail(artistId)
    }

    suspend fun radarSongs(): ApiResult<List<Song>> = safeApiCall {
        api.radarSongs()
    }

    suspend fun subscribePlaylist(id: Long, subscribe: Boolean): ApiResult<Boolean> = safeApiCall {
        api.subscribePlaylist(id, subscribe)
    }

    /** 下载歌曲到本地音乐目录（App 专属外部目录，无需额外权限） */
    suspend fun downloadSong(song: Song, quality: String): ApiResult<java.io.File> = safeApiCall {
        val url = api.songUrl(song.id, quality) ?: throw ApiException(-1, "无法获取下载地址")
        val ctx = NCMusicApp.instance
        val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
        dir.mkdirs()
        val safeName = song.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = java.io.File(dir, "$safeName.mp3")
        if (!api.downloadToFile(url, file)) throw ApiException(-1, "下载失败")
        file
    }

    suspend fun playlistDetail(id: Long): ApiResult<Playlist> = safeApiCall {
        api.playlistDetail(id)
    }

    suspend fun playlistTracks(id: Long): ApiResult<List<Song>> = safeApiCall {
        api.playlistDetail(id).tracks
    }

    // ---------------- 歌曲 / 歌词 ----------------

    suspend fun songUrl(id: Long, level: String): ApiResult<String> = safeApiCall {
        api.songUrl(id, level) ?: throw ApiException(-1, "该歌曲暂无可用播放源")
    }

    suspend fun lyric(id: Long): ApiResult<LyricResponse> = safeApiCall {
        api.lyric(id)
    }

    // ---------------- 喜欢 / 歌单 / 历史 ----------------

    suspend fun likeSong(id: Long, like: Boolean): ApiResult<Boolean> = safeApiCall {
        api.likeSong(id, like)
    }

    suspend fun likelist(uid: Long): ApiResult<List<Long>> = safeApiCall {
        api.likelist(uid)
    }

    suspend fun createPlaylist(name: String): ApiResult<Long> = safeApiCall {
        api.createPlaylist(name) ?: throw ApiException(-1, "创建歌单失败，请确认已登录")
    }

    suspend fun playHistory(uid: Long): ApiResult<List<Song>> = safeApiCall {
        api.playHistory(uid)
    }

    suspend fun identifySong(audioBytes: ByteArray): ApiResult<Song?> = safeApiCall {
        api.identifySong(audioBytes)
    }

    // ---------------- 通用 ----------------

    private suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "网络请求失败")
    }
}
