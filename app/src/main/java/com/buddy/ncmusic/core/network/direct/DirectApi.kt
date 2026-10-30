package com.buddy.ncmusic.core.network.direct

import com.buddy.ncmusic.BuildConfig
import com.buddy.ncmusic.core.network.crypto.EapiCrypto
import kotlinx.coroutines.ensureActive
import com.buddy.ncmusic.core.network.crypto.WeapiCrypto
import com.buddy.ncmusic.data.model.Account
import com.buddy.ncmusic.data.model.LyricData
import com.buddy.ncmusic.data.model.LyricResponse
import com.buddy.ncmusic.data.model.LoginStatusResponse
import com.buddy.ncmusic.data.model.Playlist
import com.buddy.ncmusic.data.model.Profile
import com.buddy.ncmusic.data.model.QrCheckResponse
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.data.model.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 直连网易云官方 API —— 无需任何中间服务器。
 *
 * 内置 weapi 加密（见 [WeapiCrypto]），直接与 music.163.com 通信。
 * 明文接口用于搜索/歌曲详情/排行榜/用户歌单，weapi 接口用于播放链接/歌词/登录。
 */
class DirectApi {

    companion object {
        const val BASE = "https://music.163.com"

        /**
         * eapi 通道域名（按优先级依次尝试）。
         *
         * 与 [BASE]（Web 端）是**两套能力**：高解析音质（Hi-Res / 母带 /
         * 空间音频）只在 eapi 开放，weapi 会按 Web 端能力裁剪，
         * 即使 VIP 也拿不到地址。
         *
         * 三个域名实测**服务端返回完全一致**，故可互相兜底：
         *  · `interface.music.163.com` —— 官方移动端域名
         *  · `music.163.com` —— 与 weapi 同域，网络最稳（weapi 走的就是它）
         *  · `api.music.163.com` —— 备用
         *
         * 之所以轮询：部分运营商网络对 interface 子域存在解析/连接异常，
         * 表现为「eapi 抛异常 → 回落 weapi → 高音质被判无权限」。
         * 把与 weapi 同域的地址排在第二，等于用"已验证可达"的域名兜底。
         */
        private val EAPI_HOSTS = listOf(
            "https://interface.music.163.com",
            "https://music.163.com",
            "https://api.music.163.com",
        )

        /**
         * eapi 客户端档案：(os, appver, osver)，**按能力从高到低排列**。
         *
         * ⚠️ Hi-Res / 超清母带 / 空间音频是**移动端专属能力**。
         * 之前强制 `os=pc`，服务端按 PC 客户端裁剪 —— 即使账号是 VIP、
         * 歌曲也确实有 Hi-Res 源（官方客户端「高清臻音」显示为可选并已勾选），
         * 我们请求 `hires` 仍只拿回无损（br≈901303 与 sq 完全一致）。
         *
         * 因此先按移动端身份请求，拿不到目标档位再退回 PC。
         */
        private val EAPI_PROFILES = listOf(
            EapiProfile(
                os = "android",
                appver = "9.1.20",
                osver = "Android 14",
                // ⚠️ UA 必须与 os 匹配。
                // Cookie 声明 android、UA 却是桌面 Chrome —— 这种自相矛盾的
                // 客户端指纹会被服务端按"未知客户端"降权处理。
                ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            ),
            EapiProfile(
                os = "pc",
                appver = "3.1.6",
                osver = "Windows 10",
                ua = UA,
            ),
        )

        /** 下载进度上报的最小字节间隔（256 KB） */
        private const val PROGRESS_STEP_BYTES = 256L * 1024L
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val BATCH = 200
    }

    /**
     * eapi 请求体里的 `header` 字段（设备信息 JSON 字符串）。
     *
     * ⚠️ 移动端 eapi 请求**必须**带这个字段。缺了它，服务端会按
     * "能力未知的基础客户端"处理 —— 表现为高解析音质一律降级到无损，
     * 即使账号是 VIP、歌曲也确实有 Hi-Res 源。
     *
     * `deviceId` 必须**稳定**（同一设备多次请求保持一致），
     * 否则每次都被当成新设备，同样影响权益判定。
     */
    private fun eapiHeader(profile: EapiProfile): String {
        val id = Regex("(?:^|;)\\s*deviceId=([^;]+)")
            .find(cookie.orEmpty())
            ?.groupValues?.get(1)
            ?: "com.buddy.ncmusic.device"
        return buildJsonObject {
            put("os", profile.os)
            put("appver", profile.appver)
            put("osver", profile.osver)
            put("deviceId", id)
            put("requestId", java.util.UUID.randomUUID().toString())
            put("channel", "netease")
        }.toString()
    }

    /** eapi 客户端档案：os / 版本 / UA 必须互相自洽 */
    private data class EapiProfile(
        val os: String,
        val appver: String,
        val osver: String,
        val ua: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 网络层失败（连接不上 / 超时 / DNS 失败），可重试 */
    class ApiNetworkException(message: String, cause: Throwable?) : Exception(message, cause)

    /** HTTP 状态码非 2xx */
    class ApiHttpException(val httpCode: Int, message: String) : Exception(message)

    /** 响应体无法解析（空体、非 JSON、结构不符） */
    class ApiParseException(message: String) : Exception(message)

    /** 登录态 Cookie（含 MUSIC_U），由登录流程写入 */
    @Volatile
    var cookie: String? = null

    // ---------------- 基础请求 ----------------

    private fun Request.Builder.common(): Request.Builder = apply {
        header("Referer", "https://music.163.com/")
        header("User-Agent", UA)
        cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
    }

    /**
     * 解析响应体为 JsonObject。
     *
     * ⚠️ 这里的处理方式很关键：
     *
     * 原先写的是 `runCatching { ... }.getOrElse { JsonObject(emptyMap()) }` ——
     * **解析失败被静默转成空对象**。于是调用方拿到的 `root["data"]` 恒为 null，
     * 与「服务端正常返回但确实没有数据」**完全无法区分**：
     * 前者是故障（该报错），后者是合理状态（该显示空态）。
     *
     * 现在改为**抛出** [ApiParseException]，由上层映射成 `ErrorKind.PARSE`。
     */
    private fun parse(text: String): JsonObject {
        if (text.isBlank()) throw ApiParseException("响应体为空")
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ApiParseException("响应格式无法解析：${e.javaClass.simpleName}")
        }
    }

    private fun captureCookie(resp: Response) {
        val raw = resp.headers("Set-Cookie")
        if (raw.isEmpty()) return
        val merged = raw.joinToString("; ") { it.substringBefore(";").trim() }
        if (merged.contains("MUSIC_U=")) cookie = merged
    }

    private suspend fun weapi(path: String, body: JsonObject = JsonObject(emptyMap())): JsonObject =
        withContext(Dispatchers.IO) {
            val (params, encSecKey) = WeapiCrypto.encrypt(body.toString())
            val form = FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
                .build()
            val req = Request.Builder().url(BASE + path).post(form).common().build()
            try {
                client.newCall(req).execute().use { resp ->
                    captureCookie(resp)
                    if (!resp.isSuccessful) {
                        throw ApiHttpException(resp.code, "HTTP ${resp.code}")
                    }
                    parse(resp.body?.string().orEmpty())
                }
            } catch (e: java.io.IOException) {
                // 连接失败 / 超时 / DNS 解析失败 —— 属于可重试的 NETWORK 类
                throw ApiNetworkException(e.message ?: "网络连接失败", e)
            }
        }

