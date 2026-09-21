package com.buddy.ncmusic.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

/**
 * 音频效果封装：系统均衡器（EQ）与响度增强（音量均衡）。
 * 依附于当前播放器的 audioSessionId。
 */
object AudioEffects {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId: Int = 0

    /** 绑定到指定音频会话（会话变化时重建，保证 EQ 始终挂在有效会话上） */
    fun attach(id: Int) {
        if (equalizer != null && id == sessionId) return
        release()
        sessionId = id
        runCatching { equalizer = Equalizer(0, id) }
        runCatching { loudness = LoudnessEnhancer(id) }
    }

    /**
     * 绑定到全局输出（session 0）：无需播放即可调节 EQ。
     * 部分设备对 session 0 支持有限，此时仍可在播放后自动切换到播放器 session。
     */
    fun attachGlobal() {
        if (equalizer != null) return
        sessionId = 0
        runCatching { equalizer = Equalizer(0, 0) }
        runCatching { loudness = LoudnessEnhancer(0) }
    }

    // ---------- EQ ----------

    fun setEqEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
    }

    fun isEqAvailable(): Boolean = equalizer != null

    fun bandCount(): Int = runCatching { equalizer?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)

    fun bandFreq(index: Int): Int =
        runCatching { (equalizer?.getCenterFreq(index.toShort()) ?: 0) / 1000 }.getOrDefault(0)

    fun bandLevel(index: Int): Short =
        runCatching { equalizer?.getBandLevel(index.toShort()) ?: 0 }.getOrDefault(0)

    fun setBandLevel(index: Int, levelMb: Short) {
        runCatching { equalizer?.setBandLevel(index.toShort(), levelMb) }
    }

    /** 单频段可调范围（毫贝） */
    fun levelRange(): Pair<Short, Short> = runCatching {
        val eq = equalizer ?: return@runCatching Pair<Short, Short>(-1500, 1500)
        Pair(eq.bandLevelRange[0], eq.bandLevelRange[1])
    }.getOrDefault(Pair<Short, Short>(-1500, 1500))

    /** 内置预设数量与名称 */
    fun presetCount(): Int = runCatching { equalizer?.numberOfPresets?.toInt() ?: 0 }.getOrDefault(0)

    fun presetName(index: Int): String =
        runCatching { equalizer?.getPresetName(index.toShort()) ?: "" }.getOrDefault("")

    fun usePreset(index: Int) {
        runCatching { equalizer?.usePreset(index.toShort()) }
    }

    fun reset() {
        runCatching {
            val eq = equalizer ?: return@runCatching
            eq.usePreset(0)
        }
    }

    // ---------- 音量均衡 ----------

    /** 当前增益（毫贝），可调 */
    private var gainMb = 300

    fun setVolumeGain(gain: Int) {
        gainMb = gain
        runCatching { loudness?.setTargetGain(gain) }
    }

    fun volumeGain(): Int = gainMb

    /** 响度增益可调范围（毫贝） */
    fun gainRange(): Pair<Int, Int> = runCatching {
        val le = loudness ?: return@runCatching 0 to 1200
        // LoudnessEnhancer 未暴露范围接口，按官方建议取 0~1200mB（约 +12dB）
        0 to 1200
    }.getOrDefault(0 to 1200)

    fun setVolumeNormalize(enabled: Boolean) {
        runCatching {
            val le = loudness ?: return@runCatching
            le.setTargetGain(if (enabled) gainMb else 0)
            le.enabled = enabled
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
        sessionId = 0
    }
}
