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
                level = fetchLevel(profile.userId),
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
            val level = fetchLevel(profile.userId)
            saveSession(cookie, profile.userId, profile.nickname, profile.avatarUrl.orEmpty(), level)
            return UserState(
                cookie = cookie,
                userId = profile.userId,
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl.orEmpty(),
                level = level,
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
            saveSession(
                ck,
                profile.userId,
                profile.nickname,
                profile.avatarUrl.orEmpty(),
                fetchLevel(profile.userId),
            )
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
            // 先写入 cookie，后续请求（含等级）才能以当前用户身份发出
            api.cookie = r.cookie ?: api.cookie.orEmpty()
            saveSession(
                cookie = r.cookie ?: api.cookie.orEmpty(),
                userId = profile.userId,
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl.orEmpty(),
                // 拉取账号等级；失败时置 0，稍后进设置页会再补一次
                level = fetchLevel(profile.userId),
            )
            true
        } else {
            false
        }
    }

    /** 刷新账号等级（设置页进入时调用；失败不覆盖已有值） */
    suspend fun refreshUserLevel(userId: Long): ApiResult<Int> = safeApiCall {
        val lv = api.userDetail(userId)
        if (lv > 0) prefs.setLevel(lv)
        lv
    }

    /**
     * 拉取账号等级。
     *
     * 单独包一层的原因：等级属于**非关键信息**，
     * 接口失败不应让整个登录流程失败，因此吞掉异常返回 0。
     */
    private suspend fun fetchLevel(userId: Long): Int =
        runCatching { api.userDetail(userId) }.getOrDefault(0)

    private suspend fun saveSession(
        cookie: String,
        userId: Long,
        nickname: String,
        avatarUrl: String,
        level: Int = 0,
    ) {
        api.cookie = cookie
        prefs.saveLogin(userId, nickname, avatarUrl, cookie, level)
    }

    // ---------------- 推荐 / 排行榜 ----------------

    suspend fun personalizedPlaylists(): ApiResult<List<Playlist>> = safeApiCall {
        api.personalizedPlaylists(30)
    }

    suspend fun recommendSongs(): ApiResult<List<Song>> = safeApiCall {
        api.recommendSongs()
    }

    /** 雷达歌单（个性化歌单，与推荐歌单不同批次） */
    /** 账号等级 */
    suspend fun userLevel(uid: Long): ApiResult<Int> = safeApiCall { api.userDetail(uid) }

    /**
     * 雷达歌单。
     *
     * limit 默认 20 —— 首页的「正方形无缝拼接」拼贴墙固定使用 18 个方块，
     * 若来源不足 18 个，拼贴会按 `index % size` 循环取用而出现**封面重复**。
     * 取 20 可确保 18 个方块各用一张不同封面。
     */
    suspend fun radarPlaylists(limit: Int = 20): ApiResult<List<Playlist>> = safeApiCall {
        api.radarPlaylists(limit)
    }

    /** 为你推荐（个性化，与每日推荐不同批次） */
    suspend fun forYouSongs(): ApiResult<List<com.buddy.ncmusic.data.model.Song>> = safeApiCall {
        api.forYouSongs()
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
