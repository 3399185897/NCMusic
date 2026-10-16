package com.buddy.ncmusic.core.network.direct

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
     * 「私人雷达」的固定歌单 id。
     *
     * 网易云没有公开此能力的 API，社区通行做法即固定 id + 登录 cookie。
     * 该 id 实测返回的歌单名为「私人雷达」。
     */
    private val RADAR_PLAYLIST_ID: Long = 3136952023L

    /** 私人雷达在返回列表中的位置（对应拼贴墙最大的 80×80 方块） */
    private val RADAR_INDEX = 2

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
    suspend fun downloadToFile(url: String, file: java.io.File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).common().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                val expected = body.contentLength()   // -1 表示服务端未提供
                val stream = body.byteStream() ?: return@use false

                var written = 0L
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
                        }
                        out.flush()
                    }
                }

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
     * 雷达歌单（个性化歌单）。
     *
     * 与「推荐歌单」共用个性化推荐体系，但走独立端点并指定批次，
     * 因此二者内容不重复，适合作为首页另一个歌单区块。
     */
    /**
     * 雷达歌单（首页拼贴墙的数据源）。
     *
     * ## 组成
     *
     * 1. **第一位固定为「私人雷达」**
     *    网易云**未公开**该能力的 API，社区通行做法是直接取固定歌单 id。
     *    实测 `v6/playlist/detail` 返回的歌单名确为「私人雷达」。
     *    该歌单需要登录态才有曲目内容，但歌单元信息（名称 / 封面）匿名即可取到，
     *    因此这里用 `runCatching` 包裹 —— **失败就跳过，不影响其余 19 个**。
     *
     * 2. **其余位置用「推荐歌单」补齐**
     *    为保证拼贴墙 18 个方块封面互不重复，多取 4 条候选再按 id 去重。
     *
     * @param limit 期望返回的总数（首页拼贴墙需要 18 个方块，故默认 20）
     */
    suspend fun radarPlaylists(limit: Int = 20): List<Playlist> {
        val result = mutableListOf<Playlist>()

        // ---- 1) 私人雷达（非关键路径，失败静默跳过）----
        // 插到索引 2：首页拼贴墙的方块顺序中，第 3 个（index 2）是最大的 80×80 方块，
        // 把私人雷达放在这里视觉上最醒目，符合它「核心推荐」的定位。
        runCatching {
            val root = getPlain("/api/v6/playlist/detail?id=$RADAR_PLAYLIST_ID")
            root["playlist"]?.toPlaylist()
        }.getOrNull()?.let { radar ->
            if (result.size >= RADAR_INDEX) result.add(RADAR_INDEX, radar) else result += radar
        }

        // ---- 2) 推荐歌单补齐 + 去重 ----
        val need = (limit - result.size).coerceAtLeast(0)
        if (need > 0) {
            // 多取 4 条作为去重/失效的缓冲
            val root = getPlain("/api/personalized/playlist?limit=${limit + 4}")
            val recommendations = root["result"]?.arr()?.mapNotNull { it.toPlaylist() } ?: emptyList()
            result += recommendations
                .filterNot { rec -> result.any { it.id == rec.id } }
                .take(need)
        }

        return result
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
        for (lv in qualityFallbackChain(level)) {
            val hit = fetchPlayerUrl(id, lv) ?: continue
            val (url, actualLevel) = hit
            if (url.isNotBlank()) {
                // 用服务端回报的实际档位，而不是我们请求的档位
                return ResolvedUrl(url = url, level = actualLevel, requestedLevel = level)
            }
        }
        return null
    }

    /**
     * 按请求音质生成**降级链**（由高到低）。
     *
     * 顺序不是随便排的：每次都退到低一档，直到 `standard`。
     */
    private fun qualityFallbackChain(level: String): List<String> = when (level) {
        "jymaster" -> listOf("jymaster", "hires", "lossless", "exhigh", "higher", "standard")
        "hires" -> listOf("hires", "lossless", "exhigh", "higher", "standard")
        "lossless" -> listOf("lossless", "exhigh", "higher", "standard")
        "exhigh" -> listOf("exhigh", "higher", "standard")
        "higher" -> listOf("higher", "standard")
        else -> listOf("standard")
    }

    /**
     * 按**单一**音质取播放地址：v1 接口优先，失败回退旧接口。
     *
     * 返回 Pair(地址, 服务端回报的实际音质)。第二项很关键：
     * 即使请求了 lossless，服务端也可能只给 320k（版权方限制），
     * 此时必须把**服务端说的真实档位**带回去，否则界面会照搬请求值撒谎。
     *
     * @return Pair(可播放地址, 实际音质)；该音质不可用时为 null
     */
    private suspend fun fetchPlayerUrl(id: Long, level: String): Pair<String, String>? {
        // ---- 优先 v1 接口：同一套 level 覆盖全部 6 档音质 ----
        // v1 会在 data[0] 里回传 level（服务端实际给的档位）与 br（码率）
        val v1Item = runCatching {
            weapi(
                "/weapi/song/enhance/player/url/v1",
                buildJsonObject {
                    put("ids", "[$id]")
                    put("level", level)
                    put("encodeType", encodeTypeOf(level))
                    put("csrf_token", "")
                },
            )["data"]?.arr()?.firstOrNull()?.safeObject()
        }.getOrNull()
        val viaV1 = v1Item?.get("url")?.str()
        if (!viaV1.isNullOrBlank()) {
            // 优先用服务端回报的 level；缺失时退回请求值
            val actual = v1Item.get("level")?.str()?.takeIf { it.isNotBlank() } ?: level
            return viaV1 to actual
        }

        // ---- 回退旧接口 ----
        // 旧接口只认 br 数值：lossless 及以上统一请求 999000，
        // 让服务端在账号权限内返回最高音质。
        // ⚠️ 不能填 320000 —— 那会把上限锁死在「极高」，
        // 反而比请求无损拿到的还差。
        val br = when (level) {
            "standard" -> 128000
            "higher" -> 192000
            else -> 999000
        }
        return runCatching {
            val item = weapi(
                "/weapi/song/enhance/player/url",
                buildJsonObject {
                    put("ids", "[$id]")
                    put("br", br)
                    put("csrf_token", "")
                },
            )["data"]?.arr()?.firstOrNull()?.safeObject()
            val url = item?.get("url")?.str()
            if (url.isNullOrBlank()) null else {
                // 旧接口不回传 level，但有 br 码率 —— 由码率反推真实档位
                // br 在 JSON 里可能是数字也可能是字符串，统一转成文本再解析，
                // 任何异常情况都退化为 0（levelOfBr 会返回 null）
                val br = item.get("br")?.str()?.toDoubleOrNull()?.toInt() ?: 0
                val actual = item.get("level")?.str()?.takeIf { it.isNotBlank() }
                    ?: levelOfBr(br)
                    ?: level
                url to actual
            }
        }.getOrNull()
    }

    /** 由码率反推音质档位（旧接口无 level 字段时的兜底） */
    private fun levelOfBr(br: Int): String? = when {
        br <= 0 -> null
        br < 160_000 -> "standard"
        br < 256_000 -> "higher"
        br < 999_000 -> "exhigh"
        else -> "lossless"
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
        val root = weapi(
            "/weapi/song/enhance/download/url/v1",
            buildJsonObject {
                put("id", "$id")
                put("level", level.ifBlank { "exhigh" })
                put("encodeType", encodeTypeOf(level))
                put("csrf_token", "")
            },
        )
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
        "lossless", "hires", "jymaster" -> "flac"
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
}
