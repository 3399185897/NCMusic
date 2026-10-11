package com.buddy.ncmusic.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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

    /**
     * 下载歌曲。
     *
     * ## 保存位置
     *
     * 优先使用用户在设置中选择的目录（`cacheDirUri`，SAF 树 URI）；
     * 未设置时落到 App 专属外部目录（无需额外权限）。
     *
     * > 修复历史 bug：此前无论用户是否设置过下载目录，
     * > 都固定写进 `getExternalFilesDir(DIRECTORY_MUSIC)` ——
     * > 设置项形同虚设，用户永远拿不到自己指定的位置。
     *
     * ## 扩展名
     *
     * 跟随实际编码：无损及以上是 FLAC（写成 .mp3 会让部分播放器拒绝识别）。
     *
     * @return 成功时返回**保存位置的描述文本**，供 UI 直接展示
     */
    suspend fun downloadSong(song: Song, quality: String): ApiResult<String> = safeApiCall {
        val url = api.downloadUrl(song.id, quality)
            ?: throw ApiException(
                -1,
                "无法获取下载地址（该音质不可用，或需要登录 VIP / SVIP 账号）",
            )

        val ctx = NCMusicApp.instance
        val safeName = song.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val ext = api.encodeTypeOf(quality)
        val fileName = "$safeName.$ext"
        val mime = if (ext == "flac") "audio/flac" else "audio/mpeg"

        // 用户设置的下载目录（空串 = 未设置，用 App 专属目录）
        val customUri = ctx.userPreferences.userStateFlow.first().cacheDirUri

        // ---- 1) 用户自定义目录（SAF）优先 ----
        val savedToCustom = customUri.isNotBlank() &&
            saveToSafDir(ctx, android.net.Uri.parse(customUri), fileName, mime, url)

        // ---- 2) 回退：App 专属外部目录 ----
        // 自定义目录写入失败时不直接报错，避免「目录设错了就完全下不了」
        if (!savedToCustom) {
            val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                ?: ctx.filesDir
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            if (!api.downloadToFile(url, file)) throw ApiException(-1, "下载失败")
        }

        // ---- 3) 顺带保存封面与歌词（失败不影响音频本身）----
        val extras = downloadArtifacts(ctx, song, safeName, customUri)

        val where = if (savedToCustom) "已保存到所选目录" else "已保存到应用目录"
        where + "：" + fileName + if (extras.isNotBlank()) "（含 " + extras + "）" else ""
    }

    /**
     * 把下载内容写入用户通过 SAF 选定的目录。
     *
     * 先落到 App 缓存目录再复制过去 —— SAF 只提供 OutputStream，
     * 而现有下载器以 File 为单位。走「临时文件 + 复制」既能复用下载逻辑，
     * 也避免在目标目录留下半截文件（复制失败时直接丢弃临时文件）。
     *
     * @return true 表示成功写入目标目录
     */
    private suspend fun saveToSafDir(
        ctx: android.content.Context,
        treeUri: android.net.Uri,
        fileName: String,
        mime: String,
        url: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = java.io.File(ctx.cacheDir, fileName)
            if (!api.downloadToFile(url, tmp)) return@runCatching false

            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

            // 若同名文件已存在，先删除再新建，避免生成 "name (1).flac"
            val children = android.provider.DocumentsContract
                .buildChildDocumentsUriUsingTree(treeUri, docId)
            ctx.contentResolver.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == fileName) {
                        runCatching {
                            android.provider.DocumentsContract.deleteDocument(
                                ctx.contentResolver,
                                android.provider.DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, c.getString(0)),
                            )
                        }
                    }
                }
            }

            val target = android.provider.DocumentsContract
                .createDocument(ctx.contentResolver, parent, mime, fileName)
                ?: return@runCatching false

            ctx.contentResolver.openOutputStream(target)?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            } ?: return@runCatching false

            tmp.delete()
            true
        }.getOrDefault(false)
    }

    /**
     * 下载完成后顺带保存**封面**与**歌词**，作者 / 专辑信息写入 LRC 头部。
     *
     * 之所以把作者信息放进 LRC 而不是另建一份 json：
     * `[ti:]` / `[ar:]` / `[al:]` 是 LRC 标准标签，任何歌词播放器都能识别；
     * 存成 json 反而没有通用消费者，等于白存。
     *
     * @return 成功保存的附加项描述（如 "歌词+封面"），全部失败时为空串
     */
    private suspend fun downloadArtifacts(
        ctx: android.content.Context,
        song: Song,
        baseName: String,
        customUri: String,
    ): String = withContext(Dispatchers.IO) {
        val done = mutableListOf<String>()

        // ---- 歌词（含元信息头部）----
        runCatching {
            val raw = api.lyric(song.id).lrc?.lyric
            if (!raw.isNullOrBlank()) {
                val header = buildString {
                    appendLine("[ti:" + song.name + "]")
                    appendLine("[ar:" + song.artistNames + "]")
                    appendLine("[al:" + (song.album?.name ?: "") + "]")
                    if (!song.coverUrl.isNullOrBlank()) {
                        appendLine("[cover:" + song.coverUrl + "]")
                    }
                    appendLine()
                }
                if (writeArtifact(ctx, customUri, baseName + ".lrc", (header + raw).toByteArray())) {
                    done += "歌词"
                }
            }
        }

        // ---- 封面 ----
        runCatching {
            val cover = song.coverUrl
            if (!cover.isNullOrBlank()) {
                val tmp = java.io.File(ctx.cacheDir, baseName + ".jpg")
                if (api.downloadToFile(cover, tmp)) {
                    val bytes = tmp.readBytes()
                    tmp.delete()
                    if (writeArtifact(ctx, customUri, baseName + ".jpg", bytes)) {
                        done += "封面"
                    }
                }
            }
        }

        done.joinToString("+")
    }

    /**
     * 写一个附加文件到下载目录（与音频同目录）。
     *
     * 自定义目录走 SAF（`createDocument` + `openOutputStream`）；
     * 否则直接写到 App 专属目录。
     */
    private suspend fun writeArtifact(
        ctx: android.content.Context,
        customUri: String,
        name: String,
        bytes: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val mime = when {
                name.endsWith(".jpg") -> "image/jpeg"
                name.endsWith(".lrc") -> "text/plain"
                else -> "application/octet-stream"
            }
            if (customUri.isNotBlank()) {
                val treeUri = android.net.Uri.parse(customUri)
                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val parent = android.provider.DocumentsContract
                    .buildDocumentUriUsingTree(treeUri, docId)

                // 同名先删，避免系统改名为 "name (1).lrc"
                val children = android.provider.DocumentsContract
                    .buildChildDocumentsUriUsingTree(treeUri, docId)
                ctx.contentResolver.query(
                    children,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1) == name) {
                            runCatching {
                                android.provider.DocumentsContract.deleteDocument(
                                    ctx.contentResolver,
                                    android.provider.DocumentsContract
                                        .buildDocumentUriUsingTree(treeUri, c.getString(0)),
                                )
                            }
                        }
                    }
                }

                val target = android.provider.DocumentsContract
                    .createDocument(ctx.contentResolver, parent, mime, name)
                    ?: return@runCatching false
                ctx.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                    ?: return@runCatching false
                true
            } else {
                val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                    ?: ctx.filesDir
                dir.mkdirs()
                java.io.File(dir, name).writeBytes(bytes)
                true
            }
        }.getOrDefault(false)
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