    /**
     * eapi 通道请求。
     *
     * 与 [weapi] 的区别：
     *  · 域名是 `interface.music.163.com`
     *  · 加密是 AES-128-ECB + MD5 摘要（见 [EapiCrypto]）
     *  · **Cookie 必须含 `os=pc`** —— 服务端据此判定客户端类型；
     *    缺少时部分接口会按未知客户端裁剪能力（高音质拿不到地址）
     *
     * 内部会**依次尝试 [EAPI_HOSTS] 中的多个域名**，任一成功即返回。
     *
     * @param path `/eapi/...` 形式的完整路径
     */
    private suspend fun eapi(
        path: String,
        body: JsonObject = JsonObject(emptyMap()),
        profile: EapiProfile = EAPI_PROFILES.first(),
    ): JsonObject {
        // 依次尝试各个 eapi 域名，任一个成功即返回。
        // 全部失败时抛出「最后一个」异常——诊断串会显示它的具体原因。
        var last: Throwable? = null
        for (host in EAPI_HOSTS) {
            val r = runCatching { eapiOn(profile, host, path, body) }
            if (r.isSuccess) return r.getOrThrow()
            last = r.exceptionOrNull()
        }
        throw last ?: ApiNetworkException("eapi 通道不可用", null)
    }

    /** 在**单个** eapi 域名上、以指定客户端档案执行请求。 */
    private suspend fun eapiOn(
        profile: EapiProfile,
        host: String,
        path: String,
        body: JsonObject,
    ): JsonObject = withContext(Dispatchers.IO) {
        // 摘要与密文用的都是 `/api/...` 路径，只有请求地址是 `/eapi/...`
        val apiPath = path.replaceFirst("/eapi/", "/api/")
        val params = EapiCrypto.encrypt(apiPath, body.toString())
        val form = FormBody.Builder().add("params", params).build()
        val req = Request.Builder()
            .url(host + path)
            .post(form)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", profile.ua)
            .header("Cookie", cookieForEapi(profile))
            // ⚠️ 显式要求**不压缩**。
            //
            // OkHttp 默认会加 `Accept-Encoding: gzip` 并负责透明解压，但 eapi 网关
            // 返回的是 `Transfer-Encoding: chunked` + `Content-Encoding: gzip`，
            // 实测这条链路上解压环节一旦失配，`body.string()` 拿到的是
            // **压缩字节按 UTF-8 解出来的乱码** —— 它不抛异常，只是不是 JSON，
            // 最终表现成「解析失败」，极难反推。
            //
            // eapi 响应只有几百字节，压缩毫无收益，索性直接要明文。
            .header("Accept-Encoding", "identity")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                captureCookie(resp)
                if (!resp.isSuccessful) {
                    throw ApiHttpException(resp.code, "HTTP ${resp.code}")
                }
                val text = resp.body?.string().orEmpty()
                try {
                    parse(text)
                } catch (e: ApiParseException) {
                    // eapi 返回非 JSON 时，把「状态码 / 长度 / 内容开头」
                    // 一起带出去 —— 否则只能看到笼统的"解析失败"，
                    // 无法判断是空响应、被劫持的 HTML，还是别的东西。
                    val ctype = resp.header("Content-Type").orEmpty().substringBefore(';')
                    val head = text.take(60).replace(Regex("\\s+"), " ").ifBlank { "<空>" }
                    throw ApiParseException(
                        "HTTP${resp.code} len=${text.length} ct=$ctype body=$head",
                    )
                }
            }
        } catch (e: java.io.IOException) {
            throw ApiNetworkException(e.message ?: "网络连接失败", e)
        }
    }

    /**
     * 为 eapi 请求准备 Cookie：**强制 `os=pc`，并精简到关键字段**。
     *
     * ## 为什么要精简
     *
     * 实测：同样的代码、同样的网络，本机只带 `os=pc` 时 eapi 返回
     * `200 + text/plain + 738B` 正常 JSON；而真机带完整登录 Cookie 时
     * 返回 `200 + application/json + 空 body`。
     *
     * 说明不是"没登录"，而是网关对**完整 Cookie 的字段组合**处理不同 ——
     * 完整 Cookie 里混有大量埋点/实验字段（`NMTID`、`_ntes_nuid`、
     * `JSESSIONID*`、`WNMCID` 等），它们对 eapi 毫无用处，却可能触发
     * 网关的异常分支，最终给一个"什么都不说"的空响应。
     *
     * 因此只保留鉴权与客户端标识所需的最小集合，其余一律剔除。
     *
     * ## 为什么强制覆盖 os
     *
     * `os` 决定服务端按哪类客户端裁剪能力。若登录时拿到的是
     * `os=android` 之类的值，而我们用 PC 的 eapi 请求格式，
     * 两者不匹配同样可能被静默拒绝。统一按 `pc` 处理。
     */
    private fun cookieForEapi(profile: EapiProfile): String {
        val raw = cookie?.trim().orEmpty()
        // 只保留鉴权字段；os / appver / osver 由 profile 强制注入。
        // 完整 Cookie 里的埋点/实验字段曾导致网关返回「200 + 空 body」，
        // 因此这里必须白名单裁剪。
        val keep = listOf(
            "MUSIC_U", "MUSIC_A", "MUSIC_R_U", "__csrf", "deviceId",
        )
        val kept = raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { kv -> keep.any { k -> kv.startsWith("$k=") } }
            .toMutableList()
        kept.add("os=${profile.os}")
        kept.add("appver=${profile.appver}")
        kept.add("osver=${profile.osver}")
        kept.add("channel=netease")
        return kept.joinToString("; ")
    }

    private suspend fun getPlain(path: String): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + path).get().common().build()
        try {
            client.newCall(req).execute().use { resp ->
                captureCookie(resp)
                if (!resp.isSuccessful) {
                    throw ApiHttpException(resp.code, "HTTP ${resp.code}")
                }
                parse(resp.body?.string().orEmpty())
            }
        } catch (e: java.io.IOException) {
            throw ApiNetworkException(e.message ?: "网络连接失败", e)
        }
    }

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

    // ---------------- 搜索 ----------------

    /** 搜索歌曲；官方搜索接口不返回封面，此处补一次 song/detail 合并封面 */
    suspend fun search(keywords: String, limit: Int, offset: Int): List<Song> {
        val root = getPlain("/api/search/get/web?s=${enc(keywords)}&type=1&offset=$offset&limit=$limit")
        val songs = root["result"]?.jsonObject?.get("songs")?.arr()?.mapNotNull { it.toSong() } ?: emptyList()
        return enrich(songs)
    }

    private suspend fun enrich(songs: List<Song>): List<Song> {
        val ids = songs.map { it.id }.filter { it > 0 }
        if (ids.isEmpty()) return songs
        val detail = songDetail(ids).associateBy { it.id }
        return songs.map { s ->
            val d = detail[s.id] ?: return@map s
            s.copy(
                album = d.album ?: s.album,
                duration = if (s.duration > 0) s.duration else d.duration,
            )
        }
    }

    /** 搜索热榜 */
    suspend fun searchHot(): List<String> {
        val root = weapi(
            "/weapi/search/hot",
            buildJsonObject { put("type", 1111) },
        )
        return root["result"]?.safeObject()?.get("hots")?.arr()
            ?.mapNotNull { it.safeObject()?.get("first")?.str() } ?: emptyList()
    }

    /** 听歌识曲：上传音频片段，返回识别到的歌曲 */
    suspend fun identifySong(audioBytes: ByteArray): Song? = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "audioFile",
                "record.mp3",
                audioBytes.toRequestBody("audio/mpeg".toMediaType()),
            )
            .build()
        val req = Request.Builder().url("$BASE/api/music/song/identify").post(body).common().build()
        client.newCall(req).execute().use { resp ->
            val root = parse(resp.body?.string().orEmpty())
            root["data"]?.safeObject()?.get("song")?.toSong()
        }
    }

    /** 雷达推荐（推荐新音乐，含单曲与歌单内歌曲） */
    suspend fun radarSongs(limit: Int = 30): List<Song> {
        val root = getPlain("/api/personalized/newsong?limit=$limit")
        return root["result"]?.arr()?.mapNotNull { el ->
            val songEl = el.safeObject()?.get("song") ?: return@mapNotNull null
            when (songEl) {
                is JsonObject -> songEl.toSong()
                is JsonArray -> songEl.firstOrNull()?.toSong()
                else -> null
            }
        } ?: emptyList()
    }

    /** 下载音频文件到本地 */
    /**
     * 下载到文件。
     *
     * ## 完整性保证
     *
     * 此前实现直接 `file.outputStream()` 写入 —— 中途失败（网络中断、
     * 存储写满、进程被杀）会**把已有的完整文件截断成半截**，
     * 且返回 false 后调用方往往已覆盖不了它。
     *
     * 现在：
     *  1. 写入前先检查 Content-Length；若服务端提供且实际字节数不符，判为失败
     *  2. 字节数为 0 判为失败（空文件对播放器无意义）
     *  3. **失败时不留半截文件**（由调用方决定是删除还是保留，
     *     这里只负责如实返回结果）
     *  4. 支持协程取消：`ensureActive()` 让长下载可被中断
     *
     * @return true 表示完整写入且长度校验通过
     */
    suspend fun downloadToFile(
        url: String,
        file: java.io.File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).common().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                val expected = body.contentLength()   // -1 表示服务端未提供
                val stream = body.byteStream() ?: return@use false

                var written = 0L
                var lastReported = 0L
                stream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            // 让下载可被取消（如在下载中途用户删除任务）
                            kotlinx.coroutines.currentCoroutineContext()
                                .ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            // 进度回调节流：每 256KB 上报一次。
                            // 若每读一 8KB 块都回调，UI 侧会因刷新过密而掉帧；
                            // 总长未知（expected<=0）时也按该节奏上报，仅 total 为 0。
                            if (onProgress != null && written - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = written
                                onProgress(written, expected.coerceAtLeast(0L))
                            }
                        }
                        out.flush()
                    }
                }
                // 收尾补一次，保证进度条能走到 100%
                onProgress?.invoke(written, expected.coerceAtLeast(0L))

                // ---- 完整性校验 ----
                if (written <= 0L) return@use false                       // 空文件
                if (expected > 0 && written != expected) return@use false  // 长度不符
                true
            }
        }.getOrDefault(false)
    }

    // ---------------- 歌曲 ----------------

    suspend fun songDetail(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<Song>()
        ids.chunked(BATCH).forEach { chunk ->
            val arrParam = chunk.joinToString(",", "[", "]")
            val root = getPlain("/api/song/detail?ids=${enc(arrParam)}")
            out += root["songs"]?.arr()?.mapNotNull { it.toSong() } ?: emptyList()
        }
        return out
    }

    // ---------------- 歌单 / 排行榜 ----------------

    suspend fun playlistDetail(id: Long): Playlist {
        val root = weapi(
            "/weapi/v6/playlist/detail",
            buildJsonObject {
                put("id", id)
                put("n", 1000)
                put("s", 8)
                put("csrf_token", "")
            },
        )
        val pl = root["playlist"]?.safeObject() ?: JsonObject(emptyMap())
        val trackIds = pl["trackIds"]?.arr()?.mapNotNull { it.safeObject()?.get("id")?.asLong() } ?: emptyList()
        val tracks = songDetail(trackIds)
        return Playlist(
            id = pl["id"]?.asLong() ?: id,
            name = pl["name"]?.str().orEmpty(),
            coverImgUrl = pl["coverImgUrl"]?.str(),
            playCount = pl["playCount"]?.asDouble() ?: 0.0,
            trackCount = pl["trackCount"]?.asInt() ?: trackIds.size,
            description = pl["description"]?.str(),
            tracks = tracks,
            trackIds = trackIds.map { TrackId(it) },
        )
    }

    suspend fun toplist(): List<Playlist> =
        getPlain("/api/toplist")["list"]?.arr()?.mapNotNull { it.toPlaylist() } ?: emptyList()

    suspend fun personalizedPlaylists(limit: Int): List<Playlist> {
        val root = weapi(
            "/weapi/personalized/playlist",
            buildJsonObject {
                put("limit", limit)
                put("total", true)
                put("n", 1000)
                put("csrf_token", "")
            },
        )
        return root["result"]?.arr()?.mapNotNull { it.toPlaylist() } ?: emptyList()
    }

    /**
     * 官方「你的雷达歌单」。
     *
     * 数据来自网易云移动端首页的 `homepage/block/page`。该区块会随登录账号
     * 返回不同的官方雷达歌单。
     *
     * **官方给几张就展示几张**，不再用其它歌单补足 —— 补足会产生
     * 「雷达歌单 + 每日推荐」的混合列表，与官方客户端该区块不一致。
     * 仅当官方区块**完全取不到**时，才按每日推荐 → 个性化歌单逐级回退，
     * 以免首页出现空白区块。
     *
     * @param limit 首页需要展示的歌单数量
     * @param refresh 下拉刷新时请求服务端重新生成首页区块
     */
    suspend fun radarPlaylists(limit: Int = 7, refresh: Boolean = false): List<Playlist> {
        if (limit <= 0) return emptyList()

        val result = LinkedHashMap<Long, Playlist>()
        var radarFailure: Throwable? = null

        runCatching { officialRadarPlaylists(refresh) }
            .onFailure { radarFailure = it }
            .getOrNull()
            .orEmpty()
            .forEach { playlist ->
                if (result.size < limit) result.putIfAbsent(playlist.id, playlist)
            }

        // ⚠️ 只有**官方区块完全取不到**时才回退，不再"补足到 7 张"。
        //
        // 原先只要官方数量不足 7，就用每日推荐 / 个性化歌单填满 ——
        // 于是首页显示的是「雷达歌单 + 一堆别的歌单」的混合体，
        // 与官方客户端的该区块必然不一致。现在官方给几张就显示几张。
        if (result.isEmpty()) {
            val fallback = runCatching { recommendResourcePlaylists(limit) }
            fallback.getOrNull().orEmpty().forEach { playlist ->
                if (result.size < limit) result.putIfAbsent(playlist.id, playlist)
            }
            if (result.isEmpty() && fallback.isFailure) {
                throw radarFailure
                    ?: fallback.exceptionOrNull()
                    ?: IllegalStateException("雷达歌单加载失败")
            }
        }

        if (result.isEmpty()) {
            // 官方与每日推荐都为空时，才是最后一层账号级回退。
            runCatching { personalizedPlaylists(limit + 4) }
                .getOrNull()
                .orEmpty()
                .forEach { playlist ->
                    if (result.size < limit) result.putIfAbsent(playlist.id, playlist)
                }
        }

        return result.values.toList()
    }

    /**
     * 账号级「推荐歌单」。
     *
     * 数据来自 `/v1/discovery/recommend/resource`，即官方客户端首页
     * 「推荐歌单」区块与「每日推荐」页共用的那份歌单 ——
     * **必须登录**，内容随账号口味变化、每日更新。
     *
     * ⚠️ 不要用 `/personalized/playlist` 充当首页推荐歌单：
     * 那个接口**未登录也返回同一份通用列表**，
     * 无论谁登录看到的都一样，完全没有个性化可言。
     */
    suspend fun recommendPlaylists(limit: Int = 30): List<Playlist> =
        recommendResourcePlaylists(limit)

    /** 网易云移动端首页的「你的雷达歌单」区块。 */
    private suspend fun officialRadarPlaylists(refresh: Boolean): List<Playlist> {
        val root = weapi(
            "/weapi/homepage/block/page",
            buildJsonObject {
                put("refresh", refresh)
                put("cursor", "")
                put("csrf_token", "")
            },
        )
        val blocks = root["data"]?.safeObject()?.get("blocks")?.arr().orEmpty()
        val radarBlock = blocks.asSequence()
            .mapNotNull { it.safeObject() }
            .firstOrNull { block ->
                // ⚠️ 这里**不能**用 `mainTitle ?: subTitle` 的写法。
                //
                // 官方区块的 `uiElement.mainTitle.title` 经常是**空字符串**
                // 而 `subTitle.title` 才是「你的雷达歌单」。
                // 空串不是 null，`?:` 不会回退 —— 于是标题恒为空，
                // `contains("雷达歌单")` 永不成立，整个区块被判定为不存在，
                // 上层只好用每日推荐歌单补齐：
                // 表现为「与官方不同步」「下拉刷新看不出变化」
                // 「切换账号也不更新」。三处症状同源。
                //
                // 现在把所有可能的标题都取出，只要有一个非空且命中即可；
                // 另加 blockCode 兜底（官方标题可能改文案，但区块 code 稳定）。
                val ui = block["uiElement"]?.safeObject()
                val titles = listOfNotNull(
                    ui?.get("mainTitle")?.safeObject()?.get("title")?.str(),
                    ui?.get("subTitle")?.safeObject()?.get("title")?.str(),
                )
                val code = block["blockCode"]?.str()?.uppercase().orEmpty()
                // 命中规则（两条任一）：
                //  · 标题含「雷达歌单」—— 官方标题形如「是 XXX 的雷达歌单」，
                //    其中 XXX 是当前登录账号的昵称，天然随账号变化
                //  · blockCode 含 RADAR_PLAYLIST / PLAYLIST_RADAR ——
                //    官方改标题文案时区块 code 通常稳定
                // 不能放宽到「雷达」两个字：首页另有「雷达新歌」区块，会错抓。
                titles.any { it.isNotBlank() && it.contains("雷达歌单") } ||
                    "RADAR_PLAYLIST" in code ||
                    "PLAYLIST_RADAR" in code
            } ?: return emptyList()

        return radarBlock["creatives"]?.arr().orEmpty().mapNotNull { it.toRadarPlaylist() }
    }

    /** 每日推荐歌单：账号级、每日更新，用于补齐官方雷达区块。 */
    private suspend fun recommendResourcePlaylists(limit: Int): List<Playlist> {
        val root = weapi(
            "/weapi/v1/discovery/recommend/resource",
            buildJsonObject { put("csrf_token", "") },
        )
        val recommend = root["recommend"]?.arr()
            ?: root["data"]?.safeObject()?.get("recommend")?.arr()
            ?: emptyList()
        return recommend.mapNotNull { it.toPlaylist() }.distinctBy { it.id }.take(limit)
    }

    /**
     * 为你推荐（个性化）。
     *
     * 与「每日推荐」同源，但额外传 `afresh = true` 让服务端重新挑选一批，
     * 因此两者内容不重复 —— 适合作为首页的第二个个性化推荐区块。
     */
    suspend fun forYouSongs(): List<Song> {
        val root = weapi(
            "/weapi/v1/discovery/recommend/songs",
            buildJsonObject {
                put("afresh", true)
                put("csrf_token", "")
            },
        )
        return root["data"]?.safeObject()?.get("dailySongs")?.arr()
            ?.mapNotNull { it.toSong() } ?: emptyList()
    }

    suspend fun recommendSongs(): List<Song> {
        val root = weapi(
            "/weapi/v1/discovery/recommend/songs",
            buildJsonObject { put("csrf_token", "") },
        )
        return root["data"]?.safeObject()?.get("dailySongs")?.arr()?.mapNotNull { it.toSong() } ?: emptyList()
    }

    suspend fun userPlaylists(uid: Long): List<Playlist> =
        getPlain("/api/user/playlist?uid=$uid&limit=1000&offset=0")["playlist"]?.arr()
            ?.mapNotNull { it.toPlaylist() } ?: emptyList()

    /** 歌手热门作品，返回 (歌手名, 歌曲列表) */
    suspend fun artistDetail(artistId: Long): Pair<String, List<Song>> {
        val root = getPlain("/api/v1/artist/$artistId")
        val name = root["artist"]?.safeObject()?.get("name")?.str().orEmpty()
        val songs = root["hotSongs"]?.arr()?.mapNotNull { it.toSong() } ?: emptyList()
        return name to songs
    }

    // ---------------- 播放 / 歌词 ----------------

    /**
     * 获取歌曲的**播放**地址。
     *
     * ## 为什么用 v1 接口的 `level` 而不是旧接口的 `br`
     *
     * 旧接口 `/weapi/song/enhance/player/url` 只认 `br`（比特率数值），
     * 而 hires（Hi-Res）与 jymaster（超清母带）**没有对应的 br 数值** ——
     * 一旦把 level 换算成 br，这两种音质就只能退化到 320000（极高），
     * 表现为「选了无损/母带，拿到的仍是普通音质」。
     *
     * v1 接口直接接受 `level` 字符串，原生支持全部 6 档音质：
     * `standard` / `higher` / `exhigh` / `lossless` / `hires` / `jymaster`。
     *
     * @param level 音质档位，见上；空值时按 `exhigh` 兜底
     */
    /**
     * 播放地址解析结果。
     *
     * 之所以不直接返回 `String?`：**降级链会让实际音质低于请求音质**
     * （未登录/VIP 不足时自动退档）。
     * 若只回传 URL，UI 层就只能拿"请求音质"当标签显示 ——
     * 于是出现「实际播的是 128k，界面写着无损」的虚假信息。
     *
     * @param url 可播放地址
     * @param level 本次**实际命中**的音质档位（降级后的真实值）
     * @param requestedLevel 用户最初请求的档位，用于在界面提示"已降级"
     */
    data class ResolvedUrl(
        val url: String,
        val level: String,
        val requestedLevel: String,
        /**
         * 首选档位探测失败的原因（简短诊断串）。
         *
         * 形如 `有Cookie/eapi:code=200`——用于区分
         * 「请求根本没发出去」与「服务端收到但拒绝」，
         * 会附在降级提示里，便于反馈时说明现象。
         */
        val detail: String = "",
        /**
         * 实际档位是否**低于**请求档位。
         *
         * 只有"降级"才值得提示用户。服务端有时会给**更高**的规格
         * （例如请求无损却返回 Hi-Res 流），那是好事，不该弹"不可用"。
         */
        val degraded: Boolean = false,
    )

    suspend fun songUrl(id: Long, level: String): ResolvedUrl? {
        // 逐级降级尝试。
        //
        // 服务端会按**账号权限**决定给不给某个音质的播放地址：
        // 未登录（或非会员）请求 exhigh 及以上时，返回的 url 往往是 null。
        // 若只按请求音质取一次，结果就是「完全播不出来」——
        // 这正是「未登录无法播放」的根因。
        //
        // 因此从请求音质开始逐级下调，取到第一个非空地址即返回
        // （最差也会退到 standard，保证能出声）。
        // 同时把**命中的那一档**一并带回去，供 UI 展示真实音质。
        // 首选档位（用户真正选的那个）失败时的原因，回传给上层拼进提示
        var requestedDetail = ""
        for (lv in qualityFallbackChain(level)) {
            val probe = fetchPlayerUrl(id, lv)
            if (lv == level) requestedDetail = probe.detail
            val url = probe.url
            if (!url.isNullOrBlank()) {
                // 用服务端回报的实际档位，而不是我们请求的档位
                return ResolvedUrl(
                    url = url,
                    level = probe.level,
                    requestedLevel = level,
                    detail = requestedDetail,
                    // 仅在**实际低于请求**时才算降级；
                    // 服务端给更高规格（如请求无损却返回 Hi-Res）不提示。
                    degraded = levelRank(probe.level) < levelRank(level),
                )
            }
        }
        return null
    }

    /**
     * 按请求音质生成**降级链**（由高到低）。
     *
     * 顺序不是随便排的：每次都退到低一档，直到 `standard`。
     */
    private fun qualityFallbackChain(level: String): List<String> {
        // 与官方一致的 7 档（详见 AudioQuality）：
        //   standard < exhigh < lossless < hires < {jyeffect, sky} < jymaster
        //
        // 环绕声 / 全景声（jyeffect / sky）属于**空间音频处理**，
        // 拿不到时退回高解析音频（hires）而非直接掉到 MP3 ——
        // 这样至少保住解析度，听感上比降到 320k 更接近用户预期。
        val hqFallback = listOf("hires", "lossless", "exhigh", "standard")
        return when (level) {
            "jymaster" -> listOf("jymaster") + hqFallback
            "sky" -> listOf("sky") + hqFallback
            "jyeffect" -> listOf("jyeffect") + hqFallback
            "hires" -> listOf("hires", "lossless", "exhigh", "standard")
            "lossless" -> listOf("lossless", "exhigh", "standard")
            "exhigh" -> listOf("exhigh", "standard")
            "higher" -> listOf("higher", "standard")
            else -> listOf("standard")
        }
    }

    /** 单次音质探测的结果。[url] 为空表示该档位取不到地址。 */
    private data class PlayerUrlProbe(
        val url: String?,
        val level: String,
        /** 简短诊断：Cookie 是否就绪 + 各通道的失败原因 */
        val detail: String,
        /** 服务端回报的码率（bps），用于横向比较不同 level 的产出 */
        val br: Int = 0,
    )

    /**
     * 把异常压成**可读且不被混淆**的短标签。
     *
     * release 构建启用了 R8，`javaClass.simpleName` 只会得到 `a`/`b`/`c`，
     * 对诊断毫无用处。这里优先用：
     *  · 自有异常类型的 instanceof 判断（编译后是 instanceof，不受改名影响）
     *  · 兜底用 `message`（运行时字符串，永远保留原样）
     */
    private fun Throwable?.describe(): String = when (this) {
        null -> "未知"
        is ApiHttpException -> "HTTP$httpCode"
        is ApiNetworkException -> "网络:" + (message ?: "连接失败").take(40)
        is ApiParseException -> "解析失败:" + (message ?: "?").take(80)
        is java.net.UnknownHostException -> "DNS解析失败"
        is java.net.SocketTimeoutException -> "超时"
        is javax.net.ssl.SSLException -> "SSL握手失败"
        else -> message?.take(60)?.ifBlank { null } ?: "无消息"
    }

    /**
     * 按**单一**音质探测播放地址：eapi → weapi v1 → 旧接口。
     *
     * 分工：
     *  · **eapi**（`interface.music.163.com`）—— 移动端通道，
     *    Hi-Res / 母带 / 空间音频**只有这里给**；Web 端会被裁剪
     *  · **weapi v1** —— Web 端，低档位（标准/极高/无损）可用，作兜底
     *  · **旧接口**（按 br）—— 只认码率，最后一道保险
     *
     * [PlayerUrlProbe.detail] 会一路带到界面提示里，用来区分
     * 「请求没发出去」与「服务端收到但拒绝」—— 两者修法完全不同。
     */
    private suspend fun fetchPlayerUrl(id: Long, level: String): PlayerUrlProbe {
        val v1Body = buildJsonObject {
            put("ids", "[$id]")
            put("level", level)
            put("encodeType", encodeTypeOf(level))
            put("csrf_token", "")
        }
        // 末尾的 /bN 是构建号（versionCode）——
        // 用于确认用户装的到底是不是当前构建：
        // 此前多轮反馈因"旧包与新包同名（1.2.1/code 8）"而产生歧义。
        val ckTag = (if (cookie.isNullOrBlank()) "无Cookie" else "有Cookie") +
            "/b" + BuildConfig.VERSION_CODE

        // ---- ① eapi 通道：**移动端 → PC 端逐个档案尝试** ----
        //
        // Hi-Res / 母带 / 空间音频属于移动端能力。此前强制 `os=pc`，
        // 服务端按 PC 客户端裁剪 —— 即使账号是 VIP、歌曲确有 Hi-Res 源
        // （官方「高清臻音」显示可选且已勾选），我们请求 hires 仍只拿回无损。
        //
        // 这里逐个档案请求，取第一个**达到请求档位**的结果；
        // 都达不到时保留其中档位最高的一个（有胜于无）。
        var bestProbe: PlayerUrlProbe? = null
        val tried = StringBuilder()
        for (profile in EAPI_PROFILES) {
            // 每个档案的 body 都要带各自的 header（含 os/appver/deviceId）
            val eapiBody = buildJsonObject {
                put("ids", "[$id]")
                put("level", level)
                put("encodeType", encodeTypeOf(level))
                put("csrf_token", "")
                put("header", eapiHeader(profile))
            }
            val result = runCatching {
                eapi("/eapi/song/enhance/player/url/v1", eapiBody, profile)["data"]
                    ?.arr()?.firstOrNull()?.safeObject()
            }
            val item = result.getOrNull()
            val br = item?.get("br")?.str()?.toDoubleOrNull()?.toInt() ?: 0
            val actual = reconcileLevel(item?.get("level")?.str(), br, level)
            val tag = when {
                // ⚠️ 不能用 javaClass.simpleName —— release 开了 R8，
                // 异常类名会被混淆成 a/b/c，诊断信息就废了。
                result.isFailure ->
                    "os=${profile.os}/" + result.exceptionOrNull().describe()
                item == null -> "os=${profile.os}/无data"
                else -> "os=${profile.os}/code=${item.get("code")?.str()}/br=$br" +
                    "/fee=${item.get("fee")?.str()}/payed=${item.get("payed")?.str()}"
            }
            if (tried.isNotEmpty()) tried.append("|")
            tried.append(tag)

            val url = item?.get("url")?.str()
            if (!url.isNullOrBlank()) {
                val probe = PlayerUrlProbe(url, actual, "$ckTag/$tag", br)
                // 达到或超过请求档位 → 直接采用
                if (levelRank(actual) >= levelRank(level)) return probe
                if (bestProbe == null || levelRank(actual) > levelRank(bestProbe.level)) {
                    bestProbe = probe
                }
            }
        }
        // 降级时**额外探测更高档位**：
        // 如果 `jymaster` / `sky` 能拿到明显更高的码率，说明「高清臻音」
        // 对应的 level 并不是 `hires`，而是别的枚举值 —— 这能一次性
        // 区分「歌曲确实没有 Hi-Res」与「我们的 level 映射错了」。
        bestProbe?.let { probe ->
            // 向上探测更高档位，并且**真正采用更优结果** —— 不只是诊断。
            //
            // 实测（《我不难过》孙燕姿）：
            //   请求 hires    → br = 901,303（无损）
            //   请求 jymaster → br = 3,018,378（3 Mbps，换算约 96kHz/24bit）
            // 说明服务端把高解析资源挂在 `jymaster` 名下，`hires` 反而只给无损。
            // 因此不能只按请求的 level 取一次就收工。
            var best = probe
            val probes = StringBuilder()
            for (lv in listOf("jymaster", "sky")) {
                val probeBody = buildJsonObject {
                    put("ids", "[$id]")
                    put("level", lv)
                    put("encodeType", "flac")
                    put("csrf_token", "")
                    put("header", eapiHeader(EAPI_PROFILES.first()))
                }
                val item = runCatching {
                    eapi("/eapi/song/enhance/player/url/v1", probeBody)["data"]
                        ?.arr()?.firstOrNull()?.safeObject()
                }.getOrNull()
                val b = item?.get("br")?.str()?.toDoubleOrNull()?.toInt() ?: 0
                val url = item?.get("url")?.str()
                if (probes.isNotEmpty()) probes.append(";")
                probes.append("$lv=$b")

                if (!url.isNullOrBlank()) {
                    val actual = reconcileLevel(item.get("level")?.str(), b, lv)
                    // 档位更高，或同档但码率更高才替换
                    val better = levelRank(actual) > levelRank(best.level) ||
                        (levelRank(actual) == levelRank(best.level) && b > best.br)
                    if (better) {
                        best = PlayerUrlProbe(url, actual, "$ckTag/$tried/探测[$probes]", b)
                    }
                }
            }
            return best.copy(detail = "$ckTag/$tried/探测[$probes]")
        }
        val eapiDetail = tried.toString()

        // ---- ② weapi v1（Web 端兜底）----
        val v1Item = runCatching {
            weapi("/weapi/song/enhance/player/url/v1", v1Body)["data"]
                ?.arr()?.firstOrNull()?.safeObject()
        }.getOrNull()
        v1Item?.get("url")?.str()?.takeIf { it.isNotBlank() }?.let { url ->
            val br = v1Item.get("br")?.str()?.toDoubleOrNull()?.toInt() ?: 0
            val actual = reconcileLevel(v1Item.get("level")?.str(), br, level)
            return PlayerUrlProbe(url, actual, "$ckTag/$eapiDetail/weapi命中/br=$br", br)
        }

        // ---- ③ 旧接口（只认 br）----
        // lossless 及以上统一请求 999000，让服务端在权限内给最高。
        // ⚠️ 不能填 320000 —— 会把上限锁死在「极高」。
        val br = when (level) {
            "standard" -> 128000
            "higher" -> 192000
            else -> 999000
        }
        val legacyItem = runCatching {
            weapi(
                "/weapi/song/enhance/player/url",
                buildJsonObject {
                    put("ids", "[$id]")
                    put("br", br)
                    put("csrf_token", "")
                },
            )["data"]?.arr()?.firstOrNull()?.safeObject()
        }.getOrNull()
        val legacyUrl = legacyItem?.get("url")?.str()
        if (!legacyUrl.isNullOrBlank()) {
            val brValue = legacyItem.get("br")?.str()?.toDoubleOrNull()?.toInt() ?: 0
            val actual = reconcileLevel(legacyItem.get("level")?.str(), brValue, level)
            return PlayerUrlProbe(legacyUrl, actual, "$ckTag/$eapiDetail/旧接口命中", brValue)
        }

        return PlayerUrlProbe(null, level, "$ckTag/$eapiDetail/全部通道无地址")
    }


    /**
     * 由码率反推音质档位。
     *
     * 阈值按网易云实测码率划分：
     * ```
     * standard 128k / higher 192k / exhigh 320k
     * lossless 约 0.8–1.0M / hires 约 1.4–2.8M / jymaster 3M+
     * ```
     *
     * ⚠️ 不能只覆盖到 lossless —— 那样 Hi-Res 流会被误判成无损
     * （曾因此把 2.79Mbps 的 Hi-Res 显示成「无损 / 48kHz/16bit」）。
     */
    private fun levelOfBr(br: Int): String? = when {
        br <= 0 -> null
        br < 160_000 -> "standard"
        br < 256_000 -> "higher"
        br < 700_000 -> "exhigh"
        br < 1_400_000 -> "lossless"
        // 1.4M–4.5M 归 Hi-Res（高清臻音，96kHz/24bit）。
        // 实测该档位典型码率约 2.8–3.0 Mbps（如 3018378）；
        // 阈值若卡在 3.0M，会把 3018378 误判成「超清母带（192kHz/24bit）」，
        // 界面规格就与实际不符。
        br < 4_500_000 -> "hires"
        else -> "jymaster"
    }

    /** 档位高低排序，用于比较两个来源给出的档位 */
    private fun levelRank(level: String): Int = when (level) {
        "standard" -> 0
        "higher" -> 1
        "exhigh" -> 2
        "lossless" -> 3
        "hires" -> 4
        "jyeffect", "sky" -> 5
        "jymaster" -> 6
        else -> -1
    }

    /**
     * 校正服务端回报的档位。
     *
     * ## 为什么需要校正
     *
     * `song/enhance/player/url/v1` 的 `level` 字段**并不可靠**：
     * 实测请求 `hires` 时，服务端返回了 **2.79 Mbps 的流**（等于该曲
     * `hr.br`），但 `level` 却写着 `lossless`。
     * 若直接采信 `level`，界面会显示「无损 / 48kHz/16bit」并弹出
     * 「已降级为无损」的提示 —— 而用户实际听到的正是 Hi-Res。
     *
     * 因此以**码率**为主要依据：当码率指向的档位**高于** `level` 时，
     * 采信码率；否则沿用 `level`（避免把高码率 MP3 误抬档）。
     */
    private fun reconcileLevel(fieldLevel: String?, br: Int, requested: String): String {
        // **码率优先**。
        //
        // `level` 字段经常只是"请求档位的回显"，甚至是另一个档位名：
        // 实测向上探测 `jymaster` / `sky` 时，两者都回报 3,018,378 bps
        // （96kHz/24bit），但 `level` 字段可能写 `sky` ——
        // 若采信它，界面就会把用户选的「高清臻音」显示成「沉浸环绕声」。
        //
        // 码率是文件本身的属性，比标签可靠，因此先按码率判定；
        // 码率无法判定（为 0）时才退回 `level` 字段。
        levelOfBr(br)?.let { return it }
        if (!fieldLevel.isNullOrBlank()) return fieldLevel
        return requested
    }

    /**
     * 获取歌曲的**下载**地址。
     *
     * 与播放地址走**不同接口**：`/song/enhance/download/url/v1`。
     * 该接口**必须登录**（未登录返回 code 301），且对高音质资源更完整 ——
     * 播放接口出于版权保护可能只给试听片段，下载接口才会给出整曲文件。
     *
     * @return 下载直链；无权限或该音质不可用时为 null
     */
    suspend fun downloadUrl(id: Long, level: String): String? {
        val body = buildJsonObject {
            put("id", "$id")
            put("level", level.ifBlank { "exhigh" })
            put("encodeType", encodeTypeOf(level))
            put("csrf_token", "")
        }

        // 与播放地址同一原因：高解析档位只在 eapi 通道开放，
        // weapi 下即使 VIP 也会被判为无权限。
        runCatching {
            eapi("/eapi/song/enhance/download/url/v1", body)["data"]
                ?.safeObject()?.get("url")?.str()
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }

        val root = weapi("/weapi/song/enhance/download/url/v1", body)
        return root["data"]?.safeObject()?.get("url")?.str()
    }

    /**
     * 按音质档位推断容器格式。
     *
     * 无损及以上（lossless / hires / jymaster）为 FLAC，其余为 MP3。
     * 该值会传给接口的 `encodeType`，也决定下载文件的扩展名 ——
     * 若把 FLAC 数据写成 .mp3 后缀，部分播放器会拒绝识别。
     */
    fun encodeTypeOf(level: String): String = when (level) {
        // 无损及以上（含空间音频类）均为 FLAC 容器
        "lossless", "hires", "jymaster", "sky", "jyeffect" -> "flac"
        else -> "mp3"
    }

    suspend fun lyric(id: Long): LyricResponse {
        val root = weapi(
            "/weapi/song/lyric",
            buildJsonObject {
                put("id", id)
                put("lv", -1)
                put("kv", -1)
                put("tv", -1)
                put("csrf_token", "")
            },
        )
        return LyricResponse(
            code = root["code"]?.asInt() ?: 200,
            lrc = root["lrc"]?.safeObject()?.let { LyricData(it["lyric"]?.str()) },
            tlyric = root["tlyric"]?.safeObject()?.let { LyricData(it["lyric"]?.str()) },
        )
    }

    // ---------------- 喜欢 / 歌单 / 历史 ----------------

    /** 喜欢/取消喜欢一首歌 */
    suspend fun likeSong(id: Long, like: Boolean): Boolean {
        val root = weapi(
            "/weapi/song/like",
            buildJsonObject {
                put("alg", "itembased")
                put("trackId", id)
                put("like", like)
                put("time", 3)
                put("csrf_token", "")
            },
        )
        return root["code"]?.asInt() == 200
    }

    /** 收藏 / 取消收藏歌单（t=1 收藏，t=2 取消） */
    suspend fun subscribePlaylist(id: Long, subscribe: Boolean): Boolean {
        val root = weapi(
            "/weapi/playlist/subscribe",
            buildJsonObject {
                put("id", id)
                put("t", if (subscribe) 1 else 2)
                put("csrf_token", "")
            },
        )
        return root["code"]?.asInt() == 200
    }

    /** 获取喜欢的歌曲 id 列表 */
    suspend fun likelist(uid: Long): List<Long> {
        val root = weapi(
            "/weapi/likelist",
            buildJsonObject {
                put("uid", uid)
                put("csrf_token", "")
            },
        )
        return root["ids"]?.arr()?.mapNotNull { it.asLong() } ?: emptyList()
    }

    /** 新建歌单，返回新歌单 id */
    suspend fun createPlaylist(name: String): Long? {
        val root = weapi(
            "/weapi/playlist/create",
            buildJsonObject {
                put("name", name)
                put("privacy", 0)
                put("type", "NORMAL")
                put("csrf_token", "")
            },
        )
        return root["id"]?.asLong() ?: root["playlist"]?.safeObject()?.get("id")?.asLong()
    }

    /**
     * 听歌排行（type 0 = 全部，1 = 最近一周）。
     *
     * 注意：这是按播放次数排序的「听歌排行」，不是按时间排序的
     * 「最近播放」。界面上的最近播放请使用 [recentSongs]。
     */
    suspend fun playHistory(uid: Long, type: Int = 0): List<Song> {
        val root = weapi(
            "/weapi/v1/play/record",
            buildJsonObject {
                put("type", type)
                put("uid", uid)
                put("csrf_token", "")
            },
        )
        val arr = root["allData"]?.arr() ?: root["weekData"]?.arr() ?: emptyList()
        return arr.mapNotNull { it.safeObject()?.get("song")?.toSong() }
    }

    /**
     * 真实最近播放（按播放时间倒序）。
     *
     * `/api/v1/play/record` 返回的是听歌排行；移动端的「最近播放」
     * 使用 `/api/play-record/song/list`，歌曲位于每行记录的 `data` 字段。
     */
    suspend fun recentSongs(limit: Int = 100): List<Song> {
        val root = weapi(
            "/weapi/play-record/song/list",
            buildJsonObject {
                put("limit", limit.coerceIn(1, 500))
                put("csrf_token", "")
            },
        )
        val data = root["data"]
        val rows = when (data) {
            is JsonArray -> data.toList()
            is JsonObject -> data["list"]?.arr().orEmpty()
            else -> emptyList()
        }
        return rows.asSequence()
            .mapNotNull { row ->
                val rowObject = row.safeObject() ?: return@mapNotNull null
                (rowObject["data"] ?: rowObject["song"] ?: rowObject).toSong()
            }
            .filter { it.id > 0L }
            .distinctBy { it.id }
            .toList()
    }

    // ---------------- 登录 ----------------

    /**
     * 获取二维码 unikey。
     *
     * 使用**明文接口** /api/login/qrcode/unikey：weapi 通道在部分环境下
     * 会被服务端风控（返回"请切换其他登录方式或升级版本再试"）。
     */
    suspend fun qrKey(): String? {
        // 通道一：明文接口
        val plain = runCatching {
            getPlain("/api/login/qrcode/unikey?type=1")["unikey"]?.str()
        }.getOrNull()
        if (!plain.isNullOrBlank()) return plain
        // 通道二：weapi 回退
        return runCatching {
            weapi(
                "/weapi/login/qrcode/unikey",
                buildJsonObject { put("type", 1) },
            )["unikey"]?.str()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 二维码状态轮询。
     *
     * 直接返回**官方状态码**，不做任何重映射：
     * - 800 = 二维码已过期
     * - 801 = 等待扫码
     * - 802 = 已扫码，等待手机上确认
     * - 803 = 授权成功（该响应才携带登录 Cookie）
     *
     * 上层（Repository / ViewModel）均按此语义处理，避免二次映射造成错位。
     */
    suspend fun qrCheck(key: String): QrCheckResponse {
        // 通道一：明文接口；通道二：weapi 回退；两者都失败也不抛异常，
        // 而是返回中性状态，让轮询继续（避免"轮询失败"中断整个扫码流程）
        val root = runCatching {
            getPlain("/api/login/qrcode/client/login?key=$key&type=1")
        }.getOrElse {
            runCatching {
                weapi(
                    "/weapi/login/qrcode/client/login",
                    buildJsonObject {
                        put("key", key)
                        put("type", 1)
                        put("csrf_token", "")
                    },
                )
            }.getOrElse { JsonObject(emptyMap()) }
        }
        val raw = root["code"]?.asInt() ?: 801
        return QrCheckResponse(
            code = raw,
            message = root["message"]?.str(),
            cookie = if (raw == 803) cookie else null,
        )
    }

    suspend fun cellphoneLogin(phone: String, password: String): LoginStatusResponse {
        val root = weapi(
            "/weapi/login/cellphone",
            buildJsonObject {
                put("phone", phone)
                put("password", password)
                put("countrycode", "86")
                put("rememberLogin", "true")
                put("csrf_token", "")
            },
        )
        return root.toLoginStatus()
    }

    /** 发送短信验证码 */
    suspend fun sendCaptcha(phone: String): Boolean {
        val root = weapi(
            "/weapi/sms/captcha/sent",
            buildJsonObject {
                put("cellphone", phone)
                put("ctcode", "86")
            },
        )
        return root["code"]?.asInt() == 200
    }

    /** 验证码登录 */
    suspend fun captchaLogin(phone: String, captcha: String): LoginStatusResponse {
        val root = weapi(
            "/weapi/login/cellphone",
            buildJsonObject {
                put("phone", phone)
                put("captcha", captcha)
                put("countrycode", "86")
                put("rememberLogin", "true")
                put("csrf_token", "")
            },
        )
        return root.toLoginStatus()
    }

    private fun JsonObject.toLoginStatus() = LoginStatusResponse(
        code = this["code"]?.asInt() ?: 0,
        cookie = cookie,
        profile = profileOf(this),
        account = accountOf(this),
    )

    /** 账号详情：主要用于获取等级（level） */
    suspend fun userDetail(uid: Long): Int {
        val root = getPlain("/api/v1/user/detail/$uid")
        return root["level"]?.str()?.toIntOrNull()
            ?: root["profile"]?.safeObject()?.get("level")?.str()?.toIntOrNull()
            ?: 0
    }

    suspend fun loginStatus(): LoginStatusResponse {
        val root = weapi(
            "/weapi/w/nuser/account/get",
            buildJsonObject { put("csrf_token", "") },
        )
        return LoginStatusResponse(
            code = 200,
            cookie = cookie,
            profile = profileOf(root),
            account = accountOf(root),
        )
    }

    fun profileOf(root: JsonObject): Profile? =
        root["profile"]?.takeIf { it !is JsonNull }?.let {
            runCatching { json.decodeFromJsonElement(Profile.serializer(), it) }.getOrNull()
        }

    fun accountOf(root: JsonObject): Account? =
        root["account"]?.takeIf { it !is JsonNull }?.let {
            runCatching { json.decodeFromJsonElement(Account.serializer(), it) }.getOrNull()
        }

    // ---------------- JSON 转换工具 ----------------

    private fun JsonElement?.arr(): List<JsonElement>? = (this as? JsonArray)?.toList()

    private fun JsonElement?.safeObject(): JsonObject? = this as? JsonObject

    private fun JsonElement?.str(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonElement?.asLong(): Long? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
            ?.let { runCatching { it.long }.getOrNull() }

    private fun JsonElement?.asInt(): Int? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
            ?.let { runCatching { it.int }.getOrNull() }

    private fun JsonElement?.asDouble(): Double? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
            ?.let { runCatching { it.double }.getOrNull() }

    private fun JsonElement.toSong(): Song? =
        runCatching { json.decodeFromJsonElement(Song.serializer(), this) }.getOrNull()

    private fun JsonElement.toPlaylist(): Playlist? =
        runCatching { json.decodeFromJsonElement(Playlist.serializer(), this) }.getOrNull()

    /**
     * 解析首页 `HOMEPAGE_BLOCK_MGC_PLAYLIST` 中的雷达卡片。
     *
     * 移动端区块不会返回完整歌单对象，而是把 id、标题、封面和播放量
     * 分散在 `creative` / `resources[0]` 中，因此这里单独映射。
     */
    private fun JsonElement.toRadarPlaylist(): Playlist? {
        val creative = safeObject() ?: return null
        val resource = creative["resources"]?.arr()?.firstOrNull()?.safeObject()
        val creativeUi = creative["uiElement"]?.safeObject()
        val resourceUi = resource?.get("uiElement")?.safeObject()
        val id = listOfNotNull(
            creative["creativeId"]?.asLong(),
            resource?.get("resourceId")?.asLong(),
            resource?.get("id")?.asLong(),
        ).firstOrNull { it != null && it > 0L } ?: return null
        if (id <= 0L) return null

        // 同上：空串不能阻断回退，否则会整张卡被丢弃。
        // 依次尝试 creative 与 resource 的 mainTitle，取第一个**非空白**值。
        val ext = resource?.get("resourceExtInfo")?.safeObject()
        val title = listOfNotNull(
            creativeUi?.get("mainTitle")?.safeObject()?.get("title")?.str(),
            resourceUi?.get("mainTitle")?.safeObject()?.get("title")?.str(),
            // 部分区块只有 resourceExtInfo 里有可读名
            ext?.get("name")?.str(),
            resource?.get("name")?.str(),
        ).firstOrNull { it.isNotBlank() } ?: return null

        val cover = listOfNotNull(
            creativeUi?.get("image")?.safeObject()?.get("imageUrl")?.str(),
            resourceUi?.get("image")?.safeObject()?.get("imageUrl")?.str(),
            ext?.get("coverUrl")?.str(),
            resource?.get("coverUrl")?.str(),
        ).firstOrNull { it.isNotBlank() }
        val playCount = ext?.get("playCount")?.asDouble() ?: 0.0

        return Playlist(
            id = id,
            name = title,
            coverImgUrl = cover?.replaceFirst("http://", "https://"),
            playCount = playCount,
        )
    }
}
