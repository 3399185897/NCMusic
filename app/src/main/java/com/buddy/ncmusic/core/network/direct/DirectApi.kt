package com.buddy.ncmusic.core.network.direct

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
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val BATCH = 200
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 登录态 Cookie（含 MUSIC_U），由登录流程写入 */
    @Volatile
    var cookie: String? = null

    // ---------------- 基础请求 ----------------

    private fun Request.Builder.common(): Request.Builder = apply {
        header("Referer", "https://music.163.com/")
        header("User-Agent", UA)
        cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
    }

    private fun parse(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { JsonObject(emptyMap()) }

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
            client.newCall(req).execute().use { resp ->
                captureCookie(resp)
                parse(resp.body?.string().orEmpty())
            }
        }

    private suspend fun getPlain(path: String): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + path).get().common().build()
        client.newCall(req).execute().use { resp ->
            captureCookie(resp)
            parse(resp.body?.string().orEmpty())
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
    suspend fun downloadToFile(url: String, file: java.io.File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).common().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val stream = resp.body?.byteStream() ?: return@use false
                stream.use { input -> file.outputStream().use { input.copyTo(it) } }
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

    suspend fun songUrl(id: Long, level: String): String? {
        val br = when (level) {
            "standard" -> 128000
            "higher" -> 192000
            "exhigh" -> 320000
            "lossless" -> 999000
            else -> 320000
        }
        val root = weapi(
            "/weapi/song/enhance/player/url",
            buildJsonObject {
                put("ids", "[$id]")
                put("br", br)
                put("csrf_token", "")
            },
        )
        return root["data"]?.arr()?.firstOrNull()?.safeObject()?.get("url")?.str()
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

    /** 播放历史（type 0 = 全部，1 = 最近一周） */
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
}
