package com.buddy.ncmusic.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ncmusic")

/** 用户偏好与登录态快照 */
data class UserState(
    val cookie: String = "",
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    /** 主题模式：system / light / dark */
    val themeMode: String = "system",
    /** 是否启用莫奈动态取色 */
    val dynamicColor: Boolean = true,
    /** 自定义主题色（ARGB Long，0 = 未设置） */
    val customColor: Long = 0L,
    /** 默认音质（兜底） */
    val quality: String = "standard",
    /** WiFi 下最高音质 */
    val wifiQuality: String = "exhigh",
    /** 移动流量下最高音质 */
    val mobileQuality: String = "higher",
    val startPage: String = "home",
    /** 定时关闭（分钟，0 = 关闭） */
    val timerMinutes: Int = 0,
    /** 最大缓存（MB） */
    val maxCacheMb: Int = 500,
    /** 音量均衡（ReplayGain） */
    val volumeNormalize: Boolean = false,
    /** 音量均衡增益（毫贝，0~1200） */
    val volumeGain: Int = 300,
    /** 解码模式：hard / soft */
    val decodeMode: String = "hard",
    /** EQ 均衡器开关 */
    val eqEnabled: Boolean = false,
    /** USB 独占输出 */
    val usbExclusive: Boolean = false,
    /** 累计听歌时长（秒） */
    val totalListenSeconds: Long = 0L,
    /** 近若干日听歌时长（日期 -> 秒） */
    val listenByDay: Map<String, Long> = emptyMap(),
    /** 搜索历史（最新在前） */
    val searchHistory: List<String> = emptyList(),
    /** 桌面歌词悬浮窗 */
    val lyricOverlay: Boolean = false,
    /** 歌词字体颜色（ARGB） */
    val lyricColor: Long = 0xFFFFFFFF,
    /** 歌词字号（sp） */
    val lyricFontSize: Int = 18,
    /** 歌词悬浮窗横向位置 */
    val lyricX: Int = 0,
    /** 歌词悬浮窗纵向位置 */
    val lyricY: Int = 320,
    /** 歌词悬浮窗是否锁定位置（锁定后不可拖动） */
    val lyricLocked: Boolean = false,
    /** 自定义下载 / 缓存目录（SAF URI，空 = 应用私有目录） */
    val cacheDirUri: String = "",
) {
    val isLoggedIn: Boolean
        get() = cookie.isNotBlank() && userId > 0
}

/** DataStore 封装：仅存登录态与轻量设置，替代 Room 减少依赖与内存 */
class UserPreferences(private val context: Context) {

    private object Keys {
        val COOKIE = stringPreferencesKey("cookie")
        val USER_ID = longPreferencesKey("user_id")
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR = stringPreferencesKey("avatar_url")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CUSTOM_COLOR = longPreferencesKey("custom_color")
        val QUALITY = stringPreferencesKey("quality")
        val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        val MOBILE_QUALITY = stringPreferencesKey("mobile_quality")
        val START_PAGE = stringPreferencesKey("start_page")
        val TIMER_MINUTES = intPreferencesKey("timer_minutes")
        val MAX_CACHE_MB = intPreferencesKey("max_cache_mb")
        val VOLUME_NORMALIZE = booleanPreferencesKey("volume_normalize")
        val VOLUME_GAIN = intPreferencesKey("volume_gain")
        val DECODE_MODE = stringPreferencesKey("decode_mode")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val USB_EXCLUSIVE = booleanPreferencesKey("usb_exclusive")
        val TOTAL_LISTEN = longPreferencesKey("total_listen_seconds")
        val LISTEN_BY_DAY = stringPreferencesKey("listen_by_day")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val LYRIC_OVERLAY = booleanPreferencesKey("lyric_overlay")
        val LYRIC_COLOR = longPreferencesKey("lyric_color")
        val LYRIC_FONT_SIZE = intPreferencesKey("lyric_font_size")
        val LYRIC_X = intPreferencesKey("lyric_x")
        val LYRIC_Y = intPreferencesKey("lyric_y")
        val LYRIC_LOCKED = booleanPreferencesKey("lyric_locked")
        val CACHE_DIR_URI = stringPreferencesKey("cache_dir_uri")
    }

    val userStateFlow: Flow<UserState> = context.dataStore.data.map { p ->
        UserState(
            cookie = p[Keys.COOKIE] ?: "",
            userId = p[Keys.USER_ID] ?: 0,
            nickname = p[Keys.NICKNAME] ?: "",
            avatarUrl = p[Keys.AVATAR] ?: "",
            themeMode = p[Keys.THEME_MODE] ?: "system",
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            customColor = p[Keys.CUSTOM_COLOR] ?: 0L,
            quality = p[Keys.QUALITY] ?: "standard",
            wifiQuality = p[Keys.WIFI_QUALITY] ?: "exhigh",
            mobileQuality = p[Keys.MOBILE_QUALITY] ?: "higher",
            startPage = p[Keys.START_PAGE] ?: "home",
            timerMinutes = p[Keys.TIMER_MINUTES] ?: 0,
            maxCacheMb = p[Keys.MAX_CACHE_MB] ?: 500,
            volumeNormalize = p[Keys.VOLUME_NORMALIZE] ?: false,
            volumeGain = p[Keys.VOLUME_GAIN] ?: 300,
            decodeMode = p[Keys.DECODE_MODE] ?: "hard",
            eqEnabled = p[Keys.EQ_ENABLED] ?: false,
            usbExclusive = p[Keys.USB_EXCLUSIVE] ?: false,
            totalListenSeconds = p[Keys.TOTAL_LISTEN] ?: 0L,
            listenByDay = parseListenMap(p[Keys.LISTEN_BY_DAY] ?: ""),
            searchHistory = (p[Keys.SEARCH_HISTORY] ?: "")
                .split(SEP)
                .filter { it.isNotBlank() },
            lyricOverlay = p[Keys.LYRIC_OVERLAY] ?: false,
            lyricColor = p[Keys.LYRIC_COLOR] ?: 0xFFFFFFFF,
            lyricFontSize = p[Keys.LYRIC_FONT_SIZE] ?: 18,
            lyricX = p[Keys.LYRIC_X] ?: 0,
            lyricY = p[Keys.LYRIC_Y] ?: 320,
            lyricLocked = p[Keys.LYRIC_LOCKED] ?: false,
            cacheDirUri = p[Keys.CACHE_DIR_URI] ?: "",
        )
    }

