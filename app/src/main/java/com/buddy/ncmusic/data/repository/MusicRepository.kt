package com.buddy.ncmusic.data.repository

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
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

    /**
     * Cookie 是否已恢复到 [DirectApi]。
     *
     * ## 为什么需要这个标记（修复"更新后每日推荐消失"）
     *
     * 启动时序原本是：
     * ```
     * NCMusicApp.onCreate  → appScope.launch { restoreCookie(...) }   // 异步
     * HomeViewModel.init   → load() → recommendSongs()                // 立刻发起
     * ```
     * 两者**并发**。若 `recommendSongs()` 抢在 `restoreCookie` 之前发出，
     * 请求就**不带 Cookie** → 服务端按未登录处理 → 每日推荐返回空。
     *
     * 更麻烦的是它**不会自愈**：`HomeViewModel` 里的
     * `collectLatest { if (state.isLoggedIn) loadDaily() }`
     * 只在 `isLoggedIn` **发生变化**时才触发，而初始值本就是 true ——
     * 所以这次失败后再没有任何时机重试，用户只能重新登录
     * （重新登录会写 Cookie，从而触发一次重载）。
     *
     * 升级后首次启动尤其明显：DataStore 需要从磁盘读取，
     * 恢复耗时更长，竞态窗口更大。
     */
    @Volatile
    private var cookieRestored = false

    /** 保护首次恢复，避免多个并发请求同时读写 Cookie */
    private val cookieMutex = Mutex()

    /**
     * 启动时恢复登录 Cookie（显式路径）。
     *
     * 与 [ensureCookieRestored] 配合：这里直接从内存传值，
     * 无需再读 DataStore，但同样要置位就绪标记。
     */
    fun restoreCookie(cookie: String?) {
        api.cookie = cookie?.takeIf { it.isNotBlank() }
        cookieRestored = true
    }

    /**
     * 确保 Cookie 已恢复（**惰性、幂等、并发安全**）。
     *
     * 需登录的接口在发起请求前调用它。若 [restoreCookie] 尚未跑完，
     * 这里会**直接读一次 DataStore** 补上，从而彻底消除启动时序依赖 ——
     * 请求不再依赖"启动流程是否及时完成"，而是"自己需要时就取"。
     *
     * 之所以不改成阻塞式启动：在 `Application.onCreate` 里同步读 DataStore
     * 会拖慢冷启动，且 DataStore 本身不提供阻塞 API。惰性按需恢复
     * 既不影响启动速度，又能保证正确性。
     */
    private suspend fun ensureCookieRestored() {
        if (cookieRestored) return
        cookieMutex.withLock {
            // 双重检查：可能在等锁期间已被其它协程完成
            if (cookieRestored) return
            runCatching {
                val cookie = prefs.userStateFlow.first().cookie
                api.cookie = cookie.takeIf { it.isNotBlank() }
            }
            cookieRestored = true
        }
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
        // 需登录：先确保 Cookie 就绪，避免启动竞态导致返回空列表
        ensureCookieRestored()
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
        ensureCookieRestored()
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
        ensureCookieRestored()
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
        // 文件名 = 歌名 + 歌手。
        //
        // 只用歌名会产生**跨歌手同名覆盖**：例如多首《偏偏喜欢你》
        // 会被写成同一个文件，后下载的悄悄替掉先前的。
        // 歌手缺省时退回歌名，避免生成 "歌名 - .flac" 这种畸形文件名。
        val safeName = buildDownloadBaseName(song)
        val ext = api.encodeTypeOf(quality)
        val fileName = "$safeName.$ext"
        val mime = if (ext == "flac") "audio/flac" else "audio/mpeg"

        // 本地临时文件的基础名：**带歌曲 ID**。
        //
        // 为什么必须带 ID：临时文件落在 `cacheDir` 这一**全局共享**目录里，
        // 若只用文件名，两首同名歌曲并发下载会互相覆盖对方的临时数据，
        // 结果是两个文件都损坏（或其中一个拿到对方的音频）。
        // ID 让每个下载任务拥有独立的工作区。
        val tmpBase = "dl_${song.id}_${System.nanoTime()}_$safeName"

        // 用户设置的下载目录（空串 = 未设置，用 App 专属目录）
        val customUri = ctx.userPreferences.userStateFlow.first().cacheDirUri

        // ---- 1) 用户自定义目录（SAF）优先 ----
        val savedToCustom = customUri.isNotBlank() &&
            saveToSafDir(ctx, android.net.Uri.parse(customUri), fileName, mime, url, tmpBase)

        // ---- 2) 回退：App 专属外部目录 ----
        // 自定义目录写入失败时不直接报错，避免「目录设错了就完全下不了」
        if (!savedToCustom) {
            val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                ?: ctx.filesDir
            dir.mkdirs()
            val target = java.io.File(dir, fileName)

            // 先下载到**唯一命名的临时文件**，成功后再原子替换目标文件。
            //
            // 原先直接 `downloadToFile(url, target)` —— 中途失败会把已经存在的
            // 完整文件截断成半截，用户既丢了旧文件又没拿到新文件。
            val tmp = java.io.File(ctx.cacheDir, "$tmpBase.$ext")
            try {
                if (!api.downloadToFile(url, tmp)) {
                    throw ApiException(-1, "下载失败（网络中断或数据不完整）")
                }
                replaceFileAtomically(tmp, target)
            } finally {
                // 无论成功失败都清理临时文件，避免 cacheDir 堆积
                if (tmp.exists()) tmp.delete()
            }
        }

        // ---- 3) 顺带保存封面与歌词（失败不影响音频本身）----
        // 下载完成后按用户设置的「最大缓存大小」回收缓存。
        //
        // 这一步让该设置**真正生效** —— 此前它只被读取显示，
        // 从未参与任何淘汰逻辑，属于无效设置。
        // 剔除保护：刚写入的临时文件不能在本轮被删掉。
        runCatching { NCMusicApp.instance.cacheManager.enforceLimit() }

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
    /**
     * 把下载内容写入用户通过 SAF 选定的目录。
     *
     * ## 写入策略（三步，任一步失败都不破坏原文件）
     *
     * 1. 音频先完整落到 cacheDir 的**唯一命名**临时文件（在 [downloadSong] 中完成）
     * 2. 复制到目标目录下一个**唯一命名的临时文档**（`.ncmusic_tmp_*`）
     * 3. 内容就绪后，**先尝试 `renameDocument` 就位**；
     *    仅当就位成功，才去清理同名旧文件
     *
     * ## 为什么顺序是"先就位、后删旧"
     *
     * 上一版写的是「先删同名旧文件 → 再 renameDocument」。
     * 若 rename 失败，就会走进回退分支；而回退分支也失败时，
     * **旧文件已经删除、新文件又没建起来** —— 用户数据直接丢失（问题 6）。
     *
     * 现在改为：就位失败时**先不动旧文件**，只在回退复制成功后才清理。
     * 两条路都失败时，旧文件仍在原位，用户至少还有原来的完整文件。
     *
     * @param tmpBase cacheDir 中临时文件的基础名（含歌曲 ID，保证唯一）
     */
    private suspend fun saveToSafDir(
        ctx: android.content.Context,
        treeUri: android.net.Uri,
        fileName: String,
        mime: String,
        url: String,
        tmpBase: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val tmp = java.io.File(ctx.cacheDir, "$tmpBase.tmp")
        try {
            runCatching {
                if (!api.downloadToFile(url, tmp)) return@runCatching false

                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val parent = android.provider.DocumentsContract
                    .buildDocumentUriUsingTree(treeUri, docId)

                // ---- 2) 复制到一个唯一命名的临时文档 ----
                val stagingName = ".ncmusic_tmp_${System.currentTimeMillis()}_$fileName"
                val staging = android.provider.DocumentsContract
                    .createDocument(ctx.contentResolver, parent, mime, stagingName)
                    ?: return@runCatching false

                val written = runCatching {
                    ctx.contentResolver.openOutputStream(staging)?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    } != null
                }.getOrDefault(false)

                if (!written) {
                    // 写入失败：清掉临时文档，**原文件保持原样**
                    deleteDocumentQuietly(ctx, staging)
                    return@runCatching false
                }

                // ---- 3) 先尝试改名就位 ----
                val renamed = runCatching {
                    android.provider.DocumentsContract
                        .renameDocument(ctx.contentResolver, staging, fileName) != null
                }.getOrDefault(false)

                if (renamed) {
                    // 就位成功后才删旧：此时新文件已存在，删除是安全的收尾动作
                    deleteSameNameDocuments(ctx, treeUri, docId, fileName, keepStaging = null)
                    return@runCatching true
                }

                // ---- 回退：复制到最终名 ----
                // 注意此处**仍未删除旧文件** —— 只有复制成功才会去清理它。
                val target = android.provider.DocumentsContract
                    .createDocument(ctx.contentResolver, parent, mime, fileName)
                if (target == null) {
                    deleteDocumentQuietly(ctx, staging)
                    return@runCatching false
                }

                val copied = runCatching {
                    ctx.contentResolver.openOutputStream(target)?.use { out ->
                        ctx.contentResolver.openInputStream(staging)?.use { it.copyTo(out) }
                    } != null
                }.getOrDefault(false)

                deleteDocumentQuietly(ctx, staging)

                if (!copied) {
                    // 回退也失败：删除刚建出的半截目标（若有），旧文件依然完好
                    deleteDocumentQuietly(ctx, target)
                    return@runCatching false
                }

                // 复制成功，清理同名旧文件（此时 target 已是完整内容）
                deleteSameNameDocuments(ctx, treeUri, docId, fileName, keepStaging = target)
                true
            }.getOrDefault(false)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** 删除一个文档，忽略失败（清理动作不应影响主流程结果） */
    private fun deleteDocumentQuietly(ctx: android.content.Context, uri: android.net.Uri) {
        runCatching {
            android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, uri)
        }
    }

    /**
     * 删除目标目录下所有 [fileName] 同名文档。
     *
     * @param keepStaging 本次刚写入的文档，**跳过不删** ——
     *   否则可能把自己刚写好的文件删掉（注意需比较 documentId）
     */
    private fun deleteSameNameDocuments(
        ctx: android.content.Context,
        treeUri: android.net.Uri,
        docId: String,
        fileName: String,
        keepStaging: android.net.Uri?,
    ) {
        val keepId = keepStaging?.let {
            runCatching { android.provider.DocumentsContract.getDocumentId(it) }.getOrNull()
        }
        val children = android.provider.DocumentsContract
            .buildChildDocumentsUriUsingTree(treeUri, docId)
        runCatching {
            ctx.contentResolver.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    if (c.getString(1) == fileName && id != keepId) {
                        runCatching {
                            android.provider.DocumentsContract.deleteDocument(
                                ctx.contentResolver,
                                android.provider.DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, id),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 用 [src] 的内容**原子地**替换 [dst]。
     *
     * 做法：先把 src 改名为 dst 临时兄弟名，再删旧 dst，最后改名就位。
     * 之所以不是「先删 dst → 再改名」：那样在两步之间失败就两头落空。
     *
     * 若同目录改名不被支持（跨设备等），退回 `copyTo` 覆盖 ——
     * 此时才可能出现短暂不完整窗口，但源数据仍在，可重试。
     *
     * @return true 表示目标文件已是新内容
     */
    private fun replaceFileAtomically(src: java.io.File, dst: java.io.File): Boolean {
        dst.parentFile?.mkdirs()

        // 首选：同目录内改名（POSIX 下原子）
        val staged = java.io.File(dst.parentFile, ".ncmusic_stage_${System.nanoTime()}_${dst.name}")
        if (runCatching { src.renameTo(staged) }.getOrDefault(false)) {
            // 旧文件先备份名，改名成功后删除；失败则回滚
            val backup = java.io.File(dst.parentFile, ".ncmusic_old_${System.nanoTime()}_${dst.name}")
            val hadOld = dst.exists() && runCatching { dst.renameTo(backup) }.getOrDefault(false)

            val ok = runCatching { staged.renameTo(dst) }.getOrDefault(false)
            if (ok) {
                if (hadOld) backup.delete()
                return true
            }
            // 就位失败：把旧文件挪回来，保证用户原有文件不丢
            if (hadOld) runCatching { backup.renameTo(dst) }
            staged.delete()
            return false
        }

        // 回退：直接复制覆盖（源仍保留，可重试）
        return runCatching {
            src.inputStream().use { input ->
                dst.outputStream().use { out -> input.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 生成下载文件的基础名（不含扩展名）。
     *
     * 规则：`歌名 - 歌手`，并对文件系统非法字符做替换。
     * 歌手为空时不追加分隔符，防止出现 `歌名 - ` 这样的尾随符号。
     *
     * 之所以把歌手拼进来：仅用歌名会让**不同歌手的同名曲目互相覆盖**，
     * 用户下载后只得到一个文件，另一个悄悄丢失。
     */
    private fun buildDownloadBaseName(song: Song): String {
        fun sanitize(raw: String): String =
            raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

        val title = sanitize(song.name).ifBlank { "未命名" }
        val artist = sanitize(song.artistNames)
        val base = if (artist.isBlank()) title else "$title - $artist"

        // 过长的文件名在部分文件系统上会保存失败，按 120 字截断
        return if (base.length > 120) base.take(120) else base
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

                // 与音频走同一套策略：先写唯一临时名，写完再替换同名旧文件。
                // 好处是「同名先删」那一步挪到了内容就绪之后，
                // 中途失败不会把已有的歌词 / 封面删掉。
                val stagingName = ".ncmusic_tmp_${System.currentTimeMillis()}_$name"
                val staging = android.provider.DocumentsContract
                    .createDocument(ctx.contentResolver, parent, mime, stagingName)
                    ?: return@runCatching false

                val written = runCatching {
                    ctx.contentResolver.openOutputStream(staging)?.use { it.write(bytes) } != null
                }.getOrDefault(false)

                if (!written) {
                    deleteDocumentQuietly(ctx, staging)
                    return@runCatching false
                }

                // 与音频同策略：**先就位、后删旧**。
                // 若顺序反过来，一旦就位失败就会两头落空（旧文件已删、新文件没有）。
                val renamed = runCatching {
                    android.provider.DocumentsContract
                        .renameDocument(ctx.contentResolver, staging, name) != null
                }.getOrDefault(false)

                if (renamed) {
                    deleteSameNameDocuments(ctx, treeUri, docId, name, keepStaging = null)
                    return@runCatching true
                }

                // 改名不受支持：复制到最终名，成功后才清理旧文件
                val target = android.provider.DocumentsContract
                    .createDocument(ctx.contentResolver, parent, mime, name)
                if (target == null) {
                    deleteDocumentQuietly(ctx, staging)
                    return@runCatching false
                }
                val copied = runCatching {
                    ctx.contentResolver.openOutputStream(target)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
                deleteDocumentQuietly(ctx, staging)
                if (!copied) {
                    deleteDocumentQuietly(ctx, target)
                    return@runCatching false
                }
                deleteSameNameDocuments(ctx, treeUri, docId, name, keepStaging = target)
                true
            } else {
                val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                    ?: ctx.filesDir
                dir.mkdirs()
                // 先写唯一临时文件，再用原子替换就位 ——
                // 直接 writeBytes 到目标会在中途失败时截断已有文件
                val tmp = java.io.File(ctx.cacheDir, "art_${System.nanoTime()}_$name")
                try {
                    tmp.writeBytes(bytes)
                    replaceFileAtomically(tmp, java.io.File(dir, name))
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
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

    /**
     * 解析播放地址。
     *
     * 返回 [DirectApi.ResolvedUrl] 而非裸字符串 —— 其中携带
     * **服务端实际给出的音质档位**（可能因权限/版权低于请求值），
     * 供播放器展示**真实**音质，而不是照搬用户设置。
     */
    suspend fun songUrl(id: Long, level: String): ApiResult<DirectApi.ResolvedUrl> = safeApiCall {
        api.songUrl(id, level) ?: throw ApiException(-1, "该歌曲暂无可用播放源")
    }

    suspend fun lyric(id: Long): ApiResult<LyricResponse> = safeApiCall {
        api.lyric(id)
    }

    // ---------------- 喜欢 / 歌单 / 历史 ----------------

    suspend fun likeSong(id: Long, like: Boolean): ApiResult<Boolean> = safeApiCall {
        ensureCookieRestored()
        api.likeSong(id, like)
    }

    suspend fun likelist(uid: Long): ApiResult<List<Long>> = safeApiCall {
        ensureCookieRestored()
        api.likelist(uid)
    }

    suspend fun createPlaylist(name: String): ApiResult<Long> = safeApiCall {
        api.createPlaylist(name) ?: throw ApiException(-1, "创建歌单失败，请确认已登录")
    }

    suspend fun playHistory(uid: Long): ApiResult<List<Song>> = safeApiCall {
        ensureCookieRestored()
        api.playHistory(uid)
    }

    suspend fun identifySong(audioBytes: ByteArray): ApiResult<Song?> = safeApiCall {
        api.identifySong(audioBytes)
    }

    // ---------------- 通用 ----------------

    /**
     * 统一的结果封装入口。
     *
     * ## 为什么要分类而不是只取 message
     *
     * 原先的实现把所有异常一律压成 `ApiResult.Error(e.message ?: "网络请求失败")`：
     * 网络断了、JSON 解析炸了、登录过期、服务端给了业务错误码 ——
     * 到了 UI 层**全都是同一句话**，用户无法判断该重试、该登录还是该放弃。
     *
     * 现在按异常类型映射成 [ApiResult.ErrorKind]，
     * 让每个界面能据此给出**不同的引导动作**。
     */
    private suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        // 协程取消不是错误，必须原样抛出，否则会破坏结构化并发
        throw e
    } catch (e: ApiException) {
        // 业务错误码：301 表示登录态失效，需要单独归类以便引导登录
        ApiResult.Error(
            message = e.message ?: "请求失败",
            code = e.code,
            kind = if (e.code == 301 || e.code == -462) ApiResult.ErrorKind.AUTH
            else ApiResult.ErrorKind.BUSINESS,
        )
    } catch (e: DirectApi.ApiNetworkException) {
        ApiResult.Error(e.message ?: "网络连接失败", kind = ApiResult.ErrorKind.NETWORK)
    } catch (e: DirectApi.ApiHttpException) {
        // 4xx/5xx：401/403 归为登录问题，其余算网络层失败
        ApiResult.Error(
            message = if (e.httpCode == 401 || e.httpCode == 403) {
                "登录状态已失效，请重新登录"
            } else {
                "服务暂时不可用（HTTP ${e.httpCode}）"
            },
            code = e.httpCode,
            kind = if (e.httpCode == 401 || e.httpCode == 403) ApiResult.ErrorKind.AUTH
            else ApiResult.ErrorKind.NETWORK,
        )
    } catch (e: DirectApi.ApiParseException) {
        ApiResult.Error(e.message ?: "响应数据异常", kind = ApiResult.ErrorKind.PARSE)
    } catch (e: java.io.IOException) {
        ApiResult.Error(e.message ?: "网络连接失败", kind = ApiResult.ErrorKind.NETWORK)
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "请求失败", kind = ApiResult.ErrorKind.BUSINESS)
    }
}
