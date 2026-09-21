package com.buddy.ncmusic.util

/** 单行歌词 */
data class LyricLine(
    val time: Long,
    val text: String,
)

/** LRC 歌词解析器 */
object LyricParser {

    // 匹配 [mm:ss.xx] 或 [mm:ss:xx] 时间标签
    private val timeTag = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val matches = timeTag.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substringAfterLast(']').trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
                val msRaw = m.groupValues[3].padEnd(3, '0').take(3)
                val ms = msRaw.toLongOrNull() ?: 0L
                result.add(LyricLine(time = min * 60_000 + sec * 1_000 + ms, text = text))
            }
        }
        return result.sortedBy { it.time }
    }
}