    companion object {
        /** 搜索历史分隔符（避免与关键词中可能出现的逗号冲突） */
        private const val SEP = "\u0001"
    }

    suspend fun addSearchHistory(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        context.dataStore.edit { p ->
            val list = (p[Keys.SEARCH_HISTORY] ?: "")
                .split(SEP)
                .filter { it.isNotBlank() && it != kw }
                .toMutableList()
            list.add(0, kw)
            p[Keys.SEARCH_HISTORY] = list.take(20).joinToString(SEP)
        }
    }

    suspend fun removeSearchHistory(keyword: String) {
        context.dataStore.edit { p ->
            val list = (p[Keys.SEARCH_HISTORY] ?: "")
                .split(SEP)
                .filter { it.isNotBlank() && it != keyword }
            p[Keys.SEARCH_HISTORY] = list.joinToString(SEP)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    suspend fun setLyricOverlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LYRIC_OVERLAY] = enabled }
    }

    suspend fun setLyricColor(argb: Long) {
        context.dataStore.edit { it[Keys.LYRIC_COLOR] = argb }
    }

    suspend fun setLyricFontSize(sp: Int) {
        context.dataStore.edit { it[Keys.LYRIC_FONT_SIZE] = sp }
    }

    suspend fun setCacheDirUri(uri: String) {
        context.dataStore.edit { it[Keys.CACHE_DIR_URI] = uri }
    }

    suspend fun setLyricLocked(locked: Boolean) {
        context.dataStore.edit { it[Keys.LYRIC_LOCKED] = locked }
    }

    suspend fun setLyricPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[Keys.LYRIC_X] = x
            it[Keys.LYRIC_Y] = y
        }
    }

    suspend fun saveLogin(userId: Long, nickname: String, avatarUrl: String, cookie: String) {
        context.dataStore.edit { p ->
            p[Keys.COOKIE] = cookie
            p[Keys.USER_ID] = userId
            p[Keys.NICKNAME] = nickname
            p[Keys.AVATAR] = avatarUrl
        }
    }

    suspend fun clearLogin() {
        context.dataStore.edit { p ->
            p.remove(Keys.COOKIE)
            p.remove(Keys.USER_ID)
            p.remove(Keys.NICKNAME)
            p.remove(Keys.AVATAR)
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCustomColor(argb: Long) {
        context.dataStore.edit { it[Keys.CUSTOM_COLOR] = argb }
    }

    suspend fun setQuality(quality: String) {
        context.dataStore.edit { it[Keys.QUALITY] = quality }
    }

    suspend fun setWifiQuality(q: String) {
        context.dataStore.edit { it[Keys.WIFI_QUALITY] = q }
    }

    suspend fun setMobileQuality(q: String) {
        context.dataStore.edit { it[Keys.MOBILE_QUALITY] = q }
    }

    suspend fun setStartPage(page: String) {
        context.dataStore.edit { it[Keys.START_PAGE] = page }
    }

    suspend fun setTimerMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.TIMER_MINUTES] = minutes }
    }

    suspend fun setMaxCacheMb(mb: Int) {
        context.dataStore.edit { it[Keys.MAX_CACHE_MB] = mb }
    }

    suspend fun setVolumeNormalize(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOLUME_NORMALIZE] = enabled }
    }

    suspend fun setVolumeGain(gain: Int) {
        context.dataStore.edit { it[Keys.VOLUME_GAIN] = gain }
    }

    suspend fun setDecodeMode(mode: String) {
        context.dataStore.edit { it[Keys.DECODE_MODE] = mode }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setUsbExclusive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USB_EXCLUSIVE] = enabled }
    }

    suspend fun addListenSeconds(seconds: Long) {
        if (seconds <= 0) return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        context.dataStore.edit { p ->
            p[Keys.TOTAL_LISTEN] = (p[Keys.TOTAL_LISTEN] ?: 0L) + seconds
            val map = parseListenMap(p[Keys.LISTEN_BY_DAY] ?: "").toMutableMap()
            map[today] = (map[today] ?: 0L) + seconds
            // 仅保留最近 30 天，避免无限增长
            val trimmed = map.entries.sortedByDescending { it.key }.take(30)
            p[Keys.LISTEN_BY_DAY] = trimmed.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    private fun parseListenMap(s: String): Map<String, Long> {
        if (s.isBlank()) return emptyMap()
        return s.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val sec = parts[1].toLongOrNull()
                if (sec != null) parts[0] to sec else null
            } else {
                null
            }
        }.toMap()
    }
}
