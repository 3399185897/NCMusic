package com.buddy.ncmusic.util

import kotlin.math.abs

/** 单行歌词 */
data class LyricLine(
    val time: Long,
    val text: String,
    /** 官方翻译（该行无翻译时为空串） */
    val translation: String = "",
)

/** LRC 歌词解析器 */
object LyricParser {

    // 匹配 [mm:ss.xx] 或 [mm:ss:xx] 时间标签
    private val timeTag = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * 解析歌词并与官方翻译合并。
     *
     * 翻译（tlyric）与主歌词（lrc）通常时间戳一一对应，但也存在
     * 只翻译部分行的情况。因此对齐策略为：
     * 1. 优先精确匹配时间戳
     * 2. 否则取 300ms 内时间最近的翻译行
     * 3. 都无则 translation 为空串
     */
    fun parse(lrc: String?, tlyric: String? = null): List<LyricLine> {
        val main = parseSingle(lrc)
        if (main.isEmpty() || tlyric.isNullOrBlank()) return main

        val translations = parseSingle(tlyric).associateBy({ it.time }, { it.text })
        if (translations.isEmpty()) return main

        return main.map { line ->
            val exact = translations[line.time]
            val nearest = if (exact == null) {
                translations.entries
                    .minByOrNull { abs(it.key - line.time) }
                    ?.takeIf { abs(it.key - line.time) <= 300L }
                    ?.value
            } else {
                null
            }
            line.copy(translation = exact ?: nearest.orEmpty())
        }
    }

    /** 解析单轨歌词（不合并翻译） */
    private fun parseSingle(lrc: String?): List<LyricLine> {
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
