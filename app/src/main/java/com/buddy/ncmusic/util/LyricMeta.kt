package com.buddy.ncmusic.util

/**
 * 判断某一行歌词是否为「元信息」（作词 / 作曲 / 编曲 …）。
 *
 * LRC 里这类行和正文混在一起，时间戳通常落在最前面。如果不过滤：
 *  · 播放器页的歌词预览会在歌曲开头显示「作曲：xxx」；
 *  · 全屏歌词页会把这一行当作"正在演唱"高亮放大。
 * 两种表现都会让用户以为进度错了。
 *
 * 这里集中一份规则，播放器与歌词页共用，避免两边判定不一致。
 */
private val META_LINE = Regex(
    """^\s*(作词|作詞|作曲|编曲|編曲|制作人|製作人|出品|监制|監製|混音|母带|母帶|录音|錄音|""" +
        """吉他|贝斯|貝斯|鼓|键盘|鍵盤|和声|和聲|弦乐|弦樂|OP|SP|词|詞|曲)\s*[:：]""",
)

fun isMetaLyric(text: String): Boolean = META_LINE.containsMatchIn(text.trim())

/**
 * 在一组歌词里找到"当前应该显示"的那一行。
 *
 * @return 下标；全部为元信息时返回 0
 */
fun currentLyricIndex(times: LongArray, texts: List<String>, position: Long): Int {
    var firstReal = texts.indexOfFirst { !isMetaLyric(it) }
    if (firstReal < 0) firstReal = 0
    var found = firstReal
    for (i in times.indices) {
        if (times[i] <= position && !isMetaLyric(texts[i])) found = i
    }
    return found
}
